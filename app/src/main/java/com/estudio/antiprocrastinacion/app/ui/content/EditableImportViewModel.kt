package com.estudio.antiprocrastinacion.app.ui.content

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estudio.antiprocrastinacion.app.data.importing.EditableImportPreparationService
import com.estudio.antiprocrastinacion.app.data.importing.NaturalTextDraftExtractor
import com.estudio.antiprocrastinacion.app.domain.repository.ContentRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ImportExecutionResult
import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidationProfile
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationReport
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableImportDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableOptionDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableQuestionDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableUnitDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedQuestionFormat
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedStableKeySource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class EditableImportStep {
    SOURCE,
    PREVIEW,
}

data class EditableImportUiState(
    val step: EditableImportStep = EditableImportStep.SOURCE,
    val sourceText: String = "",
    val draft: ReviewedEditableImportDraft? = null,
    val analysisReport: ValidationReport = ValidationReport(emptyList(), emptyList()),
    val finalReport: ValidationReport? = null,
    val preview: ImportPreview? = null,
    val compiledJson: String? = null,
    val lastImportResult: ImportExecutionResult? = null,
    val isWorking: Boolean = false,
) {
    val visibleReport: ValidationReport = finalReport ?: analysisReport
    val canAnalyze: Boolean = sourceText.isNotBlank() && !isWorking
    val canPrepareDraft: Boolean = draft != null && !isWorking
    val canConfirmImport: Boolean = step == EditableImportStep.PREVIEW && compiledJson != null && visibleReport.canImport && !isWorking
}

class EditableImportViewModel(
    private val contentRepository: ContentRepository,
    private val extractor: NaturalTextDraftExtractor,
    private val preparationService: EditableImportPreparationService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditableImportUiState())
    val uiState = _uiState.asStateFlow()

    fun updateSourceText(value: String) {
        if (_uiState.value.isWorking) return
        _uiState.value =
            _uiState.value.copy(
                sourceText = value,
                step = EditableImportStep.SOURCE,
                draft = null,
                analysisReport = ValidationReport(emptyList(), emptyList()),
                finalReport = null,
                preview = null,
                compiledJson = null,
                lastImportResult = null,
            )
    }

    fun analyzeSource() {
        val source = _uiState.value.sourceText
        if (source.isBlank()) {
            _uiState.value =
                _uiState.value.copy(
                    analysisReport =
                        ValidationReport(
                            structuralErrors =
                                listOf(
                                    com.estudio.antiprocrastinacion.app.domain.repository.ValidationMessage(
                                        code = "source_text_blank",
                                        message = "El texto fuente esta vacio.",
                                        path = "line:1",
                                        expected = "Un unico texto con Curso:, Unidad: y preguntas.",
                                        actual = "Texto vacio.",
                                        hint = "Pegá el curso completo antes de analizar.",
                                    ),
                                ),
                            authoringWarnings = emptyList(),
                        ),
                )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, lastImportResult = null)
            val extracted = extractor.extract(source)
            val prepared =
                preparationService.prepare(
                    draft = extracted.draft,
                    sourceReport = extracted.report,
                )
            val preview =
                prepared.contentPackage?.let { contentPackage ->
                    buildImportPreview(contentPackage, contentRepository.getContentTree(), additive = true)
                }
            _uiState.value =
                _uiState.value.copy(
                    step = if (prepared.canImport && preview != null) EditableImportStep.PREVIEW else EditableImportStep.SOURCE,
                    draft = extracted.draft,
                    analysisReport = prepared.sourceReport + prepared.draftReport,
                    finalReport = prepared.report,
                    preview = preview,
                    compiledJson = prepared.contentPackageJson,
                    isWorking = false,
                )
        }
    }

    fun prepareEditedDraft() {
        val draft = _uiState.value.draft ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, lastImportResult = null)
            val prepared =
                preparationService.prepare(
                    draft = draft,
                    sourceReport = ValidationReport(emptyList(), emptyList()),
                )
            val preview =
                prepared.contentPackage?.let { contentPackage ->
                    buildImportPreview(contentPackage, contentRepository.getContentTree(), additive = true)
                }
            _uiState.value =
                _uiState.value.copy(
                    step = if (prepared.canImport && preview != null) EditableImportStep.PREVIEW else EditableImportStep.SOURCE,
                    analysisReport = prepared.draftReport,
                    finalReport = prepared.report,
                    preview = preview,
                    compiledJson = prepared.contentPackageJson,
                    isWorking = false,
                )
        }
    }

    fun updateCourseTitle(value: String) {
        updateDraft { draft -> draft.copy(course = draft.course.copy(title = value)) }
    }

    fun updateUnitTitle(
        unitIndex: Int,
        value: String,
    ) {
        updateDraft { draft ->
            draft.copy(
                units =
                    draft.units.mapIndexed { index, unit ->
                        if (index == unitIndex) unit.copy(title = value) else unit
                    },
            )
        }
    }

    fun updateQuestionFormat(
        unitIndex: Int,
        questionIndex: Int,
        format: ReviewedQuestionFormat,
    ) {
        updateQuestion(unitIndex, questionIndex) { question ->
            val options =
                when (format) {
                    ReviewedQuestionFormat.MULTIPLE_CHOICE,
                    ReviewedQuestionFormat.CHOOSE_FALSE_STATEMENT,
                    -> question.options.ifEmpty {
                        listOf(
                            ReviewedEditableOptionDraft(key = "A"),
                            ReviewedEditableOptionDraft(key = "B"),
                        )
                    }

                    ReviewedQuestionFormat.TRUE_FALSE,
                    ReviewedQuestionFormat.REVEAL_ANSWER,
                    -> emptyList()
                }
            question.copy(format = format, options = options)
        }
    }

    fun updateQuestionStem(
        unitIndex: Int,
        questionIndex: Int,
        value: String,
    ) {
        updateQuestion(unitIndex, questionIndex) { question -> question.copy(stem = value) }
    }

    fun updateQuestionCorrectAnswer(
        unitIndex: Int,
        questionIndex: Int,
        value: String,
    ) {
        updateQuestion(unitIndex, questionIndex) { question -> question.copy(correctAnswer = value) }
    }

    fun updateQuestionFeedback(
        unitIndex: Int,
        questionIndex: Int,
        value: String,
    ) {
        updateQuestion(unitIndex, questionIndex) { question -> question.copy(feedback = value, feedbackGenerated = false) }
    }

    fun updateQuestionCorrectedConfusion(
        unitIndex: Int,
        questionIndex: Int,
        value: String,
    ) {
        updateQuestion(unitIndex, questionIndex) { question -> question.copy(correctedConfusion = value) }
    }

    fun updateOptionText(
        unitIndex: Int,
        questionIndex: Int,
        optionIndex: Int,
        value: String,
    ) {
        updateQuestion(unitIndex, questionIndex) { question ->
            val updatedOptions =
                question.options.mapIndexed { index, option ->
                    if (index == optionIndex) option.copy(text = value) else option
                }
            val updatedCorrectAnswer =
                updatedOptions.firstOrNull { it.isCorrect }?.text?.takeIf(String::isNotBlank) ?: question.correctAnswer
            question.copy(options = updatedOptions, correctAnswer = updatedCorrectAnswer)
        }
    }

    fun markOptionCorrect(
        unitIndex: Int,
        questionIndex: Int,
        optionIndex: Int,
    ) {
        updateQuestion(unitIndex, questionIndex) { question ->
            val updatedOptions =
                question.options.mapIndexed { index, option ->
                    option.copy(isCorrect = index == optionIndex)
                }
            question.copy(
                options = updatedOptions,
                correctAnswer = updatedOptions.getOrNull(optionIndex)?.text.orEmpty(),
            )
        }
    }

    fun addQuestionOption(
        unitIndex: Int,
        questionIndex: Int,
    ) {
        updateQuestion(unitIndex, questionIndex) { question ->
            if (question.options.size >= 6) {
                question
            } else {
                val usedKeys = question.options.map { it.key }.toSet()
                val nextKey = ('A'..'F').map(Char::toString).firstOrNull { it !in usedKeys } ?: "F"
                question.copy(options = question.options + ReviewedEditableOptionDraft(key = nextKey))
            }
        }
    }

    fun removeQuestionOption(
        unitIndex: Int,
        questionIndex: Int,
        optionIndex: Int,
    ) {
        updateQuestion(unitIndex, questionIndex) { question ->
            val removed = question.options.getOrNull(optionIndex)
            val updatedOptions = question.options.filterIndexed { index, _ -> index != optionIndex }
            question.copy(
                options = updatedOptions,
                correctAnswer =
                    if (removed?.isCorrect == true) {
                        updatedOptions.firstOrNull { it.isCorrect }?.text.orEmpty()
                    } else {
                        question.correctAnswer
                    },
            )
        }
    }

    fun addQuestion(unitIndex: Int) {
        updateDraft { draft ->
            draft.copy(
                units =
                    draft.units.mapIndexed { currentUnitIndex, unit ->
                        if (currentUnitIndex != unitIndex) {
                            unit
                        } else {
                            unit.copy(questions = unit.questions + unit.newManualQuestion())
                        }
                    },
            )
        }
    }

    fun removeQuestion(
        unitIndex: Int,
        questionIndex: Int,
    ) {
        updateDraft { draft ->
            draft.copy(
                units =
                    draft.units.mapIndexed { currentUnitIndex, unit ->
                        if (currentUnitIndex != unitIndex) {
                            unit
                        } else {
                            unit.copy(
                                questions =
                                    unit.questions.filterIndexed { currentQuestionIndex, _ ->
                                        currentQuestionIndex != questionIndex
                                    },
                            )
                        }
                    },
            )
        }
    }

    fun clearSource() {
        if (_uiState.value.isWorking) return
        _uiState.value = EditableImportUiState()
    }

    fun backToSource() {
        if (_uiState.value.isWorking) return
        _uiState.value =
            _uiState.value.copy(
                step = EditableImportStep.SOURCE,
                finalReport = null,
                preview = null,
                compiledJson = null,
            )
    }

    fun confirmImport() {
        val current = _uiState.value
        val rawJson = current.compiledJson ?: return
        if (!current.canConfirmImport) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true)
            val result =
                contentRepository.importContentPackage(
                    rawJson = rawJson,
                    sourceLabel = "manual:editable-text",
                    validationProfile = ImportValidationProfile.REVIEWED_QUESTION_BANK,
                )
            _uiState.value =
                if (result.imported) {
                    EditableImportUiState(lastImportResult = result)
                } else {
                    _uiState.value.copy(
                        lastImportResult = result,
                        isWorking = false,
                    )
                }
        }
    }

    private fun updateDraft(transform: (ReviewedEditableImportDraft) -> ReviewedEditableImportDraft) {
        val current = _uiState.value
        if (current.isWorking) return
        val draft = current.draft ?: return
        _uiState.value =
            current.copy(
                draft = transform(draft),
                step = EditableImportStep.SOURCE,
                finalReport = null,
                preview = null,
                compiledJson = null,
                lastImportResult = null,
            )
    }

    private fun updateQuestion(
        unitIndex: Int,
        questionIndex: Int,
        transform: (ReviewedEditableQuestionDraft) -> ReviewedEditableQuestionDraft,
    ) {
        updateDraft { draft ->
            draft.copy(
                units =
                    draft.units.mapIndexed { currentUnitIndex, unit ->
                        if (currentUnitIndex != unitIndex) {
                            unit
                        } else {
                            unit.copy(
                                questions =
                                    unit.questions.mapIndexed { currentQuestionIndex, question ->
                                        if (currentQuestionIndex == questionIndex) transform(question) else question
                                    },
                            )
                        }
                    },
            )
        }
    }
}

private fun ReviewedEditableUnitDraft.newManualQuestion(): ReviewedEditableQuestionDraft {
    val usedKeys = questions.map { it.key }.toSet()
    var counter = questions.size + 1
    var key = "pregunta_manual_$counter"
    while (key in usedKeys) {
        counter += 1
        key = "pregunta_manual_$counter"
    }
    return ReviewedEditableQuestionDraft(
        key = key,
        stem = "",
        format = ReviewedQuestionFormat.REVEAL_ANSWER,
        correctAnswer = "",
        feedback = "",
        keySource = ReviewedStableKeySource.GENERATED,
    )
}

private operator fun ValidationReport.plus(other: ValidationReport): ValidationReport =
    ValidationReport(
        structuralErrors = structuralErrors + other.structuralErrors,
        authoringWarnings = authoringWarnings + other.authoringWarnings,
    )
