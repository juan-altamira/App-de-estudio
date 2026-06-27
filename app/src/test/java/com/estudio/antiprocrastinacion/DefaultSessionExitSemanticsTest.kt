package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.domain.session.asResumableExitState
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.state.StudySession
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DefaultSessionExitSemanticsTest {
    @Test
    fun `resumable exit keeps the current session progress but clears transient exit flags`() {
        val original =
            StudySession(
                sessionId = "session-1",
                mode = SessionMode.DEEP,
                surface = Surface.IN_APP_DEEP,
                packetId = "packet-1",
                topicUnitId = "unit-1",
                currentTopicTitle = "Checkpoints y Finality",
                queueItemIds = listOf("item-2", "item-3"),
                rescueQueueItemIds = listOf("rescue-1"),
                goalCorrectCount = 5,
                currentNodeId = "node-1",
                currentItemId = "item-1",
                currentAttemptIndex = 1,
                correctCount = 2,
                stepIndex = 2,
                startedAt = 100L,
                lastInteractionAt = 120L,
                isMicroPromptActive = false,
                isExitArmed = true,
                exitArmedUntil = 9_000L,
            )

        val updated = original.asResumableExitState(now = 5_000L)

        assertThat(updated.sessionId).isEqualTo(original.sessionId)
        assertThat(updated.packetId).isEqualTo(original.packetId)
        assertThat(updated.currentItemId).isEqualTo(original.currentItemId)
        assertThat(updated.correctCount).isEqualTo(original.correctCount)
        assertThat(updated.stepIndex).isEqualTo(original.stepIndex)
        assertThat(updated.queueItemIds).containsExactlyElementsIn(original.queueItemIds)
        assertThat(updated.rescueQueueItemIds).containsExactlyElementsIn(original.rescueQueueItemIds)
        assertThat(updated.isMicroPromptActive).isFalse()
        assertThat(updated.isExitArmed).isFalse()
        assertThat(updated.exitArmedUntil).isNull()
        assertThat(updated.lastInteractionAt).isEqualTo(5_000L)
    }
}
