package com.closetos.app.data.model

import com.closetos.app.getEpochTimeMillis

enum class WardrobeEventType {
    GARMENT_ADDED,
    GARMENT_REMOVED,
    GARMENT_PURCHASED,
    GARMENT_SOLD
}

enum class NotificationKind {
    WARDROBE_EVOLUTION,
    SCHEDULED_OOTD,
    SCHEDULED_DIGEST,
    SCHEDULED_TRIP
}

enum class ScheduleFrequency {
    ONCE,
    DAILY,
    WEEKLY
}

data class OccasionUnlock(
    val label: String,
    val count: Int
)

data class WardrobeEvolutionPayload(
    val eventType: WardrobeEventType,
    val garmentId: String?,
    val garmentLabel: String,
    val totalNewOutfits: Int,
    val occasionUnlocks: List<OccasionUnlock>,
    val spotlightOutfitIds: List<String> = emptyList()
)

data class AppNotification(
    val id: String = generateUUID(),
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val payload: WardrobeEvolutionPayload? = null,
    val createdAtMs: Long = getEpochTimeMillis(),
    val isRead: Boolean = false,
    val actionRoute: String? = null,
    val actionLabel: String? = null
)

data class NotificationSchedule(
    val id: String = generateUUID(),
    val kind: NotificationKind,
    val title: String,
    val bodyTemplate: String,
    val hourOfDay: Int,
    val minuteOfDay: Int = 0,
    val dayOfWeek: Int? = null, // 0 = Sunday … 6 = Saturday; null = every day
    val frequency: ScheduleFrequency,
    val enabled: Boolean = true,
    val lastFiredMs: Long? = null
)
