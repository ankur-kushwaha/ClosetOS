package com.closetos.app.ui.screens

import androidx.compose.animation.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import android.util.Base64
import org.json.JSONObject
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
import androidx.compose.ui.platform.LocalContext
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
import com.closetos.app.ui.components.rememberImageBitmap
import com.closetos.app.ui.theme.*
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

        val context = LocalContext.current
        val galleryLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents()
        ) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                scope.launch {
                    val localPaths = uris.mapIndexed { idx, uri ->
                        val fileName = "gallery_crop_${System.currentTimeMillis()}_$idx.jpg"
                        val file = File(context.filesDir, fileName)
                        context.contentResolver.openInputStream(uri)?.use { inputStream: java.io.InputStream ->
                            FileOutputStream(file).use { outputStream: FileOutputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        file.absolutePath
                    }
                    ClosetRepository.queueIngestionItems(localPaths)
                    // Trigger background pipelines
                    localPaths.forEach { path: String ->
                        // Retrieve the queued item dynamically
                        val item = ClosetRepository.ingestionQueue.value.find { it.originalImageUrl == path }
                        if (item != null) {
                            launch { simulateGarmentPipeline(item, context) }
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
                    onSelectPhotos = {
                        galleryLauncher.launch("image/*")
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
                            launch { simulateGarmentPipeline(parsedItem, context) }
                            activeTab = 0
                        }
                    }
                )
            }
        }
    }
}

// PIPELINE BACKGROUND TASK SIMULATOR
private suspend fun simulateGarmentPipeline(item: IngestionItem, context: android.content.Context) {
    val id = item.id
    val filename = item.originalImageUrl
    
    // Step 1: Pre-flight check
    ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.PRE_FLIGHT, 0.10f, "Pre-flight: Checking size and duplicate index...")
    delay(800)
    
    // Step 2: Grounding DINO
    ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.GROUNDING_DINO, 0.25f, "Grounding DINO: Localizing garment bounding box...")
    delay(1000)
    
    // Step 3: SAM2
    ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.SAM2, 0.40f, "SAM2: Generative mask segmentation...")
    delay(1200)
    
    // Step 4: Crop garment
    ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.CROP_GARMENT, 0.55f, "Crop Garment: Isolating item...")
    
    val backendGarment = uploadImageToBackend(context, filename)
    
    val finalGarment = if (backendGarment != null) {
        // Step 5: FashionCLIP
        ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.FASHION_CLIP, 0.70f, "FashionCLIP: Generating 512-dimension vector key...")
        delay(1000)
        
        // Step 6: Florence-2
        ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.FLORENCE_2, 0.85f, "Florence-2: Extracting rich metadata...")
        delay(800)
        
        backendGarment
    } else {
        // Fallback to local simulation when server is offline
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Backend server offline. Running local high-fidelity fallback.", Toast.LENGTH_LONG).show()
        }
        
        // Run local crop
        val isRealFile = filename.startsWith("/") || filename.contains(".")
        val croppedPath = if (isRealFile) {
            cropAndSegmentGarmentImage(context, filename)
        } else {
            filename
        }
        
        // Step 5: FashionCLIP
        ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.FASHION_CLIP, 0.70f, "FashionCLIP (Fallback): Generating vector key...")
        delay(1000)
        
        // Step 6: Florence-2
        ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.FLORENCE_2, 0.85f, "Florence-2 (Fallback): Extracting metadata...")
        delay(800)
        
        // Match templates based on filename keyword
        val template = garmentTemplates.firstOrNull {
            filename.contains(it.subcategory.replace(" ", "").lowercase()) ||
            filename.contains(it.category.lowercase()) ||
            filename.contains(it.material.lowercase())
        } ?: garmentTemplates.firstOrNull {
            filename.contains("shirt") && it.category == "Top"
        } ?: garmentTemplates.firstOrNull {
            (filename.contains("pants") || filename.contains("trousers") || filename.contains("jeans")) && it.category == "Bottom"
        } ?: garmentTemplates.firstOrNull {
            (filename.contains("blazer") || filename.contains("jacket") || filename.contains("coat")) && it.category == "Outerwear"
        } ?: garmentTemplates.firstOrNull {
            (filename.contains("loafers") || filename.contains("sneakers") || filename.contains("shoes")) && it.category == "Shoes"
        } ?: garmentTemplates[(id.hashCode() % garmentTemplates.size).let { if (it < 0) it + garmentTemplates.size else it }]
        
        var detectedColorName = "Classic Black"
        var detectedLabColor = floatArrayOf(20f, 0f, 0f)
        if (isRealFile) {
            val avgColor = calculateAverageColor(croppedPath)
            detectedColorName = getColorNameFromRgb(avgColor)
            detectedLabColor = rgbToLab(avgColor)
        }
        
        val embedding = FloatArray(512).apply {
            for (i in indices) this[i] = (Math.random() * 0.05).toFloat()
            this[template.category.hashCode().let { if (it < 0) -it else it } % 512] = 0.8f
            this[template.subcategory.hashCode().let { if (it < 0) -it else it } % 512] = 0.6f
            this[detectedColorName.hashCode().let { if (it < 0) -it else it } % 512] = 0.7f
        }
        
        Garment(
            category = template.category,
            subcategory = template.subcategory,
            colorName = detectedColorName,
            labColor = detectedLabColor,
            material = template.material,
            pattern = template.pattern,
            fit = template.fit,
            seasons = template.seasons,
            formalityScore = template.formalityScore,
            silhouette = template.silhouette,
            price = template.price,
            brand = template.brand + " (Local Fallback)",
            imageUrl = croppedPath,
            embedding = embedding
        )
    }
    
    // Complete
    ClosetRepository.updateIngestionItemProgress(id, IngestionStatus.READY, 1.0f, "Ingestion Pipeline Completed!", finalGarment)
}

// --- BACKEND HTTP API UPLOADER ---
private suspend fun uploadImageToBackend(context: android.content.Context, imagePath: String): Garment? {
    return withContext(Dispatchers.IO) {
        var connection: java.net.HttpURLConnection? = null
        try {
            val file = File(imagePath)
            if (!file.exists()) return@withContext null

            val url = java.net.URL("http://10.0.2.2:8000/digitize")
            connection = url.openConnection() as java.net.HttpURLConnection
            val boundary = "===Boundary-${System.currentTimeMillis()}==="
            
            connection.doOutput = true
            connection.doInput = true
            connection.useCaches = false
            connection.requestMethod = "POST"
            connection.connectTimeout = 30000 // 30s timeout for ML model execution
            connection.readTimeout = 60000
            connection.setRequestProperty("Connection", "Keep-Alive")
            connection.setRequestProperty("User-Agent", "Android Ingest")
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            connection.outputStream.use { out ->
                val writer = java.io.PrintWriter(java.io.OutputStreamWriter(out, "UTF-8"), true)
                
                // File part
                writer.append("--$boundary").append("\r\n")
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"").append("\r\n")
                writer.append("Content-Type: image/jpeg").append("\r\n")
                writer.append("\r\n").flush()

                file.inputStream().use { input ->
                    input.copyTo(out)
                }
                out.flush()
                
                writer.append("\r\n")
                writer.append("--$boundary--").append("\r\n").flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                val jsonResponse = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonResponse)
                val base64Img = root.getString("image_base64")
                val attr = root.getJSONObject("attributes")

                // Decode base64 PNG and write to file
                val pngBytes = Base64.decode(base64Img, Base64.DEFAULT)
                val croppedFile = File(context.filesDir, "cropped_server_${System.currentTimeMillis()}.png")
                FileOutputStream(croppedFile).use { fos ->
                    fos.write(pngBytes)
                }

                // Parse embedding
                val embedArray = attr.getJSONArray("embedding")
                val embedding = FloatArray(512) { embedArray.getDouble(it).toFloat() }

                // Parse labColor
                val labArray = attr.getJSONArray("labColor")
                val labColor = FloatArray(3) { labArray.getDouble(it).toFloat() }

                // Parse seasons
                val seasonsArray = attr.getJSONArray("seasons")
                val seasons = List(seasonsArray.length()) { seasonsArray.getString(it) }

                Garment(
                    category = attr.getString("category"),
                    subcategory = attr.getString("subcategory"),
                    colorName = attr.getString("colorName"),
                    labColor = labColor,
                    material = attr.getString("material"),
                    pattern = attr.getString("pattern"),
                    fit = attr.getString("fit"),
                    seasons = seasons,
                    formalityScore = attr.getDouble("formalityScore").toFloat(),
                    silhouette = attr.getString("silhouette"),
                    brand = attr.optString("brand", "Unknown"),
                    price = attr.optDouble("price", 0.0),
                    imageUrl = croppedFile.absolutePath,
                    embedding = embedding
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            connection?.disconnect()
        }
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
            .border(0.5.dp, if (item.status == IngestionStatus.READY) AccentGold else GlassBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Mock thumbnail icon / actual image preview
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
                        tint = if (item.status == IngestionStatus.READY) AccentGold else TextMuted
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

// --- CROP & SEGMENTATION HELPER ---
private fun cropAndSegmentGarmentImage(context: android.content.Context, originalPath: String): String {
    try {
        val file = File(originalPath)
        if (!file.exists()) return originalPath

        // Load original bitmap
        val originalBitmap = BitmapFactory.decodeFile(originalPath) ?: return originalPath

        val width = originalBitmap.width
        val height = originalBitmap.height

        // Center crop with 75% width and height
        val cropWidth = (width * 0.75f).toInt()
        val cropHeight = (height * 0.75f).toInt()
        val startX = (width - cropWidth) / 2
        val startY = (height - cropHeight) / 2

        val croppedBitmap = Bitmap.createBitmap(originalBitmap, startX, startY, cropWidth, cropHeight)

        // Simulating SAM2 transparent background mask (vignette cutout)
        val maskedBitmap = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(maskedBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val path = Path()
        val rect = RectF(0f, 0f, cropWidth.toFloat(), cropHeight.toFloat())
        // Soft rounded corners representing segmented mask boundary
        path.addRoundRect(rect, cropWidth * 0.20f, cropHeight * 0.20f, Path.Direction.CW)

        canvas.clipPath(path)
        canvas.drawBitmap(croppedBitmap, 0f, 0f, paint)

        // Save to temporary cropped file
        val croppedFileName = "cropped_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}.png"
        val croppedFile = File(context.filesDir, croppedFileName)
        FileOutputStream(croppedFile).use { out ->
            maskedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // Clean up
        originalBitmap.recycle()
        croppedBitmap.recycle()
        maskedBitmap.recycle()

        return croppedFile.absolutePath
    } catch (e: java.lang.Exception) {
        e.printStackTrace()
        return originalPath
    }
}

// --- COLOR ANALYSIS HELPERS ---
private fun calculateAverageColor(bitmapPath: String): Int {
    try {
        val bitmap = BitmapFactory.decodeFile(bitmapPath) ?: return android.graphics.Color.GRAY
        var redBucket = 0L
        var greenBucket = 0L
        var blueBucket = 0L
        var pixelCount = 0L

        val step = 10
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val c = bitmap.getPixel(x, y)
                val alpha = android.graphics.Color.alpha(c)
                if (alpha > 50) {
                    redBucket += android.graphics.Color.red(c)
                    greenBucket += android.graphics.Color.green(c)
                    blueBucket += android.graphics.Color.blue(c)
                    pixelCount++
                }
            }
        }
        bitmap.recycle()

        if (pixelCount == 0L) return android.graphics.Color.GRAY

        val r = (redBucket / pixelCount).toInt()
        val g = (greenBucket / pixelCount).toInt()
        val b = (blueBucket / pixelCount).toInt()
        return android.graphics.Color.rgb(r, g, b)
    } catch (e: java.lang.Exception) {
        return android.graphics.Color.GRAY
    }
}

private fun getColorNameFromRgb(color: Int): String {
    val r = android.graphics.Color.red(color)
    val g = android.graphics.Color.green(color)
    val b = android.graphics.Color.blue(color)

    return when {
        r > 220 && g > 220 && b > 220 -> "Ivory White"
        r < 50 && g < 50 && b < 50 -> "Midnight Black"
        r > 130 && g > 130 && b > 130 -> "Classic Gray"
        r > 150 && g < 80 && b < 80 -> "Crimson Red"
        r < 80 && g > 130 && b < 80 -> "Forest Green"
        r < 80 && g < 80 && b > 150 -> "Ocean Blue"
        r > 200 && g > 180 && b < 80 -> "Sun Yellow"
        r > 180 && g > 110 && b < 60 -> "Terracotta Orange"
        r > 110 && g > 80 && b < 50 -> "Chestnut Brown"
        r > 120 && g < 60 && b > 120 -> "Royal Purple"
        r > 90 && g > 110 && b > 90 -> "Sage Olive"
        else -> "Desert Khaki"
    }
}

private fun rgbToLab(rgb: Int): FloatArray {
    val r = android.graphics.Color.red(rgb) / 255f
    val g = android.graphics.Color.green(rgb) / 255f
    val b = android.graphics.Color.blue(rgb) / 255f

    val l = (0.2126f * r + 0.7152f * g + 0.0722f * b) * 100f
    val a = (r - g) * 100f
    val bb = (g - b) * 100f
    return floatArrayOf(l, a, bb)
}

// --- METADATA EXTRACTION HEURISTICS ---
private data class GarmentTemplate(
    val category: String,
    val subcategory: String,
    val material: String,
    val pattern: String,
    val fit: String,
    val silhouette: String,
    val brand: String,
    val price: Double,
    val seasons: List<String>,
    val formalityScore: Float
)

private val garmentTemplates = listOf(
    GarmentTemplate("Top", "Oxford Shirt", "Organic Cotton", "Plain", "Regular", "Button-Down", "Ralph Lauren", 125.0, listOf("Spring", "Autumn", "Winter"), 0.6f),
    GarmentTemplate("Top", "Silk Blouse", "Morus Silk", "Plain", "Fluid", "Draped", "Equipment", 240.0, listOf("Spring", "Summer"), 0.8f),
    GarmentTemplate("Top", "T-Shirt", "Supima Cotton", "Plain", "Regular", "Crewneck", "A.P.C.", 45.0, listOf("Summer", "Spring"), 0.1f),
    GarmentTemplate("Bottom", "Pleated Trousers", "Linen Wool", "Plain", "Relaxed", "Wide-Leg", "Margaret Howell", 180.0, listOf("Spring", "Summer", "Autumn"), 0.7f),
    GarmentTemplate("Bottom", "Selvedge Jeans", "Japanese Denim", "Plain", "Straight", "Classic 5-Pocket", "OrSlow", 260.0, listOf("Autumn", "Winter", "Spring"), 0.2f),
    GarmentTemplate("Outerwear", "Linen Blazer", "Belgian Linen", "Plain", "Tailored", "Single-breasted", "Loro Piana", 320.0, listOf("Summer", "Spring"), 0.75f),
    GarmentTemplate("Outerwear", "Trench Coat", "Gabardine", "Plain", "Classic", "Double-breasted", "Burberry", 850.0, listOf("Spring", "Autumn"), 0.85f),
    GarmentTemplate("Shoes", "Leather Loafers", "Calfskin Leather", "Plain", "Standard", "Penny Loafer", "G.H. Bass", 175.0, listOf("Spring", "Summer", "Autumn"), 0.7f),
    GarmentTemplate("Shoes", "Canvas Sneakers", "Cotton Canvas", "Plain", "Standard", "Low-top", "Common Projects", 290.0, listOf("Summer", "Spring"), 0.1f)
)
