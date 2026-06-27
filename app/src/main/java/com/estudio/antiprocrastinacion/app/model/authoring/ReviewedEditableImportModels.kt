package com.estudio.antiprocrastinacion.app.model.authoring

data class ReviewedEditableImportDraft(
    val course: ReviewedEditableCourseDraft = ReviewedEditableCourseDraft(),
    val units: List<ReviewedEditableUnitDraft> = emptyList(),
)

data class ReviewedEditableCourseDraft(
    val title: String = "",
    val key: String = "",
    val keySource: ReviewedStableKeySource = ReviewedStableKeySource.GENERATED,
    val sourceLine: Int = 0,
)

data class ReviewedEditableUnitDraft(
    val title: String = "",
    val key: String = "",
    val sourceRef: String = "",
    val keySource: ReviewedStableKeySource = ReviewedStableKeySource.GENERATED,
    val sourceLine: Int = 0,
    val questions: List<ReviewedEditableQuestionDraft> = emptyList(),
)

data class ReviewedEditableQuestionDraft(
    val key: String = "",
    val stem: String = "",
    val format: ReviewedQuestionFormat = ReviewedQuestionFormat.REVEAL_ANSWER,
    val studyRole: ReviewedQuestionStudyRole = ReviewedQuestionStudyRole.NORMAL,
    val options: List<ReviewedEditableOptionDraft> = emptyList(),
    val correctAnswer: String = "",
    val feedback: String = "",
    val correctedConfusion: String = "",
    val keySource: ReviewedStableKeySource = ReviewedStableKeySource.GENERATED,
    val sourceSpan: SourceTextSpan = SourceTextSpan(),
    val feedbackGenerated: Boolean = false,
    val roleInferredFromKeyword: Boolean = false,
    val boundaryWeak: Boolean = false,
)

data class ReviewedEditableOptionDraft(
    val key: String = "",
    val text: String = "",
    val isCorrect: Boolean = false,
)

data class SourceTextSpan(
    val startLine: Int = 0,
    val endLine: Int = 0,
)

enum class ReviewedStableKeySource {
    EXPLICIT,
    GENERATED,
    GENERATED_DEDUPLICATED,
}

enum class ReviewedQuestionFormat(
    val label: String,
) {
    TRUE_FALSE("Verdadero/Falso"),
    MULTIPLE_CHOICE("Opción múltiple"),
    CHOOSE_FALSE_STATEMENT("Elegir la afirmación falsa"),
    REVEAL_ANSWER("Ver respuesta"),
}

enum class ReviewedQuestionStudyRole(
    val label: String,
) {
    NORMAL("Normal"),
    VARIANT("Variante"),
    TRAP("Trampa"),
    INTEGRATION("Integración"),
    DEEP("Profunda"),
}
