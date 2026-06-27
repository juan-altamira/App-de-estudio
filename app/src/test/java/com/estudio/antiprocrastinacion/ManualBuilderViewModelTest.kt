package com.estudio.antiprocrastinacion

import android.net.Uri
import com.estudio.antiprocrastinacion.app.data.importing.DefaultImportValidator
import com.estudio.antiprocrastinacion.app.data.importing.EditableImportPreparationService
import com.estudio.antiprocrastinacion.app.data.importing.ReviewedEditableDraftValidator
import com.estudio.antiprocrastinacion.app.data.importing.ReviewedQuestionBankCompiler
import com.estudio.antiprocrastinacion.app.domain.repository.ContentRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ImportExecutionResult
import com.estudio.antiprocrastinacion.app.domain.repository.ImportPreparationResult
import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidationProfile
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationReport
import com.estudio.antiprocrastinacion.app.model.content.ContentTree
import com.estudio.antiprocrastinacion.app.model.content.Course
import com.estudio.antiprocrastinacion.app.model.content.CourseWithUnits
import com.estudio.antiprocrastinacion.app.model.content.FacetType
import com.estudio.antiprocrastinacion.app.model.content.Item
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemOverride
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.ManualItemEdit
import com.estudio.antiprocrastinacion.app.model.content.Node
import com.estudio.antiprocrastinacion.app.model.content.NodeDetail
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.content.UnitModel
import com.estudio.antiprocrastinacion.app.model.content.UnitWithOutcomes
import com.estudio.antiprocrastinacion.app.model.json.ContentPackageDto
import com.estudio.antiprocrastinacion.app.ui.common.AppJson
import com.estudio.antiprocrastinacion.app.ui.common.TimeProvider
import com.estudio.antiprocrastinacion.app.ui.content.ManualBuilderViewModel
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedQuestionFormat
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.decodeFromString
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManualBuilderViewModelTest {
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
    fun `four multiple choice questions import as consistent friction one quick items`() =
        runTest(dispatcher) {
            val repository = CapturingContentRepository()
            val viewModel = newViewModel(repository)
            advanceUntilIdle()

            viewModel.createNewCourse("Biología")
            viewModel.createUnit("Fotosíntesis")
            viewModel.buildQuickQuestions(ReviewedQuestionFormat.MULTIPLE_CHOICE, count = 4)

            viewModel.requestSave()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.saved).isTrue()
            assertThat(viewModel.uiState.value.softError).isNull()
            assertThat(repository.lastProfile).isEqualTo(ImportValidationProfile.REVIEWED_QUESTION_BANK)

            val pkg = repository.lastPackage()
            assertThat(pkg.items).hasSize(4)
            assertThat(pkg.items.map { it.frictionLevel }.toSet()).containsExactly(1)
            assertThat(pkg.items.map { it.format }.toSet()).containsExactly(ItemFormat.MULTIPLE_CHOICE)
            assertThat(pkg.items.map { it.itemRole }.toSet()).containsExactly(ItemRole.CORE)
            pkg.items.forEach { item ->
                assertThat(item.allowedSurfaces).contains(Surface.IN_APP_QUICK)
                assertThat(item.allowedSurfaces).contains(Surface.SOCIAL_GATE)
            }
            val node = pkg.nodes.single()
            assertThat(node.surfaceEasyReady).isTrue()
            assertThat(node.surfaceEasyItemCount).isEqualTo(4)
        }

    @Test
    fun `choose-false questions count as quick and never become trap items`() =
        runTest(dispatcher) {
            val repository = CapturingContentRepository()
            val viewModel = newViewModel(repository)
            advanceUntilIdle()

            viewModel.createNewCourse("Lengua")
            viewModel.createUnit("Reglas")
            viewModel.buildQuickQuestions(ReviewedQuestionFormat.CHOOSE_FALSE_STATEMENT, count = 4)

            viewModel.requestSave()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.saved).isTrue()
            val pkg = repository.lastPackage()
            assertThat(pkg.items.map { it.format }.toSet()).containsExactly(ItemFormat.CHOOSE_FALSE_STATEMENT)
            assertThat(pkg.items.map { it.frictionLevel }.toSet()).containsExactly(1)
            // CHOOSE_FALSE_STATEMENT is a presentation format, NOT a pedagogical trap.
            assertThat(pkg.items.map { it.itemRole }.toSet()).containsExactly(ItemRole.CORE)
            assertThat(pkg.items.none { it.facet == FacetType.ERROR_TIPICO }).isTrue()
            assertThat(pkg.nodes.single().surfaceEasyReady).isTrue()
        }

    @Test
    fun `true false questions import without options and normalized answers`() =
        runTest(dispatcher) {
            val repository = CapturingContentRepository()
            val viewModel = newViewModel(repository)
            advanceUntilIdle()

            viewModel.createNewCourse("Historia")
            viewModel.createUnit("Fechas")
            ensureQuestionCount(viewModel, 4)
            for (i in 0..3) {
                viewModel.setQuestionFormat(i, ReviewedQuestionFormat.TRUE_FALSE)
                viewModel.setQuestionStem(i, "Afirmación $i")
                viewModel.setTrueFalseAnswer(i, isTrue = i % 2 == 0)
            }

            viewModel.requestSave()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.saved).isTrue()
            val pkg = repository.lastPackage()
            assertThat(pkg.items.map { it.format }.toSet()).containsExactly(ItemFormat.TRUE_FALSE)
            assertThat(pkg.items.all { it.options.isEmpty() }).isTrue()
            assertThat(pkg.items.map { it.correctAnswer }.toSet()).containsExactly("Verdadero", "Falso")
            assertThat(pkg.items.map { it.frictionLevel }.toSet()).containsExactly(1)
        }

    @Test
    fun `unit with fewer than four quick questions cannot be saved and guidance appears`() =
        runTest(dispatcher) {
            val repository = CapturingContentRepository()
            val viewModel = newViewModel(repository)
            advanceUntilIdle()

            viewModel.createNewCourse("Física")
            viewModel.createUnit("Cinemática")
            viewModel.buildQuickQuestions(ReviewedQuestionFormat.MULTIPLE_CHOICE, count = 3)

            viewModel.requestSave()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.saved).isFalse()
            assertThat(viewModel.uiState.value.showAlmostReady).isTrue()
            assertThat(repository.imported).isFalse()
            assertThat(viewModel.uiState.value.currentReadiness?.needQuick).isEqualTo(1)
        }

    @Test
    fun `reveal-answer questions are deep only and do not count toward the quick minimum`() =
        runTest(dispatcher) {
            val repository = CapturingContentRepository()
            val viewModel = newViewModel(repository)
            advanceUntilIdle()

            viewModel.createNewCourse("Filosofía")
            viewModel.createUnit("Conceptos")
            // Four reveal-answer questions: complete, but none is "quick".
            ensureQuestionCount(viewModel, 4)
            for (i in 0..3) {
                viewModel.setQuestionFormat(i, ReviewedQuestionFormat.REVEAL_ANSWER)
                viewModel.setQuestionStem(i, "Explicá el concepto $i")
                viewModel.setRevealAnswer(i, "Una explicación $i")
            }

            assertThat(viewModel.uiState.value.currentReadiness?.needQuick).isEqualTo(4)
            assertThat(viewModel.uiState.value.currentReadiness?.incompleteCount).isEqualTo(0)

            viewModel.requestSave()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.saved).isFalse()
            assertThat(viewModel.uiState.value.showAlmostReady).isTrue()
        }

    @Test
    fun `reveal-answer cards enter the in-app daily review but stay out of strict external surfaces`() =
        runTest(dispatcher) {
            val repository = CapturingContentRepository()
            val viewModel = newViewModel(repository)
            advanceUntilIdle()

            viewModel.createNewCourse("Química")
            viewModel.createUnit("Enlaces")
            viewModel.buildQuickQuestions(ReviewedQuestionFormat.MULTIPLE_CHOICE, count = 4)
            // Add a fifth reveal-answer question as an optional extra.
            viewModel.addQuestion()
            val revealIndex = viewModel.uiState.value.currentUnit!!.questions.lastIndex
            viewModel.setQuestionFormat(revealIndex, ReviewedQuestionFormat.REVEAL_ANSWER)
            viewModel.setQuestionStem(revealIndex, "Explicá el enlace covalente")
            viewModel.setRevealAnswer(revealIndex, "Comparten electrones")

            viewModel.requestSave()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.saved).isTrue()
            val pkg = repository.lastPackage()
            val reveal = pkg.items.single { it.format == ItemFormat.ONE_SENTENCE_EXPLANATION }
            assertThat(reveal.frictionLevel).isEqualTo(3)
            // Enters the daily pending review (and deep), so it is actually studied with spaced repetition.
            assertThat(reveal.allowedSurfaces).containsExactly(Surface.IN_APP_QUICK, Surface.IN_APP_DEEP)
            // But never leaks into auto-graded-only external surfaces.
            assertThat(reveal.allowedSurfaces).containsNoneOf(Surface.SOCIAL_GATE, Surface.NOTIFICATION, Surface.BACK_MICRO)
            // It does NOT count as a quick/easy item: the four real quick cards do.
            assertThat(pkg.nodes.single().surfaceEasyReady).isTrue()
            assertThat(pkg.nodes.single().surfaceEasyItemCount).isEqualTo(4)
        }

    @Test
    fun `blank explanation is auto-filled with mechanical feedback so nothing blocks the user`() =
        runTest(dispatcher) {
            val repository = CapturingContentRepository()
            val viewModel = newViewModel(repository)
            advanceUntilIdle()

            viewModel.createNewCourse("Geografía")
            viewModel.createUnit("Mapas")
            viewModel.buildQuickQuestions(ReviewedQuestionFormat.MULTIPLE_CHOICE, count = 4)
            // Explicitly leave every explanation blank (buildQuickQuestions never sets feedback).

            viewModel.requestSave()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.saved).isTrue()
            val pkg = repository.lastPackage()
            assertThat(pkg.items.all { it.feedbackShort.startsWith("Respuesta correcta:") }).isTrue()
        }

    @Test
    fun `adding a unit to an existing course keeps the existing course id`() =
        runTest(dispatcher) {
            val repository = CapturingContentRepository(tree = singleCourseTree())
            val viewModel = newViewModel(repository)
            advanceUntilIdle()

            val option = viewModel.uiState.value.existingCourses.single()
            viewModel.chooseExistingCourse(option)
            advanceUntilIdle()
            viewModel.createUnit("Tema nuevo")
            viewModel.buildQuickQuestions(ReviewedQuestionFormat.MULTIPLE_CHOICE, count = 4)

            viewModel.requestSave()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.saved).isTrue()
            val pkg = repository.lastPackage()
            assertThat(pkg.courses.single().courseId).isEqualTo("bio")
            // New unit id is namespaced under the existing course and does not collide.
            assertThat(pkg.units.single().unitId).isEqualTo("bio__tema_nuevo")
        }

    @Test
    fun `create new course never silently merges into an existing course with the same name`() =
        runTest(dispatcher) {
            val tree =
                ContentTree(
                    courses =
                        listOf(
                            CourseWithUnits(
                                course = Course(courseId = "biologia", title = "Biología", description = null, version = 1, updatedAt = 0L),
                                units = emptyList(),
                            ),
                        ),
                )
            val repository = CapturingContentRepository(tree = tree)
            val viewModel = newViewModel(repository)
            advanceUntilIdle()

            viewModel.createNewCourse("Biología")
            assertThat(viewModel.uiState.value.courseKey).isNotEqualTo("biologia")

            viewModel.createUnit("Tema")
            viewModel.buildQuickQuestions(ReviewedQuestionFormat.MULTIPLE_CHOICE, count = 4)
            viewModel.requestSave()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.saved).isTrue()
            // A brand-new, distinct course id — it does not overwrite or merge into "biologia".
            assertThat(repository.lastPackage().courses.single().courseId).isEqualTo("biologia_2")
        }

    // ---- helpers ----------------------------------------------------------------------------

    private fun newViewModel(repository: ContentRepository): ManualBuilderViewModel =
        ManualBuilderViewModel(
            contentRepository = repository,
            preparationService =
                EditableImportPreparationService(
                    draftValidator = ReviewedEditableDraftValidator(),
                    compiler = ReviewedQuestionBankCompiler(),
                    importValidator = DefaultImportValidator(),
                    timeProvider = FixedTime(1_000L),
                ),
        )

    private fun ensureQuestionCount(
        viewModel: ManualBuilderViewModel,
        count: Int,
    ) {
        while ((viewModel.uiState.value.currentUnit?.questions?.size ?: 0) < count) {
            viewModel.addQuestion()
        }
    }

    private fun ManualBuilderViewModel.buildQuickQuestions(
        format: ReviewedQuestionFormat,
        count: Int,
    ) {
        ensureQuestionCount(this, count)
        for (i in 0 until count) {
            setQuestionFormat(i, format)
            setQuestionStem(i, "Pregunta $i")
            setOptionText(i, 0, "Opción A $i")
            setOptionText(i, 1, "Opción B $i")
            markOptionCorrect(i, 0)
        }
    }
}

private fun singleCourseTree(): ContentTree =
    ContentTree(
        courses =
            listOf(
                CourseWithUnits(
                    course = Course(courseId = "bio", title = "Biología", description = null, version = 1, updatedAt = 0L),
                    units =
                        listOf(
                            UnitWithOutcomes(
                                unit =
                                    UnitModel(
                                        unitId = "bio__base",
                                        courseId = "bio",
                                        title = "Base",
                                        description = null,
                                        orderIndex = 0,
                                        version = 1,
                                        updatedAt = 0L,
                                    ),
                                outcomes = emptyList(),
                            ),
                        ),
                ),
            ),
    )

private class CapturingContentRepository(
    private val tree: ContentTree = ContentTree(emptyList()),
) : ContentRepository {
    var imported: Boolean = false
        private set
    var lastProfile: ImportValidationProfile? = null
        private set
    private var lastJson: String? = null

    fun lastPackage(): ContentPackageDto = AppJson.decodeFromString(lastJson!!)

    override suspend fun seedDemoIfNeeded() = Unit

    override suspend fun importContentPackage(
        rawJson: String,
        sourceLabel: String,
        validationProfile: ImportValidationProfile,
    ): ImportExecutionResult {
        imported = true
        lastProfile = validationProfile
        lastJson = rawJson
        return ImportExecutionResult("manual:builder", true, ValidationReport(emptyList(), emptyList()))
    }

    override suspend fun importContentPackage(uri: Uri): ImportExecutionResult =
        ImportExecutionResult(uri.toString(), false, ValidationReport(emptyList(), emptyList()))

    override suspend fun prepareContentPackage(uri: Uri): ImportPreparationResult =
        error("not used")

    override suspend fun getContentTree(): ContentTree = tree

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

    override suspend fun getContentPackage(rawJson: String): ContentPackageDto = error("not used")
}

private class FixedTime(
    private val now: Long,
) : TimeProvider {
    override fun now(): Long = now
}
