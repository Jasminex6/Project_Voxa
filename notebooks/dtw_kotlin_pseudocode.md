# Voxa DTW Matcher - Kotlin Port Pseudocode for Dev B

> **Author:** Dev A
> **Date:** June 16, 2026
> **Purpose:** Detailed implementation guide for porting `dtw_matcher.py` features to Kotlin.
> Dev B should use this guide to implement efficient DTW matching in the Android application.

---

## 1. Context & Complexity

Dynamic Time Warping (DTW) calculates the optimal alignment path between two time series of feature vectors.
- Inputs:
  - Template matrix $X$ of size $N \times 40$ (where $N$ is template frame count).
  - Test matrix $Y$ of size $M \times 40$ (where $M$ is incoming signal frame count).
- Time Complexity: $O(N \cdot M \cdot D)$ where $D = 40$ is the feature dimension.
- Space Complexity: $O(N \cdot M)$ for the cost matrix.

> [!TIP]
> Since this runs on mobile devices, minimizing garbage collection (GC) pressure is critical. We recommend allocating a single flat array for the cost matrix and reuse it if possible, or using 1D representation.

---

## 2. Core DTW Distance Port

### Memory-Optimized 1D Buffer representation
Instead of allocating a 2D array `DoubleArray(N + 1) { DoubleArray(M + 1) }`, use a 1D flat `DoubleArray` of size `(N + 1) * (M + 1)` and map `[i][j]` to `i * (M + 1) + j`.

```kotlin
import kotlin.math.sqrt
import kotlin.math.min

/**
 * Calculates normalized Euclidean DTW distance.
 */
fun dtwDistance(X: Array<FloatArray>, Y: Array<FloatArray>, bandWidth: Int? = null): Double {
    val N = X.size
    val M = Y.size
    if (N == 0 || M == 0) return Double.POSITIVE_INFINITY

    val numCols = M + 1
    // Cost matrix flat array initialized to Double.POSITIVE_INFINITY
    val D = DoubleArray((N + 1) * numCols) { Double.POSITIVE_INFINITY }
    
    // Base case
    D[0] = 0.0 // index [0][0] mapped to 0

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
            
            // 1. Calculate Euclidean distance between feature vectors
            var sum = 0.0
            for (d in 0 until 40) {
                val diff = xVec[d] - yVec[d]
                sum += diff * diff
            }
            val cost = sqrt(sum)

            // 2. Recurrence: D[i][j] = cost + min(D[i-1][j-1], D[i-1][j], D[i][j-1])
            val dDiagonal = D[prevRowOffset + (j - 1)]
            val dLeft = D[iRowOffset + (j - 1)]
            val dUp = D[prevRowOffset + j]

            val minPrev = min(dDiagonal, min(dLeft, dUp))
            D[iRowOffset + j] = cost + minPrev
        }
    }

    val finalCost = D[N * numCols + M]
    if (finalCost == Double.POSITIVE_INFINITY) {
        return Double.POSITIVE_INFINITY
    }

    // 3. Backtrace to count path length (normalization factor)
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
            val idxUp   = (currI - 1) * numCols + currJ
            val idxLeft = currI * numCols + (currJ - 1)

            val valDiag = D[idxDiag]
            val valUp   = D[idxUp]
            val valLeft = D[idxLeft]

            if (valDiag <= valUp && valDiag <= valLeft) {
                currI--
                currJ--
            } else if (valUp <= valLeft) {
                currI--
            } else {
                currJ--
            }
        }
    }

    return finalCost / maxOf(pathLength, 1)
}
```

---

## 3. Consensus Matching Port

Compare the test sample features against all enrolled intents using top-k=2 consensus:

```kotlin
data class MatchResult(
    val intentName: String,
    val distance: Double
)

/**
 * Sweeps all enrolled template lists and finds the closest intent.
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
            val dist = dtwDistance(template, testFeatures, bandWidth)
            distances.add(dist)
        }

        // Sort distances ascending
        distances.sort()

        // Take top-k average
        val kToUse = minOf(topK, distances.size)
        val sum = distances.take(kToUse).sum()
        val avgDist = if (kToUse > 0) sum / kToUse else Double.POSITIVE_INFINITY

        results.add(MatchResult(intentName, avgDist))
    }

    // Sort results ascending (smallest distance first)
    return results.sortedBy { it.distance }
}
```

---

## 4. JNI Optimization Warning
If testing on lower-end devices shows that a single `consensusMatch` call takes longer than 150ms, the JNI route is highly recommended. The exact C/C++ implementation of `dtwDistance` uses the same memory mapping and can be compiled into a `.so` library for 10x-20x speedups.
