package com.example.voxa

import com.example.voxa.ai.SpeakerVerifier
import com.example.voxa.logic.MarginGate
import org.junit.Assert.*
import org.junit.Test

/**
 * 🧪 AiBridgeTest
 * Unit tests to verify the mathematical and logic correctness of SpeakerVerifier (Cosine Similarity)
 * and MarginGate (absolute threshold and relative margin gating).
 */
class AiBridgeTest {

    @Test
    fun testCosineSimilarity_identicalVectors() {
        val vectorA = floatArrayOf(1.0f, 2.0f, 3.0f)
        val vectorB = floatArrayOf(1.0f, 2.0f, 3.0f)
        val similarity = SpeakerVerifier.computeCosineSimilarity(vectorA, vectorB)
        assertEquals(1.0f, similarity, 0.0001f)
    }

    @Test
    fun testCosineSimilarity_orthogonalVectors() {
        val vectorA = floatArrayOf(1.0f, 0.0f)
        val vectorB = floatArrayOf(0.0f, 1.0f)
        val similarity = SpeakerVerifier.computeCosineSimilarity(vectorA, vectorB)
        assertEquals(0.0f, similarity, 0.0001f)
    }

    @Test
    fun testCosineSimilarity_oppositeVectors() {
        val vectorA = floatArrayOf(1.0f, -1.0f)
        val vectorB = floatArrayOf(-1.0f, 1.0f)
        val similarity = SpeakerVerifier.computeCosineSimilarity(vectorA, vectorB)
        assertEquals(-1.0f, similarity, 0.0001f)
    }

    @Test
    fun testMarginGate_validMatch() {
        val candidates = listOf(
            MarginGate.CandidateMatch("Water", 0.25f),
            MarginGate.CandidateMatch("More", 0.45f)
        )
        val result = MarginGate.evaluate(
            candidates = candidates,
            absoluteThreshold = 0.40f,
            marginThreshold = 0.15f
        )
        assertTrue(result.isMatch)
        assertEquals("Water", result.matchedWord)
    }

    @Test
    fun testMarginGate_failsAbsoluteThreshold() {
        val candidates = listOf(
            MarginGate.CandidateMatch("Water", 0.42f),
            MarginGate.CandidateMatch("More", 0.55f)
        )
        val result = MarginGate.evaluate(
            candidates = candidates,
            absoluteThreshold = 0.40f,
            marginThreshold = 0.10f
        )
        assertFalse(result.isMatch)
        assertNull(result.matchedWord)
        assertTrue(result.reason.contains("exceeds absolute threshold"))
    }

    @Test
    fun testMarginGate_failsMarginCheck() {
        val candidates = listOf(
            MarginGate.CandidateMatch("Water", 0.30f),
            MarginGate.CandidateMatch("More", 0.35f)
        )
        val result = MarginGate.evaluate(
            candidates = candidates,
            absoluteThreshold = 0.40f,
            marginThreshold = 0.10f
        )
        assertFalse(result.isMatch)
        assertNull(result.matchedWord)
        assertTrue(result.reason.contains("Ambiguous match"))
    }

    @Test
    fun testMarginGate_singleCandidate() {
        val candidates = listOf(
            MarginGate.CandidateMatch("Water", 0.30f)
        )
        val result = MarginGate.evaluate(
            candidates = candidates,
            absoluteThreshold = 0.40f,
            marginThreshold = 0.10f
        )
        assertTrue(result.isMatch)
        assertEquals("Water", result.matchedWord)
    }

    @Test
    fun testMarginGate_emptyCandidates() {
        val result = MarginGate.evaluate(
            candidates = emptyList(),
            absoluteThreshold = 0.40f,
            marginThreshold = 0.10f
        )
        assertFalse(result.isMatch)
        assertNull(result.matchedWord)
    }
}
