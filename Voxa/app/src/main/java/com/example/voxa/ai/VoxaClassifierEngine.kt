package com.example.voxa.ai

import android.content.Context
import android.util.Log
import com.example.voxa.data.ChildProfile
import com.example.voxa.data.EnrolledIntent
import com.example.voxa.data.AcousticTemplate
import com.example.voxa.utils.AudioFileHelper

/**
 * 🧠 VoxaClassifierEngine — Full Pipeline Orchestrator (Architecture v4)
 *
 * Coordinates the complete audio classification pipeline:
 *   1. VAD → Extract speech segments (persistent state across audio blocks)
 *   2. Silence Trim → Normalize pre-processing to match enrollment conditions
 *   3. Pad/Crop → Normalize to exactly 1.44s (23040 samples)
 *   4. YAMNet → Extract 1024-D mean-pooled embedding
 *   5. Cosine Similarity + CCP → Score against enrolled intent centroids
 *   6. OOD Gate → Reject if similarity is below intent-specific threshold
 *   7. Margin Gate → Reject if best and second-best are too close
 *
 * Instantiated once in VoxaListenerService and reused for all audio blocks.
 *
 * Reference: proposed_arch_v4.md, voxa.ipynb
 */
class VoxaClassifierEngine(
    context: Context,
    private val activeProfile: ChildProfile?,
    private val enrolledIntents: List<EnrolledIntent>,
    private val intentTemplates: Map<Long, List<AcousticTemplate>>
) : IVoxaClassifierEngine {

    companion object {
        private const val TAG = "VoxaClassifier"
    }

    // Energy-based VAD for speech segment extraction (retained from v1 — highly optimized native code)
    private val vad = VoxaVAD()

    // YAMNet neural encoder for 1024-D feature extraction (mean-pooled)
    private val yamnetEncoder: YamnetEncoder = YamnetEncoder(context)

    // Pre-parsed centroid data for each enrolled intent
    private val parsedIntentData: List<IntentData> = buildIntentData()

    /**
     * Internal representation of an enrolled intent's matching data.
     */
    private data class IntentData(
        val intentName: String,
        val outputPhrase: String,
        val audioAssetPath: String,
        val centroids: List<FloatArray>,
        val oodThreshold: Float
    )

    /**
     * Parses enrolled intent templates from the database into centroid vectors.
     * Each AcousticTemplate's `templateFeatures` field now stores serialized centroid(s)
     * in the format "[[c0_0,c0_1,...],[c1_0,c1_1,...]]".
     */
    private fun buildIntentData(): List<IntentData> {
        val result = mutableListOf<IntentData>()

        for (intent in enrolledIntents) {
            val templates = intentTemplates[intent.id] ?: continue
            if (templates.isEmpty()) continue

            // Centroid data is stored in the first template's features field
            // (all centroid vectors for an intent are serialized together)
            val serialized = templates[0].templateFeatures
            val centroids = PrototypicalMatcher.deserializeCentroids(serialized)

            if (centroids.isEmpty()) {
                Log.w(TAG, "No valid centroids for intent '${intent.intentName}' — skipping")
                continue
            }

            // Dimension guard: reject stale centroids from previous pipeline versions (e.g. 2048-D)
            // Without this, cosineSimilarity's require(a.size == b.size) throws on every utterance,
            // which gets swallowed as a generic "Classification error" in the listener service.
            val expectedDim = YamnetEncoder.OUTPUT_DIM
            if (centroids.any { it.size != expectedDim }) {
                Log.e(TAG, "⚠️ Intent '${intent.intentName}' has stale ${centroids[0].size}-D centroids " +
                        "(expected ${expectedDim}-D). Re-enrollment required — skipping this intent.")
                continue
            }

            result.add(IntentData(
                intentName = intent.intentName,
                outputPhrase = intent.outputPhrase,
                audioAssetPath = intent.audioAssetPath,
                centroids = centroids,
                oodThreshold = intent.oodThreshold
            ))
        }

        Log.d(TAG, "Loaded ${result.size} intents with centroids")
        return result
    }

    // Carry-over buffer for tail samples from previous audio block (Fix #5)
    // minBufferSize is not guaranteed to be a multiple of frameSize (320),
    // so we can lose up to 319 samples per block — enough to split a word boundary
    private var carryOver: ShortArray = ShortArray(0)

    override fun processAudioBlock(pcmData: ShortArray): ClassificationResult? {
        // ── Step 1: Prepend carry-over from previous block, then feed frames to VAD ──
        val frameSize = 320 // 20ms at 16kHz
        val combined: ShortArray
        if (carryOver.isNotEmpty()) {
            combined = ShortArray(carryOver.size + pcmData.size)
            System.arraycopy(carryOver, 0, combined, 0, carryOver.size)
            System.arraycopy(pcmData, 0, combined, carryOver.size, pcmData.size)
        } else {
            combined = pcmData
        }

        val completedSegments = mutableListOf<ShortArray>()
        var offset = 0
        while (offset + frameSize <= combined.size) {
            val frame = combined.copyOfRange(offset, offset + frameSize)
            val (_, segment) = vad.processFrame(frame)
            if (segment != null) {
                completedSegments.add(segment)
            }
            offset += frameSize
        }

        // Save remainder for next block
        carryOver = if (offset < combined.size) {
            combined.copyOfRange(offset, combined.size)
        } else {
            ShortArray(0)
        }

        if (completedSegments.isEmpty()) {
            return null // No complete speech segment yet — continue listening
        }

        // Process ALL completed segments (Fix #5: second segment in same block was previously dropped)
        for (rawSegment in completedSegments) {
            Log.d(TAG, "VAD completed segment: ${rawSegment.size} samples (${rawSegment.size / 16000.0}s)")

            // ── Step 2: Silence trim to match enrollment pre-processing ──
            val trimmedSegment = AudioFileHelper.trimSilence(rawSegment)
            if (trimmedSegment.size < 4000) { // Less than 250ms of actual speech
                Log.d(TAG, "Segment too short after trimming (${trimmedSegment.size} samples, ${trimmedSegment.size / 16.0}ms) — skipping")
                continue
            }

            // ── Step 3: Check encoder readiness ──
            if (!yamnetEncoder.isValid()) {
                return ClassificationResult(
                    isMatch = false, intentName = null, outputPhrase = null,
                    audioAssetPath = null, confidence = 0f,
                    reason = "YAMNet encoder not loaded"
                )
            }

            // ── Step 4: Extract 1024-D embedding via YAMNet mean-pooling ──
            val liveEmbedding: FloatArray
            try {
                liveEmbedding = yamnetEncoder.extractFromPcm(trimmedSegment)
            } catch (e: Exception) {
                Log.e(TAG, "YAMNet embedding extraction failed: ${e.message}")
                continue // Try next segment instead of failing entirely
            }

            // ── Step 5: Check enrolled intents ──
            if (parsedIntentData.isEmpty()) {
                return ClassificationResult(
                    isMatch = false, intentName = null, outputPhrase = null,
                    audioAssetPath = null, confidence = 0f,
                    reason = "No enrolled templates available"
                )
            }

            // ── Step 6: Score live embedding against all enrolled intents ──
            val scores = parsedIntentData.map { intentData ->
                PrototypicalMatcher.scoreIntent(
                    liveEmbedding = liveEmbedding,
                    centroids = intentData.centroids,
                    intentName = intentData.intentName,
                    outputPhrase = intentData.outputPhrase,
                    audioAssetPath = intentData.audioAssetPath,
                    oodThreshold = intentData.oodThreshold
                )
            }

            // Log all scores for debugging
            for (score in scores) {
                Log.d(TAG, "  ${score.intentName}: raw=${String.format("%.3f", score.rawSimilarity)}, " +
                        "eff=${String.format("%.3f", score.effectiveSimilarity)}, " +
                        "centroids=${score.centroidCount}, OOD=${String.format("%.3f", score.oodThreshold)}")
            }

            // ── Step 7: Evaluate OOD Gate + Margin Gate ──
            val result = PrototypicalMatcher.evaluateGates(scores)
            if (result.isMatch) {
                return result // Return first valid match
            }
            // If not a match, log and continue to next segment
            Log.d(TAG, "Segment rejected: ${result.reason}")
        }

        return null // No segment matched
    }
}
