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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closetos.app.data.model.Garment
import com.closetos.app.data.model.Outfit
import com.closetos.app.data.model.LookbookCollection
import com.closetos.app.data.model.TryOnResult
import com.closetos.app.decodeBase64ToBitmap
import com.closetos.app.rememberImageBitmap
import com.closetos.app.ui.theme.*
import kotlin.math.pow

@Composable
fun rememberTryOnBitmap(result: TryOnResult?): ImageBitmap? {
    val path = result?.imagePath.orEmpty()
    val base64 = result?.imageBase64.orEmpty()
    val pathBitmap = rememberImageBitmap(path)
    val base64Bitmap = remember(base64) {
        if (base64.isNotBlank()) decodeBase64ToBitmap(base64) else null
    }
    return pathBitmap ?: base64Bitmap
}

@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    useGoldBorder: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderThickness = 1.dp
    val borderColor = if (useGoldBorder) AccentGold else GlassBorder

    Column(
        modifier = modifier
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = ShadowColor,
                spotColor = ShadowColor
            )
            .clip(RoundedCornerShape(12.dp))
            .background(GlassOverlay)
            .border(
                width = borderThickness,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
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
    val buttonBgColor = if (enabled) {
        AccentGold
    } else {
        if (isDarkThemeGlobal) Color(0xFF333336) else Color(0xFFEBEBEB)
    }

    if (!isSecondary) {
        Box(
            modifier = modifier
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(buttonBgColor)
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
                        tint = Color.White,
                        modifier = Modifier.size(18.dp).padding(end = 6.dp)
                    )
                }
                Text(
                    text = text,
                    fontFamily = OutfitFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (enabled) Color.White else TextMuted
                )
            }
        }
    } else {
        // Outlined button
        Box(
            modifier = modifier
                .height(48.dp)
                .border(
                    width = 1.dp,
                    color = if (isDarkThemeGlobal) Color(0x33FFFFFF) else AirbnbBorder,
                    shape = RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
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
                        tint = TextLight,
                        modifier = Modifier.size(18.dp).padding(end = 6.dp)
                    )
                }
                Text(
                    text = text,
                    fontFamily = OutfitFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = TextLight
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
    val bg = if (isSelected) {
        AccentGold
    } else {
        if (isDarkThemeGlobal) Color(0xFF2C2C2C) else Color(0xFFF0F0F0)
    }
    
    val textColor = if (isSelected) {
        Color.White
    } else {
        TextLight
    }

    val finalModifier = if (onClick != null) {
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    } else {
        modifier
    }

    Box(
        modifier = finalModifier
            .background(bg)
            .border(
                width = 1.dp,
                color = if (isSelected) AccentGold else (if (isDarkThemeGlobal) Color(0x33FFFFFF) else AirbnbBorder),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
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
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            fontFamily = PlayfairFont
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(
            color = if (isDarkThemeGlobal) Color(0x1AFFFFFF) else AirbnbBorder,
            thickness = 1.dp,
            modifier = Modifier.fillMaxWidth()
        )
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

internal fun garmentCategoryIcon(category: String): ImageVector = when (category) {
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

@Composable
fun StarRating(score: Float, modifier: Modifier = Modifier) {
    val stars = (score * 5f).coerceIn(0f, 5f)
    val fullStars = stars.toInt()
    val hasHalf = stars - fullStars >= 0.5f
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(5) { index ->
            val tint = when {
                index < fullStars -> AccentGold
                index == fullStars && hasHalf -> AccentGold.copy(alpha = 0.5f)
                else -> TextMuted.copy(alpha = 0.35f)
            }
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun OutfitPreviewStack(
    outfit: Outfit,
    modifier: Modifier = Modifier
) {
    val ordered = outfit.garments.sortedBy { garment ->
        when (garment.category) {
            "Top" -> 0
            "Bottom" -> 1
            "Outerwear" -> 2
            "Shoes" -> 3
            else -> 4
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
    ) {
        if (ordered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Checkroom, contentDescription = null, tint = TextMuted)
            }
        } else {
            when (ordered.size) {
                1 -> {
                    GarmentThumbnail(
                        garment = ordered[0],
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 8
                    )
                }
                2 -> {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        GarmentThumbnail(
                            garment = ordered[0],
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            cornerRadius = 8
                        )
                        GarmentThumbnail(
                            garment = ordered[1],
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            cornerRadius = 8
                        )
                    }
                }
                3 -> {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        GarmentThumbnail(
                            garment = ordered[0],
                            modifier = Modifier.weight(1.2f).fillMaxHeight(),
                            cornerRadius = 8
                        )
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            GarmentThumbnail(
                                garment = ordered[1],
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                cornerRadius = 8
                            )
                            GarmentThumbnail(
                                garment = ordered[2],
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                cornerRadius = 8
                            )
                        }
                    }
                }
                else -> { // 4 or more
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            GarmentThumbnail(
                                garment = ordered[0],
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                cornerRadius = 8
                            )
                            GarmentThumbnail(
                                garment = ordered[1],
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                cornerRadius = 8
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            GarmentThumbnail(
                                garment = ordered[2],
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                cornerRadius = 8
                            )
                            GarmentThumbnail(
                                garment = ordered[3],
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                cornerRadius = 8
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OutfitCard(
    outfit: Outfit,
    modifier: Modifier = Modifier,
    temperatureC: Float = outfit.temperatureC,
    onClick: () -> Unit = {},
    onFavorite: () -> Unit = {},
    onWearToday: () -> Unit = {},
    onTryOn: () -> Unit = {},
    onSave: () -> Unit = {},
    compact: Boolean = false
) {
    val cardWidth = if (compact) 200.dp else 260.dp
    val previewHeight = if (compact) 180.dp else 220.dp

    Column(
        modifier = modifier
            .width(cardWidth)
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight)
                .background(
                    if (isDarkThemeGlobal) Color(0xFF1E1E24) else Color(0xFFF3F3F5)
                )
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            OutfitPreviewStack(
                outfit = outfit,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = outfit.name,
                fontFamily = PlayfairFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            StarRating(score = outfit.overallScore)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Worn ${outfit.wornCount} times", fontFamily = OutfitFont, fontSize = 11.sp, color = TextMuted)
                    Text(
                        text = "Cost / Wear",
                        fontFamily = OutfitFont,
                        fontSize = 10.sp,
                        color = TextMuted.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "$${formatCost(outfit.avgCostPerWear)}",
                        fontFamily = OutfitFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = AccentGold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WbSunny, contentDescription = null, tint = AccentGold, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${temperatureC.toInt()}°C",
                            fontFamily = OutfitFont,
                            fontSize = 12.sp,
                            color = TextLight
                        )
                    }
                    Text("Weather", fontFamily = OutfitFont, fontSize = 10.sp, color = TextMuted.copy(alpha = 0.7f))
                }
            }

            if (!compact) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onFavorite, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (outfit.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (outfit.isFavorite) Color(0xFFFF4081) else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onWearToday, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Checkroom, contentDescription = "Wear Today", tint = AccentGold, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onTryOn, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = "Try On", tint = AccentGold, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onSave, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (outfit.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Save",
                            tint = if (outfit.isSaved) AccentGold else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun formatCost(value: Double): String {
    val whole = value.toInt()
    val frac = ((value - whole) * 100).toInt().coerceIn(0, 99)
    return "$whole.${frac.toString().padStart(2, '0')}"
}

@Composable
fun CollectionPlaylistCard(
    collection: LookbookCollection,
    outfitCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AccentGold.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = collection.name.take(1).uppercase(),
                fontFamily = PlayfairFont,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = AccentGold
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = collection.name,
            fontFamily = OutfitFont,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = TextLight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "$outfitCount looks",
            fontFamily = OutfitFont,
            fontSize = 11.sp,
            color = TextMuted
        )
    }
}

@Composable
fun LookbookSectionHeader(
    title: String,
    emoji: String? = null,
    onSeeAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (emoji != null) "$emoji $title" else title,
            fontFamily = PlayfairFont,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = TextLight
        )
        if (onSeeAll != null) {
            Text(
                text = "See All >",
                fontFamily = OutfitFont,
                fontSize = 13.sp,
                color = AccentGold,
                modifier = Modifier.clickable(onClick = onSeeAll)
            )
        }
    }
}

@Composable
fun LookbookSearchBar(
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(GlassOverlay)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        if (query.isEmpty()) {
            Text("Search outfits", fontFamily = OutfitFont, fontSize = 15.sp, color = TextMuted)
        } else {
            Text(query, fontFamily = OutfitFont, fontSize = 15.sp, color = TextLight)
        }
    }
}
