package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.domain.session.applyManualAnswerState
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.state.PendingPhase
import com.estudio.antiprocrastinacion.app.model.state.SessionPromptKind
import com.estudio.antiprocrastinacion.app.model.state.StudySession
import com.estudio.antiprocrastinacion.app.model.state.StudySessionPayload
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ManualSessionStateReducerTest {
    @Test
    fun `first pass failure keeps session open and defers correction until scheduled pass is done`() {
        val afterFailure =
            applyManualAnswerState(
                session =
                    manualSession(
                        currentItemId = "item-a",
                        queueItemIds = listOf("item-b"),
                        payload = StudySessionPayload(pendingPhase = PendingPhase.FIRST_PASS),
                    ),
                itemId = "item-a",
                isCorrect = false,
                answeredAt = 2_000L,
            )

        assertThat(afterFailure.currentItemId).isEqualTo("item-b")
        assertThat(afterFailure.queueItemIds).isEmpty()
        assertThat(afterFailure.correctCount).isEqualTo(0)
        assertThat(afterFailure.payload.correctionQueueItemIds).containsExactly("item-a")

        val afterScheduledCorrect =
            applyManualAnswerState(
                session = afterFailure,
                itemId = "item-b",
                isCorrect = true,
                answeredAt = 3_000L,
            )

        assertThat(afterScheduledCorrect.currentItemId).isEqualTo("item-a")
        assertThat(afterScheduledCorrect.queueItemIds).isEmpty()
        assertThat(afterScheduledCorrect.correctCount).isEqualTo(1)
        assertThat(afterScheduledCorrect.currentAttemptIndex).isEqualTo(2)
        assertThat(afterScheduledCorrect.payload.pendingPhase).isEqualTo(PendingPhase.CORRECTION)
    }

    @Test
    fun `correction failure rotates the live incorrect queue without duplicating the counter`() {
        val enteringCorrection =
            manualSession(
                currentItemId = "item-a",
                queueItemIds = listOf("item-b"),
                payload =
                    StudySessionPayload(
                        pendingPhase = PendingPhase.CORRECTION,
                        correctionQueueItemIds = listOf("item-a", "item-b"),
                        failCountsByItemId = mapOf("item-a" to 1, "item-b" to 1),
                    ),
                stepIndex = 2,
            )

        val afterFailure =
            applyManualAnswerState(
                session = enteringCorrection,
                itemId = "item-a",
                isCorrect = false,
                answeredAt = 4_000L,
            )

        assertThat(afterFailure.currentItemId).isEqualTo("item-b")
        assertThat(afterFailure.queueItemIds).isEmpty()
        assertThat(afterFailure.currentAttemptIndex).isEqualTo(2)
        assertThat(afterFailure.payload.correctionQueueItemIds).containsExactly("item-b", "item-a").inOrder()
        assertThat(afterFailure.payload.failCountsByItemId["item-a"]).isEqualTo(2)
        assertThat(afterFailure.payload.correctionQueueItemIds).hasSize(2)
    }

    @Test
    fun `manual session completes only after the last live incorrect is corrected`() {
        val afterCorrection =
            applyManualAnswerState(
                session =
                    manualSession(
                        currentItemId = "item-a",
                        queueItemIds = emptyList(),
                        payload =
                            StudySessionPayload(
                                pendingPhase = PendingPhase.CORRECTION,
                                correctionQueueItemIds = listOf("item-a"),
                                failCountsByItemId = mapOf("item-a" to 1),
                            ),
                        correctCount = 1,
                        stepIndex = 2,
                    ),
                itemId = "item-a",
                isCorrect = true,
                answeredAt = 5_000L,
            )

        assertThat(afterCorrection.currentItemId).isNull()
        assertThat(afterCorrection.queueItemIds).isEmpty()
        assertThat(afterCorrection.correctCount).isEqualTo(2)
        assertThat(afterCorrection.payload.correctionQueueItemIds).isEmpty()
        assertThat(afterCorrection.payload.failCountsByItemId).isEmpty()
    }
}

private fun manualSession(
    currentItemId: String,
    queueItemIds: List<String>,
    payload: StudySessionPayload,
    correctCount: Int = 0,
    stepIndex: Int = 0,
): StudySession =
    StudySession(
        sessionId = "manual-session",
        mode = SessionMode.DEEP,
        surface = Surface.IN_APP_DEEP,
        packetId = "packet",
        topicUnitId = "unit-1",
        currentCourseTitle = "Curso",
        currentTopicTitle = "Unidad",
        queueItemIds = queueItemIds,
        rescueQueueItemIds = emptyList(),
        goalCorrectCount = 2,
        currentNodeId = "node-1",
        currentItemId = currentItemId,
        currentAttemptIndex = 1,
        currentPromptKind = SessionPromptKind.MANUAL,
        correctCount = correctCount,
        stepIndex = stepIndex,
        startedAt = 1_000L,
        lastInteractionAt = 1_000L,
        isMicroPromptActive = false,
        isExitArmed = false,
        exitArmedUntil = null,
        payload = payload,
    )
