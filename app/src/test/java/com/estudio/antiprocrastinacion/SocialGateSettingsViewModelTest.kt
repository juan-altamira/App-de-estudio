package com.estudio.antiprocrastinacion

import android.content.ContextWrapper
import com.estudio.antiprocrastinacion.app.domain.repository.SocialGateRepository
import com.estudio.antiprocrastinacion.app.model.state.SocialGateDailyState
import com.estudio.antiprocrastinacion.app.model.state.SocialGateInstalledApp
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRule
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRuntimePhase
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRuntimeState
import com.estudio.antiprocrastinacion.app.ui.settings.SocialGateSettingsViewModel
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
class SocialGateSettingsViewModelTest {
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
    fun `ui state reflects resolver output and existing persisted rule`() = runTest(dispatcher) {
        val repository =
            FakeSettingsSocialGateRepository(
                initialRules =
                    listOf(
                        SocialGateRule(
                            packageName = "com.instagram.android",
                            displayName = "Instagram",
                            enabled = true,
                            maxTriggersPerDay = 4,
                            requiredCorrectAnswers = 5,
                            windowStartMinutes = 6 * 60,
                            windowEndMinutes = 22 * 60,
                        ),
                    ),
                initialDailyStates =
                    listOf(
                        SocialGateDailyState(
                            packageName = "com.instagram.android",
                            localDate = "2026-04-14",
                            solvedCount = 2,
                            lastSolvedAt = 200L,
                        ),
                    ),
                initialRuntimeState =
                    SocialGateRuntimeState(
                        phase = SocialGateRuntimePhase.ACTIVE_GATE,
                        targetPackageName = "com.instagram.android",
                        lastForegroundPackageName = "com.instagram.android",
                    ),
            )
        val viewModel =
            SocialGateSettingsViewModel(
                appContext = ContextWrapper(null),
                socialGateRepository = repository,
                installedAppsResolver = { _, rules ->
                    listOf(
                        SocialGateInstalledApp(
                            packageName = "com.instagram.android",
                            displayName = "Instagram",
                            installed = true,
                            enabled = rules.single().enabled,
                            maxTriggersPerDay = rules.single().maxTriggersPerDay,
                            requiredCorrectAnswers = rules.single().requiredCorrectAnswers,
                            windowStartMinutes = rules.single().windowStartMinutes,
                            windowEndMinutes = rules.single().windowEndMinutes,
                        ),
                    )
                },
                todayKeyProvider = { "2026-04-14" },
            )

        advanceUntilIdle()

        val installedApp = viewModel.uiState.value.installedApps.single()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(installedApp.enabled).isTrue()
        assertThat(installedApp.maxTriggersPerDay).isEqualTo(4)
        assertThat(installedApp.requiredCorrectAnswers).isEqualTo(5)
        assertThat(installedApp.windowStartMinutes).isEqualTo(6 * 60)
        assertThat(installedApp.windowEndMinutes).isEqualTo(22 * 60)
        assertThat(viewModel.uiState.value.solvedTodayByPackage["com.instagram.android"]).isEqualTo(2)
        assertThat(viewModel.uiState.value.runtimePhase).isEqualTo(SocialGateRuntimePhase.ACTIVE_GATE)
        assertThat(viewModel.uiState.value.runtimeTargetPackageName).isEqualTo("com.instagram.android")
    }

    @Test
    fun `updates create and clamp rule values from visible installed app`() = runTest(dispatcher) {
        val repository = FakeSettingsSocialGateRepository()
        val viewModel =
            SocialGateSettingsViewModel(
                appContext = ContextWrapper(null),
                socialGateRepository = repository,
                installedAppsResolver = { _, _ ->
                    listOf(
                        SocialGateInstalledApp(
                            packageName = "com.instagram.android",
                            displayName = "Instagram",
                            installed = true,
                            enabled = false,
                            maxTriggersPerDay = 1,
                            requiredCorrectAnswers = 3,
                            windowStartMinutes = 0,
                            windowEndMinutes = 22 * 60,
                        ),
                    )
                },
                todayKeyProvider = { "2026-04-14" },
            )

        advanceUntilIdle()
        viewModel.updateRuleEnabled("com.instagram.android", true)
        viewModel.updateMaxTriggersPerDay("com.instagram.android", 99)
        viewModel.updateRequiredCorrectAnswers("com.instagram.android", 0)
        advanceUntilIdle()

        val rule = repository.getRule("com.instagram.android")
        assertThat(rule).isNotNull()
        assertThat(rule!!.enabled).isTrue()
        assertThat(rule.maxTriggersPerDay).isEqualTo(10)
        assertThat(rule.requiredCorrectAnswers).isEqualTo(1)
    }

    @Test
    fun `window updates are normalized and preserved in the stored rule`() = runTest(dispatcher) {
        val repository = FakeSettingsSocialGateRepository()
        val viewModel =
            SocialGateSettingsViewModel(
                appContext = ContextWrapper(null),
                socialGateRepository = repository,
                installedAppsResolver = { _, _ ->
                    listOf(
                        SocialGateInstalledApp(
                            packageName = "com.instagram.android",
                            displayName = "Instagram",
                            installed = true,
                            enabled = false,
                            maxTriggersPerDay = 3,
                            requiredCorrectAnswers = 3,
                            windowStartMinutes = 8 * 60,
                            windowEndMinutes = 20 * 60,
                        ),
                    )
                },
                todayKeyProvider = { "2026-04-14" },
            )

        advanceUntilIdle()
        viewModel.updateWindowStartMinutes("com.instagram.android", 22 * 60)
        viewModel.updateWindowEndMinutes("com.instagram.android", 21 * 60)
        advanceUntilIdle()

        val rule = repository.getRule("com.instagram.android")
        assertThat(rule).isNotNull()
        assertThat(rule!!.windowStartMinutes).isEqualTo(22 * 60)
        assertThat(rule.windowEndMinutes).isEqualTo(23 * 60)
    }

    @Test
    fun `refresh status pulls latest daily counts and runtime state without waiting for rule changes`() = runTest(dispatcher) {
        val repository = FakeSettingsSocialGateRepository()
        val viewModel =
            SocialGateSettingsViewModel(
                appContext = ContextWrapper(null),
                socialGateRepository = repository,
                installedAppsResolver = { _, _ ->
                    listOf(
                        SocialGateInstalledApp(
                            packageName = "com.instagram.android",
                            displayName = "Instagram",
                            installed = true,
                            enabled = true,
                            maxTriggersPerDay = 3,
                            requiredCorrectAnswers = 2,
                            windowStartMinutes = 9 * 60,
                            windowEndMinutes = 21 * 60,
                        ),
                    )
                },
                todayKeyProvider = { "2026-04-14" },
            )

        advanceUntilIdle()
        repository.replaceState(
            rules =
                listOf(
                    SocialGateRule(
                        packageName = "com.instagram.android",
                        displayName = "Instagram",
                        enabled = true,
                        maxTriggersPerDay = 3,
                        requiredCorrectAnswers = 2,
                        windowStartMinutes = 9 * 60,
                        windowEndMinutes = 21 * 60,
                    ),
                ),
            dailyStates =
                listOf(
                    SocialGateDailyState(
                        packageName = "com.instagram.android",
                        localDate = "2026-04-14",
                        solvedCount = 1,
                        lastSolvedAt = 100L,
                    ),
                ),
            runtimeState =
                SocialGateRuntimeState(
                    phase = SocialGateRuntimePhase.UNLOCKED_FOR_CURRENT_FOREGROUND,
                    unlockTokenPackageName = "com.instagram.android",
                    lastForegroundPackageName = "com.instagram.android",
                ),
        )

        viewModel.refreshStatus()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.solvedTodayByPackage["com.instagram.android"]).isEqualTo(1)
        assertThat(viewModel.uiState.value.runtimePhase).isEqualTo(SocialGateRuntimePhase.UNLOCKED_FOR_CURRENT_FOREGROUND)
        assertThat(viewModel.uiState.value.unlockTokenPackageName).isEqualTo("com.instagram.android")
    }
}

private class FakeSettingsSocialGateRepository(
    initialRules: List<SocialGateRule> = emptyList(),
    initialDailyStates: List<SocialGateDailyState> = emptyList(),
    initialRuntimeState: SocialGateRuntimeState = SocialGateRuntimeState(),
) : SocialGateRepository {
    private val rulesFlow = MutableStateFlow(initialRules)
    private var dailyStates = initialDailyStates
    private var runtimeState = initialRuntimeState

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
