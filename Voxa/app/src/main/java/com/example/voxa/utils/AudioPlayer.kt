package com.example.voxa.utils

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
    private var mediaCompletion: (() -> Unit)? = null
    private val speechCompletions = ConcurrentHashMap<String, () -> Unit>()
    private var tts: TextToSpeech? = null
    @Volatile
    internal var isTtsReady = false
        private set

    init {
        // Initialize TTS as a fallback
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { finishSpeech(utteranceId) }
                    @Deprecated("Legacy TTS error callback")
                    override fun onError(utteranceId: String?) { finishSpeech(utteranceId) }
                    override fun onError(utteranceId: String?, errorCode: Int) { finishSpeech(utteranceId) }
                    override fun onStop(utteranceId: String?, interrupted: Boolean) { finishSpeech(utteranceId) }
                })
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
    fun playTranslation(audioAssetPath: String, gender: String, outputPhrase: String, onComplete: (() -> Unit)? = null) {
        // Release any previous playback
        releaseMediaPlayer()
        tts?.stop()

        // Resolve the gendered audio path
        val genderFolder = if (gender == "Female") "girl" else "boy"
        val assetPath = "audio/$genderFolder/$audioAssetPath"

        try {
            // Try loading from assets
            val player = MediaPlayer()
            mediaPlayer = player
            mediaCompletion = onComplete
            context.assets.openFd(assetPath).use { afd ->
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.declaredLength)
            }
            player.setOnCompletionListener { if (mediaPlayer === player) releaseMediaPlayer() }
            player.setOnErrorListener { _, _, _ ->
                if (mediaPlayer === player) releaseMediaPlayer()
                true
            }
            player.prepare()
            player.start()
            Log.d(TAG, "Playing translation: $assetPath")
        } catch (e: Exception) {
            Log.w(TAG, "Audio asset not found: $assetPath — falling back to TTS")
            // The same request continues through TTS; it has not finished yet.
            mediaCompletion = null
            releaseMediaPlayer()
            // Fallback to TTS
            speakWord(outputPhrase, "ar", gender, onComplete)
        }
    }

    /**
     * Preprocesses Arabic text to apply Tashkeel vowelization diacritics for Egyptian dialect pronunciation.
     */
    private fun applyTashkeel(text: String): String {
        val wordDiacritics = mapOf(
            "مية" to "مَيَّة",
            "عايز" to "عَايِز",
            "عايزة" to "عَايْزَة",
            "أكل" to "أَكْل",
            "اشرب" to "اِشْرَب",
            "أنا" to "أَنَا",
            "بابا" to "بَابَا",
            "ماما" to "مَامَا",
            "حمام" to "حَمَّام",
            "تعبان" to "تَعْبَان",
            "نام" to "نَام",
            "بردان" to "بَرْدَان",
            "حران" to "حَرَّان"
        )
        var processedText = text
        for ((word, diacriticWord) in wordDiacritics) {
            processedText = processedText.replace(word, diacriticWord)
        }
        return processedText
    }

    /**
     * Speaks the given word/phrase using Android's Text-To-Speech engine.
     * Dynamically sets language to English (Locale.US) or Egyptian Arabic (Locale("ar", "EG")),
     * applies Arabic Tashkeel when appropriate, and selects a gender-matching voice.
     */
    fun speakWord(text: String, language: String = "ar", gender: String = "Male", onComplete: (() -> Unit)? = null) {
        releaseMediaPlayer()
        val utteranceId = UUID.randomUUID().toString()
        if (onComplete != null) speechCompletions[utteranceId] = onComplete
        try {
            if (!isTtsReady || tts == null) {
                Log.w(TAG, "TTS not ready, cannot speak: $text")
                finishSpeech(utteranceId)
                return
            }
            val isEn = language.equals("en", ignoreCase = true)
            val targetLocale = if (isEn) Locale.US else Locale("ar", "EG")
            tts?.language = targetLocale

            val voices = tts?.voices
            if (voices != null) {
                val isFemale = gender.equals("Female", ignoreCase = true)
                val langPrefix = if (isEn) "en" else "ar"
                val matchVoice = voices.find { voice ->
                    val locale = voice.locale
                    locale.language.startsWith(langPrefix, ignoreCase = true) && (
                        if (isFemale) {
                            voice.name.contains("female", ignoreCase = true) ||
                            voice.name.contains("are", ignoreCase = true) ||
                            voice.name.contains("arf", ignoreCase = true) ||
                            voice.name.contains("en-us-x-sfg", ignoreCase = true)
                        } else {
                            voice.name.contains("male", ignoreCase = true) ||
                            voice.name.contains("ard", ignoreCase = true) ||
                            voice.name.contains("arc", ignoreCase = true) ||
                            voice.name.contains("en-us-x-iol", ignoreCase = true)
                        }
                    )
                } ?: voices.find { it.locale.language.startsWith(langPrefix, ignoreCase = true) }

                if (matchVoice != null) {
                    tts?.voice = matchVoice
                    Log.d(TAG, "Selected TTS Voice for $language: ${matchVoice.name}")
                }
            }

            val textToSpeak = if (!isEn) applyTashkeel(text) else text
            if (tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, utteranceId) != TextToSpeech.SUCCESS) {
                finishSpeech(utteranceId)
            }
            Log.d(TAG, "TTS speaking ($language): $textToSpeak")
        } catch (e: Exception) {
            Log.e(TAG, "Speech playback failed", e)
            finishSpeech(utteranceId)
        }
    }

    private fun finishSpeech(utteranceId: String?) {
        if (utteranceId != null) speechCompletions.remove(utteranceId)?.invoke()
    }

    private fun releaseMediaPlayer() {
        val player = mediaPlayer
        val completion = mediaCompletion
        mediaPlayer = null
        mediaCompletion = null
        try {
            player?.stop()
        } catch (e: Exception) {
            // Ignore — player may already be released
        } finally {
            runCatching { player?.release() }
            completion?.invoke()
        }
    }

    fun release() {
        releaseMediaPlayer()
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
        speechCompletions.keys.toList().forEach { finishSpeech(it) }
    }
}
