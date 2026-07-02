package com.example.voxa.ai

import android.content.Context
import android.util.Log
import com.example.voxa.data.ChildProfile
import com.example.voxa.data.EnrolledIntent
import com.example.voxa.data.AcousticTemplate

/**
 * 🧠 VoxaClassifierEngine — Full Pipeline Orchestrator (Architecture v4)
 *
 * Coordinates the complete audio classification pipeline:
 *   1. VAD → Extract speech segments from continuous audio
 *   2. Tile/Crop → Normalize segment to 1.44s (23040 samples)
 *   3. YAMNet → Extract 2048-D temporal-halved embedding
 *   4. Cosine Similarity + CCP → Score against enrolled intent centroids
 *   5. OOD Gate → Reject if similarity is below intent-specific threshold
 *   6. Margin Gate → Reject if best and second-best are too close
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

    // YAMNet neural encoder for 2048-D feature extraction
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

    override fun processAudioBlock(pcmData: ShortArray): ClassificationResult? {
        // ── Step 1: VAD — Extract speech segments from continuous audio ──
        val segments = vad.processAudio(pcmData)
        if (segments.isEmpty()) {
            return null // No speech detected — continue listening silently
        }

        // Process the first valid speech segment
        val segment = segments[0]
        Log.d(TAG, "VAD extracted segment: ${segment.size} samples (${segment.size / 16000.0}s)")

        // ── Step 2: Check encoder readiness ──
        if (!yamnetEncoder.isValid()) {
            return ClassificationResult(
                isMatch = false, intentName = null, outputPhrase = null,
                audioAssetPath = null, confidence = 0f,
                reason = "YAMNet encoder not loaded"
            )
        }

        // ── Step 3: Extract 2048-D embedding via YAMNet ──
        // YamnetEncoder.extractFromPcm handles tile/crop to 1.44s internally
        val liveEmbedding: FloatArray
        try {
            liveEmbedding = yamnetEncoder.extractFromPcm(segment)
        } catch (e: Exception) {
            Log.e(TAG, "YAMNet embedding extraction failed: ${e.message}")
            return ClassificationResult(
                isMatch = false, intentName = null, outputPhrase = null,
                audioAssetPath = null, confidence = 0f,
                reason = "Embedding extraction failed: ${e.message}"
            )
        }

        // ── Step 4: Check enrolled intents ──
        if (parsedIntentData.isEmpty()) {
            return ClassificationResult(
                isMatch = false, intentName = null, outputPhrase = null,
                audioAssetPath = null, confidence = 0f,
                reason = "No enrolled templates available"
            )
        }

        // ── Step 5: Score live embedding against all enrolled intents ──
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

        // ── Step 6: Evaluate OOD Gate + Margin Gate ──
        return PrototypicalMatcher.evaluateGates(scores)
    }
}
