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
 *   4. YAMNet → Extract 2048-D temporal-halved embedding
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

    // YAMNet neural encoder for 2048-D feature extraction
    private val yamnetEncoder: YamnetEncoder = YamnetEncoder(context)

    // MFCC feature extractor for DTW template matching
    private val mfccExtractor = com.example.voxa.ai.archive.MfccExtractor()

    // Pre-parsed centroid/template data for each enrolled intent
    private val parsedIntentData: List<IntentData> = buildIntentData()

    /**
     * Internal representation of an enrolled intent's matching data.
     */
    private data class IntentData(
        val intentName: String,
        val outputPhrase: String,
        val audioAssetPath: String,
        val centroids: List<FloatArray>,
        val dtwTemplates: List<Array<FloatArray>>,
        val isDtw: Boolean,
        val oodThreshold: Float
    )

    private fun deserializeDtwTemplate(serialized: String): Array<FloatArray> {
        val trimmed = serialized.trim()
        if (trimmed.isEmpty() || trimmed == "[]") return emptyArray()
        return try {
            val list = mutableListOf<FloatArray>()
            val inner = trimmed.removePrefix("[").removeSuffix("]")
            var depth = 0
            var current = java.lang.StringBuilder()
            for (ch in inner) {
                when (ch) {
                    '[' -> {
                        depth++
                        if (depth == 1) current = java.lang.StringBuilder()
                        else current.append(ch)
                    }
                    ']' -> {
                        depth--
                        if (depth == 0) {
                            val row = current.toString().split(",").map { it.trim().toFloat() }.toFloatArray()
                            list.add(row)
                        } else {
                            current.append(ch)
                        }
                    }
                    ',' -> {
                        if (depth == 0) {}
                        else current.append(ch)
                    }
                    else -> current.append(ch)
                }
            }
            list.toTypedArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize DTW template features: ${e.message}")
            emptyArray()
        }
    }

    /**
     * Parses enrolled intent templates from the database into centroid vectors or DTW templates.
     */
    private fun buildIntentData(): List<IntentData> {
        val result = mutableListOf<IntentData>()

        for (intent in enrolledIntents) {
            val templates = intentTemplates[intent.id] ?: continue
            if (templates.isEmpty()) continue

            // Determine if this intent uses DTW or Centroid
            val firstFeatures = templates[0].templateFeatures
            val firstMatrix = deserializeDtwTemplate(firstFeatures)
            val isDtw = templates.size > 1 || (firstMatrix.isNotEmpty() && firstMatrix[0].size == 40)

            if (isDtw) {
                val dtwTemplates = templates.map { t ->
                    deserializeDtwTemplate(t.templateFeatures)
                }.filter { it.isNotEmpty() }

                if (dtwTemplates.isEmpty()) {
                    Log.w(TAG, "No valid DTW templates for intent '${intent.intentName}' — skipping")
                    continue
                }

                result.add(IntentData(
                    intentName = intent.intentName,
                    outputPhrase = intent.outputPhrase,
                    audioAssetPath = intent.audioAssetPath,
                    centroids = emptyList(),
                    dtwTemplates = dtwTemplates,
                    isDtw = true,
                    oodThreshold = maxOf(0.85f, intent.oodThreshold)
                ))
            } else {
                val centroids = PrototypicalMatcher.deserializeCentroids(firstFeatures)
                if (centroids.isEmpty()) {
                    Log.w(TAG, "No valid centroids for intent '${intent.intentName}' — skipping")
                    continue
                }

                result.add(IntentData(
                    intentName = intent.intentName,
                    outputPhrase = intent.outputPhrase,
                    audioAssetPath = intent.audioAssetPath,
                    centroids = centroids,
                    dtwTemplates = emptyList(),
                    isDtw = false,
                    oodThreshold = intent.oodThreshold
                ))
            }
        }

        Log.d(TAG, "Loaded ${result.size} intents (Centroid & DTW)")
        return result
    }

    override fun processAudioBlock(pcmData: ShortArray): ClassificationResult? {
        // ── Step 1: Feed audio frames to persistent VAD (state preserved across blocks) ──
        val frameSize = 320 // 20ms at 16kHz
        val completedSegments = mutableListOf<ShortArray>()

        var offset = 0
        while (offset + frameSize <= pcmData.size) {
            val frame = pcmData.copyOfRange(offset, offset + frameSize)
            val (_, segment) = vad.processFrame(frame)
            if (segment != null) {
                completedSegments.add(segment)
            }
            offset += frameSize
        }

        if (completedSegments.isEmpty()) {
            return null // No complete speech segment yet — continue listening
        }

        // Process the first completed speech segment
        val rawSegment = completedSegments[0]
        Log.d(TAG, "VAD completed segment: ${rawSegment.size} samples (${rawSegment.size / 16000.0}s)")

        // ── Step 2: Silence trim to match enrollment pre-processing ──
        val trimmedSegment = AudioFileHelper.trimSilence(rawSegment)
        if (trimmedSegment.size < 800) { // Allowed down to 50ms for DTW clicks
            Log.d(TAG, "Segment too short after trimming (${trimmedSegment.size} samples) — skipping")
            return null
        }

        // ── Step 3: Check enrolled intents ──
        if (parsedIntentData.isEmpty()) {
            return ClassificationResult(
                isMatch = false, intentName = null, outputPhrase = null,
                audioAssetPath = null, confidence = 0f,
                reason = "No enrolled templates available"
            )
        }

        val hasCentroid = parsedIntentData.any { !it.isDtw }
        val hasDtw = parsedIntentData.any { it.isDtw }

        // ── Step 4: Extract features conditionally to optimize CPU ──
        val liveEmbedding = if (hasCentroid && yamnetEncoder.isValid()) {
            try {
                yamnetEncoder.extractFromPcm(trimmedSegment)
            } catch (e: Exception) {
                Log.e(TAG, "YAMNet embedding extraction failed: ${e.message}")
                null
            }
        } else null

        val liveMfcc = if (hasDtw) {
            try {
                mfccExtractor.extract(trimmedSegment)
            } catch (e: Exception) {
                Log.e(TAG, "MFCC extraction failed: ${e.message}")
                null
            }
        } else null

        // ── Step 5: Score live input against all enrolled intents ──
        val scores = parsedIntentData.map { intentData ->
            if (intentData.isDtw) {
                if (liveMfcc == null || liveMfcc.isEmpty()) {
                    PrototypicalMatcher.IntentScore(
                        intentName = intentData.intentName,
                        outputPhrase = intentData.outputPhrase,
                        audioAssetPath = intentData.audioAssetPath,
                        rawSimilarity = 0f,
                        effectiveSimilarity = 0f,
                        centroidCount = intentData.dtwTemplates.size,
                        oodThreshold = intentData.oodThreshold
                    )
                } else {
                    val distances = intentData.dtwTemplates.map { template ->
                        com.example.voxa.ai.archive.DtwMatcher.dtwDistance(template, liveMfcc)
                    }
                    val minDtwDist = distances.minOrNull() ?: 99.0
                    val similarity = (1.0f - (minDtwDist.toFloat() / 36.6f)).coerceIn(0.0f, 1.0f)

                    Log.d(TAG, "DTW Match [${intentData.intentName}]: dist=$minDtwDist, sim=$similarity, threshold=${intentData.oodThreshold}")

                    PrototypicalMatcher.IntentScore(
                        intentName = intentData.intentName,
                        outputPhrase = intentData.outputPhrase,
                        audioAssetPath = intentData.audioAssetPath,
                        rawSimilarity = similarity,
                        effectiveSimilarity = similarity,
                        centroidCount = 1,
                        oodThreshold = intentData.oodThreshold
                    )
                }
            } else {
                if (liveEmbedding == null) {
                    PrototypicalMatcher.IntentScore(
                        intentName = intentData.intentName,
                        outputPhrase = intentData.outputPhrase,
                        audioAssetPath = intentData.audioAssetPath,
                        rawSimilarity = 0f,
                        effectiveSimilarity = 0f,
                        centroidCount = intentData.centroids.size,
                        oodThreshold = intentData.oodThreshold
                    )
                } else {
                    PrototypicalMatcher.scoreIntent(
                        liveEmbedding = liveEmbedding,
                        centroids = intentData.centroids,
                        intentName = intentData.intentName,
                        outputPhrase = intentData.outputPhrase,
                        audioAssetPath = intentData.audioAssetPath,
                        oodThreshold = intentData.oodThreshold
                    )
                }
            }
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
