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
     * Computes percentage similarity between two normalized strings:
     * 100.0% for exact match, scaled by Levenshtein edit distance over max string length.
     */
    fun computeSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 100.0
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 100.0
        val dist = levenshtein(s1, s2)
        return maxOf(0.0, ((maxLen - dist).toDouble() / maxLen.toDouble()) * 100.0)
    }

    /**
     * Result of matching child speech against target word.
     */
    data class MatchResult(
        val isMatched: Boolean,
        val isExact: Boolean,
        val matchPercent: Double = 0.0,
        val starsEarned: Int = 0,
        val editDistance: Int = 999,
        val bestCandidate: String = "",
        val coinsEarned: Int = 0,
        val feedbackMessage: String = ""
    )

    /**
     * Evaluates a list of STT speech candidate transcripts against target word(s).
     *
     * Scoring Rules:
     * - 5 Stars / Coins: Word matches exactly (100% match)
     * - 4 Stars / Coins: > 90% match
     * - 3 Stars / Coins: > 80% match (and <= 90%)
     * - 1 Star  / Coin : > 70% match (and <= 80%)
     * - 0 Stars / Coins: <= 70% match ("nothing for less")
     *
     * @param candidates The list of recognized phrases returned by Android SpeechRecognizer
     * @param primaryTarget The main target word (e.g. "مايه")
     * @param aliases Alternative acceptable variants (e.g. listOf("مية", "ماية", "ماء", "water"))
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
                matchPercent = 0.0,
                starsEarned = 0,
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
                matchPercent = 0.0,
                starsEarned = 0,
                editDistance = 999,
                bestCandidate = candidates.firstOrNull() ?: "",
                coinsEarned = 0,
                feedbackMessage = "Target word was empty."
            )
        }

        // 1. STRICT EXACT MATCH CHECK (100% match only)
        // 5 stars is ONLY given if at least one candidate exactly matches the target or an alias
        for (rawCand in candidates) {
            val normCand = normalize(rawCand)
            if (normCand.isBlank()) continue

            if (allTargets.any { target -> normCand == target }) {
                return MatchResult(
                    isMatched = true,
                    isExact = true,
                    matchPercent = 100.0,
                    starsEarned = 5,
                    editDistance = 0,
                    bestCandidate = rawCand,
                    coinsEarned = 5,
                    feedbackMessage = "Awesome! Exact match! 🏆"
                )
            }
        }

        // 2. FUZZY PERCENTAGE EVALUATION FOR NON-EXACT SPEECH
        var bestPercent = 0.0
        var bestCandidate = candidates.first()
        var bestDist = Int.MAX_VALUE

        for (rawCand in candidates) {
            val normCand = normalize(rawCand)
            if (normCand.isBlank()) continue

            val tokens = normCand.split(" ").filter { it.isNotBlank() }

            for (target in allTargets) {
                // A. Direct full candidate comparison
                val fullDist = levenshtein(normCand, target)
                val fullSim = computeSimilarity(normCand, target)
                if (fullSim > bestPercent) {
                    bestPercent = fullSim
                    bestCandidate = rawCand
                    bestDist = fullDist
                }

                // B. Token-level comparison for multi-word phrases (e.g., child says "عايز مايه")
                if (tokens.size > 1) {
                    for (token in tokens) {
                        val tokenDist = levenshtein(token, target)
                        var tokenSim = computeSimilarity(token, target)
                        // If token matches target 100%, but is embedded in a phrase,
                        // cap at 85.0% so 5 stars is strictly preserved for exact standalone matches
                        if (tokenSim >= 100.0) {
                            tokenSim = 85.0
                        }
                        if (tokenSim > bestPercent) {
                            bestPercent = tokenSim
                            bestCandidate = token
                            bestDist = tokenDist
                        }
                    }
                }
            }
        }

        // Non-exact matches can NEVER reach 100%
        if (bestPercent >= 100.0) {
            bestPercent = 99.0
        }

        // Star & Coin Scoring Tiers:
        // - 5 Stars: ONLY if 100% exact match (handled above)
        // - 4 Stars: > 90% match
        // - 3 Stars: > 80% match
        // - 1 Star : > 70% match
        // - 0 Stars: <= 70% match (nothing for less)
        val stars = when {
            bestPercent >= 90.0 -> 4
            bestPercent >= 80.0 -> 3
            bestPercent > 70.0 -> 1
            else -> 0
        }

        val isMatched = stars > 0

        val feedback = when (stars) {
            4 -> "Excellent pronunciation! 🌟"
            3 -> "Great effort! Very close! 🌟"
            1 -> "Good try! Keep practicing! ⭐"
            else -> "Keep trying! You can do it! 💪"
        }

        return MatchResult(
            isMatched = isMatched,
            isExact = false,
            matchPercent = bestPercent,
            starsEarned = stars,
            editDistance = if (bestDist == Int.MAX_VALUE) 999 else bestDist,
            bestCandidate = bestCandidate,
            coinsEarned = stars,
            feedbackMessage = feedback
        )
    }
}
