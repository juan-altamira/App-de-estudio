package com.estudio.antiprocrastinacion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.estudio.antiprocrastinacion.app.ui.home.HomeScreen
import com.estudio.antiprocrastinacion.app.ui.home.HomeUiState
import com.estudio.antiprocrastinacion.app.ui.navigation.NavRoutes
import com.estudio.antiprocrastinacion.app.ui.study.quick.AnswerFeedback
import com.estudio.antiprocrastinacion.app.ui.study.quick.PendingAnswer
import com.estudio.antiprocrastinacion.app.ui.study.quick.QuickStudyScreen
import com.estudio.antiprocrastinacion.app.ui.study.quick.QuickStudyUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyUiFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun quickStart_fromHome_navigatesToStudy_withoutReturningHome() {
        composeRule.setContent {
            StudyTheme {
                StudyUiFlowHost()
            }
        }

        composeRule.onNodeWithText("Estudiar").assertIsDisplayed()
        composeRule.onNodeWithText("Tarjetas pendientes").performClick()

        composeRule.onNodeWithText("Rol de los checkpoints en Ethereum").assertIsDisplayed()
        composeRule.onNodeWithText("0/4").assertIsDisplayed()
        composeRule.onAllNodesWithText("Estudiar").assertCountEquals(0)
    }

    @Test
    fun feedback_isVisible_and_requires_manual_continue_before_next_prompt() {
        composeRule.setContent {
            StudyTheme {
                StudyUiFlowHost()
            }
        }

        composeRule.onNodeWithText("Tarjetas pendientes").performClick()
        composeRule.onNodeWithText("Falso").performClick()

        composeRule.onNodeWithText("Correcto").assertIsDisplayed()
        composeRule
            .onNodeWithText("Checkpoint es un bloque de referencia para seguir justificación y finality entre epochs.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Seguir").assertIsDisplayed()
        composeRule.onNodeWithText("Cambiar respuesta").assertIsDisplayed()
        composeRule.onNodeWithText("¿Qué describe mejor a un checkpoint en Ethereum?").assertIsDisplayed()
        composeRule.onAllNodesWithText("Un checkpoint justified y uno finalized significan exactamente lo mismo.").assertCountEquals(0)

        composeRule.onNodeWithText("Seguir").performClick()

        composeRule.onNodeWithText("Un checkpoint justified y uno finalized significan exactamente lo mismo.").assertIsDisplayed()
        composeRule.onNodeWithText("1/4").assertIsDisplayed()
        composeRule.onAllNodesWithText("Correcto").assertCountEquals(0)
        composeRule.onAllNodesWithText("Estudiar").assertCountEquals(0)
    }

    @Test
    fun answer_can_be_changed_before_continue_without_advancing() {
        composeRule.setContent {
            StudyTheme {
                StudyUiFlowHost()
            }
        }

        composeRule.onNodeWithText("Tarjetas pendientes").performClick()
        composeRule.onNodeWithText("Verdadero").performClick()

        composeRule.onNodeWithText("No").assertIsDisplayed()
        composeRule.onNodeWithText("Cambiar respuesta").performClick()
        composeRule.onAllNodesWithText("No").assertCountEquals(0)
        composeRule.onAllNodesWithText("Seguir").assertCountEquals(0)

        composeRule.onNodeWithText("Falso").performClick()
        composeRule.onNodeWithText("Correcto").assertIsDisplayed()
        composeRule.onNodeWithText("Seguir").performClick()

        composeRule.onNodeWithText("Un checkpoint justified y uno finalized significan exactamente lo mismo.").assertIsDisplayed()
    }

    @Test
    fun backPress_isIntercepted_inStudy_and_showsMicroPrompt_insteadOfHome() {
        composeRule.setContent {
            StudyTheme {
                StudyUiFlowHost()
            }
        }

        composeRule.onNodeWithText("Tarjetas pendientes").performClick()
        composeRule.onNodeWithText("¿Qué describe mejor a un checkpoint en Ethereum?").assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithText("Micro: checkpoint es un bloque de referencia entre epochs.").assertIsDisplayed()
        composeRule.onAllNodesWithText("¿Qué describe mejor a un checkpoint en Ethereum?").assertCountEquals(0)
        composeRule.onAllNodesWithText("Estudiar").assertCountEquals(0)
    }

    @Test
    fun fillFormat_usesRevealAndSelfAssessment_withoutFreeTextInput() {
        composeRule.setContent {
            StudyTheme {
                StudyUiFlowHost(
                    initialPrompt = fillPrompt(stepIndex = 0, correctCount = 0),
                    nextPrompt = nextQuickPrompt(stepIndex = 1, correctCount = 1),
                )
            }
        }

        composeRule.onNodeWithText("Tarjetas pendientes").performClick()

        composeRule.onNodeWithText("Checkpoint es un bloque de ____ dentro del progreso por epochs.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Tu respuesta").assertCountEquals(0)
        composeRule.onAllNodesWithText("Responder").assertCountEquals(0)
        composeRule.onAllNodesWithText("Responder texto").assertCountEquals(0)
        composeRule.onAllNodesWithText("O escribí V/F").assertCountEquals(0)
        composeRule.onNodeWithText("Ver respuesta").assertIsDisplayed()

        composeRule.onNodeWithText("Ver respuesta").performClick()

        composeRule.onNodeWithText("referencia").assertIsDisplayed()
        composeRule.onNodeWithText("La acerté").assertIsDisplayed()
        composeRule.onNodeWithText("La fallé").assertIsDisplayed()
    }
}

@Composable
private fun StudyUiFlowHost(
    initialPrompt: StudyPrompt = quickPrompt(stepIndex = 0, correctCount = 0),
    nextPrompt: StudyPrompt = nextQuickPrompt(stepIndex = 1, correctCount = 1),
) {
    val navController = rememberNavController()
    val rememberedInitialPrompt = remember(initialPrompt) { initialPrompt }
    val rememberedNextPrompt = remember(nextPrompt) { nextPrompt }
    val microPrompt = remember { microPrompt(stepIndex = 0, correctCount = 0) }
    var currentPrompt by remember { mutableStateOf(rememberedInitialPrompt) }
    var answerFeedback by remember { mutableStateOf<AnswerFeedback?>(null) }
    var pendingAnswer by remember { mutableStateOf<PendingAnswer?>(null) }
    var selectedResponseText by remember { mutableStateOf<String?>(null) }
    var backPressCount by remember { mutableIntStateOf(0) }

    NavHost(
        navController = navController,
        startDestination = NavRoutes.HOME,
    ) {
        composable(NavRoutes.HOME) {
            HomeScreen(
                state =
                    HomeUiState(
                        isLoading = false,
                        hasRealImportedContent = true,
                    ),
                onStartQuick = { navController.navigate(NavRoutes.study(currentPrompt.session.sessionId)) },
                onOpenContent = {},
                onOpenImport = {},
                onOpenUpcomingReviews = {},
                onOpenNotificationSettings = {},
                onOpenSocialGateSettings = {},
                onOpenDeep = {},
                onOpenDrain = {},
            )
        }
        composable(
            route = "${NavRoutes.STUDY}/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) {
            QuickStudyScreen(
                state =
                    QuickStudyUiState(
                        isLoading = false,
                        prompt = currentPrompt,
                        answerFeedback = answerFeedback,
                        pendingAnswer = pendingAnswer,
                        selectedResponseText = selectedResponseText,
                    ),
                onSubmitAnswer = { responseText, isCorrect, _ ->
                    answerFeedback =
                        AnswerFeedback(
                            isCorrect = isCorrect,
                            title = if (isCorrect) "Correcto" else "Incorrecto",
                            detail = currentPrompt.item.feedbackShort,
                        )
                    pendingAnswer =
                        PendingAnswer(
                            responseText = responseText,
                            isCorrect = isCorrect,
                            latencyMs = 1L,
                        )
                    selectedResponseText = responseText
                },
                onRevealAndSelfAssess = { responseText, isCorrect, _ ->
                    answerFeedback =
                        AnswerFeedback(
                            isCorrect = isCorrect,
                            title = if (isCorrect) "Correcto" else "Incorrecto",
                            detail = currentPrompt.item.feedbackShort,
                        )
                    pendingAnswer =
                        PendingAnswer(
                            responseText = responseText,
                            isCorrect = isCorrect,
                            latencyMs = 1L,
                        )
                    selectedResponseText = responseText
                },
                onRevealAnswer = {},
                onBackPressed = {
                    backPressCount += 1
                    currentPrompt = microPrompt.copy(promptIndex = backPressCount)
                    answerFeedback = null
                    pendingAnswer = null
                    selectedResponseText = null
                },
                onTimeout = {},
                onContinueAfterFeedback = {
                    currentPrompt = rememberedNextPrompt
                    answerFeedback = null
                    pendingAnswer = null
                    selectedResponseText = null
                },
                onRetryCurrentPrompt = {
                    answerFeedback = null
                    pendingAnswer = null
                    selectedResponseText = null
                },
            )
        }
    }
}

private fun quickPrompt(
    stepIndex: Int,
    correctCount: Int,
): StudyPrompt =
    studyPrompt(
        sessionId = "session-quick",
        itemId = "eth-item-1",
        stem = "¿Qué describe mejor a un checkpoint en Ethereum?",
        correctAnswer = "Falso",
        feedbackShort = "Checkpoint es un bloque de referencia para seguir justificación y finality entre epochs.",
        stepIndex = stepIndex,
        correctCount = correctCount,
        promptIndex = stepIndex + 1,
    )

private fun nextQuickPrompt(
    stepIndex: Int,
    correctCount: Int,
): StudyPrompt =
    studyPrompt(
        sessionId = "session-quick",
        itemId = "eth-item-2",
        stem = "Un checkpoint justified y uno finalized significan exactamente lo mismo.",
        correctAnswer = "Falso",
        feedbackShort = "Finalized implica una garantía más fuerte que justified.",
        stepIndex = stepIndex,
        correctCount = correctCount,
        promptIndex = stepIndex + 1,
    )

private fun fillPrompt(
    stepIndex: Int,
    correctCount: Int,
): StudyPrompt =
    studyPrompt(
        sessionId = "session-quick",
        itemId = "eth-item-fill",
        stem = "Checkpoint es un bloque de ____ dentro del progreso por epochs.",
        correctAnswer = "referencia",
        feedbackShort = "La palabra clave es referencia.",
        stepIndex = stepIndex,
        correctCount = correctCount,
        promptIndex = stepIndex + 1,
        format = ItemFormat.FILL_ONE_WORD,
    )

private fun microPrompt(
    stepIndex: Int,
    correctCount: Int,
): StudyPrompt =
    studyPrompt(
        sessionId = "session-quick",
        itemId = "eth-item-micro",
        stem = "Micro: checkpoint es un bloque de referencia entre epochs.",
        correctAnswer = "Verdadero",
        feedbackShort = "Checkpoint funciona como referencia entre epochs.",
        stepIndex = stepIndex,
        correctCount = correctCount,
        promptIndex = stepIndex + 1,
    )

private fun studyPrompt(
    sessionId: String,
    itemId: String,
    stem: String,
    correctAnswer: String,
    feedbackShort: String,
    stepIndex: Int,
    correctCount: Int,
    promptIndex: Int,
    format: ItemFormat = ItemFormat.TRUE_FALSE,
): StudyPrompt {
    val session =
        StudySession(
            sessionId = sessionId,
            mode = SessionMode.QUICK,
            surface = Surface.IN_APP_QUICK,
            packetId = "packet-quick",
            topicUnitId = "eth-checkpoints",
            currentTopicTitle = "Checkpoints y Finality",
            queueItemIds = emptyList(),
            rescueQueueItemIds = emptyList(),
            goalCorrectCount = 4,
            currentNodeId = "eth-node-1",
            currentItemId = itemId,
            currentAttemptIndex = 1,
            correctCount = correctCount,
            stepIndex = stepIndex,
            startedAt = 1L,
            lastInteractionAt = 1L,
            isMicroPromptActive = itemId == "eth-item-micro",
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
                itemId = itemId,
                nodeId = "eth-node-1",
                facet = FacetType.DEFINICION_FUNCIONAL,
                format = format,
                frictionLevel = 1,
                difficultySeed = 0.2,
                itemRole = ItemRole.CORE,
                allowedSurfaces = listOf(Surface.IN_APP_QUICK, Surface.BACK_MICRO),
                cooldownHours = 12.0,
                stem = stem,
                correctAnswer = correctAnswer,
                feedbackShort = feedbackShort,
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
    )
}
