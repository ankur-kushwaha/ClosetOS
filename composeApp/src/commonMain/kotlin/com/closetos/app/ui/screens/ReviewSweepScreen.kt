package com.closetos.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closetos.app.data.model.Garment
import com.closetos.app.data.model.IngestionItem
import com.closetos.app.data.model.IngestionStatus
import com.closetos.app.data.model.generateUUID
import com.closetos.app.data.repository.ClosetRepository
import com.closetos.app.rememberImageBitmap
import com.closetos.app.showToast
import com.closetos.app.ui.components.ElegantButton
import com.closetos.app.ui.components.GlassmorphicCard
import com.closetos.app.ui.components.SectionHeader
import com.closetos.app.ui.components.TagChip
import com.closetos.app.ui.theme.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ReviewSweepScreen(onBack: () -> Unit) {
    val queue by ClosetRepository.ingestionQueue.collectAsState()
    
    // Filter queue items that are ready for review
    val reviewItems = remember(queue) { queue.filter { it.status == IngestionStatus.READY && it.detectedGarment != null } }

    var activeIndex by remember { mutableStateOf(0) }
    val activeItem = remember(reviewItems, activeIndex) { reviewItems.getOrNull(activeIndex) }

    // State for temporary edits
    var editCategory by remember { mutableStateOf("") }
    var editSubcategory by remember { mutableStateOf("") }
    var editBrand by remember { mutableStateOf("") }
    var editPrice by remember { mutableStateOf("") }
    var editMaterial by remember { mutableStateOf("") }
    var editFit by remember { mutableStateOf("") }
    var editFormality by remember { mutableStateOf(0.5f) }
    var showStraightened by remember { mutableStateOf(true) }

    // Sync editing state when active item changes
    LaunchedEffect(activeItem) {
        showStraightened = true // Reset toggle on new item
        activeItem?.detectedGarment?.let { garment ->
            editCategory = garment.category
            editSubcategory = garment.subcategory
            editBrand = garment.brand
            editPrice = garment.price.toString()
            editMaterial = garment.material
            editFit = garment.fit
            editFormality = garment.formalityScore
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextLight)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Review Sweep",
                fontFamily = PlayfairFont,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = TextLight
            )
        }

        if (reviewItems.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Sweep completed! No items to review.",
                        fontFamily = OutfitFont,
                        fontSize = 16.sp,
                        color = TextLight
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    ElegantButton(text = "Back to Closet", onClick = onBack)
                }
            }
            return
        }

        // Horizontal picker of items in review queue
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(reviewItems.size) { index ->
                val item = reviewItems[index]
                val isActive = index == activeIndex
                val borderCol = if (isActive) AccentGold else GlassBorder
                val bg = if (isActive) Color(0x1CE5C185) else GlassOverlay
                
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                        .clickable { activeIndex = index }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = item.detectedGarment?.imageUrl?.let { rememberImageBitmap(it) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp))
                        )
                    } else {
                        Icon(
                            imageVector = when (item.detectedGarment?.category) {
                                "Top" -> Icons.Default.Checkroom
                                "Bottom" -> Icons.Default.Accessibility
                                else -> Icons.Default.Checkroom
                            },
                            contentDescription = null,
                            tint = if (isActive) AccentGold else TextMuted
                        )
                    }
                }
            }
        }

        // Active Garment Editing Card Workspace
        activeItem?.let { item ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Image transparent cutout container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1B1B22), Color(0xFF121215))
                            )
                        )
                        .border(0.5.dp, GlassBorder, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val displayUrl = if (showStraightened && !item.detectedGarment?.straightenedImageUrl.isNullOrEmpty()) {
                        item.detectedGarment?.straightenedImageUrl ?: ""
                    } else {
                        item.detectedGarment?.imageUrl ?: ""
                    }
                    val bitmap = if (displayUrl.isNotEmpty()) rememberImageBitmap(displayUrl) else null
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Segmented Garment Cutout",
                            modifier = Modifier
                                .fillMaxHeight(0.9f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        // Simulating a parsed product silhouette outline
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(RoundedCornerShape(45.dp))
                                    .background(Color(0x33E5C185))
                                    .border(0.5.dp, AccentGold, RoundedCornerShape(45.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = AccentGold,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Clean alpha-matte segmented cutout [RGBA PNG]",
                                fontFamily = OutfitFont,
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                            Text(
                                text = "Matte: BiRefNet • Background removed",
                                fontFamily = OutfitFont,
                                fontSize = 10.sp,
                                color = AccentGold
                            )
                        }
                    }

                    // Raw vs Straightened Toggle Pill inside the image container
                    if (item.detectedGarment != null && !item.detectedGarment.straightenedImageUrl.isNullOrEmpty()) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(CharcoalSurface.copy(alpha = 0.8f))
                                .border(0.5.dp, GlassBorder, RoundedCornerShape(20.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (!showStraightened) AccentGold else Color.Transparent)
                                    .clickable { showStraightened = false }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Raw",
                                    color = if (!showStraightened) ObsidianBg else TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (showStraightened) AccentGold else Color.Transparent)
                                    .clickable { showStraightened = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Straightened",
                                    color = if (showStraightened) ObsidianBg else TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions: Split & Merge
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ElegantButton(
                        text = "Split Garment",
                        onClick = {
                            showToast("Split item into Top & Bottom! Model Retraining log queued.")
                            val item1 = item.detectedGarment!!.copy(
                                id = "split_g_1_" + generateUUID(),
                                category = "Top",
                                subcategory = "Silk Camisole",
                                price = item.detectedGarment.price / 2.0
                            )
                            val item2 = item.detectedGarment.copy(
                                id = "split_g_2_" + generateUUID(),
                                category = "Bottom",
                                subcategory = "Silk Midi Skirt",
                                price = item.detectedGarment.price / 2.0
                            )
                            ClosetRepository.rejectIngestionItem(item.id)
                            ClosetRepository.queueIngestionItems(listOf("split_crop_1.jpg", "split_crop_2.jpg"))
                            val q = ClosetRepository.ingestionQueue.value
                            q.takeLast(2).forEach { qItem ->
                                ClosetRepository.updateIngestionItemProgress(
                                    qItem.id,
                                    IngestionStatus.READY,
                                    1.0f,
                                    "Split output derived.",
                                    if (qItem.originalImageUrl.contains("1")) item1 else item2
                                )
                            }
                            activeIndex = 0
                        },
                        isSecondary = true,
                        icon = Icons.Default.CallSplit,
                        modifier = Modifier.weight(1f)
                    )

                    ElegantButton(
                        text = "Merge Duplicate",
                        onClick = {
                            showToast("Merged with Ralph Lauren Oxford in Closet. Labeled training example recorded.")
                            ClosetRepository.rejectIngestionItem(item.id)
                            activeIndex = 0
                        },
                        isSecondary = true,
                        icon = Icons.Default.MergeType,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Attributes Edit Section
                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "VLM Attribute Auto-Tags",
                        fontFamily = PlayfairFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = AccentGold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Brand & Price Fields
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editBrand,
                            onValueChange = { editBrand = it },
                            label = { Text("Brand", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextLight, focusedBorderColor = AccentGold, unfocusedBorderColor = GlassBorder
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editPrice,
                            onValueChange = { editPrice = it },
                            label = { Text("Price ($)", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextLight, focusedBorderColor = AccentGold, unfocusedBorderColor = GlassBorder
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Category / Subcategory
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editCategory,
                            onValueChange = { editCategory = it },
                            label = { Text("Category", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextLight, focusedBorderColor = AccentGold, unfocusedBorderColor = GlassBorder
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editSubcategory,
                            onValueChange = { editSubcategory = it },
                            label = { Text("Subcategory", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextLight, focusedBorderColor = AccentGold, unfocusedBorderColor = GlassBorder
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Material Tags Choice
                    Text(
                        text = "Material",
                        fontFamily = OutfitFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextLight
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Cotton", "Linen", "Silk", "Wool", "Denim").forEach { mat ->
                            val isSel = editMaterial.contains(mat, ignoreCase = true)
                            TagChip(
                                text = mat,
                                isSelected = isSel,
                                onClick = { editMaterial = mat }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Fit tags
                    Text(
                        text = "Fit",
                        fontFamily = OutfitFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextLight
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Slim", "Regular", "Relaxed", "Tailored").forEach { fit ->
                            val isSel = editFit.contains(fit, ignoreCase = true)
                            TagChip(
                                text = fit,
                                isSelected = isSel,
                                onClick = { editFit = fit }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Formality Slider
                    Text(
                        text = "Formality Score: ${(editFormality * 10).toInt()}/10",
                        fontFamily = OutfitFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextLight
                    )
                    Slider(
                        value = editFormality,
                        onValueChange = { editFormality = it },
                        colors = SliderDefaults.colors(
                            thumbColor = AccentGold,
                            activeTrackColor = AccentGold,
                            inactiveTrackColor = GlassBorder
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons: Approve / Reject
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ElegantButton(
                        text = "Discard Crop",
                        onClick = {
                            showToast("Garment Rejected. Index cleaned.")
                            ClosetRepository.rejectIngestionItem(item.id)
                            activeIndex = 0
                        },
                        isSecondary = true,
                        modifier = Modifier.weight(1f)
                    )

                    ElegantButton(
                        text = "Confirm Item",
                        onClick = {
                            showToast("Confirmed! Garment parsed & pushed live to Wardrobe Graph.")
                            val originalGarment = item.detectedGarment!!
                            val finalImageUrl = if (showStraightened && !originalGarment.straightenedImageUrl.isNullOrEmpty()) {
                                originalGarment.straightenedImageUrl
                            } else {
                                originalGarment.imageUrl
                            }
                            val finalStraightenedImageUrl = if (showStraightened && !originalGarment.straightenedImageUrl.isNullOrEmpty()) {
                                originalGarment.imageUrl
                            } else {
                                originalGarment.straightenedImageUrl
                            }
                            val updatedGarment = originalGarment.copy(
                                category = editCategory,
                                subcategory = editSubcategory,
                                brand = editBrand,
                                price = editPrice.toDoubleOrNull() ?: originalGarment.price,
                                material = editMaterial,
                                fit = editFit,
                                formalityScore = editFormality,
                                imageUrl = finalImageUrl,
                                straightenedImageUrl = finalStraightenedImageUrl
                            )
                            ClosetRepository.editIngestedGarment(item.id, updatedGarment)
                            ClosetRepository.approveIngestionItem(item.id)
                            activeIndex = 0
                        },
                        modifier = Modifier.weight(1.5f)
                    )
                }
            }
        }
    }
}
