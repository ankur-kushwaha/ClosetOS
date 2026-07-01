package com.closetos.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closetos.app.data.model.Garment
import com.closetos.app.data.repository.ClosetRepository
import com.closetos.app.rememberImageBitmap
import com.closetos.app.ui.components.GlassmorphicCard
import com.closetos.app.ui.components.SectionHeader
import com.closetos.app.ui.components.TagChip
import com.closetos.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeScreen() {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var editingGarment by remember { mutableStateOf<Garment?>(null) }

    val allGarments by ClosetRepository.garments.collectAsState()

    val filteredGarments = remember(allGarments, searchQuery, selectedCategory) {
        val searchResults = ClosetRepository.searchGarments(searchQuery)

        searchResults.filter { (garment, _) ->
            selectedCategory == "All" || garment.category.equals(selectedCategory, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(16.dp)
    ) {
        SectionHeader(
            title = "My Wardrobe",
            subtitle = "${allGarments.size} garments digitized • Graph Index active"
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search closet (e.g., 'blue shirt', 'wool')", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AccentGold) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextLight,
                unfocusedTextColor = TextLight,
                focusedBorderColor = AccentGold,
                unfocusedBorderColor = GlassBorder,
                cursorColor = AccentGold
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Top", "Bottom", "Outerwear", "Shoes").forEach { cat ->
                TagChip(
                    text = cat + if (cat == "All") "" else "s",
                    isSelected = selectedCategory == cat,
                    onClick = { selectedCategory = cat }
                )
            }
        }

        if (filteredGarments.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No garments found matching query.",
                    fontFamily = OutfitFont,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(filteredGarments, key = { it.first.id }) { (garment, score) ->
                    GarmentCard(
                        garment = garment,
                        similarityScore = if (searchQuery.isNotEmpty()) score else null,
                        onClick = { editingGarment = garment }
                    )
                }
            }
        }
    }

    editingGarment?.let { garment ->
        GarmentEditBottomSheet(
            garment = garment,
            onDismiss = { editingGarment = null }
        )
    }
}

@Composable
fun GarmentCard(
    garment: Garment,
    similarityScore: Float?,
    onClick: () -> Unit
) {
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .border(
                width = 0.5.dp,
                color = if (similarityScore != null && similarityScore > 0.6f) AccentGold.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val bitmap = rememberImageBitmap(garment.straightenedImageUrl ?: garment.imageUrl)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                } else {
                    Icon(
                        imageVector = when (garment.category) {
                            "Top" -> Icons.Default.Checkroom
                            "Bottom" -> Icons.Default.Accessibility
                            "Outerwear" -> Icons.Default.Layers
                            else -> Icons.Default.Checkroom
                        },
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (similarityScore != null) {
                    Text(
                        text = "${(similarityScore * 100).toInt()}% match",
                        fontFamily = OutfitFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGold
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    text = garment.brand,
                    fontFamily = OutfitFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = AccentGold
                )
                Text(
                    text = garment.subcategory,
                    fontFamily = PlayfairFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = TextLight
                )
                Text(
                    text = garment.colorName,
                    fontFamily = OutfitFont,
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
    }
}
