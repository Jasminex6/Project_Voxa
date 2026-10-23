package com.example.voxa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkBackground,
    secondary = DarkSecondary,
    onSecondary = DarkBackground,
    tertiary = DarkTertiary,
    onTertiary = DarkPrimaryText,
    background = DarkBackground,
    onBackground = DarkPrimaryText,
    surface = DarkSurface,
    onSurface = DarkPrimaryText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkSecondaryText,
    outline = DarkBorder,
    outlineVariant = DarkSurfaceVariant,
    error = ErrorRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimaryPurple,
    onPrimary = Color.White,
    secondary = LightNeonCyan,
    onSecondary = LightPrimaryText,
    tertiary = LightDeepPurple,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightPrimaryText,
    surface = LightSurface,
    onSurface = LightPrimaryText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightSecondaryText,
    outline = LightBorder,
    outlineVariant = LightSurfaceVariant,
    error = ErrorRed,
    onError = Color.White
)

@Immutable
data class VoxaExtendedColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val border: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val primaryAction: Color,
    val onPrimaryAction: Color,
    val microphone: Color,
    val microphoneOuterRing: Color,
    val microphoneGlow: Color,
    val normalCard: Color,
    val headerContainer: Color,
    val textFieldBackground: Color,
    val bottomNavigationSurface: Color,
    val statisticCard: Color,
    val glassBorder: Color,
    val inputBorder: Color,
    val bottomNavigationDivider: Color,
    val innerGlassHighlight: Color,
    val shadow: Color,
    val backgroundWash: Color,
    val successCard: Color,
    val errorCard: Color,
    val selectedProfileSurface: Color
)

private val DarkExtendedColors = VoxaExtendedColors(
    isDark = true,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    border = DarkBorder,
    primaryText = DarkPrimaryText,
    secondaryText = DarkSecondaryText,
    primaryAction = DarkPrimary,
    onPrimaryAction = DarkBackground,
    microphone = Color(0xFF00F2FE),
    microphoneOuterRing = Color(0xFF00F2FE),
    microphoneGlow = Color(0xFF00F2FE),
    normalCard = DarkSurface,
    headerContainer = DarkSurface,
    textFieldBackground = Color.Transparent,
    bottomNavigationSurface = DarkSurface,
    statisticCard = DarkSurface,
    glassBorder = DarkSurfaceVariant,
    inputBorder = DarkBorder,
    bottomNavigationDivider = DarkSurfaceVariant,
    innerGlassHighlight = Color.Transparent,
    shadow = Color.Transparent,
    backgroundWash = Color.Transparent,
    successCard = Color(0xFF0C2417),
    errorCard = Color(0xFF2C1919),
    selectedProfileSurface = SelectedActiveBlue
)

private val LightExtendedColors = VoxaExtendedColors(
    isDark = false,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    border = LightBorder,
    primaryText = LightPrimaryText,
    secondaryText = LightSecondaryText,
    primaryAction = LightPrimaryPurple,
    onPrimaryAction = Color.White,
    microphone = LightNeonCyan,
    microphoneOuterRing = LightNeonCyan.copy(alpha = 0.14f),
    microphoneGlow = LightNeonCyan.copy(alpha = 0.22f),
    normalCard = LightSurface.copy(alpha = 0.78f),
    headerContainer = LightSurface.copy(alpha = 0.76f),
    textFieldBackground = LightSurface.copy(alpha = 0.68f),
    bottomNavigationSurface = LightSurface.copy(alpha = 0.46f),
    statisticCard = LightSurface.copy(alpha = 0.75f),
    glassBorder = LightBorder.copy(alpha = 0.58f),
    inputBorder = LightBorder.copy(alpha = 0.62f),
    bottomNavigationDivider = LightBorder.copy(alpha = 0.55f),
    innerGlassHighlight = Color.White.copy(alpha = 0.52f),
    shadow = Color(0xFF2A1645).copy(alpha = 0.18f),
    backgroundWash = LightPrimaryPurple.copy(alpha = 0.10f),
    successCard = SuccessGreen.copy(alpha = 0.14f),
    errorCard = ErrorRed.copy(alpha = 0.10f),
    selectedProfileSurface = LightSurfaceVariant
)

val LocalVoxaColors = staticCompositionLocalOf { DarkExtendedColors }

@Composable
fun VoxaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalVoxaColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
