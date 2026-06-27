package com.estudio.antiprocrastinacion.app.ui.content

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.estudio.antiprocrastinacion.app.data.importing.normalizeTrueFalseAnswer
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableOptionDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableQuestionDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableUnitDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedQuestionFormat
import com.estudio.antiprocrastinacion.app.ui.common.StudyBadgeTone
import com.estudio.antiprocrastinacion.app.ui.common.StudyPrimaryButton
import com.estudio.antiprocrastinacion.app.ui.common.StudySecondaryButton
import com.estudio.antiprocrastinacion.app.ui.common.StudyStatusBadge
import com.estudio.antiprocrastinacion.app.ui.common.StudySurfaceCard

private val MANUAL_FORMAT_ORDER =
    listOf(
        ReviewedQuestionFormat.MULTIPLE_CHOICE,
        ReviewedQuestionFormat.TRUE_FALSE,
        ReviewedQuestionFormat.CHOOSE_FALSE_STATEMENT,
        ReviewedQuestionFormat.REVEAL_ANSWER,
    )

private fun manualFormatLabel(format: ReviewedQuestionFormat): String =
    when (format) {
        ReviewedQuestionFormat.MULTIPLE_CHOICE -> "Con opciones"
        ReviewedQuestionFormat.TRUE_FALSE -> "Verdadero o falso"
        ReviewedQuestionFormat.CHOOSE_FALSE_STATEMENT -> "Elegir la falsa"
        ReviewedQuestionFormat.REVEAL_ANSWER -> "Con tus palabras"
    }

private fun manualFormatHint(format: ReviewedQuestionFormat): String =
    when (format) {
        ReviewedQuestionFormat.MULTIPLE_CHOICE,
        ReviewedQuestionFormat.TRUE_FALSE,
        ReviewedQuestionFormat.CHOOSE_FALSE_STATEMENT,
        -> "La app te la toma sola, todos los días y en los recordatorios."

        ReviewedQuestionFormat.REVEAL_ANSWER ->
            "Aparece solo cuando entrás a repasar a fondo. No cuenta para las preguntas que necesita la unidad."
    }

@Composable
fun ManualBuilderScreen(
    state: ManualBuilderUiState,
    viewModel: ManualBuilderViewModel,
    onExit: () -> Unit,
) {
    if (state.saved) {
        ManualSavedScreen(onExit = onExit)
        return
    }

    if (state.showAlmostReady) {
        AlmostReadyDialog(
            readiness = state.readiness,
            onDismiss = viewModel::dismissAlmostReady,
            onEditUnit = viewModel::editUnit,
        )
    }

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
            ManualHeader(
                state = state,
                onBack = { if (viewModel.atRoot()) onExit() else viewModel.back() },
            )
            when (state.step) {
                ManualBuilderStep.COURSE -> CourseStep(state = state, viewModel = viewModel)
                ManualBuilderStep.UNIT -> UnitStep(state = state, viewModel = viewModel)
                ManualBuilderStep.QUESTIONS -> QuestionsStep(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ManualHeader(
    state: ManualBuilderUiState,
    onBack: () -> Unit,
) {
    val subtitle =
        when (state.step) {
            ManualBuilderStep.COURSE -> "¿En qué curso?"
            ManualBuilderStep.UNIT -> state.courseTitle
            ManualBuilderStep.QUESTIONS ->
                listOfNotNull(state.courseTitle.takeIf { it.isNotBlank() }, state.currentUnit?.title)
                    .joinToString("  ▸  ")
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Volver",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Column {
            Text(
                text = "Crear preguntas",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---- Course step ----------------------------------------------------------------------------

@Composable
private fun CourseStep(
    state: ManualBuilderUiState,
    viewModel: ManualBuilderViewModel,
) {
    var newTitle by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = "Elegí dónde guardar tus preguntas, o creá un curso nuevo.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.existingCourses.isNotEmpty()) {
            item {
                Text(
                    text = "Mis cursos",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            items(state.existingCourses.size) { index ->
                val option = state.existingCourses[index]
                StudySurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !state.isWorking) { viewModel.chooseExistingCourse(option) }
                                .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = option.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        item {
            StudySurfaceCard(modifier = Modifier.fillMaxWidth(), emphasized = true) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Crear un curso nuevo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nombre del curso") },
                        placeholder = { Text("Ej: Biología") },
                        singleLine = true,
                        enabled = !state.isWorking,
                    )
                    StudyPrimaryButton(
                        text = "Continuar",
                        leadingIcon = Icons.Rounded.Add,
                        onClick = { viewModel.createNewCourse(newTitle) },
                        enabled = !state.isWorking && newTitle.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ---- Unit step ------------------------------------------------------------------------------

@Composable
private fun UnitStep(
    state: ManualBuilderUiState,
    viewModel: ManualBuilderViewModel,
) {
    var newTitle by rememberSaveable(state.courseKey) { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = "Dividí el curso en partes. Cada parte (unidad) agrupa un tema.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.draft.units.isNotEmpty()) {
            item {
                Text(
                    text = "Partes de este curso",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            items(state.draft.units.size) { index ->
                val unit = state.draft.units[index]
                val readiness = state.readiness.getOrNull(index)
                StudySurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !state.isWorking) { viewModel.editUnit(index) }
                                .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = unit.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (readiness != null) {
                                Text(
                                    text = unitReadinessShort(readiness),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        StudyStatusBadge(
                            text = if (readiness?.isReady == true) "Lista" else "En progreso",
                            tone = if (readiness?.isReady == true) StudyBadgeTone.Success else StudyBadgeTone.Warning,
                        )
                    }
                }
            }
        }
        item {
            StudySurfaceCard(modifier = Modifier.fillMaxWidth(), emphasized = true) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Agregar una parte",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nombre de la parte") },
                        placeholder = { Text("Ej: Fotosíntesis") },
                        singleLine = true,
                        enabled = !state.isWorking,
                    )
                    StudyPrimaryButton(
                        text = "Continuar",
                        leadingIcon = Icons.Rounded.Add,
                        onClick = {
                            viewModel.createUnit(newTitle)
                            newTitle = ""
                        },
                        enabled = !state.isWorking && newTitle.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (state.draft.units.isNotEmpty()) {
            item {
                StudyPrimaryButton(
                    text = "Guardar todo",
                    leadingIcon = Icons.Rounded.CheckCircle,
                    onClick = viewModel::requestSave,
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ---- Questions step -------------------------------------------------------------------------

@Composable
private fun QuestionsStep(
    state: ManualBuilderUiState,
    viewModel: ManualBuilderViewModel,
) {
    val unit = state.currentUnit
    if (unit == null) {
        Text(
            text = "Elegí una parte para agregar preguntas.",
            modifier = Modifier.padding(20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { ReadinessMeter(readiness = state.currentReadiness, unitTitle = unit.title) }
            state.softError?.let { message ->
                item {
                    StudySurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            items(unit.questions.size) { index ->
                ManualQuestionCard(
                    index = index,
                    question = unit.questions[index],
                    viewModel = viewModel,
                    enabled = !state.isWorking,
                )
            }
            item {
                StudySecondaryButton(
                    text = "Agregar pregunta",
                    leadingIcon = Icons.Rounded.Add,
                    onClick = viewModel::addQuestion,
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StudySecondaryButton(
                    text = "Otra parte",
                    leadingIcon = Icons.Rounded.Add,
                    onClick = viewModel::goToAddUnit,
                    enabled = !state.isWorking,
                    modifier = Modifier.weight(1f),
                )
                StudyPrimaryButton(
                    text = "Guardar todo",
                    leadingIcon = Icons.Rounded.CheckCircle,
                    onClick = viewModel::requestSave,
                    enabled = !state.isWorking,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ReadinessMeter(
    readiness: ManualUnitReadiness?,
    unitTitle: String,
) {
    readiness ?: return
    val ready = readiness.isReady
    StudySurfaceCard(modifier = Modifier.fillMaxWidth(), emphasized = !ready) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StudyStatusBadge(
                text = "${readiness.quickComplete} de $MANUAL_MIN_QUICK_QUESTIONS preguntas rápidas",
                tone = if (ready) StudyBadgeTone.Success else StudyBadgeTone.Accent,
            )
            Text(
                text = readinessLongMessage(readiness, unitTitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ManualQuestionCard(
    index: Int,
    question: ReviewedEditableQuestionDraft,
    viewModel: ManualBuilderViewModel,
    enabled: Boolean,
) {
    val complete = remember(question) { questionIsComplete(question) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StudyStatusBadge(
                        text = if (complete) "Lista" else "Te falta algo",
                        tone = if (complete) StudyBadgeTone.Success else StudyBadgeTone.Warning,
                    )
                    IconButton(onClick = { viewModel.removeQuestion(index) }, enabled = enabled) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Eliminar pregunta ${index + 1}",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // Format selector
            Text(
                text = "Tipo de pregunta",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MANUAL_FORMAT_ORDER.forEach { format ->
                    StudyStatusBadge(
                        text = manualFormatLabel(format),
                        tone = if (format == question.format) StudyBadgeTone.Accent else StudyBadgeTone.Neutral,
                        modifier = Modifier.clickable(enabled = enabled) { viewModel.setQuestionFormat(index, format) },
                    )
                }
            }
            Text(
                text = manualFormatHint(question.format),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = question.stem,
                onValueChange = { viewModel.setQuestionStem(index, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("La pregunta") },
                placeholder = { Text("Escribí la pregunta") },
                minLines = 1,
                maxLines = 5,
                enabled = enabled,
            )

            when (question.format) {
                ReviewedQuestionFormat.MULTIPLE_CHOICE,
                ReviewedQuestionFormat.CHOOSE_FALSE_STATEMENT,
                ->
                    OptionsEditor(
                        questionIndex = index,
                        question = question,
                        chooseFalse = question.format == ReviewedQuestionFormat.CHOOSE_FALSE_STATEMENT,
                        viewModel = viewModel,
                        enabled = enabled,
                    )

                ReviewedQuestionFormat.TRUE_FALSE ->
                    TrueFalseEditor(questionIndex = index, question = question, viewModel = viewModel, enabled = enabled)

                ReviewedQuestionFormat.REVEAL_ANSWER ->
                    OutlinedTextField(
                        value = question.correctAnswer,
                        onValueChange = { viewModel.setRevealAnswer(index, it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Respuesta esperada") },
                        placeholder = { Text("Lo que verás al darla vuelta") },
                        minLines = 1,
                        maxLines = 6,
                        enabled = enabled,
                    )
            }

            OutlinedTextField(
                value = question.feedback,
                onValueChange = { viewModel.setQuestionFeedback(index, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Explicación (opcional)") },
                placeholder = { Text("Por qué es la respuesta correcta") },
                minLines = 1,
                maxLines = 5,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun OptionsEditor(
    questionIndex: Int,
    question: ReviewedEditableQuestionDraft,
    chooseFalse: Boolean,
    viewModel: ManualBuilderViewModel,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = if (chooseFalse) "Opciones (tocá la que es FALSA)" else "Opciones (tocá la correcta)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        question.options.forEachIndexed { optionIndex, option ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = option.text,
                    onValueChange = { viewModel.setOptionText(questionIndex, optionIndex, it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Opción ${option.key}") },
                    minLines = 1,
                    maxLines = 3,
                    enabled = enabled,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StudyStatusBadge(
                        text = if (option.isCorrect) markedLabel(chooseFalse) else markLabel(chooseFalse),
                        tone = if (option.isCorrect) StudyBadgeTone.Success else StudyBadgeTone.Neutral,
                        modifier =
                            Modifier
                                .weight(1f)
                                .clickable(enabled = enabled) { viewModel.markOptionCorrect(questionIndex, optionIndex) },
                    )
                    if (question.options.size > 2) {
                        IconButton(onClick = { viewModel.removeOption(questionIndex, optionIndex) }, enabled = enabled) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Eliminar opción",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        StudySecondaryButton(
            text = "Agregar opción",
            leadingIcon = Icons.Rounded.Add,
            onClick = { viewModel.addOption(questionIndex) },
            enabled = enabled && question.options.size < 6,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TrueFalseEditor(
    questionIndex: Int,
    question: ReviewedEditableQuestionDraft,
    viewModel: ManualBuilderViewModel,
    enabled: Boolean,
) {
    val answer = question.correctAnswer.normalizeTrueFalseAnswer()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StudySecondaryButton(
            text = if (answer == true) "✓ Verdadero" else "Verdadero",
            onClick = { viewModel.setTrueFalseAnswer(questionIndex, true) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        StudySecondaryButton(
            text = if (answer == false) "✓ Falso" else "Falso",
            onClick = { viewModel.setTrueFalseAnswer(questionIndex, false) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
    }
}

// ---- Almost ready + saved -------------------------------------------------------------------

@Composable
private fun AlmostReadyDialog(
    readiness: List<ManualUnitReadiness>,
    onDismiss: () -> Unit,
    onEditUnit: (Int) -> Unit,
) {
    val pending = readiness.filterNot { it.isReady }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Te falta un pasito 🙂") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Para que te sirvan para estudiar, cada parte necesita 4 preguntas con opciones:")
                pending.forEach { unit ->
                    StudySecondaryButton(
                        text = "${unit.title} — ${unitReadinessShort(unit)}",
                        onClick = { onEditUnit(unit.unitIndex) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            StudyPrimaryButton(text = "Seguir editando", onClick = onDismiss)
        },
    )
}

@Composable
private fun ManualSavedScreen(onExit: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "¡Listo!",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Tus preguntas ya están guardadas. Van a aparecer en tus repasos.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StudyPrimaryButton(
                text = "Volver",
                onClick = onExit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---- Copy helpers ---------------------------------------------------------------------------

private fun markLabel(chooseFalse: Boolean): String = if (chooseFalse) "Marcar como falsa" else "Marcar correcta"

private fun markedLabel(chooseFalse: Boolean): String = if (chooseFalse) "Es la falsa" else "Correcta"

private fun unitReadinessShort(readiness: ManualUnitReadiness): String =
    when {
        readiness.isReady -> "Lista para tus repasos"
        readiness.needQuick > 0 && readiness.incompleteCount > 0 ->
            "Faltan ${readiness.needQuick} rápidas y completar ${readiness.incompleteCount}"
        readiness.needQuick > 0 -> "Faltan ${readiness.needQuick} preguntas rápidas"
        else -> "Completá ${readiness.incompleteCount} pregunta(s)"
    }

private fun readinessLongMessage(
    readiness: ManualUnitReadiness,
    unitTitle: String,
): String =
    when {
        readiness.isReady ->
            "✅ «$unitTitle» está lista. La vas a repasar cada día y te la vamos a recordar en el celular."
        readiness.needQuick == 1 ->
            "Te falta 1 pregunta con opciones para que «$unitTitle» sirva para estudiar."
        readiness.needQuick > 1 ->
            "Te faltan ${readiness.needQuick} preguntas con opciones para que «$unitTitle» sirva para estudiar."
        else ->
            "Terminá ${readiness.incompleteCount} pregunta(s) que quedaron sin completar."
    }
