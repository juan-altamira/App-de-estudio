package com.estudio.antiprocrastinacion.app.domain.session

import com.estudio.antiprocrastinacion.app.model.content.Item
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.event.AbandonReason
import com.estudio.antiprocrastinacion.app.model.event.AttemptOutcome
import com.estudio.antiprocrastinacion.app.model.state.NodeFormatStat
import com.estudio.antiprocrastinacion.app.model.state.NodeState
import com.estudio.antiprocrastinacion.app.ui.common.normalized
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NodeStateUpdater @Inject constructor() {
    fun applyAnswer(
        current: NodeState?,
        item: Item,
        outcome: AttemptOutcome,
        latencyMs: Long,
        surface: Surface,
        sessionMode: SessionMode,
        now: Long,
    ): NodeState {
        val base = current ?: NodeState(nodeId = item.nodeId, difficultyUser = item.difficultySeed.normalized())
        val newTimesSeen = base.timesSeen + 1
        val newTimesCorrectFirst = base.timesCorrectFirstTry + if (outcome == AttemptOutcome.CORRECT_FIRST_TRY) 1 else 0
        val newTimesCorrectAfterRescue = base.timesCorrectAfterRescue + if (outcome == AttemptOutcome.CORRECT_AFTER_RESCUE) 1 else 0
        val newTimesFailed = base.timesFailed + if (outcome == AttemptOutcome.INCORRECT) 1 else 0
        val newAvgLatency =
            if (base.timesSeen == 0) latencyMs.toDouble()
            else ((base.avgLatencyMs * base.timesSeen) + latencyMs) / newTimesSeen
        val newStability =
            when (outcome) {
                AttemptOutcome.CORRECT_FIRST_TRY -> base.stabilityHours * 2.0
                AttemptOutcome.CORRECT_AFTER_RESCUE -> base.stabilityHours * 1.25
                AttemptOutcome.INCORRECT -> base.stabilityHours * 0.5
            }.coerceAtLeast(1.0)
        val newRetrievability =
            when (outcome) {
                AttemptOutcome.CORRECT_FIRST_TRY -> (base.retrievability + 0.15).normalized()
                AttemptOutcome.CORRECT_AFTER_RESCUE -> (base.retrievability + 0.05).normalized()
                AttemptOutcome.INCORRECT -> (base.retrievability - 0.10).normalized()
            }
        val newMemoryScore =
            when (outcome) {
                AttemptOutcome.CORRECT_FIRST_TRY -> (base.memoryScore + 0.12).normalized()
                AttemptOutcome.CORRECT_AFTER_RESCUE -> (base.memoryScore + 0.05).normalized()
                AttemptOutcome.INCORRECT -> (base.memoryScore - 0.10).normalized()
            }
        val targetDifficulty = if (outcome == AttemptOutcome.INCORRECT) 1.0 else item.difficultySeed
        val newDifficulty = ((base.difficultyUser * 0.85) + (targetDifficulty * 0.15)).normalized()
        val frictionDelta =
            when {
                outcome == AttemptOutcome.INCORRECT -> 0.08
                latencyMs > 6000L -> 0.04
                else -> -0.03
            }
        val newFriction = (base.frictionUser + frictionDelta).normalized()
        val newCoverage =
            when (outcome) {
                AttemptOutcome.CORRECT_FIRST_TRY -> (base.coverageScore + 0.08).normalized()
                AttemptOutcome.CORRECT_AFTER_RESCUE -> (base.coverageScore + 0.04).normalized()
                AttemptOutcome.INCORRECT -> (base.coverageScore - 0.02).normalized()
            }

        return base.copy(
            memoryScore = newMemoryScore,
            stabilityHours = newStability,
            retrievability = newRetrievability,
            difficultyUser = newDifficulty,
            frictionUser = newFriction,
            coverageScore = newCoverage,
            avgLatencyMs = newAvgLatency,
            errorRate = (newTimesFailed.toDouble() / newTimesSeen).normalized(),
            abandonRate = (base.timesAbandoned.toDouble() / newTimesSeen).normalized(),
            lastReviewedAt = now,
            nextReviewAt = now + (newStability * HOUR_MS).toLong(),
            timesSeen = newTimesSeen,
            timesCorrectFirstTry = newTimesCorrectFirst,
            timesCorrectAfterRescue = newTimesCorrectAfterRescue,
            timesFailed = newTimesFailed,
            lastSurfaceUsed = surface,
            lastSessionMode = sessionMode,
        )
    }

    fun applyAbandon(
        current: NodeState?,
        nodeId: String,
        surface: Surface?,
        sessionMode: SessionMode?,
        reason: AbandonReason,
        now: Long,
    ): NodeState {
        val base = current ?: NodeState(nodeId = nodeId)
        val newTimesSeen = if (base.timesSeen == 0) 1 else base.timesSeen
        val newTimesAbandoned = base.timesAbandoned + 1
        val newStability = (base.stabilityHours * 0.35).coerceAtLeast(1.0)
        val frictionBump = if (reason == AbandonReason.TIMEOUT) 0.12 else 0.08

        return base.copy(
            memoryScore = (base.memoryScore - 0.06).normalized(),
            stabilityHours = newStability,
            retrievability = (base.retrievability - 0.08).normalized(),
            frictionUser = (base.frictionUser + frictionBump).normalized(),
            abandonRate = (newTimesAbandoned.toDouble() / newTimesSeen).normalized(),
            lastReviewedAt = now,
            nextReviewAt = now + (newStability * HOUR_MS).toLong(),
            timesAbandoned = newTimesAbandoned,
            lastSurfaceUsed = surface ?: base.lastSurfaceUsed,
            lastSessionMode = sessionMode ?: base.lastSessionMode,
        )
    }

    fun updateFormatStat(current: NodeFormatStat?, item: Item, success: Boolean, latencyMs: Long): NodeFormatStat {
        val base = current ?: NodeFormatStat(nodeId = item.nodeId, format = item.format)
        val attempts = base.attempts + 1
        val successes = base.successes + if (success) 1 else 0
        val avgLatency =
            if (base.attempts == 0) latencyMs.toDouble()
            else ((base.avgLatencyMs * base.attempts) + latencyMs) / attempts
        return base.copy(
            attempts = attempts,
            successes = successes,
            avgLatencyMs = avgLatency,
        )
    }

    private companion object {
        const val HOUR_MS = 60 * 60 * 1000L
    }
}
