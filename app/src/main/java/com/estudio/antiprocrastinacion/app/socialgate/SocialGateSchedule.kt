package com.estudio.antiprocrastinacion.app.socialgate

import com.estudio.antiprocrastinacion.app.model.state.SocialGateDailyState
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRule
import java.time.Instant
import java.time.ZoneId
import kotlin.math.min
import kotlin.math.roundToInt

object SocialGateSchedule {
    const val MAX_TRIGGERS_PER_DAY = 10
    const val MAX_REQUIRED_CORRECT_ANSWERS = 10
    const val MAX_SELECTABLE_MINUTE = 23 * 60 + 59
    const val DEFAULT_WINDOW_START_MINUTES = 0
    const val DEFAULT_WINDOW_END_MINUTES = 22 * 60

    data class ActiveSlot(
        val index: Int,
        val minuteOfDay: Int,
        val startsAtMillis: Long,
    )

    fun normalizeRule(rule: SocialGateRule): SocialGateRule {
        val safeStart = rule.windowStartMinutes.coerceIn(0, MAX_SELECTABLE_MINUTE)
        val rawEnd = rule.windowEndMinutes.coerceIn(0, MAX_SELECTABLE_MINUTE)
        val safeEnd =
            when {
                safeStart >= MAX_SELECTABLE_MINUTE -> MAX_SELECTABLE_MINUTE
                rawEnd <= safeStart -> min(safeStart + 60, MAX_SELECTABLE_MINUTE)
                else -> rawEnd
            }
        val maxCountForWindow = ((safeEnd - safeStart) + 1).coerceAtLeast(1)
        val safeCount = rule.maxTriggersPerDay.coerceIn(1, min(MAX_TRIGGERS_PER_DAY, maxCountForWindow))

        return rule.copy(
            enabled = true,
            maxTriggersPerDay = safeCount,
            requiredCorrectAnswers = rule.requiredCorrectAnswers.coerceIn(1, MAX_REQUIRED_CORRECT_ANSWERS),
            windowStartMinutes = safeStart,
            windowEndMinutes = safeEnd,
        )
    }

    fun triggerMinutes(rule: SocialGateRule): List<Int> {
        val normalized = normalizeRule(rule)
        if (normalized.maxTriggersPerDay == 1) {
            return listOf(normalized.windowStartMinutes)
        }

        val windowDuration = normalized.windowEndMinutes - normalized.windowStartMinutes
        return List(normalized.maxTriggersPerDay) { index ->
            normalized.windowStartMinutes +
                (windowDuration.toDouble() * index.toDouble() / (normalized.maxTriggersPerDay - 1).toDouble()).roundToInt()
        }
    }

    fun activeSlot(
        rule: SocialGateRule,
        now: Long,
        zoneId: ZoneId,
    ): ActiveSlot? {
        val normalized = normalizeRule(rule)
        val zonedNow = Instant.ofEpochMilli(now).atZone(zoneId)
        val nowMinutes = zonedNow.hour * 60 + zonedNow.minute
        if (nowMinutes < normalized.windowStartMinutes || nowMinutes > normalized.windowEndMinutes) {
            return null
        }

        val triggerMinutes = triggerMinutes(normalized)
        val activeIndex = triggerMinutes.indexOfLast { it <= nowMinutes }
        if (activeIndex < 0) return null

        val slotMinutes = triggerMinutes[activeIndex]
        val slotStart =
            zonedNow.toLocalDate()
                .atTime(slotMinutes / 60, slotMinutes % 60)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()

        return ActiveSlot(
            index = activeIndex,
            minuteOfDay = slotMinutes,
            startsAtMillis = slotStart,
        )
    }

    fun isGateDueNow(
        rule: SocialGateRule,
        dailyState: SocialGateDailyState?,
        now: Long,
        zoneId: ZoneId,
    ): Boolean {
        val normalized = normalizeRule(rule)
        if ((dailyState?.solvedCount ?: 0) >= normalized.maxTriggersPerDay) {
            return false
        }

        val activeSlot = activeSlot(normalized, now, zoneId) ?: return false
        val lastSolvedAt = dailyState?.lastSolvedAt ?: Long.MIN_VALUE
        return lastSolvedAt < activeSlot.startsAtMillis
    }

    fun nextTriggerMinute(
        rule: SocialGateRule,
        now: Long,
        zoneId: ZoneId,
    ): Int? {
        val normalized = normalizeRule(rule)
        val zonedNow = Instant.ofEpochMilli(now).atZone(zoneId)
        val nowMinutes = zonedNow.hour * 60 + zonedNow.minute
        return triggerMinutes(normalized).firstOrNull { it > nowMinutes }
    }
}

fun Int.toClockLabel(): String = "%02d:%02d".format((this / 60).coerceIn(0, 23), (this % 60).coerceIn(0, 59))
