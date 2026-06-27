package com.estudio.antiprocrastinacion

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.estudio.antiprocrastinacion.app.model.content.ContentOrigin
import com.estudio.antiprocrastinacion.app.model.content.FacetType
import com.estudio.antiprocrastinacion.app.model.content.Item
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.Node
import com.estudio.antiprocrastinacion.app.model.content.NodeType
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.state.StudyPrompt
import com.estudio.antiprocrastinacion.app.model.state.StudySession
import com.estudio.antiprocrastinacion.app.ui.common.StudyTheme
import com.estudio.antiprocrastinacion.app.ui.study.quick.QuickStudyScreen
import com.estudio.antiprocrastinacion.app.ui.study.quick.QuickStudyUiState
import org.junit.Rule
import org.junit.Test

class QuickStudyScreenModeContextTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun studyScreen_usesCompactHeader_withoutModeBanners() {
        composeRule.setContent {
            StudyTheme {
                QuickStudyScreen(
                    state = QuickStudyUiState(isLoading = false, prompt = samplePrompt(mode = SessionMode.DEEP)),
                    onSubmitAnswer = { _, _, _ -> },
                    onRevealAndSelfAssess = { _, _, _ -> },
                    onRevealAnswer = {},
                    onBackPressed = {},
                    onTimeout = {},
                    onContinueAfterFeedback = {},
                )
            }
        }

        composeRule.onNodeWithText("Checkpoints y Finality").assertIsDisplayed()
        composeRule.onNodeWithText("1/4").assertIsDisplayed()
        composeRule.onNodeWithText("Rol de los checkpoints en Ethereum").assertIsDisplayed()
        composeRule.onNodeWithText("¿Checkpoint y finalized nombran lo mismo?").assertIsDisplayed()
        composeRule.onAllNodesWithText("Modo profundo").assertCountEquals(0)
        composeRule.onAllNodesWithText("Vaciar tema").assertCountEquals(0)
    }

    @Test
    fun socialGateContinuation_showsPromptOrdinalProgress() {
        composeRule.setContent {
            StudyTheme {
                QuickStudyScreen(
                    state =
                        QuickStudyUiState(
                            isLoading = false,
                            prompt =
                                samplePrompt(
                                    mode = SessionMode.DRAIN,
                                    surface = Surface.SOCIAL_GATE,
                                    goalCorrectCount = 12,
                                    correctCount = 3,
                                    stepIndex = 3,
                                    promptIndex = 4,
                                ),
                        ),
                    onSubmitAnswer = { _, _, _ -> },
                    onRevealAndSelfAssess = { _, _, _ -> },
                    onRevealAnswer = {},
                    onBackPressed = {},
                    onTimeout = {},
                    onContinueAfterFeedback = {},
                )
            }
        }

        composeRule.onNodeWithText("4/12").assertIsDisplayed()
    }

    @Test
    fun studyScreen_showsPendingErrorsCounter_whenSessionStillOwesWrongItems() {
        composeRule.setContent {
            StudyTheme {
                QuickStudyScreen(
                    state =
                        QuickStudyUiState(
                            isLoading = false,
                            prompt = samplePrompt(mode = SessionMode.QUICK, pendingFailedItemCount = 2),
                        ),
                    onSubmitAnswer = { _, _, _ -> },
                    onRevealAndSelfAssess = { _, _, _ -> },
                    onRevealAnswer = {},
                    onBackPressed = {},
                    onTimeout = {},
                    onContinueAfterFeedback = {},
                )
            }
        }

        composeRule.onNodeWithText("Errores: 2").assertIsDisplayed()
    }
}

private fun samplePrompt(
    mode: SessionMode,
    surface: Surface = if (mode == SessionMode.QUICK) Surface.IN_APP_QUICK else Surface.IN_APP_DEEP,
    goalCorrectCount: Int = 4,
    correctCount: Int = 1,
    stepIndex: Int = 1,
    promptIndex: Int = stepIndex + 1,
    pendingFailedItemCount: Int = 0,
): StudyPrompt {
    val session =
        StudySession(
            sessionId = "session-mode",
            mode = mode,
            surface = surface,
            packetId = "packet-mode",
            topicUnitId = "eth-checkpoints",
            currentTopicTitle = "Checkpoints y Finality",
            queueItemIds = emptyList(),
            rescueQueueItemIds = emptyList(),
            goalCorrectCount = goalCorrectCount,
            currentNodeId = "eth-node-1",
            currentItemId = "eth-item-1",
            currentAttemptIndex = 1,
            correctCount = correctCount,
            stepIndex = stepIndex,
            startedAt = 1L,
            lastInteractionAt = 1L,
            isMicroPromptActive = false,
            isExitArmed = false,
            exitArmedUntil = null,
        )

    return StudyPrompt(
        session = session,
        node =
            Node(
                nodeId = "eth-node-1",
                courseId = "ethereum-consensus",
                unitId = "eth-checkpoints",
                outcomeIds = listOf("eth-outcome-1"),
                title = "Rol de los checkpoints en Ethereum",
                coreClaim = "Los checkpoints condensan progreso entre epochs.",
                type = NodeType.CONCEPT,
                weightExam = 0.8,
                prerequisites = emptyList(),
                facets = listOf(FacetType.DEFINICION_FUNCIONAL),
                mustKnow = listOf("Checkpoint es referencia"),
                commonErrors = listOf("Confundir justified con finalized"),
                minimumMasteryDefinition = "Distingue checkpoint, justified y finalized",
                surfaceEasyReady = true,
                surfaceEasyItemCount = 4,
                sourceRefs = listOf("manual"),
                version = 1,
                updatedAt = 1L,
                archivedCandidate = false,
                contentOrigin = ContentOrigin.IMPORTED,
            ),
        item =
            Item(
                itemId = "eth-item-1",
                nodeId = "eth-node-1",
                facet = FacetType.DEFINICION_FUNCIONAL,
                format = ItemFormat.TRUE_FALSE,
                frictionLevel = 1,
                difficultySeed = 0.2,
                itemRole = ItemRole.CORE,
                allowedSurfaces = listOf(Surface.IN_APP_QUICK, Surface.IN_APP_DEEP),
                cooldownHours = 12.0,
                stem = "¿Checkpoint y finalized nombran lo mismo?",
                correctAnswer = "Falso",
                feedbackShort = "Finalized implica una garantía más fuerte.",
                coversMustKnow = listOf("Checkpoint es referencia"),
                variantGroupId = null,
                rescueGroupId = null,
                nodeComplexity = null,
                facetComplexity = null,
                distractorSimilarity = null,
                prerequisiteDepth = null,
                targetsErrorIds = emptyList(),
                commonErrorSignals = emptyList(),
                options = emptyList(),
                version = 1,
                updatedAt = 1L,
                sourceRefs = listOf("manual"),
                contentOrigin = ContentOrigin.IMPORTED,
            ),
        promptIndex = promptIndex,
        pendingFailedItemCount = pendingFailedItemCount,
    )
}
