package com.estudio.antiprocrastinacion.app.data.importing

import com.estudio.antiprocrastinacion.app.domain.repository.ValidationMessage
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationReport
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableCourseDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableImportDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableOptionDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableQuestionDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableUnitDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedQuestionFormat
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedQuestionStudyRole
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedStableKeySource
import com.estudio.antiprocrastinacion.app.model.authoring.SourceTextSpan
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

data class SourceTextExtractionResult(
    val draft: ReviewedEditableImportDraft,
    val report: ValidationReport,
)

@Singleton
class NaturalTextDraftExtractor @Inject constructor() {
    fun extract(sourceText: String): SourceTextExtractionResult {
        val errors = mutableListOf<ValidationMessage>()
        val warnings = mutableListOf<ValidationMessage>()
        val lines = sourceText.toSourceLines()

        if (sourceText.isBlank()) {
            errors +=
                sourceError(
                    code = "source_text_blank",
                    line = 1,
                    message = "El texto fuente esta vacio.",
                    expected = "Un unico texto con Curso:, Unidad: y preguntas.",
                    actual = "Texto vacio.",
                    hint = "Pegá el curso completo con sus etiquetas antes de analizar.",
                )
        }

        val parser = SourceParser(lines, errors)
        val parsed = parser.parse()
        val mutableUnits = parsed.units.toMutableList()
        val sourceHasContent = sourceText.isNotBlank()
        val firstContentLine = lines.firstOrNull { it.trimmed.isNotBlank() }?.number ?: 1
        var courseTitle = parsed.courseTitle.orEmpty().trim()
        if (courseTitle.isBlank() && sourceHasContent) {
            courseTitle = sourceText.inferTitleFromContent(fallback = "Texto importado")
            warnings +=
                sourceWarning(
                    code = "course_inferred_from_text",
                    line = firstContentLine,
                    message = "No habia Curso: explicito; se infirio un curso desde el texto.",
                    expected = "Curso: nombre visible del curso.",
                    actual = courseTitle,
                    hint = "Si queres controlar la identidad visible, agregá Curso: al inicio del texto.",
                )
        }
        if (mutableUnits.isEmpty() && sourceHasContent) {
            val unitTitle = sourceText.inferTitleFromContent(fallback = "Contenido importado")
            mutableUnits +=
                MutableUnitBlock(
                    title = unitTitle,
                    line = firstContentLine,
                    sourceRef = parsed.globalSourceRef,
                )
            warnings +=
                sourceWarning(
                    code = "unit_inferred_from_text",
                    line = firstContentLine,
                    message = "No habia Unidad: explicita; se creo una unidad desde el texto.",
                    expected = "Unidad: nombre visible de la unidad.",
                    actual = unitTitle,
                    hint = "Si queres separar temas, agregá lineas Unidad: dentro del texto fuente.",
                )
        }
        if (mutableUnits.isNotEmpty() && mutableUnits.none { it.questions.isNotEmpty() } && sourceHasContent) {
            val paragraphQuestions = lines.toFallbackParagraphQuestions()
            if (paragraphQuestions.isNotEmpty()) {
                mutableUnits.first().questions += paragraphQuestions
                warnings +=
                    sourceWarning(
                        code = "questions_generated_from_paragraphs",
                        line = paragraphQuestions.first().sourceSpan.startLine,
                        message = "No se detectaron bloques de pregunta; se generaron tarjetas desde parrafos reales.",
                        expected = "Preguntas marcadas o texto argumentativo separable en puntos.",
                        actual = "${paragraphQuestions.size} parrafos convertidos en preguntas de ver respuesta.",
                        hint = "Revisá la vista previa; si una tarjeta mezcla ideas, separá el texto con lineas en blanco.",
                    )
            }
        }
        val courseKey =
            parsed.courseKey?.trim().orEmpty().ifBlank {
                if (courseTitle.isNotBlank()) {
                    warnings +=
                        sourceWarning(
                            code = "course_id_generated",
                            line = parsed.courseLine.coerceAtLeast(1),
                            message = "El curso no trae ID curso explicito.",
                            expected = "ID curso: id_estable",
                            actual = "No se encontro ID curso.",
                            hint = "El ID se genero desde el titulo. Si el titulo cambia, agregá ID curso para conservar identidad.",
                        )
                }
                courseTitle.slugKey("curso")
            }
        val courseKeySource =
            if (parsed.courseKey.isNullOrBlank()) {
                ReviewedStableKeySource.GENERATED
            } else {
                ReviewedStableKeySource.EXPLICIT
            }

        if (courseTitle.isBlank()) {
            errors +=
                sourceError(
                    code = "source_course_missing",
                    line = 1,
                    message = "Falta la etiqueta Curso: o Materia:.",
                    expected = "Curso: nombre visible del curso.",
                    actual = "No se detecto curso.",
                    hint = "Agregá una linea Curso: antes de las unidades.",
                )
        }
        if (parsed.courseCount > 1) {
            errors +=
                sourceError(
                    code = "multiple_courses_not_supported",
                    line = parsed.extraCourseLines.firstOrNull() ?: parsed.courseLine.coerceAtLeast(1),
                    message = "El importador editable acepta un solo curso por texto.",
                    expected = "Un unico Curso: por carga.",
                    actual = "${parsed.courseCount} cursos detectados.",
                    hint = "Separá cursos distintos en cargas distintas.",
                )
        }
        validateExplicitId(parsed.courseKey, parsed.courseKeyLine, errors)

        val units = finalizeUnits(mutableUnits, warnings, errors)
        if (units.isEmpty()) {
            errors +=
                sourceError(
                    code = "source_unit_missing",
                    line = 1,
                    message = "Falta la etiqueta Unidad:, Tema:, Modulo: o Seccion:.",
                    expected = "Unidad: nombre visible de la unidad.",
                    actual = "No se detecto ninguna unidad.",
                    hint = "Cada pregunta debe quedar debajo de una unidad explicita.",
                )
        }

        return SourceTextExtractionResult(
            draft =
                ReviewedEditableImportDraft(
                    course =
                        ReviewedEditableCourseDraft(
                            title = courseTitle,
                            key = courseKey,
                            keySource = courseKeySource,
                            sourceLine = parsed.courseLine,
                        ),
                    units = units,
                ),
            report = ValidationReport(errors, warnings),
        )
    }

    private fun finalizeUnits(
        units: List<MutableUnitBlock>,
        warnings: MutableList<ValidationMessage>,
        errors: MutableList<ValidationMessage>,
    ): List<ReviewedEditableUnitDraft> {
        val explicitUnitKeys = units.mapNotNull { unit -> unit.key?.takeIf { it.isNotBlank() } }
        explicitUnitKeys.duplicates().forEach { key ->
            errors +=
                sourceError(
                    code = "explicit_id_duplicate:$key",
                    line = units.firstOrNull { it.key == key }?.keyLine ?: 1,
                    message = "Hay unidades con el mismo ID explicito: $key.",
                    expected = "Cada ID unidad debe ser unico dentro del curso.",
                    actual = key,
                    hint = "Cambiá uno de los ID unidad antes de aprobar.",
                )
        }

        val usedUnitKeys = mutableSetOf<String>()
        return units.mapIndexed { unitIndex, unit ->
            validateExplicitId(unit.key, unit.keyLine, errors)
            val baseKey = unit.key?.trim().orEmpty().ifBlank { unit.title.slugKey("unidad_${unitIndex + 1}") }
            val keySource = if (unit.key.isNullOrBlank()) ReviewedStableKeySource.GENERATED else ReviewedStableKeySource.EXPLICIT
            val dedupedUnitKey = dedupeKey(baseKey, usedUnitKeys)
            val finalUnitKeySource =
                if (dedupedUnitKey != baseKey && keySource == ReviewedStableKeySource.GENERATED) {
                    ReviewedStableKeySource.GENERATED_DEDUPLICATED
                } else {
                    keySource
                }
            if (keySource == ReviewedStableKeySource.GENERATED) {
                warnings +=
                    sourceWarning(
                        code = "unit_id_generated",
                        line = unit.line,
                        message = "La unidad '${unit.title}' no trae ID unidad explicito.",
                        expected = "ID unidad: id_estable",
                        actual = "No se encontro ID unidad.",
                        hint = "El ID se genero desde el titulo. Agregá ID unidad si esta unidad se va a reimportar.",
                    )
            }
            if (finalUnitKeySource == ReviewedStableKeySource.GENERATED_DEDUPLICATED) {
                warnings +=
                    sourceWarning(
                        code = "unit_id_deduplicated",
                        line = unit.line,
                        message = "Se ajusto un ID unidad generado para evitar duplicados.",
                        expected = "IDs de unidad unicos.",
                        actual = baseKey,
                        hint = "Agregá ID unidad explicito para controlar la identidad.",
                    )
            }
            val questions = finalizeQuestions(unit, warnings, errors)
            if (questions.size > 30) {
                warnings +=
                    sourceWarning(
                        code = "many_questions_single_unit",
                        line = unit.line,
                        message = "La unidad '${unit.title}' tiene muchas preguntas.",
                        expected = "Una unidad mantenible y revisable.",
                        actual = "${questions.size} preguntas.",
                        hint = "Si mezcla temas, dividila con otra etiqueta Unidad:.",
                    )
            }
            ReviewedEditableUnitDraft(
                title = unit.title,
                key = dedupedUnitKey,
                sourceRef = unit.sourceRef,
                keySource = finalUnitKeySource,
                sourceLine = unit.line,
                questions = questions,
            )
        }
    }

    private fun finalizeQuestions(
        unit: MutableUnitBlock,
        warnings: MutableList<ValidationMessage>,
        errors: MutableList<ValidationMessage>,
    ): List<ReviewedEditableQuestionDraft> {
        val explicitKeys =
            unit.questions.mapNotNull { question ->
                question.key.takeIf { question.keySource == ReviewedStableKeySource.EXPLICIT && it.isNotBlank() }
            }
        explicitKeys.duplicates().forEach { key ->
            errors +=
                sourceError(
                    code = "explicit_id_duplicate:$key",
                    line = unit.questions.firstOrNull { it.key == key }?.sourceSpan?.startLine ?: unit.line,
                    message = "Hay preguntas con el mismo ID explicito: $key.",
                    expected = "Cada [id: ...] debe ser unico dentro de la unidad.",
                    actual = key,
                    hint = "Cambiá uno de los IDs o separá la pregunta en otra unidad.",
                )
        }
        val generatedKeyQuestions =
            unit.questions.filter { question ->
                question.keySource != ReviewedStableKeySource.EXPLICIT || question.key.isBlank()
            }
        if (generatedKeyQuestions.isNotEmpty()) {
            warnings +=
                sourceWarning(
                    code = "question_id_generated_from_stem",
                    line = generatedKeyQuestions.first().sourceSpan.startLine,
                    message = "Se generaron IDs desde el enunciado para ${generatedKeyQuestions.size} preguntas.",
                    expected = "[id: id_estable] en cada pregunta que deba conservar identidad al editarse.",
                    actual = "${generatedKeyQuestions.size} preguntas sin [id: ...].",
                    hint = "La importacion funciona; agregá [id: ...] solo si vas a reimportar tras editar enunciados.",
                )
        }
        val mechanicalFeedbackQuestions = unit.questions.filter { it.feedbackGenerated }
        if (mechanicalFeedbackQuestions.isNotEmpty()) {
            warnings +=
                sourceWarning(
                    code = "feedback_generated_mechanical",
                    line = mechanicalFeedbackQuestions.first().sourceSpan.startLine,
                    message = "Se genero feedback mecanico para ${mechanicalFeedbackQuestions.size} preguntas.",
                    expected = "Explicacion: motivo breve cuando exista.",
                    actual = "${mechanicalFeedbackQuestions.size} preguntas sin explicacion explicita.",
                    hint = "La importacion funciona; agregá Explicacion: si queres feedback pedagogico mas preciso.",
                )
        }

        val usedQuestionKeys = mutableSetOf<String>()
        return unit.questions.mapIndexed { index, parsed ->
            validateExplicitId(parsed.key.takeIf { parsed.keySource == ReviewedStableKeySource.EXPLICIT }, parsed.sourceSpan.startLine, errors)
            val baseKey = parsed.key.trim().ifBlank { parsed.stem.slugKey("pregunta_${index + 1}") }
            val keySource = if (parsed.keySource == ReviewedStableKeySource.EXPLICIT && parsed.key.isNotBlank()) ReviewedStableKeySource.EXPLICIT else ReviewedStableKeySource.GENERATED
            val dedupedKey = dedupeKey(baseKey, usedQuestionKeys)
            val finalKeySource =
                if (dedupedKey != baseKey && keySource == ReviewedStableKeySource.GENERATED) {
                    ReviewedStableKeySource.GENERATED_DEDUPLICATED
                } else {
                    keySource
                }
            if (finalKeySource == ReviewedStableKeySource.GENERATED_DEDUPLICATED) {
                warnings +=
                    sourceWarning(
                        code = "question_id_deduplicated",
                        line = parsed.sourceSpan.startLine,
                        message = "Se ajusto un ID de pregunta generado para evitar duplicados.",
                        expected = "IDs de pregunta unicos dentro de la unidad.",
                        actual = baseKey,
                        hint = "Agregá [id: ...] explicito para controlar la identidad.",
                    )
            }
            if (parsed.boundaryWeak) {
                warnings +=
                    sourceWarning(
                        code = "weak_question_boundary_detected",
                        line = parsed.sourceSpan.startLine,
                        message = "La pregunta se detecto por una linea interrogativa dentro de una zona evaluable.",
                        expected = "Marcador fuerte como Pregunta: o --- PREGUNTA ---.",
                        actual = parsed.stem,
                        hint = "Para evitar falsos positivos, conviene marcarla con Pregunta: o [id: ...].",
                    )
            }
            parsed.copy(
                key = dedupedKey,
                keySource = finalKeySource,
            )
        }
    }
}

private class SourceParser(
    private val lines: List<SourceLine>,
    private val errors: MutableList<ValidationMessage>,
) {
    private var courseTitle: String? = null
    private var courseLine: Int = 0
    private var courseKey: String? = null
    private var courseKeyLine: Int = 0
    private var courseCount: Int = 0
    private val extraCourseLines = mutableListOf<Int>()
    private val units = mutableListOf<MutableUnitBlock>()
    private var currentUnit: MutableUnitBlock? = null
    private var currentQuestion: MutableQuestionBlock? = null
    private var globalSourceRef: String = ""
    private var inEvaluableZone: Boolean = false

    fun parse(): ParsedSource {
        lines.forEachIndexed { index, line ->
            val nextLines = lines.drop(index + 1).take(8)
            processLine(line, nextLines)
        }
        flushQuestion()
        return ParsedSource(
            courseTitle = courseTitle,
            courseLine = courseLine,
            courseKey = courseKey,
            courseKeyLine = courseKeyLine,
            courseCount = courseCount,
            extraCourseLines = extraCourseLines.toList(),
            globalSourceRef = globalSourceRef,
            units = units.toList(),
        )
    }

    private fun processLine(
        line: SourceLine,
        nextLines: List<SourceLine>,
    ) {
        if (line.trimmed.isBlank()) {
            currentQuestion?.lines?.add(line)
            return
        }
        if (line.isQuestionEnd()) {
            flushQuestion()
            return
        }

        val metadata = line.metadata()
        if (metadata != null) {
            if (currentQuestion != null && metadata.kind != MetadataKind.UNIT_TITLE) {
                errors +=
                    sourceError(
                        code = "metadata_inside_question_conflict",
                        line = line.number,
                        message = "Hay metadata global dentro de una pregunta.",
                        expected = "Metadata fuera de bloques de pregunta.",
                        actual = line.trimmed,
                        hint = "Cerrá la pregunta con --- FIN --- antes de declarar metadata.",
                    )
                currentQuestion?.lines?.add(line)
                return
            }
            handleMetadata(metadata, line)
            return
        }

        if (line.isEvaluableZoneMarker()) {
            flushQuestion()
            inEvaluableZone = true
            return
        }

        val start = line.questionStart(nextLines, inEvaluableZone)
        if (start != null) {
            if (currentQuestion != null && !line.isQuestionDelimiter() && currentQuestion?.hasStemOrEvidence() == false) {
                currentQuestion?.lines?.add(line)
            } else {
                startQuestion(line, start)
            }
            return
        }

        currentQuestion?.lines?.add(line)
    }

    private fun handleMetadata(
        metadata: Metadata,
        line: SourceLine,
    ) {
        when (metadata.kind) {
            MetadataKind.COURSE_TITLE -> {
                flushQuestion()
                courseCount += 1
                if (courseTitle == null) {
                    courseTitle = metadata.value
                    courseLine = line.number
                } else {
                    extraCourseLines += line.number
                }
            }

            MetadataKind.COURSE_KEY -> {
                flushQuestion()
                courseKey = metadata.value.trim()
                courseKeyLine = line.number
            }

            MetadataKind.UNIT_TITLE -> {
                flushQuestion()
                currentUnit =
                    MutableUnitBlock(
                        title = metadata.value,
                        line = line.number,
                        sourceRef = globalSourceRef,
                    ).also { units += it }
                inEvaluableZone = false
            }

            MetadataKind.UNIT_KEY -> {
                flushQuestion()
                val unit = currentUnit
                if (unit == null) {
                    errors +=
                        sourceError(
                            code = "unit_id_without_unit",
                            line = line.number,
                            message = "Hay ID unidad antes de declarar una unidad.",
                            expected = "Unidad: antes de ID unidad:.",
                            actual = line.trimmed,
                            hint = "Mové el ID unidad debajo de su Unidad:.",
                        )
                } else {
                    unit.key = metadata.value.trim()
                    unit.keyLine = line.number
                }
            }

            MetadataKind.SOURCE_REF -> {
                flushQuestion()
                val unit = currentUnit
                if (unit == null) {
                    globalSourceRef = metadata.value
                } else {
                    unit.sourceRef = metadata.value
                }
            }
        }
    }

    private fun startQuestion(
        line: SourceLine,
        start: QuestionStart,
    ) {
        if (currentUnit == null) {
            currentUnit =
                MutableUnitBlock(
                    title = "Contenido importado",
                    line = line.number,
                    sourceRef = globalSourceRef,
                ).also { units += it }
        }
        flushQuestion()
        currentQuestion =
            MutableQuestionBlock(
                startLine = line.number,
                weakBoundary = start.weak,
            ).also { block ->
                if (start.includeLine) {
                    block.lines += line
                }
            }
    }

    private fun flushQuestion() {
        val block = currentQuestion ?: return
        val unit = currentUnit
        currentQuestion = null
        if (unit == null) return
        val parsed = block.toDraftQuestion(errors) ?: return
        unit.questions += parsed
    }
}

private fun MutableQuestionBlock.toDraftQuestion(errors: MutableList<ValidationMessage>): ReviewedEditableQuestionDraft? {
    val meaningfulLines = lines.filterNot { it.trimmed.isBlank() || it.isQuestionDelimiter() || it.isQuestionEnd() }
    if (meaningfulLines.isEmpty()) return null

    val explicitKeyLine = meaningfulLines.firstNotNullOfOrNull { line -> line.questionIdValue()?.let { line to it } }
    val options = meaningfulLines.mapNotNull(SourceLine::option)
    if (options.count { it.markedCorrect } > 1) {
        errors +=
            sourceError(
                code = "multiple_marked_correct_options",
                line = startLine,
                message = "Hay mas de una opcion marcada como correcta.",
                expected = "Una sola opcion con * o marca visual.",
                actual = "${options.count { it.markedCorrect }} opciones marcadas.",
                hint = "Dejá una sola opcion correcta.",
            )
    }
    val answerLine = meaningfulLines.firstNotNullOfOrNull { line -> line.answerValue()?.let { line to it } }
    val feedbackLine = meaningfulLines.firstNotNullOfOrNull { line -> line.feedbackValue()?.let { line to it } }
    val roleEvidence = meaningfulLines.firstNotNullOfOrNull(SourceLine::roleEvidence)
    val stem = meaningfulLines.firstNotNullOfOrNull { line -> line.stemCandidate() }.orEmpty()
    val resolved = resolveOptionsAndAnswer(stem, options, answerLine?.second, startLine, errors)
    val format = inferFormat(stem, resolved.options, resolved.correctAnswer)
    val role = roleEvidence?.role ?: ReviewedQuestionStudyRole.NORMAL
    val feedbackGenerated = feedbackLine == null && resolved.correctAnswer.isNotBlank()
    val feedback =
        feedbackLine?.second?.trim().orEmpty().ifBlank {
            if (feedbackGenerated) {
                "Respuesta correcta: ${resolved.correctAnswer.trim()}."
            } else {
                ""
            }
        }
    val correctedConfusion = roleEvidence?.confusion.orEmpty()
    if (format == ReviewedQuestionFormat.TRUE_FALSE && resolved.options.isNotEmpty()) {
        errors +=
            sourceError(
                code = "true_false_with_options_conflict",
                line = startLine,
                message = "Una pregunta Verdadero/Falso no debe tener opciones.",
                expected = "Respuesta: Verdadero/Falso sin opciones.",
                actual = "${resolved.options.size} opciones detectadas.",
                hint = "Borrá las opciones o convertí la respuesta en opcion multiple.",
            )
    }

    return ReviewedEditableQuestionDraft(
        key = explicitKeyLine?.second.orEmpty(),
        stem = stem,
        format = format,
        studyRole = role,
        options =
            if (format == ReviewedQuestionFormat.MULTIPLE_CHOICE || format == ReviewedQuestionFormat.CHOOSE_FALSE_STATEMENT) {
                resolved.options
            } else {
                emptyList()
            },
        correctAnswer = normalizedCorrectAnswer(format, resolved.correctAnswer),
        feedback = feedback,
        correctedConfusion = correctedConfusion,
        keySource = if (explicitKeyLine == null) ReviewedStableKeySource.GENERATED else ReviewedStableKeySource.EXPLICIT,
        sourceSpan = SourceTextSpan(startLine = startLine, endLine = lines.lastOrNull()?.number ?: startLine),
        feedbackGenerated = feedbackGenerated,
        roleInferredFromKeyword = roleEvidence != null && roleEvidence.fromKeyword,
        boundaryWeak = weakBoundary,
    )
}

private fun resolveOptionsAndAnswer(
    stem: String,
    parsedOptions: List<ParsedOption>,
    explicitAnswer: String?,
    line: Int,
    errors: MutableList<ValidationMessage>,
): ResolvedAnswer {
    if (parsedOptions.isEmpty()) {
        if (explicitAnswer.isNullOrBlank()) {
            errors +=
                sourceError(
                    code = "question_correct_missing",
                    line = line,
                    message = "La pregunta no tiene Respuesta: ni Correcta:.",
                    expected = "Respuesta correcta explicita.",
                    actual = "No se encontro respuesta.",
                    hint = "Agregá Respuesta: ... dentro del bloque.",
                )
        }
        return ResolvedAnswer(emptyList(), explicitAnswer.orEmpty().trim())
    }

    if (parsedOptions.size !in 2..6) {
        errors +=
            sourceError(
                code = "choice_option_count_invalid",
                line = line,
                message = "La pregunta de opciones debe tener entre 2 y 6 opciones.",
                expected = "2 a 6 opciones A-F.",
                actual = "${parsedOptions.size} opciones.",
                hint = "Usá A) ... hasta F) ... y eliminá opciones vacias.",
            )
    }
    if (parsedOptions.any { it.text.isBlank() }) {
        errors +=
            sourceError(
                code = "choice_option_blank",
                line = line,
                message = "Hay una opcion vacia.",
                expected = "Cada opcion debe tener texto.",
                actual = "Opcion sin texto.",
                hint = "Completá o eliminá la opcion.",
            )
    }

    val marked = parsedOptions.filter { it.markedCorrect }
    val answerMatch = explicitAnswer?.let { answer -> parsedOptions.resolveAnswer(answer) }
    if (!explicitAnswer.isNullOrBlank() && answerMatch == null) {
        errors +=
            sourceError(
                code = "choice_answer_text_no_match",
                line = line,
                message = "La respuesta correcta no coincide con ninguna opcion.",
                expected = "Letra A-F o texto exacto de una opcion.",
                actual = explicitAnswer,
                hint = "Usá Correcta: B o copiá exactamente el texto de la opcion.",
            )
    }
    if (marked.size == 1 && answerMatch != null && marked.single().key != answerMatch.key) {
        errors +=
            sourceError(
                code = "choice_correct_conflict",
                line = line,
                message = "La marca visual y Correcta: apuntan a opciones distintas.",
                expected = "La marca * o visual debe coincidir con Correcta:.",
                actual = "Marca ${marked.single().key}, respuesta ${answerMatch.key}.",
                hint = "Dejá una sola evidencia de respuesta o hacelas coincidir.",
            )
    }
    if (marked.isEmpty() && answerMatch == null) {
        errors +=
            sourceError(
                code = "question_correct_missing",
                line = line,
                message = "La pregunta de opciones no tiene opcion correcta resoluble.",
                expected = "Correcta: letra/texto o una opcion marcada.",
                actual = "No se encontro opcion correcta.",
                hint = "Agregá Correcta: A-F o marcá una opcion con *.",
            )
    }

    val correctKey = answerMatch?.key ?: marked.singleOrNull()?.key
    val draftOptions =
        parsedOptions.map { option ->
            ReviewedEditableOptionDraft(
                key = option.key,
                text = option.text,
                isCorrect = option.key == correctKey,
            )
        }
    val correctAnswer = parsedOptions.firstOrNull { it.key == correctKey }?.text ?: explicitAnswer.orEmpty()
    return ResolvedAnswer(draftOptions, correctAnswer)
}

private fun List<ParsedOption>.resolveAnswer(answer: String): ParsedOption? {
    val cleaned = answer.trim()
    val letter = Regex("^([A-Fa-f])(?:[\\).])?$").matchEntire(cleaned)?.groupValues?.get(1)?.uppercase()
    if (letter != null) return firstOrNull { it.key == letter }
    val normalized = cleaned.normalizedValue()
    return singleOrNull { option -> option.text.normalizedValue() == normalized }
}

private fun normalizedCorrectAnswer(
    format: ReviewedQuestionFormat,
    correctAnswer: String,
): String =
    if (format == ReviewedQuestionFormat.TRUE_FALSE) {
        when (correctAnswer.normalizeTrueFalseAnswer()) {
            true -> "Verdadero"
            false -> "Falso"
            null -> correctAnswer.trim()
        }
    } else {
        correctAnswer.trim()
    }

private fun inferFormat(
    stem: String,
    options: List<ReviewedEditableOptionDraft>,
    correctAnswer: String,
): ReviewedQuestionFormat =
    when {
        options.isEmpty() && correctAnswer.normalizeTrueFalseAnswer() != null -> ReviewedQuestionFormat.TRUE_FALSE
        options.isNotEmpty() && stem.containsFalsePrompt() -> ReviewedQuestionFormat.CHOOSE_FALSE_STATEMENT
        options.isNotEmpty() -> ReviewedQuestionFormat.MULTIPLE_CHOICE
        else -> ReviewedQuestionFormat.REVEAL_ANSWER
    }

private fun SourceLine.stemCandidate(): String? {
    questionLabelValue()?.let { return it }
    numberedQuestionValue()?.let { return it }
    if (isFormatMarker() || option() != null || answerValue() != null || feedbackValue() != null || roleEvidence() != null || questionIdValue() != null) {
        return null
    }
    if (metadata() != null || isEvaluableZoneMarker() || isQuestionDelimiter() || isQuestionEnd()) {
        return null
    }
    return trimmed.takeIf(String::isNotBlank)
}

private fun SourceLine.questionStart(
    nextLines: List<SourceLine>,
    inEvaluableZone: Boolean,
): QuestionStart? =
    when {
        isQuestionDelimiter() -> QuestionStart(includeLine = false, weak = false)
        hasLabel(QuestionLabels) -> QuestionStart(includeLine = true, weak = false)
        numberedQuestionValue() != null -> QuestionStart(includeLine = true, weak = false)
        isFormatMarker() && nextLines.hasQuestionEvidence() -> QuestionStart(includeLine = true, weak = false)
        inEvaluableZone && trimmed.contains("?") && !isNonQuestionLabel() && nextLines.hasQuestionEvidence() ->
            QuestionStart(includeLine = true, weak = true)

        else -> null
    }

private fun List<SourceLine>.hasQuestionEvidence(): Boolean =
    takeWhile { !it.isQuestionEnd() && it.metadata()?.kind != MetadataKind.UNIT_TITLE }
        .take(6)
        .any { line -> line.option() != null || line.answerValue() != null || line.feedbackValue() != null }

private fun SourceLine.metadata(): Metadata? {
    val value = labelValue(CourseTitleLabels)
    if (value != null) return Metadata(MetadataKind.COURSE_TITLE, value)
    val courseKey = labelValue(CourseKeyLabels)
    if (courseKey != null) return Metadata(MetadataKind.COURSE_KEY, courseKey)
    val unitTitle = labelValue(UnitTitleLabels)
    if (unitTitle != null) return Metadata(MetadataKind.UNIT_TITLE, unitTitle)
    val unitKey = labelValue(UnitKeyLabels)
    if (unitKey != null) return Metadata(MetadataKind.UNIT_KEY, unitKey)
    val source = labelValue(SourceLabels)
    if (source != null) return Metadata(MetadataKind.SOURCE_REF, source)
    return null
}

private fun SourceLine.questionLabelValue(): String? =
    labelValue(QuestionLabels)

private fun SourceLine.questionIdValue(): String? {
    QuestionIdBracketRegex.matchEntire(trimmed)?.let { return it.groupValues[1].trim() }
    return labelValue(QuestionIdLabels)
}

private fun SourceLine.numberedQuestionValue(): String? =
    NumberedQuestionRegex.matchEntire(trimmed)?.groupValues?.get(1)?.trim()?.takeIf(String::isNotBlank)

private fun SourceLine.answerValue(): String? =
    labelValue(AnswerLabels)

private fun SourceLine.feedbackValue(): String? =
    labelValue(FeedbackLabels)

private fun SourceLine.roleEvidence(): RoleEvidence? {
    labelValue(listOf("rol"))?.let { value ->
        val normalized = value.folded()
        return when {
            normalized.contains("trampa") -> RoleEvidence(ReviewedQuestionStudyRole.TRAP, "", fromKeyword = false)
            normalized.contains("variante") -> RoleEvidence(ReviewedQuestionStudyRole.VARIANT, "", fromKeyword = false)
            normalized.contains("integracion") -> RoleEvidence(ReviewedQuestionStudyRole.INTEGRATION, "", fromKeyword = false)
            normalized.contains("profunda") -> RoleEvidence(ReviewedQuestionStudyRole.DEEP, "", fromKeyword = false)
            else -> null
        }
    }
    labelValue(listOf("trampa", "error tipico", "confusion", "no confundir"))?.let { value ->
        return RoleEvidence(ReviewedQuestionStudyRole.TRAP, value, fromKeyword = true)
    }
    labelValue(listOf("variante", "diferencia", "distingui", "distingui", "compara", "compara"))?.let {
        return RoleEvidence(ReviewedQuestionStudyRole.VARIANT, "", fromKeyword = true)
    }
    labelValue(listOf("integracion", "conecta", "relaciona"))?.let {
        return RoleEvidence(ReviewedQuestionStudyRole.INTEGRATION, "", fromKeyword = true)
    }
    labelValue(listOf("profunda", "desarrollo", "reconstruccion", "explica completo", "explica completo"))?.let {
        return RoleEvidence(ReviewedQuestionStudyRole.DEEP, "", fromKeyword = true)
    }
    return null
}

private fun SourceLine.option(): ParsedOption? {
    val match = OptionRegex.matchEntire(trimmed) ?: return null
    val marker = match.groupValues[1]
    val key = match.groupValues[2].uppercase()
    val text = match.groupValues[3].trim()
    return ParsedOption(
        key = key,
        text = text,
        markedCorrect = marker == "*" || marker == "✅",
    )
}

private fun SourceLine.labelValue(labels: List<String>): String? {
    val separator = trimmed.indexOf(':')
    if (separator <= 0) return null
    val label = trimmed.take(separator).trim().folded()
    return if (label in labels) {
        trimmed.substring(separator + 1).trim().takeIf(String::isNotBlank)
    } else {
        null
    }
}

private fun SourceLine.hasLabel(labels: List<String>): Boolean {
    val separator = trimmed.indexOf(':')
    if (separator <= 0) return false
    val label = trimmed.take(separator).trim().folded()
    return label in labels
}

private fun SourceLine.isQuestionDelimiter(): Boolean =
    folded == "--- pregunta ---"

private fun SourceLine.isQuestionEnd(): Boolean =
    folded == "--- fin ---"

private fun SourceLine.isEvaluableZoneMarker(): Boolean =
    folded.trim('#', '-', '*', ' ') in EvaluableZoneMarkers

private fun SourceLine.isFormatMarker(): Boolean =
    folded.trim().removePrefix("(").removeSuffix(")") in FormatMarkers

private fun SourceLine.isNonQuestionLabel(): Boolean =
    answerValue() != null || feedbackValue() != null || metadata() != null || option() != null

private fun validateExplicitId(
    key: String?,
    line: Int,
    errors: MutableList<ValidationMessage>,
) {
    if (key.isNullOrBlank()) return
    if (!StableKeyRegex.matches(key)) {
        errors +=
            sourceError(
                code = "explicit_id_invalid:$key",
                line = line.coerceAtLeast(1),
                message = "El ID explicito '$key' no respeta el formato permitido.",
                expected = "Solo minusculas sin acentos, numeros y guiones bajos.",
                actual = key,
                hint = "Escribilo como ethereum_basico o pregunta_01.",
            )
    }
}

private fun dedupeKey(
    baseKey: String,
    usedKeys: MutableSet<String>,
): String {
    var candidate = baseKey
    var suffix = 2
    while (candidate in usedKeys) {
        candidate = "${baseKey}_$suffix"
        suffix += 1
    }
    usedKeys += candidate
    return candidate
}

internal fun String.slugKey(fallback: String): String {
    val normalized =
        Normalizer
            .normalize(trim().lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("ñ", "n")
            .replace("[^a-z0-9]+".toRegex(), "_")
            .trim('_')
            .replace("_+".toRegex(), "_")
    return normalized.ifBlank { fallback }.take(64).trim('_').ifBlank { fallback }
}

private fun String.containsFalsePrompt(): Boolean =
    folded().containsAny("falsa", "falso", "incorrecta", "incorrecto", "no corresponde", "no es correcta")

private fun String.containsAny(vararg needles: String): Boolean =
    needles.any { contains(it) }

private fun String.normalizedValue(): String =
    folded().replace("\\s+".toRegex(), " ").trim()

private fun String.folded(): String =
    Normalizer
        .normalize(trim().lowercase(), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .replace("ñ", "n")

private fun String.toSourceLines(): List<SourceLine> =
    replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .mapIndexed { index, raw ->
            SourceLine(
                number = index + 1,
                raw = raw,
                trimmed = raw.trim(),
                folded = raw.trim().folded(),
            )
        }

private fun List<SourceLine>.toFallbackParagraphQuestions(): List<ReviewedEditableQuestionDraft> {
    val paragraphs = mutableListOf<List<SourceLine>>()
    val current = mutableListOf<SourceLine>()

    fun flushParagraph() {
        if (current.isNotEmpty()) {
            paragraphs += current.toList()
            current.clear()
        }
    }

    forEach { line ->
        when {
            line.trimmed.isBlank() -> flushParagraph()
            line.metadata() != null || line.isEvaluableZoneMarker() || line.isQuestionDelimiter() || line.isQuestionEnd() ->
                flushParagraph()

            else -> current += line
        }
    }
    flushParagraph()

    return paragraphs
        .map { paragraphLines ->
            ParagraphBlock(
                text =
                    paragraphLines
                        .joinToString(" ") { it.trimmed }
                        .replace("\\s+".toRegex(), " ")
                        .trim(),
                startLine = paragraphLines.first().number,
                endLine = paragraphLines.last().number,
            )
        }.filter { it.text.length >= 24 }
        .mapIndexed { index, paragraph ->
            val answer = paragraph.text
            ReviewedEditableQuestionDraft(
                key = "",
                stem = "Punto ${index + 1}: ${answer.compactText(maxLength = 96)}",
                format = ReviewedQuestionFormat.REVEAL_ANSWER,
                studyRole = ReviewedQuestionStudyRole.NORMAL,
                correctAnswer = answer,
                feedback = "Respuesta correcta: ${answer.compactText(maxLength = 220)}.",
                keySource = ReviewedStableKeySource.GENERATED,
                sourceSpan = SourceTextSpan(startLine = paragraph.startLine, endLine = paragraph.endLine),
                feedbackGenerated = true,
            )
        }
}

private fun String.inferTitleFromContent(fallback: String): String {
    val words =
        folded()
            .split(Regex("[^a-z0-9]+"))
            .filter { word -> word.length >= 5 && word !in TitleStopWords }
    val keywordTitle =
        words
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(3)
            .joinToString(" ") { (word, _) -> word.replaceFirstChar { char -> char.titlecase() } }
    if (keywordTitle.isNotBlank()) return keywordTitle
    return lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        ?.compactText(maxLength = 56)
        .orEmpty()
        .ifBlank { fallback }
}

private fun String.compactText(maxLength: Int): String {
    val singleLine = replace("\\s+".toRegex(), " ").trim()
    if (singleLine.length <= maxLength) return singleLine
    return singleLine
        .take(maxLength - 3)
        .trimEnd(' ', ',', ';', ':', '.', '-')
        .plus("...")
}

private fun sourceError(
    code: String,
    line: Int,
    message: String,
    expected: String,
    actual: String,
    hint: String,
): ValidationMessage =
    ValidationMessage(
        code = code,
        message = message,
        path = "line:$line",
        expected = expected,
        actual = actual,
        hint = hint,
    )

private fun sourceWarning(
    code: String,
    line: Int,
    message: String,
    expected: String,
    actual: String,
    hint: String,
): ValidationMessage =
    ValidationMessage(
        code = code,
        message = message,
        path = "line:$line",
        expected = expected,
        actual = actual,
        hint = hint,
    )

private data class SourceLine(
    val number: Int,
    val raw: String,
    val trimmed: String,
    val folded: String,
)

private data class ParagraphBlock(
    val text: String,
    val startLine: Int,
    val endLine: Int,
)

private data class ParsedSource(
    val courseTitle: String?,
    val courseLine: Int,
    val courseKey: String?,
    val courseKeyLine: Int,
    val courseCount: Int,
    val extraCourseLines: List<Int>,
    val globalSourceRef: String,
    val units: List<MutableUnitBlock>,
)

private data class MutableUnitBlock(
    val title: String,
    val line: Int,
    var key: String? = null,
    var keyLine: Int = 0,
    var sourceRef: String = "",
    val questions: MutableList<ReviewedEditableQuestionDraft> = mutableListOf(),
)

private data class MutableQuestionBlock(
    val startLine: Int,
    val weakBoundary: Boolean,
    val lines: MutableList<SourceLine> = mutableListOf(),
) {
    fun hasStemOrEvidence(): Boolean =
        lines.any { line ->
            line.stemCandidate() != null ||
                line.option() != null ||
                line.answerValue() != null ||
                line.feedbackValue() != null
        }
}

private data class QuestionStart(
    val includeLine: Boolean,
    val weak: Boolean,
)

private data class Metadata(
    val kind: MetadataKind,
    val value: String,
)

private enum class MetadataKind {
    COURSE_TITLE,
    COURSE_KEY,
    UNIT_TITLE,
    UNIT_KEY,
    SOURCE_REF,
}

private data class ParsedOption(
    val key: String,
    val text: String,
    val markedCorrect: Boolean,
)

private data class ResolvedAnswer(
    val options: List<ReviewedEditableOptionDraft>,
    val correctAnswer: String,
)

private data class RoleEvidence(
    val role: ReviewedQuestionStudyRole,
    val confusion: String,
    val fromKeyword: Boolean,
)

private val StableKeyRegex = Regex("[a-z0-9_]+")
private val NumberedQuestionRegex = Regex("^\\d+[\\).]\\s+(.+)$")
private val QuestionIdBracketRegex = Regex("^\\[\\s*id\\s*:\\s*([^\\]]+)\\s*\\]$", RegexOption.IGNORE_CASE)
private val OptionRegex = Regex("^\\s*(?:([-*✅])\\s*)?([A-Fa-f])[\\.)]\\s+(.+)$")

private val CourseTitleLabels = listOf("curso", "materia")
private val CourseKeyLabels = listOf("id curso", "course_id", "coursekey")
private val UnitTitleLabels = listOf("unidad", "tema", "modulo", "seccion")
private val UnitKeyLabels = listOf("id unidad", "unit_id", "unitkey", "section_id")
private val SourceLabels = listOf("fuente", "referencia", "source")
private val QuestionLabels = listOf("pregunta", "p", "q")
private val QuestionIdLabels = listOf("id", "id pregunta")
private val AnswerLabels = listOf("correcta", "respuesta", "respuesta correcta", "solucion", "answer", "correct answer")
private val FeedbackLabels = listOf("explicacion", "justificacion", "feedback", "porque", "por que")
private val EvaluableZoneMarkers = setOf("mini-examen", "examen", "preguntas", "evaluacion", "cuestionario", "responde")
private val FormatMarkers = setOf("opcion multiple", "verdadero/falso", "verdadero o falso", "elegir falsa", "ver respuesta")
private val TitleStopWords =
    setOf(
        "sobre",
        "porque",
        "seguro",
        "ciertos",
        "particulares",
        "total",
        "todos",
        "todas",
        "decir",
        "miden",
        "mirar",
        "agregados",
        "niveles",
        "permite",
        "cuando",
        "donde",
        "desde",
        "hasta",
        "entre",
        "tiene",
        "tienen",
        "estaba",
        "estan",
        "estos",
        "estas",
    )
