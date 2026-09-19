package com.estudio.antiprocrastinacion.app.ui.content

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estudio.antiprocrastinacion.app.data.importing.EditableImportPreparationService
import com.estudio.antiprocrastinacion.app.data.importing.ExistingCourseCompilationTarget
import com.estudio.antiprocrastinacion.app.data.importing.ExistingUnitCompilationTarget
import com.estudio.antiprocrastinacion.app.data.importing.ReviewedQuestionBankCompilationContext
import com.estudio.antiprocrastinacion.app.data.importing.normalizeTrueFalseAnswer
import com.estudio.antiprocrastinacion.app.data.importing.slugKey
import com.estudio.antiprocrastinacion.app.domain.repository.ContentRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidationProfile
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableCourseDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableImportDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableOptionDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableQuestionDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableUnitDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedQuestionFormat
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedStableKeySource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Maximum options allowed for a choice question (matches the reviewed contract: 2..6). */
private const val MANUAL_MAX_OPTIONS = 6

enum class ManualBuilderStep {
    COURSE,
    UNIT,
    QUESTIONS,
}

data class ManualCourseOption(
    val courseId: String,
    val title: String,
)

data class ManualExistingUnitOption(
    val draftKey: String,
    val title: String,
)

/** Per-unit, human-facing readiness used to gate saving and to drive the "casi listo" guidance. */
data class ManualUnitReadiness(
    val unitIndex: Int,
    val title: String,
    val completeCount: Int,
    val incompleteCount: Int,
) {
    val isReady: Boolean = completeCount > 0 && incompleteCount == 0
}

data class ManualBuilderUiState(
    val step: ManualBuilderStep = ManualBuilderStep.COURSE,
    val isWorking: Boolean = false,
    val existingCourses: List<ManualCourseOption> = emptyList(),
    val existingUnits: List<ManualExistingUnitOption> = emptyList(),
    val courseChosen: Boolean = false,
    val courseIsExisting: Boolean = false,
    val courseTitle: String = "",
    val courseKey: String = "",
    val existingUnitLocalKeys: Set<String> = emptySet(),
    val draft: ReviewedEditableImportDraft = ReviewedEditableImportDraft(),
    val currentUnitIndex: Int = -1,
    val readiness: List<ManualUnitReadiness> = emptyList(),
    val showAlmostReady: Boolean = false,
    val saved: Boolean = false,
    val softError: String? = null,
) {
    val currentUnit: ReviewedEditableUnitDraft?
        get() = draft.units.getOrNull(currentUnitIndex)

    val currentReadiness: ManualUnitReadiness?
        get() = readiness.getOrNull(currentUnitIndex)

    val allReady: Boolean
        get() = draft.units.isNotEmpty() && readiness.isNotEmpty() && readiness.all { it.isReady }
}

class ManualBuilderViewModel(
    private val contentRepository: ContentRepository,
    private val preparationService: EditableImportPreparationService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ManualBuilderUiState())
    val uiState = _uiState.asStateFlow()
    private var compilationContext = ReviewedQuestionBankCompilationContext()
    private var existingUnitTargets: Map<String, ExistingUnitCompilationTarget> = emptyMap()

    init {
        refreshCourses()
    }

    private fun refreshCourses() {
        viewModelScope.launch {
            val tree = contentRepository.getContentTree()
            val courses =
                tree.courses
                    .map { ManualCourseOption(courseId = it.course.courseId, title = it.course.title) }
                    .sortedBy { it.title.lowercase() }
            _uiState.value = _uiState.value.copy(existingCourses = courses)
        }
    }

    // ---- Course step ------------------------------------------------------------------------

    fun createNewCourse(title: String) {
        if (_uiState.value.isWorking) return
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return
        // "Create new" must never silently merge into an existing course that happens to share a
        // slug: dedupe the key against existing course ids so the new course is always distinct.
        val existingCourseIds = _uiState.value.existingCourses.map { it.courseId }.toSet()
        val key = dedupeKey(cleanTitle.slugKey("curso"), existingCourseIds)
        compilationContext = ReviewedQuestionBankCompilationContext()
        existingUnitTargets = emptyMap()
        _uiState.value =
            _uiState.value.copy(
                courseChosen = true,
                courseIsExisting = false,
                existingUnits = emptyList(),
                courseTitle = cleanTitle,
                courseKey = key,
                existingUnitLocalKeys = emptySet(),
                draft =
                    ReviewedEditableImportDraft(
                        course =
                            ReviewedEditableCourseDraft(
                                title = cleanTitle,
                                key = key,
                                keySource = ReviewedStableKeySource.GENERATED,
                            ),
                        units = emptyList(),
                    ),
                currentUnitIndex = -1,
                step = ManualBuilderStep.UNIT,
                softError = null,
                saved = false,
            )
        recomputeReadiness()
    }

    fun chooseExistingCourse(option: ManualCourseOption) {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true)
            val tree = contentRepository.getContentTree()
            val course = tree.courses.firstOrNull { it.course.courseId == option.courseId }
            val usedDraftKeys = mutableSetOf<String>()
            val targets = linkedMapOf<String, ExistingUnitCompilationTarget>()
            val existingUnits =
                course
                    ?.units
                    ?.sortedBy { it.unit.orderIndex }
                    ?.map { unitWithOutcomes ->
                        val unit = unitWithOutcomes.unit
                        val suggestedDraftKey =
                            localUnitKey(unit.unitId, option.courseId)
                                ?: unit.title.slugKey("unidad_existente")
                        val draftKey = dedupeKey(suggestedDraftKey, usedDraftKeys)
                        usedDraftKeys += draftKey
                        val existingOutcomeIds = unitWithOutcomes.outcomes.map { it.outcome.outcomeId }.toSet()
                        val existingNodeIds =
                            unitWithOutcomes.outcomes
                                .flatMap { it.nodes }
                                .map { it.nodeId }
                                .toSet()
                        targets[draftKey] =
                            ExistingUnitCompilationTarget(
                                unitId = unit.unitId,
                                courseId = unit.courseId,
                                title = unit.title,
                                description = unit.description,
                                orderIndex = unit.orderIndex,
                                version = unit.version,
                                updatedAt = unit.updatedAt,
                                newOutcomeId =
                                    dedupeKey(
                                        base = "${unit.unitId}__resolver_preguntas_manual",
                                        used = existingOutcomeIds,
                                    ),
                                newNodeId =
                                    dedupeKey(
                                        base = "${unit.unitId}__contenido_manual",
                                        used = existingNodeIds,
                                    ),
                            )
                        ManualExistingUnitOption(draftKey = draftKey, title = unit.title)
                    }.orEmpty()
            val existingLocalKeys = existingUnits.map { it.draftKey }.toSet()
            existingUnitTargets = targets
            compilationContext =
                ReviewedQuestionBankCompilationContext(
                    existingCourse =
                        course?.course?.let {
                            ExistingCourseCompilationTarget(
                                courseId = it.courseId,
                                title = it.title,
                                description = it.description,
                                version = it.version,
                                updatedAt = it.updatedAt,
                            )
                        },
                    nextNewUnitOrderIndex =
                        (course?.units?.maxOfOrNull { it.unit.orderIndex } ?: -1) + 1,
                )
            _uiState.value =
                _uiState.value.copy(
                    isWorking = false,
                    courseChosen = true,
                    courseIsExisting = true,
                    existingUnits = existingUnits,
                    courseTitle = course?.course?.title ?: option.title,
                    courseKey = option.courseId,
                    existingUnitLocalKeys = existingLocalKeys,
                    draft =
                        ReviewedEditableImportDraft(
                            course =
                                ReviewedEditableCourseDraft(
                                    title = course?.course?.title ?: option.title,
                                    key = option.courseId,
                                    keySource = ReviewedStableKeySource.EXPLICIT,
                                ),
                            units = emptyList(),
                        ),
                    currentUnitIndex = -1,
                    step = ManualBuilderStep.UNIT,
                    softError = null,
                    saved = false,
                )
            recomputeReadiness()
        }
    }

    // ---- Unit step --------------------------------------------------------------------------

    fun chooseExistingUnit(option: ManualExistingUnitOption) {
        if (_uiState.value.isWorking) return
        val current = _uiState.value
        val existingDraftIndex = current.draft.units.indexOfFirst { it.key == option.draftKey }
        if (existingDraftIndex >= 0) {
            editUnit(existingDraftIndex)
            return
        }
        val target = existingUnitTargets[option.draftKey] ?: return
        val newUnit =
            ReviewedEditableUnitDraft(
                title = target.title,
                key = option.draftKey,
                keySource = ReviewedStableKeySource.EXPLICIT,
                questions = listOf(newQuestion(emptyList())),
            )
        val units = current.draft.units + newUnit
        compilationContext =
            compilationContext.copy(
                existingUnits = compilationContext.existingUnits + (option.draftKey to target),
            )
        _uiState.value =
            current.copy(
                draft = current.draft.copy(units = units),
                currentUnitIndex = units.lastIndex,
                step = ManualBuilderStep.QUESTIONS,
                softError = null,
                saved = false,
                showAlmostReady = false,
            )
        recomputeReadiness()
    }

    fun createUnit(title: String) {
        if (_uiState.value.isWorking) return
        val current = _uiState.value
        if (!current.courseChosen) return
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return
        val used = current.draft.units.map { it.key }.toSet() + current.existingUnitLocalKeys
        val key = dedupeKey(cleanTitle.slugKey("unidad"), used)
        val newUnit =
            ReviewedEditableUnitDraft(
                title = cleanTitle,
                key = key,
                keySource = ReviewedStableKeySource.EXPLICIT,
                questions = listOf(newQuestion(emptyList())),
            )
        val units = current.draft.units + newUnit
        _uiState.value =
            current.copy(
                draft = current.draft.copy(units = units),
                currentUnitIndex = units.lastIndex,
                step = ManualBuilderStep.QUESTIONS,
                softError = null,
                saved = false,
                showAlmostReady = false,
            )
        recomputeReadiness()
    }

    /** Re-enter the unit step to add another unit to the same course. */
    fun goToAddUnit() {
        if (_uiState.value.isWorking) return
        _uiState.value =
            _uiState.value.copy(
                step = ManualBuilderStep.UNIT,
                softError = null,
                showAlmostReady = false,
            )
    }

    fun editUnit(unitIndex: Int) {
        if (_uiState.value.isWorking) return
        if (unitIndex !in _uiState.value.draft.units.indices) return
        _uiState.value =
            _uiState.value.copy(
                currentUnitIndex = unitIndex,
                step = ManualBuilderStep.QUESTIONS,
                showAlmostReady = false,
                softError = null,
            )
    }

    // ---- Question editing (operates on the current unit) ------------------------------------

    fun addQuestion() {
        updateCurrentUnit { unit -> unit.copy(questions = unit.questions + newQuestion(unit.questions)) }
    }

    fun removeQuestion(questionIndex: Int) {
        updateCurrentUnit { unit ->
            unit.copy(questions = unit.questions.filterIndexed { index, _ -> index != questionIndex })
        }
    }

    fun setQuestionFormat(
        questionIndex: Int,
        format: ReviewedQuestionFormat,
    ) {
        updateQuestion(questionIndex) { question ->
            val options =
                when (format) {
                    ReviewedQuestionFormat.MULTIPLE_CHOICE,
                    ReviewedQuestionFormat.CHOOSE_FALSE_STATEMENT,
                    ->
                        question.options.ifEmpty {
                            listOf(
                                ReviewedEditableOptionDraft(key = "A"),
                                ReviewedEditableOptionDraft(key = "B"),
                            )
                        }

                    ReviewedQuestionFormat.TRUE_FALSE,
                    ReviewedQuestionFormat.REVEAL_ANSWER,
                    -> emptyList()
                }
            // Changing the format changes what "correct" means, so the answer is re-specified.
            question.copy(format = format, options = options, correctAnswer = "")
        }
    }

    fun setQuestionStem(
        questionIndex: Int,
        value: String,
    ) {
        updateQuestion(questionIndex) { it.copy(stem = value) }
    }

    /** For "responder con tus palabras". */
    fun setRevealAnswer(
        questionIndex: Int,
        value: String,
    ) {
        updateQuestion(questionIndex) { it.copy(correctAnswer = value) }
    }

    /** For verdadero/falso. */
    fun setTrueFalseAnswer(
        questionIndex: Int,
        isTrue: Boolean,
    ) {
        updateQuestion(questionIndex) { it.copy(correctAnswer = if (isTrue) "Verdadero" else "Falso") }
    }

    fun setQuestionFeedback(
        questionIndex: Int,
        value: String,
    ) {
        updateQuestion(questionIndex) { it.copy(feedback = value, feedbackGenerated = false) }
    }

    fun setOptionText(
        questionIndex: Int,
        optionIndex: Int,
        value: String,
    ) {
        updateQuestion(questionIndex) { question ->
            val options =
                question.options.mapIndexed { index, option ->
                    if (index == optionIndex) option.copy(text = value) else option
                }
            val correctAnswer =
                options.firstOrNull { it.isCorrect }?.text?.takeIf(String::isNotBlank) ?: question.correctAnswer
            question.copy(options = options, correctAnswer = correctAnswer)
        }
    }

    fun markOptionCorrect(
        questionIndex: Int,
        optionIndex: Int,
    ) {
        updateQuestion(questionIndex) { question ->
            val options = question.options.mapIndexed { index, option -> option.copy(isCorrect = index == optionIndex) }
            question.copy(
                options = options,
                correctAnswer = options.getOrNull(optionIndex)?.text.orEmpty(),
            )
        }
    }

    fun addOption(questionIndex: Int) {
        updateQuestion(questionIndex) { question ->
            if (question.options.size >= MANUAL_MAX_OPTIONS) {
                question
            } else {
                val used = question.options.map { it.key }.toSet()
                val nextKey = ('A'..'F').map(Char::toString).firstOrNull { it !in used } ?: "F"
                question.copy(options = question.options + ReviewedEditableOptionDraft(key = nextKey))
            }
        }
    }

    fun removeOption(
        questionIndex: Int,
        optionIndex: Int,
    ) {
        updateQuestion(questionIndex) { question ->
            val removed = question.options.getOrNull(optionIndex)
            val options = question.options.filterIndexed { index, _ -> index != optionIndex }
            question.copy(
                options = options,
                correctAnswer =
                    if (removed?.isCorrect == true) {
                        options.firstOrNull { it.isCorrect }?.text.orEmpty()
                    } else {
                        question.correctAnswer
                    },
            )
        }
    }

    // ---- Navigation / saving ----------------------------------------------------------------

    fun back() {
        if (_uiState.value.isWorking) return
        val current = _uiState.value
        if (current.showAlmostReady) {
            _uiState.value = current.copy(showAlmostReady = false)
            return
        }
        _uiState.value =
            when (current.step) {
                ManualBuilderStep.QUESTIONS -> current.copy(step = ManualBuilderStep.UNIT, softError = null)
                ManualBuilderStep.UNIT -> current.copy(step = ManualBuilderStep.COURSE, softError = null)
                ManualBuilderStep.COURSE -> current
            }
    }

    /** True when [back] cannot go further inside the wizard and the screen should pop navigation. */
    fun atRoot(): Boolean = _uiState.value.step == ManualBuilderStep.COURSE && !_uiState.value.showAlmostReady

    fun requestSave() {
        if (_uiState.value.isWorking) return
        recomputeReadiness()
        if (!_uiState.value.allReady) {
            _uiState.value = _uiState.value.copy(showAlmostReady = true)
            return
        }
        doImport()
    }

    fun dismissAlmostReady() {
        _uiState.value = _uiState.value.copy(showAlmostReady = false)
    }

    private fun doImport() {
        val draft = withCompiledDefaults(_uiState.value.draft)
        val currentCompilationContext = compilationContext
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, softError = null, showAlmostReady = false)
            val prepared =
                preparationService.prepare(
                    draft = draft,
                    compilationContext = currentCompilationContext,
                )
            val json = prepared.contentPackageJson
            if (prepared.canImport && json != null) {
                val result =
                    contentRepository.importContentPackage(
                        rawJson = json,
                        sourceLabel = "manual:builder",
                        validationProfile = ImportValidationProfile.REVIEWED_QUESTION_BANK,
                    )
                _uiState.value =
                    if (result.imported) {
                        _uiState.value.copy(isWorking = false, saved = true, softError = null)
                    } else {
                        _uiState.value.copy(isWorking = false, softError = SOFT_SAVE_ERROR)
                    }
            } else {
                // Safety net: readiness should guarantee this branch is unreachable.
                _uiState.value = _uiState.value.copy(isWorking = false, softError = SOFT_SAVE_ERROR)
            }
        }
    }

    // ---- Internals --------------------------------------------------------------------------

    private fun updateCurrentUnit(transform: (ReviewedEditableUnitDraft) -> ReviewedEditableUnitDraft) {
        val current = _uiState.value
        if (current.isWorking) return
        val index = current.currentUnitIndex
        if (index !in current.draft.units.indices) return
        val units =
            current.draft.units.mapIndexed { unitIndex, unit ->
                if (unitIndex == index) transform(unit) else unit
            }
        _uiState.value =
            current.copy(
                draft = current.draft.copy(units = units),
                softError = null,
                saved = false,
            )
        recomputeReadiness()
    }

    private fun updateQuestion(
        questionIndex: Int,
        transform: (ReviewedEditableQuestionDraft) -> ReviewedEditableQuestionDraft,
    ) {
        updateCurrentUnit { unit ->
            unit.copy(
                questions =
                    unit.questions.mapIndexed { index, question ->
                        if (index == questionIndex) transform(question) else question
                    },
            )
        }
    }

    private fun recomputeReadiness() {
        _uiState.value =
            _uiState.value.copy(
                readiness = computeManualReadiness(_uiState.value.draft),
            )
    }

    private fun newQuestion(existing: List<ReviewedEditableQuestionDraft>): ReviewedEditableQuestionDraft =
        ReviewedEditableQuestionDraft(
            key = nextQuestionKey(existing),
            stem = "",
            format = ReviewedQuestionFormat.MULTIPLE_CHOICE,
            studyRole = com.estudio.antiprocrastinacion.app.model.authoring.ReviewedQuestionStudyRole.NORMAL,
            options =
                listOf(
                    ReviewedEditableOptionDraft(key = "A"),
                    ReviewedEditableOptionDraft(key = "B"),
                ),
            correctAnswer = "",
            feedback = "",
            keySource = ReviewedStableKeySource.GENERATED,
        )

    private companion object {
        const val SOFT_SAVE_ERROR = "Revisá que cada pregunta tenga su enunciado y su respuesta marcada."
    }
}

// ---- Pure helpers (unit-tested) -------------------------------------------------------------

internal fun questionIsComplete(question: ReviewedEditableQuestionDraft): Boolean {
    if (question.stem.isBlank()) return false
    return when (question.format) {
        ReviewedQuestionFormat.MULTIPLE_CHOICE,
        ReviewedQuestionFormat.CHOOSE_FALSE_STATEMENT,
        -> {
            val nonBlank = question.options.filter { it.text.isNotBlank() }
            nonBlank.size in 2..MANUAL_MAX_OPTIONS && nonBlank.count { it.isCorrect } == 1
        }

        ReviewedQuestionFormat.TRUE_FALSE ->
            question.options.isEmpty() && question.correctAnswer.normalizeTrueFalseAnswer() != null

        ReviewedQuestionFormat.REVEAL_ANSWER ->
            question.options.isEmpty() && question.correctAnswer.isNotBlank()
    }
}

internal fun computeManualReadiness(
    draft: ReviewedEditableImportDraft,
): List<ManualUnitReadiness> =
    draft.units.mapIndexed { index, unit ->
        val complete = unit.questions.count(::questionIsComplete)
        val incomplete = unit.questions.count { !questionIsComplete(it) }
        ManualUnitReadiness(
            unitIndex = index,
            title = unit.title,
            completeCount = complete,
            incompleteCount = incomplete,
        )
    }

/**
 * Normalizes the draft into a shape the reviewed validator accepts without surfacing technical
 * errors: keeps the correct answer field in sync with the marked option, normalizes true/false,
 * and fills blank explanations with mechanical feedback (same convention as the text extractor).
 */
internal fun withCompiledDefaults(draft: ReviewedEditableImportDraft): ReviewedEditableImportDraft =
    draft.copy(
        units =
            draft.units.map { unit ->
                unit.copy(
                    questions =
                        unit.questions.map { question ->
                            val correct =
                                when (question.format) {
                                    ReviewedQuestionFormat.MULTIPLE_CHOICE,
                                    ReviewedQuestionFormat.CHOOSE_FALSE_STATEMENT,
                                    ->
                                        question.options
                                            .firstOrNull { it.isCorrect && it.text.isNotBlank() }
                                            ?.text
                                            ?.trim()
                                            ?: question.correctAnswer.trim()

                                    ReviewedQuestionFormat.TRUE_FALSE ->
                                        when (question.correctAnswer.normalizeTrueFalseAnswer()) {
                                            true -> "Verdadero"
                                            false -> "Falso"
                                            null -> question.correctAnswer.trim()
                                        }

                                    ReviewedQuestionFormat.REVEAL_ANSWER -> question.correctAnswer.trim()
                                }
                            val feedback =
                                question.feedback.trim().ifBlank {
                                    if (correct.isNotBlank()) "Respuesta correcta: $correct." else ""
                                }
                            question.copy(
                                stem = question.stem.trim(),
                                correctAnswer = correct,
                                feedback = feedback,
                            )
                        },
                )
            },
    )

internal fun dedupeKey(
    base: String,
    used: Set<String>,
): String {
    if (base !in used) return base
    var suffix = 2
    var candidate = "${base}_$suffix"
    while (candidate in used) {
        suffix += 1
        candidate = "${base}_$suffix"
    }
    return candidate
}

internal fun nextQuestionKey(existing: List<ReviewedEditableQuestionDraft>): String {
    val used = existing.map { it.key }.toSet()
    var counter = existing.size + 1
    var key = "pregunta_$counter"
    while (key in used) {
        counter += 1
        key = "pregunta_$counter"
    }
    return key
}

/**
 * Recovers the local unit key (the part after `courseId__`) so the wizard can avoid colliding with
 * an existing unit when adding a brand-new unit to an existing course. Returns null when the unit id
 * does not follow the deterministic `courseId__localKey` shape.
 */
internal fun localUnitKey(
    unitId: String,
    courseId: String,
): String? {
    val prefix = "${courseId}__"
    if (!unitId.startsWith(prefix)) return null
    return unitId.removePrefix(prefix).takeIf { it.isNotBlank() }
}
