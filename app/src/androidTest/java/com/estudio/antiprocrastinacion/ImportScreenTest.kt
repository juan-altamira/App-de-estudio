package com.estudio.antiprocrastinacion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.estudio.antiprocrastinacion.app.domain.repository.ImportExecutionResult
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationMessage
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationReport
import com.estudio.antiprocrastinacion.app.ui.common.StudyTheme
import com.estudio.antiprocrastinacion.app.ui.content.ContentUiState
import com.estudio.antiprocrastinacion.app.ui.content.ImportScreen
import org.junit.Rule
import org.junit.Test

class ImportScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersOnlyImportActions() {
        composeRule.setContent {
            StudyTheme {
                ImportScreen(
                    state = ContentUiState(isLoading = false),
                    onBack = {},
                    onImportClick = {},
                    onImportSnapshotClick = {},
                    onExportSnapshotClick = {},
                    onImportUserStateClick = {},
                    onExportUserStateClick = {},
                    onOpenEditableImport = {},
                    onOpenManualBuilder = {},
                    onOpenManualImport = {},
                    onCloseManualImport = {},
                    onManualImportTextChange = {},
                    onClearManualImportText = {},
                    onRequestManualImportConfirmation = {},
                    onDismissManualImportConfirmation = {},
                    onConfirmManualImport = {},
                )
            }
        }

        composeRule.onNodeWithText("Importar").assertIsDisplayed()
        composeRule.onNodeWithText("Archivo").assertIsDisplayed()
        composeRule.onNodeWithText("JSON").assertIsDisplayed()
        composeRule.onNodeWithText("Texto editable").assertIsDisplayed()
        composeRule.onNodeWithText("Snapshot").assertIsDisplayed()
    }

    @Test
    fun manualJsonEditorKeepsActionsVisibleWithLongText() {
        val longJson =
            buildString {
                append("{")
                repeat(80) { index ->
                    append("\n  \"field_")
                    append(index)
                    append("\": \"value_")
                    append(index)
                    append("\",")
                }
                append("\n  \"last\": true\n}")
            }

        composeRule.setContent {
            StudyTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 640.dp)) {
                    ImportScreen(
                        state =
                            ContentUiState(
                                isLoading = false,
                                isManualImportOpen = true,
                                manualImportText = longJson,
                            ),
                        onBack = {},
                        onImportClick = {},
                        onImportSnapshotClick = {},
                        onExportSnapshotClick = {},
                        onImportUserStateClick = {},
                        onExportUserStateClick = {},
                        onOpenEditableImport = {},
                        onOpenManualBuilder = {},
                        onOpenManualImport = {},
                        onCloseManualImport = {},
                        onManualImportTextChange = {},
                        onClearManualImportText = {},
                        onRequestManualImportConfirmation = {},
                        onDismissManualImportConfirmation = {},
                        onConfirmManualImport = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Limpiar").assertIsDisplayed()
        composeRule.onNodeWithText("Cerrar").assertIsDisplayed()
    }

    @Test
    fun manualJsonEditorKeepsActionsReachableWithKeyboardSizedViewport() {
        val longJson =
            buildString {
                append("{")
                repeat(80) { index ->
                    append("\n  \"field_")
                    append(index)
                    append("\": \"value_")
                    append(index)
                    append("\",")
                }
                append("\n  \"last\": true\n}")
            }

        composeRule.setContent {
            StudyTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 360.dp)) {
                    ImportScreen(
                        state =
                            ContentUiState(
                                isLoading = false,
                                isManualImportOpen = true,
                                manualImportText = longJson,
                            ),
                        onBack = {},
                        onImportClick = {},
                        onImportSnapshotClick = {},
                        onExportSnapshotClick = {},
                        onImportUserStateClick = {},
                        onExportUserStateClick = {},
                        onOpenEditableImport = {},
                        onOpenManualBuilder = {},
                        onOpenManualImport = {},
                        onCloseManualImport = {},
                        onManualImportTextChange = {},
                        onClearManualImportText = {},
                        onRequestManualImportConfirmation = {},
                        onDismissManualImportConfirmation = {},
                        onConfirmManualImport = {},
                    )
                }
            }
        }

        composeRule
            .onNodeWithTag("manual_import_scroll")
            .performScrollToNode(hasText("Cerrar"))

        composeRule.onNodeWithText("Cerrar").assertIsDisplayed()
    }

    @Test
    fun importErrorCardExpandsPreciseValidationDetailsOnTap() {
        composeRule.setContent {
            StudyTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 640.dp)) {
                    ImportScreen(
                        state =
                            ContentUiState(
                                isLoading = false,
                                lastImportResult =
                                    ImportExecutionResult(
                                        packageId = "manual:text-entry",
                                        imported = false,
                                        report =
                                            ValidationReport(
                                                structuralErrors =
                                                    listOf(
                                                        ValidationMessage(
                                                            code = "content_package_parse_error",
                                                            message = "El content_package no respeta el contrato: campo requerido ausente.",
                                                            path = "$.items[0].format",
                                                            expected = "Enum ItemFormat conocido.",
                                                            actual = "multiple choice",
                                                            hint = "Usá MULTIPLE_CHOICE o un alias authoring válido antes de importar.",
                                                        ),
                                                    ),
                                                authoringWarnings = emptyList(),
                                            ),
                                    ),
                            ),
                        onBack = {},
                        onImportClick = {},
                        onImportSnapshotClick = {},
                        onExportSnapshotClick = {},
                        onImportUserStateClick = {},
                        onExportUserStateClick = {},
                        onOpenEditableImport = {},
                        onOpenManualBuilder = {},
                        onOpenManualImport = {},
                        onCloseManualImport = {},
                        onManualImportTextChange = {},
                        onClearManualImportText = {},
                        onRequestManualImportConfirmation = {},
                        onDismissManualImportConfirmation = {},
                        onConfirmManualImport = {},
                    )
                }
            }
        }

        composeRule.onAllNodesWithText("Código: content_package_parse_error", substring = true).assertCountEquals(0)

        composeRule.onNodeWithTag("import_result_status").performClick()

        composeRule.onNodeWithText("Código: content_package_parse_error", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Ubicación: $.items[0].format", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Esperado: Enum ItemFormat conocido", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Recibido: multiple choice", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Detalle exacto: El content_package no respeta el contrato", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Usá MULTIPLE_CHOICE", substring = true).assertIsDisplayed()
    }
}
