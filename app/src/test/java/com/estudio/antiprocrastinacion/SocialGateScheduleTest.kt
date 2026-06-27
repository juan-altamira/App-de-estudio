package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.model.state.SocialGateDailyState
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRule
import com.estudio.antiprocrastinacion.app.socialgate.SocialGateSchedule
import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import org.junit.Test

class SocialGateScheduleTest {
    private val zoneId = ZoneId.of("UTC")

    @Test
    fun `trigger minutes start exactly at window start and spread to window end`() {
        val rule =
            SocialGateRule(
                packageName = "com.instagram.android",
                displayName = "Instagram",
                maxTriggersPerDay = 3,
                windowStartMinutes = 0,
                windowEndMinutes = 22 * 60,
            )

        val triggerMinutes = SocialGateSchedule.triggerMinutes(rule)

        assertThat(triggerMinutes).containsExactly(0, 11 * 60, 22 * 60).inOrder()
    }

    @Test
    fun `gate is not due before first scheduled slot`() {
        val rule =
            SocialGateRule(
                packageName = "com.instagram.android",
                displayName = "Instagram",
                maxTriggersPerDay = 3,
                windowStartMinutes = 8 * 60,
                windowEndMinutes = 20 * 60,
            )

        val due =
            SocialGateSchedule.isGateDueNow(
                rule = rule,
                dailyState = null,
                now = utcTime("2026-04-14T07:59:00Z"),
                zoneId = zoneId,
            )

        assertThat(due).isFalse()
    }

    @Test
    fun `solving current slot prevents repeated gate until next slot`() {
        val rule =
            SocialGateRule(
                packageName = "com.instagram.android",
                displayName = "Instagram",
                maxTriggersPerDay = 3,
                windowStartMinutes = 0,
                windowEndMinutes = 22 * 60,
            )
        val solvedCurrentSlot =
            SocialGateDailyState(
                packageName = "com.instagram.android",
                localDate = "2026-04-14",
                solvedCount = 1,
                lastSolvedAt = utcTime("2026-04-14T15:00:00Z"),
            )

        val dueAt1515 =
            SocialGateSchedule.isGateDueNow(
                rule = rule,
                dailyState = solvedCurrentSlot,
                now = utcTime("2026-04-14T15:15:00Z"),
                zoneId = zoneId,
            )
        val dueAt2200 =
            SocialGateSchedule.isGateDueNow(
                rule = rule,
                dailyState = solvedCurrentSlot,
                now = utcTime("2026-04-14T22:00:00Z"),
                zoneId = zoneId,
            )

        assertThat(dueAt1515).isFalse()
        assertThat(dueAt2200).isTrue()
    }

    @Test
    fun `normalization keeps window valid and clamps frequency to available unique minutes`() {
        val normalized =
            SocialGateSchedule.normalizeRule(
                SocialGateRule(
                    packageName = "com.instagram.android",
                    displayName = "Instagram",
                    maxTriggersPerDay = 10,
                    requiredCorrectAnswers = 99,
                    windowStartMinutes = 23 * 60 + 58,
                    windowEndMinutes = 10,
                ),
            )

        assertThat(normalized.windowStartMinutes).isEqualTo(23 * 60 + 58)
        assertThat(normalized.windowEndMinutes).isEqualTo(23 * 60 + 59)
        assertThat(normalized.maxTriggersPerDay).isEqualTo(2)
        assertThat(normalized.requiredCorrectAnswers).isEqualTo(10)
    }

    private fun utcTime(instant: String): Long = java.time.Instant.parse(instant).toEpochMilli()
}
