package com.example.voxa.ai

import android.content.Context
import android.util.Log
import com.example.voxa.data.ChildProfile
import com.example.voxa.data.EnrolledIntent
import com.example.voxa.data.AcousticTemplate
import com.example.voxa.logic.MarginGate
import com.example.voxa.utils.AudioFileHelper
import java.io.File
import kotlin.math.sqrt

/**
 * 🧠 VoxaClassifierEngine — Full Pipeline Orchestrator
 *
 * Coordinates the complete audio classification pipeline:
 *   1. VAD → Extract speech segments from continuous audio
 *   2. Speaker Verification → ECAPA-TDNN checks if it's the enrolled child
 *   3. MFCC Extraction → 40-dim feature vectors from speech
 *   4. DTW Matching → Compare against enrolled templates
 *   5. Margin Gate → Validate match confidence
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
        // Analogy: ABSOLUTE_THRESHOLD is the maximum distance budget. If the DTW distance exceeds this,
        // we reject the sound. We set it to 7.80f (up from 6.50f) to be more forgiving of child speech variations 
        // and mispronunciations (like saying "ءااء" instead of "سماء").
        private const val ABSOLUTE_THRESHOLD = 7.80f
        
        // Analogy: MARGIN_THRESHOLD prevents false positives. If two candidate words have very close distances,
        // it marks it as "Ambiguous" to avoid choosing the wrong one. We lower it to 0.15f (down from 0.32f) 
        // to be less strict and choose the best candidate more decisively unless they are extremely close.
        private const val MARGIN_THRESHOLD = 0.15f
        private const val SPEAKER_THRESHOLD = 0.25f
    }

    private val vad = VoxaVAD()
    private val mfccExtractor = MfccExtractor()
    private val speakerVerifier: SpeakerVerifier = SpeakerVerifier(context)

    // Pre-parse the enrolled speaker embedding
    private val enrolledEmbedding: FloatArray? = parseEmbedding(activeProfile?.speakerEmbedding)

    // Pre-parse MFCC templates from the database into feature matrices
    private val parsedTemplates: Map<String, List<Array<FloatArray>>> = buildTemplateMap()

    /**
     * Parses a JSON-serialized FloatArray from the database.
     */
    private fun parseEmbedding(json: String?): FloatArray? {
        if (json.isNullOrBlank()) return null
        return try {
            val cleaned = json.trim().removePrefix("[").removeSuffix("]")
            cleaned.split(",").map { it.trim().toFloat() }.toFloatArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse speaker embedding: ${e.message}")
            null
        }
    }

    /**
     * Parses serialized MFCC template features from AcousticTemplate.templateFeatures.
     * Each template's features are stored as a flat JSON array of floats with 40 features per frame.
     */
    private fun parseTemplateFeatures(serialized: String): Array<FloatArray>? {
        return try {
            val cleaned = serialized.trim().removePrefix("[").removeSuffix("]")
            val floats = cleaned.split(",").map { it.trim().toFloat() }
            val featureDim = 40
            val numFrames = floats.size / featureDim
            if (numFrames == 0) return null

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
     * Builds a map of intent name → list of parsed feature matrices for DTW matching.
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
        return result
    }

    override fun processAudioBlock(pcmData: ShortArray): ClassificationResult? {
        // Step 1: VAD — extract speech segments
        val segments = vad.processAudio(pcmData)
        if (segments.isEmpty()) {
            return null // No speech detected
        }

        // Process the first valid segment
        val segment = segments[0]

        // Step 2: Speaker Verification (if enrolled embedding exists and model initialized successfully)
        if (enrolledEmbedding != null) {
            if (speakerVerifier.isValid()) {
                val melFeatures = computeLogMelSpectrogram(segment)
                if (melFeatures.isEmpty()) {
                    return ClassificationResult(
                        isMatch = false, intentName = null, outputPhrase = null,
                        audioAssetPath = null, confidence = 0f,
                        reason = "Mel spectrogram computation failed"
                    )
                }

                val testEmbedding = speakerVerifier.extractEmbedding(melFeatures)
                val similarity = SpeakerVerifier.computeCosineSimilarity(testEmbedding, enrolledEmbedding)

                if (similarity < SPEAKER_THRESHOLD) {
                    Log.d(TAG, "Speaker rejected (similarity=$similarity)")
                    return ClassificationResult(
                        isMatch = false, intentName = null, outputPhrase = null,
                        audioAssetPath = null, confidence = similarity,
                        reason = "Speaker rejected (similarity=${String.format("%.2f", similarity)})",
                        speakerSimilarity = similarity
                    )
                }
                Log.d(TAG, "Speaker verified (similarity=$similarity)")
            } else {
                Log.w(TAG, "Speaker verifier model is not active — bypassing speaker check")
            }
        }

        // Step 3: MFCC extraction (40-dim features for DTW)
        val mfccFeatures = mfccExtractor.extract(segment)
        if (mfccFeatures.isEmpty()) {
            return ClassificationResult(
                isMatch = false, intentName = null, outputPhrase = null,
                audioAssetPath = null, confidence = 0f,
                reason = "MFCC extraction produced empty features"
            )
        }

        // Step 4: DTW consensus matching
        if (parsedTemplates.isEmpty()) {
            return ClassificationResult(
                isMatch = false, intentName = null, outputPhrase = null,
                audioAssetPath = null, confidence = 0f,
                reason = "No enrolled templates available"
            )
        }

        val dtwResults = DtwMatcher.consensusMatch(mfccFeatures, parsedTemplates)
        if (dtwResults.isEmpty()) {
            return ClassificationResult(
                isMatch = false, intentName = null, outputPhrase = null,
                audioAssetPath = null, confidence = 0f,
                reason = "DTW matching returned no results"
            )
        }

        // Step 5: Margin Gate evaluation
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
        // Map distance [0.0, ABSOLUTE_THRESHOLD] to user-friendly confidence [1.0, 0.60] (60% to 100%)
        // Only return confidence > 0 for a confirmed match; otherwise return 0f to prevent misleading high confidence in ambiguity/rejections.
        val confidence = if (gateResult.isMatch && bestDistance < ABSOLUTE_THRESHOLD) {
            val ratio = (bestDistance / ABSOLUTE_THRESHOLD).toFloat()
            (1.0f - ratio * 0.40f).coerceIn(0.60f, 1.0f)
        } else 0f

        return ClassificationResult(
            isMatch = gateResult.isMatch,
            intentName = gateResult.matchedWord,
            outputPhrase = matchedIntent?.outputPhrase,
            audioAssetPath = matchedIntent?.audioAssetPath,
            confidence = confidence,
            reason = gateResult.reason
        )
    }

    /**
     * Computes 80-band Log-Mel Spectrogram specifically for ECAPA-TDNN speaker verification.
     * This uses DIFFERENT parameters than the MFCC pipeline (80 mels, Hann window, n_fft=400).
     */
    private fun computeLogMelSpectrogram(pcmInt16: ShortArray): Array<FloatArray> {
        val sampleRate = 16000
        val nFft = 400
        val hopLength = 160
        val nMels = 80
        val fMin = 0.0
        val fMax = 8000.0

        // Normalize to float
        val pcmSamples = FloatArray(pcmInt16.size) { pcmInt16[it].toFloat() / 32768.0f }

        // Pre-emphasis
        val emphasized = FloatArray(pcmSamples.size)
        emphasized[0] = pcmSamples[0]
        for (i in 1 until pcmSamples.size) {
            emphasized[i] = pcmSamples[i] - 0.97f * pcmSamples[i - 1]
        }

        // Frame count
        val numFrames = (emphasized.size - nFft) / hopLength + 1
        if (numFrames <= 0) return emptyArray()

        // Hann window
        val hannWindow = FloatArray(nFft) { i ->
            (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (nFft - 1)))).toFloat()
        }

        // Mel filterbank [nMels x (nFft/2 + 1)]
        val melFilterbank = buildEcapaMelFilterbank(nMels, nFft, sampleRate, fMin, fMax)

        // Process each frame
        val melSpectrogram = Array(numFrames) { FloatArray(nMels) }
        for (t in 0 until numFrames) {
            val start = t * hopLength
            val frame = FloatArray(nFft) { i -> emphasized[start + i] * hannWindow[i] }

            // Simple power spectrum via DFT for the smaller n_fft=400
            val powerSpec = computePowerSpectrumFloat(frame, nFft)

            for (m in 0 until nMels) {
                var energy = 0.0f
                for (k in powerSpec.indices) {
                    energy += melFilterbank[m][k] * powerSpec[k]
                }
                melSpectrogram[t][m] = Math.log(Math.max(energy.toDouble(), 1e-10)).toFloat()
            }
        }

        // Per-utterance mean normalization (mean only, NOT variance)
        val means = FloatArray(nMels)
        for (m in 0 until nMels) {
            var sum = 0.0f
            for (t in 0 until numFrames) sum += melSpectrogram[t][m]
            means[m] = sum / numFrames
        }
        for (t in 0 until numFrames) {
            for (m in 0 until nMels) {
                melSpectrogram[t][m] -= means[m]
            }
        }

        return melSpectrogram
    }

    /**
     * Computes power spectrum for ECAPA-TDNN (nFft=400, zero-padded to 512 for FFT).
     */
    private fun computePowerSpectrumFloat(frame: FloatArray, nFft: Int): FloatArray {
        // Zero-pad to next power of 2 for FFT
        val fftSize = 512
        val re = DoubleArray(fftSize)
        val im = DoubleArray(fftSize)
        for (i in frame.indices) {
            re[i] = frame[i].toDouble()
        }

        MfccExtractor.fftInPlace(re, im)

        val nBins = nFft / 2 + 1
        val power = FloatArray(nBins)
        for (k in 0 until nBins) {
            power[k] = ((re[k] * re[k] + im[k] * im[k]) / nFft.toDouble()).toFloat()
        }
        return power
    }

    /**
     * Builds the mel filterbank specifically for ECAPA-TDNN (80 mels, fMin=0Hz).
     */
    private fun buildEcapaMelFilterbank(
        nMels: Int, nFft: Int, sampleRate: Int,
        fMin: Double, fMax: Double
    ): Array<FloatArray> {
        val nBins = nFft / 2 + 1

        fun hzToMel(hz: Double): Double = 2595.0 * Math.log10(1.0 + hz / 700.0)
        fun melToHz(mel: Double): Double = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)

        val melMin = hzToMel(fMin)
        val melMax = hzToMel(fMax)
        val melPoints = DoubleArray(nMels + 2) { i ->
            melMin + i * (melMax - melMin) / (nMels + 1)
        }
        val hzPoints = melPoints.map { melToHz(it) }
        val binPoints = hzPoints.map { (it * nFft / sampleRate).toInt() }

        val filterbank = Array(nMels) { FloatArray(nBins) }
        for (m in 0 until nMels) {
            val left = binPoints[m]
            val center = binPoints[m + 1]
            val right = binPoints[m + 2]

            for (k in left until center) {
                if (k in 0 until nBins && center > left) {
                    filterbank[m][k] = (k - left).toFloat() / (center - left)
                }
            }
            for (k in center until right) {
                if (k in 0 until nBins && right > center) {
                    filterbank[m][k] = (right - k).toFloat() / (right - center)
                }
            }
        }
        return filterbank
    }
}
