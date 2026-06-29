package com.example.voxa.utils

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * 🎙️ AudioFileHelper
 * Handles raw 16kHz PCM audio template recording, silence trimming,
 * duration constraint verification, and disk serialization.
 */
object AudioFileHelper {
    private const val SAMPLE_RATE = 16000
    private const val SILENCE_THRESHOLD = 300 // Lowered from 500 to capture normal speech sensitivity

    /**
     * Trims leading and trailing silence from the raw PCM buffer using a robust window-based average amplitude.
     * This avoids clipping on transient clicks/rustles and ensures clean templates.
     */
    fun trimSilence(pcmData: ShortArray): ShortArray {
        val windowSize = 160 // 10ms window at 16kHz
        val threshold = 350  // average absolute amplitude threshold
        
        var start = 0
        while (start + windowSize <= pcmData.size) {
            var sum = 0L
            for (i in 0 until windowSize) {
                sum += abs(pcmData[start + i].toInt())
            }
            if (sum / windowSize >= threshold) {
                break
            }
            start += 16 // 1ms step
        }

        var end = pcmData.size
        while (end - windowSize >= start) {
            var sum = 0L
            for (i in 0 until windowSize) {
                sum += abs(pcmData[end - windowSize + i].toInt())
            }
            if (sum / windowSize >= threshold) {
                break
            }
            end -= 16 // 1ms step
        }

        if (start >= end) {
            return ShortArray(0)
        }

        return pcmData.copyOfRange(start, end)
    }

    /**
     * Validates that the trimmed PCM data duration is between 200ms (0.2s) and 4000ms (4.0s).
     * Throws IllegalArgumentException if validation fails.
     */
    fun validateDuration(pcmData: ShortArray) {
        val durationMs = (pcmData.size.toFloat() / SAMPLE_RATE) * 1000f
        if (durationMs < 200) {
            throw IllegalArgumentException("Audio too short (${durationMs.toInt()}ms). Vocalization must be at least 200ms.")
        }
        if (durationMs > 2500) {
            throw IllegalArgumentException("Audio too long (${durationMs.toInt()}ms). Vocalization must be under 2500ms.")
        }
    }

    /**
     * Saves a PCM ShortArray to a file in the internal cache directory.
     * Returns the absolute path to the saved file.
     */
    fun savePcmFile(context: Context, pcmData: ShortArray, fileName: String): String {
        val file = File(context.cacheDir, fileName)
        
        // Convert ShortArray to ByteArray (little-endian byte order)
        val byteBuffer = ByteBuffer.allocate(pcmData.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in pcmData) {
            byteBuffer.putShort(sample)
        }

        try {
            FileOutputStream(file).use { fos ->
                fos.write(byteBuffer.array())
            }
        } catch (e: IOException) {
            throw IOException("Failed to save audio file: ${e.message}", e)
        }

        return file.absolutePath
    }

    /**
     * Reads a PCM file from disk back into a ShortArray.
     */
    fun readPcmFile(file: File): ShortArray {
        if (!file.exists()) {
            throw IOException("Audio file does not exist: ${file.absolutePath}")
        }
        val size = file.length().toInt()
        val bytes = ByteArray(size)
        try {
            FileInputStream(file).use { fis ->
                fis.read(bytes)
            }
        } catch (e: IOException) {
            throw IOException("Failed to read audio file: ${e.message}", e)
        }

        val shortBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val shorts = ShortArray(shortBuffer.limit())
        shortBuffer.get(shorts)
        return shorts
    }
}
