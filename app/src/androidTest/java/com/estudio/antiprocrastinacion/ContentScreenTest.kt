package com.estudio.antiprocrastinacion

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.estudio.antiprocrastinacion.app.ui.common.StudyTheme
import com.estudio.antiprocrastinacion.app.ui.content.ArchiveFilter
import com.estudio.antiprocrastinacion.app.ui.content.ContentCourseRow
import com.estudio.antiprocrastinacion.app.ui.content.ContentScreen
import com.estudio.antiprocrastinacion.app.ui.content.ContentUiState
import com.estudio.antiprocrastinacion.app.ui.content.ContentUnitRow
import org.junit.Rule
import org.junit.Test

class ContentScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersCourseLevelWithoutStackedChildrenOrInlineAdminButtons() {
        composeRule.setContent {
            StudyTheme {
                ContentScreen(
                    state =
                        ContentUiState(
                            isLoading = false,
                            archiveFilter = ArchiveFilter.ACTIVE_ONLY,
                            visibleCourses =
                                listOf(
                                    ContentCourseRow(
                                        courseId = "course-1",
                                        title = "Ethereum",
                                        units =
                                            listOf(
                                                ContentUnitRow(
                                                    unitId = "unit-1",
                                                    title = "Consensus",
                                                    nodeIds = listOf("node-1"),
                                                    questionCount = 3,
                                                    items = emptyList(),
                                                ),
                                            ),
                                        nodeIds = listOf("node-1"),
                                    ),
                                ),
                        ),
                    onBack = {},
                    onRefresh = {},
                    onExportSnapshotClick = {},
                    onArchiveFilterChange = {},
                    onConfirmReset = {},
                    onDismissReset = {},
                    onRequestCourseReset = {},
                    onRequestUnitReset = {},
                    onToggleCourseArchive = { _, _ -> },
                    onToggleUnitArchive = { _, _ -> },
                    onToggleItemArchive = { _, _ -> },
                    onOpenItemEdit = {},
                    onDismissItemEdit = {},
                    onConfirmItemEdit = {},
                    onPendingItemEditStemChange = {},
                    onPendingItemEditCorrectAnswerChange = {},
                    onPendingItemEditOptionsTextChange = {},
                )
            }
        }

        composeRule.onNodeWithText("Contenido").assertIsDisplayed()
        composeRule.onNodeWithText("Activos").assertIsDisplayed()
        composeRule.onNodeWithText("Archivados").assertIsDisplayed()
        composeRule.onNodeWithText("Ethereum").assertIsDisplayed()
        composeRule.onAllNodesWithText("Consensus").assertCountEquals(0)
        composeRule.onAllNodesWithText("Ver unidades").assertCountEquals(0)
        composeRule.onAllNodesWithText("Archivar curso").assertCountEquals(0)
        composeRule.onAllNodesWithText("Nodo").assertCountEquals(0)
    }

    @Test
    fun archivedViewDoesNotExposeRestoreButtonsInlineOrInternalModelWords() {
        composeRule.setContent {
            StudyTheme {
                ContentScreen(
                    state =
                        ContentUiState(
                            isLoading = false,
                            archiveFilter = ArchiveFilter.ARCHIVED_ONLY,
                            visibleCourses =
                                listOf(
                                    ContentCourseRow(
                                        courseId = "course-2",
                                        title = "Sistemas Operativos",
                                        units = emptyList(),
                                        nodeIds = listOf("node-archived"),
                                    ),
                                ),
                        ),
                    onBack = {},
                    onRefresh = {},
                    onExportSnapshotClick = {},
                    onArchiveFilterChange = {},
                    onConfirmReset = {},
                    onDismissReset = {},
                    onRequestCourseReset = {},
                    onRequestUnitReset = {},
                    onToggleCourseArchive = { _, _ -> },
                    onToggleUnitArchive = { _, _ -> },
                    onToggleItemArchive = { _, _ -> },
                    onOpenItemEdit = {},
                    onDismissItemEdit = {},
                    onConfirmItemEdit = {},
                    onPendingItemEditStemChange = {},
                    onPendingItemEditCorrectAnswerChange = {},
                    onPendingItemEditOptionsTextChange = {},
                )
            }
        }

        composeRule.onNodeWithText("Sistemas Operativos").assertIsDisplayed()
        composeRule.onAllNodesWithText("Restaurar curso").assertCountEquals(0)
        composeRule.onAllNodesWithText("Reiniciar curso").assertCountEquals(0)
        composeRule.onAllNodesWithText("Outcome").assertCountEquals(0)
        composeRule.onAllNodesWithText("Nodo").assertCountEquals(0)
    }
}
