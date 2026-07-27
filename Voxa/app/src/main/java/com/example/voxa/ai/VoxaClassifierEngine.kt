package com.example.voxa.ai

import android.content.Context
import android.util.Log
import com.example.voxa.data.ChildProfile
import com.example.voxa.data.EnrolledIntent
import com.example.voxa.data.AcousticTemplate
import com.example.voxa.utils.AudioFileHelper

/**
 * 🧠 VoxaClassifierEngine — Full Pipeline Orchestrator (MFCC + DTW Demo)
 *
 * Coordinates the complete audio classification pipeline:
 *   1. VAD → Extract speech segments (persistent state across audio blocks)
 *   2. Silence Trim → Remove leading/trailing silence to match enrollment conditions
 *   3. MFCC Extraction → 40-dim feature vectors from trimmed speech
 *   4. DTW Consensus Matching → Compare against enrolled templates
 *   5. Margin Gate → Validate match confidence (absolute + margin thresholds)
 *
 * ⚠️ CRITICAL PARITY: The processing pipeline here (VAD → trimSilence → MFCC) is
 *    IDENTICAL to the enrollment pipeline in VoxaViewModel.enrollIntent().
 *    Any change to one must be mirrored in the other.
 *
 * Instantiated once in VoxaListenerService and reused for all audio blocks.
 */
class VoxaClassifierEngine(
    context: Context,
    private val activeProfile: ChildProfile?,
    private val enrolledIntents: List<EnrolledIntent>,
    private val intentTemplates: Map<Long, List<AcousticTemplate>>
) : IVoxaClassifierEngine {

    companion object {
        private const val TAG = "VoxaClassifier"

        // --- 📊 MATCHING THRESHOLDS ---
        // ABSOLUTE_THRESHOLD: Maximum DTW distance for a valid match.
        // Lower distance = more similar. Sounds beyond this threshold are rejected as unknown.
        private const val ABSOLUTE_THRESHOLD = 7.80f

        // MARGIN_THRESHOLD: Minimum separation between top-1 and top-2 match distances.
        // Prevents ambiguous matches when two intents produce similar DTW scores.
        private const val MARGIN_THRESHOLD = 0.15f
    }

    // Energy-based VAD for speech segment extraction (same VAD as enrollment recording)
    private val vad = VoxaVAD()

    // Kotlin-native MFCC feature extractor (40-D: 13 MFCC + 13 Δ + 13 ΔΔ + 1 Log RMS)
    private val mfccExtractor = MfccExtractor()

    // Pre-parsed MFCC template matrices from the database, grouped by intent name
    private val parsedTemplates: Map<String, List<Array<FloatArray>>> = buildTemplateMap()

    /**
     * Parses serialized MFCC template features from AcousticTemplate.templateFeatures.
     * Each template's features are stored as a flat JSON array of floats with 40 features per frame.
     * Returns Array<FloatArray> of shape [T, 40].
     */
    private fun parseTemplateFeatures(serialized: String): Array<FloatArray>? {
        return try {
            val cleaned = serialized.trim().removePrefix("[").removeSuffix("]")
            val floats = cleaned.split(",").map { it.trim().toFloat() }
            val featureDim = 40
            val numFrames = floats.size / featureDim
            if (numFrames == 0 || floats.size % featureDim != 0) return null

            Array(numFrames) { t ->
                FloatArray(featureDim) { d ->
                    floats[t * featureDim + d]
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse template features: ${e.message}")
            null
        }
    }

    /**
     * Builds a map of intent name → list of parsed [T × 40] feature matrices for DTW matching.
     */
    private fun buildTemplateMap(): Map<String, List<Array<FloatArray>>> {
        val result = mutableMapOf<String, MutableList<Array<FloatArray>>>()

        for (intent in enrolledIntents) {
            val templates = intentTemplates[intent.id] ?: continue
            val featureList = mutableListOf<Array<FloatArray>>()

            for (template in templates) {
                val features = parseTemplateFeatures(template.templateFeatures)
                if (features != null && features.isNotEmpty()) {
                    featureList.add(features)
                }
            }

            if (featureList.isNotEmpty()) {
                result[intent.intentName] = featureList
            }
        }

        Log.d(TAG, "Loaded ${result.size} intents with DTW templates " +
                "(${result.values.sumOf { it.size }} total templates)")
        return result
    }

    // Carry-over buffer for tail samples from previous audio block
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

        var bestRejection: ClassificationResult? = null

        // Process ALL completed segments (second segment in same block was previously dropped)
        for (rawSegment in completedSegments) {
            Log.d(TAG, "VAD completed segment: ${rawSegment.size} samples (${rawSegment.size / 16000.0}s)")

            // ── Step 2: Silence trim to match enrollment pre-processing ──
            // ⚠️ PARITY: This is the same trimSilence() call used during enrollment in VoxaViewModel
            val trimmedSegment = AudioFileHelper.trimSilence(rawSegment)
            if (!AudioFileHelper.isDurationValid(trimmedSegment)) {
                Log.d(TAG, "Segment too short after trimming (${trimmedSegment.size} samples, ${trimmedSegment.size / 16.0}ms) — skipping")
                continue
            }

            // ── Step 3: MFCC extraction (40-dim features for DTW) ──
            // ⚠️ PARITY: Same MfccExtractor() with default MfccConfig() as enrollment
            val mfccFeatures = mfccExtractor.extract(trimmedSegment)
            if (mfccFeatures.isEmpty()) {
                Log.d(TAG, "MFCC extraction produced empty features — skipping")
                continue
            }

            // ── Step 4: Check enrolled templates ──
            if (parsedTemplates.isEmpty()) {
                return ClassificationResult(
                    isMatch = false, intentName = null, outputPhrase = null,
                    audioAssetPath = null, confidence = 0f,
                    reason = "No enrolled templates available"
                )
            }

            // ── Step 5: DTW consensus matching ──
            val dtwResults = DtwMatcher.consensusMatch(mfccFeatures, parsedTemplates)
            if (dtwResults.isEmpty()) {
                continue
            }

            // Log all DTW distances for debugging
            for (r in dtwResults) {
                Log.d(TAG, "  ${r.intentName}: DTW distance=${String.format("%.3f", r.distance)}")
            }

            // ── Step 6: MarginGate evaluation ──
            val candidates = dtwResults.map { result ->
                MarginGate.CandidateMatch(result.intentName, result.distance.toFloat())
            }

            val gateResult = MarginGate.evaluate(
                candidates = candidates,
                absoluteThreshold = ABSOLUTE_THRESHOLD,
                marginThreshold = MARGIN_THRESHOLD
            )

            // Look up the matched intent's output phrase and audio path
            val matchedIntent = enrolledIntents.find { it.intentName == gateResult.matchedWord }
            val bestDistance = dtwResults.firstOrNull()?.distance ?: Double.POSITIVE_INFINITY

            // Map distance [0.0, ABSOLUTE_THRESHOLD] to user-friendly confidence [1.0, 0.60]
            // Only return confidence > 0 for a confirmed match
            val confidence = if (gateResult.isMatch && bestDistance < ABSOLUTE_THRESHOLD) {
                val ratio = (bestDistance / ABSOLUTE_THRESHOLD).toFloat()
                (1.0f - ratio * 0.40f).coerceIn(0.60f, 1.0f)
            } else 0f

            val result = ClassificationResult(
                isMatch = gateResult.isMatch,
                intentName = gateResult.matchedWord,
                outputPhrase = matchedIntent?.outputPhrase,
                audioAssetPath = matchedIntent?.audioAssetPath,
                confidence = confidence,
                reason = gateResult.reason
            )

            if (result.isMatch) {
                return result
            }
            if (bestRejection == null || result.confidence > bestRejection.confidence) {
                bestRejection = result
            }
        }

        return bestRejection // Report the strongest rejection only after all segments are checked
    }
}
