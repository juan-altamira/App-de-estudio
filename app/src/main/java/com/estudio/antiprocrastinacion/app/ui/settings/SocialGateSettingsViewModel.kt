package com.estudio.antiprocrastinacion.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estudio.antiprocrastinacion.app.domain.repository.SocialGateRepository
import com.estudio.antiprocrastinacion.app.model.state.SocialGateInstalledApp
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRule
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRuntimePhase
import com.estudio.antiprocrastinacion.app.socialgate.SocialGateCatalog
import com.estudio.antiprocrastinacion.app.socialgate.SocialGateSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class SocialGateSettingsUiState(
    val isLoading: Boolean = true,
    val installedApps: List<SocialGateInstalledApp> = emptyList(),
    val solvedTodayByPackage: Map<String, Int> = emptyMap(),
    val runtimePhase: SocialGateRuntimePhase = SocialGateRuntimePhase.IDLE,
    val runtimeTargetPackageName: String? = null,
    val unlockTokenPackageName: String? = null,
    val lastForegroundPackageName: String? = null,
)

class SocialGateSettingsViewModel(
    private val appContext: Context,
    private val socialGateRepository: SocialGateRepository,
    private val installedAppsResolver: (Context, List<SocialGateRule>) -> List<SocialGateInstalledApp> =
        SocialGateCatalog::resolveInstalledApps,
    private val todayKeyProvider: () -> String = { LocalDate.now(ZoneId.systemDefault()).toString() },
) : ViewModel() {
    private val _uiState = MutableStateFlow(SocialGateSettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            socialGateRepository.observeRules().collect { rules ->
                _uiState.value = buildUiState(rules)
            }
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            _uiState.value = buildUiState(socialGateRepository.getRules())
        }
    }

    fun updateRuleEnabled(
        packageName: String,
        enabled: Boolean,
    ) {
        updateRule(packageName) { it.copy(enabled = enabled) }
    }

    fun updateMaxTriggersPerDay(
        packageName: String,
        count: Int,
    ) {
        updateRule(packageName) { it.copy(maxTriggersPerDay = count.coerceIn(1, 10)) }
    }

    fun updateRequiredCorrectAnswers(
        packageName: String,
        count: Int,
    ) {
        updateRule(packageName) { it.copy(requiredCorrectAnswers = count.coerceIn(1, 10)) }
    }

    fun updateWindowStartMinutes(
        packageName: String,
        minutes: Int,
    ) {
        updateRule(packageName) {
            it.copy(
                windowStartMinutes = minutes.coerceIn(0, SocialGateSchedule.MAX_SELECTABLE_MINUTE),
            )
        }
    }

    fun updateWindowEndMinutes(
        packageName: String,
        minutes: Int,
    ) {
        updateRule(packageName) {
            it.copy(
                windowEndMinutes = minutes.coerceIn(0, SocialGateSchedule.MAX_SELECTABLE_MINUTE),
            )
        }
    }

    private fun updateRule(
        packageName: String,
        transform: (SocialGateRule) -> SocialGateRule,
    ) {
        val app = _uiState.value.installedApps.firstOrNull { it.packageName == packageName } ?: return
        viewModelScope.launch {
            val current =
                socialGateRepository.getRule(packageName)
                    ?: SocialGateRule(
                        packageName = packageName,
                        displayName = app.displayName,
                        enabled = app.enabled,
                        maxTriggersPerDay = app.maxTriggersPerDay,
                        requiredCorrectAnswers = app.requiredCorrectAnswers,
                        windowStartMinutes = app.windowStartMinutes,
                        windowEndMinutes = app.windowEndMinutes,
                    )
            socialGateRepository.upsertRule(SocialGateSchedule.normalizeRule(transform(current)))
        }
    }

    private suspend fun buildUiState(rules: List<SocialGateRule>): SocialGateSettingsUiState {
        val runtime = socialGateRepository.getRuntimeState()
        val todayKey = todayKeyProvider()
        val solvedTodayByPackage =
            socialGateRepository.getDailyStates()
                .filter { it.localDate == todayKey }
                .associate { it.packageName to it.solvedCount }

        return SocialGateSettingsUiState(
            isLoading = false,
            installedApps = installedAppsResolver(appContext, rules),
            solvedTodayByPackage = solvedTodayByPackage,
            runtimePhase = runtime.phase,
            runtimeTargetPackageName = runtime.targetPackageName,
            unlockTokenPackageName = runtime.unlockTokenPackageName,
            lastForegroundPackageName = runtime.lastForegroundPackageName,
        )
    }
}
