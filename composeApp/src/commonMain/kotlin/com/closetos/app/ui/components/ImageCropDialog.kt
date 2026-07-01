package com.closetos.app.ui.components

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
import androidx.compose.ui.graphics.Color
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
import kotlin.math.roundToInt

@Composable
fun ImageCropDialog(
    imagePath: String? = null,
    imageBase64: String? = null,
    title: String = "Crop Image",
    onDismiss: () -> Unit,
    onConfirm: (cropLeft: Float, cropTop: Float, cropWidth: Float, cropHeight: Float) -> Unit
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var cropLeft by remember { mutableFloatStateOf(0.1f) }
    var cropTop by remember { mutableFloatStateOf(0.1f) }
    var cropWidth by remember { mutableFloatStateOf(0.8f) }
    var cropHeight by remember { mutableFloatStateOf(0.8f) }

    val base64Bitmap = if (!imageBase64.isNullOrEmpty()) {
        remember(imageBase64) { decodeBase64ToBitmap(imageBase64) }
    } else null
    val pathBitmap = if (!imagePath.isNullOrEmpty()) rememberImageBitmap(imagePath) else null
    val displayBitmap = base64Bitmap ?: pathBitmap

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
                    text = "Drag to reposition, drag corner to resize",
                    fontFamily = OutfitFont,
                    color = TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1B1B22))
                        .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
                        .onSizeChanged { containerSize = it },
                    contentAlignment = Alignment.Center
                ) {
                    if (displayBitmap != null) {
                        Image(
                            bitmap = displayBitmap,
                            contentDescription = "Image to crop",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        if (containerSize.width > 0 && containerSize.height > 0) {
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (cropLeft * containerSize.width).roundToInt(),
                                            (cropTop * containerSize.height).roundToInt()
                                        )
                                    }
                                    .size(
                                        width = (cropWidth * containerSize.width).dp,
                                        height = (cropHeight * containerSize.height).dp
                                    )
                                    .border(2.dp, AccentGold, RoundedCornerShape(4.dp))
                                    .background(AccentGold.copy(alpha = 0.1f))
                                    .pointerInput(containerSize) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dx = dragAmount.x / containerSize.width
                                            val dy = dragAmount.y / containerSize.height
                                            cropLeft = (cropLeft + dx).coerceIn(0f, 1f - cropWidth)
                                            cropTop = (cropTop + dy).coerceIn(0f, 1f - cropHeight)
                                        }
                                    }
                                    .pointerInput(containerSize, cropLeft, cropTop) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dx = dragAmount.x / containerSize.width
                                            val dy = dragAmount.y / containerSize.height
                                            cropWidth = (cropWidth + dx).coerceIn(0.15f, 1f - cropLeft)
                                            cropHeight = (cropHeight + dy).coerceIn(0.15f, 1f - cropTop)
                                        }
                                    }
                            )
                        }
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
