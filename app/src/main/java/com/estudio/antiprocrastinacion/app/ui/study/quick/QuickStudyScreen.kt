package com.estudio.antiprocrastinacion.app.ui.study.quick

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.Surface as StudySurface
import com.estudio.antiprocrastinacion.app.ui.common.AuxiliaryPromptHint
import com.estudio.antiprocrastinacion.app.ui.common.StudyChoiceButton
import com.estudio.antiprocrastinacion.app.ui.common.isTrueFalseMatch
import com.estudio.antiprocrastinacion.app.ui.common.StudyChoiceState
import com.estudio.antiprocrastinacion.app.ui.common.StudyBadgeTone
import com.estudio.antiprocrastinacion.app.ui.common.StudyPrimaryButton
import com.estudio.antiprocrastinacion.app.ui.common.StudyProgressMeta
import com.estudio.antiprocrastinacion.app.ui.common.StudySecondaryButton
import com.estudio.antiprocrastinacion.app.ui.common.StudyStatusBadge
import com.estudio.antiprocrastinacion.app.ui.common.StudySurfaceCard
import kotlinx.coroutines.delay

@Composable
fun QuickStudyScreen(
    state: QuickStudyUiState,
    onSubmitAnswer: (String, Boolean, Long) -> Unit,
    onRevealAndSelfAssess: (String, Boolean, Long) -> Unit,
    onRevealAnswer: () -> Unit,
    onBackPressed: (String) -> Unit,
    onTimeout: (String) -> Unit,
    onContinueAfterFeedback: () -> Unit,
    onRetryCurrentPrompt: () -> Unit = {},
    onRequestTerminateSession: () -> Unit = {},
    onConfirmTerminateSession: () -> Unit = {},
    onDismissTerminateSessionConfirmation: () -> Unit = {},
) {
    val prompt = state.prompt
    var shownAt by remember(prompt?.item?.itemId) { mutableLongStateOf(System.currentTimeMillis()) }
    val hasPendingAnswer = state.answerFeedback != null

    if (state.isTerminateSessionConfirmOpen) {
        AlertDialog(
            onDismissRequest = onDismissTerminateSessionConfirmation,
            title = { Text("Terminar sesión") },
            text = { Text("Esta sesión se cierra solo para este modo.") },
            confirmButton = {
                StudyPrimaryButton(
                    text = "Terminar sesión",
                    leadingIcon = Icons.Rounded.StopCircle,
                    onClick = onConfirmTerminateSession,
                )
            },
            dismissButton = {
                StudySecondaryButton(
                    text = "Cancelar",
                    onClick = onDismissTerminateSessionConfirmation,
                )
            },
        )
    }

    prompt?.let {
        BackHandler {
            if (hasPendingAnswer) {
                onRetryCurrentPrompt()
            } else {
                onBackPressed(it.session.sessionId)
            }
        }
        LaunchedEffect(it.session.sessionId, it.session.lastInteractionAt, it.item.itemId, hasPendingAnswer) {
            if (hasPendingAnswer) return@LaunchedEffect
            delay(5 * 60 * 1000L)
            onTimeout(it.session.sessionId)
        }
    }

    if (prompt == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Preparando sesión...", style = MaterialTheme.typography.headlineSmall)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .imePadding()
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                CompactStudyHeader(prompt = prompt)
            }
            item {
                StudySurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    emphasized = true,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Text(
                            text = prompt.node.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AuxiliaryPromptHint(
                            promptKind = prompt.session.currentPromptKind,
                            triggerStems = prompt.auxiliaryTriggerStems,
                        )
                        Text(
                            text = prompt.item.stem,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        PromptOptions(
                            state = state,
                            prompt = prompt,
                            shownAt = shownAt,
                            onSubmitAnswer = onSubmitAnswer,
                            onRevealAndSelfAssess = onRevealAndSelfAssess,
                            onRevealAnswer = onRevealAnswer,
                            onQuestionDisplayed = { shownAt = System.currentTimeMillis() },
                        )
                        AnimatedVisibility(
                            visible = state.answerFeedback != null,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
                            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
                        ) {
                            state.answerFeedback?.let { feedback ->
                                FeedbackBlock(
                                    feedback = feedback,
                                    onContinue = onContinueAfterFeedback,
                                    onRetry = onRetryCurrentPrompt,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (prompt.session.mode != com.estudio.antiprocrastinacion.app.model.content.SessionMode.QUICK) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StudySecondaryButton(
                        text = "Terminar sesión",
                        leadingIcon = Icons.Rounded.StopCircle,
                        onClick = onRequestTerminateSession,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactStudyHeader(prompt: com.estudio.antiprocrastinacion.app.model.state.StudyPrompt) {
    val progressCurrent =
        if (prompt.session.surface == StudySurface.SOCIAL_GATE) {
            prompt.session.correctCount
        } else {
            prompt.session.correctCount
        }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (prompt.session.currentCourseTitle.isNotBlank()) {
                    Text(
                        text = prompt.session.currentCourseTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = prompt.session.currentTopicTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = "$progressCurrent/${prompt.session.goalCorrectCount}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        StudyProgressMeta(
            current = progressCurrent,
            total = prompt.session.goalCorrectCount,
            label = "",
        )
        if (prompt.pendingFailedItemCount > 0) {
            StudyStatusBadge(
                text = "Errores: ${prompt.pendingFailedItemCount}",
                tone = StudyBadgeTone.Danger,
            )
        }
    }
}

@Composable
private fun PromptOptions(
    state: QuickStudyUiState,
    prompt: com.estudio.antiprocrastinacion.app.model.state.StudyPrompt,
    shownAt: Long,
    onSubmitAnswer: (String, Boolean, Long) -> Unit,
    onRevealAndSelfAssess: (String, Boolean, Long) -> Unit,
    onRevealAnswer: () -> Unit,
    onQuestionDisplayed: () -> Unit,
) {
    val item = prompt.item
    val latency = { System.currentTimeMillis() - shownAt }
    val selected = state.selectedResponseText
    val feedback = state.answerFeedback

    LaunchedEffect(item.itemId, state.revealAnswer, feedback == null) {
        onQuestionDisplayed()
    }

    if (item.format == ItemFormat.TRUE_FALSE) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StudyChoiceButton(
                text = "Verdadero",
                onClick = {
                    onSubmitAnswer(
                        "Verdadero",
                        isTrueFalseMatch("Verdadero", item.correctAnswer),
                        latency(),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                state = choiceStateFor(optionText = "Verdadero", selectedText = selected, feedback = feedback),
                enabled = feedback == null,
            )
            StudyChoiceButton(
                text = "Falso",
                onClick = {
                    onSubmitAnswer(
                        "Falso",
                        isTrueFalseMatch("Falso", item.correctAnswer),
                        latency(),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                state = choiceStateFor(optionText = "Falso", selectedText = selected, feedback = feedback),
                enabled = feedback == null,
            )
        }
        return
    }

    if (item.options.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item.options.forEach { option ->
                StudyChoiceButton(
                    text = option.text,
                    onClick = { onSubmitAnswer(option.text, option.isCorrect, latency()) },
                    modifier = Modifier.fillMaxWidth(),
                    state = choiceStateFor(option.text, selected, feedback),
                    enabled = feedback == null,
                )
            }
        }
        return
    }

    if (!state.revealAnswer) {
        StudyPrimaryButton(
            text = "Ver respuesta",
            onClick = onRevealAnswer,
            modifier = Modifier.fillMaxWidth(),
            enabled = feedback == null,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = item.correctAnswer,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StudyPrimaryButton(
                text = "La acerté",
                onClick = { onRevealAndSelfAssess("", true, latency()) },
                modifier = Modifier.weight(1f),
                enabled = feedback == null,
            )
            StudySecondaryButton(
                text = "La fallé",
                onClick = { onRevealAndSelfAssess("", false, latency()) },
                modifier = Modifier.weight(1f),
                enabled = feedback == null,
            )
        }
    }
}

@Composable
private fun FeedbackBlock(
    feedback: AnswerFeedback,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
) {
    val backgroundColor =
        if (feedback.isCorrect) {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.36f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.34f)
        }
    val contentColor =
        if (feedback.isCorrect) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = backgroundColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (feedback.isCorrect) "Correcto" else "No",
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
            )
            feedback.detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
            }
            StudyPrimaryButton(
                text = "Seguir",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
            StudySecondaryButton(
                text = "Cambiar respuesta",
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun choiceStateFor(
    optionText: String,
    selectedText: String?,
    feedback: AnswerFeedback?,
): StudyChoiceState {
    if (selectedText == null || selectedText != optionText) return StudyChoiceState.Default
    if (feedback == null) return StudyChoiceState.Selected
    return if (feedback.isCorrect) StudyChoiceState.Selected else StudyChoiceState.ErrorSelected
}

