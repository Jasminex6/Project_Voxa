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
    fun testMatchWord_exactMatchAwards5Coins() {
        val candidates = listOf("مايه")
        val result = FuzzyMatcher.matchWord(candidates, "مايه")
        assertTrue(result.isMatched)
        assertTrue(result.isExact)
        assertEquals(0, result.editDistance)
        assertEquals(5, result.coinsEarned)
    }

    @Test
    fun testMatchWord_closeMatchAwards3Coins() {
        // "ميه" has edit distance 1 to "مايه"
        val candidates = listOf("ميه")
        val result = FuzzyMatcher.matchWord(candidates, "مايه")
        assertTrue(result.isMatched)
        assertFalse(result.isExact)
        assertEquals(1, result.editDistance)
        assertEquals(3, result.coinsEarned)
    }

    @Test
    fun testMatchWord_multiWordSpeechMatchesToken() {
        // Child says "عايز مايه"
        val candidates = listOf("عايز مايه")
        val result = FuzzyMatcher.matchWord(candidates, "مايه")
        assertTrue(result.isMatched)
        assertEquals(5, result.coinsEarned)
    }

    @Test
    fun testMatchWord_aliasesMatch() {
        val candidates = listOf("حليب")
        val result = FuzzyMatcher.matchWord(candidates, "لبن", aliases = listOf("حليب", "اللبن"))
        assertTrue(result.isMatched)
        assertEquals(5, result.coinsEarned)
    }

    @Test
    fun testMatchWord_toddlerAndBilingualAliases() {
        // Child says "امبو" for water
        val cand1 = listOf("امبو")
        val res1 = FuzzyMatcher.matchWord(cand1, "مايه", aliases = listOf("مية", "امبو", "water"))
        assertTrue(res1.isMatched)

        // Child or parent says "water"
        val cand2 = listOf("water")
        val res2 = FuzzyMatcher.matchWord(cand2, "مايه", aliases = listOf("مية", "امبو", "water"))
        assertTrue(res2.isMatched)
    }

    @Test
    fun testMatchWord_completelyDifferentWordFails() {
        val candidates = listOf("بابا")
        val result = FuzzyMatcher.matchWord(candidates, "مايه")
        assertFalse(result.isMatched)
        assertEquals(0, result.coinsEarned)
    }
}
