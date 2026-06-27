package com.estudio.antiprocrastinacion.app.ui.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.estudio.antiprocrastinacion.app.domain.repository.ImportExecutionResult
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationMessage
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationReport
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableImportDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableOptionDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableQuestionDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableUnitDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedQuestionFormat
import com.estudio.antiprocrastinacion.app.ui.common.StudyBadgeTone
import com.estudio.antiprocrastinacion.app.ui.common.StudyPrimaryButton
import com.estudio.antiprocrastinacion.app.ui.common.StudySecondaryButton
import com.estudio.antiprocrastinacion.app.ui.common.StudyStatusBadge
import com.estudio.antiprocrastinacion.app.ui.common.StudySurfaceCard

private const val EditableImportSourceTag = "editable_import_source"
private const val EditableImportSourceInputTag = "editable_import_source_input"
private const val EditableImportApproveTag = "editable_import_approve"

@Composable
fun EditableImportScreen(
    state: EditableImportUiState,
    onBack: () -> Unit,
    onBackToSource: () -> Unit,
    onSourceTextChange: (String) -> Unit,
    onAnalyzeSource: () -> Unit,
    onPrepareDraft: () -> Unit,
    onClearSource: () -> Unit,
    onConfirmImport: () -> Unit,
    onCourseTitleChange: (String) -> Unit,
    onUnitTitleChange: (unitIndex: Int, value: String) -> Unit,
    onQuestionFormatChange: (unitIndex: Int, questionIndex: Int, format: ReviewedQuestionFormat) -> Unit,
    onQuestionStemChange: (unitIndex: Int, questionIndex: Int, value: String) -> Unit,
    onQuestionCorrectAnswerChange: (unitIndex: Int, questionIndex: Int, value: String) -> Unit,
    onQuestionFeedbackChange: (unitIndex: Int, questionIndex: Int, value: String) -> Unit,
    onQuestionCorrectedConfusionChange: (unitIndex: Int, questionIndex: Int, value: String) -> Unit,
    onOptionTextChange: (unitIndex: Int, questionIndex: Int, optionIndex: Int, value: String) -> Unit,
    onMarkOptionCorrect: (unitIndex: Int, questionIndex: Int, optionIndex: Int) -> Unit,
    onAddOption: (unitIndex: Int, questionIndex: Int) -> Unit,
    onRemoveOption: (unitIndex: Int, questionIndex: Int, optionIndex: Int) -> Unit,
    onAddQuestion: (unitIndex: Int) -> Unit,
    onRemoveQuestion: (unitIndex: Int, questionIndex: Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .imePadding(),
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                contentPadding = PaddingValues(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    EditableImportHeader(
                        state = state,
                        onBack =
                            when (state.step) {
                                EditableImportStep.SOURCE -> onBack
                                EditableImportStep.PREVIEW -> onBackToSource
                            },
                    )
                }

                if (state.step == EditableImportStep.SOURCE) {
                    item {
                        SourceTextCard(
                            sourceText = state.sourceText,
                            onSourceTextChange = onSourceTextChange,
                            enabled = !state.isWorking,
                        )
                    }
                }

                state.draft?.let { draft ->
                    item {
                        DetectedCourseCard(
                            draft = draft,
                            onCourseTitleChange = onCourseTitleChange,
                            enabled = !state.isWorking,
                        )
                    }
                    draft.units.forEachIndexed { unitIndex, unit ->
                        item(key = "unit-header-${unit.key}-$unitIndex") {
                            DetectedUnitHeader(
                                index = unitIndex,
                                unit = unit,
                                onUnitTitleChange = { value -> onUnitTitleChange(unitIndex, value) },
                                onAddQuestion = { onAddQuestion(unitIndex) },
                                enabled = !state.isWorking,
                            )
                        }
                        unit.questions.forEachIndexed { questionIndex, question ->
                            item(key = "question-${unit.key}-${question.key}-$questionIndex") {
                                DetectedQuestionCard(
                                    index = questionIndex,
                                    question = question,
                                    onFormatChange = { format -> onQuestionFormatChange(unitIndex, questionIndex, format) },
                                    onStemChange = { value -> onQuestionStemChange(unitIndex, questionIndex, value) },
                                    onCorrectAnswerChange = { value -> onQuestionCorrectAnswerChange(unitIndex, questionIndex, value) },
                                    onFeedbackChange = { value -> onQuestionFeedbackChange(unitIndex, questionIndex, value) },
                                    onCorrectedConfusionChange = { value -> onQuestionCorrectedConfusionChange(unitIndex, questionIndex, value) },
                                    onOptionTextChange = { optionIndex, value -> onOptionTextChange(unitIndex, questionIndex, optionIndex, value) },
                                    onMarkOptionCorrect = { optionIndex -> onMarkOptionCorrect(unitIndex, questionIndex, optionIndex) },
                                    onAddOption = { onAddOption(unitIndex, questionIndex) },
                                    onRemoveOption = { optionIndex -> onRemoveOption(unitIndex, questionIndex, optionIndex) },
                                    onRemoveQuestion = { onRemoveQuestion(unitIndex, questionIndex) },
                                    enabled = !state.isWorking,
                                )
                            }
                        }
                    }
                }

                if (state.step == EditableImportStep.PREVIEW) {
                    item {
                        state.preview?.let { preview ->
                            PreviewCard(preview)
                        }
                    }
                }

                if (state.visibleReport.hasMessages()) {
                    item {
                        DiagnosticsCard(
                            title = "Diagnóstico técnico",
                            report = state.visibleReport,
                            emptyText = if (state.step == EditableImportStep.PREVIEW) "Listo para importar." else "Sin errores.",
                        )
                    }
                }

                item {
                    state.lastImportResult?.let { result ->
                        ImportResultCard(result)
                    }
                }
            }
            EditableImportBottomBar(
                state = state,
                onBackToSource = onBackToSource,
                onAnalyzeSource = onAnalyzeSource,
                onPrepareDraft = onPrepareDraft,
                onClearSource = onClearSource,
                onConfirmImport = onConfirmImport,
            )
        }
    }
}

@Composable
private fun EditableImportHeader(
    state: EditableImportUiState,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Volver",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Importar",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text =
                    when (state.step) {
                        EditableImportStep.SOURCE -> "Texto fuente"
                        EditableImportStep.PREVIEW -> "Vista previa"
                    },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StudyStatusBadge(
                text =
                    when (state.step) {
                        EditableImportStep.SOURCE -> "Texto"
                        EditableImportStep.PREVIEW -> "Revisar"
                    },
            tone = StudyBadgeTone.Neutral,
        )
    }
}

@Composable
private fun SourceTextCard(
    sourceText: String,
    onSourceTextChange: (String) -> Unit,
    enabled: Boolean,
) {
    StudySurfaceCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(EditableImportSourceTag),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = sourceText,
                onValueChange = onSourceTextChange,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 184.dp, max = 260.dp)
                        .testTag(EditableImportSourceInputTag),
                minLines = 7,
                maxLines = 10,
                label = { Text("Texto fuente") },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun DetectedCourseCard(
    draft: ReviewedEditableImportDraft,
    onCourseTitleChange: (String) -> Unit,
    enabled: Boolean,
) {
    StudySurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = draft.course.title,
                onValueChange = onCourseTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Curso") },
                placeholder = { Text("Curso sin nombre") },
                textStyle = MaterialTheme.typography.titleMedium,
                minLines = 1,
                maxLines = 2,
                enabled = enabled,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StudyStatusBadge(text = "Curso", tone = StudyBadgeTone.Neutral)
                StudyStatusBadge(text = "${draft.units.size} unidades", tone = StudyBadgeTone.Accent)
                StudyStatusBadge(text = "${draft.units.sumOf { it.questions.size }} preguntas", tone = StudyBadgeTone.Accent)
            }
        }
    }
}

@Composable
private fun DetectedUnitHeader(
    index: Int,
    unit: ReviewedEditableUnitDraft,
    onUnitTitleChange: (String) -> Unit,
    onAddQuestion: () -> Unit,
    enabled: Boolean,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Unidad ${index + 1}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = unit.title,
            onValueChange = onUnitTitleChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Unidad") },
            placeholder = { Text("Unidad sin nombre") },
            textStyle = MaterialTheme.typography.titleSmall,
            minLines = 1,
            maxLines = 3,
            enabled = enabled,
        )
        Text(
            text = "${unit.questions.size} preguntas detectadas",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StudySecondaryButton(
            text = "Agregar pregunta",
            leadingIcon = Icons.Rounded.Add,
            onClick = onAddQuestion,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
    }
}

@Composable
private fun DetectedQuestionCard(
    index: Int,
    question: ReviewedEditableQuestionDraft,
    onFormatChange: (ReviewedQuestionFormat) -> Unit,
    onStemChange: (String) -> Unit,
    onCorrectAnswerChange: (String) -> Unit,
    onFeedbackChange: (String) -> Unit,
    onCorrectedConfusionChange: (String) -> Unit,
    onOptionTextChange: (optionIndex: Int, value: String) -> Unit,
    onMarkOptionCorrect: (optionIndex: Int) -> Unit,
    onAddOption: () -> Unit,
    onRemoveOption: (optionIndex: Int) -> Unit,
    onRemoveQuestion: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = questionCardColor(),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.9f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Pregunta ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StudyStatusBadge(
                        text = question.reviewStatusLabel(),
                        tone = question.reviewStatusTone(),
                    )
                    IconButton(
                        onClick = onRemoveQuestion,
                        enabled = enabled,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Eliminar pregunta ${index + 1}",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            QuestionFormatSelector(
                selectedFormat = question.format,
                onFormatChange = onFormatChange,
                enabled = enabled,
            )
            EditableReviewField(
                label = "Pregunta",
                value = question.stem,
                onValueChange = onStemChange,
                emptyText = "Falta la pregunta",
                minLines = 1,
                maxLines = 6,
                enabled = enabled,
            )
            if (question.format == ReviewedQuestionFormat.MULTIPLE_CHOICE || question.format == ReviewedQuestionFormat.CHOOSE_FALSE_STATEMENT) {
                QuestionOptions(
                    options = question.options,
                    onOptionTextChange = onOptionTextChange,
                    onMarkOptionCorrect = onMarkOptionCorrect,
                    onAddOption = onAddOption,
                    onRemoveOption = onRemoveOption,
                    enabled = enabled,
                )
            }
            EditableReviewField(
                label = "Respuesta esperada",
                value = question.correctAnswer,
                onValueChange = onCorrectAnswerChange,
                emptyText = "Falta la respuesta",
                highlightEmpty = true,
                minLines = 1,
                maxLines = 6,
                enabled = enabled,
            )
            EditableReviewField(
                label = "Explicación",
                value = question.feedback,
                onValueChange = onFeedbackChange,
                emptyText = "Sin explicación",
                minLines = 2,
                maxLines = 8,
                enabled = enabled,
            )
            if (question.correctedConfusion.isNotBlank()) {
                EditableReviewField(
                    label = "Confusión que corrige",
                    value = question.correctedConfusion,
                    onValueChange = onCorrectedConfusionChange,
                    emptyText = "",
                    minLines = 1,
                    maxLines = 5,
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun QuestionFormatSelector(
    selectedFormat: ReviewedQuestionFormat,
    onFormatChange: (ReviewedQuestionFormat) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Tipo de respuesta",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReviewedQuestionFormat.entries.forEach { format ->
                StudyStatusBadge(
                    text = format.label,
                    tone = if (format == selectedFormat) StudyBadgeTone.Accent else StudyBadgeTone.Neutral,
                    modifier = Modifier.clickable(enabled = enabled) { onFormatChange(format) },
                )
            }
        }
    }
}

@Composable
private fun QuestionOptions(
    options: List<ReviewedEditableOptionDraft>,
    onOptionTextChange: (optionIndex: Int, value: String) -> Unit,
    onMarkOptionCorrect: (optionIndex: Int) -> Unit,
    onAddOption: () -> Unit,
    onRemoveOption: (optionIndex: Int) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Opciones",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        options.forEachIndexed { index, option ->
            OptionRow(
                option = option,
                onTextChange = { value -> onOptionTextChange(index, value) },
                onMarkCorrect = { onMarkOptionCorrect(index) },
                onRemove = { onRemoveOption(index) },
                enabled = enabled,
            )
        }
        StudySecondaryButton(
            text = "Agregar opción",
            leadingIcon = Icons.Rounded.Add,
            onClick = onAddOption,
            enabled = enabled && options.size < 6,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OptionRow(
    option: ReviewedEditableOptionDraft,
    onTextChange: (String) -> Unit,
    onMarkCorrect: () -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = option.text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Opción ${option.key}") },
            placeholder = { Text("Texto de la opción") },
            textStyle = MaterialTheme.typography.bodyLarge,
            minLines = 1,
            maxLines = 4,
            enabled = enabled,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StudyStatusBadge(
                text = if (option.isCorrect) "Correcta" else "Marcar correcta",
                tone = if (option.isCorrect) StudyBadgeTone.Success else StudyBadgeTone.Neutral,
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable(enabled = enabled) { onMarkCorrect() },
            )
            IconButton(
                onClick = onRemove,
                enabled = enabled,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Eliminar opción",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EditableReviewField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    emptyText: String,
    highlightEmpty: Boolean = false,
    minLines: Int,
    maxLines: Int,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = { Text(emptyText) },
            isError = value.isBlank() && highlightEmpty,
            textStyle = MaterialTheme.typography.bodyLarge,
            minLines = minLines,
            maxLines = maxLines,
            enabled = enabled,
        )
    }
}

@Composable
private fun questionCardColor(): Color =
    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f)

@Composable
private fun PreviewCard(preview: ImportPreview) {
    StudySurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Preview,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Resumen final",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StudyStatusBadge(text = "${preview.courseCount} curso", tone = StudyBadgeTone.Neutral)
                StudyStatusBadge(text = "${preview.unitCount} unidades", tone = StudyBadgeTone.Neutral)
                StudyStatusBadge(text = "${preview.itemCount} preguntas", tone = StudyBadgeTone.Accent)
                if (preview.potentialArchivedNodeCount > 0) {
                    StudyStatusBadge(text = "${preview.potentialArchivedNodeCount} para revisar", tone = StudyBadgeTone.Warning)
                }
            }
            Text(
                text = "Se cargarán ${preview.itemCount} preguntas. Nuevo contenido: ${preview.newCourseCount} cursos y ${preview.newUnitCount} unidades. Actualizaciones detectadas: ${preview.existingCourseMatches} cursos y ${preview.existingUnitMatches} unidades.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiagnosticsCard(
    title: String,
    report: ValidationReport,
    emptyText: String,
) {
    var expanded by rememberSaveable(title, report.structuralErrors.size, report.authoringWarnings.size) { mutableStateOf(false) }
    val hasMessages = report.hasMessages()
    StudySurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = hasMessages) { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text =
                            if (hasMessages) {
                                "${report.structuralErrors.size} errores, ${report.authoringWarnings.size} advertencias"
                            } else {
                                emptyText
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (report.canImport) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
                if (hasMessages) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Ocultar detalle" else "Ver detalle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded && hasMessages,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                DiagnosticsList(report.structuralErrors + report.authoringWarnings)
            }
        }
    }
}

@Composable
private fun DiagnosticsList(messages: List<ValidationMessage>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        messages.forEachIndexed { index, message ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "${index + 1}. ${message.code}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = message.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                message.path?.let {
                    DiagnosticLine(label = "Ubicacion", value = it)
                }
                message.expected?.let {
                    DiagnosticLine(label = "Esperado", value = it)
                }
                message.actual?.let {
                    DiagnosticLine(label = "Recibido", value = it)
                }
                message.hint?.let {
                    DiagnosticLine(label = "Accion", value = it, accent = true)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticLine(
    label: String,
    value: String,
    accent: Boolean = false,
) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ImportResultCard(result: ImportExecutionResult) {
    StudySurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StudyStatusBadge(
                text = if (result.imported) "Importado" else "Error",
                tone = if (result.imported) StudyBadgeTone.Success else StudyBadgeTone.Danger,
            )
            Text(
                text =
                    if (result.imported) {
                        "Contenido cargado."
                    } else {
                        "${result.report.structuralErrors.size} errores. No se importo contenido."
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EditableImportBottomBar(
    state: EditableImportUiState,
    onBackToSource: () -> Unit,
    onAnalyzeSource: () -> Unit,
    onPrepareDraft: () -> Unit,
    onClearSource: () -> Unit,
    onConfirmImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state.step) {
                EditableImportStep.SOURCE -> {
                    StudySecondaryButton(
                        text = "Limpiar",
                        leadingIcon = Icons.Rounded.Delete,
                        onClick = onClearSource,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isWorking && (state.sourceText.isNotBlank() || state.draft != null),
                    )
                    StudyPrimaryButton(
                        text = if (state.draft == null) "Analizar" else "Preparar",
                        leadingIcon = Icons.Rounded.Search,
                        onClick = if (state.draft == null) onAnalyzeSource else onPrepareDraft,
                        modifier = Modifier.weight(1f),
                        enabled = if (state.draft == null) state.canAnalyze else state.canPrepareDraft,
                    )
                }

                EditableImportStep.PREVIEW -> {
                    StudySecondaryButton(
                        text = "Editar",
                        leadingIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                        onClick = onBackToSource,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isWorking,
                    )
                    StudyPrimaryButton(
                        text = "Aprobar",
                        leadingIcon = Icons.Rounded.DoneAll,
                        onClick = onConfirmImport,
                        modifier =
                            Modifier
                                .weight(1f)
                                .testTag(EditableImportApproveTag),
                        enabled = state.canConfirmImport,
                    )
                }
            }
        }
    }
}

private fun ReviewedEditableQuestionDraft.reviewStatusLabel(): String =
    when {
        stem.isBlank() || correctAnswer.isBlank() -> "Incompleta"
        feedback.isBlank() -> "Revisar"
        else -> "Lista"
    }

private fun ReviewedEditableQuestionDraft.reviewStatusTone(): StudyBadgeTone =
    when {
        stem.isBlank() || correctAnswer.isBlank() -> StudyBadgeTone.Danger
        feedback.isBlank() -> StudyBadgeTone.Warning
        else -> StudyBadgeTone.Success
    }

private fun ValidationReport.hasMessages(): Boolean =
    structuralErrors.isNotEmpty() || authoringWarnings.isNotEmpty()
