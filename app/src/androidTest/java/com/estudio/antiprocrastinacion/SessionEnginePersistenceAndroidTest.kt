package com.estudio.antiprocrastinacion

import android.net.Uri
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.estudio.antiprocrastinacion.app.data.local.db.LocalEventRepository
import com.estudio.antiprocrastinacion.app.data.local.db.LocalProgressRepository
import com.estudio.antiprocrastinacion.app.data.local.db.LocalSessionRepository
import com.estudio.antiprocrastinacion.app.data.local.db.StudyDatabase
import com.estudio.antiprocrastinacion.app.domain.repository.ContentRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ImportExecutionResult
import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidationProfile
import com.estudio.antiprocrastinacion.app.domain.repository.ProgressRepository
import com.estudio.antiprocrastinacion.app.domain.repository.SettingsRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationReport
import com.estudio.antiprocrastinacion.app.domain.scheduler.DeepModeUnitOption
import com.estudio.antiprocrastinacion.app.domain.scheduler.SchedulerService
import com.estudio.antiprocrastinacion.app.domain.session.BackPressResult
import com.estudio.antiprocrastinacion.app.domain.session.DefaultSessionEngine
import com.estudio.antiprocrastinacion.app.domain.session.ItemStateUpdater
import com.estudio.antiprocrastinacion.app.domain.session.NodeStateUpdater
import com.estudio.antiprocrastinacion.app.domain.session.SessionTransition
import com.estudio.antiprocrastinacion.app.model.content.ContentOrigin
import com.estudio.antiprocrastinacion.app.model.content.ContentTree
import com.estudio.antiprocrastinacion.app.model.content.FacetType
import com.estudio.antiprocrastinacion.app.model.content.Item
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemOption
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.Node
import com.estudio.antiprocrastinacion.app.model.content.NodeDetail
import com.estudio.antiprocrastinacion.app.model.content.NodeType
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.content.UnitModel
import com.estudio.antiprocrastinacion.app.model.event.AbandonReason
import com.estudio.antiprocrastinacion.app.model.event.AttemptOutcome
import com.estudio.antiprocrastinacion.app.model.event.ReviewEvent
import com.estudio.antiprocrastinacion.app.model.state.AppSettings
import com.estudio.antiprocrastinacion.app.model.state.NodeState
import com.estudio.antiprocrastinacion.app.model.state.TopicPacket
import com.estudio.antiprocrastinacion.app.model.state.UserAnswer
import com.estudio.antiprocrastinacion.app.ui.common.IdProvider
import com.estudio.antiprocrastinacion.app.ui.common.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionEnginePersistenceAndroidTest {
    private lateinit var database: StudyDatabase
    private lateinit var contentRepository: EngineContentRepository
    private lateinit var progressRepository: ProgressRepository
    private lateinit var sessionRepository: LocalSessionRepository
    private lateinit var eventRepository: LocalEventRepository
    private lateinit var schedulerService: FakeSchedulerService
    private lateinit var settingsRepository: FixedSettingsRepository
    private lateinit var timeProvider: MutableTimeProvider
    private lateinit var engine: DefaultSessionEngine

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StudyDatabase::class.java).build()
        contentRepository = EngineContentRepository(fixtures = baseFixtures())
        progressRepository = LocalProgressRepository(database.nodeStateDao())
        timeProvider = MutableTimeProvider(currentTime = 1_000L)
        sessionRepository = LocalSessionRepository(database.sessionDao(), timeProvider)
        eventRepository = LocalEventRepository(database.eventDao())
        schedulerService = FakeSchedulerService()
        settingsRepository = FixedSettingsRepository(AppSettings(backExitWindowSeconds = 8))
        engine =
            DefaultSessionEngine(
                schedulerService = schedulerService,
                contentRepository = contentRepository,
                progressRepository = progressRepository,
                sessionRepository = sessionRepository,
                eventRepository = eventRepository,
                settingsRepository = settingsRepository,
                database = database,
                nodeStateUpdater = NodeStateUpdater(),
                itemStateUpdater = ItemStateUpdater(),
                idProvider = SequentialIdProvider(),
                timeProvider = timeProvider,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deepSession_persistsExactActiveSession_and_resumeRestoresSamePrompt() = runBlocking {
        seedSeenItems("deep-main", "deep-next")
        schedulerService.deepPacket =
            packet(
                packetId = "packet-deep",
                topicUnitId = "unit-deep",
                topicTitle = "Checkpoints y Finality",
                surface = Surface.IN_APP_DEEP,
                mode = SessionMode.DEEP,
                initialItemIds = listOf("deep-main", "deep-next"),
                rescueItemIds = listOf("deep-rescue"),
                goalCorrectCount = 2,
            )

        val started = engine.startDeepSession("unit-deep")
        assertNotNull(started)

        val persisted = sessionRepository.getActiveSession()
        assertNotNull(persisted)
        persisted!!
        assertEquals(SessionMode.DEEP, persisted.mode)
        assertEquals(Surface.IN_APP_DEEP, persisted.surface)
        assertEquals("packet-deep", persisted.packetId)
        assertEquals("unit-deep", persisted.topicUnitId)
        assertEquals("Checkpoints y Finality", persisted.currentTopicTitle)
        assertEquals(listOf("deep-next"), persisted.queueItemIds)
        assertEquals(listOf("deep-rescue"), persisted.rescueQueueItemIds)
        assertEquals(2, persisted.goalCorrectCount)
        assertEquals("node-deep", persisted.currentNodeId)
        assertEquals("deep-main", persisted.currentItemId)
        assertEquals(0, persisted.correctCount)
        assertEquals(0, persisted.stepIndex)

        timeProvider.currentTime = 2_000L
        val resumed = engine.resumeSession(persisted.sessionId)
        assertNotNull(resumed)
        resumed!!
        assertEquals(persisted.sessionId, resumed.session.sessionId)
        assertEquals("deep-main", resumed.item.itemId)
        assertEquals("node-deep", resumed.node.nodeId)
        assertEquals(1, resumed.promptIndex)

        assertEquals(listOf("unit-deep", "unit-deep"), eventRepository.getRecentTopicIds())
    }

    @Test
    fun drainSession_keepsDeepSessionIsolated_and_updates_recentTopics() = runBlocking {
        seedSeenItems("deep-main", "deep-next", "drain-main", "drain-next", "drain-boss")
        schedulerService.deepPacket =
            packet(
                packetId = "packet-deep",
                topicUnitId = "unit-deep",
                topicTitle = "Deep Unit",
                surface = Surface.IN_APP_DEEP,
                mode = SessionMode.DEEP,
                initialItemIds = listOf("deep-main", "deep-next"),
                rescueItemIds = listOf("deep-rescue"),
                goalCorrectCount = 2,
            )
        schedulerService.drainPacket =
            packet(
                packetId = "packet-drain",
                topicUnitId = "unit-drain",
                topicTitle = "Drain Unit",
                surface = Surface.IN_APP_DEEP,
                mode = SessionMode.DRAIN,
                initialItemIds = listOf("drain-main", "drain-next", "drain-boss"),
                rescueItemIds = emptyList(),
                goalCorrectCount = 3,
            )

        val deepStarted = engine.startDeepSession("unit-deep")
        assertNotNull(deepStarted)
        val deepSessionId = sessionRepository.getActiveSession(SessionMode.DEEP)!!.sessionId

        timeProvider.currentTime = 2_000L
        val drainStarted = engine.startDrainSession("unit-drain")
        assertNotNull(drainStarted)

        val persistedDrain = sessionRepository.getActiveSession(SessionMode.DRAIN)
        assertNotNull(persistedDrain)
        persistedDrain!!
        assertNotEquals(deepSessionId, persistedDrain.sessionId)
        assertEquals(SessionMode.DRAIN, persistedDrain.mode)
        assertEquals("packet-drain", persistedDrain.packetId)
        assertEquals("drain-main", persistedDrain.currentItemId)
        assertEquals(listOf("drain-next", "drain-boss"), persistedDrain.queueItemIds)
        assertEquals(emptyList<String>(), persistedDrain.rescueQueueItemIds)
        assertEquals(deepSessionId, sessionRepository.getActiveSession(SessionMode.DEEP)?.sessionId)
        assertEquals(listOf("unit-drain", "unit-deep"), eventRepository.getRecentTopicIds())
    }

    @Test
    fun deepSession_wrongMainItem_requeuesThatItem_toEndOfSameSession() = runBlocking {
        schedulerService.deepPacket =
            packet(
                packetId = "packet-deep",
                topicUnitId = "unit-deep",
                topicTitle = "Deep Unit",
                surface = Surface.IN_APP_DEEP,
                mode = SessionMode.DEEP,
                initialItemIds = listOf("deep-main", "deep-next"),
                rescueItemIds = listOf("deep-rescue"),
                goalCorrectCount = 2,
            )

        engine.startDeepSession("unit-deep")
        val sessionId = sessionRepository.getActiveSession()!!.sessionId

        timeProvider.currentTime = 2_000L
        val afterError =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "wrong", isCorrect = false, latencyMs = 1_200L),
            )
        assertTrue(afterError is SessionTransition.Advanced)
        afterError as SessionTransition.Advanced
        assertEquals("deep-rescue", afterError.prompt.item.itemId)
        assertEquals(2, afterError.prompt.session.currentAttemptIndex)
        assertEquals(listOf("deep-next", "deep-main"), afterError.prompt.session.queueItemIds)
        assertEquals(1, afterError.prompt.pendingFailedItemCount)

        timeProvider.currentTime = 3_000L
        val afterRescue =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 900L),
            )
        assertTrue(afterRescue is SessionTransition.Advanced)
        afterRescue as SessionTransition.Advanced
        assertEquals("deep-next", afterRescue.prompt.item.itemId)
        assertEquals(0, afterRescue.prompt.session.correctCount)
        assertEquals(1, afterRescue.prompt.session.stepIndex)
        assertEquals(listOf("deep-main"), afterRescue.prompt.session.queueItemIds)

        timeProvider.currentTime = 4_000L
        val afterSecondBaseCorrect =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 850L),
            )
        assertTrue(afterSecondBaseCorrect is SessionTransition.Advanced)
        afterSecondBaseCorrect as SessionTransition.Advanced
        assertEquals("deep-main", afterSecondBaseCorrect.prompt.item.itemId)
        assertEquals(1, afterSecondBaseCorrect.prompt.session.correctCount)
        assertEquals(2, afterSecondBaseCorrect.prompt.session.stepIndex)
        assertEquals(listOf("deep-next"), afterSecondBaseCorrect.prompt.session.queueItemIds)
        assertEquals(1, afterSecondBaseCorrect.prompt.pendingFailedItemCount)

        timeProvider.currentTime = 5_000L
        val afterRequeuedMainCorrect =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 800L),
            )
        assertTrue(afterRequeuedMainCorrect is SessionTransition.Advanced)
        afterRequeuedMainCorrect as SessionTransition.Advanced
        assertEquals("deep-next", afterRequeuedMainCorrect.prompt.item.itemId)
        assertEquals(listOf("deep-main"), afterRequeuedMainCorrect.prompt.session.queueItemIds)
        assertEquals(2, afterRequeuedMainCorrect.prompt.session.correctCount)
        assertEquals(0, afterRequeuedMainCorrect.prompt.pendingFailedItemCount)

        timeProvider.currentTime = 6_000L
        val afterSecondDeepNextCorrect =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 780L),
            )
        assertTrue(afterSecondDeepNextCorrect is SessionTransition.Advanced)
        afterSecondDeepNextCorrect as SessionTransition.Advanced
        assertEquals("deep-main", afterSecondDeepNextCorrect.prompt.item.itemId)
        assertEquals(emptyList<String>(), afterSecondDeepNextCorrect.prompt.session.queueItemIds)
        assertEquals(3, afterSecondDeepNextCorrect.prompt.session.correctCount)

        timeProvider.currentTime = 7_000L
        val completed =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 760L),
            )
        assertTrue(completed is SessionTransition.Completed)
    }

    @Test
    fun drainSession_wrongItem_staysOpen_untilRequeuedItemIsAnsweredCorrectly() = runBlocking {
        seedSeenItems("drain-main", "drain-next", "drain-boss")
        schedulerService.drainPacket =
            packet(
                packetId = "packet-drain",
                topicUnitId = "unit-drain",
                topicTitle = "Drain Unit",
                surface = Surface.IN_APP_DEEP,
                mode = SessionMode.DRAIN,
                initialItemIds = listOf("drain-main", "drain-next", "drain-boss"),
                rescueItemIds = emptyList(),
                goalCorrectCount = 3,
            )

        engine.startDrainSession("unit-drain")
        val sessionId = sessionRepository.getActiveSession()!!.sessionId

        timeProvider.currentTime = 2_000L
        val afterError =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "wrong", isCorrect = false, latencyMs = 1_100L),
            )
        assertTrue(afterError is SessionTransition.Advanced)
        afterError as SessionTransition.Advanced
        assertEquals("drain-next", afterError.prompt.item.itemId)
        assertEquals(2, afterError.prompt.session.currentAttemptIndex)
        assertEquals(listOf("drain-next", "drain-boss", "drain-main"), afterError.prompt.session.queueItemIds)

        timeProvider.currentTime = 3_000L
        val afterFallbackCorrect =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 900L),
            )
        assertTrue(afterFallbackCorrect is SessionTransition.Advanced)
        afterFallbackCorrect as SessionTransition.Advanced
        assertEquals("drain-next", afterFallbackCorrect.prompt.item.itemId)
        assertEquals(0, afterFallbackCorrect.prompt.session.correctCount)
        assertEquals(listOf("drain-boss", "drain-main"), afterFallbackCorrect.prompt.session.queueItemIds)

        timeProvider.currentTime = 4_000L
        val afterScheduledNext =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 800L),
            )
        assertTrue(afterScheduledNext is SessionTransition.Advanced)
        afterScheduledNext as SessionTransition.Advanced
        assertEquals("drain-boss", afterScheduledNext.prompt.item.itemId)
        assertEquals(1, afterScheduledNext.prompt.session.correctCount)
        assertEquals(listOf("drain-main"), afterScheduledNext.prompt.session.queueItemIds)

        timeProvider.currentTime = 5_000L
        val afterBoss =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 780L),
            )
        assertTrue(afterBoss is SessionTransition.Advanced)
        afterBoss as SessionTransition.Advanced
        assertEquals("drain-main", afterBoss.prompt.item.itemId)
        assertEquals(2, afterBoss.prompt.session.correctCount)
        assertEquals(emptyList<String>(), afterBoss.prompt.session.queueItemIds)

        timeProvider.currentTime = 6_000L
        val completed =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 760L),
            )
        assertTrue(completed is SessionTransition.Completed)
        assertNull(sessionRepository.getActiveSession())
    }

    @Test
    fun socialGateContinuation_allowsDeepOnlyVariantRetry_afterUnlock() = runBlocking {
        schedulerService.quickPacket =
            packet(
                packetId = "packet-social",
                topicUnitId = "unit-social",
                topicTitle = "Social Unit",
                surface = Surface.SOCIAL_GATE,
                mode = SessionMode.DRAIN,
                initialItemIds = listOf("social-main", "social-next"),
                rescueItemIds = listOf("social-rescue"),
                goalCorrectCount = 2,
            )

        val started = engine.startQuickSession()
        assertNotNull(started)
        val sessionId = sessionRepository.getActiveSession()!!.sessionId

        timeProvider.currentTime = 2_000L
        val result =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "incorrect", isCorrect = false, latencyMs = 900L),
            )

        assertTrue(result is SessionTransition.Advanced)
        result as SessionTransition.Advanced
        assertEquals("social-variant", result.prompt.item.itemId)
        assertEquals(Surface.SOCIAL_GATE, result.prompt.session.surface)
    }

    @Test
    fun terminateDeepSession_clearsOnlyDeepSector_and_recordsExitEvent() = runBlocking {
        seedSeenItems("quick-main", "quick-next", "deep-main", "deep-next")
        schedulerService.quickPacket =
            packet(
                packetId = "packet-quick",
                topicUnitId = "unit-quick",
                topicTitle = "Quick Unit",
                surface = Surface.IN_APP_QUICK,
                mode = SessionMode.QUICK,
                initialItemIds = listOf("quick-main", "quick-next"),
                rescueItemIds = listOf("quick-rescue"),
                goalCorrectCount = 2,
            )
        schedulerService.deepPacket =
            packet(
                packetId = "packet-deep",
                topicUnitId = "unit-deep",
                topicTitle = "Deep Unit",
                surface = Surface.IN_APP_DEEP,
                mode = SessionMode.DEEP,
                initialItemIds = listOf("deep-main", "deep-next"),
                rescueItemIds = listOf("deep-rescue"),
                goalCorrectCount = 2,
            )

        assertNotNull(engine.startQuickSession())
        assertNotNull(engine.startDeepSession("unit-deep"))
        val quickSessionId = sessionRepository.getActiveSession(SessionMode.QUICK)!!.sessionId
        val deepSessionId = sessionRepository.getActiveSession(SessionMode.DEEP)!!.sessionId

        timeProvider.currentTime = 2_000L
        val terminated = engine.terminateSession(deepSessionId)

        assertTrue(terminated)
        assertNull(sessionRepository.getActiveSession(SessionMode.DEEP))
        assertEquals(quickSessionId, sessionRepository.getActiveSession(SessionMode.QUICK)?.sessionId)
        val recentTopics = eventRepository.getRecentTopicIds()
        assertEquals(listOf("unit-deep", "unit-deep", "unit-quick"), recentTopics)
        database.query(
            SimpleSQLiteQuery(
                "SELECT COUNT(*) FROM session_events WHERE sessionId = ? AND type = 'SESSION_EXITED'",
                arrayOf(deepSessionId),
            ),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    fun microPromptExit_keeps_resumable_active_session_without_abandon_event() = runBlocking {
        seedSeenItems("quick-main", "quick-next")
        schedulerService.quickPacket =
            packet(
                packetId = "packet-quick",
                topicUnitId = "unit-quick",
                topicTitle = "Quick Unit",
                surface = Surface.IN_APP_QUICK,
                mode = SessionMode.QUICK,
                initialItemIds = listOf("quick-main", "quick-next"),
                rescueItemIds = listOf("quick-rescue"),
                goalCorrectCount = 2,
            )

        val started = engine.startQuickSession()
        assertNotNull(started)
        val sessionId = sessionRepository.getActiveSession()!!.sessionId

        timeProvider.currentTime = 2_000L
        val firstBack = engine.handleBackPressed(sessionId)
        assertTrue(firstBack is BackPressResult.ShowMicroPrompt)
        firstBack as BackPressResult.ShowMicroPrompt
        assertEquals("quick-micro", firstBack.prompt.item.itemId)
        assertTrue(sessionRepository.getActiveSession()!!.isMicroPromptActive)

        timeProvider.currentTime = 3_000L
        val answerResult =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 900L),
            )
        assertTrue(answerResult is SessionTransition.Advanced)
        answerResult as SessionTransition.Advanced
        assertEquals("quick-next", answerResult.prompt.item.itemId)
        assertTrue(answerResult.prompt.session.isExitArmed)
        assertEquals(11_000L, answerResult.prompt.session.exitArmedUntil)

        timeProvider.currentTime = 4_000L
        val exit = engine.handleBackPressed(sessionId)
        assertTrue(exit is BackPressResult.Exit)

        val persisted = sessionRepository.getActiveSession()
        assertNotNull(persisted)
        persisted!!
        assertEquals("quick-next", persisted.currentItemId)
        assertEquals(1, persisted.correctCount)
        assertEquals(1, persisted.stepIndex)
        assertFalse(persisted.isMicroPromptActive)
        assertFalse(persisted.isExitArmed)
        assertNull(persisted.exitArmedUntil)
        assertTrue(eventRepository.getAbandonEventsSince(0L).isEmpty())
    }

    @Test
    fun quickSession_incorrect_variant_rescue_then_correct_advances_and_updates_node_state() = runBlocking {
        seedSeenItems("quick-clean-main", "quick-clean-next")
        schedulerService.quickPacket =
            packet(
                packetId = "packet-quick",
                topicUnitId = "unit-quick",
                topicTitle = "Quick Unit",
                surface = Surface.IN_APP_QUICK,
                mode = SessionMode.QUICK,
                initialItemIds = listOf("quick-clean-main", "quick-clean-next"),
                rescueItemIds = listOf("quick-clean-rescue"),
                goalCorrectCount = 2,
            )

        val started = engine.startQuickSession()
        assertNotNull(started)
        val sessionId = sessionRepository.getActiveSession()!!.sessionId

        timeProvider.currentTime = 2_000L
        val afterFirstError =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "wrong", isCorrect = false, latencyMs = 2_500L),
            )
        assertTrue(afterFirstError is SessionTransition.Advanced)
        afterFirstError as SessionTransition.Advanced
        assertEquals("quick-clean-variant", afterFirstError.prompt.item.itemId)
        assertEquals(2, afterFirstError.prompt.session.currentAttemptIndex)
        assertEquals(1, afterFirstError.prompt.pendingFailedItemCount)

        timeProvider.currentTime = 3_000L
        val afterSecondError =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "wrong-again", isCorrect = false, latencyMs = 2_700L),
            )
        assertTrue(afterSecondError is SessionTransition.Advanced)
        afterSecondError as SessionTransition.Advanced
        assertEquals("quick-clean-rescue", afterSecondError.prompt.item.itemId)
        assertEquals(3, afterSecondError.prompt.session.currentAttemptIndex)
        assertEquals(1, afterSecondError.prompt.pendingFailedItemCount)

        timeProvider.currentTime = 4_000L
        val afterRecovery =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 1_000L),
            )
        assertTrue(afterRecovery is SessionTransition.Advanced)
        afterRecovery as SessionTransition.Advanced
        assertEquals("quick-clean-next", afterRecovery.prompt.item.itemId)
        assertEquals(0, afterRecovery.prompt.session.correctCount)
        assertEquals(1, afterRecovery.prompt.session.stepIndex)
        assertEquals(1, afterRecovery.prompt.pendingFailedItemCount)

        timeProvider.currentTime = 5_000L
        val afterScheduledVariantCorrect =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 950L),
            )
        assertTrue(afterScheduledVariantCorrect is SessionTransition.Advanced)
        afterScheduledVariantCorrect as SessionTransition.Advanced
        assertEquals("quick-clean-main", afterScheduledVariantCorrect.prompt.item.itemId)
        assertEquals(1, afterScheduledVariantCorrect.prompt.session.correctCount)
        assertEquals(1, afterScheduledVariantCorrect.prompt.pendingFailedItemCount)

        timeProvider.currentTime = 6_000L
        val completed =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 900L),
            )
        assertTrue(completed is SessionTransition.Completed)
        assertNull(sessionRepository.getActiveSession())

        val state = progressRepository.getNodeState("node-quick-clean")
        assertNotNull(state)
        state!!
        assertEquals(5, state.timesSeen)
        assertEquals(2, state.timesFailed)
        assertEquals(1, state.timesCorrectAfterRescue)
        assertEquals(2, state.timesCorrectFirstTry)
    }

    @Test
    fun quickSession_goalReached_keepsSessionOpen_whileAnotherFailedScheduledItemRemainsPending() = runBlocking {
        seedSeenItems("quick-clean-main", "quick-extra", "quick-main")
        schedulerService.quickPacket =
            packet(
                packetId = "packet-quick-pending",
                topicUnitId = "unit-quick",
                topicTitle = "Quick Unit",
                surface = Surface.IN_APP_QUICK,
                mode = SessionMode.QUICK,
                initialItemIds = listOf("quick-clean-main", "quick-extra", "quick-main"),
                rescueItemIds = listOf("quick-clean-rescue", "quick-rescue"),
                goalCorrectCount = 2,
            )

        val started = engine.startQuickSession()
        assertNotNull(started)
        val sessionId = sessionRepository.getActiveSession()!!.sessionId

        timeProvider.currentTime = 2_000L
        val afterFirstError =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "wrong", isCorrect = false, latencyMs = 1_100L),
            )
        assertTrue(afterFirstError is SessionTransition.Advanced)
        afterFirstError as SessionTransition.Advanced
        assertEquals("quick-clean-variant", afterFirstError.prompt.item.itemId)
        assertEquals(1, afterFirstError.prompt.pendingFailedItemCount)

        timeProvider.currentTime = 3_000L
        val afterFallbackCorrect =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 900L),
            )
        assertTrue(afterFallbackCorrect is SessionTransition.Advanced)
        afterFallbackCorrect as SessionTransition.Advanced
        assertEquals("quick-extra", afterFallbackCorrect.prompt.item.itemId)
        assertEquals(0, afterFallbackCorrect.prompt.session.correctCount)
        assertEquals(1, afterFallbackCorrect.prompt.pendingFailedItemCount)

        timeProvider.currentTime = 4_000L
        val afterScheduledExtraCorrect =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 850L),
            )
        assertTrue(afterScheduledExtraCorrect is SessionTransition.Advanced)
        afterScheduledExtraCorrect as SessionTransition.Advanced
        assertEquals("quick-main", afterScheduledExtraCorrect.prompt.item.itemId)
        assertEquals(1, afterScheduledExtraCorrect.prompt.session.correctCount)
        assertEquals(1, afterScheduledExtraCorrect.prompt.pendingFailedItemCount)

        timeProvider.currentTime = 5_000L
        val afterSecondError =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "wrong-again", isCorrect = false, latencyMs = 1_250L),
            )
        assertTrue(afterSecondError is SessionTransition.Advanced)
        afterSecondError as SessionTransition.Advanced
        assertEquals("quick-next", afterSecondError.prompt.item.itemId)
        assertEquals(2, afterSecondError.prompt.pendingFailedItemCount)

        timeProvider.currentTime = 6_000L
        val afterSecondFallbackCorrect =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 800L),
            )
        assertTrue(afterSecondFallbackCorrect is SessionTransition.Advanced)
        afterSecondFallbackCorrect as SessionTransition.Advanced
        assertEquals("quick-clean-main", afterSecondFallbackCorrect.prompt.item.itemId)
        assertEquals(1, afterSecondFallbackCorrect.prompt.session.correctCount)
        assertEquals(2, afterSecondFallbackCorrect.prompt.pendingFailedItemCount)

        timeProvider.currentTime = 7_000L
        val afterFirstRecoveredScheduled =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 780L),
            )
        assertTrue(afterFirstRecoveredScheduled is SessionTransition.Advanced)
        afterFirstRecoveredScheduled as SessionTransition.Advanced
        assertEquals("quick-main", afterFirstRecoveredScheduled.prompt.item.itemId)
        assertEquals(2, afterFirstRecoveredScheduled.prompt.session.correctCount)
        assertEquals(1, afterFirstRecoveredScheduled.prompt.pendingFailedItemCount)

        val persistedAfterGoalReached = sessionRepository.getActiveSession()
        assertNotNull(persistedAfterGoalReached)
        persistedAfterGoalReached!!
        assertEquals("quick-main", persistedAfterGoalReached.currentItemId)
        assertEquals(2, persistedAfterGoalReached.correctCount)

        val resumedAfterGoalReached = engine.resumeSession(persistedAfterGoalReached.sessionId)
        assertNotNull(resumedAfterGoalReached)
        resumedAfterGoalReached!!
        assertEquals("quick-main", resumedAfterGoalReached.item.itemId)
        assertEquals(1, resumedAfterGoalReached.pendingFailedItemCount)

        timeProvider.currentTime = 8_000L
        val completed =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 760L),
            )
        assertTrue(completed is SessionTransition.Completed)
        completed as SessionTransition.Completed
        assertEquals(3, completed.session.correctCount)
        assertEquals(5, completed.session.stepIndex)
        assertNull(sessionRepository.getActiveSession())
    }

    @Test
    fun quickSession_sameScheduledItemFailedAgain_stillCountsAsSinglePendingError() = runBlocking {
        seedSeenItems("quick-main", "quick-extra")
        schedulerService.quickPacket =
            packet(
                packetId = "packet-quick-same-item",
                topicUnitId = "unit-quick",
                topicTitle = "Quick Unit",
                surface = Surface.IN_APP_QUICK,
                mode = SessionMode.QUICK,
                initialItemIds = listOf("quick-main", "quick-extra"),
                rescueItemIds = listOf("quick-rescue"),
                goalCorrectCount = 2,
            )

        val started = engine.startQuickSession()
        assertNotNull(started)
        val sessionId = sessionRepository.getActiveSession()!!.sessionId

        timeProvider.currentTime = 2_000L
        val afterFirstError =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "wrong", isCorrect = false, latencyMs = 1_100L),
            )
        assertTrue(afterFirstError is SessionTransition.Advanced)
        afterFirstError as SessionTransition.Advanced
        assertEquals("quick-next", afterFirstError.prompt.item.itemId)
        assertEquals(1, afterFirstError.prompt.pendingFailedItemCount)

        timeProvider.currentTime = 3_000L
        val afterFallbackCorrect =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 900L),
            )
        assertTrue(afterFallbackCorrect is SessionTransition.Advanced)
        afterFallbackCorrect as SessionTransition.Advanced
        assertEquals("quick-extra", afterFallbackCorrect.prompt.item.itemId)
        assertEquals(1, afterFallbackCorrect.prompt.pendingFailedItemCount)

        timeProvider.currentTime = 4_000L
        val afterOtherScheduledCorrect =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 850L),
            )
        assertTrue(afterOtherScheduledCorrect is SessionTransition.Advanced)
        afterOtherScheduledCorrect as SessionTransition.Advanced
        assertEquals("quick-main", afterOtherScheduledCorrect.prompt.item.itemId)
        assertEquals(1, afterOtherScheduledCorrect.prompt.pendingFailedItemCount)

        timeProvider.currentTime = 5_000L
        val afterSameItemErrorAgain =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "wrong-again", isCorrect = false, latencyMs = 1_200L),
            )
        assertTrue(afterSameItemErrorAgain is SessionTransition.Advanced)
        afterSameItemErrorAgain as SessionTransition.Advanced
        assertEquals("quick-next", afterSameItemErrorAgain.prompt.item.itemId)
        assertEquals(1, afterSameItemErrorAgain.prompt.pendingFailedItemCount)
    }

    @Test
    fun quickSession_firstSeenItems_repeatAtEnd_ofSameSession() = runBlocking {
        schedulerService.quickPacket =
            packet(
                packetId = "packet-quick-fresh",
                topicUnitId = "unit-quick",
                topicTitle = "Quick Unit",
                surface = Surface.IN_APP_QUICK,
                mode = SessionMode.QUICK,
                initialItemIds = listOf("quick-clean-main", "quick-clean-next"),
                rescueItemIds = listOf("quick-clean-rescue"),
                goalCorrectCount = 2,
            )

        val started = engine.startQuickSession()
        assertNotNull(started)
        started!!
        assertEquals(4, started.session.goalCorrectCount)
        val sessionId = started.session.sessionId

        timeProvider.currentTime = 2_000L
        val afterFirstCorrect =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 900L),
            )
        assertTrue(afterFirstCorrect is SessionTransition.Advanced)
        afterFirstCorrect as SessionTransition.Advanced
        assertEquals("quick-clean-next", afterFirstCorrect.prompt.item.itemId)
        assertEquals(listOf("quick-clean-main"), afterFirstCorrect.prompt.session.queueItemIds)
        assertEquals(1, afterFirstCorrect.prompt.session.correctCount)
        assertEquals(4, afterFirstCorrect.prompt.session.goalCorrectCount)

        timeProvider.currentTime = 3_000L
        val afterSecondCorrect =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 850L),
            )
        assertTrue(afterSecondCorrect is SessionTransition.Advanced)
        afterSecondCorrect as SessionTransition.Advanced
        assertEquals("quick-clean-main", afterSecondCorrect.prompt.item.itemId)
        assertEquals(listOf("quick-clean-next"), afterSecondCorrect.prompt.session.queueItemIds)
        assertEquals(2, afterSecondCorrect.prompt.session.correctCount)

        timeProvider.currentTime = 4_000L
        val afterThirdCorrect =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 820L),
            )
        assertTrue(afterThirdCorrect is SessionTransition.Advanced)
        afterThirdCorrect as SessionTransition.Advanced
        assertEquals("quick-clean-next", afterThirdCorrect.prompt.item.itemId)
        assertEquals(emptyList<String>(), afterThirdCorrect.prompt.session.queueItemIds)
        assertEquals(3, afterThirdCorrect.prompt.session.correctCount)

        timeProvider.currentTime = 5_000L
        val completed =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 800L),
            )
        assertTrue(completed is SessionTransition.Completed)
        assertNull(sessionRepository.getActiveSession())
    }

    @Test
    fun quickSession_previouslySeenItems_doNotRepeatAtEnd() = runBlocking {
        seedSeenItems("quick-clean-main", "quick-clean-next")
        schedulerService.quickPacket =
            packet(
                packetId = "packet-quick-seen",
                topicUnitId = "unit-quick",
                topicTitle = "Quick Unit",
                surface = Surface.IN_APP_QUICK,
                mode = SessionMode.QUICK,
                initialItemIds = listOf("quick-clean-main", "quick-clean-next"),
                rescueItemIds = listOf("quick-clean-rescue"),
                goalCorrectCount = 2,
            )

        val started = engine.startQuickSession()
        assertNotNull(started)
        started!!
        assertEquals(2, started.session.goalCorrectCount)
    }

    @Test
    fun exitWindowExpired_requiresNewMicroPrompt_insteadOfExit() = runBlocking {
        seedSeenItems("quick-main", "quick-next")
        schedulerService.quickPacket =
            packet(
                packetId = "packet-quick",
                topicUnitId = "unit-quick",
                topicTitle = "Quick Unit",
                surface = Surface.IN_APP_QUICK,
                mode = SessionMode.QUICK,
                initialItemIds = listOf("quick-main", "quick-next"),
                rescueItemIds = listOf("quick-rescue"),
                goalCorrectCount = 2,
            )

        engine.startQuickSession()
        val sessionId = sessionRepository.getActiveSession()!!.sessionId

        timeProvider.currentTime = 2_000L
        val firstBack = engine.handleBackPressed(sessionId)
        assertTrue(firstBack is BackPressResult.ShowMicroPrompt)

        timeProvider.currentTime = 3_000L
        val afterMicroCorrect =
            engine.submitAnswer(
                sessionId = sessionId,
                answer = UserAnswer(responseText = "correct", isCorrect = true, latencyMs = 800L),
            )
        assertTrue(afterMicroCorrect is SessionTransition.Advanced)
        afterMicroCorrect as SessionTransition.Advanced
        assertTrue(afterMicroCorrect.prompt.session.isExitArmed)
        assertEquals(11_000L, afterMicroCorrect.prompt.session.exitArmedUntil)

        timeProvider.currentTime = 12_500L
        val expiredBack = engine.handleBackPressed(sessionId)
        assertTrue(expiredBack is BackPressResult.ShowMicroPrompt)
        expiredBack as BackPressResult.ShowMicroPrompt
        assertTrue(expiredBack.prompt.item.itemId in setOf("quick-main", "quick-micro", "quick-rescue"))
        assertNotEquals("quick-next", expiredBack.prompt.item.itemId)
        assertEquals("node-quick", expiredBack.prompt.item.nodeId)
        assertTrue(expiredBack.prompt.session.isMicroPromptActive)
        assertFalse(expiredBack.prompt.session.isExitArmed)
        assertNull(expiredBack.prompt.session.exitArmedUntil)
    }

    @Test
    fun inactivityTimeout_recordsAbandon_updatesNodeState_and_clearsSession() = runBlocking {
        schedulerService.quickPacket =
            packet(
                packetId = "packet-quick",
                topicUnitId = "unit-quick",
                topicTitle = "Quick Unit",
                surface = Surface.IN_APP_QUICK,
                mode = SessionMode.QUICK,
                initialItemIds = listOf("quick-main", "quick-next"),
                rescueItemIds = listOf("quick-rescue"),
                goalCorrectCount = 2,
            )

        engine.startQuickSession()
        val sessionId = sessionRepository.getActiveSession()!!.sessionId

        timeProvider.currentTime = 6_000L
        val timeoutResult = engine.handleInactivityTimeout(sessionId)
        assertTrue(timeoutResult is BackPressResult.Exit)
        assertNull(sessionRepository.getActiveSession())

        val abandons = eventRepository.getAbandonEventsSince(0L)
        assertEquals(1, abandons.size)
        assertEquals(AbandonReason.TIMEOUT, abandons.single().reason)
        assertEquals(sessionId, abandons.single().sessionId)

        val state = progressRepository.getNodeState("node-quick")
        assertNotNull(state)
        state!!
        assertEquals(1, state.timesAbandoned)
        assertEquals(Surface.IN_APP_QUICK, state.lastSurfaceUsed)
        assertEquals(SessionMode.QUICK, state.lastSessionMode)
        assertTrue(state.abandonRate > 0.0)
    }

    @Test
    fun secondBackDuringMicroPrompt_recordsBackAbandon_and_clearsSession() = runBlocking {
        schedulerService.quickPacket =
            packet(
                packetId = "packet-quick",
                topicUnitId = "unit-quick",
                topicTitle = "Quick Unit",
                surface = Surface.IN_APP_QUICK,
                mode = SessionMode.QUICK,
                initialItemIds = listOf("quick-main", "quick-next"),
                rescueItemIds = listOf("quick-rescue"),
                goalCorrectCount = 2,
            )

        engine.startQuickSession()
        val sessionId = sessionRepository.getActiveSession()!!.sessionId

        timeProvider.currentTime = 2_000L
        val firstBack = engine.handleBackPressed(sessionId)
        assertTrue(firstBack is BackPressResult.ShowMicroPrompt)

        timeProvider.currentTime = 2_500L
        val secondBack = engine.handleBackPressed(sessionId)
        assertTrue(secondBack is BackPressResult.Exit)
        assertNull(sessionRepository.getActiveSession())

        val abandons = eventRepository.getAbandonEventsSince(0L)
        assertEquals(1, abandons.size)
        assertEquals(AbandonReason.BACK, abandons.single().reason)

        val state = progressRepository.getNodeState("node-quick")
        assertNotNull(state)
        state!!
        assertEquals(1, state.timesAbandoned)
        assertTrue(state.frictionUser > NodeState(nodeId = "node-quick").frictionUser)
    }

    private suspend fun seedSeenItems(vararg itemIds: String) {
        itemIds.forEachIndexed { index, itemId ->
            val item = requireNotNull(contentRepository.getItem(itemId))
            eventRepository.recordReviewEvent(
                ReviewEvent(
                    eventId = "seed-$itemId-$index",
                    sessionId = "history-$itemId",
                    nodeId = item.nodeId,
                    itemId = item.itemId,
                    answerOutcome = AttemptOutcome.CORRECT_FIRST_TRY,
                    latencyMs = 750L,
                    surface = Surface.IN_APP_QUICK,
                    sessionMode = SessionMode.QUICK,
                    attemptIndex = 1,
                    occurredAt = 100L + index,
                ),
            )
        }
    }

    private fun packet(
        packetId: String,
        topicUnitId: String,
        topicTitle: String,
        surface: Surface,
        mode: SessionMode,
        initialItemIds: List<String>,
        rescueItemIds: List<String>,
        goalCorrectCount: Int,
    ): TopicPacket =
        TopicPacket(
            packetId = packetId,
            topicUnitId = topicUnitId,
            currentTopicTitle = topicTitle,
            surface = surface,
            sessionMode = mode,
            nodeIds = initialItemIds.mapNotNull { contentRepository.getItemBlocking(it)?.nodeId }.distinct(),
            initialItemIds = initialItemIds,
            rescueItemIds = rescueItemIds,
            goalCorrectCount = goalCorrectCount,
            priorityScore = 1.0,
        )

    private fun baseFixtures(): List<ItemFixture> =
        listOf(
            ItemFixture("deep-main", "node-deep", "unit-deep", allowedSurfaces = listOf(Surface.IN_APP_DEEP), role = ItemRole.CORE),
            ItemFixture("deep-next", "node-deep", "unit-deep", allowedSurfaces = listOf(Surface.IN_APP_DEEP), role = ItemRole.TRAP),
            ItemFixture("deep-rescue", "node-deep", "unit-deep", allowedSurfaces = listOf(Surface.IN_APP_DEEP, Surface.BACK_MICRO), role = ItemRole.RESCUE),
            ItemFixture("drain-main", "node-drain", "unit-drain", allowedSurfaces = listOf(Surface.IN_APP_DEEP), role = ItemRole.CORE),
            ItemFixture("drain-next", "node-drain", "unit-drain", allowedSurfaces = listOf(Surface.IN_APP_DEEP), role = ItemRole.INTEGRATION),
            ItemFixture("drain-boss", "node-drain", "unit-drain", allowedSurfaces = listOf(Surface.IN_APP_DEEP), role = ItemRole.BOSS),
            ItemFixture("social-main", "node-social", "unit-social", allowedSurfaces = listOf(Surface.SOCIAL_GATE, Surface.IN_APP_DEEP), role = ItemRole.CORE),
            ItemFixture("social-variant", "node-social", "unit-social", allowedSurfaces = listOf(Surface.IN_APP_DEEP), role = ItemRole.VARIANT),
            ItemFixture("social-next", "node-social", "unit-social", allowedSurfaces = listOf(Surface.IN_APP_DEEP), role = ItemRole.TRAP),
            ItemFixture("social-rescue", "node-social", "unit-social", allowedSurfaces = listOf(Surface.IN_APP_DEEP, Surface.BACK_MICRO), role = ItemRole.RESCUE),
            ItemFixture("quick-main", "node-quick", "unit-quick", allowedSurfaces = listOf(Surface.IN_APP_QUICK, Surface.BACK_MICRO), role = ItemRole.CORE),
            ItemFixture("quick-next", "node-quick", "unit-quick", allowedSurfaces = listOf(Surface.IN_APP_QUICK), role = ItemRole.VARIANT),
            ItemFixture("quick-micro", "node-quick", "unit-quick", allowedSurfaces = listOf(Surface.BACK_MICRO, Surface.IN_APP_QUICK), role = ItemRole.VARIANT),
            ItemFixture("quick-rescue", "node-quick", "unit-quick", allowedSurfaces = listOf(Surface.IN_APP_QUICK, Surface.BACK_MICRO), role = ItemRole.RESCUE),
            ItemFixture("quick-extra", "node-quick-extra", "unit-quick", allowedSurfaces = listOf(Surface.IN_APP_QUICK), role = ItemRole.CORE),
            ItemFixture("quick-clean-main", "node-quick-clean", "unit-quick", allowedSurfaces = listOf(Surface.IN_APP_QUICK, Surface.BACK_MICRO), role = ItemRole.CORE),
            ItemFixture("quick-clean-next", "node-quick-clean", "unit-quick", allowedSurfaces = listOf(Surface.IN_APP_QUICK), role = ItemRole.CORE),
            ItemFixture("quick-clean-variant", "node-quick-clean", "unit-quick", allowedSurfaces = listOf(Surface.IN_APP_QUICK, Surface.BACK_MICRO), role = ItemRole.VARIANT),
            ItemFixture("quick-clean-rescue", "node-quick-clean", "unit-quick", allowedSurfaces = listOf(Surface.IN_APP_QUICK, Surface.BACK_MICRO), role = ItemRole.RESCUE),
        )
}

private data class ItemFixture(
    val itemId: String,
    val nodeId: String,
    val unitId: String,
    val allowedSurfaces: List<Surface>,
    val role: ItemRole,
)

private class EngineContentRepository(
    fixtures: List<ItemFixture>,
) : ContentRepository {
    private val nodes: Map<String, Node> =
        fixtures.associate { fixture ->
            fixture.nodeId to
                Node(
                    nodeId = fixture.nodeId,
                    courseId = "course-${fixture.unitId}",
                    unitId = fixture.unitId,
                    outcomeIds = listOf("outcome-${fixture.unitId}"),
                    title = "Node ${fixture.nodeId}",
                    coreClaim = "Claim ${fixture.nodeId}",
                    type = NodeType.CONCEPT,
                    weightExam = 0.8,
                    prerequisites = emptyList(),
                    facets = listOf(FacetType.DEFINICION_FUNCIONAL),
                    mustKnow = listOf("know-${fixture.nodeId}"),
                    commonErrors = listOf("error-${fixture.nodeId}"),
                    minimumMasteryDefinition = "mastery-${fixture.nodeId}",
                    surfaceEasyReady = true,
                    surfaceEasyItemCount = 4,
                    sourceRefs = listOf("source"),
                    version = 1,
                    updatedAt = 1L,
                    archivedCandidate = false,
                    contentOrigin = ContentOrigin.IMPORTED,
                )
        }
    private val items: Map<String, Item> =
        fixtures.associate { fixture ->
            fixture.itemId to
                Item(
                    itemId = fixture.itemId,
                    nodeId = fixture.nodeId,
                    facet = FacetType.DEFINICION_FUNCIONAL,
                    format = ItemFormat.MULTIPLE_CHOICE,
                    frictionLevel = if (fixture.role == ItemRole.BOSS) 3 else 1,
                    difficultySeed = 0.2,
                    itemRole = fixture.role,
                    allowedSurfaces = fixture.allowedSurfaces,
                    cooldownHours = 1.0,
                    stem = fixture.itemId,
                    correctAnswer = "correct",
                    feedbackShort = "feedback-${fixture.itemId}",
                    coversMustKnow = listOf("know-${fixture.nodeId}"),
                    variantGroupId = null,
                    rescueGroupId = null,
                    nodeComplexity = null,
                    facetComplexity = null,
                    distractorSimilarity = null,
                    prerequisiteDepth = null,
                    targetsErrorIds = emptyList(),
                    commonErrorSignals = emptyList(),
                    options = listOf(ItemOption("a", "correct", true)),
                    version = 1,
                    updatedAt = 1L,
                    sourceRefs = listOf("source"),
                    contentOrigin = ContentOrigin.IMPORTED,
                )
        }

    override suspend fun seedDemoIfNeeded() = Unit

    override suspend fun importContentPackage(
        rawJson: String,
        sourceLabel: String,
        validationProfile: ImportValidationProfile,
    ): ImportExecutionResult =
        ImportExecutionResult(sourceLabel, false, ValidationReport(emptyList(), emptyList()))

    override suspend fun importContentPackage(uri: Uri): ImportExecutionResult =
        ImportExecutionResult(uri.toString(), false, ValidationReport(emptyList(), emptyList()))

    override suspend fun prepareContentPackage(uri: Uri) =
        error("Not used in session engine persistence tests")

    override suspend fun getContentTree(): ContentTree = ContentTree(emptyList())

    override suspend fun getUnits(): List<UnitModel> =
        nodes.values.map { it.unitId }.distinct().mapIndexed { index, unitId ->
            UnitModel(unitId, "course-$unitId", "Unit $unitId", null, index, 1, 1L)
        }

    override suspend fun getSchedulableNodes(): List<Node> = nodes.values.toList()

    override suspend fun getSchedulableNodesByUnit(unitId: String): List<Node> = nodes.values.filter { it.unitId == unitId }

    override suspend fun getNode(nodeId: String): Node? = nodes[nodeId]

    override suspend fun getNodeDetail(nodeId: String, includeArchivedItems: Boolean): NodeDetail? =
        nodes[nodeId]?.let { node ->
            NodeDetail(node = node, items = items.values.filter { it.nodeId == nodeId })
        }

    override suspend fun getItem(itemId: String, includeArchived: Boolean): Item? = items[itemId]

    fun getItemBlocking(itemId: String): Item? = items[itemId]

    override suspend fun getItemsForNode(nodeId: String, includeArchived: Boolean): List<Item> = items.values.filter { it.nodeId == nodeId }

    override suspend fun getItemsForNodes(nodeIds: List<String>, includeArchived: Boolean): List<Item> = items.values.filter { it.nodeId in nodeIds }

    override suspend fun setArchivedCandidate(nodeId: String, archived: Boolean) = Unit

    override suspend fun hasRealImportedContent(): Boolean = true

    override suspend fun getContentPackage(rawJson: String) = throw UnsupportedOperationException()
}

private class FakeSchedulerService : SchedulerService {
    var quickPacket: TopicPacket? = null
    var notificationPacket: TopicPacket? = null
    var socialGatePacket: TopicPacket? = null
    var deepPacket: TopicPacket? = null
    var drainPacket: TopicPacket? = null

    override suspend fun buildQuickPacket(
        preferredUnitId: String?,
        preferredItemId: String?,
    ): TopicPacket? =
        quickPacket?.takeIf {
            (preferredUnitId == null || it.topicUnitId == preferredUnitId) &&
                (preferredItemId == null || it.initialItemIds.firstOrNull() == preferredItemId)
        }
    override suspend fun buildNotificationPacket(
        preferredUnitId: String?,
        preferredItemId: String?,
    ): TopicPacket? =
        notificationPacket?.takeIf {
            (preferredUnitId == null || it.topicUnitId == preferredUnitId) &&
                (preferredItemId == null || it.initialItemIds.firstOrNull() == preferredItemId)
        }
    override suspend fun buildSocialGatePacket(goalCorrectCount: Int): TopicPacket? =
        socialGatePacket?.copy(goalCorrectCount = goalCorrectCount)

    override suspend fun buildDeepPacket(unitId: String): TopicPacket? = deepPacket?.takeIf { it.topicUnitId == unitId }

    override suspend fun buildDrainPacket(unitId: String): TopicPacket? = drainPacket?.takeIf { it.topicUnitId == unitId }

    override suspend fun getAvailableUnits(): List<DeepModeUnitOption> = emptyList()
}

private class FixedSettingsRepository(
    private val settings: AppSettings,
) : SettingsRepository {
    override fun observeSettings(): Flow<AppSettings> = flowOf(settings)

    override suspend fun getSettings(): AppSettings = settings

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = Unit
}

private class MutableTimeProvider(
    var currentTime: Long,
) : TimeProvider {
    override fun now(): Long = currentTime
}

private class SequentialIdProvider : IdProvider {
    private var next = 0

    override fun newId(): String {
        next += 1
        return "id-$next"
    }
}

private suspend fun LocalSessionRepository.getActiveSession(): com.estudio.antiprocrastinacion.app.model.state.StudySession? =
    getMostRecentActiveSession()
