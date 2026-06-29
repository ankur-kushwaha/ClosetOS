package com.closetos.app.ui.screens

import androidx.compose.animation.*
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
import com.closetos.app.data.model.*
import com.closetos.app.data.repository.ClosetRepository
import com.closetos.app.ui.components.ElegantButton
import com.closetos.app.ui.components.GlassmorphicCard
import com.closetos.app.ui.components.SectionHeader
import com.closetos.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

        // Tab Content Panel
        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                0 -> BulkCameraOnramp(
                    queue = queue,
                    onSelectPhotos = {
                        val photoUrls = listOf(
                            "gallery_image_cotton_tshirt.jpg",
                            "gallery_image_pleated_trousers.jpg",
                            "gallery_image_linen_blazer.jpg"
                        )
                        ClosetRepository.queueIngestionItems(photoUrls)
                        // Trigger background processor simulators
                        scope.launch {
                            ClosetRepository.ingestionQueue.value.takeLast(3).forEach { item ->
                                launch { simulateGarmentPipeline(item) }
                            }
                        }
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
                            // Put single parsed item in the queue
                            ClosetRepository.queueIngestionItems(listOf("retailer_fetched_silk_dress.jpg"))
                            val parsedItem = ClosetRepository.ingestionQueue.value.last()
                            launch { simulateGarmentPipeline(parsedItem) }
                            activeTab = 0 // jump back to monitor queue
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
    
    // Choose garment profile based on filename
    val mockGarment = when {
        filename.contains("tshirt") -> Garment(
            category = "Top",
            subcategory = "T-Shirt",
            colorName = "Crimson Red",
            labColor = floatArrayOf(45f, 60f, 30f),
            material = "Organic Cotton",
            pattern = "Plain",
            fit = "Regular",
            seasons = listOf("Summer", "Spring"),
            formalityScore = 0.1f,
            silhouette = "Relaxed",
            price = 45.00,
            brand = "A.P.C.",
            imageUrl = "red_tshirt",
            embedding = FloatArray(512).apply { this[100] = 0.3f; this[60] = 0.5f }
        )
        filename.contains("trousers") -> Garment(
            category = "Bottom",
            subcategory = "Pleated Trousers",
            colorName = "Olive Khaki",
            labColor = floatArrayOf(60f, -10f, 15f),
            material = "Linen Wool",
            pattern = "Plain",
            fit = "Relaxed",
            seasons = listOf("Spring", "Summer", "Autumn"),
            formalityScore = 0.7f,
            silhouette = "Wide-Leg",
            price = 180.00,
            brand = "Margaret Howell",
            imageUrl = "olive_trousers",
            embedding = FloatArray(512).apply { this[200] = 0.8f }
        )
        filename.contains("blazer") -> Garment(
            category = "Outerwear",
            subcategory = "Linen Blazer",
            colorName = "Natural Oatmeal",
            labColor = floatArrayOf(85f, 2f, 12f),
            material = "Linen",
            pattern = "Plain",
            fit = "Tailored",
            seasons = listOf("Summer", "Spring"),
            formalityScore = 0.75f,
            silhouette = "Single-breasted",
            price = 320.00,
            brand = "Loro Piana",
            imageUrl = "oatmeal_blazer",
            embedding = FloatArray(512).apply { this[300] = 0.8f; this[50] = 0.6f }
        )
        else -> Garment(
            category = "Top",
            subcategory = "Silk Blouse",
            colorName = "Ivory Cream",
            labColor = floatArrayOf(95f, 0f, 5f),
            material = "Morus Silk",
            pattern = "Plain",
            fit = "Fluid",
            seasons = listOf("Spring", "Summer"),
            formalityScore = 0.8f,
            silhouette = "Draped",
            price = 240.00,
            brand = "Equipment",
            imageUrl = "cream_blouse",
            embedding = FloatArray(512).apply { this[100] = 0.8f; this[30] = 0.8f }
        )
    }

    ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.PRE_FLIGHT, 0.15f, "Pre-flight: Checking size and exact duplicates...")
    delay(1000)
    
    ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.DETECTION, 0.35f, "GroundingDINO: Localizing boundaries...")
    delay(1200)

    ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.SEGMENTATION, 0.60f, "SAM2: Generating clean alpha-matte mask...")
    delay(1500)

    ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.TAGGING, 0.80f, "Florence-2: Distilling category/brand attributes...")
    delay(1200)

    ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.EMBEDDING, 0.95f, "FashionCLIP: Generating 512-dimension vector key...")
    delay(900)

    ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.READY, 1.0f, "Ingestion Pipeline Completed!", mockGarment)
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
            .border(0.5.dp, if (item.status == IngestionStatus.READY) AccentGold else GlassBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Mock thumbnail icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2E2E35))
                    .border(0.5.dp, GlassBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        item.originalImageUrl.contains("tshirt") -> Icons.Default.Checkroom
                        item.originalImageUrl.contains("trousers") -> Icons.Default.Accessibility
                        else -> Icons.Default.Checkroom
                    },
                    contentDescription = null,
                    tint = if (item.status == IngestionStatus.READY) AccentGold else TextMuted
                )
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
                        text = if (item.status == IngestionStatus.READY) "Review" else "${(item.progress * 100).toInt()}%",
                        fontFamily = OutfitFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.status == IngestionStatus.READY) AccentGold else TextMuted,
                        modifier = if (item.status == IngestionStatus.READY) Modifier.clickable { onReviewClick() } else Modifier
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.stepLabel,
                    fontFamily = OutfitFont,
                    fontSize = 11.sp,
                    color = if (item.status == IngestionStatus.READY) AccentGold else TextMuted
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
                            .background(progressBrush)
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
