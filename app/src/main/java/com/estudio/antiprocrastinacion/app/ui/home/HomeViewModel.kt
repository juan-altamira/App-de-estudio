package com.estudio.antiprocrastinacion.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estudio.antiprocrastinacion.app.data.local.db.CourseRecovery
import com.estudio.antiprocrastinacion.app.data.local.db.RecoveryNoticeAccess
import com.estudio.antiprocrastinacion.app.data.local.db.RecoveryReport
import com.estudio.antiprocrastinacion.app.domain.repository.ContentRepository
import com.estudio.antiprocrastinacion.app.domain.repository.SessionRepository
import com.estudio.antiprocrastinacion.app.domain.session.SessionEngine
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.state.StudySession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ActiveSessionSummary(
    val sessionId: String,
    val topicTitle: String,
    val modeLabel: String,
    val progressLabel: String,
    val statusLabel: String? = null,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val quickSessionSummary: ActiveSessionSummary? = null,
    val deepSessionSummary: ActiveSessionSummary? = null,
    val drainSessionSummary: ActiveSessionSummary? = null,
    val hasRealImportedContent: Boolean = false,
    // Aviso persistente cuando se detectó un borrado y se recuperó contenido; se muestra hasta "OK".
    val recoveryNotice: String? = null,
)

sealed interface HomeEffect {
    data class NavigateToStudy(val sessionId: String) : HomeEffect
    data object NavigateToQuickComplete : HomeEffect
    data object NavigateToContent : HomeEffect
    data object NavigateToImport : HomeEffect
    data object NavigateToUpcomingReviews : HomeEffect
    data object NavigateToNotificationSettings : HomeEffect
    data object NavigateToSocialGateSettings : HomeEffect
    data object NavigateToDeep : HomeEffect
    data object NavigateToDrain : HomeEffect
}

class HomeViewModel(
    private val contentRepository: ContentRepository,
    private val sessionRepository: SessionRepository,
    private val sessionEngine: SessionEngine,
    private val courseRecoveryStore: CourseRecovery,
    private val recoveryNoticeStore: RecoveryNoticeAccess,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<HomeEffect>()
    val effects = _effects.asSharedFlow()

    init {
        refresh()
    }

    fun startQuick() {
        viewModelScope.launch {
            launchQuickSession()
        }
    }

    private suspend fun launchQuickSession() {
        val prompt = sessionEngine.startQuickSession()
        if (prompt != null) {
            _effects.emit(HomeEffect.NavigateToStudy(prompt.session.sessionId))
            refresh()
        } else {
            _effects.emit(HomeEffect.NavigateToQuickComplete)
        }
    }

    fun openContent() {
        viewModelScope.launch {
            _effects.emit(HomeEffect.NavigateToContent)
        }
    }

    fun openImport() {
        viewModelScope.launch {
            _effects.emit(HomeEffect.NavigateToImport)
        }
    }

    fun openUpcomingReviews() {
        viewModelScope.launch {
            _effects.emit(HomeEffect.NavigateToUpcomingReviews)
        }
    }

    fun openNotificationSettings() {
        viewModelScope.launch {
            _effects.emit(HomeEffect.NavigateToNotificationSettings)
        }
    }

    fun openSocialGateSettings() {
        viewModelScope.launch {
            _effects.emit(HomeEffect.NavigateToSocialGateSettings)
        }
    }

    fun openDeep() {
        viewModelScope.launch {
            val activeSession = sessionRepository.getActiveSession(SessionMode.DEEP)
            _effects.emit(
                if (activeSession != null) {
                    HomeEffect.NavigateToStudy(activeSession.sessionId)
                } else {
                    HomeEffect.NavigateToDeep
                },
            )
        }
    }

    fun openDrain() {
        viewModelScope.launch {
            val activeSession = sessionRepository.getActiveSession(SessionMode.DRAIN)
            _effects.emit(
                if (activeSession != null) {
                    HomeEffect.NavigateToStudy(activeSession.sessionId)
                } else {
                    HomeEffect.NavigateToDrain
                },
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            // 1) Recuperar contenido perdido ANTES de sembrar el demo, para no enmascarar lo recuperado
            //    ni reaparecer el demo encima. Si algo se restauró, dejamos el aviso persistente.
            val report = courseRecoveryStore.detectAndRecover()
            if (report != null) {
                recoveryNoticeStore.setNotice(recoveryMessage(report), System.currentTimeMillis())
            }
            contentRepository.seedDemoIfNeeded()
            val quickSession = sessionRepository.getActiveSession(SessionMode.QUICK)
            val deepSession = sessionRepository.getActiveSession(SessionMode.DEEP)
            val drainSession = sessionRepository.getActiveSession(SessionMode.DRAIN)
            _uiState.value =
                HomeUiState(
                    isLoading = false,
                    quickSessionSummary = quickSession?.toHomeSummary(),
                    deepSessionSummary = deepSession?.toHomeSummary(),
                    drainSessionSummary = drainSession?.toHomeSummary(),
                    hasRealImportedContent = contentRepository.hasRealImportedContent(),
                    recoveryNotice = recoveryNoticeStore.activeNotice()?.message,
                )
            // 2) Respaldar el estado bueno actual (contenido + progreso + ediciones) cada vez que se abre
            //    Home. Es solo lectura sobre la base + escritura de archivo; no afecta ninguna otra lógica.
            courseRecoveryStore.backup()
        }
    }

    fun acknowledgeRecoveryNotice() {
        recoveryNoticeStore.acknowledge()
        _uiState.value = _uiState.value.copy(recoveryNotice = null)
    }

    private fun recoveryMessage(report: RecoveryReport): String {
        val cursos = report.recoveredCourseTitles.filter { it.isNotBlank() }
        val cursoPart = if (cursos.isNotEmpty()) "Cursos: ${cursos.joinToString(", ")}. " else ""
        return "Se detectó un borrado de datos y se recuperó automáticamente desde el último respaldo. " +
            "${cursoPart}Tarjetas recuperadas: ${report.recoveredCardCount}."
    }
}

private fun StudySession.toHomeSummary(): ActiveSessionSummary =
    ActiveSessionSummary(
        sessionId = sessionId,
        topicTitle = currentTopicTitle,
        modeLabel =
            when (mode) {
                SessionMode.QUICK -> "Tarjetas pendientes"
                SessionMode.DEEP -> "Modo profundo"
                SessionMode.DRAIN -> "Vaciar"
            },
        progressLabel = "Progreso $correctCount/$goalCorrectCount",
        statusLabel = if (isMicroPromptActive) "Micro-pregunta activa" else null,
    )
