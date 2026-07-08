package com.example.voxa.utils

import android.content.Context
import android.media.MediaPlayer
import android.media.AudioTrack
import android.media.AudioFormat
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import java.io.File

/**
 * 🔊 AudioPlayer — Translation Playback Wrapper
 *
 * Plays Egyptian Arabic voice translations when an intent is matched.
 * Resolves the audio file dynamically based on the active profile's voice pack gender:
 *   - Male profile → "audio/boy/{audioAssetPath}"
 *   - Female profile → "audio/girl/{audioAssetPath}"
 *
 * Falls back to Android TTS if the specific audio file is missing.
 */
class AudioPlayer(private val context: Context) {

    companion object {
        private const val TAG = "AudioPlayer"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    init {
        // Initialize TTS as a fallback
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                Log.d(TAG, "TTS initialized successfully")
                // Try setting Arabic locale
                val arabicLocale = Locale("ar", "EG")
                val result = tts?.setLanguage(arabicLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fall back to default Arabic
                    tts?.setLanguage(Locale("ar"))
                }
            } else {
                Log.e(TAG, "TTS initialization failed with status $status")
            }
        }
    }

    fun playPcmFile(file: File): Boolean {
        if (!file.exists()) {
            Log.w(TAG, "PCM preview file does not exist: ${file.absolutePath}")
            return false
        }
        return try {
            val pcmData = com.example.voxa.utils.AudioFileHelper.readPcmFile(file)
            val minBufSize = AudioTrack.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                16000,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBufSize, pcmData.size * 2),
                AudioTrack.MODE_STATIC
            )
            audioTrack.write(pcmData, 0, pcmData.size)
            audioTrack.play()
            Log.d(TAG, "AudioTrack static playback started for local PCM file: ${file.absolutePath}")
            
            // Release after playback is done
            Thread {
                val durationMs = (pcmData.size.toFloat() / 16000f) * 1000f
                try {
                    Thread.sleep(durationMs.toLong() + 200)
                    audioTrack.stop()
                    audioTrack.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping AudioTrack: ${e.message}")
                }
            }.start()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play PCM file: ${e.message}", e)
            false
        }
    }

    fun playTranslation(audioAssetPath: String, gender: String, outputPhrase: String) {
        Log.d(TAG, "playTranslation invoked for asset path: $audioAssetPath, gender: $gender, phrase: $outputPhrase")
        // Release any previous playback
        releaseMediaPlayer()

        // Resolve the gendered audio path
        val genderFolder = if (gender.equals("Female", ignoreCase = true)) "girl" else "boy"
        val assetPath = "audio/$genderFolder/$audioAssetPath"

        try {
            // Try loading from assets
            val afd = context.assets.openFd(assetPath)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.declaredLength)
                afd.close()
                prepare()
                setOnCompletionListener { releaseMediaPlayer() }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error playing asset $assetPath: what=$what, extra=$extra. Falling back to TTS.")
                    releaseMediaPlayer()
                    speakFallback(outputPhrase, "ar", gender)
                    true
                }
                start()
            }
            Log.d(TAG, "Playing translation: $assetPath")
        } catch (e: Exception) {
            Log.w(TAG, "Audio asset not found: $assetPath — falling back to TTS")
            // Fallback to TTS
            speakFallback(outputPhrase, "ar", gender)
        }
    }

    /**
     * Uses Android's Text-To-Speech engine to speak the phrase.
     * Respects the child's profile gender, falls back gracefully, and applies Tashkeel to Arabic text.
     */
    fun speakFallback(text: String, language: String, gender: String?) {
        if (isTtsReady && tts != null) {
            try {
                // Determine locale based on explicitly passed language or text content
                val isArabic = language.equals("ar", ignoreCase = true) || isArabicText(text)
                val locale = if (isArabic) {
                    Locale("ar", "EG")
                } else {
                    Locale.ENGLISH
                }

                val result = tts?.setLanguage(locale)
                if (isArabic && (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED)) {
                    tts?.setLanguage(Locale("ar"))
                }

                // Try selecting a matching voice based on language and gender
                try {
                    val voices = tts?.voices
                    if (!voices.isNullOrEmpty()) {
                        // Diagnostic Log: Print all available voices to Logcat
                        Log.d(TAG, "Listing all ${voices.size} available TTS voices on device:")
                        for (v in voices) {
                            Log.d(TAG, "  - Voice Name: ${v.name}, Locale: ${v.locale}, Offline: ${!v.isNetworkConnectionRequired}")
                        }

                        // Try to filter offline voices first
                        var matchingVoices = voices.filter { voice ->
                            voice.locale.language.equals(locale.language, ignoreCase = true) && !voice.isNetworkConnectionRequired
                        }
                        // If offline voices are empty, try any voice matching the language
                        if (matchingVoices.isEmpty()) {
                            matchingVoices = voices.filter { voice ->
                                voice.locale.language.equals(locale.language, ignoreCase = true)
                            }
                        }

                        if (matchingVoices.isNotEmpty()) {
                            val isFemale = gender.equals("Female", ignoreCase = true)
                            val isMale = gender.equals("Male", ignoreCase = true)

                            val selectedVoice = when {
                                isFemale -> matchingVoices.firstOrNull {
                                    it.name.contains("female", ignoreCase = true) ||
                                    it.name.contains("a-voice", ignoreCase = true) ||
                                    it.name.contains("c-voice", ignoreCase = true)
                                } ?: matchingVoices.firstOrNull()

                                isMale -> matchingVoices.firstOrNull {
                                    it.name.contains("male", ignoreCase = true) ||
                                    it.name.contains("boy", ignoreCase = true) ||
                                    it.name.contains("b-voice", ignoreCase = true) ||
                                    it.name.contains("d-voice", ignoreCase = true) ||
                                    it.name.contains("-x-arb", ignoreCase = true) ||
                                    it.name.contains("-x-ard", ignoreCase = true) ||
                                    it.name.contains("-m00", ignoreCase = true) ||
                                    it.name.contains("-m0", ignoreCase = true) ||
                                    it.name.contains("b-path", ignoreCase = true)
                                } ?: matchingVoices.getOrNull(1) ?: matchingVoices.firstOrNull()

                                else -> matchingVoices.firstOrNull()
                            }

                            if (selectedVoice != null) {
                                tts?.voice = selectedVoice
                                Log.d(TAG, "Selected TTS voice: ${selectedVoice.name} for gender: $gender")
                            }
                        }
                    } else {
                        Log.w(TAG, "No system voices returned by TTS engine")
                    }
                } catch (ve: Exception) {
                    Log.w(TAG, "Failed to select specific TTS voice, using system default: ${ve.message}")
                }

                // Apply Tashkeel to Arabic text
                val processedText = if (isArabic) {
                    com.example.voxa.utils.TashkeelHelper.apply(text)
                } else {
                    text
                }

                // Adjust pitch to sound child-like/gender-distinct
                val isFemale = gender.equals("Female", ignoreCase = true)
                val isMale = gender.equals("Male", ignoreCase = true)
                val pitch = if (isFemale) {
                    1.30f // Female → pitch around 1.30f
                } else if (isMale) {
                    0.70f // Male → pitch around 0.70f
                } else {
                    1.0f // Unknown → default pitch 1.0f
                }

                tts?.setPitch(pitch)
                val speechRate = if (isFemale) {
                    0.90f // Female child speech rate
                } else if (isMale) {
                    1.15f // Male child speech rate (faster rate offsets the low pitch to sound boy-like)
                } else {
                    1.0f
                }
                tts?.setSpeechRate(speechRate)

                tts?.speak(processedText, TextToSpeech.QUEUE_FLUSH, null, "voxa_translation")
                Log.d(TAG, "TTS speakFallback speaking: '$processedText' (gender=$gender, pitch=$pitch)")
            } catch (e: Exception) {
                Log.e(TAG, "Error during TTS playback: ${e.message}")
            }
        } else {
            Log.w(TAG, "TTS not ready, cannot speak: $text")
        }
    }

    private fun isArabicText(text: String): Boolean {
        for (c in text) {
            if (c.code in 0x0600..0x06FF) return true
        }
        return false
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore — player may already be released
        }
        mediaPlayer = null
    }

    fun release() {
        releaseMediaPlayer()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
