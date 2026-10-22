package com.example.voxa

import com.example.voxa.ai.PrototypicalMatcher
import com.example.voxa.ai.YamnetEncoder
import com.example.voxa.ai.MarginGate
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

/**
 * 🧪 AiBridgeTest
 * Unit tests to verify the mathematical and logic correctness of PrototypicalMatcher
 * (Cosine Similarity, QC Outlier Rejection, K-Means clustering, CCP, OOD, and Margin gates)
 * and YamnetEncoder (tiling, center-cropping).
 */
class AiBridgeTest {

    @Test
    fun testCosineSimilarity_identicalNormalizedVectors() {
        val vectorA = floatArrayOf(1.0f, 0.0f, 0.0f)
        val vectorB = floatArrayOf(1.0f, 0.0f, 0.0f)
        val similarity = PrototypicalMatcher.cosineSimilarity(vectorA, vectorB)
        assertEquals(1.0f, similarity, 0.0001f)
    }

    @Test
    fun testCosineSimilarity_orthogonalNormalizedVectors() {
        val vectorA = floatArrayOf(1.0f, 0.0f)
        val vectorB = floatArrayOf(0.0f, 1.0f)
        val similarity = PrototypicalMatcher.cosineSimilarity(vectorA, vectorB)
        assertEquals(0.0f, similarity, 0.0001f)
    }

    @Test
    fun testCosineSimilarity_oppositeNormalizedVectors() {
        val vectorA = floatArrayOf(0.7071f, 0.7071f)
        val vectorB = floatArrayOf(-0.7071f, -0.7071f)
        val similarity = PrototypicalMatcher.cosineSimilarity(vectorA, vectorB)
        assertEquals(-1.0f, similarity, 0.001f)
    }

    @Test
    fun testYamnetEncoder_prepareAudioWindow_padsShortSignal() {
        // The current encoder center-pads to a fixed 1.44-second window.
        val pcm = ShortArray(1000) { it.toShort() }
        val prepared = YamnetEncoder.prepareAudioWindow(pcm)
        assertEquals(23040, prepared.size)
        val offset = (23040 - pcm.size) / 2
        assertEquals(0f, prepared[offset - 1], 0.0001f)
        assertEquals(pcm[999] / 32768f, prepared[offset + 999], 0.0001f)
        assertEquals(0f, prepared[offset + 1000], 0.0001f)
    }

    @Test
    fun testYamnetEncoder_prepareAudioWindow_leavesLongSignal() {
        // Long input is center-cropped, preserving the middle of the utterance.
        val pcm = ShortArray(30000) { it.toShort() }
        val prepared = YamnetEncoder.prepareAudioWindow(pcm)
        assertEquals(23040, prepared.size)
        val start = (pcm.size - 23040) / 2
        assertEquals(pcm[start] / 32768f, prepared.first(), 0.0001f)
        assertEquals(pcm[start + 23039] / 32768f, prepared.last(), 0.0001f)
    }

    @Test
    fun testPrototypicalMatcher_qcOutlierRejection() {
        // Create 5 identical vectors and 1 outlier vector
        val baseVec = FloatArray(1024) { 0.0f }.apply { this[0] = 1.0f } // L2 normalized
        val outlierVec = FloatArray(1024) { 0.0f }.apply { this[1] = 1.0f } // Orthogonal outlier

        val samples = listOf(
            baseVec,
            baseVec,
            baseVec,
            baseVec,
            baseVec,
            outlierVec
        )

        val qcResult = PrototypicalMatcher.qcOutlierRejection(samples)
        // Outlier should be rejected
        assertEquals(5, qcResult.size)
        for (vec in qcResult) {
            assertArrayEquals(baseVec, vec, 0.0001f)
        }
    }

    @Test
    fun testPrototypicalMatcher_kMeans2() {
        // Create two clearly separated clusters
        val baseVec1 = FloatArray(1024) { 0.0f }.apply { this[0] = 1.0f }
        val baseVec2 = FloatArray(1024) { 0.0f }.apply { this[1] = 1.0f }

        val samples = listOf(
            baseVec1, baseVec1, baseVec1,
            baseVec2, baseVec2, baseVec2
        )

        val (c1, c2) = PrototypicalMatcher.kMeans2(samples)
        
        // The centroids should align perfectly with baseVec1 and baseVec2 (or vice versa)
        val scoreA1 = PrototypicalMatcher.cosineSimilarity(c1, baseVec1)
        val scoreA2 = PrototypicalMatcher.cosineSimilarity(c1, baseVec2)
        val scoreB1 = PrototypicalMatcher.cosineSimilarity(c2, baseVec1)
        val scoreB2 = PrototypicalMatcher.cosineSimilarity(c2, baseVec2)

        assertTrue(
            (scoreA1 > 0.99f && scoreB2 > 0.99f) || (scoreA2 > 0.99f && scoreB1 > 0.99f)
        )
    }

    @Test
    fun testPrototypicalMatcher_ccpPenalty() {
        val liveVec = FloatArray(1024) { 0.0f }.apply { this[0] = 1.0f }
        val centroid = FloatArray(1024) { 0.0f }.apply { this[0] = 0.9f; this[1] = 0.435f } // L2 normalized

        // Intent with 1 centroid
        val score1 = PrototypicalMatcher.scoreIntent(
            liveEmbedding = liveVec,
            centroids = listOf(centroid),
            intentName = "Water",
            outputPhrase = "Water",
            audioAssetPath = "water.mp3",
            oodThreshold = 0.8f
        )
        // No penalty: raw similarity = effective similarity
        assertEquals(0.9f, score1.rawSimilarity, 0.001f)
        assertEquals(0.9f, score1.effectiveSimilarity, 0.001f)

        // Intent with 2 centroids (ccp penalty = 0.05f)
        val score2 = PrototypicalMatcher.scoreIntent(
            liveEmbedding = liveVec,
            centroids = listOf(centroid, centroid),
            intentName = "Water",
            outputPhrase = "Water",
            audioAssetPath = "water.mp3",
            oodThreshold = 0.8f
        )
        assertEquals(0.9f, score2.rawSimilarity, 0.001f)
        assertEquals(0.85f, score2.effectiveSimilarity, 0.001f) // 0.90 - 0.05
    }

    @Test
    fun testPrototypicalMatcher_gates_validMatch() {
        val liveVec = FloatArray(1024) { 0.0f }.apply { this[0] = 1.0f }
        
        val scores = listOf(
            PrototypicalMatcher.IntentScore(
                intentName = "Water",
                outputPhrase = "أنا عايز ميّه",
                audioAssetPath = "water.mp3",
                rawSimilarity = 0.92f,
                effectiveSimilarity = 0.92f,
                centroidCount = 1,
                oodThreshold = 0.85f
            ),
            PrototypicalMatcher.IntentScore(
                intentName = "More",
                outputPhrase = "عايز تاني",
                audioAssetPath = "more.mp3",
                rawSimilarity = 0.80f,
                effectiveSimilarity = 0.80f,
                centroidCount = 1,
                oodThreshold = 0.85f
            )
        )

        val result = PrototypicalMatcher.evaluateGates(scores)
        assertTrue(result.isMatch)
        assertEquals("Water", result.intentName)
        assertEquals("أنا عايز ميّه", result.outputPhrase)
        assertEquals(0.92f, result.confidence, 0.0001f)
    }

    @Test
    fun testPrototypicalMatcher_gates_failsOOD() {
        val scores = listOf(
            PrototypicalMatcher.IntentScore(
                intentName = "Water",
                outputPhrase = "أنا عايز ميّه",
                audioAssetPath = "water.mp3",
                rawSimilarity = 0.82f,
                effectiveSimilarity = 0.82f,
                centroidCount = 1,
                oodThreshold = 0.85f // similarity 0.82 < threshold 0.85
            )
        )

        val result = PrototypicalMatcher.evaluateGates(scores)
        assertFalse(result.isMatch)
        assertEquals("Water", result.intentName)
        assertNull(result.outputPhrase)
        assertTrue(result.reason.contains("OOD rejected"))
    }

    @Test
    fun testPrototypicalMatcher_gates_failsMargin() {
        val scores = listOf(
            PrototypicalMatcher.IntentScore(
                intentName = "Water",
                outputPhrase = "أنا عايز ميّه",
                audioAssetPath = "water.mp3",
                rawSimilarity = 0.89f,
                effectiveSimilarity = 0.89f,
                centroidCount = 1,
                oodThreshold = 0.85f
            ),
            PrototypicalMatcher.IntentScore(
                intentName = "More",
                outputPhrase = "عايز تاني",
                audioAssetPath = "more.mp3",
                rawSimilarity = 0.87f,
                effectiveSimilarity = 0.87f,
                centroidCount = 1,
                oodThreshold = 0.85f
            )
        )

        val result = PrototypicalMatcher.evaluateGates(scores)
        // Margin (0.89 - 0.87 = 0.02) is less than 0.04 margin threshold
        assertFalse(result.isMatch)
        assertNull(result.intentName)
        assertTrue(result.reason.contains("Ambiguous"))
    }

    // ── MARGIN GATE TESTS ──

    @Test
    fun testMarginGate_validMatch() {
        val candidates = listOf(
            MarginGate.CandidateMatch("Water", 0.10f),
            MarginGate.CandidateMatch("More", 0.18f)
        )
        val result = MarginGate.evaluate(
            candidates = candidates,
            absoluteThreshold = 0.82f,
            marginThreshold = 0.04f
        )
        assertTrue(result.isMatch)
        assertEquals("Water", result.matchedWord)
    }

    @Test
    fun testMarginGate_failsAbsoluteThreshold() {
        val candidates = listOf(
            MarginGate.CandidateMatch("Water", 0.90f),
            MarginGate.CandidateMatch("More", 0.95f)
        )
        val result = MarginGate.evaluate(
            candidates = candidates,
            absoluteThreshold = 0.82f,
            marginThreshold = 0.04f
        )
        assertFalse(result.isMatch)
        assertNull(result.matchedWord)
        assertTrue(result.reason.contains("exceeds absolute threshold"))
    }

    @Test
    fun testMarginGate_failsMarginCheck() {
        val candidates = listOf(
            MarginGate.CandidateMatch("Water", 0.15f),
            MarginGate.CandidateMatch("More", 0.17f)
        )
        val result = MarginGate.evaluate(
            candidates = candidates,
            absoluteThreshold = 0.82f,
            marginThreshold = 0.04f
        )
        assertFalse(result.isMatch)
        assertNull(result.matchedWord)
        assertTrue(result.reason.contains("Ambiguous match"))
    }

    @Test
    fun testMarginGate_singleCandidate() {
        val candidates = listOf(
            MarginGate.CandidateMatch("Water", 0.15f)
        )
        val result = MarginGate.evaluate(
            candidates = candidates,
            absoluteThreshold = 0.82f,
            marginThreshold = 0.04f
        )
        assertTrue(result.isMatch)
        assertEquals("Water", result.matchedWord)
    }

    @Test
    fun testMarginGate_emptyCandidates() {
        val result = MarginGate.evaluate(
            candidates = emptyList(),
            absoluteThreshold = 0.82f,
            marginThreshold = 0.04f
        )
        assertFalse(result.isMatch)
        assertNull(result.matchedWord)
    }
}
