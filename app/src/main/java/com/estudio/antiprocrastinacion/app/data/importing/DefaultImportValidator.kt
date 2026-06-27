package com.estudio.antiprocrastinacion.app.data.importing

import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidator
import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidationProfile
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationMessage
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationReport
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.json.ContentPackageDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultImportValidator @Inject constructor() : ImportValidator {
    suspend fun validate(contentPackage: ContentPackageDto): ValidationReport =
        validate(contentPackage, ImportValidationProfile.PEDAGOGICAL_NODE)

    override suspend fun validate(
        contentPackage: ContentPackageDto,
        profile: ImportValidationProfile,
    ): ValidationReport {
        val errors = mutableListOf<ValidationMessage>()
        val warnings = mutableListOf<ValidationMessage>()
        val reviewedQuestionBank = profile == ImportValidationProfile.REVIEWED_QUESTION_BANK

        fun error(code: String, message: String) {
            errors += ValidationMessage(code, message)
        }

        fun warning(code: String, message: String) {
            warnings += ValidationMessage(code, message)
        }

        if (contentPackage.packageId.isBlank()) {
            error("package_id_blank", "packageId no puede estar vacío.")
        }

        if (contentPackage.courses.map { it.courseId }.hasDuplicates()) {
            error("course_ids_duplicate", "Hay courseId duplicados.")
        }
        if (contentPackage.units.map { it.unitId }.hasDuplicates()) {
            error("unit_ids_duplicate", "Hay unitId duplicados.")
        }
        if (contentPackage.outcomes.map { it.outcomeId }.hasDuplicates()) {
            error("outcome_ids_duplicate", "Hay outcomeId duplicados.")
        }
        if (contentPackage.nodes.map { it.nodeId }.hasDuplicates()) {
            error("node_ids_duplicate", "Hay nodeId duplicados.")
        }
        if (contentPackage.items.map { it.itemId }.hasDuplicates()) {
            error("item_ids_duplicate", "Hay itemId duplicados.")
        }

        val courseIds = contentPackage.courses.map { it.courseId }.toSet()
        val unitIds = contentPackage.units.map { it.unitId }.toSet()
        val outcomeIds = contentPackage.outcomes.map { it.outcomeId }.toSet()
        val nodeIds = contentPackage.nodes.map { it.nodeId }.toSet()
        val itemsByNode = contentPackage.items.groupBy { it.nodeId }
        val unitsById = contentPackage.units.associateBy { it.unitId }
        val outcomesById = contentPackage.outcomes.associateBy { it.outcomeId }
        val nodesById = contentPackage.nodes.associateBy { it.nodeId }

        contentPackage.courses.forEach { course ->
            if (course.title.isBlank()) {
                error("course_title_blank:${course.courseId}", "El curso ${course.courseId} no tiene título.")
            }
        }

        contentPackage.units.forEach { unit ->
            if (unit.courseId !in courseIds) {
                error("unit_course_missing:${unit.unitId}", "La unidad ${unit.unitId} referencia un courseId inexistente.")
            }
            if (unit.title.isBlank()) {
                error("unit_title_blank:${unit.unitId}", "La unidad ${unit.unitId} no tiene título.")
            }
        }

        contentPackage.outcomes.forEach { outcome ->
            if (outcome.unitId !in unitIds) {
                error("outcome_unit_missing:${outcome.outcomeId}", "El outcome ${outcome.outcomeId} referencia una unit inexistente.")
            }
            if (outcome.title.isBlank()) {
                error("outcome_title_blank:${outcome.outcomeId}", "El outcome ${outcome.outcomeId} no tiene título.")
            }
        }

        contentPackage.nodes.forEach { node ->
            if (node.unitId !in unitIds) {
                error("node_unit_missing:${node.nodeId}", "El nodo ${node.nodeId} referencia una unit inexistente.")
            }
            if (node.courseId !in courseIds) {
                error("node_course_missing:${node.nodeId}", "El nodo ${node.nodeId} referencia un course inexistente.")
            }
            val unit = unitsById[node.unitId]
            if (unit != null && unit.courseId != node.courseId) {
                error("node_course_unit_mismatch:${node.nodeId}", "El nodo ${node.nodeId} no coincide con el courseId de su unidad.")
            }
            if (node.outcomeIds.isEmpty()) {
                error("node_outcomes_empty:${node.nodeId}", "El nodo ${node.nodeId} debe tener outcomeIds.")
            }
            if (node.outcomeIds.any { it !in outcomeIds }) {
                error("node_outcome_missing:${node.nodeId}", "El nodo ${node.nodeId} referencia outcomes inexistentes.")
            }
            node.outcomeIds.mapNotNull(outcomesById::get).filter { it.unitId != node.unitId }.forEach { outcome ->
                error("node_outcome_unit_mismatch:${node.nodeId}:${outcome.outcomeId}", "El nodo ${node.nodeId} referencia un outcome de otra unidad.")
            }
            if (node.weightExam !in 0.0..1.0) {
                error("node_weight_invalid:${node.nodeId}", "El nodo ${node.nodeId} tiene weightExam fuera de 0..1.")
            }
            if (node.coreClaim.isBlank()) {
                error("node_core_claim_blank:${node.nodeId}", "El nodo ${node.nodeId} no tiene coreClaim.")
            }
            if (node.mustKnow.isEmpty()) {
                error("node_must_know_empty:${node.nodeId}", "El nodo ${node.nodeId} no tiene mustKnow.")
            }
            if (!reviewedQuestionBank && node.commonErrors.isEmpty()) {
                error("node_common_errors_empty:${node.nodeId}", "El nodo ${node.nodeId} no tiene commonErrors.")
            }
            if (reviewedQuestionBank && node.facets.isEmpty()) {
                error("node_facets_empty:${node.nodeId}", "El nodo ${node.nodeId} debe tener al menos una faceta.")
            }
            if (!reviewedQuestionBank && node.facets.size < 3) {
                warning("node_few_facets:${node.nodeId}", "El nodo ${node.nodeId} tiene menos de 3 facetas.")
            }
            if (node.facets.size > 6) {
                warning("node_many_facets:${node.nodeId}", "El nodo ${node.nodeId} tiene más de 6 facetas.")
            }
            val nodeItems = itemsByNode[node.nodeId].orEmpty()
            val strictEasySurfaceItems =
                nodeItems.count {
                    ContentImportRules.countsAsRealSurfaceEasy(
                        format = it.format,
                        role = it.itemRole,
                        frictionLevel = it.frictionLevel,
                        allowedSurfaces = it.allowedSurfaces,
                    )
                }
            val legacyEasySurfaceItems =
                nodeItems.count {
                    it.frictionLevel == 1 &&
                        it.allowedSurfaces.any { surface ->
                            surface in setOf(
                                Surface.ALARM,
                                Surface.NOTIFICATION,
                                Surface.WIDGET,
                                Surface.SOCIAL_GATE,
                                Surface.BACK_MICRO,
                            )
                        }
                }
            if (node.surfaceEasyReady && legacyEasySurfaceItems < 4) {
                error(
                    code = "node_surface_easy_insufficient:${node.nodeId}",
                    message = "El nodo ${node.nodeId} marca surfaceEasyReady pero no tiene 4 items easy aptos.",
                )
            }
            if (node.surfaceEasyReady && strictEasySurfaceItems < 4 && legacyEasySurfaceItems >= 4) {
                warning(
                    code = "node_surface_easy_strict_insufficient:${node.nodeId}",
                    message = "El nodo ${node.nodeId} es importable legacy, pero no tiene 4 items easy estrictos para superficies externas nuevas.",
                )
            }
            if (reviewedQuestionBank && !node.surfaceEasyReady && strictEasySurfaceItems == 0) {
                warning(
                    code = "node_reviewed_bank_without_easy_items:${node.nodeId}",
                    message = "El nodo ${node.nodeId} no tiene preguntas rápidas estrictas para entrada de baja fricción.",
                )
            }
            if (node.surfaceEasyItemCount != strictEasySurfaceItems) {
                warning(
                    code = "node_surface_easy_count_mismatch:${node.nodeId}",
                    message = "El nodo ${node.nodeId} declara surfaceEasyItemCount=${node.surfaceEasyItemCount}, pero se calcularon $strictEasySurfaceItems estrictos.",
                )
            }
        }

        contentPackage.items.forEach { item ->
            if (item.nodeId !in nodeIds) {
                error("item_node_missing:${item.itemId}", "El item ${item.itemId} referencia un nodo inexistente.")
            }
            val node = nodesById[item.nodeId]
            if (node != null) {
                if (item.facet !in node.facets) {
                    error("item_facet_not_declared:${item.itemId}", "El item ${item.itemId} usa una faceta no declarada por su nodo.")
                }
                item.coversMustKnow.filterNot { it in node.mustKnow }.forEach { mustKnow ->
                    error("item_covers_unknown:${item.itemId}", "El item ${item.itemId} cubre un mustKnow inexistente: $mustKnow.")
                }
                if (item.itemRole == ItemRole.TRAP && item.commonErrorSignals.isEmpty() && item.targetsErrorIds.isEmpty()) {
                    if (reviewedQuestionBank) {
                        error("item_trap_without_error:${item.itemId}", "El trap ${item.itemId} debe apuntar a una confusión corregida.")
                    } else {
                        warning("item_trap_without_error:${item.itemId}", "El trap ${item.itemId} debería apuntar a un error típico.")
                    }
                }
            }
            if (item.allowedSurfaces.isEmpty()) {
                error("item_allowed_surfaces_empty:${item.itemId}", "El item ${item.itemId} no tiene allowedSurfaces.")
            }
            if (item.feedbackShort.isBlank()) {
                error("item_feedback_blank:${item.itemId}", "El item ${item.itemId} no tiene feedbackShort.")
            }
            if (item.version <= 0) {
                error("item_version_invalid:${item.itemId}", "El item ${item.itemId} tiene versión inválida.")
            }
            if (item.frictionLevel !in 1..4) {
                error("item_friction_invalid:${item.itemId}", "El item ${item.itemId} tiene frictionLevel fuera de 1..4.")
            }
            if (item.difficultySeed !in 0.0..1.0) {
                error("item_difficulty_invalid:${item.itemId}", "El item ${item.itemId} tiene difficultySeed fuera de 0..1.")
            }
            validateItemAnswerShape(item, ::error, ::warning)
        }

        contentPackage.nodes.forEach { node ->
            val nodeItems = itemsByNode[node.nodeId].orEmpty()
            val friction1 = nodeItems.count { it.frictionLevel == 1 }
            val friction2 = nodeItems.count { it.frictionLevel == 2 }
            val friction3 = nodeItems.count { it.frictionLevel == 3 }
            val traps = nodeItems.count { it.itemRole == ItemRole.TRAP }
            val integrationBoss = nodeItems.count { it.itemRole == ItemRole.INTEGRATION || it.itemRole == ItemRole.BOSS }

            if (!reviewedQuestionBank && (friction1 < 2 || friction2 < 2 || friction3 < 1)) {
                warning(
                    code = "node_friction_distribution:${node.nodeId}",
                    message = "El nodo ${node.nodeId} no cumple la distribución 2/2/1 por fricción.",
                )
            }
            if (!reviewedQuestionBank && traps == 0) {
                warning("node_missing_traps:${node.nodeId}", "El nodo ${node.nodeId} no tiene traps.")
            }
            if (!reviewedQuestionBank && integrationBoss == 0) {
                warning("node_missing_integration_boss:${node.nodeId}", "El nodo ${node.nodeId} no tiene integration ni boss.")
            }
        }

        return ValidationReport(
            structuralErrors = errors,
            authoringWarnings = warnings,
        )
    }
}

private fun <T> List<T>.hasDuplicates(): Boolean = size != toSet().size

private fun validateItemAnswerShape(
    item: com.estudio.antiprocrastinacion.app.model.json.ItemDto,
    error: (String, String) -> Unit,
    warning: (String, String) -> Unit,
) {
    if (item.format in ContentImportRules.choiceFormats) {
        val correctOptions = item.options.filter { it.isCorrect }
        if (item.options.size !in 2..6) {
            error("item_choice_option_count:${item.itemId}", "El item ${item.itemId} debe tener entre 2 y 6 opciones.")
        }
        if (correctOptions.size != 1) {
            error("item_choice_correct_count:${item.itemId}", "El item ${item.itemId} debe tener exactamente una opción correcta.")
        }
        val correctText = correctOptions.singleOrNull()?.text
        if (correctText != null && item.correctAnswer.trim().trimEnd('.') != correctText.trim().trimEnd('.')) {
            warning("item_choice_answer_mismatch:${item.itemId}", "El correctAnswer del item ${item.itemId} no coincide exactamente con su opción correcta.")
        }
    } else if (item.format == ItemFormat.TRUE_FALSE) {
        if (item.options.isNotEmpty()) {
            error("item_true_false_options:${item.itemId}", "El true/false ${item.itemId} no debe tener options.")
        }
        if (normalizeTrueFalse(item.correctAnswer) == null) {
            error("item_true_false_answer_invalid:${item.itemId}", "El true/false ${item.itemId} debe tener correctAnswer verdadero/falso.")
        }
    } else if (item.options.isNotEmpty()) {
        error("item_open_format_options:${item.itemId}", "El formato ${item.format} no debe tener options.")
    }

    val hasExternalSurface =
        item.allowedSurfaces.any {
            it in setOf(
                Surface.ALARM,
                Surface.NOTIFICATION,
                Surface.WIDGET,
                Surface.SOCIAL_GATE,
                Surface.BACK_MICRO,
            )
        }
    if (hasExternalSurface && !ContentImportRules.isStrictFrictionOne(item.format, item.frictionLevel)) {
        warning("item_external_surface_not_strict:${item.itemId}", "El item ${item.itemId} tiene surface externa pero no es friction 1 estricta.")
    }
}
