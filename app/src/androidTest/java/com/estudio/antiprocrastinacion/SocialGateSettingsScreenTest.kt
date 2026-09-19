package com.estudio.antiprocrastinacion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import com.estudio.antiprocrastinacion.app.model.state.SocialGateInstalledApp
import com.estudio.antiprocrastinacion.app.socialgate.SocialGateGuardStatus
import com.estudio.antiprocrastinacion.app.ui.common.StudyTheme
import com.estudio.antiprocrastinacion.app.ui.settings.SocialGateSettingsScreen
import com.estudio.antiprocrastinacion.app.ui.settings.SocialGateSettingsUiState
import org.junit.Rule
import org.junit.Test

class SocialGateSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersScheduleConfigurationAndPreview() {
        composeRule.setContent {
            StudyTheme {
                SocialGateSettingsScreen(
                    state =
                        SocialGateSettingsUiState(
                            isLoading = false,
                            installedApps =
                                listOf(
                                    SocialGateInstalledApp(
                                        packageName = "com.instagram.android",
                                        displayName = "Instagram",
                                        installed = true,
                                        enabled = true,
                                        maxTriggersPerDay = 3,
                                        requiredCorrectAnswers = 2,
                                        windowStartMinutes = 0,
                                        windowEndMinutes = 22 * 60,
                        ),
                        ),
                    ),
                    guardStatus =
                        SocialGateGuardStatus(
                            usageAccessGranted = false,
                            backgroundLaunchGranted = false,
                            serviceRunning = false,
                        ),
                    onBack = {},
                    onOpenUsageAccessSettings = {},
                    onOpenOverlaySettings = {},
                    onMaxTriggersPerDayChange = { _, _ -> },
                    onWindowStartMinutesChange = { _, _ -> },
                    onWindowEndMinutesChange = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Gate social").assertIsDisplayed()
        composeRule.onNodeWithText("Conceder acceso de uso").assertIsDisplayed()
        composeRule.onNodeWithText("Permitir reapertura en segundo plano").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Editar configuración"))
        composeRule.onNodeWithText("Instagram").assertIsDisplayed()
        composeRule.onNodeWithText("Editar configuración").assertIsDisplayed()
        composeRule.onNodeWithText("Hoy: 0 de 3").assertIsDisplayed()
    }

    @Test
    fun clickingTimeButtonOpensPickerDialog() {
        composeRule.setContent {
            StudyTheme {
                SocialGateSettingsScreen(
                    state =
                        SocialGateSettingsUiState(
                            isLoading = false,
                            installedApps =
                                listOf(
                                    SocialGateInstalledApp(
                                        packageName = "com.instagram.android",
                                        displayName = "Instagram",
                                        installed = true,
                                        enabled = true,
                                        maxTriggersPerDay = 2,
                                        requiredCorrectAnswers = 2,
                                        windowStartMinutes = 8 * 60,
                                        windowEndMinutes = 20 * 60,
                        ),
                        ),
                    ),
                    guardStatus =
                        SocialGateGuardStatus(
                            usageAccessGranted = true,
                            backgroundLaunchGranted = true,
                            serviceRunning = true,
                        ),
                    onBack = {},
                    onOpenUsageAccessSettings = {},
                    onOpenOverlaySettings = {},
                    onMaxTriggersPerDayChange = { _, _ -> },
                    onWindowStartMinutesChange = { _, _ -> },
                    onWindowEndMinutesChange = { _, _ -> },
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Editar configuración"))
        composeRule.onNodeWithText("Editar configuración").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Desde 08:00"))
        composeRule.onNodeWithText("Desde 08:00").performClick()
        composeRule.onNodeWithText("Hora de inicio").assertIsDisplayed()
        composeRule.onNodeWithText("Guardar").assertIsDisplayed()
        composeRule.onAllNodesWithText("Conceder acceso de uso").assertCountEquals(0)
        composeRule.onAllNodesWithText("Permitir reapertura en segundo plano").assertCountEquals(0)
    }
}
