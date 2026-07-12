package com.example.voxa.ai

import com.example.voxa.utils.SafeLog as Log
import kotlin.math.sqrt

/**
 * 🎯 PrototypicalMatcher — Centroid-Based Sound Intent Matching
 *
 * Implements the mathematical core of the Architecture v4 prototypical matching pipeline:
 *   1. QC Outlier Rejection (μ + 2σ rule)
 *   2. K-Means (K=2) Bifurcation for high-variance intents
 *   3. Centroid computation (mean of L2-normalized vectors)
 *   4. Cosine similarity scoring with Centroid Count Penalty (CCP)
 *   5. OOD Gate and Margin Gate evaluation
 *
 * All vectors are assumed to be L2-normalized (via YamnetEncoder), so cosine
 * similarity simplifies to the dot product.
 *
 * Reference: proposed_arch_v4.md (Layers 3-5) and voxa.ipynb
 */
object PrototypicalMatcher {

    private const val TAG = "ProtoMatcher"

    // ── THRESHOLDS (from proposed_arch_v4.md) ──

    /** Centroid Count Penalty: subtracted per extra centroid beyond 1 */
    const val CCP_PENALTY = 0.05f

    /** Margin Gate: minimum separation between best and second-best similarity */
    const val MARGIN_THRESHOLD = 0.04f

    /** Default OOD threshold if not stored per-intent */
    const val DEFAULT_OOD_THRESHOLD = 0.85f

    /** Bifurcation trigger: max pairwise distance exceeding this fraction of OOD threshold */
    const val BIFURCATION_DISTANCE_FACTOR = 0.5f

    /** Minimum valid embeddings required after QC for bifurcation */
    const val MIN_SAMPLES_FOR_BIFURCATION = 12

    /** K-Means iteration limit */
    private const val KMEANS_MAX_ITER = 30

    /** Minimum σ floor for OOD calibration (prevents threshold near 1.0 with very consistent enrollment) */
    private const val MIN_SIGMA_FLOOR = 0.08f

    /** Minimum OOD threshold floor — lowered from 0.55 after software RMS normalization shifted cosine ranges */
    private const val MIN_OOD_FLOOR = 0.35f

    /** Maximum OOD threshold ceiling */
    private const val MAX_OOD_CEILING = 0.90f
    // ═══════════════════════════════════════════════════════════
    // SECTION 1: SIMILARITY & DISTANCE
    // ═══════════════════════════════════════════════════════════

    /**
     * Computes cosine similarity between two L2-normalized vectors.
     * Since both vectors are unit-length, this is simply the dot product.
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vector dimensions must match: ${a.size} vs ${b.size}" }
        var dot = 0.0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }

    /**
     * Computes the cosine distance (1 - similarity) between two L2-normalized vectors.
     */
    fun cosineDistance(a: FloatArray, b: FloatArray): Float {
        return 1.0f - cosineSimilarity(a, b)
    }

    // ═══════════════════════════════════════════════════════════
    // SECTION 2: QC OUTLIER REJECTION
    // ═══════════════════════════════════════════════════════════

    /**
     * Performs Quality Control (QC) outlier rejection on a set of embedding vectors.
     *
     * Algorithm:
     *   1. Compute the mean (centroid) of all vectors.
     *   2. Compute cosine distance of each vector to the centroid.
     *   3. Compute μ and σ of these distances.
     *   4. Reject any vector whose distance > μ + 2σ.
     *
     * @param embeddings List of L2-normalized embedding vectors
     * @return List of embeddings that pass QC (outliers removed)
     */
    fun qcOutlierRejection(embeddings: List<FloatArray>): List<FloatArray> {
        if (embeddings.size < 3) return embeddings // Not enough data for statistics

        val dim = embeddings[0].size
        val centroid = computeCentroid(embeddings, dim)

        // Compute distances to centroid
        val distances = embeddings.map { cosineDistance(it, centroid) }

        // Compute μ and σ of distances
        val mean = distances.average().toFloat()
        val variance = distances.map { (it - mean) * (it - mean) }.average().toFloat()
        val stdDev = sqrt(variance)

        val threshold = mean + 2.0f * stdDev
        val accepted = mutableListOf<FloatArray>()
        var rejectedCount = 0

        for (i in embeddings.indices) {
            if (distances[i] <= threshold) {
                accepted.add(embeddings[i])
            } else {
                rejectedCount++
                Log.d(TAG, "QC rejected sample $i: distance=${distances[i]}, threshold=$threshold")
            }
        }

        Log.d(TAG, "QC: ${accepted.size}/${embeddings.size} accepted, $rejectedCount rejected (μ=$mean, σ=$stdDev, threshold=$threshold)")
        return accepted
    }

    // ═══════════════════════════════════════════════════════════
    // SECTION 3: K-MEANS BIFURCATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Runs K=2 K-Means clustering on L2-normalized embeddings.
     *
     * @param embeddings List of L2-normalized vectors
     * @return Pair of two L2-normalized centroids
     */
    fun kMeans2(embeddings: List<FloatArray>): Pair<FloatArray, FloatArray> {
        require(embeddings.size >= 2) { "Need at least 2 embeddings for K=2 clustering" }
        val dim = embeddings[0].size

        // Initialize centroids: pick the two most distant vectors
        var maxDist = -1.0f
        var initA = 0
        var initB = 1
        for (i in embeddings.indices) {
            for (j in i + 1 until embeddings.size) {
                val d = cosineDistance(embeddings[i], embeddings[j])
                if (d > maxDist) {
                    maxDist = d
                    initA = i
                    initB = j
                }
            }
        }

        var centroidA = embeddings[initA].copyOf()
        var centroidB = embeddings[initB].copyOf()

        for (iter in 0 until KMEANS_MAX_ITER) {
            // Assign each embedding to the nearest centroid
            val clusterA = mutableListOf<FloatArray>()
            val clusterB = mutableListOf<FloatArray>()

            for (emb in embeddings) {
                val distA = cosineDistance(emb, centroidA)
                val distB = cosineDistance(emb, centroidB)
                if (distA <= distB) clusterA.add(emb) else clusterB.add(emb)
            }

            // Handle empty clusters: reset to farthest point in the other cluster
            if (clusterA.isEmpty()) {
                clusterA.add(clusterB.maxByOrNull { cosineDistance(it, centroidB) }!!)
                clusterB.remove(clusterA[0])
            }
            if (clusterB.isEmpty()) {
                clusterB.add(clusterA.maxByOrNull { cosineDistance(it, centroidA) }!!)
                clusterA.remove(clusterB[0])
            }

            // Recompute centroids
            val newA = computeCentroid(clusterA, dim)
            val newB = computeCentroid(clusterB, dim)

            // Check convergence (centroids didn't move)
            val shiftA = cosineDistance(centroidA, newA)
            val shiftB = cosineDistance(centroidB, newB)

            centroidA = newA
            centroidB = newB

            if (shiftA < 1e-6f && shiftB < 1e-6f) {
                Log.d(TAG, "K-Means converged after ${iter + 1} iterations")
                break
            }
        }

        return Pair(centroidA, centroidB)
    }

    /**
     * Computes the maximum pairwise cosine distance among a set of embeddings.
     * Used to determine whether bifurcation should be triggered.
     */
    fun maxPairwiseDistance(embeddings: List<FloatArray>): Float {
        var maxDist = 0.0f
        for (i in embeddings.indices) {
            for (j in i + 1 until embeddings.size) {
                val d = cosineDistance(embeddings[i], embeddings[j])
                if (d > maxDist) maxDist = d
            }
        }
        return maxDist
    }

    // ═══════════════════════════════════════════════════════════
    // SECTION 4: ENROLLMENT — BIFURCATION & CENTROID EXTRACTION
    // ═══════════════════════════════════════════════════════════

    /**
     * Result of the enrollment centroid extraction process.
     */
    data class EnrollmentResult(
        /** 1 or 2 centroids (L2-normalized 2048-D vectors) */
        val centroids: List<FloatArray>,
        /** Whether K-Means bifurcation was triggered */
        val bifurcated: Boolean,
        /** Per-intent OOD threshold: mean similarity minus 2σ (floored at 0.50) */
        val oodThreshold: Float,
        /** Number of samples that passed QC */
        val validCount: Int,
        /** Number of samples rejected by QC */
        val rejectedCount: Int
    )

    /**
     * Orchestrates the full enrollment process for a single intent:
     *   1. QC outlier rejection
     *   2. Check bifurcation trigger
     *   3. Compute 1 or 2 centroids
     *   4. Compute per-intent OOD threshold
     *
     * @param embeddings Raw L2-normalized embeddings from all enrollment recordings
     * @return EnrollmentResult containing centroids and metadata
     */
    fun computeEnrollmentCentroids(embeddings: List<FloatArray>): EnrollmentResult {
        require(embeddings.isNotEmpty()) { "At least one embedding is required for enrollment" }

        val dim = embeddings[0].size

        // Step 1: QC Outlier Rejection
        val validEmbeddings = qcOutlierRejection(embeddings)
        val rejectedCount = embeddings.size - validEmbeddings.size

        if (validEmbeddings.isEmpty()) {
            Log.e(TAG, "All samples rejected by QC! Falling back to unfiltered embeddings.")
            return computeEnrollmentCentroids(embeddings.take(3)) // Recursive safeguard
        }

        // Step 2: Check bifurcation trigger
        val shouldBifurcate = validEmbeddings.size >= MIN_SAMPLES_FOR_BIFURCATION &&
                maxPairwiseDistance(validEmbeddings) > DEFAULT_OOD_THRESHOLD * BIFURCATION_DISTANCE_FACTOR

        // Step 3: Compute centroids
        val centroids: List<FloatArray>
        val bifurcated: Boolean

        if (shouldBifurcate) {
            Log.d(TAG, "Bifurcation triggered: running K=2 K-Means on ${validEmbeddings.size} samples")
            val (c1, c2) = kMeans2(validEmbeddings)
            centroids = listOf(c1, c2)
            bifurcated = true
        } else {
            val singleCentroid = computeCentroid(validEmbeddings, dim)
            centroids = listOf(singleCentroid)
            bifurcated = false
        }

        // Step 4: Compute per-intent OOD threshold
        // OOD threshold = mean similarity to own centroid(s) minus 2σ, floored at 0.50
        val similarities = validEmbeddings.map { emb ->
            centroids.maxOf { centroid -> cosineSimilarity(emb, centroid) }
        }
        val meanSim = similarities.average().toFloat()
        val simVariance = similarities.map { (it - meanSim) * (it - meanSim) }.average().toFloat()
        val simStdDev = sqrt(simVariance)
        // Per-intent OOD threshold: μ - 2σ of enrollment similarities, floored at 0.50
        val oodThreshold = maxOf(0.50f, meanSim - 2.0f * simStdDev)

        Log.d(TAG, "Enrollment complete: ${centroids.size} centroid(s), OOD=$oodThreshold, " +
                "bifurcated=$bifurcated, valid=${validEmbeddings.size}/${embeddings.size}")

        return EnrollmentResult(
            centroids = centroids,
            bifurcated = bifurcated,
            oodThreshold = oodThreshold,
            validCount = validEmbeddings.size,
            rejectedCount = rejectedCount
        )
    }

    // ═══════════════════════════════════════════════════════════
    // SECTION 5: RUNTIME CLASSIFICATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Result of matching a live embedding against an intent's centroids.
     */
    data class IntentScore(
        val intentName: String,
        val outputPhrase: String,
        val audioAssetPath: String,
        /** Raw max similarity to any centroid */
        val rawSimilarity: Float,
        /** Effective similarity after Centroid Count Penalty */
        val effectiveSimilarity: Float,
        /** Number of centroids for this intent */
        val centroidCount: Int,
        /** Per-intent OOD threshold */
        val oodThreshold: Float
    )

    /**
     * Scores a live embedding against a single intent's centroids.
     *
     * Applies Centroid Count Penalty (CCP):
     *   Effective_Sim = Max_Sim - (K - 1) * CCP_PENALTY
     *
     * @param liveEmbedding L2-normalized 2048-D live vector
     * @param centroids 1 or 2 L2-normalized centroids for this intent
     * @param intentName Name of the intent
     * @param outputPhrase Translation output phrase
     * @param audioAssetPath Audio file path
     * @param oodThreshold Per-intent OOD threshold
     */
    fun scoreIntent(
        liveEmbedding: FloatArray,
        centroids: List<FloatArray>,
        intentName: String,
        outputPhrase: String,
        audioAssetPath: String,
        oodThreshold: Float
    ): IntentScore {
        val maxSim = centroids.maxOf { cosineSimilarity(liveEmbedding, it) }
        val ccpPenalty = (centroids.size - 1) * CCP_PENALTY
        val effectiveSim = maxSim - ccpPenalty

        android.util.Log.e("VoxaMatch", "Intent: $intentName | Similarity: $effectiveSim (raw: $maxSim, threshold: $oodThreshold)")

        return IntentScore(
            intentName = intentName,
            outputPhrase = outputPhrase,
            audioAssetPath = audioAssetPath,
            rawSimilarity = maxSim,
            effectiveSimilarity = effectiveSim,
            centroidCount = centroids.size,
            oodThreshold = oodThreshold
        )
    }

    /**
     * Evaluates OOD Gate and Margin Gate across all scored intents.
     *
     * @param scores List of IntentScore objects (one per enrolled intent)
     * @return ClassificationResult with match details or rejection reason
     */
    fun evaluateGates(scores: List<IntentScore>): ClassificationResult {
        if (scores.isEmpty()) {
            return ClassificationResult(
                isMatch = false,
                intentName = null,
                outputPhrase = null,
                audioAssetPath = null,
                confidence = 0f,
                reason = "No enrolled intents available"
            )
        }

        // Sort by effective similarity (descending)
        val sorted = scores.sortedByDescending { it.effectiveSimilarity }
        val best = sorted[0]

        // OOD Gate: reject if the best effective similarity is below the intent's threshold
        if (best.effectiveSimilarity < best.oodThreshold) {
            return ClassificationResult(
                isMatch = false,
                intentName = best.intentName,
                outputPhrase = null,
                audioAssetPath = null,
                confidence = best.effectiveSimilarity,
                reason = "OOD rejected: similarity ${String.format("%.3f", best.effectiveSimilarity)} < threshold ${String.format("%.3f", best.oodThreshold)}"
            )
        }

        // Margin Gate: if multiple intents, check separation
        if (sorted.size >= 2) {
            val secondBest = sorted[1]
            val margin = best.effectiveSimilarity - secondBest.effectiveSimilarity

            if (margin < MARGIN_THRESHOLD) {
                return ClassificationResult(
                    isMatch = false,
                    intentName = null,
                    outputPhrase = null,
                    audioAssetPath = null,
                    confidence = best.effectiveSimilarity,
                    reason = "Ambiguous: margin ${String.format("%.3f", margin)} < ${MARGIN_THRESHOLD}. " +
                            "Best: ${best.intentName} (${String.format("%.3f", best.effectiveSimilarity)}), " +
                            "Second: ${secondBest.intentName} (${String.format("%.3f", secondBest.effectiveSimilarity)})"
                )
            }
        }

        // Both gates passed
        return ClassificationResult(
            isMatch = true,
            intentName = best.intentName,
            outputPhrase = best.outputPhrase,
            audioAssetPath = best.audioAssetPath,
            confidence = best.effectiveSimilarity,
            reason = "Valid match (OOD=${String.format("%.3f", best.effectiveSimilarity)}, " +
                    "threshold=${String.format("%.3f", best.oodThreshold)}, " +
                    "centroids=${best.centroidCount})"
        )
    }

    // ═══════════════════════════════════════════════════════════
    // INTERNAL HELPERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Computes the L2-normalized centroid (mean) of a list of L2-normalized vectors.
     */
    private fun computeCentroid(vectors: List<FloatArray>, dim: Int): FloatArray {
        val sum = FloatArray(dim)
        for (vec in vectors) {
            for (i in 0 until dim) sum[i] += vec[i]
        }
        val n = vectors.size.toFloat()
        for (i in 0 until dim) sum[i] /= n
        return YamnetEncoder.l2Normalize(sum)
    }

    // ═══════════════════════════════════════════════════════════
    // SERIALIZATION HELPERS (for Room storage)
    // ═══════════════════════════════════════════════════════════

    /**
     * Serializes a list of centroid vectors into a flat JSON string.
     * Format: "[[c0_0,c0_1,...],[c1_0,c1_1,...]]"
     * For a single centroid: "[[c0_0,c0_1,...]]"
     */
    fun serializeCentroids(centroids: List<FloatArray>): String {
        val sb = StringBuilder("[")
        for ((i, centroid) in centroids.withIndex()) {
            if (i > 0) sb.append(",")
            sb.append("[")
            sb.append(centroid.joinToString(","))
            sb.append("]")
        }
        sb.append("]")
        return sb.toString()
    }

    /**
     * Deserializes a centroid JSON string back to a list of FloatArrays.
     * Handles both the new format "[[...],[...]]" and legacy flat format "[...]".
     */
    fun deserializeCentroids(serialized: String): List<FloatArray> {
        val trimmed = serialized.trim()
        if (trimmed.isEmpty() || trimmed == "[]") return emptyList()

        return try {
            if (trimmed.startsWith("[[")) {
                // New format: nested arrays
                val inner = trimmed.removePrefix("[").removeSuffix("]")
                // Split by "],[" — careful to reassemble
                val parts = mutableListOf<String>()
                var depth = 0
                var current = StringBuilder()
                for (ch in inner) {
                    when (ch) {
                        '[' -> {
                            depth++
                            if (depth == 1) {
                                current = StringBuilder()
                            } else {
                                current.append(ch)
                            }
                        }
                        ']' -> {
                            depth--
                            if (depth == 0) {
                                parts.add(current.toString())
                            } else {
                                current.append(ch)
                            }
                        }
                        ',' -> {
                            if (depth == 0) { /* skip separator between arrays */ }
                            else current.append(ch)
                        }
                        else -> current.append(ch)
                    }
                }
                parts.map { part ->
                    part.split(",").map { it.trim().toFloat() }.toFloatArray()
                }
            } else {
                // Legacy flat format: single vector "[f1,f2,...]"
                val cleaned = trimmed.removePrefix("[").removeSuffix("]")
                val values = cleaned.split(",").map { it.trim().toFloat() }.toFloatArray()
                listOf(values)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize centroids: ${e.message}")
            emptyList()
        }
    }
}
