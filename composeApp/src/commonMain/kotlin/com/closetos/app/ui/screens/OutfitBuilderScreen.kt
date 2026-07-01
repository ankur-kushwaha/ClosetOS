package com.closetos.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closetos.app.data.model.Garment
import com.closetos.app.data.repository.ClosetRepository
import com.closetos.app.showToast
import com.closetos.app.ui.components.ElegantButton
import com.closetos.app.ui.components.GarmentThumbnail
import com.closetos.app.ui.components.LookbookGarmentChip
import com.closetos.app.ui.components.SectionHeader
import com.closetos.app.ui.theme.*
import kotlin.math.roundToInt

data class PlacedItem(
    val garment: Garment,
    var offsetX: Float = 0f,
    var offsetY: Float = 0f,
    var scale: Float = 1.0f,
    var rotation: Float = 0f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitBuilderScreen() {
    val allGarments by ClosetRepository.garments.collectAsState()

    var canvasWidth by remember { mutableStateOf(0f) }
    var canvasHeight by remember { mutableStateOf(0f) }

    val placedItems = remember { mutableStateListOf<PlacedItem>() }
    var selectedItemIndex by remember { mutableStateOf(-1) }

    val previewOutfit = remember(placedItems.size, placedItems.map { it.garment.id }) {
        if (placedItems.size >= 2) {
            ClosetRepository.scoreLookbookOutfit(placedItems.map { it.garment })
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(16.dp)
    ) {
        SectionHeader(
            title = "Lookbook Canvas",
            subtitle = "Drag, rotate, and style your outfits. Saved looks update your style vector."
        )

        Box(
            modifier = Modifier
                .weight(1.3f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF131317))
                .border(0.5.dp, GlassBorder, RoundedCornerShape(16.dp))
                .onGloballyPositioned { coordinates ->
                    canvasWidth = coordinates.size.width.toFloat()
                    canvasHeight = coordinates.size.height.toFloat()
                }
        ) {
            if (placedItems.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = AccentGold.copy(alpha = 0.35f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Tap garments below to build your look",
                        fontFamily = OutfitFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextLight,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Drag to position, pinch controls to scale and rotate",
                        fontFamily = OutfitFont,
                        fontSize = 12.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }

            placedItems.forEachIndexed { index, placedItem ->
                val isSelected = index == selectedItemIndex
                val borderCol = if (isSelected) AccentGold else Color.Transparent

                Box(
                    modifier = Modifier
                        .offset { IntOffset(placedItem.offsetX.roundToInt(), placedItem.offsetY.roundToInt()) }
                        .size(120.dp)
                        .scale(placedItem.scale)
                        .rotate(placedItem.rotation)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.5.dp, borderCol, RoundedCornerShape(10.dp))
                        .clickable { selectedItemIndex = index }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    placedItem.offsetX += dragAmount.x
                                    placedItem.offsetY += dragAmount.y
                                    selectedItemIndex = -1
                                    selectedItemIndex = index
                                }
                            )
                        }
                ) {
                    GarmentThumbnail(
                        garment = placedItem.garment,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        cornerRadius = 10
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                                )
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = placedItem.garment.subcategory,
                            fontFamily = OutfitFont,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextLight,
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }

            if (placedItems.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedItemIndex != -1) {
                        IconButton(
                            onClick = {
                                placedItems.removeAt(selectedItemIndex)
                                selectedItemIndex = -1
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ErrorColor.copy(alpha = 0.8f))
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Item", tint = ObsidianBg, modifier = Modifier.size(18.dp))
                        }
                    }

                    IconButton(
                        onClick = {
                            placedItems.clear()
                            selectedItemIndex = -1
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GlassOverlay)
                            .border(0.5.dp, GlassBorder, CircleShape)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Canvas", tint = TextLight, modifier = Modifier.size(18.dp))
                    }
                }

                if (selectedItemIndex != -1) {
                    val activePlacedItem = placedItems[selectedItemIndex]

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(CharcoalSurface)
                            .border(0.5.dp, GoldBorder, RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(onClick = { activePlacedItem.scale = (activePlacedItem.scale - 0.1f).coerceAtLeast(0.5f); selectedItemIndex = -1; selectedItemIndex = placedItems.indexOf(activePlacedItem) }) {
                            Icon(Icons.Default.ZoomOut, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { activePlacedItem.scale = (activePlacedItem.scale + 0.1f).coerceAtMost(2.0f); selectedItemIndex = -1; selectedItemIndex = placedItems.indexOf(activePlacedItem) }) {
                            Icon(Icons.Default.ZoomIn, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { activePlacedItem.rotation -= 15f; selectedItemIndex = -1; selectedItemIndex = placedItems.indexOf(activePlacedItem) }) {
                            Icon(Icons.Default.RotateLeft, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { activePlacedItem.rotation += 15f; selectedItemIndex = -1; selectedItemIndex = placedItems.indexOf(activePlacedItem) }) {
                            Icon(Icons.Default.RotateRight, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        if (previewOutfit != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassOverlay)
                    .border(0.5.dp, GoldBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Live Compatibility",
                        fontFamily = OutfitFont,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "${(previewOutfit.overallScore * 100).toInt()}% match",
                        fontFamily = PlayfairFont,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGold
                    )
                }
                Text(
                    text = "Color ${(previewOutfit.colorHarmonyScore * 100).toInt()}% · Formality ${(previewOutfit.formalityCoherence * 100).toInt()}%",
                    fontFamily = OutfitFont,
                    fontSize = 11.sp,
                    color = TextLight
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Tap to Add Garments",
            fontFamily = OutfitFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = TextLight,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (allGarments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassOverlay)
                    .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Your wardrobe is empty. Add garments from the Digitize tab first.",
                    fontFamily = OutfitFont,
                    fontSize = 12.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(allGarments, key = { it.id }) { garment ->
                    LookbookGarmentChip(
                        garment = garment,
                        onClick = {
                            val itemHalf = 60f
                            val defaultX = (canvasWidth / 2f) - itemHalf + (placedItems.size * 24f)
                            val defaultY = (canvasHeight / 2f) - itemHalf + (placedItems.size * 24f)
                            placedItems.add(
                                PlacedItem(
                                    garment = garment,
                                    offsetX = defaultX.coerceAtLeast(8f),
                                    offsetY = defaultY.coerceAtLeast(8f)
                                )
                            )
                            selectedItemIndex = placedItems.size - 1
                        }
                    )
                }
            }
        }

        ElegantButton(
            text = if (placedItems.isEmpty()) "Save Look & Index Custom Style"
            else "Save Look (${placedItems.size} items)",
            onClick = {
                if (placedItems.size < 2) {
                    showToast("Add at least 2 garments to compose an outfit.")
                } else {
                    val outfit = ClosetRepository.saveLookbookOutfit(placedItems.map { it.garment })
                    showToast("Look saved at ${(outfit.overallScore * 100).toInt()}% compatibility. Style vector updated.")
                    placedItems.clear()
                    selectedItemIndex = -1
                }
            },
            enabled = placedItems.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
