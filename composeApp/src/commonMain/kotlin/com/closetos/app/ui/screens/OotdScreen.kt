package com.closetos.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.closetos.app.*
import com.closetos.app.data.model.Outfit
import com.closetos.app.data.repository.ClosetRepository
import com.closetos.app.ui.components.ElegantButton
import com.closetos.app.ui.components.GlassmorphicCard
import com.closetos.app.ui.components.TagChip
import com.closetos.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OotdScreen() {
    val scope = rememberCoroutineScope()

    var currentTempC by remember { mutableStateOf(23f) }
    var weatherDesc by remember { mutableStateOf("Clear & Sunny") }
    var locationName by remember { mutableStateOf("") }
    val occasionContext = "Work Presentation & Socials"
    var weatherRefreshKey by remember { mutableIntStateOf(0) }

    RequestLocationPermission { granted ->
        if (granted) weatherRefreshKey++
    }

    LaunchedEffect(weatherRefreshKey) {
        val weatherInfo = fetchWeatherInfo()
        currentTempC = weatherInfo.tempCelsius
        weatherDesc = weatherInfo.description
        locationName = weatherInfo.locationName
    }

    // Recommendations list from Repository
    val garmentsList by ClosetRepository.garments.collectAsState()
    
    // Generate cached recommendations overnight (repository uses Fahrenheit)
    val tempFahrenheit = currentTempC * 9f / 5f + 32f
    val cachedOutfits = remember(garmentsList, currentTempC) {
        ClosetRepository.generateRecommendations(tempFahrenheit, occasionContext)
    }

    var selectedOutfitIndex by remember { mutableStateOf(0) }
    val activeOutfit = remember(cachedOutfits, selectedOutfitIndex) {
        cachedOutfits.getOrNull(selectedOutfitIndex)
    }

    // Wear Logging states
    var wearLogged by remember { mutableStateOf(false) }
    var loveLogged by remember { mutableStateOf(false) }

    // Reroll state
    var isRerolling by remember { mutableStateOf(false) }
    var showPremiumRerollDialog by remember { mutableStateOf(false) }

    // Reset feedback state on outfit index change
    LaunchedEffect(selectedOutfitIndex) {
        wearLogged = false
        loveLogged = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(16.dp)
    ) {
        // Morning Welcome Banner
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Monday Morning",
                    fontFamily = OutfitFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Bonjour, Ankur",
                    fontFamily = PlayfairFont,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextLight
                )
            }

            // Weather & location pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlassOverlay)
                    .border(0.5.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    if (locationName.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = AccentGold,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = locationName,
                                fontFamily = OutfitFont,
                                fontSize = 11.sp,
                                color = TextMuted,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = null,
                            tint = AccentGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${currentTempC.toInt()}°C • $weatherDesc",
                            fontFamily = OutfitFont,
                            fontSize = 12.sp,
                            color = TextLight
                        )
                    }
                }
            }
        }

        Divider(color = GoldBorder, thickness = 0.5.dp, modifier = Modifier.padding(bottom = 16.dp))

        if (activeOutfit == null) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No garments in closet. Please digitize garments first.",
                    fontFamily = OutfitFont,
                    color = TextMuted
                )
            }
            return
        }

        // Main OOTD Card Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isRerolling) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = AccentGold, strokeWidth = 4.dp, modifier = Modifier.size(60.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Running IDM-VTON model on Hetzner GPU...",
                        fontFamily = OutfitFont,
                        fontSize = 14.sp,
                        color = AccentGold
                    )
                    Text(
                        text = "Pre-generating silhouette overlay...",
                        fontFamily = OutfitFont,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Digital twin canvas
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF16161B), Color(0xFF0F0F12))
                                )
                            )
                            .border(0.5.dp, GlassBorder, RoundedCornerShape(16.dp))
                    ) {
                        // Watermark tag
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33000000))
                                .border(0.5.dp, GlassBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = AccentGold.copy(alpha = 0.7f),
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "SynthID Secured",
                                    fontFamily = OutfitFont,
                                    fontSize = 9.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        // Simulated avatar rendering
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsRun,
                                contentDescription = "Digital Twin Canvas",
                                tint = AccentGold.copy(alpha = 0.15f),
                                modifier = Modifier.size(140.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Segmented crops grid layout
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                activeOutfit.garments.forEach { garment ->
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(GlassOverlay)
                                            .border(0.5.dp, GoldBorder, RoundedCornerShape(8.dp))
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            val bitmap = rememberImageBitmap(garment.imageUrl)
                                            if (bitmap != null) {
                                                Image(
                                                    bitmap = bitmap,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp))
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
                                                    tint = AccentGold,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Text(
                                                text = garment.subcategory.take(8),
                                                fontFamily = OutfitFont,
                                                fontSize = 8.sp,
                                                color = TextLight,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Compatibility specs card
                    GlassmorphicCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "OOTD Rationale",
                                fontFamily = PlayfairFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = AccentGold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TagChip(text = "Color Harmony: 94%", isSelected = false)
                                TagChip(text = "Style Fit: 98%", isSelected = false)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = activeOutfit.reason,
                            fontFamily = OutfitFont,
                            fontSize = 13.sp,
                            color = TextLight,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // Wear Logging Flywheel Trigger Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // "Wore It" trigger
                            ElegantButton(
                                text = if (wearLogged) "Worn Logged" else "Wore It",
                                onClick = {
                                    ClosetRepository.logWear(activeOutfit, loved = false)
                                    wearLogged = true
                                    showToast("CPW adjusted! Ingested garments washing cycle log written.")
                                },
                                enabled = !wearLogged,
                                icon = if (wearLogged) Icons.Default.Check else Icons.Default.CheckCircle,
                                modifier = Modifier.weight(1f)
                            )

                            // "Loved It" trigger
                            IconButton(
                                onClick = {
                                    if (wearLogged) {
                                        ClosetRepository.logWear(activeOutfit, loved = true)
                                        loveLogged = true
                                        showToast("Evolved ClosetOS taste vector centroid!")
                                    } else {
                                        ClosetRepository.logWear(activeOutfit, loved = true)
                                        wearLogged = true
                                        loveLogged = true
                                        showToast("Logged wear + taste vector optimized!")
                                    }
                                },
                                enabled = !loveLogged,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (loveLogged) Color(0xFFFF4081) else GlassOverlay)
                                    .border(0.5.dp, if (loveLogged) Color.Transparent else GlassBorder, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Love Outfit",
                                    tint = if (loveLogged) ObsidianBg else AccentGold
                                )
                            }

                            // Reroll premium button
                            IconButton(
                                onClick = { showPremiumRerollDialog = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(GlassOverlay)
                                    .border(0.5.dp, GlassBorder, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "On-demand reroll",
                                    tint = AccentGold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Alternates Shelf
        Text(
            text = "Overnight Cached Alternates",
            fontFamily = OutfitFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = TextLight,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cachedOutfits.size) { index ->
                val outfit = cachedOutfits[index]
                val isSel = index == selectedOutfitIndex
                val borderCol = if (isSel) AccentGold else GlassBorder
                val bg = if (isSel) Color(0x1CE5C185) else GlassOverlay

                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                        .clickable { selectedOutfitIndex = index }
                        .padding(8.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = if (index == 0) "Top Daily Pick" else "Alternate #${index}",
                            fontFamily = OutfitFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isSel) AccentGold else TextLight
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            outfit.garments.forEach { g ->
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2E2E35)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when(g.category) {
                                            "Top" -> Icons.Default.Checkroom
                                            "Bottom" -> Icons.Default.Accessibility
                                            else -> Icons.Default.Checkroom
                                        },
                                        contentDescription = null,
                                        tint = AccentGold.copy(alpha = 0.7f),
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // PREMIUM REROLL ALERT DIALOG
    if (showPremiumRerollDialog) {
        AlertDialog(
            onDismissRequest = { showPremiumRerollDialog = false },
            containerColor = CharcoalSurface,
            title = {
                Text(
                    text = "On-Demand GPU Re-render",
                    fontFamily = PlayfairFont,
                    color = AccentGold
                )
            },
            text = {
                Text(
                    text = "Generating a custom lookbook render outside the nightly batch cache consumes 1 Premium GPU Token. Continue?",
                    fontFamily = OutfitFont,
                    color = TextLight
                )
            },
            dismissButton = {
                ElegantButton(
                    text = "Cancel",
                    isSecondary = true,
                    onClick = { showPremiumRerollDialog = false }
                )
            },
            confirmButton = {
                ElegantButton(
                    text = "Render (Token -1)",
                    onClick = {
                        showPremiumRerollDialog = false
                        isRerolling = true
                        scope.launch {
                            delay(3000) // Simulate Hetzner render delay
                            isRerolling = false
                            showToast("GPU Try-on successful! New outfit cached.")
                            selectedOutfitIndex = (selectedOutfitIndex + 1) % cachedOutfits.size
                        }
                    }
                )
            }
        )
    }
}
