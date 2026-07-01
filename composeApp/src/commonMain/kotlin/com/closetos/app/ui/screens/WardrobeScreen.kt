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
import androidx.compose.ui.graphics.Brush
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
import com.closetos.app.ui.components.garmentPreviewColor
import com.closetos.app.ui.components.garmentCategoryIcon
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurface)
            .border(
                width = 1.dp,
                color = if (similarityScore != null && similarityScore > 0.6f) AccentGold else GlassBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
    ) {
        // 1. Full height Image Preview / Color placeholder
        val imagePath = garment.straightenedImageUrl.ifEmpty { garment.imageUrl }
        val bitmap = if (imagePath.isNotEmpty()) rememberImageBitmap(imagePath) else null

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (bitmap == null) {
                        garmentPreviewColor(garment.labColor)
                    } else {
                        Color.Transparent
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = garment.subcategory,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = garmentCategoryIcon(garment.category),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // 2. Gradient Overlay for text readability (Fade from transparent to black)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // 3. Floating Similarity Match Badge (Top End)
        if (similarityScore != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AirbnbCoral)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${(similarityScore * 100).toInt()}% match",
                    fontFamily = OutfitFont,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // 4. Text Details (Overlayed on bottom)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = garment.brand,
                fontFamily = OutfitFont,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = AirbnbCoral,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = garment.subcategory,
                fontFamily = PlayfairFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = garment.colorName,
                fontFamily = OutfitFont,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
