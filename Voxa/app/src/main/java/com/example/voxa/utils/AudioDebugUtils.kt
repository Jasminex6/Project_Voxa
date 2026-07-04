package com.example.voxa.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioDebugUtils {
    /**
     * Saves a FloatArray audio buffer to a standard RIFF WAV file for debugging.
     * Float values [-1.0, 1.0] are converted back to 16-bit PCM Short [-32768, 32767].
     */
    fun saveDebugWav(audioFloats: FloatArray, context: Context, fileName: String) {
        val wavFile = File(context.getExternalFilesDir(null), "$fileName.wav")
        
        // Convert Float [-1.0, 1.0] back to Short [-32768, 32767]
        val shortAudio = ShortArray(audioFloats.size) { 
            (audioFloats[it] * 32767).toInt().coerceIn(-32768, 32767).toShort() 
        }
        
        val sampleRate = 16000
        val channels = 1
        val bitRate = 16
        
        val byteData = ByteArray(shortAudio.size * 2)
        ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shortAudio)
        
        val totalAudioLen = byteData.size
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitRate / 8

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF/WAVE header
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * bitRate / 8).toByte() // block align
        header[33] = 0
        header[34] = bitRate.toByte() // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()

        try {
            val out = FileOutputStream(wavFile)
            out.write(header, 0, 44)
            out.write(byteData)
            out.close()
            SafeLog.d("VoxaDebug", "Saved debug audio to: ${wavFile.absolutePath}")
        } catch (e: Exception) {
            SafeLog.e("VoxaDebug", "Error saving debug audio", e)
        }
    }
}
