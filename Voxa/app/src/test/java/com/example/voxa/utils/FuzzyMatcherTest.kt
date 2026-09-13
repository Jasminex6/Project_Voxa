package com.example.voxa.utils

import org.junit.Assert.*
import org.junit.Test

class FuzzyMatcherTest {

    @Test
    fun testNormalize_arabicDiacriticsAndVariants() {
        // Tashkeel removal
        assertEquals("ميه", FuzzyMatcher.normalize("مَيَّة"))
        assertEquals("اكل", FuzzyMatcher.normalize("أَكْل"))

        // Alef variants
        assertEquals("انا", FuzzyMatcher.normalize("أنا"))
        assertEquals("اب", FuzzyMatcher.normalize("إب"))
        assertEquals("اكل", FuzzyMatcher.normalize("آكل"))

        // Taa Marbuta to Haa
        assertEquals("مساعده", FuzzyMatcher.normalize("مساعدة"))

        // Alif Maqsura to Yaa
        assertEquals("علي", FuzzyMatcher.normalize("على"))
    }

    @Test
    fun testLevenshteinDistance() {
        assertEquals(0, FuzzyMatcher.levenshtein("مايه", "مايه"))
        assertEquals(1, FuzzyMatcher.levenshtein("مايه", "ميه"))
        assertEquals(1, FuzzyMatcher.levenshtein("water", "wate"))
        assertEquals(2, FuzzyMatcher.levenshtein("water", "waiter"))
    }

    @Test
    fun testMatchWord_exactMatchAwards5Stars() {
        // 100% exact match gives 5 stars and 5 coins
        val candidates = listOf("مايه")
        val result = FuzzyMatcher.matchWord(candidates, "مايه")
        assertTrue(result.isMatched)
        assertTrue(result.isExact)
        assertEquals(100.0, result.matchPercent, 0.001)
        assertEquals(5, result.starsEarned)
        assertEquals(5, result.coinsEarned)
        assertEquals(0, result.editDistance)
    }

    @Test
    fun testMatchWord_greaterThan90PercentAwards4Stars() {
        // "helicoptor" vs "helicopter": 10 chars, 1 edit distance -> 90.0% match
        val candidates = listOf("helicoptor")
        val result = FuzzyMatcher.matchWord(candidates, "helicopter")
        assertTrue(result.isMatched)
        assertFalse(result.isExact)
        assertEquals(90.0, result.matchPercent, 0.001)
        assertEquals(4, result.starsEarned)
        assertEquals(4, result.coinsEarned)
    }

    @Test
    fun testMatchWord_greaterThan80PercentAwards3Stars() {
        // "wate" vs "water": 5 chars, 1 edit distance -> 80.0% match
        val candidates = listOf("wate")
        val result = FuzzyMatcher.matchWord(candidates, "water")
        assertTrue(result.isMatched)
        assertFalse(result.isExact)
        assertEquals(80.0, result.matchPercent, 0.001)
        assertEquals(3, result.starsEarned)
        assertEquals(3, result.coinsEarned)

        // "مساعد" vs "مساعدة" (norm: "مساعده"): 6 chars, 1 edit distance -> 83.33% match
        val candArabic = listOf("مساعد")
        val resArabic = FuzzyMatcher.matchWord(candArabic, "مساعدة")
        assertTrue(resArabic.isMatched)
        assertEquals(3, resArabic.starsEarned)
        assertEquals(3, resArabic.coinsEarned)
    }

    @Test
    fun testMatchWord_greaterThan70PercentAwards1Star() {
        // "mikk" vs "milk": 4 chars, 1 edit distance -> 75.0% match (> 70% and <= 80%)
        val candidates = listOf("mikk")
        val result = FuzzyMatcher.matchWord(candidates, "milk")
        assertTrue(result.isMatched)
        assertFalse(result.isExact)
        assertEquals(75.0, result.matchPercent, 0.001)
        assertEquals(1, result.starsEarned)
        assertEquals(1, result.coinsEarned)
    }

    @Test
    fun testMatchWord_lessThanOrEqualTo70PercentAwardsNothing() {
        // "عش" vs "عيش": 3 chars, 1 edit distance -> 66.67% match (<= 70%)
        val candidates = listOf("عش")
        val result = FuzzyMatcher.matchWord(candidates, "عيش")
        assertFalse(result.isMatched)
        assertFalse(result.isExact)
        assertEquals(0, result.starsEarned)
        assertEquals(0, result.coinsEarned)
    }

    @Test
    fun testMatchWord_multiWordSpeechDoesNotAward5Stars() {
        // Child says "عايز مايه" — not an exact match (100%), so it must NOT give 5 stars
        val candidates = listOf("عايز مايه")
        val result = FuzzyMatcher.matchWord(candidates, "مايه")
        assertTrue(result.isMatched)
        assertFalse(result.isExact)
        assertNotEquals(5, result.starsEarned)
        assertEquals(3, result.starsEarned)
        assertEquals(3, result.coinsEarned)
    }

    @Test
    fun testMatchWord_aliasesExactMatchAwards5Stars() {
        // Child says "حليب" which is an alias for "لبن"
        val candidates = listOf("حليب")
        val result = FuzzyMatcher.matchWord(candidates, "لبن", aliases = listOf("حليب", "اللبن"))
        assertTrue(result.isMatched)
        assertTrue(result.isExact)
        assertEquals(100.0, result.matchPercent, 0.001)
        assertEquals(5, result.starsEarned)
        assertEquals(5, result.coinsEarned)
    }

    @Test
    fun testMatchWord_toddlerAndBilingualAliases() {
        // Child says "امبو" for water
        val cand1 = listOf("امبو")
        val res1 = FuzzyMatcher.matchWord(cand1, "مايه", aliases = listOf("مية", "امبو", "water"))
        assertTrue(res1.isMatched)
        assertEquals(5, res1.starsEarned)

        // Child or parent says "water"
        val cand2 = listOf("water")
        val res2 = FuzzyMatcher.matchWord(cand2, "مايه", aliases = listOf("مية", "امبو", "water"))
        assertTrue(res2.isMatched)
        assertEquals(5, res2.starsEarned)
    }

    @Test
    fun testMatchWord_completelyDifferentWordFails() {
        val candidates = listOf("بابا")
        val result = FuzzyMatcher.matchWord(candidates, "مايه")
        assertFalse(result.isMatched)
        assertEquals(0, result.starsEarned)
        assertEquals(0, result.coinsEarned)
    }

    @Test
    fun testMatchWord_englishPracticeMode() {
        val aliases = listOf("water", "wawa", "watr")

        // Exact match
        val exact = FuzzyMatcher.matchWord(listOf("water"), "Water", aliases = aliases)
        assertTrue(exact.isMatched)
        assertTrue(exact.isExact)
        assertEquals(5, exact.starsEarned)

        // Close approximation (>80% match)
        val close = FuzzyMatcher.matchWord(listOf("wate"), "Water", aliases = aliases)
        assertTrue(close.isMatched)
        assertFalse(close.isExact)
        assertEquals(80.0, close.matchPercent, 0.001)
        assertEquals(3, close.starsEarned)

        // Arabic spoken during English practice should fail
        val arabicSpoken = FuzzyMatcher.matchWord(listOf("مايه"), "Water", aliases = aliases)
        assertFalse(arabicSpoken.isMatched)
        assertEquals(0, arabicSpoken.starsEarned)
    }
}
