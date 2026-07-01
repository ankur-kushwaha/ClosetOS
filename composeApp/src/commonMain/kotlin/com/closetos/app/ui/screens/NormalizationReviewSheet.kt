package com.closetos.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closetos.app.data.model.IngestionItem
import com.closetos.app.decodeBase64ToBitmap
import com.closetos.app.ui.components.ElegantButton
import com.closetos.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NormalizationReviewBottomSheet(
    item: IngestionItem,
    onAccept: (useNormalized: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showNormalized by remember { mutableStateOf(true) }

    val cropBase64 = item.cropBase64.orEmpty()
    val normalizedBase64 = item.normalizedBase64.orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianBg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AccentGold.copy(alpha = 0.6f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Review Normalization",
                    fontFamily = PlayfairFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = TextLight
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            Text(
                text = "Compare the AI-normalized flat-lay with your original crop. Accept to continue, or reject to keep the original crop.",
                fontFamily = OutfitFont,
                fontSize = 13.sp,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CharcoalSurface.copy(alpha = 0.8f))
                    .border(0.5.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (!showNormalized) AccentGold else Color.Transparent)
                        .clickable { showNormalized = false }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Original Crop",
                        color = if (!showNormalized) ObsidianBg else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = OutfitFont
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (showNormalized) AccentGold else Color.Transparent)
                        .clickable { showNormalized = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Normalized",
                        color = if (showNormalized) ObsidianBg else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = OutfitFont
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1B1B22), Color(0xFF121215))))
                    .border(0.5.dp, GlassBorder, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                val displayBase64 = if (showNormalized) normalizedBase64 else cropBase64
                val bitmap = if (displayBase64.isNotEmpty()) {
                    remember(displayBase64) { decodeBase64ToBitmap(displayBase64) }
                } else null
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = if (showNormalized) "Normalized" else "Original crop",
                        modifier = Modifier
                            .fillMaxHeight(0.9f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ElegantButton(
                    text = "Reject Normalization",
                    onClick = { onAccept(false) },
                    isSecondary = true,
                    modifier = Modifier.weight(1f)
                )
                ElegantButton(
                    text = "Accept Normalized",
                    onClick = { onAccept(true) },
                    modifier = Modifier.weight(1.2f)
                )
            }
        }
    }
}
