package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.ui.review.UpcomingReviewSource
import com.estudio.antiprocrastinacion.app.ui.review.buildUpcomingReviewWeek
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class UpcomingReviewsTest {
    private val zone = ZoneId.of("UTC")
    private val now = LocalDateTime.of(2026, 6, 9, 10, 0).atZone(zone).toInstant().toEpochMilli()
    private val oneHour = 3_600_000L
    private val oneDay = 24 * oneHour

    private fun source(
        id: String,
        nextReviewAt: Long?,
        stage: Int,
    ) = UpcomingReviewSource(
        itemId = id,
        stem = "Pregunta $id",
        unitTitle = "Unidad",
        courseTitle = "Curso",
        nextReviewAt = nextReviewAt,
        stage = stage,
    )

    @Test
    fun `groups upcoming reviews by day with due-now and later buckets`() {
        val sources =
            listOf(
                source("new", nextReviewAt = null, stage = 0),
                source("overdue", nextReviewAt = now - oneHour, stage = 2),
                source("today-later", nextReviewAt = now + 5 * oneHour, stage = 1),
                source("tomorrow", nextReviewAt = now + oneDay, stage = 1),
                source("in-three", nextReviewAt = now + 3 * oneDay, stage = 3),
                source("later", nextReviewAt = now + 10 * oneDay, stage = 4),
            )

        val state = buildUpcomingReviewWeek(sources, now, zone)

        assertThat(state.isLoading).isFalse()
        // New (stage 0) + overdue (nextReviewAt <= now) are available now.
        assertThat(state.dueNowCount).isEqualTo(2)
        // Three scheduled days within the week: today (later), tomorrow, in three days.
        assertThat(state.days).hasSize(3)
        assertThat(state.days[0].dayLabel).isEqualTo("Hoy")
        assertThat(state.days[1].dayLabel).isEqualTo("Mañana")
        assertThat(state.days[2].dayLabel).endsWith("12")
        assertThat(state.days[2].dateLabel).isEqualTo("12 jun")
        assertThat(state.days[0].isToday).isTrue()
        assertThat(state.days[0].cards.single().timeLabel).isEqualTo("15:00")
        // The 10-days-out card is beyond the 7-day window.
        assertThat(state.laterCount).isEqualTo(1)
        assertThat(state.totalScheduled).isEqualTo(4)
    }

    @Test
    fun `empty when there is nothing scheduled or due`() {
        val state = buildUpcomingReviewWeek(emptyList(), now, zone)

        assertThat(state.isEmpty).isTrue()
        assertThat(state.dueNowCount).isEqualTo(0)
        assertThat(state.days).isEmpty()
    }
}
