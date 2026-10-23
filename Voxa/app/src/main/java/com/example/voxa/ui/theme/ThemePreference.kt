package com.example.voxa.ui.theme

import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*

data class ThemePreference(val isDark: Boolean, val toggle: () -> Unit)

/** One preference source for the dashboard switch, theme, and system bars. */
@Composable
fun rememberThemePreference(settings: SharedPreferences): ThemePreference {
    val systemDark = isSystemInDarkTheme()
    var mode by remember(settings) { mutableStateOf(settings.getString("theme_mode", "system")) }
    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    return ThemePreference(dark) {
        mode = if (dark) "light" else "dark"
        settings.edit().putString("theme_mode", mode).apply()
    }
}
