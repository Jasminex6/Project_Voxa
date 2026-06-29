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

    // ── NEW DSP & CLASSIFIER TESTS ──

    @Test
    fun testVoxaVAD_silenceFrameDoesNotTriggerSpeech() {
        val vad = com.example.voxa.ai.VoxaVAD()
        val config = com.example.voxa.ai.VADConfig()
        val frame = ShortArray(config.frameSize) { 0 } // silent frame
        
        val (state, segment) = vad.processFrame(frame)
        assertEquals(com.example.voxa.ai.VADState.SILENCE, state)
        assertNull(segment)
    }

    @Test
    fun testVoxaVAD_speechFramesTriggerSpeechCollecting() {
        val config = com.example.voxa.ai.VADConfig(speechTriggerFrames = 2)
        val vad = com.example.voxa.ai.VoxaVAD(config)
        val frame = ShortArray(config.frameSize) { 2000 } // loud frame (exceeds energyThreshold = 500)

        val (state1, segment1) = vad.processFrame(frame)
        assertEquals(com.example.voxa.ai.VADState.SILENCE, state1)
        assertNull(segment1)

        val (state2, segment2) = vad.processFrame(frame)
        assertEquals(com.example.voxa.ai.VADState.SPEECH_COLLECTING, state2)
        assertNull(segment2)
    }

    @Test
    fun testMfccExtractor_outputDimensions() {
        val extractor = com.example.voxa.ai.MfccExtractor()
        // Create 0.5s of artificial 16kHz audio (8000 samples)
        val pcm = ShortArray(8000) { (1000 * kotlin.math.sin(2.0 * Math.PI * it * 440.0 / 16000.0)).toInt().toShort() }
        
        val features = extractor.extract(pcm)
        assertTrue(features.isNotEmpty())
        assertEquals(40, features[0].size) // 40-dimensional vector
    }

    @Test
    fun testDtwMatcher_distanceOfIdenticalMatricesIsZero() {
        val frame1 = floatArrayOf(0.1f, -0.2f, 0.5f)
        val frame2 = floatArrayOf(0.3f, 0.4f, -0.1f)
        val matrix = arrayOf(frame1, frame2)

        val distance = com.example.voxa.ai.DtwMatcher.dtwDistance(matrix, matrix)
        assertEquals(0.0, distance, 0.0001)
    }

    @Test
    fun testDtwMatcher_consensusMatchSelectsClosest() {
        val testFrame = arrayOf(floatArrayOf(0.1f, 0.2f))
        val templateWater = arrayOf(floatArrayOf(0.12f, 0.22f))
        val templateMore = arrayOf(floatArrayOf(0.9f, 0.9f))

        val templates = mapOf(
            "Water" to listOf(templateWater),
            "More" to listOf(templateMore)
        )

        val results = com.example.voxa.ai.DtwMatcher.consensusMatch(testFrame, templates)
        assertEquals(2, results.size)
        assertEquals("Water", results[0].intentName)
        assertTrue(results[0].distance < results[1].distance)
    }
}
