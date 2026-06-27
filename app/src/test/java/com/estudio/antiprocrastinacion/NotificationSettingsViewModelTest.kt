package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.domain.repository.SettingsRepository
import com.estudio.antiprocrastinacion.app.model.state.AppSettings
import com.estudio.antiprocrastinacion.app.notification.CognitiveNotificationScheduler
import com.estudio.antiprocrastinacion.app.ui.settings.NotificationSettingsViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {
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
    fun `update notifications per day persists settings and refreshes schedule`() = runTest(dispatcher) {
        val settingsRepository = FakeNotificationSettingsRepository()
        val scheduler = FakeNotificationSettingsScheduler()
        val viewModel = NotificationSettingsViewModel(settingsRepository, scheduler)

        advanceUntilIdle()
        viewModel.updateNotificationsPerDay(3)
        advanceUntilIdle()

        assertThat(settingsRepository.current.cognitiveNotificationsPerDay).isEqualTo(3)
        assertThat(scheduler.refreshCalls).isEqualTo(1)
    }

    @Test
    fun `start minutes normalization keeps window valid`() = runTest(dispatcher) {
        val settingsRepository = FakeNotificationSettingsRepository(
            AppSettings(
                cognitiveNotificationsEnabled = true,
                cognitiveNotificationWindowStartMinutes = 18 * 60,
                cognitiveNotificationWindowEndMinutes = 19 * 60,
            ),
        )
        val viewModel = NotificationSettingsViewModel(settingsRepository, FakeNotificationSettingsScheduler())

        advanceUntilIdle()
        viewModel.updateNotificationWindowStartMinutes(21 * 60)
        advanceUntilIdle()

        assertThat(settingsRepository.current.cognitiveNotificationWindowStartMinutes).isEqualTo(21 * 60)
        assertThat(settingsRepository.current.cognitiveNotificationWindowEndMinutes).isGreaterThan(21 * 60)
    }
}

private class FakeNotificationSettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    val current: AppSettings get() = state.value

    override fun observeSettings(): Flow<AppSettings> = state
    override suspend fun getSettings(): AppSettings = state.value
    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

private class FakeNotificationSettingsScheduler : CognitiveNotificationScheduler {
    var refreshCalls: Int = 0
    override suspend fun refreshSchedule() {
        refreshCalls += 1
    }
    override suspend fun handleScheduledTrigger(slotIndex: Int): Boolean = false
}
