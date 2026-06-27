package com.estudio.antiprocrastinacion.app.socialgate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.ui.common.AuxiliaryPromptHint
import com.estudio.antiprocrastinacion.app.ui.common.StudyBadgeTone
import com.estudio.antiprocrastinacion.app.ui.common.StudyChoiceButton
import com.estudio.antiprocrastinacion.app.ui.common.StudyChoiceState
import com.estudio.antiprocrastinacion.app.ui.common.StudyPrimaryButton
import com.estudio.antiprocrastinacion.app.ui.common.StudyProgressMeta
import com.estudio.antiprocrastinacion.app.ui.common.StudySecondaryButton
import com.estudio.antiprocrastinacion.app.ui.common.StudyStatusBadge
import com.estudio.antiprocrastinacion.app.ui.common.StudySurfaceCard
import com.estudio.antiprocrastinacion.app.ui.common.isTrueFalseMatch

/**
 * Full-screen gate content rendered inside the accessibility overlay. It mirrors the
 * Tarjetas pendientes screen (QuickStudyScreen) structure and components on purpose;
 * the only intentional differences are the gate context badge and the absence of any
 * exit/retry affordance, because the gate cannot be dismissed without solving it.
 */
@Composable
fun SocialGateOverlayScreen(
    state: SocialGatePromptState,
    onSubmitAnswer: (String, Boolean) -> Unit,
    onRevealAnswer: () -> Unit,
    onContinueAfterFeedback: () -> Unit,
    onUseEscape: () -> Unit,
) {
    val prompt = state.prompt
    var showEscapeDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    GateStudyHeader(state = state)
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
                            GatePromptOptions(
                                state = state,
                                onSubmitAnswer = onSubmitAnswer,
                                onRevealAnswer = onRevealAnswer,
                            )
                            AnimatedVisibility(
                                visible = state.answerFeedback != null,
                                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
                                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
                            ) {
                                state.answerFeedback?.let { feedback ->
                                    GateFeedbackBlock(
                                        feedback = feedback,
                                        continueLabel = if (state.isPendingCompletion) "Desbloquear app" else "Seguir",
                                        onContinue = onContinueAfterFeedback,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Comodín de escape: pequeño, en la esquina inferior. Solo aparece si quedan usos.
        if (state.escape.canUse) {
            TextButton(
                onClick = { showEscapeDialog = true },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        .padding(8.dp),
            ) {
                Text(
                    // Sin contador: el botón solo aparece cuando hay 1 uso disponible
                    // (1 por semana). Si no queda uso, el bloque entero no se muestra.
                    text = "Escape",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Confirmación del escape: inline (NO AlertDialog). Un Compose Dialog abre una ventana
        // hija que necesita token de Activity y crashea dentro del overlay del servicio
        // (BadTokenException). Se dibuja como scrim + card EN EL MISMO overlay.
        if (showEscapeDialog && state.escape.canUse) {
            GateEscapeConfirm(
                escape = state.escape,
                appDisplayName = state.targetAppDisplayName,
                onConfirm = {
                    showEscapeDialog = false
                    onUseEscape()
                },
                onDismiss = { showEscapeDialog = false },
            )
        }
    }
}

@Composable
private fun GateEscapeConfirm(
    escape: SocialGateEscapeInfo,
    appDisplayName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        StudySurfaceCard(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
            emphasized = true,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Comodín de escape",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = escapeDialogMessage(escape, appDisplayName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StudySecondaryButton(
                        text = "Cancelar",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    StudyPrimaryButton(
                        text = "Usar comodín",
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun escapeDialogMessage(
    escape: SocialGateEscapeInfo,
    appDisplayName: String,
): String =
    if (escape.canUse) {
        buildString {
            append("Vas a desbloquear $appDisplayName sin terminar el repaso.\n\n")
            append("Es tu única salida de emergencia de la semana.")
            escape.nextAvailableLabel?.let {
                append("\n\nSi la usás ahora, vas a poder volver a usarla recién el $it.")
            }
        }
    } else {
        val dia = escape.nextAvailableLabel ?: "la próxima semana"
        "Ya usaste tu salida de emergencia de esta semana.\n\nVas a poder usarla de nuevo el $dia."
    }

@Composable
private fun GateStudyHeader(state: SocialGatePromptState) {
    val session = state.prompt.session
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StudyStatusBadge(
            text = "Desbloquear ${state.targetAppDisplayName}",
            tone = StudyBadgeTone.Accent,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (session.currentCourseTitle.isNotBlank()) {
                    Text(
                        text = session.currentCourseTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = session.currentTopicTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            // Contador "X/Y" oculto a pedido del usuario: no muestra cuántas le quedan.
        }
        StudyProgressMeta(
            current = state.gateResolvedCount,
            total = state.gateRequiredCorrectAnswers,
            label = "",
        )
        if (state.prompt.pendingFailedItemCount > 0) {
            StudyStatusBadge(
                text = "Errores: ${state.prompt.pendingFailedItemCount}",
                tone = StudyBadgeTone.Danger,
            )
        }
    }
}

@Composable
private fun GatePromptOptions(
    state: SocialGatePromptState,
    onSubmitAnswer: (String, Boolean) -> Unit,
    onRevealAnswer: () -> Unit,
) {
    val item = state.prompt.item
    val selected = state.selectedResponseText
    val feedback = state.answerFeedback

    if (item.format == ItemFormat.TRUE_FALSE) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StudyChoiceButton(
                text = "Verdadero",
                onClick = { onSubmitAnswer("Verdadero", isTrueFalseMatch("Verdadero", item.correctAnswer)) },
                modifier = Modifier.fillMaxWidth(),
                state = gateChoiceStateFor("Verdadero", selected, feedback),
                enabled = feedback == null,
            )
            StudyChoiceButton(
                text = "Falso",
                onClick = { onSubmitAnswer("Falso", isTrueFalseMatch("Falso", item.correctAnswer)) },
                modifier = Modifier.fillMaxWidth(),
                state = gateChoiceStateFor("Falso", selected, feedback),
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
                    onClick = { onSubmitAnswer(option.text, option.isCorrect) },
                    modifier = Modifier.fillMaxWidth(),
                    state = gateChoiceStateFor(option.text, selected, feedback),
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
                onClick = { onSubmitAnswer("", true) },
                modifier = Modifier.weight(1f),
                enabled = feedback == null,
            )
            StudySecondaryButton(
                text = "La fallé",
                onClick = { onSubmitAnswer("", false) },
                modifier = Modifier.weight(1f),
                enabled = feedback == null,
            )
        }
    }
}

@Composable
private fun GateFeedbackBlock(
    feedback: SocialGateAnswerFeedback,
    continueLabel: String,
    onContinue: () -> Unit,
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
                text = feedback.title,
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
                text = continueLabel,
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun gateChoiceStateFor(
    optionText: String,
    selectedText: String?,
    feedback: SocialGateAnswerFeedback?,
): StudyChoiceState {
    if (selectedText == null || selectedText != optionText) return StudyChoiceState.Default
    if (feedback == null) return StudyChoiceState.Selected
    return if (feedback.isCorrect) StudyChoiceState.Selected else StudyChoiceState.ErrorSelected
}
