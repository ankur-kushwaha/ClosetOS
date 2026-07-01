package com.closetos.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

var isDarkThemeGlobal by mutableStateOf(false) // Default to light theme

// Airbnb Color Palette
val AirbnbCoral = Color(0xFFFF385C)
val AirbnbTeal = Color(0xFF008489)
val AirbnbDarkText = Color(0xFF222222)
val AirbnbMutedText = Color(0xFF717171)
val AirbnbBorder = Color(0xFFEBEBEB)
val AirbnbLightBg = Color(0xFFF7F7F7)

val ObsidianBg: Color
    get() = if (isDarkThemeGlobal) Color(0xFF121212) else AirbnbLightBg

val CharcoalSurface: Color
    get() = if (isDarkThemeGlobal) Color(0xFF222222) else Color(0xFFFFFFFF)

val CardSurface: Color
    get() = if (isDarkThemeGlobal) Color(0xFF2D2D2D) else Color(0xFFFFFFFF)

val AccentGold = AirbnbCoral
val AccentGoldMuted = Color(0xFFE03152)

val TextLight: Color
    get() = if (isDarkThemeGlobal) Color(0xFFF5F5F7) else AirbnbDarkText

val TextMuted: Color
    get() = if (isDarkThemeGlobal) Color(0xFF9A9A9E) else AirbnbMutedText

// Glassmorphism replaced with flat card overlays
val GlassOverlay: Color
    get() = if (isDarkThemeGlobal) Color(0xFF2C2C2C) else Color(0xFFFFFFFF)

val GlassBorder: Color
    get() = if (isDarkThemeGlobal) Color(0x1AFFFFFF) else AirbnbBorder

val GoldBorder = AirbnbBorder
val ShadowColor = Color(0x0A000000) // Much softer shadows

val ErrorColor = Color(0xFFCF6679)
val SuccessColor = Color(0xFF4BB543)
val InfoBlue = AirbnbTeal

