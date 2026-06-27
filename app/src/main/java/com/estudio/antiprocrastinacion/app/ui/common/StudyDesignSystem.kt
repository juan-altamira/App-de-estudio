package com.estudio.antiprocrastinacion.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class StudyBadgeTone {
    Accent,
    Success,
    Warning,
    Neutral,
    Danger,
}

enum class StudyChoiceState {
    Default,
    Selected,
    ErrorSelected,
}

@Composable
fun StudySurfaceCard(
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (emphasized) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
    ) {
        content()
    }
}

@Composable
fun StudyPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 60.dp),
        enabled = enabled,
        shape = CircleShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.74f),
            ),
    ) {
        StudyButtonLabel(
            text = text,
            leadingIcon = leadingIcon,
            iconSize = 20.dp,
        )
    }
}

@Composable
fun StudySecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 60.dp),
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.9f)),
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            ),
    ) {
        StudyButtonLabel(
            text = text,
            leadingIcon = leadingIcon,
            iconSize = 18.dp,
        )
    }
}

@Composable
fun StudyStatusBadge(
    text: String,
    tone: StudyBadgeTone,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val containerColor: Color
    val contentColor: Color
    when (tone) {
        StudyBadgeTone.Accent -> {
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            contentColor = MaterialTheme.colorScheme.primary
        }
        StudyBadgeTone.Success -> {
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        }
        StudyBadgeTone.Warning -> {
            containerColor = MaterialTheme.colorScheme.secondaryContainer
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        }
        StudyBadgeTone.Neutral -> {
            containerColor = MaterialTheme.colorScheme.surfaceVariant
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
        StudyBadgeTone.Danger -> {
            containerColor = MaterialTheme.colorScheme.errorContainer
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun StudyButtonLabel(
    text: String,
    leadingIcon: ImageVector?,
    iconSize: androidx.compose.ui.unit.Dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
fun StudySectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun StudyProgressMeta(
    current: Int,
    total: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val safeTotal = total.coerceAtLeast(1)
    val progress = (current.coerceIn(0, safeTotal)).toFloat() / safeTotal.toFloat()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (label.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$current de $total",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            gapSize = 0.dp,
        )
    }
}

@Composable
fun StudyInlineFeedback(
    title: String,
    detail: String?,
    isPositive: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = if (isPositive) StudyBadgeTone.Success else StudyBadgeTone.Danger
    val background =
        if (isPositive) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
    val content =
        if (isPositive) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        }
    Surface(
        modifier = modifier,
        color = background,
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StudyStatusBadge(
                    text = title,
                    tone = tone,
                )
                detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = content,
                    )
                }
            }
            StudyPrimaryButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun StudyChoiceButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: StudyChoiceState = StudyChoiceState.Default,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val targetContainer =
        when (state) {
            StudyChoiceState.Default -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
            StudyChoiceState.Selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            StudyChoiceState.ErrorSelected -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f)
        }
    val targetContent =
        when (state) {
            StudyChoiceState.Default -> MaterialTheme.colorScheme.onSurface
            StudyChoiceState.Selected -> MaterialTheme.colorScheme.primary
            StudyChoiceState.ErrorSelected -> MaterialTheme.colorScheme.onErrorContainer
        }
    val targetBorder =
        when (state) {
            StudyChoiceState.Default -> MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)
            StudyChoiceState.Selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
            StudyChoiceState.ErrorSelected -> MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
        }
    val containerColor by animateColorAsState(targetContainer, label = "choiceContainer")
    val contentColor by animateColorAsState(targetContent, label = "choiceContent")
    val borderColor by animateColorAsState(targetBorder, label = "choiceBorder")
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 60.dp),
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, borderColor),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = containerColor.copy(alpha = 0.45f),
                disabledContentColor = contentColor.copy(alpha = 0.45f),
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = text,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 6,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
fun StudyValueStepper(
    label: String,
    valueLabel: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StudySurfaceCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StepperActionButton(label = "−", onClick = onDecrease)
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                StepperActionButton(label = "+", onClick = onIncrease)
            }
        }
    }
}

@Composable
private fun StepperActionButton(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(52.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun StudyTimeChipRow(
    startLabel: String,
    endLabel: String,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudySecondaryButton(text = startLabel, onClick = onStartClick, modifier = Modifier.weight(1f))
        StudySecondaryButton(text = endLabel, onClick = onEndClick, modifier = Modifier.weight(1f))
    }
}

@Composable
fun StudyChipCloud(
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEach { label ->
            StudyStatusBadge(text = label, tone = StudyBadgeTone.Neutral)
        }
    }
}

@Composable
fun StudyDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
    )
}

@Composable
fun StudySectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
