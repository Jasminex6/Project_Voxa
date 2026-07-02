package com.example.voxa.ai

import android.content.Context
import com.example.voxa.utils.SafeLog as Log
import com.example.voxa.utils.TFLiteModelLoader
import org.tensorflow.lite.Interpreter
import kotlin.math.sqrt

/**
 * 🧠 YamnetEncoder — YAMNet-based Audio Feature Extractor
 *
 * Loads the frozen YAMNet TFLite model from assets and extracts a 2048-D temporal-halved
 * feature vector from a 1.44-second (23040 sample) audio window.
 *
 * Pipeline position:  VAD → [Tile/Crop to 1.44s] → [YamnetEncoder] → Prototypical Matcher
 *
 * Architecture v4 specifies:
 *   - Input: 23040 float32 samples (1.44s at 16kHz)
 *   - YAMNet outputs 2 frames of 1024-D embeddings → shape [2, 1024]
 *   - Concatenate frame[0] ++ frame[1] → 2048-D vector
 *   - L2-normalize the concatenated vector
 *
 * Reference: proposed_arch_v4.md (Layer 2: Neural Feature Extraction)
 */
class YamnetEncoder(context: Context, modelName: String = "yamnet.tflite") {

    companion object {
        private const val TAG = "YamnetEncoder"

        /** Target number of samples for a 1.44s window at 16kHz */
        const val TARGET_SAMPLES = 23_040

        /** Each YAMNet frame produces a 1024-D embedding */
        private const val EMBEDDING_DIM = 1024

        /** We expect exactly 2 frames for a 1.44s input → 2048-D output */
        const val OUTPUT_DIM = 2048

        /**
         * Prepares a raw PCM speech segment for YAMNet by tiling (if short) or center-cropping
         * (if long) to exactly [TARGET_SAMPLES] float32 samples.
         *
         * This mirrors the `load_and_vad_pad` function from voxa.ipynb:
         *   - Normalize Int16 → Float32 [-1.0, 1.0]
         *   - If shorter than target: repeat-tile, then center-crop
         *   - If longer than target: center-crop
         *
         * @param pcmInt16 Raw 16-bit PCM speech segment at 16kHz
         * @return FloatArray of exactly [TARGET_SAMPLES] values in [-1.0, 1.0]
         */
        fun prepareAudioWindow(pcmInt16: ShortArray): FloatArray {
            // Normalize to float [-1.0, 1.0]
            val floats = FloatArray(pcmInt16.size) { pcmInt16[it].toFloat() / 32768.0f }

            val target = TARGET_SAMPLES
            val result: FloatArray

            if (floats.size >= target) {
                // Center-crop: take the middle `target` samples
                val start = (floats.size - target) / 2
                result = floats.copyOfRange(start, start + target)
            } else {
                // Repeat-tile until >= target, then center-crop
                val repeats = (target + floats.size - 1) / floats.size // ceil division
                val tiled = FloatArray(floats.size * repeats)
                for (r in 0 until repeats) {
                    System.arraycopy(floats, 0, tiled, r * floats.size, floats.size)
                }
                val start = (tiled.size - target) / 2
                result = tiled.copyOfRange(start, start + target)
            }

            return result
        }

        /**
         * L2-normalizes a float vector in-place and returns it.
         * After normalization, cosine similarity simplifies to the dot product.
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

            // Resize input tensor to accept exactly TARGET_SAMPLES floats
            interpreter?.resizeInput(0, intArrayOf(TARGET_SAMPLES))
            interpreter?.allocateTensors()

            Log.d(TAG, "YAMNet interpreter loaded. Input shape: ${interpreter?.getInputTensor(0)?.shape()?.contentToString()}")

            // Log output tensor shapes for debugging
            val numOutputs = interpreter?.outputTensorCount ?: 0
            for (i in 0 until numOutputs) {
                val shape = interpreter?.getOutputTensor(i)?.shape()
                Log.d(TAG, "Output[$i] shape: ${shape?.contentToString()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize YAMNet interpreter: ${e.message}", e)
        }
    }

    /**
     * Returns true if the TFLite interpreter is loaded and ready.
     */
    fun isValid(): Boolean = interpreter != null

    /**
     * Extracts a 2048-D L2-normalized embedding vector from a pre-prepared audio window.
     *
     * @param audioWindow FloatArray of exactly [TARGET_SAMPLES] values (call [prepareAudioWindow] first)
     * @return L2-normalized 2048-D FloatArray
     * @throws IllegalStateException if the interpreter is not loaded
     * @throws IllegalArgumentException if audio window size is wrong
     */
    fun extractEmbedding(audioWindow: FloatArray): FloatArray {
        val interp = interpreter
            ?: throw IllegalStateException("YAMNet interpreter is not loaded")

        require(audioWindow.size == TARGET_SAMPLES) {
            "Audio window must be exactly $TARGET_SAMPLES samples, got ${audioWindow.size}"
        }

        // YAMNet has 3 output tensors:
        //   [0] scores  — shape [N, 521]    (AudioSet class scores per frame)
        //   [1] embeddings — shape [N, 1024] (the feature vectors we need)
        //   [2] spectrogram — shape [M, 64]  (log-mel spectrogram)
        //
        // With 23040 input samples (1.44s), N=2 frames.

        // Query output shapes dynamically to prevent shape mismatch exceptions on static models
        val scoresShape = interp.getOutputTensor(0).shape()
        val embShape = interp.getOutputTensor(1).shape()
        val specShape = interp.getOutputTensor(2).shape()

        val numFrames = if (embShape.isNotEmpty()) embShape[0] else 2
        val scoresDim = if (scoresShape.size > 1) scoresShape[1] else 521
        val embDim = if (embShape.size > 1) embShape[1] else EMBEDDING_DIM
        val specFrames = if (specShape.isNotEmpty()) specShape[0] else 141
        val specDim = if (specShape.size > 1) specShape[1] else 64

        // Allocate output containers based on dynamic shapes
        val scores = Array(numFrames) { FloatArray(scoresDim) }
        val embeddings = Array(numFrames) { FloatArray(embDim) }
        val spectrogram = Array(specFrames) { FloatArray(specDim) }

        val outputs = HashMap<Int, Any>()
        outputs[0] = scores
        outputs[1] = embeddings
        outputs[2] = spectrogram

        // Run inference with a flat 1-D input (no batch dimension for YAMNet)
        interp.runForMultipleInputsOutputs(arrayOf(audioWindow), outputs)

        // Temporal Halving / Padding: Concatenate emb[0] (1024-D) ++ emb[1] (1024-D) = 2048-D
        val combined = FloatArray(OUTPUT_DIM)
        if (numFrames >= 2) {
            System.arraycopy(embeddings[0], 0, combined, 0, EMBEDDING_DIM)
            System.arraycopy(embeddings[1], 0, combined, EMBEDDING_DIM, EMBEDDING_DIM)
        } else if (numFrames == 1) {
            System.arraycopy(embeddings[0], 0, combined, 0, EMBEDDING_DIM)
            // The remaining 1024 elements remain 0.0f (zero-padded, matching voxa.ipynb fallback)
        }

        // L2-normalize for cosine similarity via dot product
        return l2Normalize(combined)
    }

    /**
     * Convenience method: takes raw PCM, prepares the window, and extracts the embedding.
     *
     * @param pcmInt16 Raw 16-bit PCM speech segment at 16kHz (any length ≥ 200ms)
     * @return L2-normalized 2048-D FloatArray
     */
    fun extractFromPcm(pcmInt16: ShortArray): FloatArray {
        val window = prepareAudioWindow(pcmInt16)
        return extractEmbedding(window)
    }

    /**
     * Releases the interpreter resources.
     */
    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
