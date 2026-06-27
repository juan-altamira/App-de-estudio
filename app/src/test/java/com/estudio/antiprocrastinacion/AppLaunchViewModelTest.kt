package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.domain.repository.SessionRepository
import com.estudio.antiprocrastinacion.app.domain.session.BackPressResult
import com.estudio.antiprocrastinacion.app.domain.session.SessionEngine
import com.estudio.antiprocrastinacion.app.domain.session.SessionTransition
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.event.AbandonReason
import com.estudio.antiprocrastinacion.app.model.state.StudyPrompt
import com.estudio.antiprocrastinacion.app.model.state.StudySession
import com.estudio.antiprocrastinacion.app.model.state.UserAnswer
import com.estudio.antiprocrastinacion.app.notification.NotificationLaunchRequest
import com.estudio.antiprocrastinacion.app.ui.navigation.AppLaunchTarget
import com.estudio.antiprocrastinacion.app.ui.navigation.AppLaunchViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLaunchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh targets home when there is no active session`() = runTest(dispatcher) {
        val viewModel =
            AppLaunchViewModel(
                FakeAppLaunchSessionRepository(),
                FakeAppLaunchSessionEngine(null),
            )

        viewModel.refresh()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.target).isEqualTo(AppLaunchTarget.Home)
    }

    @Test
    fun `refresh targets study when there is exactly one active session`() = runTest(dispatcher) {
        val session = sampleLaunchSession("session-42")
        val viewModel =
            AppLaunchViewModel(
                FakeAppLaunchSessionRepository(listOf(session)),
                FakeAppLaunchSessionEngine(null),
            )

        viewModel.refresh()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.target).isEqualTo(AppLaunchTarget.Study("session-42"))
    }

    @Test
    fun `refresh targets home when there are multiple active sessions`() = runTest(dispatcher) {
        val viewModel =
            AppLaunchViewModel(
                FakeAppLaunchSessionRepository(
                    listOf(
                        sampleLaunchSession("session-1"),
                        sampleLaunchSession("session-2").copy(mode = SessionMode.DRAIN),
                    ),
                ),
                FakeAppLaunchSessionEngine(null),
            )

        viewModel.refresh()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.target).isEqualTo(AppLaunchTarget.Home)
    }

    @Test
    fun `notification launch starts notification session even if another sector is active`() = runTest(dispatcher) {
        val prompt = sampleLaunchPrompt("notification-session")
        val engine = FakeAppLaunchSessionEngine(prompt)
        val viewModel =
            AppLaunchViewModel(
                FakeAppLaunchSessionRepository(
                    listOf(sampleLaunchSession("deep-session").copy(mode = SessionMode.DEEP)),
                ),
                engine,
            )

        viewModel.refresh(NotificationLaunchRequest(preferredUnitId = "unit-1", preferredItemId = null))
        advanceUntilIdle()

        assertThat(engine.requestedUnitIds).containsExactly("unit-1")
        assertThat(viewModel.uiState.value.target).isEqualTo(AppLaunchTarget.Study("notification-session"))
    }
}

private class FakeAppLaunchSessionRepository(
    private val activeSessions: List<StudySession> = emptyList(),
) : SessionRepository {
    override suspend fun getActiveSession(mode: SessionMode): StudySession? =
        activeSessions.firstOrNull { it.mode == mode }

    override suspend fun getActiveSessionById(sessionId: String): StudySession? =
        activeSessions.firstOrNull { it.sessionId == sessionId }

    override suspend fun getAllActiveSessions(): List<StudySession> = activeSessions

    override suspend fun saveActiveSession(session: StudySession) = Unit

    override suspend fun clearActiveSession(mode: SessionMode) = Unit

    override suspend fun clearAllActiveSessions() = Unit
}

private class FakeAppLaunchSessionEngine(
    private val notificationPrompt: StudyPrompt?,
) : SessionEngine {
    val requestedUnitIds = mutableListOf<String?>()

    override suspend fun startQuickSession(): StudyPrompt? = notificationPrompt

    override suspend fun startNotificationSession(
        preferredUnitId: String?,
        preferredItemId: String?,
    ): StudyPrompt? {
        requestedUnitIds += preferredUnitId
        return notificationPrompt
    }

    override suspend fun startSocialGateSession(goalCorrectCount: Int): StudyPrompt? = null

    override suspend fun startDeepSession(unitId: String): StudyPrompt? = null

    override suspend fun startDrainSession(unitId: String): StudyPrompt? = null

    override suspend fun terminateSession(sessionId: String): Boolean = false

    override suspend fun resumeSession(sessionId: String): StudyPrompt? = notificationPrompt

    override suspend fun submitAnswer(sessionId: String, answer: UserAnswer): SessionTransition =
        error("Not used in unit test")

    override suspend fun handleBackPressed(sessionId: String): BackPressResult =
        error("Not used in unit test")

    override suspend fun recordAbandon(sessionId: String, reason: AbandonReason) = Unit

    override suspend fun handleInactivityTimeout(sessionId: String): BackPressResult =
        error("Not used in unit test")
}

private fun sampleLaunchSession(sessionId: String): StudySession =
    StudySession(
        sessionId = sessionId,
        mode = SessionMode.DEEP,
        surface = Surface.IN_APP_DEEP,
        packetId = "packet-1",
        topicUnitId = "unit-1",
        currentTopicTitle = "Checkpoints y Finality",
        queueItemIds = listOf("item-1", "item-2"),
        rescueQueueItemIds = emptyList(),
        goalCorrectCount = 6,
        currentNodeId = "node-1",
        currentItemId = "item-1",
        currentAttemptIndex = 1,
        correctCount = 2,
        stepIndex = 2,
        startedAt = 1L,
        lastInteractionAt = 2L,
        isMicroPromptActive = false,
        isExitArmed = false,
        exitArmedUntil = null,
    )

private fun sampleLaunchPrompt(sessionId: String): StudyPrompt =
    StudyPrompt(
        session = sampleLaunchSession(sessionId).copy(mode = SessionMode.QUICK, surface = Surface.NOTIFICATION),
        node = testLaunchNode(),
        item = testLaunchItem(),
        promptIndex = 1,
    )

private fun testLaunchNode() =
    com.estudio.antiprocrastinacion.app.model.content.Node(
        nodeId = "node-1",
        courseId = "course-1",
        unitId = "unit-1",
        outcomeIds = listOf("outcome-1"),
        title = "Nodo",
        coreClaim = "Claim",
        type = com.estudio.antiprocrastinacion.app.model.content.NodeType.CONCEPT,
        weightExam = 0.8,
        prerequisites = emptyList(),
        facets = listOf(com.estudio.antiprocrastinacion.app.model.content.FacetType.DEFINICION_FUNCIONAL),
        mustKnow = listOf("mk"),
        commonErrors = listOf("ce"),
        minimumMasteryDefinition = "mm",
        surfaceEasyReady = true,
        surfaceEasyItemCount = 4,
        sourceRefs = emptyList(),
        version = 1,
        updatedAt = 1L,
        archivedCandidate = false,
        contentOrigin = com.estudio.antiprocrastinacion.app.model.content.ContentOrigin.IMPORTED,
    )

private fun testLaunchItem() =
    com.estudio.antiprocrastinacion.app.model.content.Item(
        itemId = "item-1",
        nodeId = "node-1",
        facet = com.estudio.antiprocrastinacion.app.model.content.FacetType.DEFINICION_FUNCIONAL,
        format = com.estudio.antiprocrastinacion.app.model.content.ItemFormat.TRUE_FALSE,
        frictionLevel = 1,
        difficultySeed = 0.2,
        itemRole = com.estudio.antiprocrastinacion.app.model.content.ItemRole.CORE,
        allowedSurfaces = listOf(Surface.NOTIFICATION, Surface.IN_APP_QUICK),
        cooldownHours = 1.0,
        stem = "Stem",
        correctAnswer = "Falso",
        feedbackShort = "Feedback",
        coversMustKnow = listOf("mk"),
        variantGroupId = null,
        rescueGroupId = null,
        nodeComplexity = null,
        facetComplexity = null,
        distractorSimilarity = null,
        prerequisiteDepth = null,
        targetsErrorIds = emptyList(),
        commonErrorSignals = emptyList(),
        options = emptyList(),
        version = 1,
        updatedAt = 1L,
        sourceRefs = emptyList(),
        contentOrigin = com.estudio.antiprocrastinacion.app.model.content.ContentOrigin.IMPORTED,
    )
