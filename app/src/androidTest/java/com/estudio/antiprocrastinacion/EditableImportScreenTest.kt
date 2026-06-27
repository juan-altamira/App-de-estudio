package com.estudio.antiprocrastinacion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationMessage
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationReport
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableCourseDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableImportDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableQuestionDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableUnitDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedStableKeySource
import com.estudio.antiprocrastinacion.app.ui.common.StudyTheme
import com.estudio.antiprocrastinacion.app.ui.content.EditableImportScreen
import com.estudio.antiprocrastinacion.app.ui.content.EditableImportStep
import com.estudio.antiprocrastinacion.app.ui.content.EditableImportUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditableImportScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sourceStepHasExactlyOneVisibleInputSurfaceForText() {
        composeRule.setContent {
            StudyTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 460.dp)) {
                    editableImportScreen(state = EditableImportUiState())
                }
            }
        }

        composeRule.onNodeWithTag("editable_import_source").assertIsDisplayed()
        composeRule.onNodeWithText("Texto fuente").assertIsDisplayed()
        composeRule.onAllNodesWithText("ID curso").assertCountEquals(0)
        composeRule.onAllNodesWithText("ID unidad").assertCountEquals(0)
        composeRule.onNodeWithText("Analizar").assertIsDisplayed()
    }

    @Test
    fun sourceStepKeepsInputCompactAndActionsReachableInKeyboardSizedViewport() {
        composeRule.setContent {
            StudyTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 420.dp)) {
                    editableImportScreen(state = EditableImportUiState())
                }
            }
        }

        val inputBounds = composeRule.onNodeWithTag("editable_import_source_input").getUnclippedBoundsInRoot()
        val inputHeight = inputBounds.bottom - inputBounds.top

        assertTrue("El campo fuente no debe crecer hasta tapar la pantalla", inputHeight.value <= 300f)
        composeRule.onNodeWithTag("editable_import_source").assertIsDisplayed()
        composeRule.onNodeWithText("Limpiar").assertIsDisplayed()
        composeRule.onNodeWithText("Analizar").assertIsDisplayed()
    }

    @Test
    fun diagnosticsExpandOnTapWithPreciseErrorDetails() {
        composeRule.setContent {
            StudyTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 760.dp)) {
                    editableImportScreen(
                        state =
                            EditableImportUiState(
                                sourceText = "Curso: Ethereum\nUnidad: Base\nPregunta: incompleta",
                                draft = detectedDraft(),
                                analysisReport =
                                    ValidationReport(
                                        structuralErrors =
                                            listOf(
                                                ValidationMessage(
                                                    code = "question_correct_missing:q1",
                                                    message = "La pregunta q1 no tiene respuesta correcta.",
                                                    path = "$.units[0].questions[0].correctAnswer",
                                                    expected = "Respuesta correcta explicita.",
                                                    actual = "Texto vacio.",
                                                    hint = "Agregá Respuesta: dentro del bloque.",
                                                ),
                                            ),
                                        authoringWarnings = emptyList(),
                                    ),
                            ),
                    )
                }
            }
        }

        composeRule.onNodeWithText("Diagnóstico técnico").performClick()
        composeRule.onNodeWithText("question_correct_missing:q1", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Ubicacion: $.units[0].questions[0].correctAnswer").assertIsDisplayed()
        composeRule.onNodeWithText("Accion: Agregá Respuesta: dentro del bloque.").assertIsDisplayed()
    }

    @Test
    fun previewStepKeepsApproveActionReachable() {
        composeRule.setContent {
            StudyTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 620.dp)) {
                    editableImportScreen(
                        state =
                            EditableImportUiState(
                                step = EditableImportStep.PREVIEW,
                                draft = detectedDraft(),
                                compiledJson = "{}",
                                finalReport = ValidationReport(emptyList(), emptyList()),
                            ),
                    )
                }
            }
        }

        composeRule.onAllNodesWithText("Texto fuente").assertCountEquals(0)
        composeRule.onNodeWithText("Ethereum").assertIsDisplayed()
        composeRule.onNodeWithText("Base").assertIsDisplayed()
        composeRule.onNodeWithText("Aprobar").assertIsDisplayed()
    }

    @Test
    fun technicalIdsAreHiddenFromUserPreview() {
        val longUnitKey = "ethereum_como_sistema_completo_tesis_central"
        val longQuestionKey = "que_recurso_limitado_genera_competencia_economica_dentro_de_ethereum"

        composeRule.setContent {
            StudyTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 720.dp)) {
                    editableImportScreen(
                        state =
                            EditableImportUiState(
                                sourceText = "texto",
                                draft =
                                    ReviewedEditableImportDraft(
                                        course =
                                            ReviewedEditableCourseDraft(
                                                title = "Ethereum",
                                                key = "ethereum_formacion_conceptual_extendida",
                                                keySource = ReviewedStableKeySource.GENERATED,
                                            ),
                                        units =
                                            listOf(
                                                ReviewedEditableUnitDraft(
                                                    title = "Ethereum como sistema completo y tesis central",
                                                    key = longUnitKey,
                                                    keySource = ReviewedStableKeySource.GENERATED,
                                                    questions =
                                                        listOf(
                                                            ReviewedEditableQuestionDraft(
                                                                key = longQuestionKey,
                                                                keySource = ReviewedStableKeySource.GENERATED,
                                                                stem = "¿Qué recurso limitado genera competencia económica dentro de Ethereum?",
                                                                correctAnswer = "Blockspace.",
                                                                feedback = "Respuesta correcta: Blockspace.",
                                                            ),
                                                        ),
                                                ),
                                            ),
                                    ),
                            ),
                    )
                }
            }
        }

        composeRule.onAllNodesWithText(longUnitKey).assertCountEquals(0)
        composeRule.onAllNodesWithText(longQuestionKey).assertCountEquals(0)
        composeRule.onAllNodesWithText("ID unidad", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("ID pregunta", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("_", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("Respuesta esperada").assertIsDisplayed()
        composeRule.onNodeWithText("Tipo de respuesta").assertIsDisplayed()
        composeRule.onNodeWithText("Ver respuesta").assertIsDisplayed()
    }

    @Test
    fun questionAddAndDeleteActionsAreVisibleFromPreview() {
        var addedUnitIndex: Int? = null
        var removedQuestion: Pair<Int, Int>? = null

        composeRule.setContent {
            StudyTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 760.dp)) {
                    editableImportScreen(
                        state =
                            EditableImportUiState(
                                step = EditableImportStep.PREVIEW,
                                draft = detectedDraft(),
                                compiledJson = "{}",
                                finalReport = ValidationReport(emptyList(), emptyList()),
                            ),
                        onAddQuestion = { unitIndex -> addedUnitIndex = unitIndex },
                        onRemoveQuestion = { unitIndex, questionIndex -> removedQuestion = unitIndex to questionIndex },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Agregar pregunta").performClick()
        composeRule.onNodeWithContentDescription("Eliminar pregunta 1").performClick()

        assertTrue("Debe agregar en la unidad visible", addedUnitIndex == 0)
        assertTrue("Debe eliminar la pregunta visible", removedQuestion == (0 to 0))
    }
}

@Composable
private fun editableImportScreen(
    state: EditableImportUiState,
    onAddQuestion: (Int) -> Unit = {},
    onRemoveQuestion: (Int, Int) -> Unit = { _, _ -> },
) {
    EditableImportScreen(
        state = state,
        onBack = {},
        onBackToSource = {},
        onSourceTextChange = {},
        onAnalyzeSource = {},
        onPrepareDraft = {},
        onClearSource = {},
        onConfirmImport = {},
        onCourseTitleChange = {},
        onUnitTitleChange = { _, _ -> },
        onQuestionFormatChange = { _, _, _ -> },
        onQuestionStemChange = { _, _, _ -> },
        onQuestionCorrectAnswerChange = { _, _, _ -> },
        onQuestionFeedbackChange = { _, _, _ -> },
        onQuestionCorrectedConfusionChange = { _, _, _ -> },
        onOptionTextChange = { _, _, _, _ -> },
        onMarkOptionCorrect = { _, _, _ -> },
        onAddOption = { _, _ -> },
        onRemoveOption = { _, _, _ -> },
        onAddQuestion = onAddQuestion,
        onRemoveQuestion = onRemoveQuestion,
    )
}

private fun detectedDraft(): ReviewedEditableImportDraft =
    ReviewedEditableImportDraft(
        course = ReviewedEditableCourseDraft(title = "Ethereum", key = "eth"),
        units =
            listOf(
                ReviewedEditableUnitDraft(
                    title = "Base",
                    key = "base",
                    questions =
                        listOf(
                            ReviewedEditableQuestionDraft(
                                key = "q1",
                                stem = "Pregunta incompleta",
                                correctAnswer = "",
                                feedback = "",
                            ),
                        ),
                ),
            ),
    )
