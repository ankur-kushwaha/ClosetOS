package com.closetos.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closetos.app.data.model.Outfit
import com.closetos.app.data.model.TripPlan
import com.closetos.app.data.repository.ClosetRepository
import com.closetos.app.showToast
import com.closetos.app.getEpochTimeMillis
import com.closetos.app.ui.components.ElegantButton
import com.closetos.app.ui.components.GlassmorphicCard
import com.closetos.app.ui.components.SectionHeader
import com.closetos.app.ui.components.TagChip
import com.closetos.app.ui.theme.*

@Composable
fun TripPlannerScreen() {
    val allPlans by ClosetRepository.tripPlans.collectAsState()
    val activePlan = allPlans.lastOrNull()

    // Input States
    var destination by remember { mutableStateOf("") }
    var tripDays by remember { mutableStateOf(4) }
    var weatherMode by remember { mutableStateOf("Mild (55-70°F)") } // Cold, Mild, Warm

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(16.dp)
    ) {
        SectionHeader(
            title = "Trip Planner",
            subtitle = "Pack light. ClosetOS creates minimal travel capsules based on destination weather."
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Planner config card
            GlassmorphicCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text(
                    text = "Configure Travel Plan",
                    fontFamily = PlayfairFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AccentGold
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("Where are you traveling?", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextLight, focusedBorderColor = AccentGold, unfocusedBorderColor = GlassBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Days selector
                Text(
                    text = "Trip Duration: $tripDays Days",
                    fontFamily = OutfitFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextLight
                )
                Slider(
                    value = tripDays.toFloat(),
                    onValueChange = { tripDays = it.toInt() },
                    valueRange = 2f..7f,
                    steps = 4,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentGold, activeTrackColor = AccentGold, inactiveTrackColor = GlassBorder
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Weather selection
                Text(
                    text = "Forecast Profile",
                    fontFamily = OutfitFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextLight
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Cold (40-55°F)", "Mild (55-70°F)", "Warm (70-85°F)").forEach { mode ->
                        val isSel = mode == weatherMode
                        TagChip(
                            text = mode.split(" ").first(),
                            isSelected = isSel,
                            onClick = { weatherMode = mode }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                ElegantButton(
                    text = "Generate Travel Capsule",
                    enabled = destination.isNotBlank(),
                    onClick = {
                        val tempLow = when {
                            weatherMode.contains("Cold") -> 42f
                            weatherMode.contains("Mild") -> 56f
                            else -> 72f
                        }
                        val tempHigh = when {
                            weatherMode.contains("Cold") -> 52f
                            weatherMode.contains("Mild") -> 68f
                            else -> 82f
                        }
                        val condition = when {
                            weatherMode.contains("Cold") -> "Overcast / Rainy"
                            weatherMode.contains("Mild") -> "Partly Cloudy"
                            else -> "Clear & Sunny"
                        }
                        
                        ClosetRepository.generateTripPlan(
                            destination = destination,
                            start = getEpochTimeMillis(),
                            end = getEpochTimeMillis() + (tripDays * 24L * 60 * 60 * 1000),
                            tempLow = tempLow,
                            tempHigh = tempHigh,
                            condition = condition
                        )
                        showToast("Capsule generated for $destination!")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Display Active Generated Capsule Plan
            activePlan?.let { plan ->
                // Section: Capsule items to pack
                Text(
                    text = "Packed Capsule Wardrobe (${plan.capsuleGarments.size} items)",
                    fontFamily = PlayfairFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AccentGold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(plan.capsuleGarments) { garment ->
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(GlassOverlay)
                                .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
                                .padding(8.dp)
                        ) {
                            Column {
                                Icon(
                                    imageVector = when(garment.category) {
                                        "Top" -> Icons.Default.Checkroom
                                        "Bottom" -> Icons.Default.Accessibility
                                        else -> Icons.Default.Checkroom
                                    },
                                    contentDescription = null,
                                    tint = AccentGold,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = garment.subcategory,
                                    fontFamily = OutfitFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = TextLight
                                )
                                Text(
                                    text = garment.brand,
                                    fontFamily = OutfitFont,
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }

                // Section: Day by Day Outfits
                Text(
                    text = "Daily Lookbook Plan",
                    fontFamily = PlayfairFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AccentGold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    plan.dailyOutfits.forEachIndexed { idx, outfit ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(GlassOverlay)
                                .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(AccentGold)
                                        .border(0.5.dp, GoldBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        fontFamily = OutfitFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = ObsidianBg
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Day ${idx + 1} Outfit",
                                        fontFamily = OutfitFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextLight
                                    )
                                    Text(
                                        text = outfit.garments.joinToString(" + ") { it.subcategory },
                                        fontFamily = OutfitFont,
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }

                // Pre-render capsule control
                ElegantButton(
                    text = "Batch-Render Capsule Outfits (GPU queue)",
                    onClick = {
                        showToast("Travel capsule batch rendering queued on Hetzner GPU server!")
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    icon = Icons.Default.AutoAwesome
                )
            }
        }
    }
}
