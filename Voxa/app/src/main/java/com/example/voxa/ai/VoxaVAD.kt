package com.example.voxa.ai

import kotlin.math.sqrt

/**
 * 🎙️ VoxaVAD — Voice Activity Detection State Machine
 *
 * Detects speech segments from continuous PCM audio by monitoring energy levels
 * and tracking consecutive speech/silence frames using a configurable state machine.
 *
 * Pipeline position: Microphone → [VAD] → Silence Trim → YAMNet → Prototypical Matcher
 *
 * Key changes from v4.0:
 *   - Adaptive energy threshold replaces fixed 900.0 (device-gain independent)
 *   - Speech trigger lowered from 8→4 frames (catches short plosive vocalizations)
 *   - Pre-roll buffer captures onset frames before trigger confirmation
 *
 * Ported from: notebooks/vad.py + notebooks/vad_kotlin_pseudocode.md (Dev A)
 */

data class VADConfig(
    val sampleRate: Int = 16_000,
    val frameMs: Int = 20,                  // 20ms per frame
    val speechTriggerFrames: Int = 4,       // M = 4 consecutive speech frames → trigger (was 8; lowered to catch short bursts)
    val silenceBoundaryFrames: Int = 15,    // N = 15 consecutive silent frames → end segment
    val minSegmentMs: Int = 250,            // Minimum valid segment (unified with AudioFileHelper)
    val maxSegmentMs: Int = 2000,
    val energyThreshold: Double = 900.0,    // Initial/fallback RMS energy threshold
    val adaptiveMultiplier: Double = 3.0,   // Threshold = max(floor, multiplier × ambient RMS)
    val adaptiveFloor: Double = 600.0,      // Minimum adaptive threshold (avoids triggering on mic self-noise)
    val ambientWindowFrames: Int = 250      // 5s of 20ms frames for ambient RMS estimation
) {
    val frameSize: Int get() = sampleRate * frameMs / 1000      // 320 samples
    val minSamples: Int get() = sampleRate * minSegmentMs / 1000 // 4000 (was 6400 at 400ms)
    val maxSamples: Int get() = sampleRate * maxSegmentMs / 1000 // 32000
}

enum class VADState {
    SILENCE,
    SPEECH_COLLECTING,
    SEGMENT_COMPLETE,
    DISCARDED
}

class VoxaVAD(private val config: VADConfig = VADConfig()) {

    private var state: VADState = VADState.SILENCE
    private var speechFrameCount: Int = 0
    private var silenceFrameCount: Int = 0
    private val segmentBuffer: MutableList<ShortArray> = mutableListOf()
    private var segmentSampleCount: Int = 0

    // ── Adaptive threshold state ──
    // Tracks ambient RMS over a rolling window of non-speech frames
    private val ambientRmsHistory = ArrayDeque<Double>(config.ambientWindowFrames)
    private var currentThreshold: Double = config.energyThreshold

    // ── Pre-roll buffer ──
    // Stores recent frames so we can include onset frames before the trigger confirmation
    private val preRollBuffer = ArrayDeque<ShortArray>(config.speechTriggerFrames)

    fun reset() {
        state = VADState.SILENCE
        speechFrameCount = 0
        silenceFrameCount = 0
        segmentBuffer.clear()
        segmentSampleCount = 0
        // Note: we do NOT reset ambientRmsHistory or currentThreshold on segment reset
        // because the ambient estimate should persist across segments
    }

    /**
     * Returns the current adaptive energy threshold.
     * Useful for debugging/logging.
     */
    fun getCurrentThreshold(): Double = currentThreshold

    /**
     * Energy-based speech detection with adaptive threshold.
     *
     * The threshold adapts to ambient noise conditions:
     *   threshold = max(adaptiveFloor, adaptiveMultiplier × rollingAmbientRMS)
     *
     * This makes the VAD device-gain independent: a quiet mic gets a lower
     * threshold, a hot mic gets a higher one, automatically.
     */
    private fun isSpeechEnergy(frameShorts: ShortArray): Boolean {
        var sumSquares = 0.0
        for (s in frameShorts) {
            sumSquares += s.toDouble() * s.toDouble()
        }
        val rms = sqrt(sumSquares / frameShorts.size)
        return rms > currentThreshold
    }

    /**
     * Updates the ambient RMS estimate from a non-speech frame.
     * Called only when we're in SILENCE state (not during speech collection).
     */
    private fun updateAmbientEstimate(frameShorts: ShortArray) {
        var sumSquares = 0.0
        for (s in frameShorts) {
            sumSquares += s.toDouble() * s.toDouble()
        }
        val rms = sqrt(sumSquares / frameShorts.size)

        // Only add to ambient if it's below the current threshold (definitely not speech)
        if (rms < currentThreshold) {
            if (ambientRmsHistory.size >= config.ambientWindowFrames) {
                ambientRmsHistory.removeFirst()
            }
            ambientRmsHistory.addLast(rms)

            // Recompute threshold from rolling average
            if (ambientRmsHistory.size >= 10) { // Need at least 10 frames (~200ms) for stable estimate
                val avgAmbient = ambientRmsHistory.average()
                currentThreshold = maxOf(config.adaptiveFloor, config.adaptiveMultiplier * avgAmbient)
            }
        }
    }

    /**
     * Processes a single audio frame through the VAD state machine.
     *
     * @param frameSamples A ShortArray of [config.frameSize] samples (320 at 16kHz/20ms)
     * @return Pair of (current state, completed segment or null)
     */
    fun processFrame(frameSamples: ShortArray): Pair<VADState, ShortArray?> {
        val isSpeech = isSpeechEnergy(frameSamples)

        when (state) {
            VADState.SILENCE -> {
                if (isSpeech) {
                    speechFrameCount++
                    segmentBuffer.add(frameSamples.copyOf())
                    segmentSampleCount += frameSamples.size

                    if (speechFrameCount >= config.speechTriggerFrames) {
                        // Include pre-roll frames to capture onset
                        val preRollFrames = preRollBuffer.toList()
                        if (preRollFrames.isNotEmpty()) {
                            // Prepend pre-roll frames to segment buffer
                            for (i in preRollFrames.indices.reversed()) {
                                segmentBuffer.add(0, preRollFrames[i])
                                segmentSampleCount += preRollFrames[i].size
                            }
                        }
                        preRollBuffer.clear()
                        state = VADState.SPEECH_COLLECTING
                        silenceFrameCount = 0
                    }
                } else {
                    // Not speech — update ambient estimate and maintain pre-roll buffer
                    updateAmbientEstimate(frameSamples)

                    if (preRollBuffer.size >= config.speechTriggerFrames) {
                        preRollBuffer.removeFirst()
                    }
                    preRollBuffer.addLast(frameSamples.copyOf())

                    speechFrameCount = 0
                    segmentBuffer.clear()
                    segmentSampleCount = 0
                }
            }

            VADState.SPEECH_COLLECTING -> {
                segmentBuffer.add(frameSamples.copyOf())
                segmentSampleCount += frameSamples.size

                if (!isSpeech) {
                    silenceFrameCount++

                    if (silenceFrameCount >= config.silenceBoundaryFrames) {
                        // Segment ended — validate length
                        val segment = concatenateBuffers(segmentBuffer)
                        reset()

                        return if (segment.size in config.minSamples..config.maxSamples) {
                            Pair(VADState.SEGMENT_COMPLETE, segment)
                        } else {
                            Pair(VADState.DISCARDED, null)
                        }
                    }
                } else {
                    silenceFrameCount = 0
                }

                // Safety: reject if segment is too long
                if (segmentSampleCount > config.maxSamples) {
                    reset()
                    return Pair(VADState.DISCARDED, null)
                }
            }

            else -> { /* SEGMENT_COMPLETE and DISCARDED are transient states */ }
        }

        return Pair(state, null)
    }

    /**
     * Processes a full audio buffer and extracts all valid speech segments.
     *
     * @param pcmInt16 Raw 16-bit PCM audio at 16kHz
     * @return List of speech segments as ShortArrays
     */
    fun processAudio(pcmInt16: ShortArray): List<ShortArray> {
        reset()
        val segments = mutableListOf<ShortArray>()
        val frameSize = config.frameSize

        var start = 0
        while (start + frameSize <= pcmInt16.size) {
            val frame = pcmInt16.copyOfRange(start, start + frameSize)
            val (_, segment) = processFrame(frame)
            if (segment != null) {
                segments.add(segment)
            }
            start += frameSize
        }

        // Flush any remaining collected speech
        if (state == VADState.SPEECH_COLLECTING) {
            val segment = concatenateBuffers(segmentBuffer)
            if (segment.size >= config.minSamples) {
                segments.add(segment)
            }
        }

        return segments
    }

    companion object {
        /**
         * Concatenates a list of ShortArrays into a single contiguous ShortArray.
         */
        fun concatenateBuffers(buffers: List<ShortArray>): ShortArray {
            val totalSize = buffers.sumOf { it.size }
            val result = ShortArray(totalSize)
            var offset = 0
            for (buf in buffers) {
                System.arraycopy(buf, 0, result, offset, buf.size)
                offset += buf.size
            }
            return result
        }
    }
}
