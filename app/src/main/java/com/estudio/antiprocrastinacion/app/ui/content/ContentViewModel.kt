package com.estudio.antiprocrastinacion.app.ui.content

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estudio.antiprocrastinacion.app.domain.repository.ContentRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ImportExecutionResult
import com.estudio.antiprocrastinacion.app.domain.repository.ImportPreparationResult
import com.estudio.antiprocrastinacion.app.domain.repository.ImportPreparationService
import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidationProfile
import com.estudio.antiprocrastinacion.app.domain.repository.PreparedImportKind
import com.estudio.antiprocrastinacion.app.domain.repository.ProgressRepository
import com.estudio.antiprocrastinacion.app.domain.repository.SnapshotExecutionResult
import com.estudio.antiprocrastinacion.app.domain.repository.SnapshotRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationReport
import com.estudio.antiprocrastinacion.app.model.content.ContentTree
import com.estudio.antiprocrastinacion.app.model.content.Item
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemOption
import com.estudio.antiprocrastinacion.app.model.content.Node
import com.estudio.antiprocrastinacion.app.model.content.NodeDetail
import com.estudio.antiprocrastinacion.app.model.content.ManualItemEdit
import com.estudio.antiprocrastinacion.app.model.json.ContentPackageDto
import com.estudio.antiprocrastinacion.app.notification.CognitiveNotificationScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ArchiveFilter {
    ACTIVE_ONLY,
    ARCHIVED_ONLY,
}

data class ContentCourseRow(
    val courseId: String,
    val title: String,
    val units: List<ContentUnitRow>,
    val nodeIds: List<String>,
)

data class ContentUnitRow(
    val unitId: String,
    val title: String,
    val nodeIds: List<String>,
    val questionCount: Int,
    val items: List<ContentItemRow>,
)

data class ContentItemRow(
    val itemId: String,
    val stem: String,
    val correctAnswer: String,
    val sourceTitle: String,
    val archivedManual: Boolean,
    val editedManual: Boolean,
)

data class PendingItemEdit(
    val itemId: String,
    val nodeTitle: String,
    val stem: String,
    val correctAnswer: String,
    val optionsText: String,
)

data class PendingResetTarget(
    val title: String,
    val nodeIds: List<String>,
)

data class ContentStats(
    val courseCount: Int = 0,
    val unitCount: Int = 0,
    val outcomeCount: Int = 0,
    val nodeCount: Int = 0,
    val archivedNodeCount: Int = 0,
)

data class ImportPreview(
    val packageId: String,
    val originLabel: String,
    val courseCount: Int,
    val unitCount: Int,
    val outcomeCount: Int,
    val nodeCount: Int,
    val itemCount: Int,
    val existingCourseMatches: Int,
    val existingUnitMatches: Int,
    val existingOutcomeMatches: Int,
    val existingNodeMatches: Int,
    val newCourseCount: Int,
    val newUnitCount: Int,
    val newOutcomeCount: Int,
    val newNodeCount: Int,
    val affectedCourseCount: Int,
    val potentialArchivedNodeCount: Int,
    val courseSummaries: List<CourseImportPreview>,
    // Vista previa de cada tarjeta que se importaría, para revisar antes de confirmar (no aprobar a ciegas).
    val questions: List<ImportQuestionPreview> = emptyList(),
)

data class ImportQuestionPreview(
    val stem: String,
    val formatLabel: String,
    val options: List<ImportOptionPreview>,
    val correctAnswer: String,
    val feedback: String,
)

data class ImportOptionPreview(
    val text: String,
    val isCorrect: Boolean,
)

data class CourseImportPreview(
    val courseId: String,
    val title: String,
    val incomingUnitCount: Int,
    val incomingOutcomeCount: Int,
    val incomingNodeCount: Int,
    val existingCourseMatch: Boolean,
    val existingUnitMatches: Int,
    val existingOutcomeMatches: Int,
    val existingNodeMatches: Int,
    val newUnitCount: Int,
    val newOutcomeCount: Int,
    val newNodeCount: Int,
    val potentialArchivedNodeCount: Int,
)

data class ContentUiState(
    val isLoading: Boolean = true,
    val contentTree: ContentTree? = null,
    val visibleContentTree: ContentTree? = null,
    val nodeDetailsById: Map<String, NodeDetail> = emptyMap(),
    val visibleCourses: List<ContentCourseRow> = emptyList(),
    val stats: ContentStats = ContentStats(),
    val lastImportResult: ImportExecutionResult? = null,
    val lastSnapshotResult: SnapshotExecutionResult? = null,
    val manualImportPreview: ImportPreview? = null,
    val manualImportKind: PreparedImportKind? = null,
    val manualImportReport: ValidationReport? = null,
    val preparedManualImportJson: String? = null,
    val preparedImportSourceLabel: String? = null,
    val preparedImportValidationProfile: ImportValidationProfile? = null,
    val pendingResetTarget: PendingResetTarget? = null,
    val isManualImportOpen: Boolean = false,
    val isManualImportConfirmOpen: Boolean = false,
    val manualImportText: String = "",
    val searchQuery: String = "",
    val archiveFilter: ArchiveFilter = ArchiveFilter.ACTIVE_ONLY,
    val selectedNodeIds: Set<String> = emptySet(),
    val pendingItemEdit: PendingItemEdit? = null,
)

class ContentViewModel(
    private val contentRepository: ContentRepository,
    private val importPreparationService: ImportPreparationService,
    private val progressRepository: ProgressRepository,
    private val snapshotRepository: SnapshotRepository,
    private val cognitiveNotificationScheduler: CognitiveNotificationScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ContentUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val contentTree = contentRepository.getContentTree()
            val nodeIds =
                contentTree.courses
                    .flatMap { it.units }
                    .flatMap { it.outcomes }
                    .flatMap { it.nodes }
                    .map { it.nodeId }
                    .distinct()
            val nodeDetailsById =
                nodeIds.associateWith { nodeId ->
                    contentRepository.getNodeDetail(nodeId, includeArchivedItems = true)
                }.mapNotNull { (nodeId, detail) ->
                    detail?.let { nodeId to it }
                }.toMap()
            _uiState.value =
                _uiState.value
                    .copy(
                        isLoading = false,
                        contentTree = contentTree,
                        nodeDetailsById = nodeDetailsById,
                    ).withDerivedContent()
        }
    }

    fun importUri(uri: Uri) {
        requestUriImportConfirmation(uri)
    }

    fun requestUriImportConfirmation(uri: Uri) {
        viewModelScope.launch {
            val prepared = contentRepository.prepareContentPackage(uri)
            applyPreparedImportPreview(prepared)
        }
    }

    fun exportSnapshot(uri: Uri) {
        viewModelScope.launch {
            val result = snapshotRepository.exportSyncSnapshot(uri)
            _uiState.value = _uiState.value.copy(lastSnapshotResult = result)
        }
    }

    fun importSnapshot(uri: Uri) {
        viewModelScope.launch {
            val result = snapshotRepository.importSyncSnapshot(uri)
            _uiState.value = _uiState.value.copy(lastSnapshotResult = result)
            if (result.success) {
                cognitiveNotificationScheduler.refreshSchedule()
                refresh()
            }
        }
    }

    fun exportUserStatePackage(uri: Uri) {
        viewModelScope.launch {
            val result = snapshotRepository.exportUserStatePackage(uri)
            _uiState.value = _uiState.value.copy(lastSnapshotResult = result)
        }
    }

    fun importUserStatePackage(uri: Uri) {
        viewModelScope.launch {
            val result = snapshotRepository.importUserStatePackage(uri)
            _uiState.value = _uiState.value.copy(lastSnapshotResult = result)
            if (result.success) {
                cognitiveNotificationScheduler.refreshSchedule()
                refresh()
            }
        }
    }

    fun openManualImport() {
        _uiState.value = _uiState.value.copy(isManualImportOpen = true)
    }

    fun closeManualImport() {
        _uiState.value =
            _uiState.value.copy(
                isManualImportOpen = false,
                isManualImportConfirmOpen = false,
                manualImportPreview = null,
                manualImportKind = null,
                manualImportReport = null,
                preparedManualImportJson = null,
                preparedImportSourceLabel = null,
                preparedImportValidationProfile = null,
            )
    }

    fun updateManualImportText(value: String) {
        _uiState.value =
            _uiState.value.copy(
                manualImportText = value,
                manualImportPreview = null,
                manualImportKind = null,
                manualImportReport = null,
                preparedManualImportJson = null,
                preparedImportSourceLabel = null,
                preparedImportValidationProfile = null,
            )
    }

    fun updateSearchQuery(value: String) {
        _uiState.value = _uiState.value.copy(searchQuery = value).withDerivedContent()
    }

    fun updateArchiveFilter(filter: ArchiveFilter) {
        _uiState.value = _uiState.value.copy(archiveFilter = filter).withDerivedContent()
    }

    fun clearManualImportText() {
        _uiState.value =
            _uiState.value.copy(
                manualImportText = "",
                manualImportPreview = null,
                manualImportKind = null,
                manualImportReport = null,
                preparedManualImportJson = null,
                preparedImportSourceLabel = null,
                preparedImportValidationProfile = null,
            )
    }

    fun requestManualImportConfirmation() {
        val rawJson = _uiState.value.manualImportText.trim()
        if (rawJson.isEmpty()) return
        viewModelScope.launch {
            val prepared = importPreparationService.prepare(rawJson, "manual:text-entry:preview")
            applyPreparedImportPreview(prepared.copy(sourceLabel = "manual:text-entry"))
        }
    }

    private suspend fun applyPreparedImportPreview(prepared: ImportPreparationResult) {
        val parsedPackage = prepared.contentPackage
        if (parsedPackage == null) {
            _uiState.value =
                _uiState.value.copy(
                    lastImportResult =
                        ImportExecutionResult(
                            packageId = prepared.packageId,
                            imported = false,
                            report = prepared.report,
                        ),
                    isManualImportConfirmOpen = false,
                    manualImportPreview = null,
                    manualImportKind = prepared.kind,
                    manualImportReport = prepared.report,
                    preparedManualImportJson = null,
                    preparedImportSourceLabel = null,
                    preparedImportValidationProfile = null,
                )
            return
        }
        val currentTree = _uiState.value.contentTree ?: contentRepository.getContentTree()
        _uiState.value =
            _uiState.value.copy(
                isManualImportConfirmOpen = true,
                manualImportPreview =
                    buildImportPreview(
                        parsedPackage,
                        currentTree,
                        additive = prepared.validationProfile != ImportValidationProfile.PEDAGOGICAL_NODE,
                    ),
                manualImportKind = prepared.kind,
                manualImportReport = prepared.report,
                preparedManualImportJson = prepared.contentPackageJson,
                preparedImportSourceLabel = prepared.sourceLabel,
                preparedImportValidationProfile = prepared.validationProfile,
            )
    }

    fun dismissManualImportConfirmation() {
        _uiState.value = _uiState.value.copy(isManualImportConfirmOpen = false)
    }

    fun confirmManualImport() {
        val current = _uiState.value
        val rawJson = current.preparedManualImportJson ?: current.manualImportText
        val sourceLabel = current.preparedImportSourceLabel ?: "manual:text-entry"
        val validationProfile = current.preparedImportValidationProfile ?: ImportValidationProfile.PEDAGOGICAL_NODE
        if (current.manualImportReport?.canImport == false) return
        viewModelScope.launch {
            val result = contentRepository.importContentPackage(rawJson, sourceLabel, validationProfile)
            _uiState.value =
                _uiState.value.copy(
                    lastImportResult = result,
                    isManualImportConfirmOpen = false,
                    isManualImportOpen = !result.imported,
                    manualImportText = if (result.imported) "" else rawJson,
                    manualImportPreview = null,
                    manualImportKind = null,
                    manualImportReport = null,
                    preparedManualImportJson = null,
                    preparedImportSourceLabel = null,
                    preparedImportValidationProfile = null,
                ).withDerivedContent()
            refresh()
        }
    }

    fun requestCourseReset(courseId: String) {
        val course =
            _uiState.value.contentTree
                ?.courses
                ?.firstOrNull { it.course.courseId == courseId }
                ?: return
        val nodeIds =
            course.units
                .flatMap { it.outcomes }
                .flatMap { it.nodes }
                .map { it.nodeId }
                .distinct()
        if (nodeIds.isEmpty()) return
        _uiState.value =
            _uiState.value.copy(
                pendingResetTarget = PendingResetTarget(title = course.course.title, nodeIds = nodeIds),
            )
    }

    fun requestUnitReset(unitId: String) {
        val unit =
            _uiState.value.contentTree
                ?.courses
                ?.flatMap { it.units }
                ?.firstOrNull { it.unit.unitId == unitId }
                ?: return
        val nodeIds =
            unit.outcomes
                .flatMap { it.nodes }
                .map { it.nodeId }
                .distinct()
        if (nodeIds.isEmpty()) return
        _uiState.value =
            _uiState.value.copy(
                pendingResetTarget = PendingResetTarget(title = unit.unit.title, nodeIds = nodeIds),
            )
    }

    fun confirmReset() {
        val target = _uiState.value.pendingResetTarget ?: return
        viewModelScope.launch {
            target.nodeIds.forEach { nodeId ->
                progressRepository.resetNodeProgress(nodeId)
            }
            _uiState.value = _uiState.value.copy(pendingResetTarget = null)
            refresh()
        }
    }

    fun dismissReset() {
        _uiState.value = _uiState.value.copy(pendingResetTarget = null)
    }

    fun toggleCourseArchive(courseId: String, archived: Boolean) {
        viewModelScope.launch {
            val course =
                _uiState.value.contentTree
                    ?.courses
                    ?.firstOrNull { it.course.courseId == courseId }
                    ?: return@launch
            course.units
                .flatMap { it.outcomes }
                .flatMap { it.nodes }
                .map { it.nodeId }
                .distinct()
                .forEach { nodeId ->
                    contentRepository.setArchivedCandidate(nodeId, archived)
                }
            refresh()
        }
    }

    fun toggleUnitArchive(unitId: String, archived: Boolean) {
        viewModelScope.launch {
            val unit =
                _uiState.value.contentTree
                    ?.courses
                    ?.flatMap { it.units }
                    ?.firstOrNull { it.unit.unitId == unitId }
                    ?: return@launch
            unit.outcomes
                .flatMap { it.nodes }
                .map { it.nodeId }
                .distinct()
                .forEach { nodeId ->
                    contentRepository.setArchivedCandidate(nodeId, archived)
                }
            refresh()
        }
    }

    fun toggleItemArchive(itemId: String, archived: Boolean) {
        viewModelScope.launch {
            contentRepository.setItemArchived(itemId, archived)
            refresh()
        }
    }

    fun openItemEdit(itemId: String) {
        viewModelScope.launch {
            val item = contentRepository.getItem(itemId, includeArchived = true) ?: return@launch
            val nodeTitle = contentRepository.getNode(item.nodeId)?.title.orEmpty()
            _uiState.value =
                _uiState.value.copy(
                    pendingItemEdit =
                        PendingItemEdit(
                            itemId = item.itemId,
                            nodeTitle = nodeTitle,
                            stem = item.stem,
                            correctAnswer = item.correctAnswer,
                            optionsText = item.options.toEditableText(),
                        ),
                )
        }
    }

    fun updatePendingItemEditStem(value: String) {
        val current = _uiState.value.pendingItemEdit ?: return
        _uiState.value = _uiState.value.copy(pendingItemEdit = current.copy(stem = value))
    }

    fun updatePendingItemEditCorrectAnswer(value: String) {
        val current = _uiState.value.pendingItemEdit ?: return
        _uiState.value = _uiState.value.copy(pendingItemEdit = current.copy(correctAnswer = value))
    }

    fun updatePendingItemEditOptionsText(value: String) {
        val current = _uiState.value.pendingItemEdit ?: return
        _uiState.value = _uiState.value.copy(pendingItemEdit = current.copy(optionsText = value))
    }

    fun dismissItemEdit() {
        _uiState.value = _uiState.value.copy(pendingItemEdit = null)
    }

    fun confirmItemEdit() {
        val draft = _uiState.value.pendingItemEdit ?: return
        viewModelScope.launch {
            val item = contentRepository.getItem(draft.itemId, includeArchived = true) ?: return@launch
            val editedOptions = parseEditableOptions(draft.optionsText, item)
            contentRepository.updateItemManualEdit(
                ManualItemEdit(
                    itemId = draft.itemId,
                    stem = draft.stem.trim(),
                    correctAnswer = draft.correctAnswer.trim(),
                    options = editedOptions,
                ),
            )
            _uiState.value = _uiState.value.copy(pendingItemEdit = null)
            refresh()
        }
    }

    fun toggleNodeSelection(nodeId: String) {
        val selected = _uiState.value.selectedNodeIds
        _uiState.value =
            _uiState.value.copy(
                selectedNodeIds =
                    if (nodeId in selected) selected - nodeId else selected + nodeId,
            )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedNodeIds = emptySet())
    }

    fun selectAllVisibleArchivedCandidates() {
        val visibleArchivedIds =
            _uiState.value.visibleContentTree
                ?.courses
                ?.flatMap { it.units }
                ?.flatMap { it.outcomes }
                ?.flatMap { it.nodes }
                ?.filter { it.archivedCandidate }
                ?.map { it.nodeId }
                .orEmpty()
                .toSet()
        _uiState.value = _uiState.value.copy(selectedNodeIds = visibleArchivedIds)
    }

    fun restoreSelectedArchivedCandidates() {
        val selectedIds = _uiState.value.selectedNodeIds.toList()
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            selectedIds.forEach { nodeId ->
                contentRepository.setArchivedCandidate(nodeId, false)
            }
            _uiState.value = _uiState.value.copy(selectedNodeIds = emptySet())
            refresh()
        }
    }
}

internal fun ContentUiState.withDerivedContent(): ContentUiState {
    val tree = contentTree ?: return copy(visibleContentTree = null, visibleCourses = emptyList(), stats = ContentStats())
    val visibleContentTree = filterContentTree(tree, searchQuery, archiveFilter)
    val visibleNodeIds = buildVisibleCourses(tree, nodeDetailsById, archiveFilter).flatMap { it.nodeIds }.toSet()
    return copy(
        selectedNodeIds = selectedNodeIds.filterTo(mutableSetOf()) { it in visibleNodeIds },
        visibleContentTree = visibleContentTree,
        visibleCourses = buildVisibleCourses(tree, nodeDetailsById, archiveFilter),
        stats = computeContentStats(tree),
    )
}

internal fun computeContentStats(tree: ContentTree): ContentStats {
    val units = tree.courses.sumOf { it.units.size }
    val outcomes = tree.courses.sumOf { course -> course.units.sumOf { it.outcomes.size } }
    val nodes = tree.courses.sumOf { course -> course.units.sumOf { unit -> unit.outcomes.sumOf { it.nodes.size } } }
    val archivedNodes =
        tree.courses.sumOf { course ->
            course.units.sumOf { unit ->
                unit.outcomes.sumOf { outcome -> outcome.nodes.count { it.archivedCandidate } }
            }
        }
    return ContentStats(
        courseCount = tree.courses.size,
        unitCount = units,
        outcomeCount = outcomes,
        nodeCount = nodes,
        archivedNodeCount = archivedNodes,
    )
}

internal fun filterContentTree(
    tree: ContentTree,
    rawQuery: String,
    archiveFilter: ArchiveFilter,
): ContentTree {
    val query = rawQuery.normalizedForSearch()
    val filteredCourses =
        tree.courses.mapNotNull { course ->
            val courseMatches = course.course.title.normalizedForSearch().contains(query)
            val filteredUnits =
                course.units.mapNotNull { unit ->
                    val unitMatches = unit.unit.title.normalizedForSearch().contains(query)
                    val filteredOutcomes =
                        unit.outcomes.mapNotNull { outcome ->
                            val outcomeMatches = outcome.outcome.title.normalizedForSearch().contains(query)
                            val filteredNodes =
                                outcome.nodes.filter { node ->
                                    matchesArchiveFilter(node, archiveFilter) && (
                                        query.isBlank() ||
                                            courseMatches ||
                                            unitMatches ||
                                            outcomeMatches ||
                                            node.title.normalizedForSearch().contains(query) ||
                                            node.coreClaim.normalizedForSearch().contains(query) ||
                                            node.nodeId.normalizedForSearch().contains(query)
                                    )
                                }
                            if (filteredNodes.isEmpty()) null else outcome.copy(nodes = filteredNodes)
                        }
                    if (filteredOutcomes.isEmpty()) null else unit.copy(outcomes = filteredOutcomes)
                }
            if (filteredUnits.isEmpty()) null else course.copy(units = filteredUnits)
        }
    return ContentTree(filteredCourses)
}

private fun matchesArchiveFilter(node: Node, archiveFilter: ArchiveFilter): Boolean =
    when (archiveFilter) {
        ArchiveFilter.ACTIVE_ONLY -> !node.archivedCandidate
        ArchiveFilter.ARCHIVED_ONLY -> node.archivedCandidate
    }

internal fun buildVisibleCourses(
    tree: ContentTree,
    nodeDetailsById: Map<String, NodeDetail>,
    archiveFilter: ArchiveFilter,
): List<ContentCourseRow> =
    tree.courses.mapNotNull { course ->
        val visibleUnits =
            course.units.mapNotNull { unit ->
                val visibleNodes =
                    unit.outcomes
                        .flatMap { it.nodes }
                        .mapNotNull { node ->
                            val detail = nodeDetailsById[node.nodeId]
                            val visibleItems =
                                detail
                                    ?.items
                                    .orEmpty()
                                    .filter { item ->
                                        when (archiveFilter) {
                                            ArchiveFilter.ACTIVE_ONLY -> !item.archivedManual
                                            ArchiveFilter.ARCHIVED_ONLY -> item.archivedManual
                                        }
                                    }
                                    .map { item ->
                                        ContentItemRow(
                                            itemId = item.itemId,
                                            stem = item.stem,
                                            correctAnswer = item.correctAnswer,
                                            sourceTitle = node.title,
                                            archivedManual = item.archivedManual,
                                            editedManual = item.editedManual,
                                        )
                                    }
                            val includeNode =
                                when (archiveFilter) {
                                    ArchiveFilter.ACTIVE_ONLY -> !node.archivedCandidate
                                    ArchiveFilter.ARCHIVED_ONLY -> node.archivedCandidate || visibleItems.isNotEmpty()
                                }
                            if (!includeNode) {
                                null
                            } else {
                                VisibleNode(nodeId = node.nodeId, items = visibleItems)
                            }
                        }
                if (visibleNodes.isEmpty()) {
                    null
                } else {
                    ContentUnitRow(
                        unitId = unit.unit.unitId,
                        title = unit.unit.title,
                        nodeIds = visibleNodes.map(VisibleNode::nodeId),
                        questionCount = visibleNodes.sumOf { it.items.size },
                        items = visibleNodes.flatMap(VisibleNode::items),
                    )
                }
            }
        if (visibleUnits.isEmpty()) {
            null
        } else {
            ContentCourseRow(
                courseId = course.course.courseId,
                title = course.course.title,
                units = visibleUnits,
                nodeIds = visibleUnits.flatMap { it.nodeIds }.distinct(),
            )
        }
    }

private data class VisibleNode(
    val nodeId: String,
    val items: List<ContentItemRow>,
)

private fun String.normalizedForSearch(): String =
    trim()
        .lowercase()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")

private fun List<ItemOption>.toEditableText(): String =
    joinToString(separator = "\n") { option ->
        if (option.isCorrect) {
            "* ${option.text}"
        } else {
            option.text
        }
    }

private fun parseEditableOptions(
    rawText: String,
    currentItem: Item,
): List<ItemOption>? {
    val lines =
        rawText
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
    if (lines.isEmpty()) {
        return currentItem.options.takeIf { it.isNotEmpty() }
    }
    return lines.mapIndexed { index, line ->
        val isCorrect = line.startsWith("*")
        ItemOption(
            id = "edited-${index + 1}",
            text = line.removePrefix("*").trim(),
            isCorrect = isCorrect,
        )
    }
}

// Categorías visibles del desglose de import, en el orden en que se muestran (incluso con 0).
internal val importQuestionCategories =
    listOf(
        "Seleccionar la correcta",
        "Elegir la falsa",
        "Verdadero / falso",
        "Ver respuesta",
    )

internal fun importQuestionCategory(format: ItemFormat): String =
    when (format) {
        ItemFormat.MULTIPLE_CHOICE, ItemFormat.MINI_SCENARIO_MCQ -> "Seleccionar la correcta"
        ItemFormat.CHOOSE_FALSE_STATEMENT -> "Elegir la falsa"
        ItemFormat.TRUE_FALSE -> "Verdadero / falso"
        else -> "Ver respuesta"
    }

internal fun buildImportPreview(
    contentPackage: ContentPackageDto,
    currentTree: ContentTree,
    additive: Boolean = false,
): ImportPreview {
    val currentCourseIds = currentTree.courses.map { it.course.courseId }.toSet()
    val currentUnitIds = currentTree.courses.flatMap { it.units }.map { it.unit.unitId }.toSet()
    val currentOutcomeIds =
        currentTree.courses.flatMap { it.units }.flatMap { it.outcomes }.map { it.outcome.outcomeId }.toSet()
    val currentNodeIds =
        currentTree.courses.flatMap { it.units }.flatMap { it.outcomes }.flatMap { it.nodes }.map { it.nodeId }.toSet()
    val currentNodes =
        currentTree.courses
            .flatMap { it.units }
            .flatMap { it.outcomes }
            .flatMap { it.nodes }
    val currentUnitsByCourseId = currentTree.courses.associate { course -> course.course.courseId to course.units.map { it.unit.unitId }.toSet() }
    val currentOutcomesByCourseId =
        currentTree.courses.associate { course ->
            course.course.courseId to course.units.flatMap { it.outcomes }.map { it.outcome.outcomeId }.toSet()
        }
    val currentNodesByCourseId =
        currentTree.courses.associate { course ->
            course.course.courseId to course.units.flatMap { it.outcomes }.flatMap { it.nodes }
        }

    val existingCourseMatches = contentPackage.courses.count { it.courseId in currentCourseIds }
    val existingUnitMatches = contentPackage.units.count { it.unitId in currentUnitIds }
    val existingOutcomeMatches = contentPackage.outcomes.count { it.outcomeId in currentOutcomeIds }
    val existingNodeMatches = contentPackage.nodes.count { it.nodeId in currentNodeIds }
    val incomingNodeIds = contentPackage.nodes.map { it.nodeId }.toSet()
    val affectedCourseIds = contentPackage.courses.map { it.courseId }.toSet()
    val potentialArchivedNodeCount =
        if (additive) {
            0
        } else {
            currentNodes.count { node ->
                node.contentOrigin == contentPackage.origin &&
                    node.courseId in affectedCourseIds &&
                    node.nodeId !in incomingNodeIds &&
                    !node.archivedCandidate
            }
        }
    val courseSummaries =
        contentPackage.courses.map { courseDto ->
            val incomingUnits = contentPackage.units.filter { it.courseId == courseDto.courseId }
            val incomingUnitIds = incomingUnits.map { it.unitId }.toSet()
            val incomingOutcomes =
                contentPackage.outcomes.filter { outcome ->
                    outcome.unitId in incomingUnitIds
                }
            val incomingOutcomeIds = incomingOutcomes.map { it.outcomeId }.toSet()
            val incomingNodes = contentPackage.nodes.filter { it.courseId == courseDto.courseId }
            val currentCourseNodes = currentNodesByCourseId[courseDto.courseId].orEmpty()
            val currentCourseNodeIds = currentCourseNodes.map { it.nodeId }.toSet()
            val existingUnitMatchesForCourse = incomingUnits.count { it.unitId in currentUnitsByCourseId[courseDto.courseId].orEmpty() }
            val existingOutcomeMatchesForCourse =
                incomingOutcomes.count { it.outcomeId in currentOutcomesByCourseId[courseDto.courseId].orEmpty() }
            val existingNodeMatchesForCourse = incomingNodes.count { it.nodeId in currentCourseNodeIds }
            val incomingCourseNodeIds = incomingNodes.map { it.nodeId }.toSet()
            val potentialArchivedForCourse =
                if (additive) {
                    0
                } else {
                    currentCourseNodes.count { node ->
                        node.contentOrigin == contentPackage.origin &&
                            node.nodeId !in incomingCourseNodeIds &&
                            !node.archivedCandidate
                    }
                }

            CourseImportPreview(
                courseId = courseDto.courseId,
                title = courseDto.title,
                incomingUnitCount = incomingUnits.size,
                incomingOutcomeCount = incomingOutcomes.size,
                incomingNodeCount = incomingNodes.size,
                existingCourseMatch = courseDto.courseId in currentCourseIds,
                existingUnitMatches = existingUnitMatchesForCourse,
                existingOutcomeMatches = existingOutcomeMatchesForCourse,
                existingNodeMatches = existingNodeMatchesForCourse,
                newUnitCount = incomingUnits.size - existingUnitMatchesForCourse,
                newOutcomeCount = incomingOutcomes.size - existingOutcomeMatchesForCourse,
                newNodeCount = incomingNodes.size - existingNodeMatchesForCourse,
                potentialArchivedNodeCount = potentialArchivedForCourse,
            )
        }
            .sortedBy { it.title.lowercase() }

    return ImportPreview(
        packageId = contentPackage.packageId,
        originLabel = contentPackage.origin.name,
        courseCount = contentPackage.courses.size,
        unitCount = contentPackage.units.size,
        outcomeCount = contentPackage.outcomes.size,
        nodeCount = contentPackage.nodes.size,
        itemCount = contentPackage.items.size,
        existingCourseMatches = existingCourseMatches,
        existingUnitMatches = existingUnitMatches,
        existingOutcomeMatches = existingOutcomeMatches,
        existingNodeMatches = existingNodeMatches,
        newCourseCount = contentPackage.courses.size - existingCourseMatches,
        newUnitCount = contentPackage.units.size - existingUnitMatches,
        newOutcomeCount = contentPackage.outcomes.size - existingOutcomeMatches,
        newNodeCount = contentPackage.nodes.size - existingNodeMatches,
        affectedCourseCount = affectedCourseIds.size,
        potentialArchivedNodeCount = potentialArchivedNodeCount,
        courseSummaries = courseSummaries,
        questions =
            contentPackage.items.map { item ->
                ImportQuestionPreview(
                    stem = item.stem,
                    formatLabel = importQuestionCategory(item.format),
                    options = item.options.map { ImportOptionPreview(text = it.text, isCorrect = it.isCorrect) },
                    correctAnswer = item.correctAnswer,
                    feedback = item.feedbackShort,
                )
            },
    )
}
