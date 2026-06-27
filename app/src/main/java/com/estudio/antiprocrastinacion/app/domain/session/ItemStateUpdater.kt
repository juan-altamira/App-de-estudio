package com.estudio.antiprocrastinacion.app.domain.session

import com.estudio.antiprocrastinacion.app.model.state.ItemReviewOutcome
import com.estudio.antiprocrastinacion.app.model.state.ItemState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemStateUpdater @Inject constructor() {
    fun isDue(
        current: ItemState?,
        now: Long,
    ): Boolean {
        val state = current ?: return true
        return state.stage == 0 || state.nextReviewAt == null || state.nextReviewAt <= now
    }

    fun applyCleanCorrect(
        itemId: String,
        current: ItemState?,
        now: Long,
    ): ItemState = applyCleanCorrectResolved(base = current ?: ItemState(itemId = itemId), now = now)

    /**
     * Entrada única para una respuesta correcta de una tarjeta real (pendientes o gate). Decide si es
     * un acierto limpio o una recuperación post-fallo mirando tanto los fallos de ESTA sesión
     * ([inSessionFailureCount]) como la marca persistida de un fallo anterior sin corregir. Así el
     * castigo por fallo es idéntico en gate y en pendientes, se aplica una sola vez, y no se evapora
     * aunque la sesión haya muerto entre el fallo y la corrección.
     */
    fun applyCorrectAnswer(
        itemId: String,
        current: ItemState?,
        inSessionFailureCount: Int,
        now: Long,
    ): ItemState {
        val markerFailureCount = if (current.hasUnrecoveredFailure()) 1 else 0
        val failureCount = maxOf(inSessionFailureCount, markerFailureCount)
        return if (failureCount <= 0) {
            applyCleanCorrect(itemId = itemId, current = current, now = now)
        } else {
            applyRecoveredAfterFailure(itemId = itemId, current = current, failureCount = failureCount, now = now)
        }
    }

    /**
     * Marca una tarjeta real como fallada y pendiente de corrección, sin bajar la etapa (el descuento
     * de etapa lo aplica [applyRecoveredAfterFailure] al corregir, una sola vez). Mantiene la tarjeta
     * "due" para que siga apareciendo hasta resolverse, y persiste la marca para que el castigo no se
     * pierda si la sesión muere antes de corregir. timesFailed se cuenta acá (una vez por fallo real).
     */
    fun applyFailureMark(
        itemId: String,
        current: ItemState?,
        now: Long,
    ): ItemState {
        val base = current ?: ItemState(itemId = itemId)
        return base.copy(
            lastReviewedAt = now,
            nextReviewAt = if (base.stage == 0) null else now,
            timesFailed = base.timesFailed + 1,
            lastOutcome = ItemReviewOutcome.FAILED_AWAITING_RECOVERY,
        )
    }

    private fun ItemState?.hasUnrecoveredFailure(): Boolean =
        this?.lastOutcome == ItemReviewOutcome.FAILED_AWAITING_RECOVERY ||
            this?.lastOutcome == ItemReviewOutcome.GATE_FAILURE

    fun recordRealExposure(
        itemId: String,
        current: ItemState?,
    ): ItemState {
        val base = current ?: ItemState(itemId = itemId)
        return base.copy(timesShown = base.timesShown + 1)
    }

    /**
     * Sella el momento en que la tarjeta se mostró/respondió como auxiliar (anzuelo/rescate/relleno) sin
     * tocar la repetición espaciada (no cambia etapa, nextReviewAt ni contadores). Solo actualiza
     * lastReviewedAt para que el cooldown del scheduler también evite que una auxiliar reaparezca como
     * auxiliar el mismo día. No marca la tarjeta como "due/no due": una stage 0 sigue due (stage manda).
     */
    fun recordAuxiliaryExposure(
        itemId: String,
        current: ItemState?,
        now: Long,
    ): ItemState {
        val base = current ?: ItemState(itemId = itemId)
        return base.copy(lastReviewedAt = now)
    }

    fun applyRecoveredAfterFailure(
        itemId: String,
        current: ItemState?,
        failureCount: Int,
        now: Long,
    ): ItemState {
        require(failureCount >= 1) { "failureCount must be >= 1" }
        val base = current ?: ItemState(itemId = itemId)
        val nextStage =
            when {
                base.stage == 0 -> 0
                failureCount == 1 -> penalizedStage(base.stage, stagesToDrop = 3)
                else -> penalizedStage(base.stage, stagesToDrop = 4)
            }
        return base.copy(
            stage = nextStage,
            lastReviewedAt = now,
            nextReviewAt = nextReviewAtForStage(nextStage, now),
            // timesFailed NO se incrementa acá: ya se contó en applyFailureMark al momento del fallo,
            // para que un fallo cueste exactamente lo mismo en gate y en pendientes (sin doble conteo).
            timesRecoveredAfterFailure = base.timesRecoveredAfterFailure + 1,
            lastOutcome =
                if (failureCount == 1) {
                    ItemReviewOutcome.RECOVERED_AFTER_ONE_FAILURE
                } else {
                    ItemReviewOutcome.RECOVERED_AFTER_MULTIPLE_FAILURES
                },
        )
    }

    fun applyAbandon(
        itemId: String,
        current: ItemState?,
        now: Long,
    ): ItemState {
        val base = current ?: ItemState(itemId = itemId)
        val nextStage =
            if (base.stage == 0) {
                0
            } else {
                penalizedStage(base.stage, stagesToDrop = 4)
            }
        return base.copy(
            stage = nextStage,
            lastReviewedAt = now,
            nextReviewAt = nextReviewAtForStage(nextStage, now),
            timesAbandoned = base.timesAbandoned + 1,
            lastOutcome = ItemReviewOutcome.ABANDONED,
        )
    }

    fun nextReviewAtForStage(
        stage: Int,
        now: Long,
    ): Long? {
        val intervalMs = stageIntervalMs(stage)
        return if (stage == 0) now else now + intervalMs
    }

    fun stageIntervalMs(stage: Int): Long {
        require(stage in STAGE_INTERVALS_MS.indices) { "Unknown spaced stage: $stage" }
        return STAGE_INTERVALS_MS[stage]
    }

    private fun applyCleanCorrectResolved(
        base: ItemState,
        now: Long,
    ): ItemState {
        val (nextStage, outcome) =
            if (base.stage == 0) {
                1 to ItemReviewOutcome.CLEAN_CORRECT
            } else {
                val scheduledInterval = stageIntervalMs(base.stage)
                val overdueMs =
                    when (val nextReviewAt = base.nextReviewAt) {
                        null -> 0L
                        else -> (now - nextReviewAt).coerceAtLeast(0L)
                    }
                when {
                    overdueMs < scheduledInterval -> (base.stage + 1).coerceAtMost(MAX_STAGE) to ItemReviewOutcome.CLEAN_CORRECT
                    overdueMs < (scheduledInterval * 3) -> base.stage to ItemReviewOutcome.CLEAN_CORRECT_LATE
                    else -> penalizedStage(base.stage, stagesToDrop = 1) to ItemReviewOutcome.CLEAN_CORRECT_VERY_LATE
                }
            }
        return base.copy(
            stage = nextStage,
            lastReviewedAt = now,
            nextReviewAt = nextReviewAtForStage(nextStage, now),
            timesCorrectFirstTry = base.timesCorrectFirstTry + 1,
            lastOutcome = outcome,
        )
    }

    private fun penalizedStage(
        currentStage: Int,
        stagesToDrop: Int,
    ): Int = (currentStage - stagesToDrop).coerceAtLeast(1)

    companion object {
        const val MAX_STAGE = 8
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private val STAGE_INTERVALS_MS =
            longArrayOf(
                0L,
                DAY_MS,
                2 * DAY_MS,
                4 * DAY_MS,
                7 * DAY_MS,
                12 * DAY_MS,
                20 * DAY_MS,
                30 * DAY_MS,
                45 * DAY_MS,
            )
    }
}
