package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.domain.session.BackPressResult
import com.estudio.antiprocrastinacion.app.domain.session.SessionEngine
import com.estudio.antiprocrastinacion.app.domain.session.SessionTransition
import com.estudio.antiprocrastinacion.app.model.content.ContentOrigin
import com.estudio.antiprocrastinacion.app.model.content.FacetType
import com.estudio.antiprocrastinacion.app.model.content.Item
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.Node
import com.estudio.antiprocrastinacion.app.model.content.NodeType
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.event.AbandonReason
import com.estudio.antiprocrastinacion.app.model.state.StudyPrompt
import com.estudio.antiprocrastinacion.app.model.state.StudySession
import com.estudio.antiprocrastinacion.app.model.state.UserAnswer
import com.estudio.antiprocrastinacion.app.ui.study.quick.QuickStudyEffect
import com.estudio.antiprocrastinacion.app.ui.study.quick.QuickStudyViewModel
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
class QuickStudyViewModelTest {
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
    fun `advanced transition waits for manual continue before showing next prompt`() = runTest(dispatcher) {
        val currentPrompt = samplePrompt(sessionId = "session-1", itemId = "item-1", stem = "Pregunta 1", mode = SessionMode.QUICK)
        val nextPrompt = samplePrompt(sessionId = "session-1", itemId = "item-2", stem = "Pregunta 2", mode = SessionMode.QUICK)
        val engine =
            FakeQuickSessionEngine(
                resumedPrompt = currentPrompt,
                submitTransition = SessionTransition.Advanced(nextPrompt),
            )
        val viewModel = QuickStudyViewModel(sessionEngine = engine)

        viewModel.load("session-1")
        advanceUntilIdle()
        viewModel.submitAnswer("Falso", isCorrect = true, latencyMs = 1200L)
        advanceUntilIdle()

        assertThat(engine.submitCalls).isEqualTo(0)

        viewModel.continueAfterFeedback()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.prompt?.item?.itemId).isEqualTo("item-2")
    }

    @Test
    fun `completed transition exits only after manual continue`() = runTest(dispatcher) {
        val currentPrompt = samplePrompt(sessionId = "session-1", itemId = "item-1", stem = "Pregunta 1", mode = SessionMode.QUICK)
        val completedSession = currentPrompt.session.copy(correctCount = 4)
        val viewModel =
            QuickStudyViewModel(
                sessionEngine =
                    FakeQuickSessionEngine(
                        resumedPrompt = currentPrompt,
                        submitTransition = SessionTransition.Completed(completedSession),
                    ),
            )

        viewModel.load("session-1")
        advanceUntilIdle()
        viewModel.submitAnswer("Falso", isCorrect = true, latencyMs = 1200L)
        advanceUntilIdle()

        val exitEffect = async { viewModel.effects.first() }
        viewModel.continueAfterFeedback()
        advanceUntilIdle()

        assertThat(exitEffect.await()).isEqualTo(QuickStudyEffect.ExitToHome)
    }

    @Test
    fun `continue after feedback exits to home instead of crashing when the session disappeared`() = runTest(dispatcher) {
        val currentPrompt = samplePrompt(sessionId = "session-1", itemId = "item-1", stem = "Pregunta 1", mode = SessionMode.QUICK)
        val engine =
            FakeQuickSessionEngine(
                resumedPrompt = currentPrompt,
                submitTransition = SessionTransition.Advanced(currentPrompt),
                submitError = IllegalArgumentException("No active session for id=session-1"),
            )
        val viewModel = QuickStudyViewModel(sessionEngine = engine)

        viewModel.load("session-1")
        advanceUntilIdle()
        viewModel.submitAnswer("Falso", isCorrect = true, latencyMs = 800L)
        advanceUntilIdle()

        val exitEffect = async { viewModel.effects.first() }
        viewModel.continueAfterFeedback()
        advanceUntilIdle()

        assertThat(exitEffect.await()).isEqualTo(QuickStudyEffect.ExitToHome)
    }

    @Test
    fun `back press exits to home instead of crashing when the session disappeared`() = runTest(dispatcher) {
        val currentPrompt = samplePrompt(sessionId = "session-1", itemId = "item-1", stem = "Pregunta 1", mode = SessionMode.QUICK)
        val engine =
            FakeQuickSessionEngine(
                resumedPrompt = currentPrompt,
                submitTransition = SessionTransition.Advanced(currentPrompt),
                backPressedError = IllegalArgumentException("No active session for id=session-1"),
            )
        val viewModel = QuickStudyViewModel(sessionEngine = engine)

        viewModel.load("session-1")
        advanceUntilIdle()

        val exitEffect = async { viewModel.effects.first() }
        viewModel.onBackPressed("session-1")
        advanceUntilIdle()

        assertThat(exitEffect.await()).isEqualTo(QuickStudyEffect.ExitToHome)
    }

    @Test
    fun `continue after feedback submits the pending answer only once for repeated taps`() = runTest(dispatcher) {
        val currentPrompt = samplePrompt(sessionId = "session-1", itemId = "item-1", stem = "Pregunta 1", mode = SessionMode.QUICK)
        val nextPrompt = samplePrompt(sessionId = "session-1", itemId = "item-2", stem = "Pregunta 2", mode = SessionMode.QUICK)
        val engine =
            FakeQuickSessionEngine(
                resumedPrompt = currentPrompt,
                submitTransition = SessionTransition.Advanced(nextPrompt),
            )
        val viewModel = QuickStudyViewModel(sessionEngine = engine)

        viewModel.load("session-1")
        advanceUntilIdle()
        viewModel.submitAnswer("Falso", isCorrect = true, latencyMs = 800L)
        advanceUntilIdle()

        viewModel.continueAfterFeedback()
        viewModel.continueAfterFeedback()
        advanceUntilIdle()

        assertThat(engine.submitCalls).isEqualTo(1)
    }

    @Test
    fun `reload keeps feedback for an answer that was not submitted yet`() = runTest(dispatcher) {
        val currentPrompt = samplePrompt(sessionId = "session-1", itemId = "item-1", stem = "Pregunta 1", mode = SessionMode.QUICK)
        val engine =
            FakeQuickSessionEngine(
                resumedPrompt = currentPrompt,
                submitTransition = SessionTransition.Advanced(currentPrompt),
            )
        val viewModel = QuickStudyViewModel(sessionEngine = engine)

        viewModel.load("session-1")
        advanceUntilIdle()
        viewModel.submitAnswer("Falso", isCorrect = true, latencyMs = 800L)
        advanceUntilIdle()

        viewModel.load("session-1")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.answerFeedback).isNotNull()
        assertThat(viewModel.uiState.value.pendingAnswer).isNotNull()
    }

    @Test
    fun `reload exits to home when the session no longer exists`() = runTest(dispatcher) {
        val currentPrompt = samplePrompt(sessionId = "session-1", itemId = "item-1", stem = "Pregunta 1", mode = SessionMode.QUICK)
        val engine =
            FakeQuickSessionEngine(
                resumedPrompt = currentPrompt,
                submitTransition = SessionTransition.Advanced(currentPrompt),
            )
        val viewModel = QuickStudyViewModel(sessionEngine = engine)

        viewModel.load("session-1")
        advanceUntilIdle()

        engine.resumedPrompt = null
        val exitEffect = async { viewModel.effects.first() }
        viewModel.load("session-1")
        advanceUntilIdle()

        assertThat(exitEffect.await()).isEqualTo(QuickStudyEffect.ExitToHome)
    }

    @Test
    fun `confirm terminate session exits to corresponding menu for deep mode`() = runTest(dispatcher) {
        val currentPrompt = samplePrompt(sessionId = "session-1", itemId = "item-1", stem = "Pregunta 1", mode = SessionMode.DEEP)
        val engine =
            FakeQuickSessionEngine(
                resumedPrompt = currentPrompt,
                submitTransition = SessionTransition.Advanced(currentPrompt),
                terminateResult = true,
            )
        val viewModel = QuickStudyViewModel(sessionEngine = engine)

        viewModel.load("session-1")
        advanceUntilIdle()

        val effect = async { viewModel.effects.first() }
        viewModel.requestTerminateSession()
        assertThat(viewModel.uiState.value.isTerminateSessionConfirmOpen).isTrue()

        viewModel.confirmTerminateSession()
        advanceUntilIdle()

        assertThat(engine.terminatedSessionIds).containsExactly("session-1")
        assertThat(effect.await()).isEqualTo(QuickStudyEffect.ExitToModeMenu(SessionMode.DEEP))
    }
}

private class FakeQuickSessionEngine(
    var resumedPrompt: StudyPrompt?,
    private val submitTransition: SessionTransition,
    private val terminateResult: Boolean = false,
    private val submitError: Throwable? = null,
    private val backPressedError: Throwable? = null,
) : SessionEngine {
    var submitCalls: Int = 0
    val terminatedSessionIds = mutableListOf<String>()

    override suspend fun startQuickSession(): StudyPrompt? = resumedPrompt

    override suspend fun startNotificationSession(
        preferredUnitId: String?,
        preferredItemId: String?,
    ): StudyPrompt? = resumedPrompt

    override suspend fun startSocialGateSession(goalCorrectCount: Int): StudyPrompt? = resumedPrompt

    override suspend fun startDeepSession(unitId: String): StudyPrompt? = resumedPrompt

    override suspend fun startDrainSession(unitId: String): StudyPrompt? = resumedPrompt

    override suspend fun terminateSession(sessionId: String): Boolean {
        terminatedSessionIds += sessionId
        return terminateResult
    }

    override suspend fun resumeSession(sessionId: String): StudyPrompt? = resumedPrompt

    override suspend fun submitAnswer(sessionId: String, answer: UserAnswer): SessionTransition {
        submitError?.let { throw it }
        submitCalls += 1
        return submitTransition
    }

    override suspend fun handleBackPressed(sessionId: String): BackPressResult {
        backPressedError?.let { throw it }
        return BackPressResult.Exit(sessionId)
    }

    override suspend fun recordAbandon(sessionId: String, reason: AbandonReason) = Unit

    override suspend fun handleInactivityTimeout(sessionId: String): BackPressResult = BackPressResult.Exit(sessionId)
}

private fun samplePrompt(
    sessionId: String,
    itemId: String,
    stem: String,
    mode: SessionMode,
): StudyPrompt {
    val session =
        StudySession(
            sessionId = sessionId,
            mode = mode,
            surface = if (mode == SessionMode.QUICK) Surface.IN_APP_QUICK else Surface.IN_APP_DEEP,
            packetId = "packet-1",
            topicUnitId = "unit-1",
            currentTopicTitle = "Tema",
            queueItemIds = emptyList(),
            rescueQueueItemIds = emptyList(),
            goalCorrectCount = 4,
            currentNodeId = "node-1",
            currentItemId = itemId,
            currentAttemptIndex = 1,
            correctCount = 0,
            stepIndex = 0,
            startedAt = 1L,
            lastInteractionAt = 1L,
            isMicroPromptActive = false,
            isExitArmed = false,
            exitArmedUntil = null,
        )

    return StudyPrompt(
        session = session,
        node =
            Node(
                nodeId = "node-1",
                courseId = "course-1",
                unitId = "unit-1",
                outcomeIds = listOf("outcome-1"),
                title = "Nodo",
                coreClaim = "Claim",
                type = NodeType.CONCEPT,
                weightExam = 0.7,
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
                itemId = itemId,
                nodeId = "node-1",
                facet = FacetType.DEFINICION_FUNCIONAL,
                format = ItemFormat.TRUE_FALSE,
                frictionLevel = 1,
                difficultySeed = 0.2,
                itemRole = ItemRole.CORE,
                allowedSurfaces = listOf(Surface.IN_APP_QUICK),
                cooldownHours = 1.0,
                stem = stem,
                correctAnswer = "Falso",
                feedbackShort = "Corrección",
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
                sourceRefs = listOf("manual"),
                contentOrigin = ContentOrigin.IMPORTED,
            ),
        promptIndex = 1,
    )
}
