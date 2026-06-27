package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.domain.scheduler.DeepModeUnitOption
import com.estudio.antiprocrastinacion.app.domain.scheduler.SchedulerService
import com.estudio.antiprocrastinacion.app.domain.session.BackPressResult
import com.estudio.antiprocrastinacion.app.domain.session.SessionEngine
import com.estudio.antiprocrastinacion.app.domain.session.SessionTransition
import com.estudio.antiprocrastinacion.app.model.content.ContentOrigin
import com.estudio.antiprocrastinacion.app.model.content.FacetType
import com.estudio.antiprocrastinacion.app.model.content.Item
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemOption
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.Node
import com.estudio.antiprocrastinacion.app.model.content.NodeType
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.event.AbandonReason
import com.estudio.antiprocrastinacion.app.model.state.StudyPrompt
import com.estudio.antiprocrastinacion.app.model.state.StudySession
import com.estudio.antiprocrastinacion.app.model.state.UserAnswer
import com.estudio.antiprocrastinacion.app.ui.study.deep.DeepStudyViewModel
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

@OptIn(ExperimentalCoroutinesApi::class)
class DeepStudyViewModelTest {
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
    fun `refresh loads available deep units`() = runTest(dispatcher) {
        val unit =
            DeepModeUnitOption(
                unitId = "unit-1",
                courseTitle = "Ethereum Consensus",
                unitTitle = "Checkpoints y Finality",
                availableNodeCount = 3,
            )

        val viewModel =
            DeepStudyViewModel(
                schedulerService = FakeDeepSchedulerService(availableUnits = listOf(unit)),
                sessionEngine = FakeDeepSessionEngine(),
            )

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.units).containsExactly(unit)
    }

    @Test
    fun `start deep emits started session id`() = runTest(dispatcher) {
        val prompt = deepSamplePrompt(deepSampleSession(sessionId = "deep-session", mode = SessionMode.DEEP))
        val engine = FakeDeepSessionEngine(startDeepPrompt = prompt)
        val viewModel =
            DeepStudyViewModel(
                schedulerService = FakeDeepSchedulerService(),
                sessionEngine = engine,
            )

        advanceUntilIdle()
        val effect = async { viewModel.effects.first() }

        viewModel.startDeep("unit-1")
        advanceUntilIdle()

        assertThat(engine.deepUnitIds).containsExactly("unit-1")
        assertThat(effect.await()).isEqualTo("deep-session")
    }

    @Test
    fun `start drain emits started session id`() = runTest(dispatcher) {
        val prompt = deepSamplePrompt(deepSampleSession(sessionId = "drain-session", mode = SessionMode.DRAIN))
        val engine = FakeDeepSessionEngine(startDrainPrompt = prompt)
        val viewModel =
            DeepStudyViewModel(
                schedulerService = FakeDeepSchedulerService(),
                sessionEngine = engine,
            )

        advanceUntilIdle()
        val effect = async { viewModel.effects.first() }

        viewModel.startDrain("unit-2")
        advanceUntilIdle()

        assertThat(engine.drainUnitIds).containsExactly("unit-2")
        assertThat(effect.await()).isEqualTo("drain-session")
    }
}

private class FakeDeepSchedulerService(
    private val availableUnits: List<DeepModeUnitOption> = emptyList(),
) : SchedulerService {
    override suspend fun buildQuickPacket(
        preferredUnitId: String?,
        preferredItemId: String?,
    ) = null

    override suspend fun buildNotificationPacket(
        preferredUnitId: String?,
        preferredItemId: String?,
    ) = null

    override suspend fun buildSocialGatePacket(goalCorrectCount: Int) = null
    override suspend fun buildDeepPacket(unitId: String) = null
    override suspend fun buildDrainPacket(unitId: String) = null
    override suspend fun getAvailableUnits(): List<DeepModeUnitOption> = availableUnits
}

private class FakeDeepSessionEngine(
    private val startDeepPrompt: StudyPrompt? = null,
    private val startDrainPrompt: StudyPrompt? = null,
) : SessionEngine {
    val deepUnitIds = mutableListOf<String>()
    val drainUnitIds = mutableListOf<String>()

    override suspend fun startQuickSession(): StudyPrompt? = null

    override suspend fun startNotificationSession(
        preferredUnitId: String?,
        preferredItemId: String?,
    ): StudyPrompt? = null

    override suspend fun startSocialGateSession(goalCorrectCount: Int): StudyPrompt? = null

    override suspend fun startDeepSession(unitId: String): StudyPrompt? {
        deepUnitIds += unitId
        return startDeepPrompt
    }

    override suspend fun startDrainSession(unitId: String): StudyPrompt? {
        drainUnitIds += unitId
        return startDrainPrompt
    }

    override suspend fun terminateSession(sessionId: String): Boolean = false
    override suspend fun resumeSession(sessionId: String): StudyPrompt? = null
    override suspend fun submitAnswer(sessionId: String, answer: UserAnswer): SessionTransition = error("Not used")
    override suspend fun handleBackPressed(sessionId: String): BackPressResult = error("Not used")
    override suspend fun recordAbandon(sessionId: String, reason: AbandonReason) = Unit
    override suspend fun handleInactivityTimeout(sessionId: String): BackPressResult = BackPressResult.Exit(sessionId)
}

private fun deepSampleSession(
    sessionId: String,
    mode: SessionMode,
): StudySession =
    StudySession(
        sessionId = sessionId,
        mode = mode,
        surface = Surface.IN_APP_DEEP,
        packetId = "packet",
        topicUnitId = "unit",
        currentTopicTitle = "Tema profundo",
        queueItemIds = emptyList(),
        rescueQueueItemIds = emptyList(),
        goalCorrectCount = 4,
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

private fun deepSamplePrompt(session: StudySession): StudyPrompt =
    StudyPrompt(
        session = session,
        node =
            Node(
                nodeId = "node",
                courseId = "course",
                unitId = "unit",
                outcomeIds = listOf("outcome"),
                title = "Tema profundo",
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
                sourceRefs = listOf("manual"),
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
                allowedSurfaces = listOf(Surface.IN_APP_DEEP),
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
                options = listOf(ItemOption("false", "Falso", isCorrect = true)),
                version = 1,
                updatedAt = 1L,
                sourceRefs = listOf("manual"),
                contentOrigin = ContentOrigin.IMPORTED,
            ),
        promptIndex = 0,
    )
