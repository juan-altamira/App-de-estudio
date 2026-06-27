package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.data.local.dao.SessionDao
import com.estudio.antiprocrastinacion.app.data.local.db.ActiveSessionEntity
import com.estudio.antiprocrastinacion.app.data.local.db.LocalSessionRepository
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.state.StudySession
import com.estudio.antiprocrastinacion.app.ui.common.TimeProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SessionRepositoryIsolationTest {
    private val zoneId = ZoneId.of("America/Argentina/Buenos_Aires")

    @Test
    fun `save active session keeps one session per sector without clearing others`() = runTest {
        val dao = FakeSessionDao()
        val repository = LocalSessionRepository(dao, FixedRepositoryTimeProvider(epoch("2026-04-23T12:00:00Z")), zoneId)

        repository.saveActiveSession(sampleSession("quick-session", SessionMode.QUICK, epoch("2026-04-23T11:00:00Z")))
        repository.saveActiveSession(sampleSession("deep-session", SessionMode.DEEP, epoch("2026-04-23T10:00:00Z")))

        assertThat(repository.getActiveSession(SessionMode.QUICK)?.sessionId).isEqualTo("quick-session")
        assertThat(repository.getActiveSession(SessionMode.DEEP)?.sessionId).isEqualTo("deep-session")
        assertThat(repository.getAllActiveSessions().map { it.sessionId }).containsExactly("quick-session", "deep-session")
    }

    @Test
    fun `saving a second session in same sector replaces only that sector`() = runTest {
        val dao = FakeSessionDao()
        val repository = LocalSessionRepository(dao, FixedRepositoryTimeProvider(epoch("2026-04-23T12:00:00Z")), zoneId)

        repository.saveActiveSession(sampleSession("quick-old", SessionMode.QUICK, epoch("2026-04-23T09:00:00Z")))
        repository.saveActiveSession(sampleSession("deep-session", SessionMode.DEEP, epoch("2026-04-23T10:00:00Z")))
        repository.saveActiveSession(sampleSession("quick-new", SessionMode.QUICK, epoch("2026-04-23T11:00:00Z")))

        assertThat(repository.getActiveSession(SessionMode.QUICK)?.sessionId).isEqualTo("quick-new")
        assertThat(repository.getActiveSession(SessionMode.DEEP)?.sessionId).isEqualTo("deep-session")
        assertThat(repository.getAllActiveSessions().map { it.sessionId }).containsExactly("deep-session", "quick-new")
    }

    @Test
    fun `quick session from previous local day expires but deep session does not`() = runTest {
        val dao = FakeSessionDao()
        dao.upsertActiveSession(sampleSession("quick-old", SessionMode.QUICK, epoch("2026-04-22T22:30:00Z")).toEntity())
        dao.upsertActiveSession(sampleSession("deep-old", SessionMode.DEEP, epoch("2026-04-22T22:30:00Z")).toEntity())

        val repository = LocalSessionRepository(dao, FixedRepositoryTimeProvider(epoch("2026-04-23T12:00:00Z")), zoneId)

        assertThat(repository.getActiveSession(SessionMode.QUICK)).isNull()
        assertThat(repository.getActiveSession(SessionMode.DEEP)?.sessionId).isEqualTo("deep-old")
        assertThat(dao.getActiveSession(SessionMode.QUICK.name)).isNull()
    }
}

private class FakeSessionDao : SessionDao {
    private val sessionsByMode = linkedMapOf<String, ActiveSessionEntity>()

    override suspend fun upsertActiveSession(session: ActiveSessionEntity) {
        sessionsByMode[session.mode] = session
    }

    override suspend fun getActiveSession(mode: String): ActiveSessionEntity? = sessionsByMode[mode]

    override suspend fun getActiveSessionById(sessionId: String): ActiveSessionEntity? =
        sessionsByMode.values.firstOrNull { it.sessionId == sessionId }

    override suspend fun getActiveSessions(): List<ActiveSessionEntity> =
        sessionsByMode.values.sortedByDescending { it.lastInteractionAt }

    override suspend fun clearActiveSession(mode: String) {
        sessionsByMode.remove(mode)
    }

    override suspend fun clearActiveSessions() {
        sessionsByMode.clear()
    }
}

private class FixedRepositoryTimeProvider(
    private val now: Long,
) : TimeProvider {
    override fun now(): Long = now
}

private fun sampleSession(
    sessionId: String,
    mode: SessionMode,
    startedAt: Long,
): StudySession =
    StudySession(
        sessionId = sessionId,
        mode = mode,
        surface = if (mode == SessionMode.QUICK) Surface.IN_APP_QUICK else Surface.IN_APP_DEEP,
        packetId = "packet-$sessionId",
        topicUnitId = "unit-1",
        currentTopicTitle = "Tema",
        queueItemIds = emptyList(),
        rescueQueueItemIds = emptyList(),
        goalCorrectCount = 3,
        currentNodeId = "node-1",
        currentItemId = "item-1",
        currentAttemptIndex = 1,
        correctCount = 1,
        stepIndex = 1,
        startedAt = startedAt,
        lastInteractionAt = startedAt,
        isMicroPromptActive = false,
        isExitArmed = false,
        exitArmedUntil = null,
    )

private fun StudySession.toEntity(): ActiveSessionEntity =
    ActiveSessionEntity(
        sessionId = sessionId,
        mode = mode.name,
        surface = surface.name,
        packetId = packetId,
        topicUnitId = topicUnitId,
        currentCourseTitle = currentCourseTitle,
        currentTopicTitle = currentTopicTitle,
        queueJson = "[]",
        rescueQueueJson = "[]",
        goalCorrectCount = goalCorrectCount,
        currentNodeId = currentNodeId,
        currentItemId = currentItemId,
        currentAttemptIndex = currentAttemptIndex,
        currentPromptKind = currentPromptKind.name,
        correctCount = correctCount,
        stepIndex = stepIndex,
        startedAt = startedAt,
        lastInteractionAt = lastInteractionAt,
        isMicroPromptActive = isMicroPromptActive,
        isExitArmed = isExitArmed,
        exitArmedUntil = exitArmedUntil,
        payloadJson = "{}",
    )

private fun epoch(instant: String): Long = Instant.parse(instant).toEpochMilli()
