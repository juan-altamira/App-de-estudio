package com.estudio.antiprocrastinacion.app.data.importing

import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableImportDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableQuestionDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableUnitDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedQuestionFormat
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedQuestionStudyRole
import com.estudio.antiprocrastinacion.app.model.content.ContentOrigin
import com.estudio.antiprocrastinacion.app.model.content.FacetType
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.NodeType
import com.estudio.antiprocrastinacion.app.model.json.ContentPackageDto
import com.estudio.antiprocrastinacion.app.model.json.CourseDto
import com.estudio.antiprocrastinacion.app.model.json.ItemDto
import com.estudio.antiprocrastinacion.app.model.json.ItemOptionDto
import com.estudio.antiprocrastinacion.app.model.json.NodeDto
import com.estudio.antiprocrastinacion.app.model.json.OutcomeDto
import com.estudio.antiprocrastinacion.app.model.json.UnitDto
import com.estudio.antiprocrastinacion.app.ui.common.AppJson
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString

@Singleton
class ReviewedQuestionBankCompiler @Inject constructor() {
    fun compile(
        draft: ReviewedEditableImportDraft,
        now: Long,
    ): ContentPackageDto {
        val courseId = draft.course.key
        val compiledUnits = draft.units.mapIndexed { index, unit -> unit.compile(courseId, index, now) }
        val items = compiledUnits.flatMap(CompiledReviewedUnit::items)
        val packageWithoutHash =
            ContentPackageDto(
                packageId = "editable_$courseId",
                schemaVersion = 1,
                generatedAt = now,
                contentHash = null,
                origin = ContentOrigin.IMPORTED,
                courses =
                    listOf(
                        CourseDto(
                            courseId = courseId,
                            title = draft.course.title.trim(),
                            description = null,
                            version = 1,
                            updatedAt = now,
                        ),
                    ),
                units = compiledUnits.map(CompiledReviewedUnit::unit),
                outcomes = compiledUnits.map(CompiledReviewedUnit::outcome),
                nodes = compiledUnits.map(CompiledReviewedUnit::node),
                items = items,
            )
        return packageWithoutHash.copy(
            contentHash = stableHash(AppJson.encodeToString(packageWithoutHash.copy(contentHash = null))),
        )
    }
}

private fun ReviewedEditableUnitDraft.compile(
    courseId: String,
    orderIndex: Int,
    now: Long,
): CompiledReviewedUnit {
    val unitId = "${courseId}__${key}"
    val outcomeId = "${unitId}__resolver_preguntas"
    val nodeId = "${unitId}__contenido"
    val sourceRefs = listOfNotNull(sourceRef.trim().takeIf(String::isNotBlank))
    val mustKnowByQuestion =
        questions.associate { question ->
            question.key to question.mustKnowLine()
        }
    val facets =
        questions
            .map(ReviewedEditableQuestionDraft::compiledFacet)
            .distinct()
            .ifEmpty { listOf(FacetType.DEFINICION_FUNCIONAL) }
    val commonErrors =
        questions
            .filter { it.studyRole == ReviewedQuestionStudyRole.TRAP }
            .map { it.correctedConfusion.trim() }
            .filter(String::isNotBlank)
            .distinct()
    val items =
        questions.map { question ->
            val role = question.compiledRole()
            val format = question.compiledFormat()
            val friction = ContentImportRules.frictionFor(format)
            val commonErrorSignals =
                if (role == ItemRole.TRAP) {
                    listOf(question.correctedConfusion.trim()).filter(String::isNotBlank)
                } else {
                    emptyList()
                }
            ItemDto(
                itemId = "${nodeId}__${question.key}",
                nodeId = nodeId,
                facet = question.compiledFacet(),
                format = format,
                frictionLevel = friction,
                difficultySeed = question.difficultySeed(friction),
                itemRole = role,
                allowedSurfaces = ContentImportRules.allowedSurfacesFor(format, role),
                cooldownHours = ContentImportRules.defaultCooldownHoursFor(format, role),
                stem = question.stem.trim(),
                correctAnswer = question.compiledCorrectAnswer(),
                feedbackShort = question.feedback.trim(),
                coversMustKnow = listOfNotNull(mustKnowByQuestion[question.key]),
                variantGroupId = if (role == ItemRole.VARIANT) "${nodeId}__variant" else null,
                rescueGroupId = null,
                nodeComplexity = null,
                facetComplexity = null,
                distractorSimilarity = null,
                prerequisiteDepth = null,
                targetsErrorIds = emptyList(),
                commonErrorSignals = commonErrorSignals,
                options = question.compiledOptions(),
                version = 1,
                updatedAt = now,
                sourceRefs = sourceRefs,
            )
        }
    val strictEasyCount =
        items.count {
            ContentImportRules.countsAsRealSurfaceEasy(
                format = it.format,
                role = it.itemRole,
                frictionLevel = it.frictionLevel,
                allowedSurfaces = it.allowedSurfaces,
            )
        }
    return CompiledReviewedUnit(
        unit =
            UnitDto(
                unitId = unitId,
                courseId = courseId,
                title = title.trim(),
                description = sourceRef.trim().takeIf(String::isNotBlank),
                orderIndex = orderIndex,
                version = 1,
                updatedAt = now,
            ),
        outcome =
            OutcomeDto(
                outcomeId = outcomeId,
                unitId = unitId,
                title = "Resolver preguntas revisadas de ${title.trim()}",
                description = null,
                version = 1,
                updatedAt = now,
            ),
        node =
            NodeDto(
                nodeId = nodeId,
                courseId = courseId,
                unitId = unitId,
                outcomeIds = listOf(outcomeId),
                title = title.trim(),
                coreClaim = "Banco revisado manualmente: ${title.trim()}",
                type = NodeType.CONCEPT,
                weightExam = 0.7,
                prerequisites = emptyList(),
                facets = facets,
                mustKnow = mustKnowByQuestion.values.toList(),
                commonErrors = commonErrors,
                minimumMasteryDefinition = "Responder correctamente las preguntas revisadas de esta unidad.",
                surfaceEasyReady = strictEasyCount >= 4,
                surfaceEasyItemCount = strictEasyCount,
                sourceRefs = sourceRefs,
                version = 1,
                updatedAt = now,
            ),
        items = items,
    )
}

private data class CompiledReviewedUnit(
    val unit: UnitDto,
    val outcome: OutcomeDto,
    val node: NodeDto,
    val items: List<ItemDto>,
)

private fun ReviewedEditableQuestionDraft.compiledFormat(): ItemFormat =
    when (format) {
        ReviewedQuestionFormat.TRUE_FALSE -> ItemFormat.TRUE_FALSE
        ReviewedQuestionFormat.MULTIPLE_CHOICE -> ItemFormat.MULTIPLE_CHOICE
        ReviewedQuestionFormat.CHOOSE_FALSE_STATEMENT -> ItemFormat.CHOOSE_FALSE_STATEMENT
        ReviewedQuestionFormat.REVEAL_ANSWER -> ItemFormat.ONE_SENTENCE_EXPLANATION
    }

private fun ReviewedEditableQuestionDraft.compiledRole(): ItemRole =
    when (studyRole) {
        ReviewedQuestionStudyRole.NORMAL -> ItemRole.CORE
        ReviewedQuestionStudyRole.VARIANT -> ItemRole.VARIANT
        ReviewedQuestionStudyRole.TRAP -> ItemRole.TRAP
        ReviewedQuestionStudyRole.INTEGRATION -> ItemRole.INTEGRATION
        ReviewedQuestionStudyRole.DEEP -> ItemRole.BOSS
    }

private fun ReviewedEditableQuestionDraft.compiledFacet(): FacetType =
    when {
        studyRole == ReviewedQuestionStudyRole.TRAP -> FacetType.ERROR_TIPICO
        studyRole == ReviewedQuestionStudyRole.VARIANT -> FacetType.DIFERENCIA_ENTRE_CONCEPTOS
        studyRole == ReviewedQuestionStudyRole.INTEGRATION || studyRole == ReviewedQuestionStudyRole.DEEP -> FacetType.INTEGRACION_CON_OTRO_NODO
        else -> FacetType.DEFINICION_FUNCIONAL
    }

private fun ReviewedEditableQuestionDraft.compiledOptions(): List<ItemOptionDto> =
    when (format) {
        ReviewedQuestionFormat.MULTIPLE_CHOICE,
        ReviewedQuestionFormat.CHOOSE_FALSE_STATEMENT,
        -> options.filter { it.text.isNotBlank() }.mapIndexed { index, option ->
            ItemOptionDto(
                id = "o${index + 1}",
                text = option.text.trim(),
                isCorrect = option.isCorrect,
            )
        }

        ReviewedQuestionFormat.TRUE_FALSE,
        ReviewedQuestionFormat.REVEAL_ANSWER,
        -> emptyList()
    }

private fun ReviewedEditableQuestionDraft.compiledCorrectAnswer(): String =
    when (format) {
        ReviewedQuestionFormat.TRUE_FALSE ->
            if (correctAnswer.normalizeTrueFalseAnswer() == true) "Verdadero" else "Falso"

        ReviewedQuestionFormat.MULTIPLE_CHOICE,
        ReviewedQuestionFormat.CHOOSE_FALSE_STATEMENT,
        -> options.firstOrNull { it.isCorrect }?.text?.trim().orEmpty().ifBlank { correctAnswer.trim() }

        ReviewedQuestionFormat.REVEAL_ANSWER -> correctAnswer.trim()
    }

private fun ReviewedEditableQuestionDraft.mustKnowLine(): String =
    "${key}: ${stem.trim()} -> ${compiledCorrectAnswer()}".take(700)

private fun ReviewedEditableQuestionDraft.difficultySeed(friction: Int): Double {
    val base =
        when (studyRole) {
            ReviewedQuestionStudyRole.NORMAL -> 0.35
            ReviewedQuestionStudyRole.VARIANT -> 0.4
            ReviewedQuestionStudyRole.TRAP -> 0.55
            ReviewedQuestionStudyRole.INTEGRATION -> 0.7
            ReviewedQuestionStudyRole.DEEP -> 0.85
        }
    return base.coerceAtLeast(friction * 0.1).coerceIn(0.0, 1.0)
}

private fun stableHash(raw: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}
