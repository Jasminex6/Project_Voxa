package com.example.voxa.logic

/**
 * 🔒 MarginGate
 * Checks if the matching distance is close enough (absolute threshold) and distinct enough (margin threshold).
 */
object MarginGate {

    data class CandidateMatch(
        val word: String,
        val distance: Float
    )

    data class GateResult(
        val isMatch: Boolean,
        val matchedWord: String?,
        val reason: String
    )

    /**
     * Evaluates candidates using thresholds.
     * @param candidates List of candidate matches with their calculated distances (lower is better).
     * @param absoluteThreshold The maximum distance allowed for a match to be considered valid.
     * @param marginThreshold The minimum distance separation between the best and second-best matches.
     */
    fun evaluate(
        candidates: List<CandidateMatch>,
        absoluteThreshold: Float,
        marginThreshold: Float
    ): GateResult {
        // 1. Guard check: Return immediately if no enrolled word templates are found.
        if (candidates.isEmpty()) {
            return GateResult(false, null, "No candidates provided")
        }

        // 2. Sort candidates by matching distance in ascending order (lower distance = closer sound similarity).
        val sorted = candidates.sortedBy { it.distance }
        val best = sorted[0]

        // 3. Absolute threshold gate: Ensure the candidate is close enough to represent a real translation.
        if (best.distance >= absoluteThreshold) {
            return GateResult(
                false, 
                null, 
                "Best match distance (${best.distance}) exceeds absolute threshold ($absoluteThreshold)"
            )
        }

        // 4. Single candidate exception: If the child only has one word enrolled, bypass the margin check.
        if (sorted.size == 1) {
            return GateResult(true, best.word, "Valid match (single candidate)")
        }

        // 5. Margin check: Calculate distance separation between the top match and second-best alternative.
        val secondBest = sorted[1]
        val margin = secondBest.distance - best.distance
        
        // If they are too close (margin is small), flag it as ambiguous to prevent playing the wrong translation.
        if (margin < marginThreshold) {
            return GateResult(
                false,
                null,
                "Ambiguous match. Margin ($margin) is below threshold ($marginThreshold). Best: ${best.word} (${best.distance}), Second: ${secondBest.word} (${secondBest.distance})"
            )
        }

        // 6. Success: Both distance constraints pass.
        return GateResult(true, best.word, "Valid match (passed absolute and margin thresholds)")
    }
}
