package com.estudio.antiprocrastinacion

import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.estudio.antiprocrastinacion.app.di.AppContainer
import com.estudio.antiprocrastinacion.app.ui.home.HomeViewModel
import com.estudio.antiprocrastinacion.app.ui.study.quick.QuickStudyViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class StudySessionRestorationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val app = targetContext.applicationContext as StudyApplication

    @After
    fun tearDown() {
        clearActiveSessions()
    }

    @Test
    fun quickSession_restoresExactPromptState_acrossActivityRecreation() {
        ensureDeviceReadyForUi()
        clearActiveSessions()

        val owner = TestViewModelStoreOwner()
        val homeViewModel =
            ViewModelProvider(owner, app.container.homeViewModelFactory())[HomeViewModel::class.java]

        homeViewModel.refresh()
        waitUntil("home finished seeding/loading") {
            !homeViewModel.uiState.value.isLoading
        }

        homeViewModel.startQuick()
        waitUntil("quick session created in repository") {
            homeViewModel.uiState.value.quickSessionSummary?.sessionId != null
        }

        val expectedSnapshot = requireNotNull(readActiveSessionSnapshot()) {
            "Expected an active session snapshot after starting quick session"
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            ensureDeviceReadyForUi()

            val firstPromptState = loadPromptThroughActivityViewModel(scenario, expectedSnapshot.sessionId)
            assertEquals(
                expectedSnapshot.toPromptSnapshot(),
                firstPromptState,
            )

            val persistedBeforeRecreate = readActiveSessionSnapshot()
            assertEquals(expectedSnapshot, persistedBeforeRecreate)

            ensureDeviceReadyForUi()
            scenario.recreate()
            scenario.moveToState(Lifecycle.State.RESUMED)
            ensureDeviceReadyForUi()

            val restoredPromptState = loadPromptThroughActivityViewModel(scenario, expectedSnapshot.sessionId) 
            assertEquals(
                expectedSnapshot.toPromptSnapshot(),
                restoredPromptState,
            )

            val persistedAfterRecreate = readActiveSessionSnapshot()
            assertEquals(persistedBeforeRecreate, persistedAfterRecreate)
        }

        owner.viewModelStore.clear()
    }

    private fun loadPromptThroughActivityViewModel(
        scenario: ActivityScenario<MainActivity>,
        sessionId: String,
    ): PromptSnapshot {
        lateinit var quickStudyViewModel: QuickStudyViewModel
        scenario.onActivity { activity ->
            quickStudyViewModel =
                ViewModelProvider(
                    activity,
                    (activity.application as StudyApplication).container.quickStudyViewModelFactory(),
                )[QuickStudyViewModel::class.java]
            quickStudyViewModel.load(sessionId)
        }

        waitUntil("quick study view model loaded prompt for session $sessionId") {
            quickStudyViewModel.uiState.value.prompt?.session?.sessionId == sessionId
        }

        val prompt =
            requireNotNull(quickStudyViewModel.uiState.value.prompt) {
                "Expected QuickStudyViewModel to restore a prompt for session $sessionId"
            }
        return PromptSnapshot(
            sessionId = prompt.session.sessionId,
            currentItemId = prompt.item.itemId,
            currentNodeId = prompt.node.nodeId,
            stepIndex = prompt.session.stepIndex,
            goalCorrectCount = prompt.session.goalCorrectCount,
            correctCount = prompt.session.correctCount,
            topicTitle = prompt.session.currentTopicTitle,
            itemStem = prompt.item.stem,
        )
    }

    private fun clearActiveSessions() {
        val dbFile = targetContext.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) return
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("DELETE FROM active_sessions")
        }
    }

    private fun readActiveSessionSnapshot(): PersistedSessionSnapshot? {
        val dbFile = targetContext.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) return null

        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(
                """
                SELECT
                    s.sessionId,
                    s.currentItemId,
                    s.currentNodeId,
                    s.stepIndex,
                    s.goalCorrectCount,
                    s.correctCount,
                    s.currentTopicTitle,
                    i.stem
                FROM active_sessions s
                LEFT JOIN items i ON i.itemId = s.currentItemId
                LIMIT 1
                """.trimIndent(),
                emptyArray(),
            ).use { cursor ->
                if (!cursor.moveToFirst()) return null
                return PersistedSessionSnapshot(
                    sessionId = cursor.getString(0),
                    currentItemId = cursor.getString(1),
                    currentNodeId = cursor.getString(2),
                    stepIndex = cursor.getInt(3),
                    goalCorrectCount = cursor.getInt(4),
                    correctCount = cursor.getInt(5),
                    topicTitle = cursor.getString(6),
                    itemStem = cursor.getString(7),
                )
            }
        }
    }

    private fun waitUntil(
        description: String,
        timeoutMs: Long = 10_000,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            instrumentation.waitForIdleSync()
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for: $description")
    }

    private fun ensureDeviceReadyForUi() {
        runShellCommand("input keyevent KEYCODE_WAKEUP")
        runShellCommand("wm dismiss-keyguard")
        runShellCommand("input keyevent 82")
    }

    private fun runShellCommand(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).close()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(250)
    }

    private data class PersistedSessionSnapshot(
        val sessionId: String,
        val currentItemId: String?,
        val currentNodeId: String?,
        val stepIndex: Int,
        val goalCorrectCount: Int,
        val correctCount: Int,
        val topicTitle: String,
        val itemStem: String,
    ) {
        fun toPromptSnapshot(): PromptSnapshot =
            PromptSnapshot(
                sessionId = sessionId,
                currentItemId = currentItemId,
                currentNodeId = currentNodeId,
                stepIndex = stepIndex,
                goalCorrectCount = goalCorrectCount,
                correctCount = correctCount,
                topicTitle = topicTitle,
                itemStem = itemStem,
            )
    }

    private data class PromptSnapshot(
        val sessionId: String,
        val currentItemId: String?,
        val currentNodeId: String?,
        val stepIndex: Int,
        val goalCorrectCount: Int,
        val correctCount: Int,
        val topicTitle: String,
        val itemStem: String,
    )

    private class TestViewModelStoreOwner : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = ViewModelStore()
    }

    companion object {
        // Apunta a la MISMA base que usa la app bajo instrumentación (aislada por StudyInstrumentationRunner),
        // nunca a la "study.db" real del usuario.
        private val DB_NAME: String
            get() = AppContainer.databaseNameOverride ?: AppContainer.DEFAULT_DATABASE_NAME
    }
}
