package com.closetos.app.data.repository

import com.closetos.app.PlatformStorage
import com.closetos.app.data.model.*
import com.closetos.app.getEpochTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NotificationCenter {

    private const val STORAGE_KEY = "closet_os_notifications.txt"

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _activeBanner = MutableStateFlow<AppNotification?>(null)
    val activeBanner: StateFlow<AppNotification?> = _activeBanner.asStateFlow()

    private val _schedules = MutableStateFlow<List<NotificationSchedule>>(emptyList())
    val schedules: StateFlow<List<NotificationSchedule>> = _schedules.asStateFlow()

    var pendingLookbookOccasion: String? = null

    fun init() {
        load()
        if (_schedules.value.isEmpty()) {
            seedDefaultSchedules()
        }
    }

    fun unreadCount(): Int = _notifications.value.count { !it.isRead }

    fun dismissBanner() {
        _activeBanner.value = null
    }

    fun markRead(notificationId: String) {
        _notifications.value = _notifications.value.map { n ->
            if (n.id == notificationId) n.copy(isRead = true) else n
        }
        save()
    }

    fun markAllRead() {
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
        save()
    }

    fun onWardrobeEvent(
        eventType: WardrobeEventType,
        garment: Garment?,
        beforeGarments: List<Garment>,
        afterGarments: List<Garment>
    ) {
        val evolution = ClosetRepository.computeWardrobeEvolution(
            eventType = eventType,
            garment = garment,
            beforeGarments = beforeGarments,
            afterGarments = afterGarments
        ) ?: return

        val (title, body, actionRoute, actionLabel) = buildEvolutionCopy(evolution)
        val notification = AppNotification(
            kind = NotificationKind.WARDROBE_EVOLUTION,
            title = title,
            body = body,
            payload = evolution,
            actionRoute = actionRoute,
            actionLabel = actionLabel
        )
        push(notification, showBanner = true)
    }

    fun tickSchedules(nowMs: Long = getEpochTimeMillis()) {
        val due = _schedules.value.filter { schedule ->
            schedule.enabled && isScheduleDue(schedule, nowMs)
        }
        due.forEach { schedule ->
            fireSchedule(schedule, nowMs)
        }
    }

    private fun fireSchedule(schedule: NotificationSchedule, nowMs: Long) {
        val notification = when (schedule.kind) {
            NotificationKind.SCHEDULED_OOTD -> buildOotdNotification()
            NotificationKind.SCHEDULED_DIGEST -> buildWeeklyDigestNotification()
            NotificationKind.SCHEDULED_TRIP -> null
            NotificationKind.WARDROBE_EVOLUTION -> null
        } ?: return

        push(notification, showBanner = schedule.frequency != ScheduleFrequency.DAILY)
        _schedules.value = _schedules.value.map { s ->
            if (s.id == schedule.id) s.copy(lastFiredMs = nowMs) else s
        }
        save()
    }

    private fun buildOotdNotification(): AppNotification {
        val outfits = ClosetRepository.getRecommendedOutfits(limit = 1)
        val topPick = outfits.firstOrNull()
        val body = topPick?.let {
            "${it.name} — ${(it.overallScore * 100).toInt()}% match for today."
        } ?: "Your nightly cache is ready. Open OOTD to see today's pick."

        return AppNotification(
            kind = NotificationKind.SCHEDULED_OOTD,
            title = "☀️ Your outfit of the day is ready",
            body = body,
            actionRoute = "ootd",
            actionLabel = "Wear it →"
        )
    }

    private fun buildWeeklyDigestNotification(): AppNotification {
        val garmentCount = ClosetRepository.garments.value.size
        val outfitCount = ClosetRepository.lookbookOutfits.value.size
        val favorites = ClosetRepository.getFavoriteOutfits(limit = 99).size

        return AppNotification(
            kind = NotificationKind.SCHEDULED_DIGEST,
            title = "📊 Weekly wardrobe digest",
            body = "$garmentCount pieces · $outfitCount looks · $favorites saved favorites. Your closet evolved this week.",
            actionRoute = "builder",
            actionLabel = "Open lookbook →"
        )
    }

    private fun buildEvolutionCopy(
        evolution: WardrobeEvolutionPayload
    ): EvolutionCopy {
        return when (evolution.eventType) {
            WardrobeEventType.GARMENT_ADDED,
            WardrobeEventType.GARMENT_PURCHASED -> {
                val verb = if (evolution.eventType == WardrobeEventType.GARMENT_PURCHASED) "purchase" else "addition"
                EvolutionCopy(
                    title = "🎉 Your wardrobe just evolved!",
                    body = buildUnlockBody(evolution, verb),
                    actionRoute = "builder",
                    actionLabel = "See them →"
                )
            }
            WardrobeEventType.GARMENT_REMOVED -> EvolutionCopy(
                title = "🎉 Your wardrobe just evolved!",
                body = buildStreamlineBody(evolution, "removed"),
                actionRoute = "builder",
                actionLabel = "See what's left →"
            )
            WardrobeEventType.GARMENT_SOLD -> EvolutionCopy(
                title = "🎉 Your wardrobe just evolved!",
                body = buildStreamlineBody(evolution, "sold"),
                actionRoute = "builder",
                actionLabel = "See refreshed looks →"
            )
        }
    }

    private fun buildUnlockBody(evolution: WardrobeEvolutionPayload, sourceVerb: String): String {
        val garmentLine = evolution.garmentLabel.takeIf { it.isNotBlank() }?.let { "This $it unlocked" }
            ?: "Your latest $sourceVerb unlocked"
        val totalLine = if (evolution.totalNewOutfits > 0) {
            "${evolution.totalNewOutfits} brand-new outfits"
        } else {
            "fresh outfit combinations"
        }
        val breakdown = evolution.occasionUnlocks
            .filter { it.count > 0 }
            .joinToString("\n") { "${it.count} ${it.label.lowercase()} looks" }
        return listOfNotNull(garmentLine, totalLine, breakdown.takeIf { it.isNotBlank() })
            .joinToString("\n")
    }

    private fun buildStreamlineBody(evolution: WardrobeEvolutionPayload, verb: String): String {
        val garmentLine = evolution.garmentLabel.takeIf { it.isNotBlank() }?.let { "Your $it was $verb." }
            ?: "A piece was $verb from your closet."
        val spotlight = if (evolution.totalNewOutfits > 0) {
            "${evolution.totalNewOutfits} refreshed looks spotlighted from what's left."
        } else {
            "Your remaining pieces were re-ranked for you."
        }
        return "$garmentLine\n$spotlight"
    }

    private fun push(notification: AppNotification, showBanner: Boolean) {
        _notifications.value = listOf(notification) + _notifications.value.take(49)
        if (showBanner) {
            _activeBanner.value = notification
        }
        save()
    }

    private fun isScheduleDue(schedule: NotificationSchedule, nowMs: Long): Boolean {
        val cal = epochToLocalParts(nowMs)
        if (schedule.dayOfWeek != null && cal.dayOfWeek != schedule.dayOfWeek) return false
        if (cal.hour < schedule.hourOfDay) return false
        if (cal.hour == schedule.hourOfDay && cal.minute < schedule.minuteOfDay) return false

        val lastFired = schedule.lastFiredMs ?: return true
        return when (schedule.frequency) {
            ScheduleFrequency.ONCE -> false
            ScheduleFrequency.DAILY -> !isSameLocalDay(lastFired, nowMs)
            ScheduleFrequency.WEEKLY -> nowMs - lastFired >= 6 * 24 * 60 * 60 * 1000L
        }
    }

    private data class LocalParts(val dayOfWeek: Int, val hour: Int, val minute: Int)

    private fun epochToLocalParts(epochMs: Long): LocalParts {
        val totalMinutes = epochMs / 60_000L
        val minute = (totalMinutes % 60).toInt()
        val totalHours = totalMinutes / 60
        val hour = (totalHours % 24).toInt()
        val totalDays = totalHours / 24
        val dayOfWeek = ((totalDays + 4) % 7).toInt() // 1970-01-01 was Thursday
        return LocalParts(dayOfWeek = dayOfWeek, hour = hour, minute = minute)
    }

    private fun isSameLocalDay(aMs: Long, bMs: Long): Boolean {
        return aMs / 86_400_000L == bMs / 86_400_000L
    }

    private fun seedDefaultSchedules() {
        _schedules.value = listOf(
            NotificationSchedule(
                kind = NotificationKind.SCHEDULED_OOTD,
                title = "Morning OOTD",
                bodyTemplate = "Your outfit of the day is ready.",
                hourOfDay = 8,
                frequency = ScheduleFrequency.DAILY
            ),
            NotificationSchedule(
                kind = NotificationKind.SCHEDULED_DIGEST,
                title = "Weekly Wardrobe Digest",
                bodyTemplate = "Your weekly closet recap is ready.",
                hourOfDay = 18,
                dayOfWeek = 0,
                frequency = ScheduleFrequency.WEEKLY
            )
        )
        save()
    }

    private fun save() {
        try {
            PlatformStorage.saveString(STORAGE_KEY, serialize())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun load() {
        val raw = PlatformStorage.loadString(STORAGE_KEY) ?: return
        try {
            val parsed = deserialize(raw)
            _notifications.value = parsed.notifications
            _schedules.value = parsed.schedules
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun serialize(): String = buildString {
        _notifications.value.forEach { n ->
            append("[NOTIF]")
            append(n.id).append('|')
            append(n.kind.name).append('|')
            append(n.title).append('|')
            append(n.body.replace('|', '/')).append('|')
            append(n.createdAtMs).append('|')
            append(n.isRead).append('|')
            append(n.actionRoute.orEmpty()).append('|')
            append(n.actionLabel.orEmpty()).append('|')
            n.payload?.let { p ->
                append(p.eventType.name).append('|')
                append(p.garmentId.orEmpty()).append('|')
                append(p.garmentLabel.replace('|', '/')).append('|')
                append(p.totalNewOutfits).append('|')
                append(p.occasionUnlocks.joinToString(";") { "${it.label}:${it.count}" }).append('|')
                append(p.spotlightOutfitIds.joinToString(","))
            } ?: run {
                repeat(6) { append('|') }
            }
            append('\n')
        }
        _schedules.value.forEach { s ->
            append("[SCHED]")
            append(s.id).append('|')
            append(s.kind.name).append('|')
            append(s.title).append('|')
            append(s.bodyTemplate.replace('|', '/')).append('|')
            append(s.hourOfDay).append('|')
            append(s.minuteOfDay).append('|')
            append(s.dayOfWeek?.toString().orEmpty()).append('|')
            append(s.frequency.name).append('|')
            append(s.enabled).append('|')
            append(s.lastFiredMs?.toString().orEmpty())
            append('\n')
        }
    }

    private data class ParsedNotifications(
        val notifications: List<AppNotification>,
        val schedules: List<NotificationSchedule>
    )

    private fun deserialize(raw: String): ParsedNotifications {
        val notifications = mutableListOf<AppNotification>()
        val schedules = mutableListOf<NotificationSchedule>()
        raw.lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = line.substringAfter(']').split('|')
            when {
                line.startsWith("[NOTIF]") && parts.size >= 8 -> {
                    val payload = if (parts.size >= 11 && parts[8].isNotBlank()) {
                        val occasionUnlocks = if (parts.size < 13 || parts[12].isBlank()) {
                            emptyList()
                        } else {
                            parts[12].split(";").mapNotNull { entry ->
                                val split = entry.split(':')
                                if (split.size == 2) OccasionUnlock(split[0], split[1].toIntOrNull() ?: 0) else null
                            }
                        }
                        WardrobeEvolutionPayload(
                            eventType = WardrobeEventType.valueOf(parts[8]),
                            garmentId = parts[9].ifBlank { null },
                            garmentLabel = parts.getOrElse(10) { "" },
                            totalNewOutfits = parts.getOrElse(11) { "0" }.toIntOrNull() ?: 0,
                            occasionUnlocks = occasionUnlocks,
                            spotlightOutfitIds = parts.getOrElse(13) { "" }
                                .takeIf { it.isNotBlank() }
                                ?.split(",")
                                ?: emptyList()
                        )
                    } else null
                    notifications.add(
                        AppNotification(
                            id = parts[0],
                            kind = NotificationKind.valueOf(parts[1]),
                            title = parts[2],
                            body = parts[3],
                            createdAtMs = parts[4].toLongOrNull() ?: getEpochTimeMillis(),
                            isRead = parts[5].toBoolean(),
                            actionRoute = parts[6].ifBlank { null },
                            actionLabel = parts[7].ifBlank { null },
                            payload = payload
                        )
                    )
                }
                line.startsWith("[SCHED]") && parts.size >= 10 -> {
                    schedules.add(
                        NotificationSchedule(
                            id = parts[0],
                            kind = NotificationKind.valueOf(parts[1]),
                            title = parts[2],
                            bodyTemplate = parts[3],
                            hourOfDay = parts[4].toIntOrNull() ?: 8,
                            minuteOfDay = parts[5].toIntOrNull() ?: 0,
                            dayOfWeek = parts[6].toIntOrNull(),
                            frequency = ScheduleFrequency.valueOf(parts[7]),
                            enabled = parts[8].toBoolean(),
                            lastFiredMs = parts[9].toLongOrNull()
                        )
                    )
                }
            }
        }
        return ParsedNotifications(notifications, schedules)
    }

    private data class EvolutionCopy(
        val title: String,
        val body: String,
        val actionRoute: String?,
        val actionLabel: String?
    )
}
