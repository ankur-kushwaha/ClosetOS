package com.closetos.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closetos.app.data.model.Garment
import com.closetos.app.ui.theme.*
import kotlin.math.pow

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

@Composable
fun rememberImageBitmap(path: String): androidx.compose.ui.graphics.ImageBitmap? {
    return remember(path) {
        try {
            val file = java.io.File(path)
            if (file.exists()) {
                android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

fun garmentPreviewColor(labColor: FloatArray): Color {
    val l = labColor.getOrElse(0) { 50f }
    val a = labColor.getOrElse(1) { 0f }
    val b = labColor.getOrElse(2) { 0f }

    var y = (l + 16f) / 116f
    var x = a / 500f + y
    var z = y - b / 200f

    fun f(t: Float): Float {
        val delta = 6f / 29f
        return if (t > delta) t.pow(3f) else 3f * delta * delta * (t - 4f / 29f)
    }

    x = 0.95047f * f(x)
    y = 1.00000f * f(y)
    z = 1.08883f * f(z)

    var r = x * 3.2406f + y * -1.5372f + z * -0.4986f
    var g = x * -0.9689f + y * 1.8758f + z * 0.0415f
    var blue = x * 0.0557f + y * -0.2040f + z * 1.0570f

    fun gamma(channel: Float): Float =
        if (channel > 0.0031308f) 1.055f * channel.pow(1f / 2.4f) - 0.055f else 12.92f * channel

    return Color(
        red = gamma(r.coerceIn(0f, 1f)),
        green = gamma(g.coerceIn(0f, 1f)),
        blue = gamma(blue.coerceIn(0f, 1f))
    )
}

private fun garmentCategoryIcon(category: String): ImageVector = when (category) {
    "Top" -> Icons.Default.Checkroom
    "Bottom" -> Icons.Default.Accessibility
    "Outerwear" -> Icons.Default.Layers
    "Shoes" -> Icons.Default.Hiking
    else -> Icons.Default.Checkroom
}

@Composable
fun GarmentThumbnail(
    garment: Garment,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    showIconFallback: Boolean = true,
    cornerRadius: Int = 8
) {
    val imagePath = garment.straightenedImageUrl.ifEmpty { garment.imageUrl }
    val bitmap = if (imagePath.isNotEmpty()) rememberImageBitmap(imagePath) else null
    val shape = RoundedCornerShape(cornerRadius.dp)

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = garment.subcategory,
            modifier = modifier.clip(shape),
            contentScale = contentScale
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(garmentPreviewColor(garment.labColor)),
            contentAlignment = Alignment.Center
        ) {
            if (showIconFallback) {
                Icon(
                    imageVector = garmentCategoryIcon(garment.category),
                    contentDescription = garment.subcategory,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxSize(0.45f)
                )
            }
        }
    }
}

@Composable
fun LookbookGarmentChip(
    garment: Garment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GlassOverlay)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GarmentThumbnail(
            garment = garment,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            contentScale = ContentScale.Crop,
            cornerRadius = 8
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = garment.subcategory,
            fontFamily = OutfitFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            color = TextLight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = garment.brand,
            fontFamily = OutfitFont,
            fontSize = 9.sp,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
