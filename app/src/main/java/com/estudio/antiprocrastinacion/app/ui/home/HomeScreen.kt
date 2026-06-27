package com.estudio.antiprocrastinacion.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.estudio.antiprocrastinacion.app.ui.common.StudyPrimaryButton
import com.estudio.antiprocrastinacion.app.ui.common.StudySecondaryButton

@Composable
fun HomeScreen(
    state: HomeUiState,
    onStartQuick: () -> Unit,
    onOpenContent: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenUpcomingReviews: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenSocialGateSettings: () -> Unit,
    onOpenDeep: () -> Unit,
    onOpenDrain: () -> Unit,
    onAcknowledgeRecoveryNotice: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val recoveryNotice = state.recoveryNotice
        if (recoveryNotice != null) {
            item {
                RecoveryBanner(
                    message = recoveryNotice,
                    onAcknowledge = onAcknowledgeRecoveryNotice,
                )
            }
        }
        item {
            Text(
                text = "Estudiar",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            StudyPrimaryButton(
                text = "Tarjetas pendientes",
                leadingIcon = Icons.Rounded.PlayArrow,
                onClick = onStartQuick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            StudyPrimaryButton(
                text = "Modo profundo",
                leadingIcon = Icons.Rounded.Bolt,
                onClick = onOpenDeep,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            StudyPrimaryButton(
                text = "Vaciar",
                leadingIcon = Icons.Rounded.Archive,
                onClick = onOpenDrain,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Spacer(modifier = Modifier.padding(vertical = 4.dp))
        }
        item {
            StudySecondaryButton(
                text = "Contenido",
                leadingIcon = Icons.Rounded.Inventory2,
                onClick = onOpenContent,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            StudySecondaryButton(
                text = "Importar",
                leadingIcon = Icons.Rounded.UploadFile,
                onClick = onOpenImport,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            StudySecondaryButton(
                text = "Próximos repasos",
                leadingIcon = Icons.Rounded.CalendarMonth,
                onClick = onOpenUpcomingReviews,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Spacer(modifier = Modifier.padding(vertical = 4.dp))
        }
        item {
            StudySecondaryButton(
                text = "Notificación",
                leadingIcon = Icons.Rounded.Notifications,
                onClick = onOpenNotificationSettings,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            StudySecondaryButton(
                text = "Gate social",
                leadingIcon = Icons.Rounded.Shield,
                onClick = onOpenSocialGateSettings,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RecoveryBanner(
    message: String,
    onAcknowledge: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Recuperación de datos",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            StudyPrimaryButton(
                text = "OK",
                onClick = onAcknowledge,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
