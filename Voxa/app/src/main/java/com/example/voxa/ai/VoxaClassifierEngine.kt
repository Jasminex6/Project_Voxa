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
 *   6. Cooldown → Suppress re-triggering for 1.5s after any classification result
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

        // COOLDOWN_MS: After any classification result (match OR reject), suppress new results
        // for this duration. Prevents echo/reverb from the same utterance re-triggering the VAD
        // and producing ghost matches on a different word.
        //
        // Why 3500ms: A typical child vocalization is 300–800ms, plus TTS playback of the
        // translated phrase takes 1–2s. The mic picks up the speaker output and the VAD
        // re-triggers on it, producing false matches. 3500ms covers:
        //   utterance tail (~400ms) + TTS latency (~200ms) + TTS playback (~1.5s) + safety margin (~1.4s)
        private const val COOLDOWN_MS = 3500L
    }

    // Energy-based VAD for speech segment extraction (same VAD as enrollment recording)
    private val vad = VoxaVAD()

    // Kotlin-native MFCC feature extractor (40-D: 13 MFCC + 13 Δ + 13 ΔΔ + 1 Log RMS)
    private val mfccExtractor = MfccExtractor()

    // Pre-parsed MFCC template matrices from the database, grouped by intent name
    private val parsedTemplates: Map<String, List<Array<FloatArray>>> = buildTemplateMap()

    // Cooldown state: timestamp of the last classification result (match or reject)
    private var lastResultTimeMs: Long = 0L

    /**
     * Externally extends the cooldown window.
     * Called by VoxaListenerService when TTS/audio playback begins, to prevent the mic
     * from hearing the speaker output and re-classifying it as a new utterance.
     */
    fun notifyAudioPlaybackStarted() {
        lastResultTimeMs = System.currentTimeMillis()
        vad.reset()
        Log.d(TAG, "Cooldown extended — audio playback started")
    }

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
        val now = System.currentTimeMillis()

        // ── Cooldown gate ──
        // After any classification result, suppress new results for COOLDOWN_MS.
        // This prevents echo/reverb from a single utterance re-triggering the VAD
        // and producing ghost matches (e.g., saying "مياه" triggers "water" then "milk" from echo).
        //
        // During cooldown, we still feed frames to the VAD so it drains its buffers
        // and resets cleanly, but we discard any completed segments.
        if (now - lastResultTimeMs < COOLDOWN_MS) {
            // Drain VAD: feed frames but discard segments
            val frameSize = 320
            val combined: ShortArray
            if (carryOver.isNotEmpty()) {
                combined = ShortArray(carryOver.size + pcmData.size)
                System.arraycopy(carryOver, 0, combined, 0, carryOver.size)
                System.arraycopy(pcmData, 0, combined, carryOver.size, pcmData.size)
            } else {
                combined = pcmData
            }

            var offset = 0
            while (offset + frameSize <= combined.size) {
                val frame = combined.copyOfRange(offset, offset + frameSize)
                vad.processFrame(frame) // feed but ignore result
                offset += frameSize
            }
            carryOver = if (offset < combined.size) {
                combined.copyOfRange(offset, combined.size)
            } else {
                ShortArray(0)
            }

            return null // Suppressed during cooldown
        }

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

        // ── Process ONLY the FIRST completed segment ──
        // One utterance = one classification. If the VAD produces multiple segments
        // in one block, it's almost always echo/reverb from the same sound.
        // Take only the first (which contains the actual speech) and discard the rest.
        val rawSegment = completedSegments[0]
        if (completedSegments.size > 1) {
            Log.d(TAG, "VAD produced ${completedSegments.size} segments in one block — using first, discarding ${completedSegments.size - 1} echo segments")
        }

        Log.d(TAG, "VAD completed segment: ${rawSegment.size} samples (${rawSegment.size / 16000.0}s)")

        // ── Step 2: Silence trim to match enrollment pre-processing ──
        // ⚠️ PARITY: This is the same trimSilence() call used during enrollment in VoxaViewModel
        val trimmedSegment = AudioFileHelper.trimSilence(rawSegment)
        if (!AudioFileHelper.isDurationValid(trimmedSegment)) {
            Log.d(TAG, "Segment too short after trimming (${trimmedSegment.size} samples, ${trimmedSegment.size / 16.0}ms) — skipping")
            // Start cooldown even on invalid segments to prevent echo re-trigger
            lastResultTimeMs = now
            vad.reset()
            return null
        }

        // ── Step 3: MFCC extraction (40-dim features for DTW) ──
        // ⚠️ PARITY: Same MfccExtractor() with default MfccConfig() as enrollment
        val mfccFeatures = mfccExtractor.extract(trimmedSegment)
        if (mfccFeatures.isEmpty()) {
            Log.d(TAG, "MFCC extraction produced empty features — skipping")
            lastResultTimeMs = now
            vad.reset()
            return null
        }

        // ── Step 4: Check enrolled templates ──
        if (parsedTemplates.isEmpty()) {
            lastResultTimeMs = now
            vad.reset()
            return ClassificationResult(
                isMatch = false, intentName = null, outputPhrase = null,
                audioAssetPath = null, confidence = 0f,
                reason = "No enrolled templates available"
            )
        }

        // ── Step 5: DTW consensus matching ──
        val dtwResults = DtwMatcher.consensusMatch(mfccFeatures, parsedTemplates)
        if (dtwResults.isEmpty()) {
            lastResultTimeMs = now
            vad.reset()
            return null
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

        // ── Step 7: Start cooldown and reset VAD ──
        // After producing ANY result (match or reject), enter cooldown.
        // This ensures one utterance = one classification attempt, matching the enrollment
        // behavior where the user records one sample at a time.
        lastResultTimeMs = now
        vad.reset() // Force-clear VAD buffers to prevent leftover frames from forming ghost segments

        return result
    }
}
