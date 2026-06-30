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
import com.closetos.app.*
import com.closetos.app.data.model.*
import com.closetos.app.data.repository.ClosetRepository
import com.closetos.app.ui.components.ElegantButton
import com.closetos.app.ui.components.GlassmorphicCard
import com.closetos.app.ui.components.SectionHeader
import com.closetos.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

@Composable
fun IngestionScreen(
    sharedUrl: String? = null,
    onNavigateToReview: () -> Unit
) {
    var activeTab by remember { mutableStateOf(0) } // 0: Bulk, 1: Receipt, 2: Retailer
    val queue by ClosetRepository.ingestionQueue.collectAsState()
    val scope = rememberCoroutineScope()

    var retailerUrl by remember { mutableStateOf(sharedUrl ?: "") }
    var isFetchingLink by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current

    var showServerConfig by remember { mutableStateOf(false) }
    var serverIpInput by remember {
        mutableStateOf(PlatformStorage.loadString("backend_ip") ?: "http://10.0.2.2:8000")
    }
    var connectionStatus by remember { mutableStateOf("Unknown") } // "Checking", "Connected", "Failed", "Unknown"

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
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val testUrl = if (serverIpInput.startsWith("http://") || serverIpInput.startsWith("https://")) {
                                            serverIpInput
                                        } else {
                                            "http://$serverIpInput"
                                        }
                                        val url = java.net.URL(testUrl.trimEnd('/') + "/")
                                        val conn = url.openConnection() as java.net.HttpURLConnection
                                        conn.connectTimeout = 3000
                                        conn.readTimeout = 3000
                                        conn.requestMethod = "GET"
                                        val code = conn.responseCode
                                        connectionStatus = if (code in 200..399 || code == 404) "Connected" else "Failed"
                                        conn.disconnect()
                                    } catch (e: Exception) {
                                        connectionStatus = "Failed"
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
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("Receipts", fontFamily = OutfitFont, fontSize = 14.sp) },
                icon = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp)) }
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                text = { Text("Retailer Link", fontFamily = OutfitFont, fontSize = 14.sp) },
                icon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(20.dp)) }
            )
        }

        val galleryLauncher = rememberImagePickerLauncher { localPaths ->
            if (localPaths.isNotEmpty()) {
                ClosetRepository.queueIngestionItems(localPaths)
                localPaths.forEach { path ->
                    val item = ClosetRepository.ingestionQueue.value.find { it.originalImageUrl == path }
                    if (item != null) {
                        scope.launch { simulateGarmentPipeline(item) }
                    }
                }
            }
        }

        // Tab Content Panel
        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                0 -> BulkCameraOnramp(
                    queue = queue,
                    onSelectPhotos = {
                        galleryLauncher()
                    },
                    onNavigateToReview = onNavigateToReview
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
    }
}

// PIPELINE BACKGROUND TASK SIMULATOR
private suspend fun simulateGarmentPipeline(item: IngestionItem) {
    val id = item.id
    val filename = item.originalImageUrl
    
    // Step 1: Pre-flight check
    ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.PRE_FLIGHT, 0.10f, "Pre-flight: Checking size and exact duplicates...")
    delay(800)
    
    // Step 2: Grounding DINO
    ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.GROUNDING_DINO, 0.25f, "Grounding DINO: Localizing garment bounding box...")
    delay(1000)
    
    // Step 3: SAM2
    ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.SAM2, 0.40f, "SAM2: Generative mask segmentation...")
    delay(1200)
    
    // Step 4: Crop garment
    ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.CROP_GARMENT, 0.55f, "Crop Garment: Isolating item...")
    
    val backendGarments = runImageExtraction(filename)
    
    if (backendGarments != null && backendGarments.isNotEmpty()) {
        // Step 5: FashionCLIP
        ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.FASHION_CLIP, 0.70f, "FashionCLIP: Generating 512-dimension vector key...")
        delay(1000)
        
        // Step 6: Florence-2
        ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.FLORENCE_2, 0.85f, "Florence-2: Extracting rich metadata...")
        delay(800)
        
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
            progress = 0.55f,
            label = "Backend connection failed. Please check server status and IP configuration."
        )
    }
}

@Composable
fun BulkCameraOnramp(
    queue: List<IngestionItem>,
    onSelectPhotos: () -> Unit,
    onNavigateToReview: () -> Unit
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
                text = "Select from Camera Roll (Multi-select)",
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
            if (readyCount > 0) {
                Text(
                    text = "$readyCount Ready for Sweep",
                    fontFamily = OutfitFont,
                    fontSize = 13.sp,
                    color = AccentGold,
                    modifier = Modifier.clickable { onNavigateToReview() }
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
                    PipelineItemRow(item, onNavigateToReview)
                }
            }
        }
    }
}

@Composable
fun PipelineItemRow(item: IngestionItem, onReviewClick: () -> Unit) {
    val progressBrush = Brush.linearGradient(listOf(AccentGold, AccentGoldMuted))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassOverlay)
            .border(
                0.5.dp,
                when (item.status) {
                    IngestionStatus.READY -> AccentGold
                    IngestionStatus.FAILED -> Color.Red
                    else -> GlassBorder
                },
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
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
                val previewPath = item.detectedGarment?.imageUrl ?: item.originalImageUrl
                val bitmap = rememberImageBitmap(previewPath)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Thumbnail Preview",
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Icon(
                        imageVector = when {
                            item.originalImageUrl.contains("tshirt") -> Icons.Default.Checkroom
                            item.originalImageUrl.contains("trousers") -> Icons.Default.Accessibility
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

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.originalImageUrl.replace("gallery_image_", "").replace("retailer_fetched_", ""),
                        fontFamily = OutfitFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextLight
                    )
                    
                    Text(
                        text = when (item.status) {
                            IngestionStatus.READY -> "Review"
                            IngestionStatus.FAILED -> "Failed"
                            else -> "${(item.progress * 100).toInt()}%"
                        },
                        fontFamily = OutfitFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (item.status) {
                            IngestionStatus.READY -> AccentGold
                            IngestionStatus.FAILED -> Color.Red
                            else -> TextMuted
                        },
                        modifier = if (item.status == IngestionStatus.READY) Modifier.clickable { onReviewClick() } else Modifier
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.stepLabel,
                    fontFamily = OutfitFont,
                    fontSize = 11.sp,
                    color = when (item.status) {
                        IngestionStatus.READY -> AccentGold
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

            if (item.status == IngestionStatus.FAILED || item.status == IngestionStatus.READY) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        ClosetRepository.rejectIngestionItem(item.id)
                    },
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
