package com.estudio.antiprocrastinacion.app.ui.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.estudio.antiprocrastinacion.app.domain.repository.ImportExecutionResult
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationMessage
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationReport
import com.estudio.antiprocrastinacion.app.ui.common.StudyBadgeTone
import com.estudio.antiprocrastinacion.app.ui.common.StudyPrimaryButton
import com.estudio.antiprocrastinacion.app.ui.common.StudySecondaryButton
import com.estudio.antiprocrastinacion.app.ui.common.StudyStatusBadge
import com.estudio.antiprocrastinacion.app.ui.common.StudySurfaceCard

private const val ManualImportScrollTag = "manual_import_scroll"
private const val ImportResultStatusTag = "import_result_status"

@Composable
fun ImportScreen(
    state: ContentUiState,
    onBack: () -> Unit,
    onImportClick: () -> Unit,
    onImportSnapshotClick: () -> Unit,
    onExportSnapshotClick: () -> Unit,
    onImportUserStateClick: () -> Unit,
    onExportUserStateClick: () -> Unit,
    onOpenEditableImport: () -> Unit,
    onOpenManualBuilder: () -> Unit,
    onOpenManualImport: () -> Unit,
    onCloseManualImport: () -> Unit,
    onManualImportTextChange: (String) -> Unit,
    onClearManualImportText: () -> Unit,
    onRequestManualImportConfirmation: () -> Unit,
    onDismissManualImportConfirmation: () -> Unit,
    onConfirmManualImport: () -> Unit,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }

    if (state.isManualImportConfirmOpen) {
        AlertDialog(
            onDismissRequest = onDismissManualImportConfirmation,
            title = { Text("Importar JSON") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val preview = state.manualImportPreview
                    val report = state.manualImportReport
                    Text("Tipo: ${state.manualImportKind?.name ?: "JSON"}")
                    if (preview != null) {
                        Text(
                            text = "Total: ${preview.itemCount} tarjetas",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        importQuestionCategories.forEach { category ->
                            val count = preview.questions.count { it.formatLabel == category }
                            Text(
                                text = "$count de ${category.lowercase()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "Paquete: ${preview.packageId} · nuevos: ${preview.newCourseCount} curso / ${preview.newUnitCount} unidad / ${preview.newNodeCount} nodo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (preview.potentialArchivedNodeCount > 0) {
                            Text(
                                text = "Archivaría candidatos: ${preview.potentialArchivedNodeCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (report != null) {
                        if (report.structuralErrors.isNotEmpty()) {
                            Text(
                                text = "Errores: ${report.structuralErrors.size}",
                                color = MaterialTheme.colorScheme.error,
                            )
                            report.structuralErrors.take(3).forEach { error ->
                                Text(
                                    text = error.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (report.authoringWarnings.isNotEmpty()) {
                            Text(
                                text = "Warnings: ${report.authoringWarnings.size}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            report.authoringWarnings.take(3).forEach { warning ->
                                Text(
                                    text = warning.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    preview?.questions?.takeIf { it.isNotEmpty() }?.let { qs ->
                        Text(
                            text = "Vista previa de tarjetas (${qs.size}):",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        qs.forEachIndexed { index, question ->
                            ImportQuestionPreviewItem(index = index + 1, question = question)
                        }
                    }
                }
            },
            confirmButton = {
                StudyPrimaryButton(
                    text = "Importar",
                    leadingIcon = Icons.Rounded.DoneAll,
                    onClick = onConfirmManualImport,
                    enabled = state.manualImportReport?.canImport != false,
                )
            },
            dismissButton = {
                StudySecondaryButton(
                    text = "Cancelar",
                    leadingIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                    onClick = onDismissManualImportConfirmation,
                )
            },
        )
    }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Volver",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = "Importar",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Menú",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Exportar") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Rounded.IosShare, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onExportSnapshotClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Importar estado") },
                        leadingIcon = {
                            Icon(imageVector = Icons.AutoMirrored.Rounded.Undo, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onImportUserStateClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Exportar estado") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Rounded.IosShare, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onExportUserStateClick()
                        },
                    )
                }
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag(ManualImportScrollTag)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.isManualImportOpen) {
                    StudySurfaceCard(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            OutlinedTextField(
                                value = state.manualImportText,
                                onValueChange = onManualImportTextChange,
                                modifier =
                                    Modifier
                                        .fillMaxWidth(),
                                minLines = 8,
                                maxLines = 12,
                                placeholder = { Text("JSON") },
                            )
                            StudyPrimaryButton(
                                text = "Importar",
                                leadingIcon = Icons.Rounded.DoneAll,
                                onClick = onRequestManualImportConfirmation,
                                enabled = state.manualImportText.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            StudySecondaryButton(
                                text = "Limpiar",
                                leadingIcon = Icons.Rounded.Code,
                                onClick = onClearManualImportText,
                                enabled = state.manualImportText.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            StudySecondaryButton(
                                text = "Cerrar",
                                leadingIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                                onClick = onCloseManualImport,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                } else {
                    StudyPrimaryButton(
                        text = "Crear manualmente",
                        leadingIcon = Icons.Rounded.Edit,
                        onClick = onOpenManualBuilder,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StudySecondaryButton(
                        text = "Archivo",
                        leadingIcon = Icons.Rounded.UploadFile,
                        onClick = onImportClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StudySecondaryButton(
                        text = "JSON",
                        leadingIcon = Icons.Rounded.Code,
                        onClick = onOpenManualImport,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StudySecondaryButton(
                        text = "Texto editable",
                        leadingIcon = Icons.Rounded.Edit,
                        onClick = onOpenEditableImport,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StudySecondaryButton(
                        text = "Snapshot",
                        leadingIcon = Icons.Rounded.Inventory2,
                        onClick = onImportSnapshotClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                state.lastImportResult?.let { result ->
                    ImportStatusCard(
                        title = if (result.imported) "Importado" else "Error",
                        detail = result.summaryText(),
                        tone = if (result.imported) StudyBadgeTone.Success else StudyBadgeTone.Danger,
                        expandedDetail = result.expandedDetail(),
                        testTag = ImportResultStatusTag,
                    )
                }
                state.lastSnapshotResult?.let { result ->
                    ImportStatusCard(
                        title = if (result.success) "Snapshot" else "Error",
                        detail = result.message,
                        tone = if (result.success) StudyBadgeTone.Success else StudyBadgeTone.Danger,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportStatusCard(
    title: String,
    detail: String,
    tone: StudyBadgeTone,
    expandedDetail: String? = null,
    testTag: String? = null,
) {
    var expanded by rememberSaveable(title, detail, expandedDetail) { mutableStateOf(false) }
    val hasExpandableDetail = expandedDetail != null
    val cardModifier =
        (testTag?.let { Modifier.testTag(it) } ?: Modifier)
            .fillMaxWidth()
            .clickable(
                enabled = hasExpandableDetail,
                role = Role.Button,
                onClick = { expanded = !expanded },
            )

    StudySurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = cardModifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                StudyStatusBadge(
                    text = title,
                    tone = tone,
                )
                if (hasExpandableDetail) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Ocultar detalle" else "Ver detalle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hasExpandableDetail) {
                Text(
                    text = if (expanded) "Tocar para ocultar el detalle." else "Tocar para ver el detalle completo.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded && expandedDetail != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Text(
                    text = expandedDetail.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tone == StudyBadgeTone.Danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun ImportExecutionResult.summaryText(): String =
    when {
        imported && report.authoringWarnings.isEmpty() -> "Contenido cargado."
        imported -> "Contenido cargado con ${report.authoringWarnings.size} advertencias."
        else -> "${report.structuralErrors.size} errores. No se importó contenido."
    }

private fun ImportExecutionResult.expandedDetail(): String? {
    val sections =
        buildList {
            if (report.structuralErrors.isNotEmpty()) {
                add(report.structuralErrors.asDetailedSection("Errores estructurales fatales", imported = imported))
            }
            if (report.authoringWarnings.isNotEmpty()) {
                add(report.authoringWarnings.asDetailedSection("Advertencias de autoría", imported = imported))
            }
        }
    if (sections.isEmpty()) return null
    val effect =
        if (imported) {
            "Efecto: el contenido fue importado; revisá las advertencias porque señalan baja calidad o datos sospechosos."
        } else {
            "Efecto: la importación fue bloqueada. No se guardó contenido nuevo ni se modificó el progreso."
        }
    return buildString {
        appendLine("Paquete: $packageId")
        appendLine(effect)
        append(sections.joinToString(separator = "\n\n"))
    }.trim()
}

private fun List<ValidationMessage>.asDetailedSection(
    title: String,
    imported: Boolean,
): String {
    val messages = this
    return buildString {
        appendLine("$title (${size})")
        messages.forEachIndexed { index, message ->
            if (index > 0) appendLine()
            appendLine("${index + 1}. Código: ${message.code}")
            appendLine("Ubicación: ${message.path ?: message.locationLabel()}")
            message.expected?.let { expected -> appendLine("Esperado: $expected") }
            message.actual?.let { actual -> appendLine("Recibido: $actual") }
            appendLine("Detalle exacto: ${message.message}")
            append("Acción necesaria: ${message.hint ?: message.actionLabel(imported)}")
        }
    }
}

private fun ValidationMessage.locationLabel(): String {
    val parts = code.split(":")
    return when {
        parts.size >= 3 -> parts.drop(1).joinToString(" -> ")
        parts.size == 2 -> parts[1]
        else -> "paquete completo o parseo inicial"
    }
}

private fun ValidationMessage.actionLabel(imported: Boolean): String =
    when {
        code.contains("parse_error") -> "Corregí la sintaxis JSON o la forma del paquete y volvé a importar."
        code.contains("unknown_package_type") -> "Usá un JSON con contrato content_package o authoring_draft."
        code.contains("duplicate") -> "Dejá un solo ID por entidad repetida."
        code.contains("missing") || code.contains("unknown") -> "Agregá la referencia faltante o corregí el ID referenciado."
        code.contains("blank") || code.contains("empty") -> "Completá el campo obligatorio indicado."
        code.contains("invalid") -> "Reemplazá el valor por uno dentro del rango o enum permitido."
        code.contains("mismatch") -> "Alineá los campos relacionados para que describan la misma entidad."
        imported -> "La app permitió importar, pero conviene corregir este punto para mejorar la calidad del banco."
        else -> "Corregí este punto en el JSON y volvé a importar."
    }

@Composable
private fun ImportQuestionPreviewItem(
    index: Int,
    question: ImportQuestionPreview,
) {
    StudySurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "$index · ${question.formatLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = question.stem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            question.options.forEach { option ->
                Text(
                    text = (if (option.isCorrect) "✓ " else "• ") + option.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (option.isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (question.options.isEmpty()) {
                Text(
                    text = "Respuesta: ${question.correctAnswer}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
