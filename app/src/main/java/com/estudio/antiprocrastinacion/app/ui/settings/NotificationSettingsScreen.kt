package com.estudio.antiprocrastinacion.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.estudio.antiprocrastinacion.app.notification.CognitiveNotificationPlanner
import com.estudio.antiprocrastinacion.app.ui.common.MinuteOfDayPickerDialog
import com.estudio.antiprocrastinacion.app.ui.common.StudyBadgeTone
import com.estudio.antiprocrastinacion.app.ui.common.StudyChipCloud
import com.estudio.antiprocrastinacion.app.ui.common.StudyPrimaryButton
import com.estudio.antiprocrastinacion.app.ui.common.StudySectionHeader
import com.estudio.antiprocrastinacion.app.ui.common.StudySecondaryButton
import com.estudio.antiprocrastinacion.app.ui.common.StudyStatusBadge
import com.estudio.antiprocrastinacion.app.ui.common.StudySurfaceCard
import com.estudio.antiprocrastinacion.app.ui.common.StudyTimeChipRow
import com.estudio.antiprocrastinacion.app.ui.common.StudyValueStepper

@Composable
fun NotificationSettingsScreen(
    state: NotificationSettingsUiState,
    notificationPermissionGranted: Boolean,
    onBack: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    onNotificationsPerDayChange: (Int) -> Unit,
    onNotificationWindowStartMinutesChange: (Int) -> Unit,
    onNotificationWindowEndMinutesChange: (Int) -> Unit,
) {
    var pickerRequest by remember { mutableStateOf<NotificationTimePickerRequest?>(null) }

    pickerRequest?.let { request ->
        MinuteOfDayPickerDialog(
            title = request.title,
            initialMinutes = request.initialMinutes,
            onDismiss = { pickerRequest = null },
            onConfirm = { pickedMinutes ->
                if (request.isStart) {
                    onNotificationWindowStartMinutesChange(pickedMinutes)
                } else {
                    onNotificationWindowEndMinutesChange(pickedMinutes)
                }
                pickerRequest = null
            },
        )
    }

    val planner = remember { CognitiveNotificationPlanner() }
    val schedulePreview =
        planner.buildPlans(
            settings = state.settings,
            nowEpochMs = System.currentTimeMillis(),
        ).map { it.minuteOfDay.toClockLabel() }

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
                title = "Notificación cognitiva",
                subtitle = "Empuja la entrada al estudio dentro de una ventana diaria y solo cuando hay contenido pendiente apto.",
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
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StudyStatusBadge(
                            text = if (state.settings.cognitiveNotificationsEnabled) "Activa" else "Apagada",
                            tone = if (state.settings.cognitiveNotificationsEnabled) StudyBadgeTone.Accent else StudyBadgeTone.Neutral,
                        )
                        Text(
                            text = "Si hay due_easy o derived_easy, la app puede interceptarte con una entrada corta y directa al estudio.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Habilitar notificaciones",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Switch(
                            checked = state.settings.cognitiveNotificationsEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && !notificationPermissionGranted) {
                                    onRequestNotificationPermission()
                                } else {
                                    onNotificationsEnabledChange(enabled)
                                }
                            },
                        )
                    }
                    if (!notificationPermissionGranted) {
                        StudyStatusBadge(
                            text = "Permiso pendiente",
                            tone = StudyBadgeTone.Danger,
                        )
                        StudyPrimaryButton(
                            text = "Conceder permiso",
                            onClick = onRequestNotificationPermission,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        item {
            StudyValueStepper(
                label = "Activaciones por día",
                valueLabel = state.settings.cognitiveNotificationsPerDay.toString(),
                onDecrease = {
                    onNotificationsPerDayChange((state.settings.cognitiveNotificationsPerDay - 1).coerceAtLeast(1))
                },
                onIncrease = {
                    onNotificationsPerDayChange(
                        (state.settings.cognitiveNotificationsPerDay + 1)
                            .coerceAtMost(CognitiveNotificationPlanner.MAX_COGNITIVE_NOTIFICATIONS_PER_DAY),
                    )
                },
            )
        }
        item {
            StudySurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Ventana activa",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    StudyTimeChipRow(
                        startLabel = "Desde ${state.settings.cognitiveNotificationWindowStartMinutes.toClockLabel()}",
                        endLabel = "Hasta ${state.settings.cognitiveNotificationWindowEndMinutes.toClockLabel()}",
                        onStartClick = {
                            pickerRequest =
                                NotificationTimePickerRequest(
                                    title = "Hora de inicio",
                                    initialMinutes = state.settings.cognitiveNotificationWindowStartMinutes,
                                    isStart = true,
                                )
                        },
                        onEndClick = {
                            pickerRequest =
                                NotificationTimePickerRequest(
                                    title = "Hora de fin",
                                    initialMinutes = state.settings.cognitiveNotificationWindowEndMinutes,
                                    isStart = false,
                                )
                        },
                    )
                    Text(
                        text = "La primera activación del día se ancla a la hora inicial. Si no hay contenido apto, no se dispara nada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            StudySurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Activaciones de hoy",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (schedulePreview.isEmpty()) {
                        Text(
                            text = "No hay activaciones programadas con la configuración actual.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        StudyChipCloud(labels = schedulePreview)
                    }
                }
            }
        }
    }
}

private data class NotificationTimePickerRequest(
    val title: String,
    val initialMinutes: Int,
    val isStart: Boolean,
)

private fun Int.toClockLabel(): String {
    val hour = (this / 60).coerceIn(0, 23)
    val minute = (this % 60).coerceIn(0, 59)
    return "%02d:%02d".format(hour, minute)
}
