package com.estudio.antiprocrastinacion.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.estudio.antiprocrastinacion.app.model.state.SessionPromptKind

/**
 * Etiqueta sutil que aparece SOLO en tarjetas auxiliares (anzuelo / rescate / calentamiento del gate)
 * para que durante el testeo se pueda distinguir de un vistazo una tarjeta real de una auxiliar, y un
 * "ver detalle" discreto que explica por qué apareció. En prompts reales no renderiza nada.
 *
 * [triggerStems] solo se usa para el rescate: son los stems de las tarjetas de fricción alta que se
 * fallaron y dispararon el rescate.
 */
@Composable
fun AuxiliaryPromptHint(
    promptKind: SessionPromptKind,
    triggerStems: List<String>,
    modifier: Modifier = Modifier,
) {
    val info = auxiliaryHintInfo(promptKind, triggerStems) ?: return
    var expanded by remember(promptKind, triggerStems) { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StudyStatusBadge(text = info.label, tone = StudyBadgeTone.Neutral)
            Text(
                text = if (expanded) "ocultar" else "ver detalle",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = info.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class AuxiliaryHintInfo(
    val label: String,
    val detail: String,
)

private fun auxiliaryHintInfo(
    promptKind: SessionPromptKind,
    triggerStems: List<String>,
): AuxiliaryHintInfo? =
    when (promptKind) {
        SessionPromptKind.AUXILIARY_ANZUELO ->
            AuxiliaryHintInfo(
                label = "Anzuelo",
                detail =
                    "Tarjeta fácil de entrada para arrancar suave: la primera tarjeta real de este tema " +
                        "es de fricción alta. No cuenta para tu repaso.",
            )

        SessionPromptKind.AUXILIARY_RESCUE ->
            AuxiliaryHintInfo(
                label = "Rescate",
                detail =
                    buildString {
                        append("Apareció para destrabarte después de varios errores seguidos")
                        val shown = triggerStems.filter { it.isNotBlank() }.take(3)
                        if (shown.isNotEmpty()) {
                            append(", concretamente en: ")
                            append(shown.joinToString(", ") { "«$it»" })
                        }
                        append(". No cuenta para tu repaso.")
                    },
            )

        SessionPromptKind.AUXILIARY_GATE_FILL ->
            AuxiliaryHintInfo(
                label = "Calentamiento",
                detail = "Tarjeta de calentamiento del gate para llegar al objetivo. No cuenta para tu repaso.",
            )

        SessionPromptKind.REAL_PENDING,
        SessionPromptKind.REAL_CORRECTION,
        SessionPromptKind.REAL_GATE,
        SessionPromptKind.MANUAL,
        SessionPromptKind.BACK_MICRO,
        -> null
    }
