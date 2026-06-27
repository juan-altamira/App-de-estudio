package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.model.state.AppSettings
import com.estudio.antiprocrastinacion.app.notification.CognitiveNotificationPlanner
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CognitiveNotificationPlannerTest {
    private val planner = CognitiveNotificationPlanner(zoneId = ZoneId.of("UTC"))

    @Test
    fun `build plans returns one slot at start time when notifications per day is one`() {
        val plans =
            planner.buildPlans(
                AppSettings(
                    cognitiveNotificationsEnabled = true,
                    cognitiveNotificationsPerDay = 1,
                    cognitiveNotificationWindowStartMinutes = 18 * 60,
                    cognitiveNotificationWindowEndMinutes = 21 * 60,
                ),
                nowEpochMs = ZonedDateTime.of(2026, 4, 14, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli(),
            )

        assertThat(plans).hasSize(1)
        assertThat(plans.first().minuteOfDay).isEqualTo(18 * 60)
    }

    @Test
    fun `build plans distributes slots across the configured window`() {
        val plans =
            planner.buildPlans(
                AppSettings(
                    cognitiveNotificationsEnabled = true,
                    cognitiveNotificationsPerDay = 3,
                    cognitiveNotificationWindowStartMinutes = 9 * 60,
                    cognitiveNotificationWindowEndMinutes = 21 * 60,
                ),
                nowEpochMs = ZonedDateTime.of(2026, 4, 14, 8, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli(),
            )

        assertThat(plans.map { it.minuteOfDay }).containsExactly(9 * 60, 15 * 60, 21 * 60).inOrder()
    }

    @Test
    fun `build plans returns empty when notifications are disabled`() {
        val plans =
            planner.buildPlans(
                AppSettings(cognitiveNotificationsEnabled = false),
                nowEpochMs = ZonedDateTime.of(2026, 4, 14, 8, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli(),
            )

        assertThat(plans).isEmpty()
    }
}
