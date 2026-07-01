package com.closetos.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closetos.app.data.model.AppNotification
import com.closetos.app.data.model.NotificationKind
import com.closetos.app.data.model.WardrobeEvolutionPayload
import com.closetos.app.data.repository.NotificationCenter
import com.closetos.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun WardrobeEvolutionBanner(
    notification: AppNotification?,
    onDismiss: () -> Unit,
    onAction: (AppNotification) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = notification != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        notification?.let { item ->
            LaunchedEffect(item.id) {
                delay(12_000)
                onDismiss()
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .shadow(12.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(CharcoalSurface)
                    .border(1.dp, GoldBorder, RoundedCornerShape(20.dp))
                    .clickable { onAction(item) }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            fontFamily = PlayfairFont,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        when (item.kind) {
                            NotificationKind.WARDROBE_EVOLUTION -> {
                                item.payload?.let { EvolutionBannerBody(it) }
                                    ?: Text(item.body, fontFamily = OutfitFont, fontSize = 13.sp, color = TextLight, lineHeight = 18.sp)
                            }
                            else -> {
                                Text(
                                    text = item.body,
                                    fontFamily = OutfitFont,
                                    fontSize = 13.sp,
                                    color = TextLight,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        item.actionLabel?.let { label ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = label,
                                fontFamily = OutfitFont,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EvolutionBannerBody(payload: WardrobeEvolutionPayload) {
    if (payload.garmentLabel.isNotBlank()) {
        val prefix = when (payload.eventType.name) {
            "GARMENT_SOLD", "GARMENT_REMOVED" -> "Your ${payload.garmentLabel}"
            else -> "This ${payload.garmentLabel}"
        }
        val suffix = when (payload.eventType.name) {
            "GARMENT_SOLD" -> " was sold"
            "GARMENT_REMOVED" -> " was removed"
            else -> " unlocked"
        }
        Text(
            text = "$prefix$suffix",
            fontFamily = OutfitFont,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextLight
        )
        Spacer(modifier = Modifier.height(4.dp))
    }

    if (payload.eventType.name == "GARMENT_ADDED" || payload.eventType.name == "GARMENT_PURCHASED") {
        Text(
            text = "${payload.totalNewOutfits} brand-new outfits",
            fontFamily = PlayfairFont,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextLight
        )
    } else if (payload.totalNewOutfits > 0) {
        Text(
            text = "${payload.totalNewOutfits} refreshed looks spotlighted",
            fontFamily = OutfitFont,
            fontSize = 14.sp,
            color = TextLight
        )
    }

    payload.occasionUnlocks.filter { it.count > 0 }.forEach { unlock ->
        Text(
            text = "${unlock.count} ${unlock.label.lowercase()}",
            fontFamily = OutfitFont,
            fontSize = 13.sp,
            color = TextMuted,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun NotificationBell(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        IconButton(onClick = onClick) {
            Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = AccentGold)
        }
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentGold),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = ObsidianBg
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationInboxSheet(
    visible: Boolean,
    notifications: List<AppNotification>,
    onDismiss: () -> Unit,
    onNotificationClick: (AppNotification) -> Unit,
    onMarkAllRead: () -> Unit
) {
    if (!visible) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CharcoalSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AccentGold.copy(alpha = 0.5f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Notifications",
                    fontFamily = PlayfairFont,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGold
                )
                if (notifications.any { !it.isRead }) {
                    TextButton(onClick = onMarkAllRead) {
                        Text("Mark all read", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (notifications.isEmpty()) {
                Text(
                    text = "Your wardrobe evolution moments will appear here.",
                    fontFamily = OutfitFont,
                    fontSize = 14.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(notifications, key = { it.id }) { notification ->
                        NotificationInboxRow(
                            notification = notification,
                            onClick = { onNotificationClick(notification) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationInboxRow(
    notification: AppNotification,
    onClick: () -> Unit
) {
    val bg = if (notification.isRead) GlassOverlay else AccentGold.copy(alpha = 0.08f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = notification.title,
                fontFamily = OutfitFont,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (notification.isRead) TextMuted else TextLight
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = notification.body.lines().take(3).joinToString(" · "),
                fontFamily = OutfitFont,
                fontSize = 12.sp,
                color = TextMuted,
                maxLines = 2
            )
        }
    }
}

@Composable
fun NotificationScheduleHost() {
    LaunchedEffect(Unit) {
        while (true) {
            NotificationCenter.tickSchedules()
            delay(60_000)
        }
    }
}
