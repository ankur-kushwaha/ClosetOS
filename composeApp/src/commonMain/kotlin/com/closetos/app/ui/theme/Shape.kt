package com.closetos.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp), // Main glassmorphic card corners
    large = RoundedCornerShape(24.dp), // Outer dialogue/sheet corners
    extraLarge = RoundedCornerShape(32.dp)
)
