package com.example.voxa.ai

import android.content.Context
import com.example.voxa.utils.AudioDebugUtils
import com.example.voxa.utils.SafeLog as Log
import com.example.voxa.utils.TFLiteModelLoader
import org.tensorflow.lite.Interpreter
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * 🧠 YamnetEncoder — YAMNet-based Audio Feature Extractor
 *
 * Loads the frozen YAMNet TFLite model from assets and extracts a 2048-D Temporal-Halved
 * feature vector from an audio window normalized to exactly 1.44s (23040 samples at 16kHz).
 *
 * Pipeline position:  VAD → [Trim + Pad/Crop to 1.44s] → [YamnetEncoder] → Prototypical Matcher
 */
class YamnetEncoder(private val context: Context, modelName: String = "yamnet.tflite") {

    companion object {
        private const val TAG = "YamnetEncoder"

        /** Each YAMNet frame produces a 1024-D embedding */
        private const val EMBEDDING_DIM = 1024

        /** Output is a 2048-D Temporal-Halved vector (first 2 YAMNet frames concatenated) */
        const val OUTPUT_DIM = 2048

        /** Target audio length: 1.44s at 16kHz (guarantees exactly 2 YAMNet frames) */
        private const val TARGET_SAMPLES = 23040

        /** Minimum samples for YAMNet to produce at least 1 frame (0.975s at 16kHz) */
        private const val MIN_SAMPLES = 15600

        /**
         * Prepares a raw PCM speech segment for YAMNet by converting to Float
         * and normalizing to exactly [TARGET_SAMPLES] (1.44s) via center-pad or center-crop.
         *
         * This matches the notebook's `load_and_pad()` behavior, ensuring enrollment
         * and live inference produce comparable embeddings.
         *
         * @param pcmInt16 Raw 16-bit PCM speech segment at 16kHz
         * @return FloatArray of exactly [TARGET_SAMPLES] values in [-1.0, 1.0]
         */
        fun prepareAudioWindow(pcmInt16: ShortArray): FloatArray {
            val floats = FloatArray(TARGET_SAMPLES)
            if (pcmInt16.size <= TARGET_SAMPLES) {
                // Center-pad: place audio in the middle of the 1.44s window
                val offset = (TARGET_SAMPLES - pcmInt16.size) / 2
                for (i in pcmInt16.indices) {
                    floats[offset + i] = pcmInt16[i] / 32768.0f
                }
            } else {
                // Center-crop: take the middle 1.44s from longer audio
                val start = (pcmInt16.size - TARGET_SAMPLES) / 2
                for (i in 0 until TARGET_SAMPLES) {
                    floats[i] = pcmInt16[start + i] / 32768.0f
                }
            }
            return floats
        }

        /**
         * L2-normalizes a float vector in-place and returns it.
         */
        fun l2Normalize(vector: FloatArray): FloatArray {
            var sumSq = 0.0f
            for (v in vector) sumSq += v * v
            val norm = sqrt(sumSq)
            if (norm > 1e-8f) {
                for (i in vector.indices) vector[i] /= norm
            }
            return vector
        }
    }

    private var interpreter: Interpreter? = null

    init {
        try {
            val modelBuffer = TFLiteModelLoader.loadModelFile(context, modelName)
            val options = Interpreter.Options().apply {
                numThreads = 2 // Use 2 CPU threads for inference
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "YAMNet interpreter loaded.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize YAMNet interpreter: ${e.message}", e)
        }
    }

    fun isValid(): Boolean = interpreter != null

    /**
     * Extracts a 2048-D Temporal-Halved, L2-normalized embedding vector.
     * Concatenates the first two YAMNet frame embeddings (each 1024-D).
     *
     * @param audioWindow FloatArray of exactly [TARGET_SAMPLES] values
     * @return L2-normalized 2048-D FloatArray
     */
    fun extractEmbedding(audioWindow: FloatArray): FloatArray {
        val interp = interpreter
            ?: throw IllegalStateException("YAMNet interpreter is not loaded")

        require(audioWindow.size >= MIN_SAMPLES) {
            "Audio window must be at least $MIN_SAMPLES samples, got ${audioWindow.size}"
        }

        // Resize input tensor dynamically
        interp.resizeInput(0, intArrayOf(audioWindow.size))
        interp.allocateTensors()

        val scoresShape = interp.getOutputTensor(0).shape()
        val embShape = interp.getOutputTensor(1).shape()
        val specShape = interp.getOutputTensor(2).shape()

        val numFrames = if (embShape.isNotEmpty()) embShape[0] else 2
        val scoresDim = if (scoresShape.size > 1) scoresShape[1] else 521
        val embDim = if (embShape.size > 1) embShape[1] else EMBEDDING_DIM
        val specFrames = if (specShape.isNotEmpty()) specShape[0] else 141
        val specDim = if (specShape.size > 1) specShape[1] else 64

        val scores = Array(numFrames) { FloatArray(scoresDim) }
        val embeddings = Array(numFrames) { FloatArray(embDim) }
        val spectrogram = Array(specFrames) { FloatArray(specDim) }

        val outputs = HashMap<Int, Any>()
        outputs[0] = scores
        outputs[1] = embeddings
        outputs[2] = spectrogram

        interp.runForMultipleInputsOutputs(arrayOf(audioWindow), outputs)

        // Temporal Halving: concatenate first 2 YAMNet frames into a 2048-D vector
        // This matches the notebook's extract_2048d() function
        val temporalHalved = FloatArray(OUTPUT_DIM)
        // Frame 0 → positions [0, 1024)
        if (numFrames > 0) {
            for (j in 0 until EMBEDDING_DIM) {
                temporalHalved[j] = embeddings[0][j]
            }
        }
        // Frame 1 → positions [1024, 2048)
        if (numFrames > 1) {
            for (j in 0 until EMBEDDING_DIM) {
                temporalHalved[EMBEDDING_DIM + j] = embeddings[1][j]
            }
        }

        return l2Normalize(temporalHalved)
    }

    /**
     * Convenience method: prepares the window, saves debug wav, and extracts the embedding.
     */
    fun extractFromPcm(pcmInt16: ShortArray): FloatArray {
        android.util.Log.e("VoxaDebug", "YamnetEncoder.extractFromPcm called! pcm size: ${pcmInt16.size}")
        try {
            val window = prepareAudioWindow(pcmInt16)
            AudioDebugUtils.saveDebugWav(window, context, "debug_audio")
            android.util.Log.e("VoxaDebug", "saveDebugWav finished, about to extractEmbedding")
            return extractEmbedding(window)
        } catch (e: Throwable) {
            android.util.Log.e("VoxaDebug", "CRASH in extractFromPcm: ${e.javaClass.simpleName} - ${e.message}", e)
            throw e
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
