package com.example.voxa.utils

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener

/** Small boundary around the installed service, also used by lifecycle regression tests. */
interface PracticeSpeechClient {
    fun setRecognitionListener(listener: RecognitionListener)
    fun startListening(request: Intent)
    fun stopListening()
    fun cancel()
    fun destroy()
}

/** Speech-service configuration shared by the working main and justinbeiber flows. */
object PracticeSpeechRecognizer {
    fun client(context: Context): PracticeSpeechClient? = create(context)?.let { recognizer ->
        object : PracticeSpeechClient {
            override fun setRecognitionListener(listener: RecognitionListener) = recognizer.setRecognitionListener(listener)
            override fun startListening(request: Intent) = recognizer.startListening(request)
            override fun stopListening() = recognizer.stopListening()
            override fun cancel() = recognizer.cancel()
            override fun destroy() = recognizer.destroy()
        }
    }
    fun create(context: Context): SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null

    fun request(context: Context, language: String): Intent {
        val english = language == "en"
        val tag = if (english) "en-US" else "ar-EG"
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES",
                if (english) arrayOf("en-US", "en-GB", "en") else arrayOf("ar-EG", "ar-SA", "ar"))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Let the installed service choose its online/offline backend, as on justinbeiber.
        }
    }
}
