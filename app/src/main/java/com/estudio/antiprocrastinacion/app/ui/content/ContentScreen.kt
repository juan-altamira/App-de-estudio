package com.estudio.antiprocrastinacion.app.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.estudio.antiprocrastinacion.app.ui.common.StudyBadgeTone
import com.estudio.antiprocrastinacion.app.ui.common.StudyPrimaryButton
import com.estudio.antiprocrastinacion.app.ui.common.StudySecondaryButton
import com.estudio.antiprocrastinacion.app.ui.common.StudyStatusBadge
import com.estudio.antiprocrastinacion.app.ui.common.StudySurfaceCard

@Composable
fun ContentScreen(
    state: ContentUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onExportSnapshotClick: () -> Unit,
    onArchiveFilterChange: (ArchiveFilter) -> Unit,
    onConfirmReset: () -> Unit,
    onDismissReset: () -> Unit,
    onRequestCourseReset: (String) -> Unit,
    onRequestUnitReset: (String) -> Unit,
    onToggleCourseArchive: (String, Boolean) -> Unit,
    onToggleUnitArchive: (String, Boolean) -> Unit,
    onToggleItemArchive: (String, Boolean) -> Unit,
    onOpenItemEdit: (String) -> Unit,
    onDismissItemEdit: () -> Unit,
    onConfirmItemEdit: () -> Unit,
    onPendingItemEditStemChange: (String) -> Unit,
    onPendingItemEditCorrectAnswerChange: (String) -> Unit,
    onPendingItemEditOptionsTextChange: (String) -> Unit,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedCourseId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedUnitId by rememberSaveable { mutableStateOf<String?>(null) }

    val selectedCourse = state.visibleCourses.firstOrNull { it.courseId == selectedCourseId }
    val selectedUnit = selectedCourse?.units?.firstOrNull { it.unitId == selectedUnitId }

    LaunchedEffect(state.visibleCourses, selectedCourseId, selectedUnitId) {
        if (selectedCourseId != null && selectedCourse == null) {
            selectedCourseId = null
            selectedUnitId = null
        } else if (selectedUnitId != null && selectedUnit == null) {
            selectedUnitId = null
        }
    }

    state.pendingResetTarget?.let { target ->
        AlertDialog(
            onDismissRequest = onDismissReset,
            title = { Text("Reiniciar progreso") },
            text = { Text(target.title) },
            confirmButton = {
                StudyPrimaryButton(
                    text = "Reiniciar",
                    leadingIcon = Icons.Rounded.RestartAlt,
                    onClick = onConfirmReset,
                )
            },
            dismissButton = {
                StudySecondaryButton(
                    text = "Cancelar",
                    leadingIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                    onClick = onDismissReset,
                )
            },
        )
    }

    state.pendingItemEdit?.let { draft ->
        AlertDialog(
            onDismissRequest = onDismissItemEdit,
            title = { Text("Editar tarjeta") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = draft.nodeTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = draft.stem,
                        onValueChange = onPendingItemEditStemChange,
                        label = { Text("Pregunta") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.correctAnswer,
                        onValueChange = onPendingItemEditCorrectAnswerChange,
                        label = { Text("Respuesta correcta") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.optionsText,
                        onValueChange = onPendingItemEditOptionsTextChange,
                        label = { Text("Opciones, una por línea. Usá * para la correcta") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                StudyPrimaryButton(
                    text = "Guardar",
                    onClick = onConfirmItemEdit,
                )
            },
            dismissButton = {
                StudySecondaryButton(
                    text = "Cancelar",
                    onClick = onDismissItemEdit,
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
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ContentTopBar(
                onBack = {
                    when {
                        selectedUnitId != null -> selectedUnitId = null
                        selectedCourseId != null -> {
                            selectedCourseId = null
                            selectedUnitId = null
                        }
                        else -> onBack()
                    }
                },
                menuExpanded = menuExpanded,
                onMenuExpandedChange = { menuExpanded = it },
                onRefresh = onRefresh,
                onExportSnapshotClick = onExportSnapshotClick,
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(22.dp),
                        )
                        .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ContentSegmentButton(
                    text = "Activos",
                    selected = state.archiveFilter == ArchiveFilter.ACTIVE_ONLY,
                    onClick = {
                        selectedCourseId = null
                        selectedUnitId = null
                        onArchiveFilterChange(ArchiveFilter.ACTIVE_ONLY)
                    },
                    modifier = Modifier.weight(1f),
                )
                ContentSegmentButton(
                    text = "Archivados",
                    selected = state.archiveFilter == ArchiveFilter.ARCHIVED_ONLY,
                    onClick = {
                        selectedCourseId = null
                        selectedUnitId = null
                        onArchiveFilterChange(ArchiveFilter.ARCHIVED_ONLY)
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.isLoading) {
                LevelPlaceholder(
                    text = "Cargando",
                    modifier = Modifier.fillMaxSize(),
                )
                return@Column
            }

            if (state.visibleCourses.isEmpty()) {
                LevelPlaceholder(
                    text = if (state.archiveFilter == ArchiveFilter.ACTIVE_ONLY) "Sin contenido" else "Sin archivados",
                    modifier = Modifier.fillMaxSize(),
                )
                return@Column
            }

            when {
                selectedUnit != null -> {
                    QuestionLevelContent(
                        course = requireNotNull(selectedCourse),
                        unit = selectedUnit,
                        archiveFilter = state.archiveFilter,
                        onBackToUnits = { selectedUnitId = null },
                        onRequestUnitReset = onRequestUnitReset,
                        onToggleUnitArchive = onToggleUnitArchive,
                        onToggleItemArchive = onToggleItemArchive,
                        onOpenItemEdit = onOpenItemEdit,
                    )
                }

                selectedCourse != null -> {
                    UnitLevelContent(
                        course = selectedCourse,
                        archiveFilter = state.archiveFilter,
                        onBackToCourses = {
                            selectedCourseId = null
                            selectedUnitId = null
                        },
                        onOpenUnit = { unitId -> selectedUnitId = unitId },
                        onRequestUnitReset = onRequestUnitReset,
                        onToggleUnitArchive = onToggleUnitArchive,
                    )
                }

                else -> {
                    CourseLevelContent(
                        courses = state.visibleCourses,
                        archiveFilter = state.archiveFilter,
                        onOpenCourse = { courseId ->
                            selectedCourseId = courseId
                            selectedUnitId = null
                        },
                        onRequestCourseReset = onRequestCourseReset,
                        onToggleCourseArchive = onToggleCourseArchive,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentTopBar(
    onBack: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onExportSnapshotClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Volver",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = "Contenido",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Box {
            IconButton(onClick = { onMenuExpandedChange(true) }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Menú",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { onMenuExpandedChange(false) },
            ) {
                DropdownMenuItem(
                    text = { Text("Actualizar") },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null) },
                    onClick = {
                        onMenuExpandedChange(false)
                        onRefresh()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Exportar") },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.IosShare, contentDescription = null) },
                    onClick = {
                        onMenuExpandedChange(false)
                        onExportSnapshotClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun CourseLevelContent(
    courses: List<ContentCourseRow>,
    archiveFilter: ArchiveFilter,
    onOpenCourse: (String) -> Unit,
    onRequestCourseReset: (String) -> Unit,
    onToggleCourseArchive: (String, Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(courses, key = ContentCourseRow::courseId) { course ->
            CourseCard(
                course = course,
                archiveFilter = archiveFilter,
                onOpenCourse = onOpenCourse,
                onRequestCourseReset = onRequestCourseReset,
                onToggleCourseArchive = onToggleCourseArchive,
            )
        }
    }
}

@Composable
private fun UnitLevelContent(
    course: ContentCourseRow,
    archiveFilter: ArchiveFilter,
    onBackToCourses: () -> Unit,
    onOpenUnit: (String) -> Unit,
    onRequestUnitReset: (String) -> Unit,
    onToggleUnitArchive: (String, Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CompactContextHeader(
                eyebrow = "Curso",
                title = course.title,
                subtitle = "${course.units.size} unidades visibles",
                backLabel = "Volver a cursos",
                onBack = onBackToCourses,
            )
        }
        items(course.units, key = ContentUnitRow::unitId) { unit ->
            UnitCard(
                unit = unit,
                archiveFilter = archiveFilter,
                onOpenUnit = onOpenUnit,
                onRequestUnitReset = onRequestUnitReset,
                onToggleUnitArchive = onToggleUnitArchive,
            )
        }
    }
}

@Composable
private fun QuestionLevelContent(
    course: ContentCourseRow,
    unit: ContentUnitRow,
    archiveFilter: ArchiveFilter,
    onBackToUnits: () -> Unit,
    onRequestUnitReset: (String) -> Unit,
    onToggleUnitArchive: (String, Boolean) -> Unit,
    onToggleItemArchive: (String, Boolean) -> Unit,
    onOpenItemEdit: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CompactContextHeader(
                eyebrow = course.title,
                title = unit.title,
                subtitle = "${unit.questionCount} tarjetas visibles",
                backLabel = "Volver a unidades",
                onBack = onBackToUnits,
                menuContent = {
                    UnitContextMenu(
                        archiveFilter = archiveFilter,
                        onRequestUnitReset = { onRequestUnitReset(unit.unitId) },
                        onToggleUnitArchive = { onToggleUnitArchive(unit.unitId, archiveFilter == ArchiveFilter.ACTIVE_ONLY) },
                    )
                },
            )
        }
        if (unit.items.isEmpty()) {
            item {
                LevelPlaceholder(
                    text = if (archiveFilter == ArchiveFilter.ACTIVE_ONLY) "Sin tarjetas visibles" else "Sin tarjetas archivadas",
                )
            }
        } else {
            items(unit.items, key = ContentItemRow::itemId) { item ->
                QuestionCard(
                    item = item,
                    archiveFilter = archiveFilter,
                    onToggleItemArchive = onToggleItemArchive,
                    onOpenItemEdit = onOpenItemEdit,
                )
            }
        }
    }
}

@Composable
private fun CourseCard(
    course: ContentCourseRow,
    archiveFilter: ArchiveFilter,
    onOpenCourse: (String) -> Unit,
    onRequestCourseReset: (String) -> Unit,
    onToggleCourseArchive: (String, Boolean) -> Unit,
) {
    var menuExpanded by rememberSaveable(course.courseId) { mutableStateOf(false) }

    StudySurfaceCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onOpenCourse(course.courseId) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = course.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${course.units.size} unidades",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Acciones del curso",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(if (archiveFilter == ArchiveFilter.ACTIVE_ONLY) "Archivar curso" else "Restaurar curso") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (archiveFilter == ArchiveFilter.ACTIVE_ONLY) Icons.Rounded.Archive else Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onToggleCourseArchive(course.courseId, archiveFilter == ArchiveFilter.ACTIVE_ONLY)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Reiniciar curso") },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.RestartAlt, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRequestCourseReset(course.courseId)
                        },
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UnitCard(
    unit: ContentUnitRow,
    archiveFilter: ArchiveFilter,
    onOpenUnit: (String) -> Unit,
    onRequestUnitReset: (String) -> Unit,
    onToggleUnitArchive: (String, Boolean) -> Unit,
) {
    var menuExpanded by rememberSaveable(unit.unitId) { mutableStateOf(false) }

    StudySurfaceCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onOpenUnit(unit.unitId) },
        emphasized = true,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = unit.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${unit.questionCount} tarjetas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Acciones de la unidad",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(if (archiveFilter == ArchiveFilter.ACTIVE_ONLY) "Archivar unidad" else "Restaurar unidad") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (archiveFilter == ArchiveFilter.ACTIVE_ONLY) Icons.Rounded.Archive else Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onToggleUnitArchive(unit.unitId, archiveFilter == ArchiveFilter.ACTIVE_ONLY)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Reiniciar unidad") },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.RestartAlt, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRequestUnitReset(unit.unitId)
                        },
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompactContextHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    backLabel: String,
    onBack: () -> Unit,
    menuContent: (@Composable () -> Unit)? = null,
) {
    StudySurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(backLabel)
                }
            }
            menuContent?.invoke()
        }
    }
}

@Composable
private fun UnitContextMenu(
    archiveFilter: ArchiveFilter,
    onRequestUnitReset: () -> Unit,
    onToggleUnitArchive: () -> Unit,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Acciones de la unidad",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(if (archiveFilter == ArchiveFilter.ACTIVE_ONLY) "Archivar unidad" else "Restaurar unidad") },
                leadingIcon = {
                    Icon(
                        imageVector = if (archiveFilter == ArchiveFilter.ACTIVE_ONLY) Icons.Rounded.Archive else Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = null,
                    )
                },
                onClick = {
                    menuExpanded = false
                    onToggleUnitArchive()
                },
            )
            DropdownMenuItem(
                text = { Text("Reiniciar unidad") },
                leadingIcon = { Icon(imageVector = Icons.Rounded.RestartAlt, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onRequestUnitReset()
                },
            )
        }
    }
}

@Composable
private fun QuestionCard(
    item: ContentItemRow,
    archiveFilter: ArchiveFilter,
    onToggleItemArchive: (String, Boolean) -> Unit,
    onOpenItemEdit: (String) -> Unit,
) {
    var menuExpanded by rememberSaveable(item.itemId) { mutableStateOf(false) }

    StudySurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        emphasized = true,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Pregunta",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Acciones de la tarjeta",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Editar tarjeta") },
                            leadingIcon = { Icon(imageVector = Icons.Rounded.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenItemEdit(item.itemId)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (archiveFilter == ArchiveFilter.ACTIVE_ONLY) "Archivar tarjeta" else "Restaurar tarjeta") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (archiveFilter == ArchiveFilter.ACTIVE_ONLY) Icons.Rounded.Archive else Icons.AutoMirrored.Rounded.Undo,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleItemArchive(item.itemId, archiveFilter == ArchiveFilter.ACTIVE_ONLY)
                            },
                        )
                    }
                }
            }
            Text(
                text = item.stem,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Respuesta",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = item.correctAnswer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (item.editedManual) {
                StudyStatusBadge(
                    text = "Editada manualmente",
                    tone = StudyBadgeTone.Accent,
                )
            }
        }
    }
}

@Composable
private fun LevelPlaceholder(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ContentSegmentButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface
        }
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Surface(
        modifier =
            modifier
                .heightIn(min = 52.dp)
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
            )
        }
    }
}
