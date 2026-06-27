package com.estudio.antiprocrastinacion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.estudio.antiprocrastinacion.app.ui.common.StudyTheme
import com.estudio.antiprocrastinacion.app.ui.home.HomeScreen
import com.estudio.antiprocrastinacion.app.ui.home.HomeUiState
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersOnlyDirectActions() {
        composeRule.setContent {
            StudyTheme {
                HomeScreen(
                    state = HomeUiState(isLoading = false),
                    onStartQuick = {},
                    onOpenContent = {},
                    onOpenImport = {},
                    onOpenUpcomingReviews = {},
                    onOpenNotificationSettings = {},
                    onOpenSocialGateSettings = {},
                    onOpenDeep = {},
                    onOpenDrain = {},
                )
            }
        }

        composeRule.onNodeWithText("Estudiar").assertIsDisplayed()
        composeRule.onNodeWithText("Tarjetas pendientes").assertIsDisplayed()
        composeRule.onNodeWithText("Modo profundo").assertIsDisplayed()
        composeRule.onNodeWithText("Vaciar").assertIsDisplayed()
        composeRule.onNodeWithText("Contenido").assertIsDisplayed()
        composeRule.onNodeWithText("Importar").assertIsDisplayed()
        composeRule.onNodeWithText("Notificación").assertIsDisplayed()
        composeRule.onNodeWithText("Gate social").assertIsDisplayed()
    }

    @Test
    fun doesNotExposeSummaryOrDuplicatedModeChoice() {
        composeRule.setContent {
            StudyTheme {
                HomeScreen(
                    state =
                        HomeUiState(
                            isLoading = false,
                            quickSessionSummary =
                                com.estudio.antiprocrastinacion.app.ui.home.ActiveSessionSummary(
                                    sessionId = "session-1",
                                    topicTitle = "Tema",
                                    modeLabel = "Tarjetas pendientes",
                                    progressLabel = "Progreso 1/3",
                                ),
                        ),
                    onStartQuick = {},
                    onOpenContent = {},
                    onOpenImport = {},
                    onOpenUpcomingReviews = {},
                    onOpenNotificationSettings = {},
                    onOpenSocialGateSettings = {},
                    onOpenDeep = {},
                    onOpenDrain = {},
                )
            }
        }

        composeRule.onNodeWithText("Modo profundo").assertIsDisplayed()
        composeRule.onAllNodesWithText("Continuar").assertCountEquals(0)
        composeRule.onAllNodesWithText("Descartar sesión").assertCountEquals(0)
    }
}
