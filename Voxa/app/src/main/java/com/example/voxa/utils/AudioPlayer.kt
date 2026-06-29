package com.example.voxa.utils

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

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
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                // Try setting Arabic locale
                val arabicLocale = Locale("ar", "EG")
                val result = tts?.setLanguage(arabicLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fall back to default Arabic
                    tts?.setLanguage(Locale("ar"))
                }
            }
        }
    }

    /**
     * Plays the translation audio for a matched intent.
     *
     * @param audioAssetPath The gender-neutral filename (e.g. "water.mp3")
     * @param gender The active profile's gender ("Male" or "Female")
     * @param outputPhrase The Arabic translation phrase (used as TTS fallback)
     */
    fun playTranslation(audioAssetPath: String, gender: String, outputPhrase: String) {
        // Release any previous playback
        releaseMediaPlayer()

        // Resolve the gendered audio path
        val genderFolder = if (gender == "Female") "girl" else "boy"
        val assetPath = "audio/$genderFolder/$audioAssetPath"

        try {
            // Try loading from assets
            val afd = context.assets.openFd(assetPath)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.declaredLength)
                afd.close()
                prepare()
                setOnCompletionListener { releaseMediaPlayer() }
                start()
            }
            Log.d(TAG, "Playing translation: $assetPath")
        } catch (e: Exception) {
            Log.w(TAG, "Audio asset not found: $assetPath — falling back to TTS")
            // Fallback to TTS
            speakWithTts(outputPhrase)
        }
    }

    /**
     * Uses Android's Text-To-Speech engine to speak the Arabic phrase.
     */
    private fun speakWithTts(text: String) {
        if (isTtsReady && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voxa_translation")
            Log.d(TAG, "TTS speaking: $text")
        } else {
            Log.w(TAG, "TTS not ready, cannot speak: $text")
        }
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
