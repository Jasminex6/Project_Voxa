package com.example.voxa.ai

import kotlin.math.sqrt

/**
 * 🎙️ VoxaVAD — Voice Activity Detection State Machine
 *
 * Detects speech segments from continuous PCM audio by monitoring energy levels
 * and tracking consecutive speech/silence frames using a configurable state machine.
 *
 * Pipeline position: Microphone → [VAD] → Speaker Verify → MFCC → DTW
 *
 * Ported from: notebooks/vad.py + notebooks/vad_kotlin_pseudocode.md (Dev A)
 */

data class VADConfig(
    val sampleRate: Int = 16_000,
    val frameMs: Int = 20,                  // 20ms per frame
    val speechTriggerFrames: Int = 8,       // M = 8 consecutive speech frames → trigger
    val silenceBoundaryFrames: Int = 15,    // N = 15 consecutive silent frames → end segment
    val minSegmentMs: Int = 400,
    val maxSegmentMs: Int = 2000,
    val energyThreshold: Double = 900.0     // RMS energy threshold for speech detection (increased from 500.0 to avoid noise)
) {
    val frameSize: Int get() = sampleRate * frameMs / 1000      // 320 samples
    val minSamples: Int get() = sampleRate * minSegmentMs / 1000 // 6400
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

    fun reset() {
        state = VADState.SILENCE
        speechFrameCount = 0
        silenceFrameCount = 0
        segmentBuffer.clear()
        segmentSampleCount = 0
    }

    /**
     * Energy-based speech detection fallback.
     * Returns true if the RMS energy of the frame exceeds the configured threshold.
     */
    private fun isSpeechEnergy(frameShorts: ShortArray): Boolean {
        var sumSquares = 0.0
        for (s in frameShorts) {
            sumSquares += s.toDouble() * s.toDouble()
        }
        val rms = sqrt(sumSquares / frameShorts.size)
        return rms > config.energyThreshold
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
                        state = VADState.SPEECH_COLLECTING
                        silenceFrameCount = 0
                    }
                } else {
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
