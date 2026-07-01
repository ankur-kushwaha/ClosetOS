package com.closetos.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import com.closetos.app.data.model.IngestionItem
import com.closetos.app.data.model.IngestionStatus
import com.closetos.app.data.model.generateUUID
import com.closetos.app.data.repository.ClosetRepository
import com.closetos.app.rememberImageBitmap
import com.closetos.app.showToast
import com.closetos.app.ui.components.ElegantButton
import com.closetos.app.ui.components.GlassmorphicCard
import com.closetos.app.ui.components.TagChip
import com.closetos.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ReviewSweepBottomSheet(
    item: IngestionItem,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianBg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AccentGold.copy(alpha = 0.6f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Review Sweep",
                    fontFamily = PlayfairFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = TextLight
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            ReviewSweepItemContent(item = item, onDismiss = onDismiss)
        }
    }
}

@Composable
fun ReviewSweepItemContent(
    item: IngestionItem,
    onDismiss: () -> Unit
) {
    val garment = item.detectedGarment ?: return

    var editCategory by remember(item.id) { mutableStateOf(garment.category) }
    var editSubcategory by remember(item.id) { mutableStateOf(garment.subcategory) }
    var editBrand by remember(item.id) { mutableStateOf(garment.brand) }
    var editPrice by remember(item.id) { mutableStateOf(garment.price.toString()) }
    var editMaterial by remember(item.id) { mutableStateOf(garment.material) }
    var editFit by remember(item.id) { mutableStateOf(garment.fit) }
    var editFormality by remember(item.id) { mutableStateOf(garment.formalityScore) }
    var showStraightened by remember(item.id) { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1B1B22), Color(0xFF121215))))
                .border(0.5.dp, GlassBorder, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            val displayUrl = if (showStraightened && !garment.straightenedImageUrl.isNullOrEmpty()) {
                garment.straightenedImageUrl ?: ""
            } else {
                garment.imageUrl
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
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(40.dp)
                )
            }

            if (!garment.straightenedImageUrl.isNullOrEmpty()) {
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

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ElegantButton(
                text = "Split Garment",
                onClick = {
                    showToast("Split item into Top & Bottom! Model Retraining log queued.")
                    val item1 = garment.copy(
                        id = "split_g_1_" + generateUUID(),
                        category = "Top",
                        subcategory = "Silk Camisole",
                        price = garment.price / 2.0
                    )
                    val item2 = garment.copy(
                        id = "split_g_2_" + generateUUID(),
                        category = "Bottom",
                        subcategory = "Silk Midi Skirt",
                        price = garment.price / 2.0
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
                    onDismiss()
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
                    onDismiss()
                },
                isSecondary = true,
                icon = Icons.Default.MergeType,
                modifier = Modifier.weight(1f)
            )
        }

        GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "VLM Attribute Auto-Tags",
                fontFamily = PlayfairFont,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = AccentGold
            )
            Spacer(modifier = Modifier.height(12.dp))

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

            Text(
                text = "Material",
                fontFamily = OutfitFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = TextLight
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Cotton", "Linen", "Silk", "Wool", "Denim").forEach { mat ->
                    TagChip(
                        text = mat,
                        isSelected = editMaterial.contains(mat, ignoreCase = true),
                        onClick = { editMaterial = mat }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Fit",
                fontFamily = OutfitFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = TextLight
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Slim", "Regular", "Relaxed", "Tailored").forEach { fit ->
                    TagChip(
                        text = fit,
                        isSelected = editFit.contains(fit, ignoreCase = true),
                        onClick = { editFit = fit }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

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

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElegantButton(
                text = "Discard Crop",
                onClick = {
                    showToast("Garment Rejected. Index cleaned.")
                    ClosetRepository.rejectIngestionItem(item.id)
                    onDismiss()
                },
                isSecondary = true,
                modifier = Modifier.weight(1f)
            )

            ElegantButton(
                text = "Confirm Item",
                onClick = {
                    showToast("Confirmed! Garment parsed & pushed live to Wardrobe Graph.")
                    val finalImageUrl = if (showStraightened && !garment.straightenedImageUrl.isNullOrEmpty()) {
                        garment.straightenedImageUrl
                    } else {
                        garment.imageUrl
                    }
                    val finalStraightenedImageUrl = if (showStraightened && !garment.straightenedImageUrl.isNullOrEmpty()) {
                        garment.imageUrl
                    } else {
                        garment.straightenedImageUrl
                    }
                    val updatedGarment = garment.copy(
                        category = editCategory,
                        subcategory = editSubcategory,
                        brand = editBrand,
                        price = editPrice.toDoubleOrNull() ?: garment.price,
                        material = editMaterial,
                        fit = editFit,
                        formalityScore = editFormality,
                        imageUrl = finalImageUrl,
                        straightenedImageUrl = finalStraightenedImageUrl
                    )
                    ClosetRepository.editIngestedGarment(item.id, updatedGarment)
                    ClosetRepository.approveIngestionItem(item.id)
                    onDismiss()
                },
                modifier = Modifier.weight(1.5f)
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ReviewSweepScreen(onBack: () -> Unit) {
    val queue by ClosetRepository.ingestionQueue.collectAsState()
    val reviewItems = remember(queue) {
        queue.filter { it.status == IngestionStatus.READY && it.detectedGarment != null }
    }

    var activeIndex by remember { mutableStateOf(0) }
    val activeItem = remember(reviewItems, activeIndex) { reviewItems.getOrNull(activeIndex) }

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

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(reviewItems.size) { index ->
                val reviewItem = reviewItems[index]
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
                    val thumbBitmap = reviewItem.detectedGarment?.imageUrl?.let { rememberImageBitmap(it) }
                    if (thumbBitmap != null) {
                        Image(
                            bitmap = thumbBitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Checkroom,
                            contentDescription = null,
                            tint = if (isActive) AccentGold else TextMuted
                        )
                    }
                }
            }
        }

        activeItem?.let { item ->
            ReviewSweepItemContent(item = item, onDismiss = onBack)
        }
    }
}
