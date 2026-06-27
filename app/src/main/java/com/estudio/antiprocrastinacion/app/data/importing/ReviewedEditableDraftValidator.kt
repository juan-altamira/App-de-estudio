package com.estudio.antiprocrastinacion.app.data.importing

import com.estudio.antiprocrastinacion.app.domain.repository.ValidationMessage
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationReport
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableImportDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableQuestionDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedQuestionFormat
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedQuestionStudyRole
import javax.inject.Inject
import javax.inject.Singleton

private val StableKeyRegex = Regex("[a-z0-9_]+")

@Singleton
class ReviewedEditableDraftValidator @Inject constructor() {
    fun validate(draft: ReviewedEditableImportDraft): ValidationReport {
        val errors = mutableListOf<ValidationMessage>()
        val warnings = mutableListOf<ValidationMessage>()

        fun error(
            code: String,
            message: String,
            path: String,
            expected: String,
            actual: String,
            hint: String,
        ) {
            errors += ValidationMessage(code, message, path, expected, actual, hint)
        }

        fun warning(
            code: String,
            message: String,
            path: String,
            expected: String,
            actual: String,
            hint: String,
        ) {
            warnings += ValidationMessage(code, message, path, expected, actual, hint)
        }

        validateKey(
            codePrefix = "course_key",
            key = draft.course.key,
            path = "$.course.key",
            label = "ID estable del curso",
            errors = errors,
        )
        if (draft.course.title.isBlank()) {
            error(
                code = "course_title_blank",
                message = "El curso no tiene nombre.",
                path = "$.course.title",
                expected = "Nombre visible del curso.",
                actual = "Texto vacio.",
                hint = "Agregá una linea Curso: al texto fuente.",
            )
        }
        if (draft.units.isEmpty()) {
            error(
                code = "unit_list_empty",
                message = "No hay unidades para compilar.",
                path = "$.units",
                expected = "Al menos una Unidad: con preguntas.",
                actual = "Lista vacia.",
                hint = "Agregá Unidad: y preguntas debajo.",
            )
        }

        draft.units.map { it.key }.duplicates().forEach { key ->
            error(
                code = "unit_key_duplicate:$key",
                message = "Hay unidades con el mismo ID estable: $key.",
                path = "$.units",
                expected = "Cada unidad debe tener un ID unico dentro del curso.",
                actual = key,
                hint = "Agregá ID unidad explicito o cambiá un titulo duplicado.",
            )
        }

        draft.units.forEachIndexed { unitIndex, unit ->
            val unitPath = "$.units[$unitIndex]"
            validateKey(
                codePrefix = "unit_key",
                key = unit.key,
                path = "$unitPath.key",
                label = "ID estable de la unidad",
                errors = errors,
            )
            if (unit.title.isBlank()) {
                error(
                    code = "unit_title_blank:${unit.key.ifBlank { unitIndex.toString() }}",
                    message = "La unidad ${unit.key.ifBlank { "#${unitIndex + 1}" }} no tiene nombre.",
                    path = "$unitPath.title",
                    expected = "Nombre visible de la unidad.",
                    actual = "Texto vacio.",
                    hint = "Agregá Unidad: nombre de la unidad.",
                )
            }
            if (unit.sourceRef.isBlank()) {
                warning(
                    code = "source_ref_blank:${unit.key}",
                    message = "La unidad ${unit.title.ifBlank { unit.key }} no tiene fuente visible.",
                    path = "$unitPath.sourceRef",
                    expected = "Fuente: o Referencia: opcional.",
                    actual = "Texto vacio.",
                    hint = "Agregá Fuente: si queres rastrear el origen del banco.",
                )
            }
            if (unit.questions.isEmpty()) {
                error(
                    code = "question_list_empty:${unit.key.ifBlank { unitIndex.toString() }}",
                    message = "La unidad ${unit.title.ifBlank { unit.key }} no tiene preguntas.",
                    path = "$unitPath.questions",
                    expected = "Al menos una pregunta completa.",
                    actual = "Lista vacia.",
                    hint = "Agregá bloques Pregunta: debajo de la unidad.",
                )
            }

            val questionKeys = unit.questions.map { it.key }
            questionKeys.duplicates().forEach { key ->
                error(
                    code = "question_key_duplicate:$key",
                    message = "Hay preguntas con el mismo ID estable: $key.",
                    path = "$unitPath.questions",
                    expected = "Cada pregunta debe tener un ID unico dentro de la unidad.",
                    actual = key,
                    hint = "Agregá [id: ...] diferente; el ID preserva identidad y progreso.",
                )
            }

            val quickQuestionCount =
                unit.questions.count { question ->
                    question.format in setOf(
                        ReviewedQuestionFormat.TRUE_FALSE,
                        ReviewedQuestionFormat.MULTIPLE_CHOICE,
                        ReviewedQuestionFormat.CHOOSE_FALSE_STATEMENT,
                    ) &&
                        question.studyRole !in setOf(
                            ReviewedQuestionStudyRole.INTEGRATION,
                            ReviewedQuestionStudyRole.DEEP,
                        )
                }
            if (unit.questions.isNotEmpty() && quickQuestionCount == 0) {
                warning(
                    code = "no_quick_questions:${unit.key}",
                    message = "La unidad no tiene preguntas rapidas para entrada liviana.",
                    path = "$unitPath.questions",
                    expected = "Al menos una pregunta de opcion fija y baja friccion.",
                    actual = "0 preguntas rapidas.",
                    hint = "Agregá Verdadero/Falso u opcion multiple si queres que aparezca facil en entradas livianas.",
                )
            } else if (quickQuestionCount in 1..3) {
                warning(
                    code = "few_quick_questions:${unit.key}",
                    message = "La unidad tiene pocas preguntas rapidas.",
                    path = "$unitPath.questions",
                    expected = "4 o mas preguntas rapidas para superficies de baja friccion.",
                    actual = "$quickQuestionCount preguntas rapidas.",
                    hint = "Agregá mas preguntas de opcion fija si este tema se usara en Tarjetas pendientes o gate.",
                )
            }

            unit.questions.forEachIndexed { questionIndex, question ->
                val questionPath = "$unitPath.questions[$questionIndex]"
                validateQuestion(
                    unitKey = unit.key,
                    question = question,
                    index = questionIndex,
                    path = questionPath,
                    errors = errors,
                    warnings = warnings,
                )
            }
        }

        return ValidationReport(errors, warnings)
    }

    private fun validateQuestion(
        unitKey: String,
        question: ReviewedEditableQuestionDraft,
        index: Int,
        path: String,
        errors: MutableList<ValidationMessage>,
        warnings: MutableList<ValidationMessage>,
    ) {
        fun error(
            code: String,
            message: String,
            path: String,
            expected: String,
            actual: String,
            hint: String,
        ) {
            errors += ValidationMessage(code, message, path, expected, actual, hint)
        }

        fun warning(
            code: String,
            message: String,
            path: String,
            expected: String,
            actual: String,
            hint: String,
        ) {
            warnings += ValidationMessage(code, message, path, expected, actual, hint)
        }

        validateKey(
            codePrefix = "question_key",
            key = question.key,
            path = "$path.key",
            label = "ID estable de la pregunta",
            errors = errors,
        )
        if (question.stem.isBlank()) {
            error(
                code = "question_stem_blank:${question.key.ifBlank { "$unitKey:$index" }}",
                message = "La pregunta ${question.key.ifBlank { "#${index + 1}" }} no tiene enunciado.",
                path = "$path.stem",
                expected = "Enunciado visible de la pregunta.",
                actual = "Texto vacio.",
                hint = "Agregá Pregunta: con el enunciado.",
            )
        }
        if (question.correctAnswer.isBlank()) {
            error(
                code = "question_correct_missing:${question.key.ifBlank { "$unitKey:$index" }}",
                message = "La pregunta ${question.key.ifBlank { "#${index + 1}" }} no tiene respuesta correcta.",
                path = "$path.correctAnswer",
                expected = "Respuesta correcta explicita.",
                actual = "Texto vacio.",
                hint = "Agregá Respuesta: o Correcta: dentro del bloque.",
            )
        }
        if (question.feedback.isBlank()) {
            error(
                code = "question_feedback_blank:${question.key.ifBlank { "$unitKey:$index" }}",
                message = "La pregunta ${question.key.ifBlank { "#${index + 1}" }} no tiene explicacion.",
                path = "$path.feedback",
                expected = "Explicacion breve o feedback mecanico generado.",
                actual = "Texto vacio.",
                hint = "Agregá Explicacion: o reanalizá desde el texto fuente para generar feedback mecanico.",
            )
        }
        if (question.roleInferredFromKeyword) {
            warning(
                code = "role_inferred_from_keyword:${question.key}",
                message = "El rol de estudio de ${question.key} se tomo de una etiqueta del texto.",
                path = "$path.studyRole",
                expected = "Rol declarado de forma explicita.",
                actual = question.studyRole.label,
                hint = "Revisá que esa etiqueta describa realmente la funcion de la pregunta.",
            )
        }
        validateQuestionShape(question, index, path, errors)
        if (question.studyRole == ReviewedQuestionStudyRole.TRAP && question.correctedConfusion.isBlank()) {
            error(
                code = "trap_confusion_missing:${question.key.ifBlank { "$unitKey:$index" }}",
                message = "La trampa ${question.key.ifBlank { "#${index + 1}" }} no dice que confusion corrige.",
                path = "$path.correctedConfusion",
                expected = "Confusion conceptual explicita.",
                actual = "Texto vacio.",
                hint = "Agregá Confusion:, Error tipico: o Trampa: con texto.",
            )
        }
        if (question.studyRole == ReviewedQuestionStudyRole.INTEGRATION && isQuickFormat(question.format)) {
            warning(
                code = "integration_question_quick:${question.key}",
                message = "Una pregunta de integracion quedo en formato rapido.",
                path = "$path.format",
                expected = "Integracion en formato de mayor elaboracion.",
                actual = question.format.label,
                hint = "Usala solo si evalua una conexion puntual de baja friccion.",
            )
        }
        if (question.studyRole == ReviewedQuestionStudyRole.DEEP && isQuickFormat(question.format)) {
            warning(
                code = "deep_question_quick:${question.key}",
                message = "Una pregunta profunda quedo en formato rapido.",
                path = "$path.format",
                expected = "Pregunta profunda con respuesta a revelar y autoevaluacion.",
                actual = question.format.label,
                hint = "Cambiá el formato textual a Ver respuesta si requiere desarrollo mental.",
            )
        }
    }

    private fun validateQuestionShape(
        question: ReviewedEditableQuestionDraft,
        index: Int,
        path: String,
        errors: MutableList<ValidationMessage>,
    ) {
        fun error(
            code: String,
            message: String,
            path: String,
            expected: String,
            actual: String,
            hint: String,
        ) {
            errors += ValidationMessage(code, message, path, expected, actual, hint)
        }

        when (question.format) {
            ReviewedQuestionFormat.MULTIPLE_CHOICE,
            ReviewedQuestionFormat.CHOOSE_FALSE_STATEMENT,
            -> {
                val nonBlankOptions = question.options.filter { it.text.isNotBlank() }
                val correctCount = nonBlankOptions.count { it.isCorrect }
                if (nonBlankOptions.size !in 2..6) {
                    error(
                        code = "choice_option_count_invalid:${question.key.ifBlank { index.toString() }}",
                        message = "La pregunta ${question.key.ifBlank { "#${index + 1}" }} debe tener entre 2 y 6 opciones.",
                        path = "$path.options",
                        expected = "Entre 2 y 6 opciones no vacias.",
                        actual = "${nonBlankOptions.size} opciones no vacias.",
                        hint = "Usá opciones A) ... hasta F) ...",
                    )
                }
                if (correctCount != 1) {
                    error(
                        code = "choice_correct_count_invalid:${question.key.ifBlank { index.toString() }}",
                        message = "La pregunta ${question.key.ifBlank { "#${index + 1}" }} debe tener exactamente una opcion correcta.",
                        path = "$path.options",
                        expected = "Una sola opcion marcada como correcta.",
                        actual = "$correctCount opciones correctas.",
                        hint = "Usá Correcta: A-F o una unica marca visual.",
                    )
                }
            }

            ReviewedQuestionFormat.TRUE_FALSE -> {
                if (question.options.isNotEmpty()) {
                    error(
                        code = "true_false_with_options_conflict:${question.key.ifBlank { index.toString() }}",
                        message = "La pregunta ${question.key.ifBlank { "#${index + 1}" }} es Verdadero/Falso y no debe tener opciones.",
                        path = "$path.options",
                        expected = "Lista vacia.",
                        actual = "${question.options.size} opciones.",
                        hint = "Borrá las opciones o convertí la pregunta en opcion multiple.",
                    )
                }
                if (question.correctAnswer.normalizeTrueFalseAnswer() == null && question.correctAnswer.isNotBlank()) {
                    error(
                        code = "true_false_answer_invalid:${question.key.ifBlank { index.toString() }}",
                        message = "La respuesta de ${question.key.ifBlank { "#${index + 1}" }} debe ser verdadero o falso.",
                        path = "$path.correctAnswer",
                        expected = "Verdadero o Falso.",
                        actual = question.correctAnswer,
                        hint = "Escribí Respuesta: Verdadero o Respuesta: Falso.",
                    )
                }
            }

            ReviewedQuestionFormat.REVEAL_ANSWER -> {
                if (question.options.isNotEmpty()) {
                    error(
                        code = "reveal_answer_has_options:${question.key.ifBlank { index.toString() }}",
                        message = "La pregunta ${question.key.ifBlank { "#${index + 1}" }} usa Ver respuesta y no debe tener opciones.",
                        path = "$path.options",
                        expected = "Lista vacia.",
                        actual = "${question.options.size} opciones.",
                        hint = "Borrá las opciones o agregá Correcta: para convertirla en opcion fija.",
                    )
                }
            }
        }
    }
}

internal fun validateKey(
    codePrefix: String,
    key: String,
    path: String,
    label: String,
    errors: MutableList<ValidationMessage>,
) {
    when {
        key.isBlank() ->
            errors +=
                ValidationMessage(
                    code = "${codePrefix}_blank",
                    message = "$label no puede estar vacio.",
                    path = path,
                    expected = "Texto en minusculas con letras, numeros y guiones bajos.",
                    actual = "Texto vacio.",
                    hint = "Usá un ID estable como ethereum_basico o pregunta_01.",
                )

        !StableKeyRegex.matches(key) ->
            errors +=
                ValidationMessage(
                    code = "${codePrefix}_invalid:$key",
                    message = "$label '$key' no respeta el formato permitido.",
                    path = path,
                    expected = "Solo minusculas sin acentos, numeros y guiones bajos.",
                    actual = key,
                    hint = "Renombralo sin espacios, acentos, mayusculas ni signos.",
                )
    }
}

internal fun <T> List<T>.duplicates(): List<T> =
    groupingBy { it }
        .eachCount()
        .filterValues { count -> count > 1 }
        .keys
        .toList()

internal fun String.normalizeTrueFalseAnswer(): Boolean? =
    when (trim().lowercase()) {
        "true", "verdadero", "v", "si", "sí" -> true
        "false", "falso", "f", "no" -> false
        else -> null
    }

private fun isQuickFormat(format: ReviewedQuestionFormat): Boolean =
    format == ReviewedQuestionFormat.TRUE_FALSE ||
        format == ReviewedQuestionFormat.MULTIPLE_CHOICE ||
        format == ReviewedQuestionFormat.CHOOSE_FALSE_STATEMENT
