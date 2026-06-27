package com.estudio.antiprocrastinacion

import android.net.Uri
import com.estudio.antiprocrastinacion.app.data.importing.DefaultImportValidator
import com.estudio.antiprocrastinacion.app.data.importing.EditableImportPreparationService
import com.estudio.antiprocrastinacion.app.data.importing.NaturalTextDraftExtractor
import com.estudio.antiprocrastinacion.app.data.importing.ReviewedEditableDraftValidator
import com.estudio.antiprocrastinacion.app.data.importing.ReviewedQuestionBankCompiler
import com.estudio.antiprocrastinacion.app.domain.repository.ContentRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ImportExecutionResult
import com.estudio.antiprocrastinacion.app.domain.repository.ImportPreparationResult
import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidationProfile
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationReport
import com.estudio.antiprocrastinacion.app.model.content.ContentTree
import com.estudio.antiprocrastinacion.app.model.content.Item
import com.estudio.antiprocrastinacion.app.model.content.ItemOverride
import com.estudio.antiprocrastinacion.app.model.content.ManualItemEdit
import com.estudio.antiprocrastinacion.app.model.content.Node
import com.estudio.antiprocrastinacion.app.model.content.NodeDetail
import com.estudio.antiprocrastinacion.app.model.content.UnitModel
import com.estudio.antiprocrastinacion.app.model.json.ContentPackageDto
import com.estudio.antiprocrastinacion.app.ui.common.TimeProvider
import com.estudio.antiprocrastinacion.app.ui.content.EditableImportViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditableImportViewModelTest {
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
    fun `source edits are ignored while analysis is preparing preview`() =
        runTest(dispatcher) {
            val repository = BlockingTreeContentRepository()
            val viewModel = viewModel(repository)
            viewModel.updateSourceText(validSource())

            viewModel.analyzeSource()
            runCurrent()

            assertThat(viewModel.uiState.value.isWorking).isTrue()
            viewModel.updateSourceText("Curso: Otro")

            assertThat(viewModel.uiState.value.sourceText).isEqualTo(validSource())

            repository.completeTree()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isWorking).isFalse()
            assertThat(viewModel.uiState.value.draft?.course?.title).isEqualTo("Ethereum")
        }

    @Test
    fun `draft edits are ignored while edited draft is preparing preview`() =
        runTest(dispatcher) {
            val repository = BlockingTreeContentRepository()
            val viewModel = viewModel(repository)
            viewModel.updateSourceText(validSource())
            viewModel.analyzeSource()
            repository.completeTree()
            advanceUntilIdle()
            val originalStem = viewModel.uiState.value.draft?.units?.single()?.questions?.single()?.stem
            repository.resetBlock()

            viewModel.prepareEditedDraft()
            runCurrent()

            assertThat(viewModel.uiState.value.isWorking).isTrue()
            viewModel.updateQuestionStem(unitIndex = 0, questionIndex = 0, value = "Texto cambiado tarde")

            assertThat(viewModel.uiState.value.draft?.units?.single()?.questions?.single()?.stem).isEqualTo(originalStem)

            repository.completeTree()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isWorking).isFalse()
            assertThat(viewModel.uiState.value.draft?.units?.single()?.questions?.single()?.stem).isEqualTo(originalStem)
        }

    private fun viewModel(repository: ContentRepository): EditableImportViewModel =
        EditableImportViewModel(
            contentRepository = repository,
            extractor = NaturalTextDraftExtractor(),
            preparationService =
                EditableImportPreparationService(
                    draftValidator = ReviewedEditableDraftValidator(),
                    compiler = ReviewedQuestionBankCompiler(),
                    importValidator = DefaultImportValidator(),
                    timeProvider = FixedEditableImportTimeProvider(10L),
                ),
        )
}

private class BlockingTreeContentRepository : ContentRepository {
    private var treeResult = CompletableDeferred<ContentTree>()

    fun completeTree(tree: ContentTree = ContentTree(emptyList())) {
        treeResult.complete(tree)
    }

    fun resetBlock() {
        treeResult = CompletableDeferred()
    }

    override suspend fun seedDemoIfNeeded() = Unit

    override suspend fun importContentPackage(
        rawJson: String,
        sourceLabel: String,
        validationProfile: ImportValidationProfile,
    ): ImportExecutionResult =
        ImportExecutionResult("pkg", true, ValidationReport(emptyList(), emptyList()))

    override suspend fun importContentPackage(uri: Uri): ImportExecutionResult =
        ImportExecutionResult(uri.toString(), false, ValidationReport(emptyList(), emptyList()))

    override suspend fun prepareContentPackage(uri: Uri): ImportPreparationResult =
        error("prepareContentPackage is not used in this test")

    override suspend fun getContentTree(): ContentTree = treeResult.await()

    override suspend fun getUnits(): List<UnitModel> = emptyList()

    override suspend fun getSchedulableNodes(): List<Node> = emptyList()

    override suspend fun getSchedulableNodesByUnit(unitId: String): List<Node> = emptyList()

    override suspend fun getNode(nodeId: String): Node? = null

    override suspend fun getNodeDetail(
        nodeId: String,
        includeArchivedItems: Boolean,
    ): NodeDetail? = null

    override suspend fun getItem(
        itemId: String,
        includeArchived: Boolean,
    ): Item? = null

    override suspend fun getItemsForNode(
        nodeId: String,
        includeArchived: Boolean,
    ): List<Item> = emptyList()

    override suspend fun getItemsForNodes(
        nodeIds: List<String>,
        includeArchived: Boolean,
    ): List<Item> = emptyList()

    override suspend fun setArchivedCandidate(
        nodeId: String,
        archived: Boolean,
    ) = Unit

    override suspend fun getAllItemOverrides(): List<ItemOverride> = emptyList()

    override suspend fun setItemArchived(
        itemId: String,
        archived: Boolean,
    ) = Unit

    override suspend fun updateItemManualEdit(edit: ManualItemEdit) = Unit

    override suspend fun hasRealImportedContent(): Boolean = false

    override suspend fun getContentPackage(rawJson: String): ContentPackageDto =
        error("getContentPackage is not used in this test")
}

private class FixedEditableImportTimeProvider(
    private val now: Long,
) : TimeProvider {
    override fun now(): Long = now
}

private fun validSource(): String =
    """
    Curso: Ethereum
    ID curso: eth
    Unidad: Base
    ID unidad: base

    Pregunta: ¿Qué es Ethereum?
    Respuesta: Un protocolo.
    Explicación: Se estudia como protocolo.
    """.trimIndent()
