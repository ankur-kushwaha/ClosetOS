package com.closetos.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closetos.app.ui.theme.*

@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    useGoldBorder: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderBrush = if (useGoldBorder) {
        Brush.linearGradient(listOf(AccentGold, AccentGoldMuted))
    } else {
        Brush.linearGradient(listOf(GlassBorder, Color(0x05FFFFFF)))
    }
    
    val borderThickness = if (useGoldBorder) 1.dp else 0.5.dp

    Column(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = ShadowColor,
                spotColor = ShadowColor
            )
            .clip(RoundedCornerShape(16.dp))
            .background(GlassOverlay)
            .border(
                width = borderThickness,
                brush = borderBrush,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        content = content
    )
}

@Composable
fun ElegantButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSecondary: Boolean = false,
    icon: ImageVector? = null
) {
    val gradientBrush = Brush.linearGradient(
        colors = if (enabled) {
            listOf(AccentGold, AccentGoldMuted)
        } else {
            listOf(Color(0xFF333336), Color(0xFF222224))
        }
    )

    if (!isSecondary) {
        Box(
            modifier = modifier
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(gradientBrush)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ObsidianBg,
                        modifier = Modifier.size(18.dp).padding(end = 6.dp)
                    )
                }
                Text(
                    text = text,
                    fontFamily = OutfitFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (enabled) ObsidianBg else TextMuted
                )
            }
        }
    } else {
        // Gold outlined button
        Box(
            modifier = modifier
                .height(48.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(listOf(AccentGold, AccentGoldMuted)),
                    shape = RoundedCornerShape(24.dp)
                )
                .clip(RoundedCornerShape(24.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(18.dp).padding(end = 6.dp)
                    )
                }
                Text(
                    text = text,
                    fontFamily = OutfitFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = AccentGold
                )
            }
        }
    }
}

@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val bg = if (isSelected) AccentGold else GlassOverlay
    val textColor = if (isSelected) ObsidianBg else TextLight
    val borderBrush = if (isSelected) {
        Brush.linearGradient(listOf(AccentGold, AccentGoldMuted))
    } else {
        Brush.linearGradient(listOf(GlassBorder, GlassBorder))
    }

    val finalModifier = if (onClick != null) {
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    } else {
        modifier
    }

    Box(
        modifier = finalModifier
            .background(bg)
            .border(0.5.dp, borderBrush, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = OutfitFont,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.displayMedium,
            fontFamily = PlayfairFont
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Divider(
            color = GoldBorder,
            thickness = 0.5.dp,
            modifier = Modifier.fillMaxWidth(0.2f)
        )
    }
}
