package com.example.voxa.utils

import kotlin.math.min

/**
 * 🔍 FuzzyMatcher
 *
 * Compares recognized speech transcriptions from Android SpeechRecognizer against
 * target words with specialized Arabic & English normalization and Levenshtein edit distance.
 *
 * Designed to be forgiving for minimally verbal children and speech approximations:
 * - Normalizes Arabic diacritics (tashkeel), alefs, taa marbuta, alif maqsura, and hamza carriers.
 * - Checks exact matches, token-level matches (if speech contains extra conversational words),
 *   and edit distance up to [maxDistance].
 */
object FuzzyMatcher {

    /**
     * Normalizes an Arabic or English text string:
     * 1. Strips Arabic Tashkeel / Harakat (diacritics: Fatha, Damma, Kasra, Tanween, Sukun, Shadda, etc.)
     * 2. Strips Tatweel (Kashida)
     * 3. Normalizes Alef variations (أ, إ, آ, ٱ -> ا)
     * 4. Normalizes Taa Marbuta (ة -> ه)
     * 5. Normalizes Alif Maqsura (ى -> ي)
     * 6. Normalizes Hamza on Waw / Yaa (ؤ -> و, ئ -> ي)
     * 7. Strips punctuation, non-alphanumeric symbols
     * 8. Trims and lowercases (for English)
     */
    fun normalize(text: String): String {
        if (text.isBlank()) return ""

        var s = text.trim().lowercase()

        // Remove Arabic diacritics (ranges: \u064B-\u065F, \u0670)
        s = s.replace(Regex("[\u064B-\u065F\u0670]"), "")

        // Remove Tatweel (\u0640)
        s = s.replace("\u0640", "")

        // Normalize Alef variants to plain bare Alef
        s = s.replace(Regex("[أإآٱ]"), "ا")

        // Normalize Taa Marbuta to Haa
        s = s.replace('ة', 'ه')

        // Normalize Alif Maqsura to Yaa
        s = s.replace('ى', 'ي')

        // Normalize Hamza carriers
        s = s.replace('ؤ', 'و').replace('ئ', 'ي')

        // Strip punctuation and special characters, retaining letters, numbers, and whitespace
        s = s.replace(Regex("[^\\p{L}\\p{N}\\s]"), "")

        // Collapse multiple whitespaces into a single space
        s = s.replace(Regex("\\s+"), " ").trim()

        return s
    }

    /**
     * Computes the Levenshtein edit distance between two strings.
     */
    fun levenshtein(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        val d = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) d[i][0] = i
        for (j in 0..s2.length) d[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                d[i][j] = min(
                    min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                    d[i - 1][j - 1] + cost
                )
            }
        }
        return d[s1.length][s2.length]
    }

    /**
     * Result of matching child speech against target word.
     */
    data class MatchResult(
        val isMatched: Boolean,
        val isExact: Boolean,
        val editDistance: Int,
        val bestCandidate: String,
        val coinsEarned: Int,
        val feedbackMessage: String
    )

    /**
     * Evaluates a list of STT speech candidate transcripts against target word(s).
     *
     * @param candidates The list of recognized phrases returned by Android SpeechRecognizer
     * @param primaryTarget The main target word (e.g. "مايه")
     * @param aliases Alternative acceptable variants (e.g. listOf("مية", "ماية", "ماء", "water"))
     * @param maxDistance Maximum edit distance for a close match (default 2)
     */
    fun matchWord(
        candidates: List<String>,
        primaryTarget: String,
        aliases: List<String> = emptyList(),
        maxDistance: Int = 2
    ): MatchResult {
        if (candidates.isEmpty()) {
            return MatchResult(
                isMatched = false,
                isExact = false,
                editDistance = 999,
                bestCandidate = "",
                coinsEarned = 0,
                feedbackMessage = "Didn't hear any speech. Try again! 🎙️"
            )
        }

        val allTargets = (listOf(primaryTarget) + aliases)
            .map { normalize(it) }
            .filter { it.isNotBlank() }

        if (allTargets.isEmpty()) {
            return MatchResult(
                isMatched = false,
                isExact = false,
                editDistance = 999,
                bestCandidate = candidates.firstOrNull() ?: "",
                coinsEarned = 0,
                feedbackMessage = "Target word was empty."
            )
        }

        var bestDist = Int.MAX_VALUE
        var closestCandidate = candidates.first()

        for (rawCand in candidates) {
            val normCand = normalize(rawCand)
            if (normCand.isBlank()) continue

            // Split into individual words in case child uttered a phrase like "عايز مايه" or "مايه يا ماما"
            val tokens = normCand.split(" ").filter { it.isNotBlank() }

            for (target in allTargets) {
                // 1. Direct comparison with full candidate
                val fullDist = levenshtein(normCand, target)
                if (fullDist < bestDist) {
                    bestDist = fullDist
                    closestCandidate = rawCand
                }

                // 2. Token-level comparison for each word in candidate phrase
                for (token in tokens) {
                    val tokenDist = levenshtein(token, target)
                    if (tokenDist < bestDist) {
                        bestDist = tokenDist
                        closestCandidate = token
                    }
                }

                // 3. Substring match (e.g., target contained in phrase or close token contained in target)
                if (normCand.contains(target) || tokens.any { it.contains(target) || (target.contains(it) && it.length >= 2 && it.length >= target.length - 1) }) {
                    if (bestDist > 0) {
                        bestDist = 0
                        closestCandidate = rawCand
                    }
                }
            }
        }

        val isExact = bestDist == 0
        val isMatched = isExact || (bestDist <= maxDistance)

        val coins = when {
            isExact -> 5
            isMatched -> 3
            else -> 0
        }

        val feedback = when {
            isExact -> "Awesome! Exact match! 🎉"
            isMatched -> "Great pronunciation! Very close! 🌟"
            else -> "Keep trying! You can do it! 💪"
        }

        return MatchResult(
            isMatched = isMatched,
            isExact = isExact,
            editDistance = if (bestDist == Int.MAX_VALUE) 999 else bestDist,
            bestCandidate = closestCandidate,
            coinsEarned = coins,
            feedbackMessage = feedback
        )
    }
}
