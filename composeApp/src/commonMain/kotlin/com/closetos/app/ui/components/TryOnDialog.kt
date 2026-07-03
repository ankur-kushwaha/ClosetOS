package com.closetos.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.closetos.app.data.model.Outfit
import com.closetos.app.data.model.TryOnResult
import com.closetos.app.data.repository.TryOnService
import com.closetos.app.showToast
import com.closetos.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TryOnDialog(
    outfit: Outfit,
    forceRefresh: Boolean = false,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember(outfit.id, forceRefresh) { mutableStateOf(true) }
    var tryOnResult by remember(outfit.id, forceRefresh) { mutableStateOf<TryOnResult?>(null) }
    var errorMessage by remember(outfit.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(outfit.id, forceRefresh) {
        isLoading = true
        errorMessage = null
        val result = TryOnService.renderOutfit(outfit, forceRefresh = forceRefresh)
        isLoading = false
        if (result != null) {
            tryOnResult = result
            if (!result.fromCache) {
                showToast("Try-on render complete")
            }
        } else {
            errorMessage = "Could not generate try-on. Add a digital twin selfie and ensure the backend is running with GEMINI_API_KEY set."
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .background(CharcoalSurface)
                .padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Virtual Try-On",
                            fontFamily = PlayfairFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = AccentGold
                        )
                        Text(
                            text = outfit.name,
                            fontFamily = OutfitFont,
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDarkThemeGlobal) Color(0xFF1E1E24) else Color(0xFFF3F3F5)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = AccentGold, strokeWidth = 3.dp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Rendering with Gemini 3.1 Flash Lite Image…",
                                    fontFamily = OutfitFont,
                                    fontSize = 13.sp,
                                    color = AccentGold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                        tryOnResult != null -> {
                            val bitmap = rememberTryOnBitmap(tryOnResult)
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = "Try-on render",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = "Try-on image could not be loaded. Tap Re-render to try again.",
                                    fontFamily = OutfitFont,
                                    fontSize = 13.sp,
                                    color = TextMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(24.dp)
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = errorMessage ?: "Try-on unavailable",
                                fontFamily = OutfitFont,
                                fontSize = 13.sp,
                                color = TextMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ElegantButton(
                        text = "Re-render",
                        isSecondary = true,
                        onClick = {
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                val result = TryOnService.renderOutfit(outfit, forceRefresh = true)
                                isLoading = false
                                if (result != null) {
                                    tryOnResult = result
                                    showToast("Try-on re-rendered")
                                } else {
                                    errorMessage = "Re-render failed"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    )
                    ElegantButton(
                        text = "Done",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
