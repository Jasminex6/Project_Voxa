package com.example.voxa.ai

import android.content.Context
import com.example.voxa.utils.TFLiteModelLoader
import org.tensorflow.lite.Interpreter
import kotlin.math.sqrt

/**
 * 🎙️ SpeakerVerifier
 * Handles loading the speaker verification embedding model and performing cosine similarity checks.
 */
class SpeakerVerifier(context: Context, modelName: String = "ecapa_speaker_id.tflite") {

    private var interpreter: Interpreter? = null

    init {
        try {
            // 1. Memory-map the TFLite model from the assets directory.
            val modelBuffer = TFLiteModelLoader.loadModelFile(context, modelName)
            
            // 2. Initialize the TensorFlow Lite interpreter with the mapped buffer.
            interpreter = Interpreter(modelBuffer)
        } catch (e: Exception) {
            // Catch and log file access issues (useful if running in host tests where context is not real).
            e.printStackTrace()
        }
    }

    /**
     * Extracts a 192-dimensional speaker embedding vector from a processed audio features matrix.
     */
    fun extractEmbedding(audioFeatures: Array<FloatArray>): FloatArray {
        // TFLite run() requires inputs and outputs to be pre-allocated multi-dimensional arrays.
        val input = arrayOf(audioFeatures)
        val output = Array(1) { FloatArray(192) } // Model outputs 192 features (embedding vector)
        
        // Execute the neural network model on the CPU
        interpreter?.run(input, output)
        
        // Return the first batch output containing our speaker features vector
        return output[0]
    }

    companion object {
        /**
         * Computes the Cosine Similarity between candidate vector A and template vector B.
         * Formula: Similarity = (A . B) / (||A|| * ||B||)
         */
        fun computeCosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
            var dotProduct = 0.0f
            var normA = 0.0f
            var normB = 0.0f
            
            // Loop through each coordinate of the 192-dimension space.
            for (i in vectorA.indices) {
                dotProduct += vectorA[i] * vectorB[i]  // Sum of products
                normA += vectorA[i] * vectorA[i]      // Sum of squares for Vector A
                normB += vectorB[i] * vectorB[i]      // Sum of squares for Vector B
            }
            
            // Avoid dividing by zero: return similarity only if both vectors are non-empty.
            return if (normA > 0.0f && normB > 0.0f) {
                dotProduct / (sqrt(normA) * sqrt(normB))
            } else {
                0.0f
            }
        }
    }
}
