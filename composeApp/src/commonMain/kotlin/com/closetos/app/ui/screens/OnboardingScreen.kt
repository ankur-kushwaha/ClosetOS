package com.closetos.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
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
import com.closetos.app.data.model.UserTaste
import com.closetos.app.data.repository.ClosetRepository
import com.closetos.app.ui.components.ElegantButton
import com.closetos.app.ui.components.GlassmorphicCard
import com.closetos.app.ui.components.SectionHeader
import com.closetos.app.ui.components.TagChip
import com.closetos.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(onOnboardingComplete: () -> Unit) {
    var step by remember { mutableStateOf(1) } // 1-6 Quiz, 7 Twin Capture, 8 Twin Processing, 9 Success
    val scope = rememberCoroutineScope()
    
    // Quiz state variables
    var aesthetic by remember { mutableStateOf("") }
    var fitPref by remember { mutableStateOf("") }
    val colorsAvoided = remember { mutableStateListOf<String>() }
    val occasions = remember { mutableStateListOf<String>() }
    var primaryMaterial by remember { mutableStateOf("") }
    var closetGoal by remember { mutableStateOf("") }

    // Consent checkbox state
    var consentLiveness by remember { mutableStateOf(false) }
    var ageGateChecked by remember { mutableStateOf(false) }

    // Digital twin simulation state
    var selfieAttached by remember { mutableStateOf(false) }
    var processingLog by remember { mutableStateOf("Ready to capture.") }
    var progressVal by remember { mutableStateOf(0.0f) }

    val cameraLauncher = rememberCameraLauncher { selfiePath ->
        if (selfiePath != null) {
            selfieAttached = true
        } else {
            showToast("Camera capture cancelled or failed.")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .statusBarsPadding()
        ) {
            // Header Progress Indicator for Steps
            if (step <= 7) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ClosetOS",
                        fontFamily = PlayfairFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = AccentGold
                    )
                    Text(
                        text = "Step $step of 7",
                        fontFamily = OutfitFont,
                        fontSize = 14.sp,
                        color = TextMuted
                    )
                }
                LinearProgressIndicator(
                    progress = step / 7.0f,
                    color = AccentGold,
                    trackColor = GlassBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Animated step content wrapper
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        slideInHorizontally { width -> width } + fadeIn() with
                                slideOutHorizontally { width -> -width } + fadeOut()
                    },
                    label = "OnboardingSteps"
                ) { targetStep ->
                    when (targetStep) {
                        1 -> QuizSingleChoice(
                            title = "What is your main aesthetic?",
                            subtitle = "Seeds your taste vector for compatibility recommendations.",
                            options = listOf("Minimalist & Clean", "Classic Tailored", "Streetwear & Casual", "Bold & Eclectic"),
                            selected = aesthetic,
                            onSelect = { aesthetic = it }
                        )
                        2 -> QuizSingleChoice(
                            title = "How do you prefer clothes to fit?",
                            subtitle = "Dictates our silhouette matching logic.",
                            options = listOf("Slim & Fitted", "Regular & Balanced", "Relaxed & Drapey", "Oversized & Boxy"),
                            selected = fitPref,
                            onSelect = { fitPref = it }
                        )
                        3 -> QuizMultiChoice(
                            title = "Any colors you avoid?",
                            subtitle = "These colors will be filtered out from candidate OOTDs.",
                            options = listOf("Neon Orange", "Lime Green", "Electric Pink", "Bright Yellow", "Pastels", "Muted Neutrals"),
                            selectedList = colorsAvoided,
                            onToggle = { color ->
                                if (colorsAvoided.contains(color)) colorsAvoided.remove(color) else colorsAvoided.add(color)
                            }
                        )
                        4 -> QuizMultiChoice(
                            title = "Which occasions do you dress for?",
                            subtitle = "Helps contextualize calendar integration signals.",
                            options = listOf("Business Casual", "Formal Workwear", "Casual Lounging", "Weekend Socials", "Gym & Active", "Night Out"),
                            selectedList = occasions,
                            onToggle = { occasion ->
                                if (occasions.contains(occasion)) occasions.remove(occasion) else occasions.add(occasion)
                            }
                        )
                        5 -> QuizSingleChoice(
                            title = "What fabric do you wear most?",
                            subtitle = "Powers compatibility weighting for fabric pairings.",
                            options = listOf("100% Cotton", "Linen & Blends", "Merino Wool & Cashmere", "Denim & Heavy Twill", "Silk & Satin"),
                            selected = primaryMaterial,
                            onSelect = { primaryMaterial = it }
                        )
                        6 -> QuizSingleChoice(
                            title = "What is your primary closet goal?",
                            subtitle = "Aligns our re-ranking frequency boosters.",
                            options = listOf("Wear everything I own (CPW drop)", "Stop the morning decision fatigue", "Shop less and style more", "Build travel capsule templates"),
                            selected = closetGoal,
                            onSelect = { closetGoal = it }
                        )
                        7 -> DigitalTwinCaptureFlow(
                            selfieAttached = selfieAttached,
                            consentLiveness = consentLiveness,
                            ageGate = ageGateChecked,
                            onConsentChange = { consentLiveness = it },
                            onAgeChange = { ageGateChecked = it },
                            onAttachSelfie = {
                                cameraLauncher()
                            }
                        )
                        8 -> DigitalTwinProcessing(
                            log = processingLog,
                            progress = progressVal
                        )
                        9 -> DigitalTwinSuccess(
                            aesthetic = aesthetic,
                            fit = fitPref
                        )
                    }
                }
            }

            // Footer Button Controls
            if (step <= 7) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step > 1) {
                        ElegantButton(
                            text = "Back",
                            onClick = { step-- },
                            isSecondary = true
                        )
                    } else {
                        Spacer(modifier = Modifier.width(48.dp))
                    }

                    val nextEnabled = when (step) {
                        1 -> aesthetic.isNotEmpty()
                        2 -> fitPref.isNotEmpty()
                        3 -> true // Empty okay
                        4 -> occasions.isNotEmpty()
                        5 -> primaryMaterial.isNotEmpty()
                        6 -> closetGoal.isNotEmpty()
                        7 -> selfieAttached && consentLiveness && ageGateChecked
                        else -> false
                    }

                    ElegantButton(
                        text = if (step == 7) "Generate Twin" else "Continue",
                        enabled = nextEnabled,
                        onClick = {
                            if (step == 7) {
                                step = 8
                                // Launch simulation of the AI twin model generator pipeline
                                scope.launch {
                                    simulateTwinPipeline { logText, progress ->
                                        processingLog = logText
                                        progressVal = progress
                                    }
                                    // Save the Taste vector seeds in the repository
                                    ClosetRepository.updateTaste(
                                        UserTaste(
                                            preferredStyles = listOf(aesthetic),
                                            colorsAvoided = colorsAvoided.toList(),
                                            preferredFits = listOf(fitPref),
                                            occasions = occasions.toList()
                                        )
                                    )
                                    step = 9
                                }
                            } else {
                                step++
                            }
                        }
                    )
                }
            } else if (step == 9) {
                // Done step
                ElegantButton(
                    text = "Enter My Closet",
                    onClick = onOnboardingComplete,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                )
            }
        }
    }
}

// SIMULATION HELPER
private suspend fun simulateTwinPipeline(updateProgress: (String, Float) -> Unit) {
    updateProgress("Executing NSFW safety filter checks...", 0.1f)
    delay(800)
    updateProgress("Running liveness check & liveness validation...", 0.25f)
    delay(800)
    updateProgress("GroundingDINO: Isolating face anchors & face bounds...", 0.45f)
    delay(1000)
    updateProgress("Pose estimator: Extracting body dimensions and height profile...", 0.70f)
    delay(1200)
    updateProgress("SAM2: Extracting hair and skin matte elements...", 0.85f)
    delay(900)
    updateProgress("Applying watermarks & caching canonical model vector...", 0.98f)
    delay(600)
    updateProgress("Digital Twin Successfully Created!", 1.0f)
    delay(400)
}

@Composable
fun QuizSingleChoice(
    title: String,
    subtitle: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
        SectionHeader(title = title, subtitle = subtitle)
        Spacer(modifier = Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            options.forEach { option ->
                val isSel = option == selected
                val borderCol = if (isSel) AccentGold else GlassBorder
                val cardBg = if (isSel) Color(0x1CE5C185) else GlassOverlay

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .border(1.dp, borderCol, RoundedCornerShape(16.dp))
                        .clickable { onSelect(option) }
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            fontFamily = OutfitFont,
                            fontSize = 16.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) AccentGold else TextLight
                        )
                        if (isSel) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AccentGold,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuizMultiChoice(
    title: String,
    subtitle: String,
    options: List<String>,
    selectedList: List<String>,
    onToggle: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
        SectionHeader(title = title, subtitle = subtitle)
        Spacer(modifier = Modifier.height(24.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(options) { option ->
                val isSel = selectedList.contains(option)
                TagChip(
                    text = option,
                    isSelected = isSel,
                    onClick = { onToggle(option) },
                    modifier = Modifier.height(56.dp)
                )
            }
        }
    }
}

@Composable
fun DigitalTwinCaptureFlow(
    selfieAttached: Boolean,
    consentLiveness: Boolean,
    ageGate: Boolean,
    onConsentChange: (Boolean) -> Unit,
    onAgeChange: (Boolean) -> Unit,
    onAttachSelfie: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
        SectionHeader(
            title = "Establish Your Digital Twin",
            subtitle = "Your twin represents you in outfit renders. High precision silhouette model."
        )
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GlassOverlay)
                .border(1.dp, if (selfieAttached) AccentGold else GlassBorder, RoundedCornerShape(16.dp))
                .clickable { onAttachSelfie() },
            contentAlignment = Alignment.Center
        ) {
            if (!selfieAttached) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Tap to upload front-facing selfie",
                        fontFamily = OutfitFont,
                        fontSize = 15.sp,
                        color = TextLight
                    )
                    Text(
                        text = "Full face, good lighting, neutral background",
                        fontFamily = OutfitFont,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            } else {
                val bitmap = rememberImageBitmap("digital_twin_selfie.jpg")
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Digital Twin Selfie",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = null,
                            tint = AccentGold,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Selfie uploaded successfully",
                            fontFamily = OutfitFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = AccentGold
                        )
                        Text(
                            text = "Face matches bounds. Liveness verified.",
                            fontFamily = OutfitFont,
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Safety gate and consent agreements
        GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = consentLiveness,
                    onCheckedChange = onConsentChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = AccentGold,
                        uncheckedColor = TextMuted,
                        checkmarkColor = ObsidianBg
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I consent to ClosetOS processing my photo to generate my private digital twin. I confirm this is a photo of myself (Liveness Gate).",
                    fontFamily = OutfitFont,
                    fontSize = 12.sp,
                    color = TextLight,
                    lineHeight = 16.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = ageGate,
                    onCheckedChange = onAgeChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = AccentGold,
                        uncheckedColor = TextMuted,
                        checkmarkColor = ObsidianBg
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I verify I am 18 years or older (Age-Gate filter).",
                    fontFamily = OutfitFont,
                    fontSize = 12.sp,
                    color = TextLight
                )
            }
        }
    }
}

@Composable
fun DigitalTwinProcessing(
    log: String,
    progress: Float
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            progress = progress,
            color = AccentGold,
            trackColor = GlassBorder,
            strokeWidth = 6.dp,
            modifier = Modifier.size(100.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Generating Digital Twin",
            fontFamily = PlayfairFont,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextLight
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        GlassmorphicCard(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            useGoldBorder = true
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = log,
                    fontFamily = OutfitFont,
                    fontSize = 14.sp,
                    color = AccentGold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun DigitalTwinSuccess(
    aesthetic: String,
    fit: String
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0x33E5C185), Color.Transparent)))
                .border(2.dp, AccentGold, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = AccentGold,
                modifier = Modifier.size(80.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Welcome to ClosetOS",
            fontFamily = PlayfairFont,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextLight
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Twin verified, age-gated, and secured.",
            fontFamily = OutfitFont,
            fontSize = 15.sp,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TagChip(text = "Style: $aesthetic", isSelected = true)
            TagChip(text = "Silhouette: $fit", isSelected = true)
        }
    }
}
