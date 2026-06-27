package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.domain.session.advanceSocialGate
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.state.PendingPhase
import com.estudio.antiprocrastinacion.app.model.state.SessionPromptKind
import com.estudio.antiprocrastinacion.app.model.state.StudySession
import com.estudio.antiprocrastinacion.app.model.state.StudySessionPayload
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdvanceSocialGateTest {
    @Test
    fun `an outstanding wrong gate question blocks the unlock even when the goal count is met`() {
        val session =
            gateSession(
                queueItemIds = emptyList(),
                correctCount = 3,
                goalCorrectCount = 3,
                payload =
                    StudySessionPayload(
                        pendingPhase = PendingPhase.FIRST_PASS,
                        correctionQueueItemIds = listOf("wrong-1"),
                        failCountsByItemId = mapOf("wrong-1" to 1),
                    ),
            )

        val advanced = advanceSocialGate(session)

        // Even though correctCount >= goal, the gate must re-ask the failed question, never complete.
        assertThat(advanced.currentItemId).isEqualTo("wrong-1")
        assertThat(advanced.currentPromptKind).isEqualTo(SessionPromptKind.REAL_GATE)
        assertThat(advanced.payload.pendingPhase).isEqualTo(PendingPhase.CORRECTION)
    }

    @Test
    fun `the gate completes only when queue and correction queue are both empty`() {
        val session =
            gateSession(
                queueItemIds = emptyList(),
                correctCount = 3,
                goalCorrectCount = 3,
                payload = StudySessionPayload(correctionQueueItemIds = emptyList()),
            )

        val advanced = advanceSocialGate(session)

        assertThat(advanced.currentItemId).isNull()
    }

    @Test
    fun `the first pass walks the real queue in order`() {
        val session =
            gateSession(
                queueItemIds = listOf("q2", "q3"),
                correctCount = 1,
                goalCorrectCount = 3,
            )

        val advanced = advanceSocialGate(session)

        assertThat(advanced.currentItemId).isEqualTo("q2")
        assertThat(advanced.currentPromptKind).isEqualTo(SessionPromptKind.REAL_GATE)
        assertThat(advanced.queueItemIds).containsExactly("q3")
    }

    @Test
    fun `gate completes instead of filling when real questions are exhausted`() {
        val session =
            gateSession(
                queueItemIds = emptyList(),
                correctCount = 1,
                goalCorrectCount = 3,
                payload =
                    StudySessionPayload(
                        correctionQueueItemIds = emptyList(),
                        gateFillerItemIds = listOf("filler-1"),
                        gateFillerCursor = 0,
                    ),
            )

        val advanced = advanceSocialGate(session)

        assertThat(advanced.currentItemId).isNull()
        assertThat(advanced.queueItemIds).isEmpty()
    }
}

private fun gateSession(
    queueItemIds: List<String>,
    correctCount: Int,
    goalCorrectCount: Int,
    payload: StudySessionPayload = StudySessionPayload(),
): StudySession =
    StudySession(
        sessionId = "gate",
        mode = SessionMode.QUICK,
        surface = Surface.SOCIAL_GATE,
        packetId = "pkt",
        topicUnitId = "u1",
        currentTopicTitle = "Unidad",
        queueItemIds = queueItemIds,
        rescueQueueItemIds = emptyList(),
        goalCorrectCount = goalCorrectCount,
        currentNodeId = null,
        currentItemId = "current",
        currentAttemptIndex = 1,
        currentPromptKind = SessionPromptKind.REAL_GATE,
        correctCount = correctCount,
        stepIndex = 0,
        startedAt = 0L,
        lastInteractionAt = 0L,
        isMicroPromptActive = false,
        isExitArmed = false,
        exitArmedUntil = null,
        payload = payload,
    )
