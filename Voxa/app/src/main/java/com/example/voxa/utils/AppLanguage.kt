package com.example.voxa.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale

/** Use the same stored language when entering the app and when changing it in setup/settings. */
object AppLanguage {
    fun localizedContext(base: Context, settings: SharedPreferences = base.getSharedPreferences("voxa_settings", Context.MODE_PRIVATE)): Context {
        val language = settings.getString("app_language", "en").takeIf { it in setOf("en", "ar") } ?: "en"
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(configuration)
    }

    fun select(context: Context, language: String) {
        require(language in setOf("en", "ar"))
        context.getSharedPreferences("voxa_settings", Context.MODE_PRIVATE).edit().putString("app_language", language).apply()
        if (context.resources.configuration.locales[0].language == language) return
        var candidate = context
        while (candidate is ContextWrapper) {
            if (candidate is Activity) {
                // MainActivity is singleTask: startActivity would only call onNewIntent and keep stale resources.
                candidate.recreate()
                return
            }
            val next = candidate.baseContext
            if (next === candidate) return
            candidate = next
        }
    }
}
