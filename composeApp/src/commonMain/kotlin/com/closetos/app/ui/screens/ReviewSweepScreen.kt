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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closetos.app.cropImageToBase64
import com.closetos.app.data.model.Garment
import com.closetos.app.data.model.GarmentCategories
import com.closetos.app.data.model.IngestionItem
import com.closetos.app.data.model.IngestionStatus
import com.closetos.app.data.repository.ClosetRepository
import com.closetos.app.decodeBase64ToBitmap
import com.closetos.app.rememberImageBitmap
import com.closetos.app.rememberImagePickerLauncher
import com.closetos.app.saveBase64ImageToFile
import com.closetos.app.showToast
import com.closetos.app.ui.components.ElegantButton
import com.closetos.app.ui.components.GlassmorphicCard
import com.closetos.app.ui.components.ImageCropDialog
import com.closetos.app.ui.components.TagChip
import com.closetos.app.ui.theme.*
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarmentEditBottomSheet(
    garment: Garment,
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
                    text = "Edit Garment",
                    fontFamily = PlayfairFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = TextLight
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            GarmentEditFormContent(
                garment = garment,
                allowImageEdit = true,
                secondaryButtonText = "Delete",
                primaryButtonText = "Save Changes",
                onSecondary = {
                    ClosetRepository.deleteGarment(garment.id)
                    showToast("Garment removed from wardrobe.")
                    onDismiss()
                },
                onPrimary = { updatedGarment ->
                    ClosetRepository.editGarment(updatedGarment)
                    showToast("Garment updated.")
                    onDismiss()
                }
            )
        }
    }
}

@Composable
fun ReviewSweepItemContent(
    item: IngestionItem,
    onDismiss: () -> Unit
) {
    val garment = item.detectedGarment ?: return

    GarmentEditFormContent(
        garment = garment,
        originalCropBase64 = item.cropBase64,
        allowImageEdit = true,
        secondaryButtonText = "Discard Crop",
        primaryButtonText = "Confirm Metadata",
        onSecondary = {
            showToast("Garment Rejected. Index cleaned.")
            ClosetRepository.rejectIngestionItem(item.id)
            onDismiss()
        },
        onPrimary = { updatedGarment ->
            showToast("Confirmed! Garment parsed & pushed live to Wardrobe Graph.")
            ClosetRepository.editIngestedGarment(item.id, updatedGarment)
            ClosetRepository.approveIngestionItem(item.id)
            onDismiss()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, color = TextMuted) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextLight,
                unfocusedTextColor = TextLight,
                focusedBorderColor = AccentGold,
                unfocusedBorderColor = GlassBorder
            ),
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(CharcoalSurface)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontFamily = OutfitFont, color = TextLight) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun GarmentEditFormContent(
    garment: Garment,
    originalCropBase64: String? = null,
    allowImageEdit: Boolean = false,
    secondaryButtonText: String,
    primaryButtonText: String,
    onSecondary: () -> Unit,
    onPrimary: (Garment) -> Unit
) {
    val scope = rememberCoroutineScope()
    var editCategory by remember(garment.id) { mutableStateOf(garment.category) }
    var editSubcategory by remember(garment.id) { mutableStateOf(garment.subcategory) }
    var editBrand by remember(garment.id) { mutableStateOf(garment.brand) }
    var editPrice by remember(garment.id) { mutableStateOf(garment.price.toString()) }
    var editMaterial by remember(garment.id) { mutableStateOf(garment.material) }
    var editFit by remember(garment.id) { mutableStateOf(garment.fit) }
    var editFormality by remember(garment.id) { mutableStateOf(garment.formalityScore) }
    var showStraightened by remember(garment.id) { mutableStateOf(true) }
    var editedImagePath by remember(garment.id) { mutableStateOf<String?>(null) }
    var editedCropBase64 by remember(garment.id) { mutableStateOf<String?>(null) }
    var cropDialogPath by remember { mutableStateOf<String?>(null) }
    var pendingPickerForEdit by remember { mutableStateOf(false) }

    val subcategoryOptions = remember(editCategory) {
        val options = GarmentCategories.subcategoriesFor(editCategory)
        if (editSubcategory !in options && editSubcategory.isNotEmpty()) {
            listOf(editSubcategory) + options
        } else {
            options
        }
    }

    LaunchedEffect(editCategory) {
        val options = GarmentCategories.subcategoriesFor(editCategory)
        if (options.isNotEmpty() && editSubcategory !in options) {
            editSubcategory = options.first()
        }
    }

    val imagePicker = rememberImagePickerLauncher { paths ->
        if (paths.isNotEmpty() && pendingPickerForEdit) {
            pendingPickerForEdit = false
            cropDialogPath = paths.first()
        }
    }

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
            val rawCropBase64 = editedCropBase64 ?: originalCropBase64
            val displayPath = when {
                showStraightened && editedImagePath != null -> editedImagePath
                !showStraightened && editedImagePath != null -> editedImagePath
                showStraightened && !garment.straightenedImageUrl.isNullOrEmpty() -> garment.straightenedImageUrl
                !showStraightened && !garment.imageUrl.isNullOrEmpty() -> garment.imageUrl
                else -> ""
            }

            val rawBitmap = if (!showStraightened && !rawCropBase64.isNullOrEmpty()) {
                remember(rawCropBase64) { decodeBase64ToBitmap(rawCropBase64) }
            } else null
            val pathBitmap = if (displayPath?.isNotEmpty() == true) rememberImageBitmap(displayPath) else null
            val bitmap = rawBitmap ?: pathBitmap

            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Garment image",
                    modifier = Modifier
                        .fillMaxHeight(0.9f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(40.dp)
                )
            }

            val hasToggle = !rawCropBase64.isNullOrEmpty() ||
                !garment.straightenedImageUrl.isNullOrEmpty() ||
                garment.imageUrl.isNotEmpty()
            if (hasToggle) {
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
                            text = "Normalized",
                            color = if (showStraightened) ObsidianBg else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (allowImageEdit) {
                IconButton(
                    onClick = {
                        pendingPickerForEdit = true
                        imagePicker()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CharcoalSurface.copy(alpha = 0.9f))
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit image", tint = AccentGold, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                CategoryDropdown(
                    label = "Category",
                    value = editCategory,
                    options = GarmentCategories.categories,
                    onValueChange = { editCategory = it },
                    modifier = Modifier.weight(1f)
                )
                CategoryDropdown(
                    label = "Subcategory",
                    value = editSubcategory,
                    options = subcategoryOptions,
                    onValueChange = { editSubcategory = it },
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
                text = secondaryButtonText,
                onClick = onSecondary,
                isSecondary = true,
                modifier = Modifier.weight(1f)
            )

            ElegantButton(
                text = primaryButtonText,
                onClick = {
                    scope.launch {
                        val rawImageUrl = editedImagePath
                            ?: if (!showStraightened) garment.imageUrl else garment.straightenedImageUrl ?: garment.imageUrl
                        val straightenedImageUrl = editedImagePath
                            ?: if (showStraightened) garment.straightenedImageUrl ?: garment.imageUrl else garment.straightenedImageUrl

                        val finalImageUrl = if (showStraightened) {
                            straightenedImageUrl ?: rawImageUrl
                        } else {
                            rawImageUrl
                        }
                        val finalStraightened = if (showStraightened) {
                            straightenedImageUrl ?: finalImageUrl
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
                            straightenedImageUrl = finalStraightened
                        )
                        onPrimary(updatedGarment)
                    }
                },
                modifier = Modifier.weight(1.5f)
            )
        }
    }

    cropDialogPath?.let { path ->
        ImageCropDialog(
            imagePath = path,
            title = "Crop Garment Image",
            onDismiss = { cropDialogPath = null },
            onConfirm = { left, top, width, height ->
                val imagePath = cropDialogPath
                cropDialogPath = null
                scope.launch {
                    val cropBase64 = cropImageToBase64(imagePath ?: return@launch, left, top, width, height)
                    if (cropBase64.isNullOrEmpty()) {
                        showToast("Failed to crop image.")
                        return@launch
                    }
                    editedCropBase64 = cropBase64
                    val savedPath = saveBase64ImageToFile(cropBase64, "edited_crop")
                    if (savedPath != null) {
                        editedImagePath = savedPath
                        showStraightened = false
                        showToast("Image updated.")
                    }
                }
            }
        )
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextLight)
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
                val cropThumb = reviewItem.cropBase64?.let { remember(it) { decodeBase64ToBitmap(it) } }
                val pathThumb = reviewItem.detectedGarment?.straightenedImageUrl?.let { rememberImageBitmap(it) }

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
                    val thumbBitmap = cropThumb ?: pathThumb
                    if (thumbBitmap != null) {
                        Image(
                            bitmap = thumbBitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop
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
