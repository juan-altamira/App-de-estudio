package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.domain.session.ItemStateUpdater
import com.estudio.antiprocrastinacion.app.model.state.ItemReviewOutcome
import com.estudio.antiprocrastinacion.app.model.state.ItemState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ItemStateUpdaterTest {
    private val updater = ItemStateUpdater()
    private val now = 1_000_000L

    @Test
    fun `stage zero clean correct consolidates into one day`() {
        val shown = updater.recordRealExposure(itemId = "item-1", current = null)
        val updated = updater.applyCleanCorrect(itemId = "item-1", current = shown, now = now)

        assertThat(updated.stage).isEqualTo(1)
        assertThat(updated.nextReviewAt).isEqualTo(now + DAY_MS)
        assertThat(updated.timesShown).isEqualTo(1)
        assertThat(updated.timesCorrectFirstTry).isEqualTo(1)
        assertThat(updated.lastOutcome).isEqualTo(ItemReviewOutcome.CLEAN_CORRECT)
    }

    @Test
    fun `clean correct on time promotes one stage`() {
        val current = ItemState(itemId = "item-2", stage = 3, nextReviewAt = now - DAY_MS)

        val updated = updater.applyCleanCorrect(itemId = current.itemId, current = current, now = now)

        assertThat(updated.stage).isEqualTo(4)
        assertThat(updated.nextReviewAt).isEqualTo(now + 7 * DAY_MS)
        assertThat(updated.lastOutcome).isEqualTo(ItemReviewOutcome.CLEAN_CORRECT)
    }

    @Test
    fun `clean correct with moderate lateness keeps same stage`() {
        val current = ItemState(itemId = "item-3", stage = 4, nextReviewAt = now - (8 * DAY_MS))

        val updated = updater.applyCleanCorrect(itemId = current.itemId, current = current, now = now)

        assertThat(updated.stage).isEqualTo(4)
        assertThat(updated.nextReviewAt).isEqualTo(now + 7 * DAY_MS)
        assertThat(updated.lastOutcome).isEqualTo(ItemReviewOutcome.CLEAN_CORRECT_LATE)
    }

    @Test
    fun `clean correct with very late answer drops one stage but not below one`() {
        val current = ItemState(itemId = "item-4", stage = 2, nextReviewAt = now - (7 * DAY_MS))

        val updated = updater.applyCleanCorrect(itemId = current.itemId, current = current, now = now)

        assertThat(updated.stage).isEqualTo(1)
        assertThat(updated.nextReviewAt).isEqualTo(now + DAY_MS)
        assertThat(updated.lastOutcome).isEqualTo(ItemReviewOutcome.CLEAN_CORRECT_VERY_LATE)
    }

    @Test
    fun `recovered after one failure drops three stages without recounting the failure`() {
        // timesFailed ya viene en 1 (lo contó applyFailureMark al fallar); la recuperación no lo vuelve a contar.
        val current = ItemState(itemId = "item-5", stage = 6, nextReviewAt = now + DAY_MS, timesFailed = 1)

        val updated = updater.applyRecoveredAfterFailure(itemId = current.itemId, current = current, failureCount = 1, now = now)

        assertThat(updated.stage).isEqualTo(3)
        assertThat(updated.nextReviewAt).isEqualTo(now + 4 * DAY_MS)
        assertThat(updated.timesFailed).isEqualTo(1)
        assertThat(updated.timesRecoveredAfterFailure).isEqualTo(1)
        assertThat(updated.lastOutcome).isEqualTo(ItemReviewOutcome.RECOVERED_AFTER_ONE_FAILURE)
    }

    @Test
    fun `recovered after multiple failures drops four stages without recounting failures`() {
        val current = ItemState(itemId = "item-6", stage = 5, nextReviewAt = now + DAY_MS, timesFailed = 3)

        val updated = updater.applyRecoveredAfterFailure(itemId = current.itemId, current = current, failureCount = 3, now = now)

        assertThat(updated.stage).isEqualTo(1)
        assertThat(updated.nextReviewAt).isEqualTo(now + DAY_MS)
        assertThat(updated.timesFailed).isEqualTo(3)
        assertThat(updated.lastOutcome).isEqualTo(ItemReviewOutcome.RECOVERED_AFTER_MULTIPLE_FAILURES)
    }

    @Test
    fun `stage zero recovery stays unconsolidated`() {
        val current = ItemState(itemId = "item-7", stage = 0)

        val updated = updater.applyRecoveredAfterFailure(itemId = current.itemId, current = current, failureCount = 2, now = now)

        assertThat(updated.stage).isEqualTo(0)
        assertThat(updated.nextReviewAt).isEqualTo(now)
    }

    @Test
    fun `failure mark counts the failure, keeps the card due, and does not drop the stage`() {
        val current = updater.recordRealExposure(itemId = "item-8", current = ItemState(itemId = "item-8", stage = 4, nextReviewAt = now + DAY_MS))

        val updated = updater.applyFailureMark(itemId = current.itemId, current = current, now = now)

        assertThat(updated.stage).isEqualTo(4) // no baja la etapa al fallar; el descuento se aplica al recuperar
        assertThat(updated.nextReviewAt).isEqualTo(now) // sigue "due" hasta corregirla
        assertThat(updated.timesShown).isEqualTo(1)
        assertThat(updated.timesFailed).isEqualTo(1)
        assertThat(updated.lastOutcome).isEqualTo(ItemReviewOutcome.FAILED_AWAITING_RECOVERY)
    }

    @Test
    fun `failure mark on stage zero stays immediate`() {
        val updated = updater.applyFailureMark(itemId = "item-z", current = ItemState(itemId = "item-z", stage = 0), now = now)

        assertThat(updated.stage).isEqualTo(0)
        assertThat(updated.nextReviewAt).isNull()
        assertThat(updated.timesFailed).isEqualTo(1)
        assertThat(updated.lastOutcome).isEqualTo(ItemReviewOutcome.FAILED_AWAITING_RECOVERY)
    }

    @Test
    fun `correct answer without failures is a clean correct`() {
        val current = ItemState(itemId = "item-c", stage = 3, nextReviewAt = now - DAY_MS)

        val updated = updater.applyCorrectAnswer(itemId = current.itemId, current = current, inSessionFailureCount = 0, now = now)

        assertThat(updated.stage).isEqualTo(4)
        assertThat(updated.lastOutcome).isEqualTo(ItemReviewOutcome.CLEAN_CORRECT)
    }

    @Test
    fun `correct answer recovers when a failure was marked in a previous dead session`() {
        // Punto 5: la sesión murió tras el fallo (in-session = 0), pero la marca persistida obliga a tratar
        // la próxima correcta como recuperación, en vez de un acierto limpio que borraría el castigo.
        val failed = updater.applyFailureMark(itemId = "item-d", current = ItemState(itemId = "item-d", stage = 6, nextReviewAt = now + DAY_MS), now = now)

        val recovered = updater.applyCorrectAnswer(itemId = "item-d", current = failed, inSessionFailureCount = 0, now = now)

        assertThat(recovered.stage).isEqualTo(3)
        assertThat(recovered.timesFailed).isEqualTo(1)
        assertThat(recovered.timesRecoveredAfterFailure).isEqualTo(1)
        assertThat(recovered.lastOutcome).isEqualTo(ItemReviewOutcome.RECOVERED_AFTER_ONE_FAILURE)
    }

    @Test
    fun `correct answer uses the larger of in-session and marker failure counts`() {
        val failed = updater.applyFailureMark(itemId = "item-e", current = ItemState(itemId = "item-e", stage = 5), now = now)

        val recovered = updater.applyCorrectAnswer(itemId = "item-e", current = failed, inSessionFailureCount = 2, now = now)

        assertThat(recovered.stage).isEqualTo(1)
        assertThat(recovered.lastOutcome).isEqualTo(ItemReviewOutcome.RECOVERED_AFTER_MULTIPLE_FAILURES)
    }

    @Test
    fun `abandonment drops four stages with floor at one`() {
        val current = updater.recordRealExposure(itemId = "item-9", current = ItemState(itemId = "item-9", stage = 8, nextReviewAt = now + DAY_MS))

        val updated = updater.applyAbandon(itemId = current.itemId, current = current, now = now)

        assertThat(updated.stage).isEqualTo(4)
        assertThat(updated.nextReviewAt).isEqualTo(now + 7 * DAY_MS)
        assertThat(updated.timesShown).isEqualTo(1)
        assertThat(updated.timesAbandoned).isEqualTo(1)
        assertThat(updated.lastOutcome).isEqualTo(ItemReviewOutcome.ABANDONED)
    }

    @Test
    fun `abandonment on stage zero keeps item immediate`() {
        val current = ItemState(itemId = "item-10", stage = 0)

        val updated = updater.applyAbandon(itemId = current.itemId, current = current, now = now)

        assertThat(updated.stage).isEqualTo(0)
        assertThat(updated.nextReviewAt).isEqualTo(now)
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
