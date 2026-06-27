package com.estudio.antiprocrastinacion

import android.net.Uri
import com.estudio.antiprocrastinacion.app.data.local.db.CourseRecovery
import com.estudio.antiprocrastinacion.app.data.local.db.RecoveryNotice
import com.estudio.antiprocrastinacion.app.data.local.db.RecoveryNoticeAccess
import com.estudio.antiprocrastinacion.app.data.local.db.RecoveryReport
import com.estudio.antiprocrastinacion.app.domain.repository.ContentRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ImportExecutionResult
import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidationProfile
import com.estudio.antiprocrastinacion.app.domain.repository.SessionRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationReport
import com.estudio.antiprocrastinacion.app.domain.session.BackPressResult
import com.estudio.antiprocrastinacion.app.domain.session.SessionEngine
import com.estudio.antiprocrastinacion.app.domain.session.SessionTransition
import com.estudio.antiprocrastinacion.app.model.content.ContentOrigin
import com.estudio.antiprocrastinacion.app.model.content.ContentTree
import com.estudio.antiprocrastinacion.app.model.content.FacetType
import com.estudio.antiprocrastinacion.app.model.content.Item
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.Node
import com.estudio.antiprocrastinacion.app.model.content.NodeDetail
import com.estudio.antiprocrastinacion.app.model.content.NodeType
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.content.UnitModel
import com.estudio.antiprocrastinacion.app.model.state.StudyPrompt
import com.estudio.antiprocrastinacion.app.model.state.StudySession
import com.estudio.antiprocrastinacion.app.model.state.UserAnswer
import com.estudio.antiprocrastinacion.app.ui.home.HomeEffect
import com.estudio.antiprocrastinacion.app.ui.home.HomeViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private object NoOpCourseRecovery : CourseRecovery {
    override suspend fun backup(): Boolean = false

    override suspend fun detectAndRecover(): RecoveryReport? = null
}

private object NoOpRecoveryNotice : RecoveryNoticeAccess {
    override fun setNotice(message: String, now: Long) = Unit

    override fun activeNotice(): RecoveryNotice? = null

    override fun acknowledge() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
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
    fun `refresh exposes isolated summaries by sector`() = runTest(dispatcher) {
        val quickSession = sampleSession(sessionId = "quick-session", mode = SessionMode.QUICK)
        val deepSession = sampleSession(sessionId = "deep-session", mode = SessionMode.DEEP)
        val drainSession = sampleSession(sessionId = "drain-session", mode = SessionMode.DRAIN)
        val viewModel =
            HomeViewModel(
                contentRepository = FakeHomeContentRepository(),
                sessionRepository =
                    FakeSessionRepository(
                        activeSessions =
                            listOf(
                                quickSession,
                                deepSession,
                                drainSession,
                            ),
                    ),
                sessionEngine = FakeSessionEngine(startQuickPrompt = samplePrompt(quickSession)),
                courseRecoveryStore = NoOpCourseRecovery,
                recoveryNoticeStore = NoOpRecoveryNotice,
            )

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.quickSessionSummary?.sessionId).isEqualTo("quick-session")
        assertThat(viewModel.uiState.value.deepSessionSummary?.modeLabel).isEqualTo("Modo profundo")
        assertThat(viewModel.uiState.value.drainSessionSummary?.modeLabel).isEqualTo("Vaciar")
    }

    @Test
    fun `start quick navigates directly when quick prompt exists`() = runTest(dispatcher) {
        val prompt = samplePrompt(sampleSession("quick-session", mode = SessionMode.QUICK))
        val viewModel =
            HomeViewModel(
                contentRepository = FakeHomeContentRepository(),
                sessionRepository = FakeSessionRepository(),
                sessionEngine = FakeSessionEngine(startQuickPrompt = prompt),
                courseRecoveryStore = NoOpCourseRecovery,
                recoveryNoticeStore = NoOpRecoveryNotice,
            )

        advanceUntilIdle()
        val effect = async { viewModel.effects.first() }

        viewModel.startQuick()
        advanceUntilIdle()

        assertThat(effect.await()).isEqualTo(HomeEffect.NavigateToStudy("quick-session"))
    }

    @Test
    fun `start quick without due cards opens dedicated completion screen`() = runTest(dispatcher) {
        val viewModel =
            HomeViewModel(
                contentRepository = FakeHomeContentRepository(),
                sessionRepository = FakeSessionRepository(),
                sessionEngine = FakeSessionEngine(startQuickPrompt = null),
                courseRecoveryStore = NoOpCourseRecovery,
                recoveryNoticeStore = NoOpRecoveryNotice,
            )

        advanceUntilIdle()
        val effect = async { viewModel.effects.first() }

        viewModel.startQuick()
        advanceUntilIdle()

        assertThat(effect.await()).isEqualTo(HomeEffect.NavigateToQuickComplete)
    }

    @Test
    fun `open deep resumes only deep session and does not care about quick session`() = runTest(dispatcher) {
        val quickSession = sampleSession(sessionId = "quick-session", mode = SessionMode.QUICK)
        val deepSession = sampleSession(sessionId = "deep-session", mode = SessionMode.DEEP)
        val viewModel =
            HomeViewModel(
                contentRepository = FakeHomeContentRepository(),
                sessionRepository = FakeSessionRepository(activeSessions = listOf(quickSession, deepSession)),
                sessionEngine = FakeSessionEngine(),
                courseRecoveryStore = NoOpCourseRecovery,
                recoveryNoticeStore = NoOpRecoveryNotice,
            )

        advanceUntilIdle()
        val effect = async { viewModel.effects.first() }

        viewModel.openDeep()
        advanceUntilIdle()

        assertThat(effect.await()).isEqualTo(HomeEffect.NavigateToStudy("deep-session"))
    }

    @Test
    fun `open drain without active drain goes to drain menu`() = runTest(dispatcher) {
        val quickSession = sampleSession(sessionId = "quick-session", mode = SessionMode.QUICK)
        val viewModel =
            HomeViewModel(
                contentRepository = FakeHomeContentRepository(),
                sessionRepository = FakeSessionRepository(activeSessions = listOf(quickSession)),
                sessionEngine = FakeSessionEngine(),
                courseRecoveryStore = NoOpCourseRecovery,
                recoveryNoticeStore = NoOpRecoveryNotice,
            )

        advanceUntilIdle()
        val effect = async { viewModel.effects.first() }

        viewModel.openDrain()
        advanceUntilIdle()

        assertThat(effect.await()).isEqualTo(HomeEffect.NavigateToDrain)
    }
}

private class FakeHomeContentRepository : ContentRepository {
    override suspend fun seedDemoIfNeeded() = Unit
    override suspend fun importContentPackage(
        rawJson: String,
        sourceLabel: String,
        validationProfile: ImportValidationProfile,
    ) =
        ImportExecutionResult(sourceLabel, false, ValidationReport(emptyList(), emptyList()))

    override suspend fun importContentPackage(uri: Uri) =
        ImportExecutionResult(uri.toString(), false, ValidationReport(emptyList(), emptyList()))

    override suspend fun prepareContentPackage(uri: Uri) =
        error("Not used in unit test")

    override suspend fun getContentTree(): ContentTree = ContentTree(emptyList())
    override suspend fun getUnits(): List<UnitModel> = emptyList()
    override suspend fun getSchedulableNodes(): List<Node> = emptyList()
    override suspend fun getSchedulableNodesByUnit(unitId: String): List<Node> = emptyList()
    override suspend fun getNode(nodeId: String): Node? = null
    override suspend fun getNodeDetail(nodeId: String, includeArchivedItems: Boolean): NodeDetail? = null
    override suspend fun getItem(itemId: String, includeArchived: Boolean): Item? = null
    override suspend fun getItemsForNode(nodeId: String, includeArchived: Boolean): List<Item> = emptyList()
    override suspend fun getItemsForNodes(nodeIds: List<String>, includeArchived: Boolean): List<Item> = emptyList()
    override suspend fun setArchivedCandidate(nodeId: String, archived: Boolean) = Unit
    override suspend fun hasRealImportedContent(): Boolean = false
    override suspend fun getContentPackage(rawJson: String): com.estudio.antiprocrastinacion.app.model.json.ContentPackageDto {
        error("Not used in unit test")
    }
}

private class FakeSessionRepository(
    activeSessions: List<StudySession> = emptyList(),
) : SessionRepository {
    private val sessions = activeSessions.associateBy(StudySession::mode).toMutableMap()

    override suspend fun getActiveSession(mode: SessionMode): StudySession? = sessions[mode]

    override suspend fun getActiveSessionById(sessionId: String): StudySession? =
        sessions.values.firstOrNull { it.sessionId == sessionId }

    override suspend fun getAllActiveSessions(): List<StudySession> = sessions.values.toList()

    override suspend fun saveActiveSession(session: StudySession) {
        sessions[session.mode] = session
    }

    override suspend fun clearActiveSession(mode: SessionMode) {
        sessions.remove(mode)
    }

    override suspend fun clearAllActiveSessions() {
        sessions.clear()
    }
}

private class FakeSessionEngine(
    private val startQuickPrompt: StudyPrompt? = null,
) : SessionEngine {
    override suspend fun startQuickSession(): StudyPrompt? = startQuickPrompt

    override suspend fun startNotificationSession(
        preferredUnitId: String?,
        preferredItemId: String?,
    ): StudyPrompt? = null

    override suspend fun startSocialGateSession(goalCorrectCount: Int): StudyPrompt? = null

    override suspend fun startDeepSession(unitId: String): StudyPrompt? = null

    override suspend fun startDrainSession(unitId: String): StudyPrompt? = null

    override suspend fun terminateSession(sessionId: String): Boolean = false

    override suspend fun resumeSession(sessionId: String): StudyPrompt? = null

    override suspend fun submitAnswer(sessionId: String, answer: UserAnswer): SessionTransition =
        error("Not used in unit test")

    override suspend fun handleBackPressed(sessionId: String): BackPressResult =
        error("Not used in unit test")

    override suspend fun recordAbandon(sessionId: String, reason: com.estudio.antiprocrastinacion.app.model.event.AbandonReason) = Unit

    override suspend fun handleInactivityTimeout(sessionId: String): BackPressResult =
        BackPressResult.Exit(sessionId)
}

private fun sampleSession(sessionId: String, mode: SessionMode): StudySession =
    StudySession(
        sessionId = sessionId,
        mode = mode,
        surface = if (mode == SessionMode.QUICK) Surface.IN_APP_QUICK else Surface.IN_APP_DEEP,
        packetId = "packet",
        topicUnitId = "unit",
        currentTopicTitle = "Tema",
        queueItemIds = emptyList(),
        rescueQueueItemIds = emptyList(),
        goalCorrectCount = 1,
        currentNodeId = "node",
        currentItemId = "item",
        currentAttemptIndex = 1,
        correctCount = 0,
        stepIndex = 0,
        startedAt = 1L,
        lastInteractionAt = 1L,
        isMicroPromptActive = false,
        isExitArmed = false,
        exitArmedUntil = null,
    )

private fun samplePrompt(session: StudySession): StudyPrompt =
    StudyPrompt(
        session = session,
        node =
            Node(
                nodeId = "node",
                courseId = "course",
                unitId = "unit",
                outcomeIds = listOf("outcome"),
                title = "Tema",
                coreClaim = "claim",
                type = NodeType.CONCEPT,
                weightExam = 0.8,
                prerequisites = emptyList(),
                facets = listOf(FacetType.DEFINICION_FUNCIONAL),
                mustKnow = listOf("must"),
                commonErrors = listOf("error"),
                minimumMasteryDefinition = "mastery",
                surfaceEasyReady = true,
                surfaceEasyItemCount = 4,
                sourceRefs = emptyList(),
                version = 1,
                updatedAt = 1L,
                archivedCandidate = false,
                contentOrigin = ContentOrigin.IMPORTED,
            ),
        item =
            Item(
                itemId = "item",
                nodeId = "node",
                facet = FacetType.DEFINICION_FUNCIONAL,
                format = ItemFormat.TRUE_FALSE,
                frictionLevel = 1,
                difficultySeed = 0.2,
                itemRole = ItemRole.CORE,
                allowedSurfaces = listOf(Surface.IN_APP_QUICK),
                cooldownHours = 1.0,
                stem = "Pregunta",
                correctAnswer = "Falso",
                feedbackShort = "Feedback",
                coversMustKnow = listOf("must"),
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
                contentOrigin = ContentOrigin.IMPORTED,
            ),
        promptIndex = 1,
    )
