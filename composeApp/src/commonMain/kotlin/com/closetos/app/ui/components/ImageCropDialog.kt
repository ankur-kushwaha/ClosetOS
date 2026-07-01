package com.closetos.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closetos.app.decodeBase64ToBitmap
import com.closetos.app.rememberImageBitmap
import com.closetos.app.ui.theme.*
import kotlin.math.min
import kotlin.math.roundToInt

private data class FitRect(val left: Float, val top: Float, val width: Float, val height: Float)

private fun computeFitRect(containerW: Float, containerH: Float, imageW: Float, imageH: Float): FitRect {
    if (imageW <= 0f || imageH <= 0f) return FitRect(0f, 0f, containerW, containerH)
    val scale = min(containerW / imageW, containerH / imageH)
    val w = imageW * scale
    val h = imageH * scale
    return FitRect((containerW - w) / 2f, (containerH - h) / 2f, w, h)
}

private fun imageNormToScreenRect(
    cropLeft: Float,
    cropTop: Float,
    cropWidth: Float,
    cropHeight: Float,
    fit: FitRect
): Rect {
    return Rect(
        left = fit.left + cropLeft * fit.width,
        top = fit.top + cropTop * fit.height,
        right = fit.left + (cropLeft + cropWidth) * fit.width,
        bottom = fit.top + (cropTop + cropHeight) * fit.height
    )
}

@Composable
fun ImageCropDialog(
    imagePath: String? = null,
    imageBase64: String? = null,
    title: String = "Crop Image",
    onDismiss: () -> Unit,
    onConfirm: (cropLeft: Float, cropTop: Float, cropWidth: Float, cropHeight: Float) -> Unit
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var cropLeft by remember { mutableFloatStateOf(0.05f) }
    var cropTop by remember { mutableFloatStateOf(0.05f) }
    var cropWidth by remember { mutableFloatStateOf(0.9f) }
    var cropHeight by remember { mutableFloatStateOf(0.9f) }
    var dragMode by remember { mutableStateOf("move") }

    val base64Bitmap = if (!imageBase64.isNullOrEmpty()) {
        remember(imageBase64) { decodeBase64ToBitmap(imageBase64) }
    } else null
    val pathBitmap = if (!imagePath.isNullOrEmpty()) rememberImageBitmap(imagePath) else null
    val displayBitmap: ImageBitmap? = base64Bitmap ?: pathBitmap

    val fitRect = remember(containerSize, displayBitmap) {
        if (displayBitmap == null || containerSize.width == 0) {
            FitRect(0f, 0f, 0f, 0f)
        } else {
            computeFitRect(
                containerSize.width.toFloat(),
                containerSize.height.toFloat(),
                displayBitmap.width.toFloat(),
                displayBitmap.height.toFloat()
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontFamily = PlayfairFont,
                fontWeight = FontWeight.Bold,
                color = AccentGold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Drag box to move · drag corner to resize",
                    fontFamily = OutfitFont,
                    color = TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1B1B22))
                        .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
                        .onSizeChanged { containerSize = it },
                    contentAlignment = Alignment.Center
                ) {
                    if (displayBitmap != null && fitRect.width > 0f) {
                        Image(
                            bitmap = displayBitmap,
                            contentDescription = "Image to crop",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        val cropScreenRect = imageNormToScreenRect(cropLeft, cropTop, cropWidth, cropHeight, fitRect)

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(
                                color = Color.Black.copy(alpha = 0.45f),
                                topLeft = Offset.Zero,
                                size = Size(size.width, cropScreenRect.top)
                            )
                            drawRect(
                                color = Color.Black.copy(alpha = 0.45f),
                                topLeft = Offset(0f, cropScreenRect.bottom),
                                size = Size(size.width, size.height - cropScreenRect.bottom)
                            )
                            drawRect(
                                color = Color.Black.copy(alpha = 0.45f),
                                topLeft = Offset(0f, cropScreenRect.top),
                                size = Size(cropScreenRect.left, cropScreenRect.height)
                            )
                            drawRect(
                                color = Color.Black.copy(alpha = 0.45f),
                                topLeft = Offset(cropScreenRect.right, cropScreenRect.top),
                                size = Size(size.width - cropScreenRect.right, cropScreenRect.height)
                            )
                            drawRect(
                                color = AccentGold,
                                topLeft = Offset(cropScreenRect.left, cropScreenRect.top),
                                size = Size(cropScreenRect.width, cropScreenRect.height),
                                style = Stroke(width = 3f)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(fitRect) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val cropRect = imageNormToScreenRect(cropLeft, cropTop, cropWidth, cropHeight, fitRect)
                                            val handleSize = 48f
                                            val inCrop = cropRect.contains(offset)
                                            val inHandle = inCrop &&
                                                offset.x >= cropRect.right - handleSize &&
                                                offset.y >= cropRect.bottom - handleSize
                                            dragMode = when {
                                                inHandle -> "resize"
                                                inCrop -> "move"
                                                else -> "none"
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            if (dragMode == "none" || fitRect.width <= 0f || fitRect.height <= 0f) return@detectDragGestures
                                            val dx = dragAmount.x / fitRect.width
                                            val dy = dragAmount.y / fitRect.height
                                            if (dragMode == "move") {
                                                cropLeft = (cropLeft + dx).coerceIn(0f, 1f - cropWidth)
                                                cropTop = (cropTop + dy).coerceIn(0f, 1f - cropHeight)
                                            } else {
                                                cropWidth = (cropWidth + dx).coerceIn(0.1f, 1f - cropLeft)
                                                cropHeight = (cropHeight + dy).coerceIn(0.1f, 1f - cropTop)
                                            }
                                        }
                                    )
                                }
                        )
                    } else {
                        CircularProgressIndicator(color = AccentGold, modifier = Modifier.size(32.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(cropLeft, cropTop, cropWidth, cropHeight) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                enabled = displayBitmap != null
            ) {
                Text("Apply Crop", color = ObsidianBg, fontFamily = OutfitFont, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted, fontFamily = OutfitFont)
            }
        },
        containerColor = ObsidianBg,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
    )
}
