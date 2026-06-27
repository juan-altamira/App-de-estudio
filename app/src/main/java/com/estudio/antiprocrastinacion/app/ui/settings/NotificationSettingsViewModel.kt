package com.estudio.antiprocrastinacion.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estudio.antiprocrastinacion.app.domain.repository.SettingsRepository
import com.estudio.antiprocrastinacion.app.model.state.AppSettings
import com.estudio.antiprocrastinacion.app.notification.CognitiveNotificationPlanner
import com.estudio.antiprocrastinacion.app.notification.CognitiveNotificationScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotificationSettingsUiState(
    val isLoading: Boolean = true,
    val settings: AppSettings = AppSettings(),
)

class NotificationSettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val cognitiveNotificationScheduler: CognitiveNotificationScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationSettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observeSettings().collect { settings ->
                _uiState.value = NotificationSettingsUiState(isLoading = false, settings = settings)
            }
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        persistSettings { it.copy(cognitiveNotificationsEnabled = enabled) }
    }

    fun updateNotificationsPerDay(perDay: Int) {
        persistSettings {
            it.copy(
                cognitiveNotificationsPerDay = perDay.coerceIn(1, CognitiveNotificationPlanner.MAX_COGNITIVE_NOTIFICATIONS_PER_DAY),
            ).normalizedNotificationWindow()
        }
    }

    fun updateNotificationWindowStartMinutes(startMinutes: Int) {
        persistSettings {
            it.copy(
                cognitiveNotificationWindowStartMinutes = startMinutes.coerceIn(0, MAX_SELECTABLE_MINUTE),
            ).normalizedNotificationWindow()
        }
    }

    fun updateNotificationWindowEndMinutes(endMinutes: Int) {
        persistSettings {
            it.copy(
                cognitiveNotificationWindowEndMinutes = endMinutes.coerceIn(0, MAX_SELECTABLE_MINUTE),
            ).normalizedNotificationWindow()
        }
    }

    private fun persistSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings { current -> transform(current) }
            cognitiveNotificationScheduler.refreshSchedule()
        }
    }

    private fun AppSettings.normalizedNotificationWindow(): AppSettings {
        val safeStart = cognitiveNotificationWindowStartMinutes.coerceIn(0, MAX_SELECTABLE_MINUTE)
        val safeEndCandidate = cognitiveNotificationWindowEndMinutes.coerceIn(0, MAX_SELECTABLE_MINUTE)
        val safeEnd =
            if (safeEndCandidate <= safeStart) {
                (safeStart + 60).coerceAtMost(MAX_SELECTABLE_MINUTE)
            } else {
                safeEndCandidate
            }
        return copy(
            cognitiveNotificationWindowStartMinutes = safeStart,
            cognitiveNotificationWindowEndMinutes = safeEnd,
        )
    }

    companion object {
        private const val MAX_SELECTABLE_MINUTE = 23 * 60 + 30
    }
}
