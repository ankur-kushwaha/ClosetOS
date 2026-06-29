package com.closetos.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentGold,
    onPrimary = ObsidianBg,
    secondary = AccentGoldMuted,
    onSecondary = ObsidianBg,
    background = ObsidianBg,
    onBackground = TextLight,
    surface = CharcoalSurface,
    onSurface = TextLight,
    error = ErrorColor,
    onError = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = AccentGoldMuted,
    onPrimary = Color.White,
    secondary = AccentGold,
    onSecondary = Color.White,
    background = Color(0xFFF9F9FB),
    onBackground = ObsidianBg,
    surface = Color.White,
    onSurface = ObsidianBg
)

@Composable
fun ClosetOSTheme(
    darkTheme: Boolean = isSystemInDarkTheme() || true, // Force premium dark mode by default
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
