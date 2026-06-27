package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.domain.repository.SessionRepository
import com.estudio.antiprocrastinacion.app.domain.repository.SocialGateRepository
import com.estudio.antiprocrastinacion.app.domain.session.BackPressResult
import com.estudio.antiprocrastinacion.app.domain.session.SessionEngine
import com.estudio.antiprocrastinacion.app.domain.session.SessionTransition
import com.estudio.antiprocrastinacion.app.model.content.FacetType
import com.estudio.antiprocrastinacion.app.model.content.Item
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.ContentOrigin
import com.estudio.antiprocrastinacion.app.model.content.Node
import com.estudio.antiprocrastinacion.app.model.content.NodeType
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.event.AbandonReason
import com.estudio.antiprocrastinacion.app.model.state.SocialGateDailyState
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRule
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRuntimePhase
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRuntimeState
import com.estudio.antiprocrastinacion.app.model.state.StudyPrompt
import com.estudio.antiprocrastinacion.app.model.state.StudySession
import com.estudio.antiprocrastinacion.app.model.state.UserAnswer
import com.estudio.antiprocrastinacion.app.socialgate.SocialGateCoordinator
import com.estudio.antiprocrastinacion.app.socialgate.SocialGateCoordinatorResult
import com.estudio.antiprocrastinacion.app.ui.common.TimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SocialGateCoordinatorTest {
    private val zoneId = ZoneId.of("UTC")

    @Test
    fun `target app redirects to study app when a non social gate session is already active and no gate is due`() = runTest {
        val socialGateRepository = FakeCoordinatorSocialGateRepository()
        val activeSession = sampleSocialGateSession("study-session", surface = Surface.IN_APP_QUICK, mode = SessionMode.QUICK)
        val sessionRepository = FakeCoordinatorSessionRepository(activeSession)
        val sessionEngine = FakeCoordinatorSessionEngine()
        val coordinator =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = sessionRepository,
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(utcTime("2026-04-14T00:00:00Z")), // Not due
                zoneId = zoneId,
                logger = {},
            )

        val result = coordinator.onTargetForegroundStable(sampleRule(windowStartMinutes = 8 * 60))

        assertThat(result).isEqualTo(SocialGateCoordinatorResult.OpenStudyAppAndHideOverlay("study-session"))
        assertThat(sessionEngine.startSocialGateCalls).isEqualTo(0)
        assertThat(socialGateRepository.runtimeState.phase).isEqualTo(SocialGateRuntimePhase.BLOCKING_EXISTING_SESSION)
        assertThat(socialGateRepository.runtimeState.targetPackageName).isEqualTo("com.instagram.android")
    }

    @Test
    fun `target app reuses existing active session when gate is due instead of silently replacing it`() = runTest {
        val socialGateRepository = FakeCoordinatorSocialGateRepository()
        val activeSession =
            sampleSocialGateSession(
                "study-session",
                surface = Surface.IN_APP_QUICK,
                mode = SessionMode.QUICK,
                goalCorrectCount = 12,
                correctCount = 0,
            )
        val sessionRepository = FakeCoordinatorSessionRepository(activeSession)
        val sessionEngine = FakeCoordinatorSessionEngine(resumePrompt = sampleSocialGatePrompt(activeSession))
        val coordinator =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = sessionRepository,
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(utcTime("2026-04-14T10:00:00Z")), // Due
                zoneId = zoneId,
                logger = {},
            )

        val result = coordinator.onTargetForegroundStable(sampleRule(requiredCorrectAnswers = 3))

        assertThat(result is SocialGateCoordinatorResult.ShowPrompt).isTrue()
        val prompt = (result as SocialGateCoordinatorResult.ShowPrompt).state
        assertThat(prompt.targetPackageName).isEqualTo("com.instagram.android")
        assertThat(prompt.prompt.session.sessionId).isEqualTo("study-session")
        assertThat(sessionEngine.resumeCalls).isEqualTo(1)
        assertThat(sessionEngine.startSocialGateCalls).isEqualTo(0)
        assertThat(socialGateRepository.runtimeState.phase).isEqualTo(SocialGateRuntimePhase.ACTIVE_GATE)
        assertThat(socialGateRepository.runtimeState.activeGateSessionId).isEqualTo("study-session")
        assertThat(socialGateRepository.runtimeState.gateUnlockBaselineCorrectCount).isEqualTo(0)
        // Modelo actual: el gate corre la sesión completa de pendientes y se desbloquea al terminarla,
        // así que el objetivo es goalCorrectCount - baseline (12 - 0), no rule.requiredCorrectAnswers.
        assertThat(socialGateRepository.runtimeState.gateUnlockRequiredCorrectAnswers).isEqualTo(12)
    }

    @Test
    fun `target app opens pending quick session when quota remains and no active session exists`() = runTest {
        val socialGateRepository = FakeCoordinatorSocialGateRepository()
        val sessionEngine =
            FakeCoordinatorSessionEngine(
                startPrompt =
                    sampleSocialGatePrompt(
                        sampleSocialGateSession(
                            "gate-session",
                            surface = Surface.IN_APP_QUICK,
                            mode = SessionMode.QUICK,
                            goalCorrectCount = 12,
                        ),
                    ),
            )
        val coordinator =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = FakeCoordinatorSessionRepository(null),
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(2_000L),
                zoneId = zoneId,
                logger = {},
            )

        val result = coordinator.onTargetForegroundStable(sampleRule(requiredCorrectAnswers = 4))

        assertThat(result is SocialGateCoordinatorResult.ShowPrompt).isTrue()
        val prompt = (result as SocialGateCoordinatorResult.ShowPrompt).state
        assertThat(prompt.prompt.session.sessionId).isEqualTo("gate-session")
        assertThat(prompt.targetPackageName).isEqualTo("com.instagram.android")
        assertThat(prompt.prompt.session.mode).isEqualTo(SessionMode.QUICK)
        assertThat(prompt.gateResolvedCount).isEqualTo(0)
        // El objetivo mostrado es terminar toda la sesión: goalCorrectCount - baseline (12 - 0).
        assertThat(prompt.gateRequiredCorrectAnswers).isEqualTo(12)
        assertThat(sessionEngine.startQuickCalls).isEqualTo(1)
        assertThat(sessionEngine.startSocialGateCalls).isEqualTo(0)
        assertThat(sessionEngine.lastStartGoalCorrectCount).isNull()
        assertThat(socialGateRepository.runtimeState.phase).isEqualTo(SocialGateRuntimePhase.ACTIVE_GATE)
        assertThat(socialGateRepository.runtimeState.activeGateSessionId).isEqualTo("gate-session")
    }

    @Test
    fun `gate due does not mount over an active deep session and opens pending quick instead`() = runTest {
        val socialGateRepository = FakeCoordinatorSocialGateRepository()
        // Hay una sesión de Modo profundo (DEEP) abierta. El gate NO debe retomarla ni reemplazarla; debe
        // abrir Tarjetas pendientes en su propio slot QUICK y dejar la sesión de estudio intacta.
        val deepSession =
            sampleSocialGateSession(
                "deep-session",
                surface = Surface.IN_APP_DEEP,
                mode = SessionMode.DEEP,
                goalCorrectCount = 12,
            )
        val sessionRepository = FakeCoordinatorSessionRepository(deepSession)
        val sessionEngine =
            FakeCoordinatorSessionEngine(
                startPrompt =
                    sampleSocialGatePrompt(
                        sampleSocialGateSession(
                            "gate-session",
                            surface = Surface.IN_APP_QUICK,
                            mode = SessionMode.QUICK,
                            goalCorrectCount = 12,
                        ),
                    ),
            )
        val coordinator =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = sessionRepository,
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(utcTime("2026-04-14T10:00:00Z")), // Due
                zoneId = zoneId,
                logger = {},
            )

        val result = coordinator.onTargetForegroundStable(sampleRule(requiredCorrectAnswers = 4))

        assertThat(result is SocialGateCoordinatorResult.ShowPrompt).isTrue()
        val prompt = (result as SocialGateCoordinatorResult.ShowPrompt).state
        assertThat(prompt.prompt.session.sessionId).isEqualTo("gate-session")
        assertThat(sessionEngine.startQuickCalls).isEqualTo(1)
        assertThat(sessionEngine.startSocialGateCalls).isEqualTo(0)
        assertThat(sessionEngine.resumeCalls).isEqualTo(0) // NO retoma la sesión DEEP
        // La sesión de estudio profundo sigue intacta en su propio slot.
        assertThat(sessionRepository.getActiveSession(SessionMode.DEEP)?.sessionId).isEqualTo("deep-session")
    }

    @Test
    fun `target app is allowed without starting gate when daily quota is exhausted`() = runTest {
        val socialGateRepository =
            FakeCoordinatorSocialGateRepository(
                dailyStates =
                    listOf(
                        SocialGateDailyState(
                            packageName = "com.instagram.android",
                            localDate = "2026-04-14",
                            solvedCount = 3,
                            lastSolvedAt = 500L,
                        ),
                    ),
            )
        val sessionEngine = FakeCoordinatorSessionEngine(startPrompt = sampleSocialGatePrompt("gate-session"))
        val coordinator =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = FakeCoordinatorSessionRepository(null),
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(utcTime("2026-04-14T00:00:00Z")),
                zoneId = zoneId,
                logger = {},
            )

        val result = coordinator.onTargetForegroundStable(sampleRule(maxTriggersPerDay = 3))

        assertThat(result).isEqualTo(SocialGateCoordinatorResult.Allowed)
        assertThat(sessionEngine.startSocialGateCalls).isEqualTo(0)
        assertThat(socialGateRepository.runtimeState.phase).isEqualTo(SocialGateRuntimePhase.IDLE)
    }

    @Test
    fun `target app is allowed before first configured slot of the day`() = runTest {
        val socialGateRepository = FakeCoordinatorSocialGateRepository()
        val sessionEngine = FakeCoordinatorSessionEngine(startPrompt = sampleSocialGatePrompt("gate-session"))
        val coordinator =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = FakeCoordinatorSessionRepository(null),
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(utcTime("2026-04-16T07:50:00Z")),
                zoneId = zoneId,
                logger = {},
            )

        val result =
            coordinator.onTargetForegroundStable(
                sampleRule(
                    maxTriggersPerDay = 3,
                    windowStartMinutes = 8 * 60,
                    windowEndMinutes = 22 * 60,
                ),
            )

        assertThat(result).isEqualTo(SocialGateCoordinatorResult.Allowed)
        assertThat(sessionEngine.startSocialGateCalls).isEqualTo(0)
    }

    @Test
    fun `target app is allowed after solving current slot until next scheduled slot`() = runTest {
        val socialGateRepository =
            FakeCoordinatorSocialGateRepository(
                dailyStates =
                    listOf(
                        SocialGateDailyState(
                            packageName = "com.instagram.android",
                            localDate = "2026-04-16",
                            solvedCount = 1,
                            lastSolvedAt = utcTime("2026-04-16T15:00:00Z"),
                        ),
                    ),
            )
        val sessionEngine = FakeCoordinatorSessionEngine(startPrompt = sampleSocialGatePrompt("gate-session"))
        val coordinator =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = FakeCoordinatorSessionRepository(null),
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(utcTime("2026-04-16T15:15:00Z")),
                zoneId = zoneId,
                logger = {},
            )

        val result =
            coordinator.onTargetForegroundStable(
                sampleRule(
                    maxTriggersPerDay = 3,
                    windowStartMinutes = 0,
                    windowEndMinutes = 22 * 60,
                ),
            )

        assertThat(result).isEqualTo(SocialGateCoordinatorResult.Allowed)
        assertThat(sessionEngine.startSocialGateCalls).isEqualTo(0)
    }

    @Test
    fun `target app is allowed when gate is due but there is no pending quick debt`() = runTest {
        val socialGateRepository = FakeCoordinatorSocialGateRepository()
        val sessionEngine = FakeCoordinatorSessionEngine(startPrompt = null)
        val coordinator =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = FakeCoordinatorSessionRepository(null),
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(utcTime("2026-04-20T02:10:00Z")),
                zoneId = zoneId,
                logger = {},
            )

        val result =
            coordinator.onTargetForegroundStable(
                sampleRule(
                    maxTriggersPerDay = 10,
                    windowStartMinutes = 2 * 60 + 9,
                    windowEndMinutes = 23 * 60,
                ),
            )

        assertThat(result).isEqualTo(SocialGateCoordinatorResult.Allowed)
        assertThat(sessionEngine.startQuickCalls).isEqualTo(1)
        assertThat(sessionEngine.startSocialGateCalls).isEqualTo(0)
        assertThat(socialGateRepository.runtimeState.phase).isEqualTo(SocialGateRuntimePhase.IDLE)
        assertThat(socialGateRepository.runtimeState.activeGateSessionId).isNull()
    }

    @Test
    fun `stale unlock token does not bypass a due gate when previous foreground was already lost`() = runTest {
        val socialGateRepository =
            FakeCoordinatorSocialGateRepository(
                runtimeState =
                    SocialGateRuntimeState(
                        phase = SocialGateRuntimePhase.UNLOCKED_FOR_CURRENT_FOREGROUND,
                        unlockTokenPackageName = "com.instagram.android",
                        unlockTokenIssuedAt = utcTime("2026-04-19T22:00:00Z"),
                        lastForegroundPackageName = "com.android.systemui",
                        lastForegroundChangedAt = utcTime("2026-04-19T22:10:00Z"),
                    ),
            )
        val sessionEngine =
            FakeCoordinatorSessionEngine(
                startPrompt =
                    sampleSocialGatePrompt(
                        sampleSocialGateSession(
                            "gate-session",
                            surface = Surface.IN_APP_QUICK,
                            mode = SessionMode.QUICK,
                            goalCorrectCount = 12,
                        ),
                    ),
            )
        val coordinator =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = FakeCoordinatorSessionRepository(null),
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(utcTime("2026-04-20T02:10:00Z")),
                zoneId = zoneId,
                logger = {},
            )

        val result =
            coordinator.onTargetForegroundStable(
                sampleRule(
                    maxTriggersPerDay = 10,
                    requiredCorrectAnswers = 4,
                    windowStartMinutes = 2 * 60 + 9,
                    windowEndMinutes = 23 * 60,
                ),
            )

        assertThat(result).isInstanceOf(SocialGateCoordinatorResult.ShowPrompt::class.java)
        assertThat(sessionEngine.startQuickCalls).isEqualTo(1)
        assertThat(sessionEngine.startSocialGateCalls).isEqualTo(0)
        assertThat(socialGateRepository.runtimeState.phase).isEqualTo(SocialGateRuntimePhase.ACTIVE_GATE)
        assertThat(socialGateRepository.runtimeState.unlockTokenPackageName).isNull()
    }

    @Test
    fun `unlock token is still honored while the same target app remains foreground`() = runTest {
        val socialGateRepository =
            FakeCoordinatorSocialGateRepository(
                runtimeState =
                    SocialGateRuntimeState(
                        phase = SocialGateRuntimePhase.UNLOCKED_FOR_CURRENT_FOREGROUND,
                        unlockTokenPackageName = "com.instagram.android",
                        unlockTokenIssuedAt = utcTime("2026-04-20T02:09:00Z"),
                        lastForegroundPackageName = "com.instagram.android",
                        lastForegroundChangedAt = utcTime("2026-04-20T02:09:00Z"),
                    ),
            )
        val sessionEngine = FakeCoordinatorSessionEngine()
        val coordinator =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = FakeCoordinatorSessionRepository(null),
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(utcTime("2026-04-20T02:10:00Z")),
                zoneId = zoneId,
                logger = {},
            )

        val result = coordinator.onTargetForegroundStable(sampleRule(windowStartMinutes = 2 * 60 + 9, windowEndMinutes = 23 * 60))

        assertThat(result).isEqualTo(SocialGateCoordinatorResult.Allowed)
        assertThat(sessionEngine.startSocialGateCalls).isEqualTo(0)
        assertThat(socialGateRepository.runtimeState.unlockTokenPackageName).isEqualTo("com.instagram.android")
    }

    @Test
    fun `existing social gate session is resumed instead of creating another one`() = runTest {
        val activeSession = sampleSocialGateSession("gate-session", surface = Surface.SOCIAL_GATE, mode = SessionMode.QUICK)
        val socialGateRepository =
            FakeCoordinatorSocialGateRepository(
                runtimeState = SocialGateRuntimeState(targetPackageName = "com.instagram.android"),
            )
        val sessionEngine = FakeCoordinatorSessionEngine(resumePrompt = sampleSocialGatePrompt("gate-session"))
        val coordinator =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = FakeCoordinatorSessionRepository(activeSession),
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(3_000L),
                zoneId = zoneId,
                logger = {},
            )

        val result = coordinator.onTargetForegroundStable(sampleRule())

        assertThat(result is SocialGateCoordinatorResult.ShowPrompt).isTrue()
        val prompt = (result as SocialGateCoordinatorResult.ShowPrompt).state
        assertThat(prompt.prompt.session.sessionId).isEqualTo("gate-session")
        assertThat(prompt.prompt.session.mode).isEqualTo(SessionMode.QUICK)
        assertThat(sessionEngine.resumeCalls).isEqualTo(1)
        assertThat(sessionEngine.startSocialGateCalls).isEqualTo(0)
        assertThat(socialGateRepository.runtimeState.phase).isEqualTo(SocialGateRuntimePhase.ACTIVE_GATE)
    }

    @Test
    fun `solving gate correctly shows feedback then unlocks on continue`() = runTest {
        val socialGateRepository = FakeCoordinatorSocialGateRepository()
        val gateSession =
            sampleSocialGateSession(
                "gate-session",
                surface = Surface.SOCIAL_GATE,
                mode = SessionMode.QUICK,
                goalCorrectCount = 12,
                correctCount = 2,
                stepIndex = 2,
                currentItemId = "item-3",
            )
        // Última pendiente: al responderla la sesión se completa → el gate se desbloquea.
        val completedSession =
            gateSession.copy(
                currentItemId = "item-4",
                correctCount = 3,
                stepIndex = 3,
            )
        val sessionRepository = FakeCoordinatorSessionRepository(null)
        val sessionEngine =
            FakeCoordinatorSessionEngine(
                startPrompt = sampleSocialGatePrompt(gateSession),
                submitTransition = SessionTransition.Completed(completedSession),
            )
        val coordinator =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = sessionRepository,
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(utcTime("2026-04-14T10:00:00Z")),
                zoneId = zoneId,
                logger = {},
            )

        coordinator.onTargetForegroundStable(sampleRule(requiredCorrectAnswers = 1))
        sessionRepository.activeSession = gateSession

        val result = coordinator.submitAnswer(responseText = "Verdadero", isCorrect = true, latencyMs = 800L)

        // Ahora muestra feedback (Correcto + comentario) antes de desbloquear, como en Pendientes.
        assertThat(result).isInstanceOf(SocialGateCoordinatorResult.ShowPrompt::class.java)
        val fb = (result as SocialGateCoordinatorResult.ShowPrompt).state
        assertThat(fb.answerFeedback?.isCorrect).isTrue()
        assertThat(fb.isPendingCompletion).isTrue()

        // Al confirmar el feedback ("Desbloquear") se desbloquea y redirige a estudiar.
        val unlocked = coordinator.continueAfterFeedback()
        assertThat(unlocked).isInstanceOf(SocialGateCoordinatorResult.OpenStudyAppAndHideOverlay::class.java)
        assertThat(socialGateRepository.runtimeState.phase).isEqualTo(SocialGateRuntimePhase.UNLOCKED_FOR_CURRENT_FOREGROUND)
        assertThat(sessionEngine.lastStartDrainUnitId).isNull()
    }

    @Test
    fun `correct answer shows feedback then advances on continue`() = runTest {
        val socialGateRepository = FakeCoordinatorSocialGateRepository()
        val startSession =
            sampleSocialGateSession(
                "gate-session",
                surface = Surface.SOCIAL_GATE,
                mode = SessionMode.QUICK,
                goalCorrectCount = 12,
                correctCount = 0,
                stepIndex = 0,
                currentItemId = "item-1",
            )
        val nextPrompt =
            sampleSocialGatePrompt(
                startSession.copy(
                    currentItemId = "item-2",
                    correctCount = 1,
                    stepIndex = 1,
                ),
                itemId = "item-2",
            )
        val sessionRepository = FakeCoordinatorSessionRepository(null)
        val sessionEngine =
            FakeCoordinatorSessionEngine(
                startPrompt = sampleSocialGatePrompt(startSession, itemId = "item-1"),
                submitTransition = SessionTransition.Advanced(nextPrompt),
            )
        val coordinator =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = sessionRepository,
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(utcTime("2026-04-14T10:00:00Z")),
                zoneId = zoneId,
                logger = {},
            )

        coordinator.onTargetForegroundStable(sampleRule(requiredCorrectAnswers = 2))
        sessionRepository.activeSession = startSession
        
        // Submit correct answer for the first question
        val result = coordinator.submitAnswer(responseText = "Verdadero", isCorrect = true, latencyMs = 800L)

        // Ahora muestra feedback (Correcto + comentario) en vez de auto-avanzar.
        assertThat(result is SocialGateCoordinatorResult.ShowPrompt).isTrue()
        val state = (result as SocialGateCoordinatorResult.ShowPrompt).state
        assertThat(state.answerFeedback?.isCorrect).isTrue()
        assertThat(state.revealAnswer).isTrue()
        assertThat(state.gateResolvedCount).isEqualTo(1)
        // Denominador = toda la sesión de pendientes (goalCorrectCount - baseline = 12 - 0).
        assertThat(state.gateRequiredCorrectAnswers).isEqualTo(12)

        // Al tocar "Seguir" avanza a la siguiente pregunta del gate.
        val next = coordinator.continueAfterFeedback()
        assertThat(next is SocialGateCoordinatorResult.ShowPrompt).isTrue()
        val nextState = (next as SocialGateCoordinatorResult.ShowPrompt).state
        assertThat(nextState.prompt.item.itemId).isEqualTo("item-2")
        assertThat(nextState.revealAnswer).isFalse()
        assertThat(nextState.answerFeedback).isNull()
    }

    @Test
    fun `answering correctly mid session does not unlock the app until the session completes`() = runTest {
        val socialGateRepository = FakeCoordinatorSocialGateRepository()
        val startSession =
            sampleSocialGateSession(
                "gate-session",
                surface = Surface.SOCIAL_GATE,
                mode = SessionMode.QUICK,
                goalCorrectCount = 12,
                correctCount = 1,
                stepIndex = 2,
                currentItemId = "item-3",
            )
        // The user already failed item-2: it waits in the correction queue for the end of the pass.
        val nextSession =
            startSession.copy(
                currentItemId = "item-4",
                correctCount = 2,
                stepIndex = 3,
                payload =
                    startSession.payload.copy(
                        correctionQueueItemIds = listOf("item-2"),
                        failCountsByItemId = mapOf("item-2" to 1),
                    ),
            )
        val sessionRepository = FakeCoordinatorSessionRepository(null)
        val sessionEngine =
            FakeCoordinatorSessionEngine(
                startPrompt = sampleSocialGatePrompt(startSession, itemId = "item-3"),
                submitTransition = SessionTransition.Advanced(sampleSocialGatePrompt(nextSession, itemId = "item-4")),
            )
        val coordinator =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = sessionRepository,
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(utcTime("2026-04-14T10:00:00Z")),
                zoneId = zoneId,
                logger = {},
            )

        coordinator.onTargetForegroundStable(sampleRule(requiredCorrectAnswers = 2))
        sessionRepository.activeSession = startSession

        // Aunque la respuesta sea correcta, la sesión sigue (Advanced): el gate NO se desbloquea hasta
        // que se completen todas las pendientes (Completed).
        val result = coordinator.submitAnswer(responseText = "Verdadero", isCorrect = true, latencyMs = 700L)

        assertThat(result).isInstanceOf(SocialGateCoordinatorResult.ShowPrompt::class.java)
        val state = (result as SocialGateCoordinatorResult.ShowPrompt).state
        assertThat(state.prompt.session.sessionId).isEqualTo("gate-session")
        assertThat(socialGateRepository.runtimeState.phase).isEqualTo(SocialGateRuntimePhase.ACTIVE_GATE)
        assertThat(socialGateRepository.runtimeState.unlockTokenPackageName).isNull()
    }

    @Test
    fun `incorrect answer still shows feedback and requires manual continuation`() = runTest {
        val socialGateRepository = FakeCoordinatorSocialGateRepository()
        val nextPrompt = sampleSocialGatePrompt("next-gate-session")
        val gateSession = sampleSocialGateSession("gate-session", surface = Surface.SOCIAL_GATE, mode = SessionMode.QUICK)
        val sessionRepository = FakeCoordinatorSessionRepository(null)
        val sessionEngine =
            FakeCoordinatorSessionEngine(
                startPrompt = sampleSocialGatePrompt(gateSession),
                submitTransition = SessionTransition.Advanced(nextPrompt),
            )
        val coordinator =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = sessionRepository,
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(utcTime("2026-04-14T10:00:00Z")),
                zoneId = zoneId,
                logger = {},
            )

        coordinator.onTargetForegroundStable(sampleRule(requiredCorrectAnswers = 2))
        sessionRepository.activeSession = gateSession
        
        // Submit INCORRECT answer
        val result = coordinator.submitAnswer(responseText = "Falso", isCorrect = false, latencyMs = 800L)

        // Should show FEEDBACK
        assertThat(result is SocialGateCoordinatorResult.ShowPrompt).isTrue()
        val state = (result as SocialGateCoordinatorResult.ShowPrompt).state
        assertThat(state.revealAnswer).isTrue()
        assertThat(state.answerFeedback?.isCorrect).isFalse()
        assertThat(state.pendingPrompt).isEqualTo(nextPrompt)
        assertThat(state.selectedResponseText).isEqualTo("Falso")
    }

    @Test
    fun `escape unlocks only current foreground and next gate shows one less escape`() = runTest {
        val socialGateRepository = FakeCoordinatorSocialGateRepository()
        val quickSession =
            sampleSocialGateSession(
                "quick-session",
                surface = Surface.IN_APP_QUICK,
                mode = SessionMode.QUICK,
                goalCorrectCount = 4,
            )
        val sessionEngine = FakeCoordinatorSessionEngine(startPrompt = sampleSocialGatePrompt(quickSession))
        val coordinator =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = FakeCoordinatorSessionRepository(null),
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(utcTime("2026-04-20T02:10:00Z")),
                zoneId = zoneId,
                logger = {},
            )
        val rule =
            sampleRule(
                maxTriggersPerDay = 10,
                windowStartMinutes = 2 * 60 + 9,
                windowEndMinutes = 23 * 60,
            )

        val firstGate = coordinator.onTargetForegroundStable(rule)
        assertThat(firstGate).isInstanceOf(SocialGateCoordinatorResult.ShowPrompt::class.java)
        assertThat((firstGate as SocialGateCoordinatorResult.ShowPrompt).state.escape.remaining).isEqualTo(1)

        val escaped = coordinator.useEscape()
        assertThat(escaped).isEqualTo(SocialGateCoordinatorResult.HideOverlay)
        assertThat(socialGateRepository.runtimeState.phase).isEqualTo(SocialGateRuntimePhase.UNLOCKED_FOR_CURRENT_FOREGROUND)
        assertThat(socialGateRepository.runtimeState.unlockTokenPackageName).isEqualTo("com.instagram.android")
        assertThat(socialGateRepository.runtimeState.escapeUsesAt).hasSize(1)

        assertThat(coordinator.onTargetForegroundStable(rule)).isEqualTo(SocialGateCoordinatorResult.Allowed)

        coordinator.onNonTargetForegroundStable("com.android.launcher")
        val secondGate = coordinator.onTargetForegroundStable(rule)

        assertThat(secondGate).isInstanceOf(SocialGateCoordinatorResult.ShowPrompt::class.java)
        val secondState = (secondGate as SocialGateCoordinatorResult.ShowPrompt).state
        assertThat(secondState.escape.canUse).isFalse()
        assertThat(secondState.escape.remaining).isEqualTo(0)
        assertThat(sessionEngine.startQuickCalls).isEqualTo(2)
        assertThat(sessionEngine.startSocialGateCalls).isEqualTo(0)
    }

    @Test
    fun `escape stays blocked when the device clock is rolled back after using it`() = runTest {
        val socialGateRepository = FakeCoordinatorSocialGateRepository()
        val quickSession =
            sampleSocialGateSession(
                "quick-session",
                surface = Surface.IN_APP_QUICK,
                mode = SessionMode.QUICK,
                goalCorrectCount = 4,
            )
        val sessionEngine = FakeCoordinatorSessionEngine(startPrompt = sampleSocialGatePrompt(quickSession))
        val rule = sampleRule(maxTriggersPerDay = 10, windowStartMinutes = 0, windowEndMinutes = 23 * 60)

        val useTime = utcTime("2026-04-20T12:00:00Z")
        val coordinatorAtUse =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = FakeCoordinatorSessionRepository(null),
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(useTime),
                zoneId = zoneId,
                logger = {},
            )

        coordinatorAtUse.onTargetForegroundStable(rule)
        assertThat(coordinatorAtUse.useEscape()).isEqualTo(SocialGateCoordinatorResult.HideOverlay)
        assertThat(socialGateRepository.runtimeState.escapeUsesAt).hasSize(1)
        coordinatorAtUse.onNonTargetForegroundStable("com.android.launcher")

        // El usuario atrasa el reloj 2 dias para intentar reabrir el comodin.
        val rolledBackTime = useTime - 2L * 24 * 60 * 60 * 1000
        val coordinatorRolledBack =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = FakeCoordinatorSessionRepository(null),
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(rolledBackTime),
                zoneId = zoneId,
                logger = {},
            )

        val gateAfterRollback = coordinatorRolledBack.onTargetForegroundStable(rule)
        assertThat(gateAfterRollback).isInstanceOf(SocialGateCoordinatorResult.ShowPrompt::class.java)
        val state = (gateAfterRollback as SocialGateCoordinatorResult.ShowPrompt).state
        assertThat(state.escape.canUse).isFalse()
        assertThat(state.escape.remaining).isEqualTo(0)
    }

    @Test
    fun `escape becomes available again after the weekly window passes`() = runTest {
        val socialGateRepository = FakeCoordinatorSocialGateRepository()
        val quickSession =
            sampleSocialGateSession(
                "quick-session",
                surface = Surface.IN_APP_QUICK,
                mode = SessionMode.QUICK,
                goalCorrectCount = 4,
            )
        val sessionEngine = FakeCoordinatorSessionEngine(startPrompt = sampleSocialGatePrompt(quickSession))
        val rule = sampleRule(maxTriggersPerDay = 10, windowStartMinutes = 0, windowEndMinutes = 23 * 60)

        val useTime = utcTime("2026-04-20T12:00:00Z")
        val coordinatorAtUse =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = FakeCoordinatorSessionRepository(null),
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(useTime),
                zoneId = zoneId,
                logger = {},
            )

        coordinatorAtUse.onTargetForegroundStable(rule)
        coordinatorAtUse.useEscape()
        coordinatorAtUse.onNonTargetForegroundStable("com.android.launcher")

        // Una semana mas tarde (la espera maxima) el comodin vuelve a estar disponible.
        val nextWeek = useTime + 7L * 24 * 60 * 60 * 1000 + 60_000
        val coordinatorNextWeek =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = FakeCoordinatorSessionRepository(null),
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(nextWeek),
                zoneId = zoneId,
                logger = {},
            )

        val gateNextWeek = coordinatorNextWeek.onTargetForegroundStable(rule)
        assertThat(gateNextWeek).isInstanceOf(SocialGateCoordinatorResult.ShowPrompt::class.java)
        val state = (gateNextWeek as SocialGateCoordinatorResult.ShowPrompt).state
        assertThat(state.escape.canUse).isTrue()
        assertThat(state.escape.remaining).isEqualTo(1)
    }

    @Test
    fun `existing gate runtime is restored with the same unlock threshold state`() = runTest {
        val activeSession =
            sampleSocialGateSession(
                "gate-session",
                surface = Surface.SOCIAL_GATE,
                mode = SessionMode.QUICK,
                goalCorrectCount = 12,
                correctCount = 2,
                stepIndex = 2,
                currentItemId = "item-3",
            )
        val socialGateRepository =
            FakeCoordinatorSocialGateRepository(
                runtimeState =
                    SocialGateRuntimeState(
                        phase = SocialGateRuntimePhase.ACTIVE_GATE,
                        targetPackageName = "com.instagram.android",
                        activeGateSessionId = "gate-session",
                        gateUnlockBaselineCorrectCount = 0,
                        gateUnlockRequiredCorrectAnswers = 3,
                    ),
            )
        val sessionEngine = FakeCoordinatorSessionEngine(resumePrompt = sampleSocialGatePrompt(activeSession))
        val coordinator =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = FakeCoordinatorSessionRepository(activeSession),
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(utcTime("2026-04-14T10:00:00Z")),
                zoneId = zoneId,
                logger = {},
            )

        val result = coordinator.onTargetForegroundStable(sampleRule(requiredCorrectAnswers = 3))

        assertThat(result).isInstanceOf(SocialGateCoordinatorResult.ShowPrompt::class.java)
        assertThat(sessionEngine.resumeCalls).isEqualTo(1)
        assertThat(socialGateRepository.runtimeState.activeGateSessionId).isEqualTo("gate-session")
        assertThat(socialGateRepository.runtimeState.gateUnlockBaselineCorrectCount).isEqualTo(0)
        assertThat(socialGateRepository.runtimeState.gateUnlockRequiredCorrectAnswers).isEqualTo(3)
    }

    @Test
    fun `stale active gate runtime is cleared and a fresh quick session starts when pending exists`() = runTest {
        val socialGateRepository =
            FakeCoordinatorSocialGateRepository(
                runtimeState =
                    SocialGateRuntimeState(
                        phase = SocialGateRuntimePhase.ACTIVE_GATE,
                        targetPackageName = "com.instagram.android",
                        activeGateSessionId = "missing-session",
                        gateUnlockBaselineCorrectCount = 1,
                        gateUnlockRequiredCorrectAnswers = 3,
                    ),
            )
        val newPrompt =
            sampleSocialGatePrompt(
                sampleSocialGateSession(
                    "gate-session",
                    surface = Surface.IN_APP_QUICK,
                    mode = SessionMode.QUICK,
                    goalCorrectCount = 12,
                ),
            )
        val sessionEngine = FakeCoordinatorSessionEngine(startPrompt = newPrompt)
        val coordinator =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = FakeCoordinatorSessionRepository(null),
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(utcTime("2026-04-20T02:10:00Z")),
                zoneId = zoneId,
                logger = {},
            )

        val result =
            coordinator.onTargetForegroundStable(
                sampleRule(
                    maxTriggersPerDay = 10,
                    windowStartMinutes = 2 * 60 + 9,
                    windowEndMinutes = 23 * 60,
                ),
            )

        assertThat(result).isInstanceOf(SocialGateCoordinatorResult.ShowPrompt::class.java)
        assertThat(sessionEngine.startQuickCalls).isEqualTo(1)
        assertThat(sessionEngine.startSocialGateCalls).isEqualTo(0)
        assertThat(sessionEngine.resumeCalls).isEqualTo(0)
        assertThat(socialGateRepository.runtimeState.phase).isEqualTo(SocialGateRuntimePhase.ACTIVE_GATE)
        assertThat(socialGateRepository.runtimeState.activeGateSessionId).isEqualTo("gate-session")
    }

    @Test
    fun `submit answer hides overlay and clears stale gate runtime when active session disappeared`() = runTest {
        val socialGateRepository = FakeCoordinatorSocialGateRepository()
        val gateSession =
            sampleSocialGateSession(
                "gate-session",
                surface = Surface.SOCIAL_GATE,
                mode = SessionMode.QUICK,
                goalCorrectCount = 12,
            )
        val sessionRepository = FakeCoordinatorSessionRepository(null)
        val sessionEngine =
            FakeCoordinatorSessionEngine(
                startPrompt = sampleSocialGatePrompt(gateSession),
                submitTransition = SessionTransition.Advanced(sampleSocialGatePrompt(gateSession.copy(stepIndex = 1, correctCount = 1))),
            )
        val coordinator =
            SocialGateCoordinator(
                socialGateRepository = socialGateRepository,
                sessionRepository = sessionRepository,
                sessionEngine = sessionEngine,
                timeProvider = FixedCoordinatorTimeProvider(utcTime("2026-04-20T02:10:00Z")),
                zoneId = zoneId,
                logger = {},
            )

        coordinator.onTargetForegroundStable(
            sampleRule(
                maxTriggersPerDay = 10,
                windowStartMinutes = 2 * 60 + 9,
                windowEndMinutes = 23 * 60,
            ),
        )
        sessionRepository.activeSession = null

        val result = coordinator.submitAnswer(responseText = "Verdadero", isCorrect = true, latencyMs = 500L)

        assertThat(result).isEqualTo(SocialGateCoordinatorResult.HideOverlay)
        assertThat(sessionEngine.lastSubmittedAnswer).isNull()
        assertThat(socialGateRepository.runtimeState.phase).isEqualTo(SocialGateRuntimePhase.IDLE)
        assertThat(socialGateRepository.runtimeState.activeGateSessionId).isNull()
    }
}



private class FakeCoordinatorSocialGateRepository(
    initialRules: List<SocialGateRule> = emptyList(),
    dailyStates: List<SocialGateDailyState> = emptyList(),
    runtimeState: SocialGateRuntimeState = SocialGateRuntimeState(),
) : SocialGateRepository {
    private val rulesFlow = MutableStateFlow(initialRules)
    var dailyStates: List<SocialGateDailyState> = dailyStates
    var runtimeState: SocialGateRuntimeState = runtimeState

    override fun observeRules(): Flow<List<SocialGateRule>> = rulesFlow

    override suspend fun getRules(): List<SocialGateRule> = rulesFlow.value

    override suspend fun getRule(packageName: String): SocialGateRule? =
        rulesFlow.value.firstOrNull { it.packageName == packageName }

    override suspend fun upsertRule(rule: SocialGateRule) {
        rulesFlow.value =
            rulesFlow.value
                .filterNot { it.packageName == rule.packageName }
                .plus(rule)
                .sortedBy { it.packageName }
    }

    override suspend fun getDailyStates(): List<SocialGateDailyState> = dailyStates

    override suspend fun incrementSolvedCount(packageName: String, localDate: String, solvedAt: Long) {
        val current = dailyStates.firstOrNull { it.packageName == packageName && it.localDate == localDate }
        dailyStates =
            dailyStates
                .filterNot { it.packageName == packageName && it.localDate == localDate }
                .plus(
                    SocialGateDailyState(
                        packageName = packageName,
                        localDate = localDate,
                        solvedCount = (current?.solvedCount ?: 0) + 1,
                        lastSolvedAt = solvedAt,
                    ),
                )
                .sortedBy { it.packageName + it.localDate }
    }

    override suspend fun getRuntimeState(): SocialGateRuntimeState = runtimeState

    override suspend fun updateRuntimeState(transform: (SocialGateRuntimeState) -> SocialGateRuntimeState) {
        runtimeState = transform(runtimeState)
    }

    override suspend fun replaceState(
        rules: List<SocialGateRule>,
        dailyStates: List<SocialGateDailyState>,
        runtimeState: SocialGateRuntimeState,
    ) {
        rulesFlow.value = rules
        this.dailyStates = dailyStates
        this.runtimeState = runtimeState
    }
}

private class FakeCoordinatorSessionRepository(
    var activeSession: StudySession?,
) : SessionRepository {
    override suspend fun getActiveSession(mode: SessionMode): StudySession? =
        activeSession?.takeIf { it.mode == mode }

    override suspend fun getActiveSessionById(sessionId: String): StudySession? =
        activeSession?.takeIf { it.sessionId == sessionId }

    override suspend fun getAllActiveSessions(): List<StudySession> = listOfNotNull(activeSession)

    override suspend fun saveActiveSession(session: StudySession) {
        activeSession = session
    }

    override suspend fun clearActiveSession(mode: SessionMode) {
        activeSession = activeSession?.takeIf { it.mode != mode }
    }

    override suspend fun clearAllActiveSessions() {
        activeSession = null
    }
}

private class FakeCoordinatorSessionEngine(
    private val startPrompt: StudyPrompt? = null,
    private val resumePrompt: StudyPrompt? = null,
    private val submitTransition: SessionTransition = SessionTransition.Completed(sampleSocialGateSession("completed", surface = Surface.SOCIAL_GATE, mode = SessionMode.QUICK)),
    private val startQuickPrompt: StudyPrompt? = null,
    var startDrainPrompt: StudyPrompt? = null,
) : SessionEngine {
    var startQuickCalls: Int = 0
    var startSocialGateCalls: Int = 0
    var resumeCalls: Int = 0
    var lastStartGoalCorrectCount: Int? = null
    var lastSubmittedAnswer: UserAnswer? = null

    override suspend fun startQuickSession(): StudyPrompt? {
        startQuickCalls += 1
        return startQuickPrompt ?: startPrompt
    }
    override suspend fun startNotificationSession(
        preferredUnitId: String?,
        preferredItemId: String?,
    ): StudyPrompt? = null

    override suspend fun startSocialGateSession(goalCorrectCount: Int): StudyPrompt? {
        startSocialGateCalls += 1
        lastStartGoalCorrectCount = goalCorrectCount
        return startPrompt
    }

    override suspend fun startDeepSession(unitId: String): StudyPrompt? = null

    var lastStartDrainUnitId: String? = null

    override suspend fun startDrainSession(unitId: String): StudyPrompt? {
        lastStartDrainUnitId = unitId
        return startDrainPrompt
    }

    override suspend fun terminateSession(sessionId: String): Boolean = false

    override suspend fun resumeSession(sessionId: String): StudyPrompt? {
        resumeCalls += 1
        return resumePrompt?.takeIf { it.session.sessionId == sessionId }
    }

    override suspend fun submitAnswer(sessionId: String, answer: UserAnswer): SessionTransition {
        lastSubmittedAnswer = answer
        return submitTransition
    }

    override suspend fun handleBackPressed(sessionId: String): BackPressResult =
        BackPressResult.Exit(sessionId)

    override suspend fun recordAbandon(sessionId: String, reason: AbandonReason) = Unit

    override suspend fun handleInactivityTimeout(sessionId: String): BackPressResult =
        BackPressResult.Exit(sessionId)
}

private class FixedCoordinatorTimeProvider(
    private val now: Long,
) : TimeProvider {
    override fun now(): Long = now
}

private fun utcTime(instant: String): Long = java.time.Instant.parse(instant).toEpochMilli()

private fun sampleRule(
    packageName: String = "com.instagram.android",
    displayName: String = "Instagram",
    maxTriggersPerDay: Int = 3,
    requiredCorrectAnswers: Int = 3,
    windowStartMinutes: Int = 0,
    windowEndMinutes: Int = 22 * 60,
): SocialGateRule =
    SocialGateRule(
        packageName = packageName,
        displayName = displayName,
        enabled = true,
        maxTriggersPerDay = maxTriggersPerDay,
        requiredCorrectAnswers = requiredCorrectAnswers,
        windowStartMinutes = windowStartMinutes,
        windowEndMinutes = windowEndMinutes,
    )

private fun sampleSocialGatePrompt(
    session: StudySession,
    itemId: String = session.currentItemId ?: "item-gate",
): StudyPrompt {
    val node =
        Node(
            nodeId = "node-gate",
            courseId = "course-1",
            unitId = "unit-1",
            outcomeIds = listOf("outcome-1"),
            title = "Tema gate",
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
        )
    val item =
        Item(
            itemId = itemId,
            nodeId = node.nodeId,
            facet = FacetType.DEFINICION_FUNCIONAL,
            format = ItemFormat.TRUE_FALSE,
            frictionLevel = 1,
            difficultySeed = 0.2,
            itemRole = ItemRole.CORE,
            allowedSurfaces = listOf(Surface.SOCIAL_GATE, Surface.IN_APP_QUICK, Surface.BACK_MICRO),
            cooldownHours = 4.0,
            stem = "Ethereum usa checkpoints.",
            correctAnswer = "Verdadero",
            feedbackShort = "Checkpoint referencia progreso del consenso.",
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
        )
    return StudyPrompt(session = session, node = node, item = item, promptIndex = 0)
}

private fun sampleSocialGatePrompt(sessionId: String): StudyPrompt =
    sampleSocialGatePrompt(sampleSocialGateSession(sessionId, surface = Surface.SOCIAL_GATE, mode = SessionMode.QUICK))

private fun sampleSocialGateSession(
    sessionId: String,
    surface: Surface,
    mode: SessionMode,
    goalCorrectCount: Int = 2,
    correctCount: Int = 0,
    stepIndex: Int = 0,
    currentItemId: String = "item-gate",
): StudySession =
    StudySession(
        sessionId = sessionId,
        mode = mode,
        surface = surface,
        packetId = "packet-gate",
        topicUnitId = "unit-1",
        currentTopicTitle = "Tema gate",
        queueItemIds = emptyList(),
        rescueQueueItemIds = emptyList(),
        goalCorrectCount = goalCorrectCount,
        currentNodeId = "node-gate",
        currentItemId = currentItemId,
        currentAttemptIndex = 1,
        correctCount = correctCount,
        stepIndex = stepIndex,
        startedAt = 1L,
        lastInteractionAt = 1L,
        isMicroPromptActive = false,
        isExitArmed = false,
        exitArmedUntil = null,
    )
