package com.closetos.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closetos.app.data.model.Garment
import com.closetos.app.data.repository.ClosetRepository
import com.closetos.app.showToast
import com.closetos.app.ui.components.ElegantButton
import com.closetos.app.ui.components.GarmentThumbnail
import com.closetos.app.ui.components.OutfitPreviewStack
import com.closetos.app.ui.components.TagChip
import com.closetos.app.ui.theme.*
import kotlin.math.abs

private data class BuilderSlot(
    val label: String,
    val category: String,
    val optional: Boolean = false
)

private val builderSlots = listOf(
    BuilderSlot("Top", "Top"),
    BuilderSlot("Bottom", "Bottom"),
    BuilderSlot("Shoes", "Shoes"),
    BuilderSlot("Accessory", "Outerwear", optional = true)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitBuilderScreen(onBack: (() -> Unit)? = null) {
    val allGarments by ClosetRepository.garments.collectAsState()
    var activeFilter by remember { mutableStateOf<String?>(null) }

    val filteredGarments = remember(allGarments, activeFilter) {
        if (activeFilter == null) allGarments
        else allGarments.filter { garment ->
            matchesHumanFilter(garment, activeFilter!!)
        }
    }

    val slotIndices = remember { mutableStateMapOf<String, Int>() }
    builderSlots.forEach { slot ->
        if (!slotIndices.containsKey(slot.category)) {
            slotIndices[slot.category] = 0
        }
    }

    val selectedGarments = builderSlots.mapNotNull { slot ->
        val pool = filteredGarments.filter { it.category == slot.category }
        val index = slotIndices[slot.category] ?: 0
        pool.getOrNull(index)
    }

    val previewOutfit = remember(selectedGarments.map { it.id }) {
        if (selectedGarments.size >= 2) {
            ClosetRepository.scoreLookbookOutfit(selectedGarments)
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .verticalScroll(rememberScrollState())
    ) {
        if (onBack != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AccentGold)
                }
                Text(
                    text = "Outfit Builder",
                    fontFamily = PlayfairFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = TextLight
                )
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Outfit Builder",
                fontFamily = PlayfairFont,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = AccentGold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Text(
            text = "Swipe through pieces — no dropdowns, just style",
            fontFamily = OutfitFont,
            fontSize = 13.sp,
            color = TextMuted,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        )

        LazyRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                TagChip(
                    text = "All",
                    isSelected = activeFilter == null,
                    onClick = { activeFilter = null }
                )
            }
            items(ClosetRepository.lookbookFilters) { filter ->
                TagChip(
                    text = filter,
                    isSelected = activeFilter == filter,
                    onClick = { activeFilter = if (activeFilter == filter) null else filter }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (previewOutfit != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF1A1A22), Color(0xFF101014)))
                    )
                    .border(0.5.dp, GoldBorder, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                OutfitPreviewStack(
                    outfit = previewOutfit,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
            Text(
                text = "${(previewOutfit.overallScore * 100).toInt()}% match · ${previewOutfit.name}",
                fontFamily = OutfitFont,
                fontSize = 12.sp,
                color = AccentGold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        builderSlots.forEach { slot ->
            SwipeGarmentSlot(
                slot = slot,
                garments = filteredGarments.filter { it.category == slot.category },
                selectedIndex = slotIndices[slot.category] ?: 0,
                onIndexChange = { slotIndices[slot.category] = it }
            )
            if (slot != builderSlots.last()) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = AccentGold.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        ElegantButton(
            text = "Save Look",
            onClick = {
                val core = selectedGarments.filter { g ->
                    builderSlots.find { it.category == g.category }?.optional != true || g.category != "Outerwear"
                }
                val required = builderSlots.filter { !it.optional }
                val hasRequired = required.all { slot ->
                    selectedGarments.any { it.category == slot.category }
                }
                if (!hasRequired || core.size < 2) {
                    showToast("Swipe to pick at least a top, bottom, and shoes.")
                } else {
                    val outfit = ClosetRepository.saveLookbookOutfit(selectedGarments, isAiGenerated = true)
                    showToast("Look saved at ${(outfit.overallScore * 100).toInt()}% — style profile updated.")
                    onBack?.invoke()
                }
            },
            enabled = selectedGarments.size >= 2,
            icon = Icons.Default.AutoFixHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        )
    }
}

@Composable
private fun SwipeGarmentSlot(
    slot: BuilderSlot,
    garments: List<Garment>,
    selectedIndex: Int,
    onIndexChange: (Int) -> Unit
) {
    val current = garments.getOrNull(selectedIndex)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = slot.label,
                fontFamily = PlayfairFont,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = TextLight
            )
            Text(
                text = "< swipe >",
                fontFamily = OutfitFont,
                fontSize = 11.sp,
                color = TextMuted,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF131317))
                .border(0.5.dp, GlassBorder, RoundedCornerShape(16.dp))
                .pointerInput(garments.size, selectedIndex) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        if (garments.isEmpty()) return@detectHorizontalDragGestures
                        if (abs(dragAmount) > 40f) {
                            val next = if (dragAmount < 0) {
                                (selectedIndex + 1) % garments.size
                            } else {
                                (selectedIndex - 1 + garments.size) % garments.size
                            }
                            onIndexChange(next)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (current == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Swipe,
                        contentDescription = null,
                        tint = TextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = if (slot.optional) "Swipe to add ${slot.label.lowercase()}" else "No ${slot.label.lowercase()} in wardrobe",
                        fontFamily = OutfitFont,
                        fontSize = 12.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp).padding(top = 8.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (garments.isNotEmpty()) {
                                onIndexChange((selectedIndex - 1 + garments.size) % garments.size)
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = AccentGold)
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GarmentThumbnail(
                            garment = current,
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .aspectRatio(0.85f),
                            contentScale = ContentScale.Fit,
                            cornerRadius = 12
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = current.subcategory,
                            fontFamily = OutfitFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextLight
                        )
                        Text(
                            text = current.colorName,
                            fontFamily = OutfitFont,
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }

                    IconButton(
                        onClick = {
                            if (garments.isNotEmpty()) {
                                onIndexChange((selectedIndex + 1) % garments.size)
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = AccentGold)
                    }
                }
            }
        }

        if (garments.isNotEmpty()) {
            Text(
                text = "${selectedIndex + 1} / ${garments.size}",
                fontFamily = OutfitFont,
                fontSize = 11.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
    }
}

private fun matchesHumanFilter(garment: Garment, filter: String): Boolean {
    return when (filter) {
        "Office", "Business" -> garment.formalityScore >= 0.5f
        "Travel" -> garment.seasons.size >= 3 || garment.subcategory.contains("Chino", ignoreCase = true)
        "Rainy" -> garment.material.contains("Nylon", ignoreCase = true) || garment.category == "Outerwear"
        "Hot Weather" -> garment.seasons.any { it.equals("Summer", ignoreCase = true) } && garment.formalityScore <= 0.6f
        "Cold Weather" -> garment.seasons.any { it.equals("Winter", ignoreCase = true) || it.equals("Autumn", ignoreCase = true) }
        "Minimal" -> garment.pattern.equals("Plain", ignoreCase = true)
        "Streetwear" -> garment.formalityScore <= 0.35f
        "Date" -> garment.formalityScore in 0.35f..0.7f
        "Wedding", "Party" -> garment.formalityScore >= 0.7f
        else -> true
    }
}
