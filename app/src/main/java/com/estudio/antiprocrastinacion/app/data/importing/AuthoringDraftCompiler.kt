package com.estudio.antiprocrastinacion.app.data.importing

import com.estudio.antiprocrastinacion.app.domain.repository.ValidationMessage
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationReport
import com.estudio.antiprocrastinacion.app.model.authoring.AuthoringDraftPackageDto
import com.estudio.antiprocrastinacion.app.model.authoring.AuthoringItemDraftDto
import com.estudio.antiprocrastinacion.app.model.authoring.AuthoringNodeDraftDto
import com.estudio.antiprocrastinacion.app.model.authoring.AuthoringUnitDraftDto
import com.estudio.antiprocrastinacion.app.model.content.ContentOrigin
import com.estudio.antiprocrastinacion.app.model.content.FacetType
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
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

data class AuthoringDraftCompileResult(
    val packageId: String,
    val contentPackage: ContentPackageDto?,
    val report: ValidationReport,
)

private const val MIN_SURFACE_EASY_ITEMS = 4

@Singleton
class AuthoringDraftValidator @Inject constructor() {
    fun validate(draft: AuthoringDraftPackageDto): ValidationReport {
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

        validateKey("package_key", draft.packageKey, "$.packageKey", errors)
        validateKey("course_key", draft.course.courseKey, "$.course.courseKey", errors)
        if (draft.course.title.isBlank()) {
            error(
                code = "draft_course_title_blank",
                message = "El course draft no tiene título.",
                path = "$.course.title",
                expected = "Título visible del curso.",
                actual = "Texto vacío.",
                hint = "Completá course.title con el nombre del curso.",
            )
        }
        if (draft.units.isEmpty()) {
            error(
                code = "draft_units_empty",
                message = "El draft debe traer al menos una unidad.",
                path = "$.units",
                expected = "Lista no vacía de unidades.",
                actual = "Lista vacía.",
                hint = "Agrupá el contenido en al menos una unidad antes de importar.",
            )
        }
        draft.units.map { it.unitKey }.duplicates().forEach { key ->
            error(
                code = "draft_unit_key_duplicate:$key",
                message = "Hay unitKey duplicado: $key.",
                path = "$.units",
                expected = "unitKey único dentro del paquete.",
                actual = key,
                hint = "Renombrá una unidad; las keys definen identidad estable.",
            )
        }

        val nodeKeys = draft.units.flatMap { it.nodeDrafts }.map { it.nodeKey }
        nodeKeys.duplicates().forEach { key ->
            error(
                code = "draft_node_key_duplicate:$key",
                message = "Hay nodeKey duplicado: $key.",
                path = "$.units[].nodeDrafts[]",
                expected = "nodeKey único dentro del paquete.",
                actual = key,
                hint = "Renombrá un nodo; las keys definen identidad estable.",
            )
        }
        val knownNodeKeys = nodeKeys.toSet()

        draft.units.forEachIndexed { unitIndex, unit ->
            validateUnit(unit, unitIndex, knownNodeKeys, errors, warnings)
        }

        return ValidationReport(errors, warnings)
    }

    private fun validateUnit(
        unit: AuthoringUnitDraftDto,
        unitIndex: Int,
        knownNodeKeys: Set<String>,
        errors: MutableList<ValidationMessage>,
        warnings: MutableList<ValidationMessage>,
    ) {
        val unitPath = "$.units[$unitIndex]"

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

        validateKey("unit_key", unit.unitKey, "$unitPath.unitKey", errors)
        if (unit.title.isBlank()) {
            error(
                code = "draft_unit_title_blank:${unit.unitKey}",
                message = "La unidad ${unit.unitKey} no tiene título.",
                path = "$unitPath.title",
                expected = "Título visible de la unidad.",
                actual = "Texto vacío.",
                hint = "Completá title para que la unidad sea identificable en UI.",
            )
        }
        if (unit.outcomes.isEmpty()) {
            error(
                code = "draft_unit_outcomes_empty:${unit.unitKey}",
                message = "La unidad ${unit.unitKey} no trae outcomes.",
                path = "$unitPath.outcomes",
                expected = "Lista no vacía de outcomes.",
                actual = "Lista vacía.",
                hint = "Definí al menos un outcome y referencialo desde los nodos.",
            )
        }
        if (unit.nodeDrafts.isEmpty()) {
            error(
                code = "draft_unit_nodes_empty:${unit.unitKey}",
                message = "La unidad ${unit.unitKey} no trae nodos.",
                path = "$unitPath.nodeDrafts",
                expected = "Lista no vacía de nodeDrafts.",
                actual = "Lista vacía.",
                hint = "Convertí las preguntas planas en nodos con semántica pedagógica.",
            )
        }
        unit.outcomes.map { it.outcomeKey }.duplicates().forEach { key ->
            error(
                code = "draft_outcome_key_duplicate:${unit.unitKey}:$key",
                message = "Hay outcomeKey duplicado en ${unit.unitKey}: $key.",
                path = "$unitPath.outcomes",
                expected = "outcomeKey único dentro de la unidad.",
                actual = key,
                hint = "Renombrá un outcome o unificá referencias antes de importar.",
            )
        }
        unit.nodeDrafts.map { it.nodeKey }.duplicates().forEach { key ->
            error(
                code = "draft_unit_node_key_duplicate:${unit.unitKey}:$key",
                message = "Hay nodeKey duplicado en ${unit.unitKey}: $key.",
                path = "$unitPath.nodeDrafts",
                expected = "nodeKey único dentro de la unidad.",
                actual = key,
                hint = "Renombrá un nodo; las keys preservan identidad y progreso.",
            )
        }
        val outcomeKeys = unit.outcomes.map { it.outcomeKey }.toSet()
        unit.outcomes.forEachIndexed { outcomeIndex, outcome ->
            val outcomePath = "$unitPath.outcomes[$outcomeIndex]"
            validateKey("outcome_key", outcome.outcomeKey, "$outcomePath.outcomeKey", errors)
            if (outcome.title.isBlank()) {
                error(
                    code = "draft_outcome_title_blank:${unit.unitKey}:${outcome.outcomeKey}",
                    message = "El outcome ${outcome.outcomeKey} no tiene título.",
                    path = "$outcomePath.title",
                    expected = "Título visible del outcome.",
                    actual = "Texto vacío.",
                    hint = "Completá title para que los nodos referencien una habilidad concreta.",
                )
            }
        }
        unit.nodeDrafts.forEachIndexed { nodeIndex, node ->
            validateNode(node, unit.unitKey, "$unitPath.nodeDrafts[$nodeIndex]", outcomeKeys, knownNodeKeys, errors, warnings)
        }
    }

    private fun validateNode(
        node: AuthoringNodeDraftDto,
        unitKey: String,
        nodePath: String,
        outcomeKeys: Set<String>,
        knownNodeKeys: Set<String>,
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

        validateKey("node_key", node.nodeKey, "$nodePath.nodeKey", errors)
        if (node.title.isBlank()) {
            error(
                code = "draft_node_title_blank:${node.nodeKey}",
                message = "El nodo ${node.nodeKey} no tiene título.",
                path = "$nodePath.title",
                expected = "Título visible del nodo.",
                actual = "Texto vacío.",
                hint = "Completá title con la idea central del nodo.",
            )
        }
        if (node.coreClaim.isBlank()) {
            error(
                code = "draft_node_core_claim_blank:${node.nodeKey}",
                message = "El nodo ${node.nodeKey} no tiene coreClaim.",
                path = "$nodePath.coreClaim",
                expected = "Afirmación central bounded y evaluable.",
                actual = "Texto vacío.",
                hint = "Definí qué debe quedar vivo en memoria para este nodo.",
            )
        }
        if (node.outcomeKeys.isEmpty()) {
            error(
                code = "draft_node_outcomes_empty:${node.nodeKey}",
                message = "El nodo ${node.nodeKey} no referencia outcomes.",
                path = "$nodePath.outcomeKeys",
                expected = "Lista no vacía de outcomeKey existentes en la unidad.",
                actual = "Lista vacía.",
                hint = "Conectá el nodo con al menos un outcome de la unidad.",
            )
        }
        node.outcomeKeys.forEachIndexed { outcomeIndex, key ->
            if (key !in outcomeKeys) {
                error(
                    code = "draft_node_outcome_missing:${node.nodeKey}:$key",
                    message = "El nodo ${node.nodeKey} referencia un outcome inexistente en $unitKey: $key.",
                    path = "$nodePath.outcomeKeys[$outcomeIndex]",
                    expected = "Uno de: ${outcomeKeys.sorted()}",
                    actual = key,
                    hint = "Corregí outcomeKeys o agregá el outcome faltante en la misma unidad.",
                )
            }
        }
        if (node.facets.size < 3) {
            warning(
                code = "draft_node_few_facets:${node.nodeKey}",
                message = "El nodo ${node.nodeKey} tiene menos de 3 facetas.",
                path = "$nodePath.facets",
                expected = "Usualmente 3 a 6 facetas significativas.",
                actual = "${node.facets.size}",
                hint = "Agregá ángulos reales del nodo si existen; no inventes facetas vacías.",
            )
        }
        if (node.facets.size > 6) {
            warning(
                code = "draft_node_many_facets:${node.nodeKey}",
                message = "El nodo ${node.nodeKey} tiene más de 6 facetas.",
                path = "$nodePath.facets",
                expected = "Usualmente 3 a 6 facetas significativas.",
                actual = "${node.facets.size}",
                hint = "Revisá si el nodo quedó demasiado grande y conviene dividirlo.",
            )
        }
        if (node.mustKnow.isEmpty()) {
            error(
                code = "draft_node_must_know_empty:${node.nodeKey}",
                message = "El nodo ${node.nodeKey} no trae mustKnow.",
                path = "$nodePath.mustKnow",
                expected = "Lista no vacía de puntos indispensables.",
                actual = "Lista vacía.",
                hint = "Definí cobertura explícita; el compilador no inventa mustKnow.",
            )
        }
        if (node.commonErrors.isEmpty()) {
            error(
                code = "draft_node_common_errors_empty:${node.nodeKey}",
                message = "El nodo ${node.nodeKey} no trae commonErrors.",
                path = "$nodePath.commonErrors",
                expected = "Lista no vacía de errores conceptuales reales.",
                actual = "Lista vacía.",
                hint = "Definí errores típicos reales; el compilador no inventa confusiones.",
            )
        }
        if (node.minimumMasteryDefinition.isBlank()) {
            error(
                code = "draft_node_mastery_blank:${node.nodeKey}",
                message = "El nodo ${node.nodeKey} no trae minimumMasteryDefinition.",
                path = "$nodePath.minimumMasteryDefinition",
                expected = "Criterio mínimo observable de dominio.",
                actual = "Texto vacío.",
                hint = "Definí cómo saber que el nodo está dominado.",
            )
        }
        if (node.items.isEmpty()) {
            error(
                code = "draft_node_items_empty:${node.nodeKey}",
                message = "El nodo ${node.nodeKey} no trae items.",
                path = "$nodePath.items",
                expected = "Lista no vacía de preguntas/tarjetas.",
                actual = "Lista vacía.",
                hint = "Agregá items que cubran facets y mustKnow del nodo.",
            )
        }
        node.items.map { it.itemKey }.duplicates().forEach { key ->
            error(
                code = "draft_item_key_duplicate:${node.nodeKey}:$key",
                message = "Hay itemKey duplicado en ${node.nodeKey}: $key.",
                path = "$nodePath.items",
                expected = "itemKey único dentro del nodo.",
                actual = key,
                hint = "Renombrá un item; las keys preservan identidad y progreso.",
            )
        }
        node.prerequisites.forEachIndexed { prerequisiteIndex, key ->
            if (key !in knownNodeKeys && "__" !in key) {
                error(
                    code = "draft_prerequisite_missing:${node.nodeKey}:$key",
                    message = "El nodo ${node.nodeKey} referencia prerequisite inexistente: $key.",
                    path = "$nodePath.prerequisites[$prerequisiteIndex]",
                    expected = "nodeKey existente o nodeId completo ya derivado.",
                    actual = key,
                    hint = "Corregí la prerequisite o agregá el nodo requerido.",
                )
            }
        }
        node.items.forEachIndexed { itemIndex, item ->
            validateItem(item, node, "$nodePath.items[$itemIndex]", errors, warnings)
        }
        validateNodeCoverage(node, nodePath, warnings)
        validateProjectedSurfaceReadiness(node, nodePath, warnings)
    }

    private fun validateNodeCoverage(
        node: AuthoringNodeDraftDto,
        nodePath: String,
        warnings: MutableList<ValidationMessage>,
    ) {
        val covered = node.items.flatMap { it.coversMustKnow }.toSet()
        node.mustKnow.forEachIndexed { mustKnowIndex, mustKnow ->
            if (mustKnow !in covered) {
                warnings +=
                    ValidationMessage(
                        code = "draft_node_must_know_uncovered:${node.nodeKey}:$mustKnow",
                        message = "El mustKnow '$mustKnow' del nodo ${node.nodeKey} no está cubierto por ningún item.",
                        path = "$nodePath.mustKnow[$mustKnowIndex]",
                        expected = "Al menos un item con coversMustKnow que referencie este punto.",
                        actual = "Sin cobertura.",
                        hint = "Agregá o ajustá un item; no elimines el mustKnow si realmente es indispensable.",
                    )
            }
        }
    }

    private fun validateProjectedSurfaceReadiness(
        node: AuthoringNodeDraftDto,
        nodePath: String,
        warnings: MutableList<ValidationMessage>,
    ) {
        val projectedStrictEasy =
            node.items.count { item ->
                item.roleHint != ItemRole.RESCUE &&
                    ContentImportRules.isStrictFrictionOne(
                        format = item.format,
                        frictionLevel = ContentImportRules.frictionFor(item.format),
                    )
            }
        if (projectedStrictEasy < MIN_SURFACE_EASY_ITEMS) {
            warnings +=
                ValidationMessage(
                    code = "draft_node_surface_easy_insufficient:${node.nodeKey}",
                    message = "El nodo ${node.nodeKey} proyecta $projectedStrictEasy items strict easy reales; se recomiendan al menos $MIN_SURFACE_EASY_ITEMS.",
                    path = "$nodePath.items",
                    expected = "Al menos $MIN_SURFACE_EASY_ITEMS items reales strict friction 1 no-rescue.",
                    actual = "$projectedStrictEasy",
                    hint = "Agregá MCQ/true-false/matching_simple reales aptos para entrada rápida si el nodo debe aparecer en superficies livianas.",
                )
        }
    }

    private fun validateItem(
        item: AuthoringItemDraftDto,
        node: AuthoringNodeDraftDto,
        itemPath: String,
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

        validateKey("item_key", item.itemKey, "$itemPath.itemKey", errors)
        if (item.facet !in node.facets) {
            error(
                code = "draft_item_facet_not_declared:${node.nodeKey}:${item.itemKey}",
                message = "El item ${item.itemKey} usa una faceta no declarada en el nodo.",
                path = "$itemPath.facet",
                expected = "Una de las facets del nodo: ${node.facets}.",
                actual = item.facet.name,
                hint = "Agregá esa facet al nodo si es real, o corregí el item para usar una facet existente.",
            )
        }
        if (item.stem.isBlank()) {
            error(
                code = "draft_item_stem_blank:${node.nodeKey}:${item.itemKey}",
                message = "El item ${item.itemKey} no tiene stem.",
                path = "$itemPath.stem",
                expected = "Prompt visible no vacío.",
                actual = "Texto vacío.",
                hint = "Escribí la pregunta o consigna visible.",
            )
        }
        if (item.feedback.isBlank()) {
            error(
                code = "draft_item_feedback_blank:${node.nodeKey}:${item.itemKey}",
                message = "El item ${item.itemKey} no tiene feedback.",
                path = "$itemPath.feedback",
                expected = "Feedback breve no vacío.",
                actual = "Texto vacío.",
                hint = "Agregá una explicación corta que refuerce el punto evaluado.",
            )
        }
        item.coversMustKnow.forEachIndexed { coversIndex, mustKnow ->
            if (mustKnow !in node.mustKnow) {
                error(
                    code = "draft_item_covers_unknown:${node.nodeKey}:${item.itemKey}",
                    message = "El item ${item.itemKey} cubre un mustKnow inexistente: $mustKnow.",
                    path = "$itemPath.coversMustKnow[$coversIndex]",
                    expected = "Uno de los mustKnow del nodo: ${node.mustKnow}.",
                    actual = mustKnow,
                    hint = "Copiá exactamente un mustKnow existente o agregá el punto al nodo si es real.",
                )
            }
        }
        item.targetsCommonErrors.forEachIndexed { errorIndex, commonError ->
            if (commonError !in node.commonErrors) {
                error(
                    code = "draft_item_error_unknown:${node.nodeKey}:${item.itemKey}",
                    message = "El item ${item.itemKey} apunta a un commonError inexistente: $commonError.",
                    path = "$itemPath.targetsCommonErrors[$errorIndex]",
                    expected = "Uno de los commonErrors del nodo: ${node.commonErrors}.",
                    actual = commonError,
                    hint = "Copiá exactamente un commonError existente o agregá la confusión al nodo si es real.",
                )
            }
        }
        if (item.roleHint == ItemRole.TRAP && item.targetsCommonErrors.isEmpty()) {
            error(
                code = "draft_trap_without_error:${node.nodeKey}:${item.itemKey}",
                message = "El trap ${item.itemKey} debe apuntar a un commonError.",
                path = "$itemPath.targetsCommonErrors",
                expected = "Al menos un commonError real del nodo.",
                actual = "Lista vacía.",
                hint = "Un trap sin error conceptual no es importable como trap.",
            )
        }
        if (item.roleHint == ItemRole.TRAP && item.facet != FacetType.ERROR_TIPICO) {
            warning(
                code = "draft_trap_facet_mismatch:${node.nodeKey}:${item.itemKey}",
                message = "El trap ${item.itemKey} no usa la facet ERROR_TIPICO.",
                path = "$itemPath.facet",
                expected = "ERROR_TIPICO para traps conceptuales.",
                actual = item.facet.name,
                hint = "Si realmente detecta una confusión típica, movelo a ERROR_TIPICO; si no, cambiá roleHint.",
            )
        }
        if (item.format in ContentImportRules.choiceFormats) {
            val optionKeys = item.options.map { it.key }
            if (item.options.size !in 2..6) {
                error(
                    code = "draft_choice_option_count:${node.nodeKey}:${item.itemKey}",
                    message = "El item ${item.itemKey} debe tener entre 2 y 6 opciones.",
                    path = "$itemPath.options",
                    expected = "2 a 6 opciones.",
                    actual = "${item.options.size}",
                    hint = "Ajustá opciones; no uses formatos choice para respuestas abiertas.",
                )
            }
            optionKeys.forEachIndexed { optionIndex, key ->
                validateKey("option_key", key, "$itemPath.options[$optionIndex].key", errors)
            }
            optionKeys.duplicates().forEach { key ->
                error(
                    code = "draft_choice_option_key_duplicate:${node.nodeKey}:${item.itemKey}:$key",
                    message = "El item ${item.itemKey} repite option key $key.",
                    path = "$itemPath.options",
                    expected = "Keys de opciones únicas.",
                    actual = key,
                    hint = "Renombrá una opción; correct debe apuntar a una sola key.",
                )
            }
            if (item.resolvedCorrectOptionKey() == null) {
                error(
                    code = "draft_choice_correct_missing:${node.nodeKey}:${item.itemKey}",
                    message = "El correct del item ${item.itemKey} debe apuntar a una única opción existente.",
                    path = "$itemPath.correct",
                    expected = "Una option.key existente: $optionKeys.",
                    actual = item.correct,
                    hint = "Usá la key de la opción correcta; la comparación tolera mayúsculas/minúsculas si es inequívoca.",
                )
            }
            val duplicateOptionTexts =
                item.options.map { it.text.trim().lowercase() }.duplicates()
            if (duplicateOptionTexts.isNotEmpty()) {
                warning(
                    code = "draft_choice_duplicate_option_text:${node.nodeKey}:${item.itemKey}",
                    message = "El item ${item.itemKey} tiene opciones con texto duplicado o equivalente.",
                    path = "$itemPath.options",
                    expected = "Distractores distinguibles.",
                    actual = duplicateOptionTexts.joinToString(),
                    hint = "Revisá si el distractor duplica la respuesta o no agrega contraste conceptual.",
                )
            }
        } else if (item.format == ItemFormat.TRUE_FALSE) {
            if (item.options.isNotEmpty()) {
                error(
                    code = "draft_true_false_options:${node.nodeKey}:${item.itemKey}",
                    message = "El true/false ${item.itemKey} no debe traer options.",
                    path = "$itemPath.options",
                    expected = "Lista vacía en TRUE_FALSE.",
                    actual = "${item.options.size} opciones.",
                    hint = "Eliminá options y dejá correct como true/false.",
                )
            }
            if (normalizeTrueFalse(item.correct) == null) {
                error(
                    code = "draft_true_false_correct_invalid:${node.nodeKey}:${item.itemKey}",
                    message = "El true/false ${item.itemKey} debe usar correct true/false.",
                    path = "$itemPath.correct",
                    expected = "true/false, verdadero/falso, v/f, si/sí/no.",
                    actual = item.correct,
                    hint = "Usá un valor booleano textual inequívoco.",
                )
            }
        } else if (item.options.isNotEmpty()) {
            error(
                code = "draft_open_format_options:${node.nodeKey}:${item.itemKey}",
                message = "El formato ${item.format} no debe traer options.",
                path = "$itemPath.options",
                expected = "Lista vacía para formatos abiertos/autoevaluados.",
                actual = "${item.options.size} opciones.",
                hint = "Si querés opciones, cambiá el format a uno choice compatible; si no, eliminá options.",
            )
        }
        val difficultySeed = item.difficultySeed
        if (difficultySeed != null && difficultySeed !in 0.0..1.0) {
            error(
                code = "draft_item_difficulty_invalid:${node.nodeKey}:${item.itemKey}",
                message = "El difficultySeed del item ${item.itemKey} debe estar entre 0 y 1.",
                path = "$itemPath.difficultySeed",
                expected = "Número entre 0.0 y 1.0.",
                actual = "$difficultySeed",
                hint = "Quitá difficultySeed para usar default o ajustalo al rango permitido.",
            )
        }
    }

    private fun validateKey(
        label: String,
        key: String,
        path: String,
        errors: MutableList<ValidationMessage>,
    ) {
        if (!KEY_REGEX.matches(key)) {
            errors +=
                ValidationMessage(
                    code = "draft_${label}_invalid:$key",
                    message = "$label debe usar solo minúsculas, números y guiones bajos: $key.",
                    path = path,
                    expected = "Regex [a-z0-9][a-z0-9_]*.",
                    actual = key,
                    hint = "No se normaliza silenciosamente porque las keys definen identidad estable.",
                )
        }
    }

    companion object {
        private val KEY_REGEX = Regex("[a-z0-9][a-z0-9_]*")
    }
}

@Singleton
class AuthoringDraftCompiler @Inject constructor(
    private val validator: AuthoringDraftValidator,
) {
    fun compile(
        draft: AuthoringDraftPackageDto,
        generatedAt: Long,
    ): AuthoringDraftCompileResult {
        val validation = validator.validate(draft)
        val packageId = draft.packageKey
        if (!validation.canImport) {
            return AuthoringDraftCompileResult(packageId, null, validation)
        }

        val courseId = draft.course.courseKey
        val unitIdsByKey = draft.units.associate { it.unitKey to unitId(courseId, it.unitKey) }
        val nodeIdsByKey =
            draft.units.flatMap { unit ->
                unit.nodeDrafts.map { node -> node.nodeKey to nodeId(unitIdsByKey.getValue(unit.unitKey), node.nodeKey) }
            }.toMap()

        val course =
            CourseDto(
                courseId = courseId,
                title = draft.course.title.trim(),
                description = draft.course.description?.trim(),
                version = 1,
                updatedAt = generatedAt,
            )
        val units =
            draft.units.map { unit ->
                UnitDto(
                    unitId = unitIdsByKey.getValue(unit.unitKey),
                    courseId = courseId,
                    title = unit.title.trim(),
                    description = unit.description?.trim(),
                    orderIndex = unit.orderIndex,
                    version = 1,
                    updatedAt = generatedAt,
                )
            }
        val outcomes =
            draft.units.flatMap { unit ->
                val unitId = unitIdsByKey.getValue(unit.unitKey)
                unit.outcomes.map { outcome ->
                    OutcomeDto(
                        outcomeId = outcomeId(unitId, outcome.outcomeKey),
                        unitId = unitId,
                        title = outcome.title.trim(),
                        description = outcome.description?.trim(),
                        version = 1,
                        updatedAt = generatedAt,
                    )
                }
            }
        val nodes =
            draft.units.flatMap { unit ->
                val unitId = unitIdsByKey.getValue(unit.unitKey)
                unit.nodeDrafts.map { node ->
                    val compiledItems = node.items.map { compileItem(nodeIdsByKey.getValue(node.nodeKey), it, generatedAt, node, unit.sourceRefs, draft.sourceRefs) }
                    val surfaceEasyCount =
                        compiledItems.count {
                            ContentImportRules.countsAsRealSurfaceEasy(
                                format = it.format,
                                role = it.itemRole,
                                frictionLevel = it.frictionLevel,
                                allowedSurfaces = it.allowedSurfaces,
                            )
                        }
                    NodeDto(
                        nodeId = nodeIdsByKey.getValue(node.nodeKey),
                        courseId = courseId,
                        unitId = unitId,
                        outcomeIds = node.outcomeKeys.map { outcomeId(unitId, it) },
                        title = node.title.trim(),
                        coreClaim = node.coreClaim.trim(),
                        type = node.nodeTypeHint,
                        weightExam = node.weightExam.coerceIn(0.0, 1.0),
                        prerequisites = node.prerequisites.map { prerequisite -> nodeIdsByKey[prerequisite] ?: prerequisite },
                        facets = node.facets,
                        mustKnow = node.mustKnow.map(String::trim),
                        commonErrors = node.commonErrors.map(String::trim),
                        minimumMasteryDefinition = node.minimumMasteryDefinition.trim(),
                        surfaceEasyReady = surfaceEasyCount >= MIN_SURFACE_EASY_ITEMS,
                        surfaceEasyItemCount = surfaceEasyCount,
                        sourceRefs = (draft.sourceRefs + unit.sourceRefs + node.sourceRefs).distinct(),
                        version = 1,
                        updatedAt = generatedAt,
                    )
                }
            }
        val items =
            draft.units.flatMap { unit ->
                unit.nodeDrafts.flatMap { node ->
                    node.items.sortedWith(compareBy<AuthoringItemDraftDto> { it.sourceOrder ?: Int.MAX_VALUE }.thenBy { it.itemKey })
                        .map { item -> compileItem(nodeIdsByKey.getValue(node.nodeKey), item, generatedAt, node, unit.sourceRefs, draft.sourceRefs) }
                }
            }

        val packageWithoutHash =
            ContentPackageDto(
                packageId = packageId,
                schemaVersion = 1,
                seedVersion = null,
                generatedAt = generatedAt,
                contentHash = null,
                origin = ContentOrigin.IMPORTED,
                courses = listOf(course),
                units = units,
                outcomes = outcomes,
                nodes = nodes,
                items = items,
            )
        val contentPackage = packageWithoutHash.copy(contentHash = hash(packageWithoutHash))
        return AuthoringDraftCompileResult(packageId, contentPackage, validation)
    }

    private fun compileItem(
        nodeId: String,
        item: AuthoringItemDraftDto,
        generatedAt: Long,
        node: AuthoringNodeDraftDto,
        unitSourceRefs: List<String>,
        packageSourceRefs: List<String>,
    ): ItemDto {
        val friction = ContentImportRules.frictionFor(item.format)
        val role = item.roleHint
        val resolvedCorrectKey =
            if (item.format in ContentImportRules.choiceFormats) {
                item.resolvedCorrectOptionKey()
            } else {
                null
            }
        val options =
            item.options.map { option ->
                ItemOptionDto(
                    id = option.key,
                    text = option.text.trim(),
                    isCorrect = option.key == resolvedCorrectKey,
                )
            }
        val correctAnswer =
            when {
                item.format in ContentImportRules.choiceFormats -> options.firstOrNull { it.isCorrect }?.text ?: item.correct.trim()
                item.format == ItemFormat.TRUE_FALSE -> if (normalizeTrueFalse(item.correct) == true) "Verdadero" else "Falso"
                else -> item.correct.trim()
            }
        return ItemDto(
            itemId = itemId(nodeId, item.itemKey),
            nodeId = nodeId,
            facet = item.facet,
            format = item.format,
            frictionLevel = friction,
            difficultySeed = item.difficultySeed ?: defaultDifficultyFor(role, friction),
            itemRole = role,
            allowedSurfaces = ContentImportRules.allowedSurfacesFor(item.format, role),
            cooldownHours = ContentImportRules.defaultCooldownHoursFor(item.format, role),
            stem = item.stem.trim(),
            correctAnswer = correctAnswer,
            feedbackShort = item.feedback.trim(),
            coversMustKnow = item.coversMustKnow.map(String::trim),
            variantGroupId = null,
            rescueGroupId = if (role == ItemRole.RESCUE) "${node.nodeKey}_rescue" else null,
            nodeComplexity = null,
            facetComplexity = null,
            distractorSimilarity = null,
            prerequisiteDepth = null,
            targetsErrorIds = emptyList(),
            commonErrorSignals = item.targetsCommonErrors.map(String::trim),
            options = options,
            version = 1,
            updatedAt = generatedAt,
            sourceRefs = (packageSourceRefs + unitSourceRefs + node.sourceRefs + item.sourceRefs).distinct(),
        )
    }

    private fun defaultDifficultyFor(
        role: ItemRole,
        friction: Int,
    ): Double =
        when (role) {
            ItemRole.CORE -> 0.35
            ItemRole.VARIANT -> 0.40
            ItemRole.TRAP -> 0.55
            ItemRole.INTEGRATION -> 0.70
            ItemRole.BOSS -> 0.85
            ItemRole.RESCUE -> 0.20
        }.coerceAtLeast(friction * 0.10)

    private fun unitId(
        courseKey: String,
        unitKey: String,
    ): String = "${courseKey}__${unitKey}"

    private fun outcomeId(
        unitId: String,
        outcomeKey: String,
    ): String = "${unitId}__${outcomeKey}"

    private fun nodeId(
        unitId: String,
        nodeKey: String,
    ): String = "${unitId}__${nodeKey}"

    private fun itemId(
        nodeId: String,
        itemKey: String,
    ): String = "${nodeId}__${itemKey}"

    private fun hash(contentPackage: ContentPackageDto): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(AppJson.encodeToString(contentPackage).toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

}

private fun List<String>.duplicates(): Set<String> =
    groupingBy { it }.eachCount().filterValues { it > 1 }.keys

private fun AuthoringItemDraftDto.resolvedCorrectOptionKey(): String? {
    val target = correct.trim()
    return options.singleOrNull { option -> option.key == target }?.key
        ?: options.singleOrNull { option -> option.key.equals(target, ignoreCase = true) }?.key
}

internal fun normalizeTrueFalse(raw: String): Boolean? =
    when (raw.trim().lowercase()) {
        "true", "verdadero", "verdad", "v", "si", "sí" -> true
        "false", "falso", "f" -> false
        else -> null
    }
