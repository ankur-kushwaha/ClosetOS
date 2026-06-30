package com.closetos.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import com.closetos.app.data.model.LaundryStatus
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

        // Semantic Search Input
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

        // Horizontal Category Row
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
                        onLaundryToggle = { ClosetRepository.toggleGarmentLaundry(garment.id) },
                        onClick = { editingGarment = garment }
                    )
                }
            }
        }
    }

    // EDIT/DELETE DIALOG
    editingGarment?.let { garment ->
        var editBrand by remember(garment) { mutableStateOf(garment.brand) }
        var editPrice by remember(garment) { mutableStateOf(garment.price.toString()) }
        var editCategory by remember(garment) { mutableStateOf(garment.category) }
        var editSubcategory by remember(garment) { mutableStateOf(garment.subcategory) }
        var editColorName by remember(garment) { mutableStateOf(garment.colorName) }
        var editMaterial by remember(garment) { mutableStateOf(garment.material) }
        var editFit by remember(garment) { mutableStateOf(garment.fit) }

        AlertDialog(
            onDismissRequest = { editingGarment = null },
            containerColor = CharcoalSurface,
            title = {
                Text(
                    text = "Edit Garment Detail",
                    fontFamily = PlayfairFont,
                    color = AccentGold
                )
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = editBrand,
                            onValueChange = { editBrand = it },
                            label = { Text("Brand", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextLight, focusedBorderColor = AccentGold, unfocusedBorderColor = GlassBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = editPrice,
                            onValueChange = { editPrice = it },
                            label = { Text("Price ($)", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextLight, focusedBorderColor = AccentGold, unfocusedBorderColor = GlassBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = editCategory,
                            onValueChange = { editCategory = it },
                            label = { Text("Category", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextLight, focusedBorderColor = AccentGold, unfocusedBorderColor = GlassBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = editSubcategory,
                            onValueChange = { editSubcategory = it },
                            label = { Text("Subcategory", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextLight, focusedBorderColor = AccentGold, unfocusedBorderColor = GlassBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = editColorName,
                            onValueChange = { editColorName = it },
                            label = { Text("Color Name", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextLight, focusedBorderColor = AccentGold, unfocusedBorderColor = GlassBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = editMaterial,
                            onValueChange = { editMaterial = it },
                            label = { Text("Material", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextLight, focusedBorderColor = AccentGold, unfocusedBorderColor = GlassBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = editFit,
                            onValueChange = { editFit = it },
                            label = { Text("Fit", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextLight, focusedBorderColor = AccentGold, unfocusedBorderColor = GlassBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedPrice = editPrice.toDoubleOrNull() ?: garment.price
                        val updated = garment.copy(
                            brand = editBrand,
                            price = parsedPrice,
                            category = editCategory,
                            subcategory = editSubcategory,
                            colorName = editColorName,
                            material = editMaterial,
                            fit = editFit,
                            costPerWear = parsedPrice / if (garment.wearCount > 0) garment.wearCount.toDouble() else 1.0
                        )
                        ClosetRepository.editGarment(updated)
                        editingGarment = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold)
                ) {
                    Text("Save", color = ObsidianBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            ClosetRepository.deleteGarment(garment.id)
                            editingGarment = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { editingGarment = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = TextLight)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
fun GarmentCard(
    garment: Garment,
    similarityScore: Float?,
    onLaundryToggle: () -> Unit,
    onClick: () -> Unit
) {
    val statusColor = when (garment.laundryStatus) {
        LaundryStatus.CLEAN -> SuccessColor
        LaundryStatus.DIRTY -> ErrorColor
        LaundryStatus.IN_LAUNDRY -> InfoBlue
    }

    val statusLabel = when (garment.laundryStatus) {
        LaundryStatus.CLEAN -> "Clean"
        LaundryStatus.DIRTY -> "Dirty"
        LaundryStatus.IN_LAUNDRY -> "Laundry"
    }

    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
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
            // Header: Icon category + Similarity Score indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val bitmap = rememberImageBitmap(garment.imageUrl)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                } else {
                    Icon(
                        imageVector = when(garment.category) {
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

            // Body info
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

            // Footer info: Laundry Status + CPW stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cost per wear
                Column {
                    Text(
                        text = "CPW",
                        fontFamily = OutfitFont,
                        fontSize = 9.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "$${garment.costPerWear}",
                        fontFamily = OutfitFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextLight
                    )
                }

                // Laundry Status pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .border(0.5.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .clickable { onLaundryToggle() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusLabel,
                            fontFamily = OutfitFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }
        }
    }
}
