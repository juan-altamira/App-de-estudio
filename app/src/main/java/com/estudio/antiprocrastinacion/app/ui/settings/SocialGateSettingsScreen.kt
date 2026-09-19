package com.estudio.antiprocrastinacion.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.estudio.antiprocrastinacion.app.model.state.SocialGateInstalledApp
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRule
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRuntimePhase
import com.estudio.antiprocrastinacion.app.socialgate.SocialGateGuardStatus
import com.estudio.antiprocrastinacion.app.socialgate.SocialGateSchedule
import com.estudio.antiprocrastinacion.app.socialgate.toClockLabel
import com.estudio.antiprocrastinacion.app.ui.common.MinuteOfDayPickerDialog
import com.estudio.antiprocrastinacion.app.ui.common.StudyBadgeTone
import com.estudio.antiprocrastinacion.app.ui.common.StudyChipCloud
import com.estudio.antiprocrastinacion.app.ui.common.StudyPrimaryButton
import com.estudio.antiprocrastinacion.app.ui.common.StudyProgressMeta
import com.estudio.antiprocrastinacion.app.ui.common.StudySectionHeader
import com.estudio.antiprocrastinacion.app.ui.common.StudySecondaryButton
import com.estudio.antiprocrastinacion.app.ui.common.StudyStatusBadge
import com.estudio.antiprocrastinacion.app.ui.common.StudySurfaceCard
import com.estudio.antiprocrastinacion.app.ui.common.StudyTimeChipRow
import com.estudio.antiprocrastinacion.app.ui.common.StudyValueStepper

@Composable
fun SocialGateSettingsScreen(
    state: SocialGateSettingsUiState,
    guardStatus: SocialGateGuardStatus,
    onBack: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onMaxTriggersPerDayChange: (String, Int) -> Unit,
    onWindowStartMinutesChange: (String, Int) -> Unit,
    onWindowEndMinutesChange: (String, Int) -> Unit,
) {
    var pickerRequest by remember { mutableStateOf<GateTimePickerRequest?>(null) }
    var diagnosticsExpanded by remember { mutableStateOf(false) }
    var expandedPackages by remember { mutableStateOf(emptySet<String>()) }

    pickerRequest?.let { request ->
        MinuteOfDayPickerDialog(
            title = request.title,
            initialMinutes = request.initialMinutes,
            onDismiss = { pickerRequest = null },
            onConfirm = { pickedMinutes ->
                if (request.isStart) {
                    onWindowStartMinutesChange(request.packageName, pickedMinutes)
                } else {
                    onWindowEndMinutesChange(request.packageName, pickedMinutes)
                }
                pickerRequest = null
            },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            StudySecondaryButton(text = "Volver", onClick = onBack)
        }
        item {
            StudySectionHeader(
                title = "Gate social",
            )
        }
        item {
            StudySurfaceCard(
                modifier = Modifier.fillMaxWidth(),
                emphasized = true,
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StudyStatusBadge(
                        text =
                            when {
                                guardStatus.fullyOperational -> "Gate activo"
                                guardStatus.configuredButNotRunning -> "Listo (servicio iniciando)"
                                else -> "Permisos pendientes"
                            },
                        tone =
                            when {
                                guardStatus.fullyOperational -> StudyBadgeTone.Success
                                guardStatus.configuredButNotRunning -> StudyBadgeTone.Warning
                                else -> StudyBadgeTone.Danger
                            },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudyStatusBadge(
                            text = if (guardStatus.usageAccessGranted) "Acceso de uso ✓" else "Acceso de uso pendiente",
                            tone = if (guardStatus.usageAccessGranted) StudyBadgeTone.Success else StudyBadgeTone.Danger,
                        )
                        StudyStatusBadge(
                            text =
                                if (guardStatus.backgroundLaunchGranted) {
                                    "Reapertura en segundo plano ✓"
                                } else {
                                    "Reapertura pendiente"
                                },
                            tone = if (guardStatus.backgroundLaunchGranted) StudyBadgeTone.Success else StudyBadgeTone.Danger,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!guardStatus.usageAccessGranted) {
                            StudyPrimaryButton(
                                text = "Conceder acceso de uso",
                                onClick = onOpenUsageAccessSettings,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (!guardStatus.backgroundLaunchGranted) {
                            StudyPrimaryButton(
                                text = "Permitir reapertura en segundo plano",
                                onClick = onOpenOverlaySettings,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Text(
                        text = "El gate no usa accesibilidad ni dibuja una superposición. El segundo permiso solo permite " +
                            "volver a traer la pantalla del gate cuando intentás salir. En Xiaomi/HyperOS, además: " +
                            "Autostart habilitado, batería en \"Sin restricciones\" y la app bloqueada en Recientes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            StudySecondaryButton(
                text = if (diagnosticsExpanded) "Ocultar diagnóstico avanzado" else "Ver diagnóstico avanzado",
                onClick = { diagnosticsExpanded = !diagnosticsExpanded },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (diagnosticsExpanded) {
            item {
                StudySurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Diagnóstico avanzado", style = MaterialTheme.typography.titleMedium)
                        Text("Fase runtime: ${state.runtimePhase.toDisplayLabel()}", style = MaterialTheme.typography.bodyMedium)
                        state.runtimeTargetPackageName?.let {
                            Text("Target runtime: $it", style = MaterialTheme.typography.bodySmall)
                        }
                        state.unlockTokenPackageName?.let {
                            Text("Unlock vigente: $it", style = MaterialTheme.typography.bodySmall)
                        }
                        state.lastForegroundPackageName?.let {
                            Text("Último foreground: $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (state.installedApps.isEmpty()) {
            item {
                StudySurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("No se detectaron apps sociales soportadas", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        } else {
            items(state.installedApps, key = { it.packageName }) { app ->
                val expanded = expandedPackages.contains(app.packageName)
                SocialGateAppCard(
                    app = app,
                    solvedToday = state.solvedTodayByPackage[app.packageName] ?: 0,
                    runtimeLabel = state.runtimeLabelFor(app.packageName),
                    expanded = expanded,
                    onToggleExpanded = {
                        expandedPackages =
                            if (expanded) {
                                expandedPackages - app.packageName
                            } else {
                                expandedPackages + app.packageName
                            }
                    },
                    onMaxTriggersPerDayChange = onMaxTriggersPerDayChange,
                    onWindowStartRequest = {
                        pickerRequest =
                            GateTimePickerRequest(
                                packageName = app.packageName,
                                title = "Hora de inicio",
                                initialMinutes = app.windowStartMinutes,
                                isStart = true,
                            )
                    },
                    onWindowEndRequest = {
                        pickerRequest =
                            GateTimePickerRequest(
                                packageName = app.packageName,
                                title = "Hora de fin",
                                initialMinutes = app.windowEndMinutes,
                                isStart = false,
                            )
                    },
                )
            }
        }
    }
}

@Composable
private fun SocialGateAppCard(
    app: SocialGateInstalledApp,
    solvedToday: Int,
    runtimeLabel: String?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onMaxTriggersPerDayChange: (String, Int) -> Unit,
    onWindowStartRequest: () -> Unit,
    onWindowEndRequest: () -> Unit,
) {
    val status = app.statusSummary(solvedToday = solvedToday, runtimeLabel = runtimeLabel)
    val schedulePreview = app.schedulePreviewLabels()

    StudySurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = app.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                StudyStatusBadge(
                    text = status.label,
                    tone = status.tone,
                )
            }
            StudyProgressMeta(
                current = solvedToday,
                total = app.maxTriggersPerDay,
                label = "Hoy",
            )
            if (expanded) {
                StudyValueStepper(
                    label = "Activaciones por día",
                    valueLabel = app.maxTriggersPerDay.toString(),
                    onDecrease = {
                        onMaxTriggersPerDayChange(
                            app.packageName,
                            (app.maxTriggersPerDay - 1).coerceAtLeast(1),
                        )
                    },
                    onIncrease = {
                        onMaxTriggersPerDayChange(
                            app.packageName,
                            (app.maxTriggersPerDay + 1).coerceAtMost(SocialGateSchedule.MAX_TRIGGERS_PER_DAY),
                        )
                    },
                )
                StudySurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Cómo se desbloquea", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "El gate trae tus tarjetas pendientes y se desbloquea cuando las terminás. Para urgencias hay un comodín de escape dentro del gate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                StudySurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("Ventana activa", style = MaterialTheme.typography.titleSmall)
                        StudyTimeChipRow(
                            startLabel = "Desde ${app.windowStartMinutes.toClockLabel()}",
                            endLabel = "Hasta ${app.windowEndMinutes.toClockLabel()}",
                            onStartClick = onWindowStartRequest,
                            onEndClick = onWindowEndRequest,
                        )
                    }
                }
                StudySurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("Activaciones de hoy", style = MaterialTheme.typography.titleSmall)
                        StudyChipCloud(labels = schedulePreview)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "La primera activación ocurre a la hora inicial.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Si resolvés el gate, esa entrada queda libre mientras la app siga en foreground.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Si comprimís la ventana, la frecuencia se redistribuye dentro del día.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StudySecondaryButton(
                    text = "Ocultar avanzado",
                    onClick = onToggleExpanded,
                    modifier = Modifier.fillMaxWidth(),
                )
                StudySurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Avanzado", style = MaterialTheme.typography.titleSmall)
                        Text("Package: ${app.packageName}", style = MaterialTheme.typography.bodySmall)
                        runtimeLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudyStatusBadge(text = "Hoy: $solvedToday de ${app.maxTriggersPerDay}", tone = StudyBadgeTone.Neutral)
                }
                StudySecondaryButton(
                    text = "Editar configuración",
                    onClick = onToggleExpanded,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private data class GateTimePickerRequest(
    val packageName: String,
    val title: String,
    val initialMinutes: Int,
    val isStart: Boolean,
)

private data class SocialGateCardStatus(
    val label: String,
    val tone: StudyBadgeTone,
)

private fun SocialGateInstalledApp.statusSummary(
    solvedToday: Int,
    runtimeLabel: String?,
): SocialGateCardStatus =
    when {
        runtimeLabel == "Gate activo ahora" -> SocialGateCardStatus("Gate activo", StudyBadgeTone.Danger)
        runtimeLabel == "Desbloqueada para el foreground actual" -> SocialGateCardStatus("Desbloqueada en esta sesión", StudyBadgeTone.Success)
        solvedToday >= maxTriggersPerDay -> SocialGateCardStatus("Cuota diaria completa", StudyBadgeTone.Warning)
        else -> SocialGateCardStatus("Lista para bloquear", StudyBadgeTone.Accent)
    }

private fun SocialGateSettingsUiState.runtimeLabelFor(packageName: String): String? =
    when {
        runtimeTargetPackageName == packageName && runtimePhase == SocialGateRuntimePhase.ACTIVE_GATE -> "Gate activo ahora"
        unlockTokenPackageName == packageName -> "Desbloqueada para el foreground actual"
        lastForegroundPackageName == packageName -> "Fue la última app social detectada"
        else -> null
    }

private fun SocialGateInstalledApp.schedulePreviewLabels(): List<String> =
    SocialGateSchedule.triggerMinutes(
        SocialGateRule(
            packageName = packageName,
            displayName = displayName,
            enabled = enabled,
            maxTriggersPerDay = maxTriggersPerDay,
            requiredCorrectAnswers = requiredCorrectAnswers,
            windowStartMinutes = windowStartMinutes,
            windowEndMinutes = windowEndMinutes,
        ),
    ).map { it.toClockLabel() }

private fun SocialGateRuntimePhase.toDisplayLabel(): String =
    when (this) {
        SocialGateRuntimePhase.IDLE -> "Idle"
        SocialGateRuntimePhase.ACTIVE_GATE -> "Gate activo"
        SocialGateRuntimePhase.BLOCKING_EXISTING_SESSION -> "Bloqueando por sesión ya activa"
        SocialGateRuntimePhase.UNLOCKED_FOR_CURRENT_FOREGROUND -> "Desbloqueado en foreground"
    }
