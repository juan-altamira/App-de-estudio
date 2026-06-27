package com.estudio.antiprocrastinacion

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.estudio.antiprocrastinacion.app.domain.scheduler.DeepModeUnitOption
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.ui.common.StudyTheme
import com.estudio.antiprocrastinacion.app.ui.study.deep.DeepStudyConfigScreen
import com.estudio.antiprocrastinacion.app.ui.study.deep.DeepStudyUiState
import org.junit.Rule
import org.junit.Test

class DeepStudyConfigScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersDirectDeepSelectionWithoutModeSelector() {
        composeRule.setContent {
            StudyTheme {
                DeepStudyConfigScreen(
                    title = "Modo profundo",
                    mode = SessionMode.DEEP,
                    state =
                        DeepStudyUiState(
                            isLoading = false,
                            units =
                                listOf(
                                    DeepModeUnitOption(
                                        unitId = "eth-checkpoints",
                                        courseTitle = "Ethereum",
                                        unitTitle = "Consensus",
                                        availableNodeCount = 3,
                                    ),
                                ),
                        ),
                    onBack = {},
                    onRefresh = {},
                    onStart = {},
                )
            }
        }

        composeRule.onNodeWithText("Modo profundo").assertIsDisplayed()
        composeRule.onNodeWithText("Consensus").assertIsDisplayed()
        composeRule.onNodeWithText("Ethereum").assertIsDisplayed()
        composeRule.onNodeWithText("Empezar").assertIsDisplayed()
        composeRule.onAllNodesWithText("Vaciar").assertCountEquals(0)
    }

    @Test
    fun rendersEmptyStateWhenNoUnitsAreAvailable() {
        composeRule.setContent {
            StudyTheme {
                DeepStudyConfigScreen(
                    title = "Vaciar",
                    mode = SessionMode.DRAIN,
                    state = DeepStudyUiState(isLoading = false, units = emptyList()),
                    onBack = {},
                    onRefresh = {},
                    onStart = {},
                )
            }
        }

        composeRule.onNodeWithText("Sin unidades").assertIsDisplayed()
        composeRule.onNodeWithText("Importá contenido.").assertIsDisplayed()
    }
}
