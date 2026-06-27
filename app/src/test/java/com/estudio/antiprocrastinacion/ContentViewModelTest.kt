package com.estudio.antiprocrastinacion

import android.net.Uri
import android.net.TestUri
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
import com.estudio.antiprocrastinacion.app.model.content.ContentOrigin
import com.estudio.antiprocrastinacion.app.model.content.ContentTree
import com.estudio.antiprocrastinacion.app.model.content.Course
import com.estudio.antiprocrastinacion.app.model.content.CourseWithUnits
import com.estudio.antiprocrastinacion.app.model.content.FacetType
import com.estudio.antiprocrastinacion.app.model.content.Item
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemOption
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.ManualItemEdit
import com.estudio.antiprocrastinacion.app.model.content.Node
import com.estudio.antiprocrastinacion.app.model.content.NodeDetail
import com.estudio.antiprocrastinacion.app.model.content.NodeType
import com.estudio.antiprocrastinacion.app.model.content.Outcome
import com.estudio.antiprocrastinacion.app.model.content.OutcomeWithNodes
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.content.UnitModel
import com.estudio.antiprocrastinacion.app.model.content.UnitWithOutcomes
import com.estudio.antiprocrastinacion.app.model.json.ContentPackageDto
import com.estudio.antiprocrastinacion.app.model.json.CourseDto
import com.estudio.antiprocrastinacion.app.model.json.ItemDto
import com.estudio.antiprocrastinacion.app.model.json.NodeDto
import com.estudio.antiprocrastinacion.app.model.json.OutcomeDto
import com.estudio.antiprocrastinacion.app.model.json.UnitDto
import com.estudio.antiprocrastinacion.app.model.state.NodeFormatStat
import com.estudio.antiprocrastinacion.app.model.state.NodeState
import com.estudio.antiprocrastinacion.app.notification.CognitiveNotificationScheduler
import com.estudio.antiprocrastinacion.app.ui.content.ArchiveFilter
import com.estudio.antiprocrastinacion.app.ui.content.ContentViewModel
import com.estudio.antiprocrastinacion.app.ui.content.buildImportPreview
import com.estudio.antiprocrastinacion.app.ui.content.buildVisibleCourses
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContentViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `active filter exposes only active courses and visible units`() = runTest(dispatcher) {
        val viewModel =
            ContentViewModel(
                contentRepository = FakeContentRepositoryForUi(sampleContentTree()),
                importPreparationService = FakeImportPreparationService(sampleImportPackage()),
                progressRepository = FakeProgressRepositoryForUi(),
                snapshotRepository = FakeSnapshotRepositoryForUi(),
                cognitiveNotificationScheduler = FakeCognitiveNotificationScheduler(),
            )

        advanceUntilIdle()

        val courses = viewModel.uiState.value.visibleCourses
        assertThat(viewModel.uiState.value.archiveFilter).isEqualTo(ArchiveFilter.ACTIVE_ONLY)
        assertThat(courses.map { it.title }).containsExactly("Ethereum")
        assertThat(courses.first().units.map { it.title }).containsExactly("Consensus")
    }

    @Test
    fun `archived filter exposes archived units and nodes`() = runTest(dispatcher) {
        val viewModel =
            ContentViewModel(
                contentRepository = FakeContentRepositoryForUi(sampleContentTree()),
                importPreparationService = FakeImportPreparationService(sampleImportPackage()),
                progressRepository = FakeProgressRepositoryForUi(),
                snapshotRepository = FakeSnapshotRepositoryForUi(),
                cognitiveNotificationScheduler = FakeCognitiveNotificationScheduler(),
            )

        advanceUntilIdle()
        viewModel.updateArchiveFilter(ArchiveFilter.ARCHIVED_ONLY)
        advanceUntilIdle()

        val courses = viewModel.uiState.value.visibleCourses
        assertThat(courses.map { it.title }).containsExactly("Sistemas Operativos")
        assertThat(courses.first().units.map { it.title }).containsExactly("Memoria virtual")
    }

    @Test
    fun `visible content flattens each unit into question cards without exposing internal layers`() {
        val visibleCourses =
            buildVisibleCourses(
                tree = sampleContentTree(),
                nodeDetailsById = sampleNodeDetailsById(),
                archiveFilter = ArchiveFilter.ACTIVE_ONLY,
            )

        val unit = visibleCourses.single().units.single()
        assertThat(unit.questionCount).isEqualTo(2)
        assertThat(unit.items.map { it.stem }).containsExactly("Checkpoint", "Finality").inOrder()
        assertThat(unit.items.map { it.sourceTitle }).containsExactly("Checkpoint", "Finality").inOrder()
    }

    @Test
    fun `course archive applies to every node in the course`() = runTest(dispatcher) {
        val repository = FakeContentRepositoryForUi(sampleContentTree())
        val viewModel =
            ContentViewModel(
                contentRepository = repository,
                importPreparationService = FakeImportPreparationService(sampleImportPackage()),
                progressRepository = FakeProgressRepositoryForUi(),
                snapshotRepository = FakeSnapshotRepositoryForUi(),
                cognitiveNotificationScheduler = FakeCognitiveNotificationScheduler(),
            )

        advanceUntilIdle()
        viewModel.toggleCourseArchive("course-1", true)
        advanceUntilIdle()

        assertThat(repository.archiveOperations)
            .containsExactly(
                "eth-node-1:true",
                "eth-node-2:true",
            )
    }

    @Test
    fun `unit reset resets every node in the unit`() = runTest(dispatcher) {
        val progressRepository = FakeProgressRepositoryForUi()
        val viewModel =
            ContentViewModel(
                contentRepository = FakeContentRepositoryForUi(sampleContentTree()),
                importPreparationService = FakeImportPreparationService(sampleImportPackage()),
                progressRepository = progressRepository,
                snapshotRepository = FakeSnapshotRepositoryForUi(),
                cognitiveNotificationScheduler = FakeCognitiveNotificationScheduler(),
            )

        advanceUntilIdle()
        viewModel.requestUnitReset("unit-1")
        viewModel.confirmReset()
        advanceUntilIdle()

        assertThat(progressRepository.resetCalls).containsExactly("eth-node-1", "eth-node-2")
    }

    @Test
    fun `item archive delegates to repository and refreshes edited state`() = runTest(dispatcher) {
        val repository = FakeContentRepositoryForUi(sampleContentTree())
        val viewModel =
            ContentViewModel(
                contentRepository = repository,
                importPreparationService = FakeImportPreparationService(sampleImportPackage()),
                progressRepository = FakeProgressRepositoryForUi(),
                snapshotRepository = FakeSnapshotRepositoryForUi(),
                cognitiveNotificationScheduler = FakeCognitiveNotificationScheduler(),
            )

        advanceUntilIdle()
        viewModel.toggleItemArchive("eth-item-1", true)
        advanceUntilIdle()

        assertThat(repository.itemArchiveOperations).containsExactly("eth-item-1:true")
        assertThat(repository.getItemBlocking("eth-item-1")?.archivedManual).isTrue()
    }

    @Test
    fun `manual item edit preserves identity and delegates override update`() = runTest(dispatcher) {
        val repository = FakeContentRepositoryForUi(sampleContentTree())
        val viewModel =
            ContentViewModel(
                contentRepository = repository,
                importPreparationService = FakeImportPreparationService(sampleImportPackage()),
                progressRepository = FakeProgressRepositoryForUi(),
                snapshotRepository = FakeSnapshotRepositoryForUi(),
                cognitiveNotificationScheduler = FakeCognitiveNotificationScheduler(),
            )

        advanceUntilIdle()
        viewModel.openItemEdit("eth-item-1")
        advanceUntilIdle()
        viewModel.updatePendingItemEditStem("Checkpoint editado")
        viewModel.updatePendingItemEditCorrectAnswer("Respuesta nueva")
        viewModel.updatePendingItemEditOptionsText("* Respuesta nueva\nDistractor")
        viewModel.confirmItemEdit()
        advanceUntilIdle()

        assertThat(repository.manualEdits.single().itemId).isEqualTo("eth-item-1")
        assertThat(repository.getItemBlocking("eth-item-1")?.stem).isEqualTo("Checkpoint editado")
        assertThat(repository.getItemBlocking("eth-item-1")?.correctAnswer).isEqualTo("Respuesta nueva")
    }

    @Test
    fun `manual import preview uses preparation service and imports compiled json`() = runTest(dispatcher) {
        val repository = FakeContentRepositoryForUi(sampleContentTree())
        val compiledJson = """{"packageId":"compiled"}"""
        val viewModel =
            ContentViewModel(
                contentRepository = repository,
                importPreparationService =
                    FakeImportPreparationService(
                        contentPackage = sampleImportPackage(),
                        kind = PreparedImportKind.AUTHORING_DRAFT,
                        contentPackageJson = compiledJson,
                    ),
                progressRepository = FakeProgressRepositoryForUi(),
                snapshotRepository = FakeSnapshotRepositoryForUi(),
                cognitiveNotificationScheduler = FakeCognitiveNotificationScheduler(),
            )

        advanceUntilIdle()
        viewModel.updateManualImportText("""{"packageKey":"draft"}""")
        viewModel.requestManualImportConfirmation()
        advanceUntilIdle()

        val previewState = viewModel.uiState.value
        assertThat(previewState.isManualImportConfirmOpen).isTrue()
        assertThat(previewState.manualImportKind).isEqualTo(PreparedImportKind.AUTHORING_DRAFT)
        assertThat(previewState.manualImportPreview?.packageId).isEqualTo("ethereum-large")
        assertThat(previewState.preparedManualImportJson).isEqualTo(compiledJson)

        viewModel.confirmManualImport()
        advanceUntilIdle()

        assertThat(repository.importedRawJsons).containsExactly(compiledJson)
    }

    @Test
    fun `file import uses preview before importing prepared json`() = runTest(dispatcher) {
        val repository = FakeContentRepositoryForUi(sampleContentTree())
        val viewModel =
            ContentViewModel(
                contentRepository = repository,
                importPreparationService = FakeImportPreparationService(sampleImportPackage()),
                progressRepository = FakeProgressRepositoryForUi(),
                snapshotRepository = FakeSnapshotRepositoryForUi(),
                cognitiveNotificationScheduler = FakeCognitiveNotificationScheduler(),
            )

        advanceUntilIdle()
        viewModel.importUri(TestUri("content://import/package.json"))
        advanceUntilIdle()

        val previewState = viewModel.uiState.value
        assertThat(previewState.isManualImportConfirmOpen).isTrue()
        assertThat(previewState.manualImportPreview?.packageId).isEqualTo("ethereum-large")
        assertThat(repository.importedRawJsons).isEmpty()

        viewModel.confirmManualImport()
        advanceUntilIdle()

        assertThat(repository.importedRawJsons).containsExactly("""{"packageId":"ethereum-large"}""")
    }

    @Test
    fun `file import preserves additive profile from preview into confirmation`() = runTest(dispatcher) {
        val repository =
            FakeContentRepositoryForUi(
                tree = sampleContentTree(),
                preparedValidationProfile = ImportValidationProfile.ADDITIVE_CONTENT_PACKAGE,
            )
        val viewModel =
            ContentViewModel(
                contentRepository = repository,
                importPreparationService = FakeImportPreparationService(sampleImportPackage()),
                progressRepository = FakeProgressRepositoryForUi(),
                snapshotRepository = FakeSnapshotRepositoryForUi(),
                cognitiveNotificationScheduler = FakeCognitiveNotificationScheduler(),
            )

        advanceUntilIdle()
        viewModel.importUri(TestUri("content://import/additive-package.json"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.manualImportPreview?.potentialArchivedNodeCount).isEqualTo(0)

        viewModel.confirmManualImport()
        advanceUntilIdle()

        assertThat(repository.importedValidationProfiles).containsExactly(ImportValidationProfile.ADDITIVE_CONTENT_PACKAGE)
    }

    @Test
    fun `build import preview separates existing ids from new ids`() {
        val preview = buildImportPreview(sampleImportPackage(), sampleContentTree())

        assertThat(preview.packageId).isEqualTo("ethereum-large")
        assertThat(preview.originLabel).isEqualTo("IMPORTED")
        assertThat(preview.courseCount).isEqualTo(2)
        assertThat(preview.unitCount).isEqualTo(2)
        assertThat(preview.outcomeCount).isEqualTo(2)
        assertThat(preview.nodeCount).isEqualTo(2)
        assertThat(preview.itemCount).isEqualTo(1)
        assertThat(preview.existingCourseMatches).isEqualTo(1)
        assertThat(preview.existingUnitMatches).isEqualTo(1)
        assertThat(preview.existingOutcomeMatches).isEqualTo(1)
        assertThat(preview.existingNodeMatches).isEqualTo(1)
        assertThat(preview.newCourseCount).isEqualTo(1)
        assertThat(preview.newUnitCount).isEqualTo(1)
        assertThat(preview.newOutcomeCount).isEqualTo(1)
        assertThat(preview.newNodeCount).isEqualTo(1)
        assertThat(preview.affectedCourseCount).isEqualTo(2)
        assertThat(preview.potentialArchivedNodeCount).isEqualTo(1)
    }
}

private class FakeContentRepositoryForUi(
    private val tree: ContentTree,
    private val preparedValidationProfile: ImportValidationProfile = ImportValidationProfile.PEDAGOGICAL_NODE,
) : ContentRepository {
    val archiveOperations = mutableListOf<String>()
    val itemArchiveOperations = mutableListOf<String>()
    val manualEdits = mutableListOf<ManualItemEdit>()
    val importedRawJsons = mutableListOf<String>()
    val importedValidationProfiles = mutableListOf<ImportValidationProfile>()
    private val nodes =
        tree.courses.flatMap { it.units }.flatMap { it.outcomes }.flatMap { it.nodes }.associateBy { it.nodeId }
    private val items = sampleItemsForUi().associateBy { it.itemId }.toMutableMap()

    override suspend fun seedDemoIfNeeded() = Unit
    override suspend fun importContentPackage(
        rawJson: String,
        sourceLabel: String,
        validationProfile: ImportValidationProfile,
    ): ImportExecutionResult {
        importedRawJsons += rawJson
        importedValidationProfiles += validationProfile
        return ImportExecutionResult("pkg", true, ValidationReport(emptyList(), emptyList()))
    }

    override suspend fun importContentPackage(uri: Uri) =
        ImportExecutionResult(uri.toString(), false, ValidationReport(emptyList(), emptyList()))

    override suspend fun prepareContentPackage(uri: Uri): ImportPreparationResult =
        ImportPreparationResult(
            sourceLabel = "file:test",
            kind = PreparedImportKind.CONTENT_PACKAGE,
            packageId = sampleImportPackage().packageId,
            schemaVersion = sampleImportPackage().schemaVersion,
            contentPackage = sampleImportPackage(),
            contentPackageJson = """{"packageId":"${sampleImportPackage().packageId}"}""",
            report = ValidationReport(emptyList(), emptyList()),
            validationProfile = preparedValidationProfile,
        )

    override suspend fun getContentTree(): ContentTree = tree
    override suspend fun getUnits(): List<UnitModel> = tree.courses.flatMap { it.units }.map { it.unit }
    override suspend fun getSchedulableNodes(): List<Node> = emptyList()
    override suspend fun getSchedulableNodesByUnit(unitId: String): List<Node> = emptyList()
    override suspend fun getNode(nodeId: String): Node? = nodes[nodeId]
    override suspend fun getNodeDetail(nodeId: String, includeArchivedItems: Boolean): NodeDetail? =
        nodes[nodeId]?.let { node ->
            NodeDetail(
                node = node,
                items = items.values.filter { it.nodeId == nodeId && (includeArchivedItems || !it.archivedManual) },
            )
        }
    override suspend fun getItem(itemId: String, includeArchived: Boolean): Item? =
        items[itemId]?.takeIf { includeArchived || !it.archivedManual }
    override suspend fun getItemsForNode(nodeId: String, includeArchived: Boolean): List<Item> =
        items.values.filter { it.nodeId == nodeId && (includeArchived || !it.archivedManual) }
    override suspend fun getItemsForNodes(nodeIds: List<String>, includeArchived: Boolean): List<Item> =
        items.values.filter { it.nodeId in nodeIds && (includeArchived || !it.archivedManual) }
    override suspend fun setArchivedCandidate(nodeId: String, archived: Boolean) {
        archiveOperations += "$nodeId:$archived"
    }
    override suspend fun setItemArchived(itemId: String, archived: Boolean) {
        itemArchiveOperations += "$itemId:$archived"
        items[itemId]?.let { current ->
            items[itemId] = current.copy(archivedManual = archived)
        }
    }
    override suspend fun updateItemManualEdit(edit: ManualItemEdit) {
        manualEdits += edit
        items[edit.itemId]?.let { current ->
            items[edit.itemId] =
                current.copy(
                    stem = edit.stem,
                    correctAnswer = edit.correctAnswer,
                    options = edit.options ?: current.options,
                    editedManual = true,
                )
        }
    }

    override suspend fun hasRealImportedContent(): Boolean = true
    override suspend fun getContentPackage(rawJson: String): ContentPackageDto {
        throw UnsupportedOperationException()
    }

    fun getItemBlocking(itemId: String): Item? = items[itemId]
}

private class FakeImportPreparationService(
    private val contentPackage: ContentPackageDto,
    private val kind: PreparedImportKind = PreparedImportKind.CONTENT_PACKAGE,
    private val contentPackageJson: String? = null,
    private val validationProfile: ImportValidationProfile = ImportValidationProfile.PEDAGOGICAL_NODE,
) : ImportPreparationService {
    override suspend fun prepare(
        rawJson: String,
        sourceLabel: String,
        validationProfile: ImportValidationProfile,
    ): ImportPreparationResult =
        ImportPreparationResult(
            sourceLabel = sourceLabel,
            kind = kind,
            packageId = contentPackage.packageId,
            schemaVersion = contentPackage.schemaVersion,
            contentPackage = contentPackage,
            contentPackageJson = contentPackageJson ?: rawJson,
            report = ValidationReport(emptyList(), emptyList()),
            validationProfile = validationProfile,
        )
}

private class FakeProgressRepositoryForUi : ProgressRepository {
    val resetCalls = mutableListOf<String>()

    override suspend fun getNodeState(nodeId: String): NodeState? = null
    override suspend fun getAllNodeStates(): List<NodeState> = emptyList()
    override suspend fun getAllFormatStats(): List<NodeFormatStat> = emptyList()
    override suspend fun upsertNodeState(nodeState: NodeState) = Unit
    override suspend fun getFormatStats(nodeId: String): List<NodeFormatStat> = emptyList()
    override suspend fun upsertFormatStats(stats: List<NodeFormatStat>) = Unit
    override suspend fun clearAllProgress() = Unit
    override suspend fun resetNodeProgress(nodeId: String) {
        resetCalls += nodeId
    }
}

private class FakeSnapshotRepositoryForUi : SnapshotRepository {
    override suspend fun exportSyncSnapshot() = throw UnsupportedOperationException()

    override suspend fun exportSyncSnapshot(uri: Uri) =
        SnapshotExecutionResult(uri.toString(), true, "ok", 0, 0, 0, 0, false)

    override suspend fun importSyncSnapshot(rawJson: String, sourceLabel: String) =
        SnapshotExecutionResult(sourceLabel, true, "ok", 0, 0, 0, 0, false)

    override suspend fun importSyncSnapshot(uri: Uri) =
        SnapshotExecutionResult(uri.toString(), true, "ok", 0, 0, 0, 0, false)

    override suspend fun exportUserStatePackage() = throw UnsupportedOperationException()

    override suspend fun exportUserStatePackage(uri: Uri) =
        SnapshotExecutionResult(uri.toString(), true, "user-state-exported", 0, 0, 0, 0, false)

    override suspend fun importUserStatePackage(rawJson: String, sourceLabel: String) =
        SnapshotExecutionResult(sourceLabel, true, "user-state-imported", 0, 0, 0, 0, false)

    override suspend fun importUserStatePackage(uri: Uri) =
        SnapshotExecutionResult(uri.toString(), true, "user-state-imported", 0, 0, 0, 0, false)
}

private class FakeCognitiveNotificationScheduler : CognitiveNotificationScheduler {
    override suspend fun refreshSchedule() = Unit
    override suspend fun handleScheduledTrigger(slotIndex: Int): Boolean = false
}

private fun sampleContentTree(): ContentTree =
    ContentTree(
        courses =
            listOf(
                CourseWithUnits(
                    course = Course(courseId = "course-1", title = "Ethereum", description = null, version = 1, updatedAt = 1L),
                    units =
                        listOf(
                            UnitWithOutcomes(
                                unit = UnitModel("unit-1", "course-1", "Consensus", null, 1, 1, 1L),
                                outcomes =
                                    listOf(
                                        OutcomeWithNodes(
                                            outcome = Outcome("outcome-1", "unit-1", "Checkpoints", null, 1, 1L),
                                            nodes =
                                                listOf(
                                                    node(
                                                        nodeId = "eth-node-1",
                                                        courseId = "course-1",
                                                        unitId = "unit-1",
                                                        title = "Checkpoint",
                                                        coreClaim = "Checkpoint anchors epoch progress",
                                                        archived = false,
                                                    ),
                                                    node(
                                                        nodeId = "eth-node-2",
                                                        courseId = "course-1",
                                                        unitId = "unit-1",
                                                        title = "Finality",
                                                        coreClaim = "Finality confirms stronger guarantees",
                                                        archived = false,
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                        ),
                ),
                CourseWithUnits(
                    course = Course(courseId = "course-2", title = "Sistemas Operativos", description = null, version = 1, updatedAt = 1L),
                    units =
                        listOf(
                            UnitWithOutcomes(
                                unit = UnitModel("unit-2", "course-2", "Memoria virtual", null, 1, 1, 1L),
                                outcomes =
                                    listOf(
                                        OutcomeWithNodes(
                                            outcome = Outcome("outcome-2", "unit-2", "TLB", null, 1, 1L),
                                            nodes =
                                                listOf(
                                                    node(
                                                        nodeId = "os-node-1",
                                                        courseId = "course-2",
                                                        unitId = "unit-2",
                                                        title = "TLB",
                                                        coreClaim = "TLB caches translations",
                                                        archived = true,
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                        ),
                ),
            ),
    )

private fun sampleImportPackage(): ContentPackageDto =
    ContentPackageDto(
        packageId = "ethereum-large",
        schemaVersion = 1,
        generatedAt = 1L,
        contentHash = "hash",
        origin = ContentOrigin.IMPORTED,
        courses =
            listOf(
                CourseDto("course-1", "Ethereum", null, 1, 1L),
                CourseDto("course-3", "Memory", null, 1, 1L),
            ),
        units =
            listOf(
                UnitDto("unit-1", "course-1", "Consensus", null, 1, 1, 1L),
                UnitDto("unit-3", "course-3", "Paging", null, 1, 1, 1L),
            ),
        outcomes =
            listOf(
                OutcomeDto("outcome-1", "unit-1", "Checkpoints", null, 1, 1L),
                OutcomeDto("outcome-3", "unit-3", "TLB", null, 1, 1L),
            ),
        nodes =
            listOf(
                NodeDto(
                    nodeId = "eth-node-1",
                    courseId = "course-1",
                    unitId = "unit-1",
                    outcomeIds = listOf("outcome-1"),
                    title = "Checkpoint",
                    coreClaim = "Checkpoint anchors epoch progress",
                    type = NodeType.CONCEPT,
                    weightExam = 0.8,
                    prerequisites = emptyList(),
                    facets = listOf(FacetType.DEFINICION_FUNCIONAL),
                    mustKnow = listOf("must"),
                    commonErrors = listOf("error"),
                    minimumMasteryDefinition = "mastery",
                    surfaceEasyReady = true,
                    surfaceEasyItemCount = 4,
                    sourceRefs = listOf("manual"),
                    version = 1,
                    updatedAt = 1L,
                ),
                NodeDto(
                    nodeId = "node-new",
                    courseId = "course-3",
                    unitId = "unit-3",
                    outcomeIds = listOf("outcome-3"),
                    title = "Role of TLB",
                    coreClaim = "TLB caches translations",
                    type = NodeType.CONCEPT,
                    weightExam = 0.8,
                    prerequisites = emptyList(),
                    facets = listOf(FacetType.DEFINICION_FUNCIONAL),
                    mustKnow = listOf("must"),
                    commonErrors = listOf("error"),
                    minimumMasteryDefinition = "mastery",
                    surfaceEasyReady = true,
                    surfaceEasyItemCount = 4,
                    sourceRefs = listOf("manual"),
                    version = 1,
                    updatedAt = 1L,
                ),
            ),
        items =
            listOf(
                ItemDto(
                    itemId = "item-1",
                    nodeId = "node-new",
                    facet = FacetType.DEFINICION_FUNCIONAL,
                    format = com.estudio.antiprocrastinacion.app.model.content.ItemFormat.MULTIPLE_CHOICE,
                    frictionLevel = 1,
                    difficultySeed = 0.2,
                    itemRole = com.estudio.antiprocrastinacion.app.model.content.ItemRole.CORE,
                    allowedSurfaces = listOf(Surface.IN_APP_QUICK),
                    cooldownHours = 12.0,
                    stem = "What is TLB?",
                    correctAnswer = "Cache",
                    feedbackShort = "TLB caches translations",
                    coversMustKnow = listOf("must"),
                    version = 1,
                    updatedAt = 1L,
                    sourceRefs = listOf("manual"),
                ),
            ),
    )

private fun sampleItemsForUi(): List<Item> =
    listOf(
        uiItem("eth-item-1", "eth-node-1", "Checkpoint"),
        uiItem("eth-item-2", "eth-node-2", "Finality"),
        uiItem("os-item-1", "os-node-1", "TLB"),
    )

private fun sampleNodeDetailsById(): Map<String, NodeDetail> {
    val tree = sampleContentTree()
    val itemsByNodeId = sampleItemsForUi().groupBy(Item::nodeId)
    return tree.courses
        .flatMap { it.units }
        .flatMap { it.outcomes }
        .flatMap { it.nodes }
        .associateWith { node ->
            NodeDetail(
                node = node,
                items = itemsByNodeId[node.nodeId].orEmpty(),
            )
        }
        .mapKeys { it.key.nodeId }
}

private fun uiItem(itemId: String, nodeId: String, stem: String): Item =
    Item(
        itemId = itemId,
        nodeId = nodeId,
        facet = FacetType.DEFINICION_FUNCIONAL,
        format = ItemFormat.MULTIPLE_CHOICE,
        frictionLevel = 1,
        difficultySeed = 0.2,
        itemRole = ItemRole.CORE,
        allowedSurfaces = listOf(Surface.IN_APP_QUICK, Surface.IN_APP_DEEP),
        cooldownHours = 1.0,
        stem = stem,
        correctAnswer = "Correcta",
        feedbackShort = "fb",
        coversMustKnow = listOf("mk"),
        variantGroupId = null,
        rescueGroupId = null,
        nodeComplexity = null,
        facetComplexity = null,
        distractorSimilarity = null,
        prerequisiteDepth = null,
        targetsErrorIds = emptyList(),
        commonErrorSignals = emptyList(),
        options = listOf(ItemOption("a", "Correcta", true), ItemOption("b", "Distractor", false)),
        version = 1,
        updatedAt = 1L,
        sourceRefs = emptyList(),
        contentOrigin = ContentOrigin.IMPORTED,
    )

private fun node(
    nodeId: String,
    courseId: String,
    unitId: String,
    title: String,
    coreClaim: String,
    archived: Boolean,
): Node =
    Node(
        nodeId = nodeId,
        courseId = courseId,
        unitId = unitId,
        outcomeIds = listOf("outcome"),
        title = title,
        coreClaim = coreClaim,
        type = NodeType.CONCEPT,
        weightExam = 0.8,
        prerequisites = emptyList(),
        facets = listOf(FacetType.DEFINICION_FUNCIONAL),
        mustKnow = listOf("must"),
        commonErrors = listOf("error"),
        minimumMasteryDefinition = "mastery",
        surfaceEasyReady = true,
        surfaceEasyItemCount = 4,
        sourceRefs = listOf("manual"),
        version = 1,
        updatedAt = 1L,
        archivedCandidate = archived,
        contentOrigin = ContentOrigin.IMPORTED,
    )
