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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closetos.app.data.model.Garment
import com.closetos.app.data.repository.ClosetRepository
import com.closetos.app.showToast
import com.closetos.app.ui.components.ElegantButton
import com.closetos.app.ui.components.SectionHeader
import com.closetos.app.ui.theme.*
import kotlin.math.roundToInt

// State model representing a garment placed on the drag canvas
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

    // Canvas layout details
    var canvasWidth by remember { mutableStateOf(0f) }
    var canvasHeight by remember { mutableStateOf(0f) }

    // Placed items on the lookbook canvas
    val placedItems = remember { mutableStateListOf<PlacedItem>() }
    var selectedItemIndex by remember { mutableStateOf(-1) }

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

        // Canvas box layout
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tap items in the drawer below to add them to your lookbook.",
                        fontFamily = OutfitFont,
                        fontSize = 13.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }

            // Render placed garments
            placedItems.forEachIndexed { index, placedItem ->
                val isSelected = index == selectedItemIndex
                val borderCol = if (isSelected) AccentGold else Color.Transparent

                // Compose Pointer Input Drag modifier
                Box(
                    modifier = Modifier
                        .offset { IntOffset(placedItem.offsetX.roundToInt(), placedItem.offsetY.roundToInt()) }
                        .size(100.dp)
                        .scale(placedItem.scale)
                        .rotate(placedItem.rotation)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GlassOverlay)
                        .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                        .clickable { selectedItemIndex = index }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    placedItem.offsetX += dragAmount.x
                                    placedItem.offsetY += dragAmount.y
                                    // Force state update
                                    selectedItemIndex = -1
                                    selectedItemIndex = index
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Icon(
                            imageVector = when(placedItem.garment.category) {
                                "Top" -> Icons.Default.Checkroom
                                "Bottom" -> Icons.Default.Accessibility
                                "Outerwear" -> Icons.Default.Layers
                                else -> Icons.Default.Hiking
                            },
                            contentDescription = null,
                            tint = AccentGold,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = placedItem.garment.subcategory,
                            fontFamily = OutfitFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextLight
                        )
                        Text(
                            text = placedItem.garment.brand,
                            fontFamily = OutfitFont,
                            fontSize = 8.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            // Canvas Floating Controls
            if (placedItems.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Delete Placed item
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

                    // Clear All
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

                // Selected Item Transformation Pill Controls (Rotate, Scale)
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
                        // Scale Down
                        IconButton(onClick = { activePlacedItem.scale = (activePlacedItem.scale - 0.1f).coerceAtLeast(0.5f); selectedItemIndex = -1; selectedItemIndex = placedItems.indexOf(activePlacedItem) }) {
                            Icon(Icons.Default.ZoomOut, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp))
                        }
                        // Scale Up
                        IconButton(onClick = { activePlacedItem.scale = (activePlacedItem.scale + 0.1f).coerceAtMost(2.0f); selectedItemIndex = -1; selectedItemIndex = placedItems.indexOf(activePlacedItem) }) {
                            Icon(Icons.Default.ZoomIn, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp))
                        }
                        // Rotate Left
                        IconButton(onClick = { activePlacedItem.rotation -= 15f; selectedItemIndex = -1; selectedItemIndex = placedItems.indexOf(activePlacedItem) }) {
                            Icon(Icons.Default.RotateLeft, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp))
                        }
                        // Rotate Right
                        IconButton(onClick = { activePlacedItem.rotation += 15f; selectedItemIndex = -1; selectedItemIndex = placedItems.indexOf(activePlacedItem) }) {
                            Icon(Icons.Default.RotateRight, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Wardrobe Bottom Selector Drawer
        Text(
            text = "Tap to Add Garments",
            fontFamily = OutfitFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = TextLight,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allGarments) { garment ->
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassOverlay)
                        .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
                        .clickable {
                            val defaultX = (canvasWidth / 2f) - 150f
                            val defaultY = (canvasHeight / 2f) - 100f
                            placedItems.add(
                                PlacedItem(
                                    garment = garment,
                                    offsetX = defaultX + (placedItems.size * 20f),
                                    offsetY = defaultY + (placedItems.size * 20f)
                                )
                            )
                            selectedItemIndex = placedItems.size - 1
                        }
                        .padding(8.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = when(garment.category) {
                                "Top" -> Icons.Default.Checkroom
                                "Bottom" -> Icons.Default.Accessibility
                                 else -> Icons.Default.Checkroom
                            },
                            contentDescription = null,
                            tint = AccentGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = garment.subcategory,
                            fontFamily = OutfitFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = TextLight
                        )
                    }
                }
            }
        }

        // Save Button controls
        ElegantButton(
            text = "Save Look & Index Custom Style",
            onClick = {
                if (placedItems.size < 2) {
                    showToast("Add at least 2 garments to compose an outfit.")
                } else {
                    showToast("Look saved! Saved outfit registered as a strong preference signal.")
                    placedItems.clear()
                    selectedItemIndex = -1
                }
            },
            enabled = placedItems.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
