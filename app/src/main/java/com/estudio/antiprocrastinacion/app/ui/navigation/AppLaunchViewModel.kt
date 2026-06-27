package com.estudio.antiprocrastinacion.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estudio.antiprocrastinacion.app.domain.repository.SessionRepository
import com.estudio.antiprocrastinacion.app.domain.session.SessionEngine
import com.estudio.antiprocrastinacion.app.notification.NotificationLaunchRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppLaunchTarget {
    data object Home : AppLaunchTarget
    data class Study(val sessionId: String) : AppLaunchTarget
}

data class AppLaunchUiState(
    val isLoading: Boolean = true,
    val target: AppLaunchTarget? = null,
)

class AppLaunchViewModel(
    private val sessionRepository: SessionRepository,
    private val sessionEngine: SessionEngine,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppLaunchUiState())
    val uiState = _uiState.asStateFlow()

    fun refresh(notificationLaunchRequest: NotificationLaunchRequest? = null) {
        viewModelScope.launch {
            _uiState.value = AppLaunchUiState(isLoading = true)
            val notificationPrompt =
                if (notificationLaunchRequest != null) {
                    sessionEngine.startNotificationSession(
                        preferredUnitId = notificationLaunchRequest.preferredUnitId,
                        preferredItemId = notificationLaunchRequest.preferredItemId,
                    )
                } else {
                    null
                }
            val activeSessions = sessionRepository.getAllActiveSessions()
            _uiState.value =
                AppLaunchUiState(
                    isLoading = false,
                    target =
                        notificationPrompt?.session?.sessionId?.let(AppLaunchTarget::Study)
                            ?: activeSessions.singleOrNull()?.sessionId?.let(AppLaunchTarget::Study)
                            ?: AppLaunchTarget.Home,
                )
        }
    }
}
