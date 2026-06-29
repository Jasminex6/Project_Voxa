package com.example.voxa.ai

import kotlin.math.sqrt
import kotlin.math.min

/**
 * 📐 DtwMatcher — Dynamic Time Warping Distance & Consensus Matching
 *
 * Calculates the optimal alignment distance between two time series of 40-dimensional
 * feature vectors using dynamic programming. Uses a memory-optimized flat 1D cost matrix
 * to minimize GC pressure on the audio recording thread.
 *
 * Ported from: notebooks/dtw_matcher.py + notebooks/dtw_kotlin_pseudocode.md (Dev A)
 */
object DtwMatcher {

    data class MatchResult(
        val intentName: String,
        val distance: Double
    )

    /**
     * Calculates normalized Euclidean DTW distance between two feature matrices.
     *
     * @param X Template feature matrix [N x 40]
     * @param Y Test feature matrix [M x 40]
     * @param bandWidth Optional Sakoe-Chiba band constraint
     * @return Normalized DTW distance (lower = more similar)
     */
    fun dtwDistance(X: Array<FloatArray>, Y: Array<FloatArray>, bandWidth: Int? = null): Double {
        val N = X.size
        val M = Y.size
        if (N == 0 || M == 0) return Double.POSITIVE_INFINITY

        val numCols = M + 1
        // Flat 1D cost matrix initialized to infinity
        val D = DoubleArray((N + 1) * numCols) { Double.POSITIVE_INFINITY }

        // Base case: D[0][0] = 0
        D[0] = 0.0

        for (i in 1..N) {
            var jStart = 1
            var jEnd = M
            if (bandWidth != null) {
                jStart = maxOf(1, i - bandWidth)
                jEnd = minOf(M, i + bandWidth)
            }

            val iRowOffset = i * numCols
            val prevRowOffset = (i - 1) * numCols
            val xVec = X[i - 1]

            for (j in jStart..jEnd) {
                val yVec = Y[j - 1]

                // Euclidean distance between 40-dim feature vectors
                var sum = 0.0
                for (d in xVec.indices) {
                    val diff = (xVec[d] - yVec[d]).toDouble()
                    sum += diff * diff
                }
                val cost = sqrt(sum)

                // Recurrence: D[i][j] = cost + min(diagonal, left, up)
                val dDiagonal = D[prevRowOffset + (j - 1)]
                val dLeft = D[iRowOffset + (j - 1)]
                val dUp = D[prevRowOffset + j]

                D[iRowOffset + j] = cost + min(dDiagonal, min(dLeft, dUp))
            }
        }

        val finalCost = D[N * numCols + M]
        if (finalCost == Double.POSITIVE_INFINITY) {
            return Double.POSITIVE_INFINITY
        }

        // Backtrace to count path length for normalization
        var pathLength = 0
        var currI = N
        var currJ = M

        while (currI > 0 || currJ > 0) {
            pathLength++
            if (currI == 0) {
                currJ--
            } else if (currJ == 0) {
                currI--
            } else {
                val idxDiag = (currI - 1) * numCols + (currJ - 1)
                val idxUp = (currI - 1) * numCols + currJ
                val idxLeft = currI * numCols + (currJ - 1)

                val valDiag = D[idxDiag]
                val valUp = D[idxUp]
                val valLeft = D[idxLeft]

                if (valDiag <= valUp && valDiag <= valLeft) {
                    currI--; currJ--
                } else if (valUp <= valLeft) {
                    currI--
                } else {
                    currJ--
                }
            }
        }

        return finalCost / maxOf(pathLength, 1)
    }

    /**
     * Sweeps all enrolled intent templates and finds the closest match using top-k=2 consensus.
     *
     * @param testFeatures 40-dim features from the incoming audio segment
     * @param intentTemplates Map of intent name → list of enrolled template matrices
     * @param topK Number of closest templates to average (default 2)
     * @return Sorted list of MatchResult (ascending by distance)
     */
    fun consensusMatch(
        testFeatures: Array<FloatArray>,
        intentTemplates: Map<String, List<Array<FloatArray>>>,
        topK: Int = 2,
        bandWidth: Int? = null
    ): List<MatchResult> {
        val results = mutableListOf<MatchResult>()

        for ((intentName, templates) in intentTemplates) {
            if (templates.isEmpty()) {
                results.add(MatchResult(intentName, Double.POSITIVE_INFINITY))
                continue
            }

            val distances = mutableListOf<Double>()
            for (template in templates) {
                distances.add(dtwDistance(template, testFeatures, bandWidth))
            }

            distances.sort()

            val kToUse = minOf(topK, distances.size)
            val sum = distances.take(kToUse).sum()
            val avgDist = if (kToUse > 0) sum / kToUse else Double.POSITIVE_INFINITY

            results.add(MatchResult(intentName, avgDist))
        }

        return results.sortedBy { it.distance }
    }
}
