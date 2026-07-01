package com.closetos.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.closetos.app.*
import com.closetos.app.data.model.*
import com.closetos.app.data.repository.ClosetRepository
import com.closetos.app.ui.components.ElegantButton
import com.closetos.app.ui.components.GlassmorphicCard
import com.closetos.app.ui.components.SectionHeader
import com.closetos.app.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun IngestionScreen(
    sharedUrl: String? = null
) {
    var activeTab by remember { mutableStateOf(0) } // 0: Bulk, 1: Receipt, 2: Retailer
    val queue by ClosetRepository.ingestionQueue.collectAsState()
    val scope = rememberCoroutineScope()

    var retailerUrl by remember { mutableStateOf(sharedUrl ?: "") }
    var isFetchingLink by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current

    var showServerConfig by remember { mutableStateOf(false) }
    var serverIpInput by remember {
        mutableStateOf(PlatformStorage.loadString("backend_ip") ?: defaultBackendUrl())
    }
    var connectionStatus by remember { mutableStateOf("Unknown") } // "Checking", "Connected", "Failed", "Unknown"

    var interactivePhotoPath by remember { mutableStateOf<String?>(null) }
    var detectedBoxes by remember { mutableStateOf<List<com.closetos.app.data.model.DetectedBox>?>(null) }
    var isDetectingGarments by remember { mutableStateOf(false) }
    var sweepItemId by remember { mutableStateOf<String?>(null) }
    var normalizationReviewId by remember { mutableStateOf<String?>(null) }
    val pipelineJobs = remember { mutableMapOf<String, Job>() }
    val sweepItem = remember(sweepItemId, queue) {
        sweepItemId?.let { id -> queue.find { it.id == id } }
    }
    val normalizationReviewItem = remember(normalizationReviewId, queue) {
        normalizationReviewId?.let { id -> queue.find { it.id == id } }
    }

    fun cancelProcessing(itemId: String) {
        pipelineJobs[itemId]?.cancel()
        pipelineJobs.remove(itemId)
        ClosetRepository.rejectIngestionItem(itemId)
        if (normalizationReviewId == itemId) normalizationReviewId = null
        if (sweepItemId == itemId) sweepItemId = null
    }

    fun startPipeline(itemId: String, block: suspend () -> Unit) {
        pipelineJobs[itemId]?.cancel()
        pipelineJobs[itemId] = scope.launch {
            try {
                block()
            } catch (_: CancellationException) {
                // User cancelled — item already removed from queue
            } finally {
                pipelineJobs.remove(itemId)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(16.dp)
    ) {
        SectionHeader(
            title = "Digitize Closet",
            subtitle = "Add items to your virtual wardrobe via three instant on-ramps."
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (connectionStatus) {
                                "Connected" -> Color(0xFF4CAF50)
                                "Failed" -> Color(0xFFF44336)
                                "Checking" -> AccentGold
                                else -> Color.Gray
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Server IP: ${serverIpInput.replace("http://", "").replace("https://", "")}",
                    fontFamily = OutfitFont,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
            Text(
                text = if (showServerConfig) "Hide Config" else "Configure Server",
                fontFamily = OutfitFont,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGold,
                modifier = Modifier.clickable { showServerConfig = !showServerConfig }
            )
        }

        if (showServerConfig) {
            GlassmorphicCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = "Backend Server LAN IP / Port",
                        fontFamily = OutfitFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextLight
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = serverIpInput,
                        onValueChange = { serverIpInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = GlassBorder,
                            cursorColor = AccentGold
                        ),
                        placeholder = { Text("e.g. http://192.168.1.15:8000", color = TextMuted) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                connectionStatus = "Checking"
                                scope.launch {
                                    connectionStatus = if (testBackendConnection(serverIpInput)) {
                                        "Connected"
                                    } else {
                                        "Failed"
                                    }
                                }
                            }
                        ) {
                            Text("Test Connection", color = AccentGold, fontFamily = OutfitFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                PlatformStorage.saveString("backend_ip", serverIpInput)
                                showServerConfig = false
                                showToast("Backend server IP saved!")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGold)
                        ) {
                            Text("Save", color = ObsidianBg, fontFamily = OutfitFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Segmented Tabs
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = Color.Transparent,
            contentColor = AccentGold,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                    color = AccentGold
                )
            },
            divider = { Divider(color = GlassBorder, thickness = 0.5.dp) },
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("Bulk Camera", fontFamily = OutfitFont, fontSize = 14.sp) },
                icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp)) }
            )
        }

        val galleryLauncher = rememberImagePickerLauncher { localPaths ->
            if (localPaths.isNotEmpty()) {
                if (localPaths.size == 1) {
                    val path = localPaths.first()
                    interactivePhotoPath = path
                    isDetectingGarments = true
                    scope.launch {
                        val result = runGarmentDetection(path)
                        isDetectingGarments = false
                        detectedBoxes = result
                    }
                } else {
                    scope.launch {
                        ClosetRepository.queueIngestionItems(localPaths)
                        localPaths.forEach { path ->
                            val item = ClosetRepository.ingestionQueue.value.find { it.originalImageUrl == path }
                            if (item != null) {
                                startPipeline(item.id) { simulateGarmentPipeline(item) }
                            }
                        }
                    }
                }
            }
        }

        // Tab Content Panel
        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                0 -> BulkCameraOnramp(
                    queue = queue,
                    onSelectPhotos = { galleryLauncher() },
                    onOpenSweep = { sweepItemId = it },
                    onOpenNormalizationReview = { normalizationReviewId = it },
                    onCancel = { cancelProcessing(it) },
                    onRetry = { item ->
                        startPipeline(item.id) { retryIngestionItem(item) }
                    }
                )
                1 -> ReceiptForwardOnramp(
                    email = "ankur.wardrobe@closet.os",
                    onCopyEmail = { clipboardManager.setText(AnnotatedString("ankur.wardrobe@closet.os")) }
                )
                2 -> RetailerLinkOnramp(
                    url = retailerUrl,
                    onUrlChange = { retailerUrl = it },
                    isFetching = isFetchingLink,
                    onFetch = {
                        isFetchingLink = true
                        scope.launch {
                            delay(1500)
                            isFetchingLink = false
                            retailerUrl = ""
                            ClosetRepository.queueIngestionItems(listOf("retailer_fetched_silk_dress.jpg"))
                            val parsedItem = ClosetRepository.ingestionQueue.value.last()
                            launch { simulateGarmentPipeline(parsedItem) }
                            activeTab = 0
                        }
                    }
                )
            }
        }

        // Multi-Garment Interactive Selection Dialog
        if (interactivePhotoPath != null) {
            Dialog(
                onDismissRequest = { /* blocked — use Cancel button */ },
                properties = DialogProperties(
                    dismissOnClickOutside = false,
                    dismissOnBackPress = true,
                    usePlatformDefaultWidth = false
                )
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianBg
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Select Garments to Digitize",
                            fontFamily = PlayfairFont,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isDetectingGarments) {
                                CircularProgressIndicator(color = AccentGold)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Analyzing photo for garments...",
                                    fontFamily = OutfitFont,
                                    color = TextMuted,
                                    fontSize = 14.sp
                                )
                            } else {
                                val boxes = detectedBoxes
                                if (boxes == null || boxes.isEmpty()) {
                                    Text(
                                        text = "No garments detected in the image.",
                                        fontFamily = OutfitFont,
                                        color = TextMuted,
                                        fontSize = 14.sp
                                    )
                                } else {
                                    Text(
                                        text = "Choose which detected items to digitize:",
                                        fontFamily = OutfitFont,
                                        color = TextLight,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(boxes.size) { idx ->
                                            val box = boxes[idx]
                                            var isChecked by remember { mutableStateOf(box.isSelected) }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF222228))
                                                    .clickable {
                                                        isChecked = !isChecked
                                                        detectedBoxes = boxes.mapIndexed { i, b ->
                                                            if (i == idx) b.copy(isSelected = isChecked) else b
                                                        }
                                                    }
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color.Black),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    val imageBitmap = remember(box.cropBase64) {
                                                        if (box.cropBase64.isNotEmpty()) {
                                                            decodeBase64ToBitmap(box.cropBase64)
                                                        } else {
                                                            null
                                                        }
                                                    }
                                                    if (imageBitmap != null) {
                                                        Image(
                                                            bitmap = imageBitmap,
                                                            contentDescription = null,
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Default.Checkroom,
                                                            contentDescription = null,
                                                            tint = if (isChecked) AccentGold else TextMuted
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(12.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = box.label.uppercase(),
                                                        fontFamily = OutfitFont,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = TextLight
                                                    )
                                                    Text(
                                                        text = "Confidence: ${(box.score * 100).toInt()}%",
                                                        fontFamily = OutfitFont,
                                                        fontSize = 11.sp,
                                                        color = TextMuted
                                                    )
                                                }

                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = { checked ->
                                                        isChecked = checked
                                                        detectedBoxes = boxes.mapIndexed { i, b ->
                                                            if (i == idx) b.copy(isSelected = checked) else b
                                                        }
                                                    },
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = AccentGold,
                                                        uncheckedColor = GlassBorder,
                                                        checkmarkColor = ObsidianBg
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    interactivePhotoPath = null
                                    detectedBoxes = null
                                    isDetectingGarments = false
                                }
                            ) {
                                Text("Cancel", color = TextMuted, fontFamily = OutfitFont)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val path = interactivePhotoPath ?: return@Button
                                    val boxes = detectedBoxes ?: return@Button
                                    val selectedBoxes = boxes.filter { it.isSelected }

                                    if (selectedBoxes.isNotEmpty()) {
                                        interactivePhotoPath = null
                                        detectedBoxes = null

                                        ClosetRepository.queueIngestionItems(listOf(path))
                                        val item = ClosetRepository.ingestionQueue.value.find { it.originalImageUrl == path }
                                        if (item != null) {
                                            selectedBoxes.forEachIndexed { index, box ->
                                                val id = if (index == 0) item.id else item.id + "_split_" + index + "_" + getEpochTimeMillis()

                                                if (index > 0) {
                                                    val splitItem = IngestionItem(
                                                        id = id,
                                                        originalImageUrl = path,
                                                        status = IngestionStatus.PRE_FLIGHT,
                                                        progress = 0.0f,
                                                        stepLabel = "Waiting for normalization...",
                                                        label = box.label,
                                                        cropBase64 = box.cropBase64,
                                                        sourceImageId = box.sourceImageId
                                                    )
                                                    ClosetRepository.addQueuedIngestionItems(listOf(splitItem))
                                                } else {
                                                    ClosetRepository.updateIngestionItemCrop(item.id, box.label, box.cropBase64, box.sourceImageId)
                                                }

                                                startPipeline(id) {
                                                    runNormalizePipeline(id, box.cropBase64, box.label)
                                                }
                                            }
                                        }
                                    } else {
                                        showToast("Please select at least one garment.")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                                enabled = !isDetectingGarments && !(detectedBoxes?.filter { it.isSelected }).isNullOrEmpty()
                            ) {
                                Text("Start Digitizing", color = ObsidianBg, fontFamily = OutfitFont, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        sweepItem?.let { item ->
            if (item.status == IngestionStatus.READY && item.detectedGarment != null) {
                ReviewSweepBottomSheet(
                    item = item,
                    onDismiss = { sweepItemId = null }
                )
            }
        }

        normalizationReviewItem?.let { item ->
            if (item.status == IngestionStatus.NORMALIZATION_REVIEW) {
                NormalizationReviewBottomSheet(
                    item = item,
                    onDismiss = { normalizationReviewId = null },
                    onAccept = { useNormalized ->
                        normalizationReviewId = null
                        startPipeline(item.id) { runFinalizePipeline(item, useNormalized) }
                    }
                )
            }
        }
    }
}

private suspend fun checkPipelineActive() {
    if (!currentCoroutineContext().isActive) throw CancellationException()
}

private suspend fun runNormalizePipeline(
    itemId: String,
    cropBase64: String,
    label: String
) {
    checkPipelineActive()
    ClosetRepository.updateIngestionItemProgress(itemId, IngestionStatus.NORMALIZATION, 0.5f, "Normalizing with AI...")
    val result = normalizeGarmentCrop(cropBase64, label)
    checkPipelineActive()
    if (result != null) {
        ClosetRepository.updateIngestionItemNormalized(itemId, result.imageBase64)
        ClosetRepository.updateIngestionItemProgress(
            itemId,
            IngestionStatus.NORMALIZATION_REVIEW,
            0.85f,
            "Tap to review normalization"
        )
    } else {
        ClosetRepository.updateIngestionItemNormalized(itemId, cropBase64)
        ClosetRepository.updateIngestionItemProgress(
            itemId,
            IngestionStatus.NORMALIZATION_REVIEW,
            0.85f,
            "Tap to review normalization (AI unavailable)"
        )
    }
}

private suspend fun runFinalizePipeline(item: IngestionItem, useNormalized: Boolean) {
    val cropBase64 = item.cropBase64 ?: return
    val label = item.label ?: "garment"
    val imageBase64 = if (useNormalized) {
        item.normalizedBase64 ?: cropBase64
    } else {
        cropBase64
    }

    checkPipelineActive()
    ClosetRepository.updateIngestionItemProgress(
        item.id,
        IngestionStatus.FLORENCE_2,
        0.92f,
        if (useNormalized) "Extracting metadata from normalized image..." else "Extracting metadata from original crop..."
    )
    val garment = finalizeGarment(imageBase64, cropBase64, label, item.sourceImageId)
    checkPipelineActive()
    if (garment != null) {
        ClosetRepository.updateIngestionItemProgress(item.id, IngestionStatus.READY, 1.0f, "Tap to confirm metadata", garment)
    } else {
        ClosetRepository.updateIngestionItemProgress(
            item.id,
            IngestionStatus.FAILED,
            1.0f,
            "Failed to extract metadata. Tap Retry."
        )
    }
}

private suspend fun retryIngestionItem(item: IngestionItem) {
    val cropBase64 = item.cropBase64
    val label = item.label
    when (item.status) {
        IngestionStatus.NORMALIZATION_REVIEW -> {
            if (!cropBase64.isNullOrEmpty()) {
                runFinalizePipeline(item, useNormalized = true)
            }
        }
        IngestionStatus.FAILED -> {
            if (!cropBase64.isNullOrEmpty() && !label.isNullOrEmpty()) {
                runNormalizePipeline(item.id, cropBase64, label)
            } else {
                simulateGarmentPipeline(item)
            }
        }
        else -> {
            if (!cropBase64.isNullOrEmpty() && !label.isNullOrEmpty()) {
                runNormalizePipeline(item.id, cropBase64, label)
            } else {
                simulateGarmentPipeline(item)
            }
        }
    }
}

// PIPELINE BACKGROUND TASK SIMULATOR
private suspend fun simulateGarmentPipeline(item: IngestionItem) {
    val id = item.id
    val filename = item.originalImageUrl
    
    // Call the actual runImageExtraction with the progress callback
    val backendGarments = runImageExtraction(filename) { statusName, progress, label ->
        // Map server steps to UI statuses
        val uiStatus = when (statusName) {
            "UPLOAD", "PRE_FLIGHT" -> IngestionStatus.PRE_FLIGHT
            "GROUNDED_SAM2", "GARMENT_DETECTION", "SEGMENTATION", "MASK_CLEANUP" -> IngestionStatus.GROUNDING_DINO
            "QUALITY_VALIDATION" -> IngestionStatus.QUALITY_VALIDATION
            "NORMALIZATION", "BACKGROUND_REMOVAL", "WHITE_BG_COMPOSITE" -> IngestionStatus.NORMALIZATION
            "ORIGINAL_CROP", "THUMBNAIL" -> IngestionStatus.CROP_GARMENT
            "FLORENCE_2", "METADATA_EXTRACTION" -> IngestionStatus.FLORENCE_2
            "FASHION_CLIP" -> IngestionStatus.FASHION_CLIP
            "DATABASE_PERSIST", "HIRES_UPSCALE" -> IngestionStatus.FASHION_CLIP
            else -> IngestionStatus.PRE_FLIGHT
        }
        
        ClosetRepository.updateIngestionItemProgress(id, uiStatus, progress, label)
    }
    
    if (backendGarments != null && backendGarments.isNotEmpty()) {
        // Complete the first garment on the original item row
        val firstGarment = backendGarments.first()
        ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.READY, 1.0f, "Ingestion Pipeline Completed!", firstGarment)
        
        // If multiple items were detected and processed, add them to the queue as split items
        if (backendGarments.size > 1) {
            val remainingGarments = backendGarments.drop(1)
            val newItems = remainingGarments.mapIndexed { idx, garment ->
                com.closetos.app.data.model.IngestionItem(
                    id = id + "_split_" + idx + "_" + getEpochTimeMillis(),
                    originalImageUrl = garment.imageUrl,
                    status = IngestionStatus.READY,
                    progress = 1.0f,
                    stepLabel = "Ingestion Pipeline Completed! (Split from photo)",
                    detectedGarment = garment
                )
            }
            ClosetRepository.addQueuedIngestionItems(newItems)
        }
    } else {
        ClosetRepository.updateIngestionItemProgress(
            itemId = id,
            status = IngestionStatus.FAILED,
            progress = 1.0f,
            label = "Backend connection failed or processing failed. Please check server."
        )
    }
}

@Composable
fun BulkCameraOnramp(
    queue: List<IngestionItem>,
    onSelectPhotos: () -> Unit,
    onOpenSweep: (String) -> Unit,
    onOpenNormalizationReview: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (IngestionItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        GlassmorphicCard(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Text(
                text = "Bulk Camera-Roll Import",
                style = MaterialTheme.typography.titleLarge,
                color = AccentGold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Upload flatlays, hanger captures, or on-body selfies. ClosetOS automatically segments garments and removes backgrounds.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            ElegantButton(
                text = "Select from Camera Roll",
                onClick = onSelectPhotos,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.PhotoLibrary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ingestion Pipeline Status",
                fontFamily = OutfitFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = TextLight
            )
            
            val readyCount = queue.count { it.status == IngestionStatus.READY }
            val reviewCount = queue.count { it.status == IngestionStatus.NORMALIZATION_REVIEW }
            if (readyCount > 0 || reviewCount > 0) {
                Text(
                    text = buildString {
                        if (reviewCount > 0) append("$reviewCount · Review normalization")
                        if (reviewCount > 0 && readyCount > 0) append("  ·  ")
                        if (readyCount > 0) append("$readyCount · Confirm metadata")
                    },
                    fontFamily = OutfitFont,
                    fontSize = 13.sp,
                    color = AccentGold
                )
            }
        }

        if (queue.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Queue is empty. Select photos to begin extraction.",
                    fontFamily = OutfitFont,
                    fontSize = 14.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(queue) { item ->
                    PipelineItemRow(item, onOpenSweep, onOpenNormalizationReview, onCancel, onRetry)
                }
            }
        }
    }
}

@Composable
fun PipelineItemRow(
    item: IngestionItem,
    onOpenSweep: (String) -> Unit,
    onOpenNormalizationReview: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (IngestionItem) -> Unit
) {
    val progressBrush = Brush.linearGradient(listOf(AccentGold, AccentGoldMuted))
    val garment = item.detectedGarment
    val isReadyForSweep = item.status == IngestionStatus.READY && garment != null
    val isNormalizationReview = item.status == IngestionStatus.NORMALIZATION_REVIEW
    val isProcessing = item.status == IngestionStatus.NORMALIZATION ||
        item.status == IngestionStatus.PRE_FLIGHT ||
        item.status == IngestionStatus.GROUNDING_DINO ||
        item.status == IngestionStatus.QUALITY_VALIDATION ||
        item.status == IngestionStatus.CROP_GARMENT ||
        item.status == IngestionStatus.FLORENCE_2 ||
        item.status == IngestionStatus.FASHION_CLIP ||
        item.status == IngestionStatus.SAM2

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassOverlay)
            .border(
                0.5.dp,
                when (item.status) {
                    IngestionStatus.READY -> AccentGold
                    IngestionStatus.NORMALIZATION_REVIEW -> AccentGold.copy(alpha = 0.7f)
                    IngestionStatus.FAILED -> Color.Red
                    else -> GlassBorder
                },
                RoundedCornerShape(12.dp)
            )
            .then(
                when {
                    isReadyForSweep -> Modifier.clickable { onOpenSweep(item.id) }
                    isNormalizationReview -> Modifier.clickable { onOpenNormalizationReview(item.id) }
                    else -> Modifier
                }
            )
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2E2E35))
                        .border(0.5.dp, GlassBorder, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val previewPath = garment?.straightenedImageUrl ?: garment?.imageUrl
                    val cropBitmap = remember(item.cropBase64, item.normalizedBase64, item.status) {
                        when {
                            item.status == IngestionStatus.NORMALIZATION_REVIEW && !item.normalizedBase64.isNullOrEmpty() ->
                                decodeBase64ToBitmap(item.normalizedBase64)
                            !item.cropBase64.isNullOrEmpty() ->
                                decodeBase64ToBitmap(item.cropBase64)
                            else -> null
                        }
                    }
                    if (cropBitmap != null) {
                        Image(
                            bitmap = cropBitmap,
                            contentDescription = "Crop Preview",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else if (previewPath != null) {
                        val bitmap = rememberImageBitmap(previewPath)
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Thumbnail Preview",
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Checkroom,
                                contentDescription = null,
                                tint = when (item.status) {
                                    IngestionStatus.READY -> AccentGold
                                    IngestionStatus.FAILED -> Color.Red
                                    else -> TextMuted
                                }
                            )
                        }
                    } else {
                        val bitmap = rememberImageBitmap(item.originalImageUrl)
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Thumbnail Preview",
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = when {
                                    item.label?.contains("pant", ignoreCase = true) == true || item.label?.contains("jean", ignoreCase = true) == true || item.label?.contains("bottom", ignoreCase = true) == true || item.originalImageUrl.contains("trousers") -> Icons.Default.Accessibility
                                    else -> Icons.Default.Checkroom
                                },
                                contentDescription = null,
                                tint = when (item.status) {
                                    IngestionStatus.READY -> AccentGold
                                    IngestionStatus.FAILED -> Color.Red
                                    else -> TextMuted
                                }
                            )
                        }
                    }

                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = AccentGold,
                            strokeWidth = 2.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (!item.label.isNullOrEmpty()) {
                                item.label.uppercase()
                            } else {
                                item.originalImageUrl.substringAfterLast("/").substringAfterLast("\\").replace("gallery_image_", "").replace("retailer_fetched_", "")
                            },
                            fontFamily = OutfitFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextLight
                        )

                        if (isReadyForSweep) {
                            Button(
                                onClick = { onOpenSweep(item.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = "Confirm Metadata",
                                    fontFamily = OutfitFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianBg
                                )
                            }
                        } else if (isNormalizationReview) {
                            Button(
                                onClick = { onOpenNormalizationReview(item.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = "Review Normalization",
                                    fontFamily = OutfitFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianBg
                                )
                            }
                        } else if (item.status == IngestionStatus.FAILED) {
                            Button(
                                onClick = { onRetry(item) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = "Retry",
                                    fontFamily = OutfitFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianBg
                                )
                            }
                        } else if (isProcessing) {
                            TextButton(
                                onClick = { onCancel(item.id) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = "Cancel",
                                    fontFamily = OutfitFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Red.copy(alpha = 0.85f)
                                )
                            }
                        } else {
                            Text(
                                text = "${(item.progress * 100).toInt()}%",
                                fontFamily = OutfitFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = item.stepLabel,
                        fontFamily = OutfitFont,
                        fontSize = 11.sp,
                        color = when (item.status) {
                            IngestionStatus.READY -> AccentGold
                            IngestionStatus.NORMALIZATION_REVIEW -> AccentGold.copy(alpha = 0.8f)
                            IngestionStatus.FAILED -> Color.Red.copy(alpha = 0.8f)
                            else -> TextMuted
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF222226))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(item.progress)
                                .clip(CircleShape)
                                .background(
                                    if (item.status == IngestionStatus.FAILED) {
                                        Brush.linearGradient(listOf(Color.Red, Color.Red.copy(alpha = 0.5f)))
                                    } else {
                                        progressBrush
                                    }
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { onCancel(item.id) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove item",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ReceiptForwardOnramp(
    email: String,
    onCopyEmail: () -> Unit
) {
    var orderLogCopied by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Email Receipt Forwarding",
                style = MaterialTheme.typography.titleLarge,
                color = AccentGold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Forward digital receipts/order confirmation emails from clothing retailers. Our engine extracts order items, pricing, images, and metadata automatically.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Clipboard copy block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF111114))
                    .border(0.5.dp, GoldBorder, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "YOUR EXCLUSIVE WARDROBE EMAIL",
                            fontFamily = OutfitFont,
                            fontSize = 10.sp,
                            color = AccentGold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = email,
                            fontFamily = OutfitFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextLight
                        )
                    }

                    IconButton(
                        onClick = {
                            onCopyEmail()
                            orderLogCopied = true
                        }
                    ) {
                        Icon(
                            imageVector = if (orderLogCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = AccentGold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Parsed Purchase Logs",
            fontFamily = OutfitFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = TextLight,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Mock purchase logs parsed
        GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Mr Porter Order #29381-P",
                        fontFamily = OutfitFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextLight
                    )
                    Text(
                        text = "Successfully parsed 2 garments • $330.00",
                        fontFamily = OutfitFont,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
fun RetailerLinkOnramp(
    url: String,
    onUrlChange: (String) -> Unit,
    isFetching: Boolean,
    onFetch: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Retailer Link share target",
                style = MaterialTheme.typography.titleLarge,
                color = AccentGold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Paste a product URL or share directly from browser/retailer app using the Android system Share intent. ClosetOS parses official catalog details.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text("Paste product page URL...", color = TextMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextLight,
                    unfocusedTextColor = TextLight,
                    focusedBorderColor = AccentGold,
                    unfocusedBorderColor = GlassBorder,
                    cursorColor = AccentGold
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            ElegantButton(
                text = if (isFetching) "Parsing metadata..." else "Import Garment",
                onClick = onFetch,
                enabled = url.isNotBlank() && !isFetching,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


