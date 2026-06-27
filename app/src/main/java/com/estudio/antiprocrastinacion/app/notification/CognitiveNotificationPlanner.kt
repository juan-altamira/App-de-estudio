package com.estudio.antiprocrastinacion.app.notification

import com.estudio.antiprocrastinacion.app.model.state.AppSettings
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt

data class CognitiveNotificationPlan(
    val slotIndex: Int,
    val minuteOfDay: Int,
    val initialDelayMs: Long,
)

class CognitiveNotificationPlanner(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun buildPlans(
        settings: AppSettings,
        nowEpochMs: Long,
    ): List<CognitiveNotificationPlan> {
        if (!settings.cognitiveNotificationsEnabled) return emptyList()

        val normalizedCount = settings.cognitiveNotificationsPerDay.coerceIn(1, MAX_COGNITIVE_NOTIFICATIONS_PER_DAY)
        val startMinute = settings.cognitiveNotificationWindowStartMinutes.coerceIn(0, MAX_MINUTE_OF_DAY)
        val requestedEndMinute = settings.cognitiveNotificationWindowEndMinutes.coerceIn(0, MAX_MINUTE_OF_DAY)
        val endMinute =
            if (requestedEndMinute <= startMinute) {
                (startMinute + 60).coerceAtMost(MAX_MINUTE_OF_DAY)
            } else {
                requestedEndMinute
            }
        val slotMinutes =
            if (normalizedCount == 1) {
                listOf(startMinute)
            } else {
                val span = endMinute - startMinute
                (0 until normalizedCount).map { index ->
                    startMinute + ((index.toDouble() * span.toDouble()) / (normalizedCount - 1)).roundToInt()
                }.distinct()
            }

        val now = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId)
        return slotMinutes.mapIndexed { index, minuteOfDay ->
            CognitiveNotificationPlan(
                slotIndex = index,
                minuteOfDay = minuteOfDay,
                initialDelayMs = computeInitialDelayMs(now, minuteOfDay),
            )
        }
    }

    private fun computeInitialDelayMs(
        now: ZonedDateTime,
        minuteOfDay: Int,
    ): Long {
        val targetHour = minuteOfDay / 60
        val targetMinute = minuteOfDay % 60
        var target =
            now
                .withHour(targetHour)
                .withMinute(targetMinute)
                .withSecond(0)
                .withNano(0)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return java.time.Duration.between(now, target).toMillis()
    }

    companion object {
        const val MAX_COGNITIVE_NOTIFICATIONS_PER_DAY = 8
        private const val MAX_MINUTE_OF_DAY = (24 * 60) - 1
    }
}
