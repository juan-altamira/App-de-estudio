package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.data.local.db.asDomain
import com.estudio.antiprocrastinacion.app.data.local.db.asEntity
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.state.StudySession
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StudySessionPersistenceTest {
    @Test
    fun `study session roundtrip preserves exact persisted state`() {
        val original =
            StudySession(
                sessionId = "session-restore-1",
                mode = SessionMode.DEEP,
                surface = Surface.IN_APP_DEEP,
                packetId = "packet-1",
                topicUnitId = "eth_checkpoints_finality",
                currentTopicTitle = "Checkpoints y Finality",
                queueItemIds = listOf("item-2", "item-3", "item-4"),
                rescueQueueItemIds = listOf("rescue-1", "rescue-2"),
                goalCorrectCount = 7,
                currentNodeId = "node-1",
                currentItemId = "item-1",
                currentAttemptIndex = 2,
                correctCount = 3,
                stepIndex = 4,
                startedAt = 100L,
                lastInteractionAt = 250L,
                isMicroPromptActive = true,
                isExitArmed = true,
                exitArmedUntil = 8_888L,
            )

        val restored = original.asEntity().asDomain()

        assertThat(restored).isEqualTo(original)
    }
}
