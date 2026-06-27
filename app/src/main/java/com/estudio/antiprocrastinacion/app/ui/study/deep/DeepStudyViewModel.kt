package com.estudio.antiprocrastinacion.app.ui.study.deep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estudio.antiprocrastinacion.app.domain.scheduler.DeepModeUnitOption
import com.estudio.antiprocrastinacion.app.domain.scheduler.SchedulerService
import com.estudio.antiprocrastinacion.app.domain.session.SessionEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeepStudyUiState(
    val isLoading: Boolean = true,
    val units: List<DeepModeUnitOption> = emptyList(),
)

class DeepStudyViewModel(
    private val schedulerService: SchedulerService,
    private val sessionEngine: SessionEngine,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DeepStudyUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<String>()
    val effects = _effects.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            _uiState.value =
                DeepStudyUiState(
                    isLoading = false,
                    units = schedulerService.getAvailableUnits(),
                )
        }
    }

    fun startDeep(unitId: String) {
        viewModelScope.launch {
            sessionEngine.startDeepSession(unitId)?.let { _effects.emit(it.session.sessionId) }
        }
    }

    fun startDrain(unitId: String) {
        viewModelScope.launch {
            sessionEngine.startDrainSession(unitId)?.let { _effects.emit(it.session.sessionId) }
        }
    }
}
