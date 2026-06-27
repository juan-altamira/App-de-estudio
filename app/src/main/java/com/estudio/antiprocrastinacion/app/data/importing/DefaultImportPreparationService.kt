package com.estudio.antiprocrastinacion.app.data.importing

import com.estudio.antiprocrastinacion.app.domain.repository.ImportPreparationResult
import com.estudio.antiprocrastinacion.app.domain.repository.ImportPreparationService
import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidationProfile
import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidator
import com.estudio.antiprocrastinacion.app.domain.repository.PreparedImportKind
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationMessage
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationReport
import com.estudio.antiprocrastinacion.app.model.authoring.AuthoringDraftPackageDto
import com.estudio.antiprocrastinacion.app.model.content.FacetType
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.NodeType
import com.estudio.antiprocrastinacion.app.model.json.ContentPackageDto
import com.estudio.antiprocrastinacion.app.ui.common.AppJson
import com.estudio.antiprocrastinacion.app.ui.common.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Singleton
class DefaultImportPreparationService @Inject constructor(
    private val importValidator: ImportValidator,
    private val authoringDraftCompiler: AuthoringDraftCompiler,
    private val timeProvider: TimeProvider,
) : ImportPreparationService {
    suspend fun prepare(
        rawJson: String,
        sourceLabel: String,
    ): ImportPreparationResult =
        prepare(
            rawJson = rawJson,
            sourceLabel = sourceLabel,
            validationProfile = ImportValidationProfile.PEDAGOGICAL_NODE,
        )

    override suspend fun prepare(
        rawJson: String,
        sourceLabel: String,
        validationProfile: ImportValidationProfile,
    ): ImportPreparationResult {
        val root =
            runCatching { StrictDraftJson.parseToJsonElement(rawJson).jsonObject }.getOrElse { error ->
                return invalidResult(
                    sourceLabel = sourceLabel,
                    code = "json_parse_error",
                    message = "El JSON no se pudo parsear: ${error.message ?: "formato inválido"}",
                    path = "$",
                    expected = "Objeto JSON válido.",
                    actual = error.message ?: "formato inválido",
                    hint = "Corregí la sintaxis JSON antes de volver a importar.",
                )
            }
        val schemaRead = root.readSchema()
        if (schemaRead == SchemaRead.Invalid) {
            return invalidSchemaResult(sourceLabel)
        }
        val schema = (schemaRead as? SchemaRead.Valid)?.value
        if (schema != null && !schema.isKnownSchemaAlias()) {
            return unknownSchemaResult(sourceLabel, schema, root)
        }
        if (root.looksLikeFlatQuestionDraft()) {
            return flatQuestionDraftResult(sourceLabel, schema, root)
        }
        return when {
            "packageKey" in root -> prepareAuthoringDraft(root, root.normalizedAuthoringDraftJson(), sourceLabel, validationProfile)
            "packageId" in root -> {
                val contentProfile =
                    root.contentPackageValidationProfile(validationProfile).getOrElse { error ->
                        return invalidResult(
                            sourceLabel = sourceLabel,
                            packageId = root["packageId"]
                                ?.let { element -> runCatching { element.jsonPrimitive.content }.getOrNull() }
                                .orEmpty()
                                .ifBlank { sourceLabel },
                            schemaVersion = root["schemaVersion"]
                                ?.let { element -> runCatching { element.jsonPrimitive.content.toIntOrNull() }.getOrNull() }
                                ?: 1,
                            code = "content_package_import_mode_invalid",
                            message = error.message ?: "importMode inválido.",
                            path = "$.importMode",
                            expected = "Ausente, full, republish, additive o incremental.",
                            actual = root["importMode"]?.toString() ?: "ausente",
                            hint = "Usá importMode: \"additive\" solo cuando el paquete agrega contenido y no republica el curso completo.",
                        )
                    }
                prepareContentPackage(root.contentPackageJson(), sourceLabel, contentProfile)
            }
            schema != null && schema in AuthoringSchemaAliases -> authoringShapeMissingResult(sourceLabel, schema, root)
            schema != null && schema in ContentSchemaAliases -> contentShapeMissingResult(sourceLabel, schema, root)
            else ->
                invalidResult(
                    sourceLabel = sourceLabel,
                    code = "json_unknown_package_type",
                    message = "El JSON no parece ser content_package ni authoring_draft.",
                    path = "$",
                    expected = "Raíz con packageKey para AuthoringDraftPackage o packageId para ContentPackageDto.",
                    actual = "Campos raíz: ${root.fieldList()}",
                    hint = "Usá AuthoringDraftPackage node-centered o ContentPackageDto final.",
                )
        }
    }

    private suspend fun prepareAuthoringDraft(
        root: JsonObject,
        rawJson: String,
        sourceLabel: String,
        validationProfile: ImportValidationProfile,
    ): ImportPreparationResult {
        val packageKey =
            root["packageKey"]
                ?.let { element -> runCatching { element.jsonPrimitive.content }.getOrNull() }
                .orEmpty()
                .ifBlank { sourceLabel }
        val schemaVersion =
            root["schemaVersion"]
                ?.let { element -> runCatching { element.jsonPrimitive.content.toIntOrNull() }.getOrNull() }
                ?: 1
        val draft =
            runCatching { StrictDraftJson.decodeFromString<AuthoringDraftPackageDto>(rawJson) }.getOrElse { error ->
                return invalidResult(
                    sourceLabel = sourceLabel,
                    packageId = packageKey,
                    schemaVersion = schemaVersion,
                    code = "authoring_draft_parse_error",
                    message = "El authoring_draft no respeta el contrato: ${error.message ?: "formato inválido"}",
                    path = "$",
                    expected = "AuthoringDraftPackage estricto con packageKey, course y units[].nodeDrafts[].",
                    actual = error.message ?: "formato inválido",
                    hint = "Eliminá claves desconocidas o corregí la forma del draft; solo schema reconocido puede ir como metadata.",
                )
            }
        val compiled = authoringDraftCompiler.compile(draft, timeProvider.now())
        val contentPackage = compiled.contentPackage
        if (contentPackage == null) {
            return ImportPreparationResult(
                sourceLabel = sourceLabel,
                kind = PreparedImportKind.AUTHORING_DRAFT,
                packageId = compiled.packageId,
                schemaVersion = schemaVersion,
                contentPackage = null,
                contentPackageJson = null,
                report = compiled.report,
            )
        }
        val finalReport = importValidator.validate(contentPackage, validationProfile)
        return ImportPreparationResult(
            sourceLabel = sourceLabel,
            kind = PreparedImportKind.AUTHORING_DRAFT,
            packageId = contentPackage.packageId,
            schemaVersion = contentPackage.schemaVersion,
            contentPackage = contentPackage,
            contentPackageJson = AppJson.encodeToString(contentPackage),
            report = compiled.report + finalReport,
            validationProfile = validationProfile,
        )
    }

    private suspend fun prepareContentPackage(
        rawJson: String,
        sourceLabel: String,
        validationProfile: ImportValidationProfile,
    ): ImportPreparationResult {
        val contentPackage =
            runCatching { AppJson.decodeFromString<ContentPackageDto>(rawJson) }.getOrElse { error ->
                return invalidResult(
                    sourceLabel = sourceLabel,
                    code = "content_package_parse_error",
                    message = "El content_package no respeta el contrato: ${error.message ?: "formato inválido"}",
                    path = "$",
                    expected = "ContentPackageDto final con packageId, schemaVersion, courses, units, outcomes, nodes e items.",
                    actual = error.message ?: "formato inválido",
                    hint = "No pegues un draft intermedio en el contrato final; si es authoring_draft debe traer packageKey.",
                )
            }
        val report = importValidator.validate(contentPackage, validationProfile)
        return ImportPreparationResult(
            sourceLabel = sourceLabel,
            kind = PreparedImportKind.CONTENT_PACKAGE,
            packageId = contentPackage.packageId,
            schemaVersion = contentPackage.schemaVersion,
            contentPackage = contentPackage,
            contentPackageJson = AppJson.encodeToString(contentPackage),
            report = report,
            validationProfile = validationProfile,
        )
    }

    private fun invalidResult(
        sourceLabel: String,
        code: String,
        message: String,
        path: String? = null,
        expected: String? = null,
        actual: String? = null,
        hint: String? = null,
    ): ImportPreparationResult =
        invalidResult(
            sourceLabel = sourceLabel,
            packageId = sourceLabel,
            schemaVersion = 0,
            code = code,
            message = message,
            path = path,
            expected = expected,
            actual = actual,
            hint = hint,
        )

    private fun invalidResult(
        sourceLabel: String,
        packageId: String,
        schemaVersion: Int,
        code: String,
        message: String,
        path: String? = null,
        expected: String? = null,
        actual: String? = null,
        hint: String? = null,
    ): ImportPreparationResult =
        ImportPreparationResult(
            sourceLabel = sourceLabel,
            kind = PreparedImportKind.INVALID,
            packageId = packageId,
            schemaVersion = schemaVersion,
            contentPackage = null,
            contentPackageJson = null,
            report =
                ValidationReport(
                    structuralErrors =
                        listOf(
                            ValidationMessage(
                                code = code,
                                message = message,
                                path = path,
                                expected = expected,
                                actual = actual,
                                hint = hint,
                            ),
                        ),
                    authoringWarnings = emptyList(),
                ),
            validationProfile = ImportValidationProfile.PEDAGOGICAL_NODE,
        )

    private fun invalidSchemaResult(sourceLabel: String): ImportPreparationResult =
        invalidResult(
            sourceLabel = sourceLabel,
            code = "json_schema_invalid",
            message = "El campo schema debe ser texto no vacío.",
            path = "$.schema",
            expected = "Texto: content_package, authoring_draft, AuthoringDraftPackage o QuestionDraftPackage.",
            actual = "schema ausente, nulo o no textual.",
            hint = "Quitá schema o usá un alias reconocido como texto.",
        )

    private fun unknownSchemaResult(
        sourceLabel: String,
        schema: String,
        root: JsonObject,
    ): ImportPreparationResult =
        invalidResult(
            sourceLabel = sourceLabel,
            code = "json_unknown_package_schema",
            message = "El schema '$schema' no es un contrato de importación conocido.",
            path = "$.schema",
            expected = "content_package, authoring_draft, AuthoringDraftPackage o QuestionDraftPackage.",
            actual = "schema=$schema; campos raíz: ${root.fieldList()}",
            hint = "Si el JSON ya tiene packageKey/course/units/nodeDrafts, usá authoring_draft. Si tiene packageId, usá content_package.",
        )

    private fun flatQuestionDraftResult(
        sourceLabel: String,
        schema: String?,
        root: JsonObject,
    ): ImportPreparationResult =
        invalidResult(
            sourceLabel = sourceLabel,
            code = "question_draft_flat_package",
            message = "El JSON parece un paquete plano de preguntas; la app requiere AuthoringDraftPackage centrado en nodos.",
            path = "$.questions",
            expected = "packageKey, course y units[].nodeDrafts[] con facets, mustKnow, commonErrors, roles y cobertura.",
            actual = "schema=${schema ?: "sin schema"}; campos raíz: ${root.fieldList()}",
            hint = "Convertí las preguntas a nodos pedagógicos antes de importar. La app no inventa nodeDrafts, facets, mustKnow ni commonErrors.",
        )

    private fun authoringShapeMissingResult(
        sourceLabel: String,
        schema: String,
        root: JsonObject,
    ): ImportPreparationResult =
        invalidResult(
            sourceLabel = sourceLabel,
            code = "authoring_draft_shape_missing",
            message = "El schema '$schema' apunta a authoring_draft, pero la forma raíz no es AuthoringDraftPackage.",
            path = "$",
            expected = "packageKey, course y units[].nodeDrafts[].",
            actual = "Campos raíz: ${root.fieldList()}",
            hint = "No alcanza con declarar schema; el JSON debe traer la estructura node-centered completa.",
        )

    private fun contentShapeMissingResult(
        sourceLabel: String,
        schema: String,
        root: JsonObject,
    ): ImportPreparationResult =
        invalidResult(
            sourceLabel = sourceLabel,
            code = "content_package_shape_missing",
            message = "El schema '$schema' apunta a content_package, pero falta packageId.",
            path = "$",
            expected = "ContentPackageDto con packageId, schemaVersion, courses, units, outcomes, nodes e items.",
            actual = "Campos raíz: ${root.fieldList()}",
            hint = "Si es un draft generado por IA, usá AuthoringDraftPackage con packageKey.",
        )

    private companion object {
        val StrictDraftJson =
            Json {
                ignoreUnknownKeys = false
                explicitNulls = false
                encodeDefaults = true
            }
    }
}

private operator fun ValidationReport.plus(other: ValidationReport): ValidationReport =
    ValidationReport(
        structuralErrors = structuralErrors + other.structuralErrors,
        authoringWarnings = authoringWarnings + other.authoringWarnings,
    )

private sealed interface SchemaRead {
    data object Missing : SchemaRead
    data object Invalid : SchemaRead
    data class Valid(val value: String) : SchemaRead
}

private val ContentSchemaAliases =
    setOf(
        "content_package",
        "ContentPackage",
        "ContentPackageDto",
    )

private val AuthoringSchemaAliases =
    setOf(
        "authoring_draft",
        "AuthoringDraftPackage",
        "AuthoringDraftPackageDto",
        "QuestionDraftPackage",
    )

private val NodeTypeByToken = NodeType.entries.associateBy { it.name.canonicalEnumToken() }
private val ItemFormatByToken = ItemFormat.entries.associateBy { it.name.canonicalEnumToken() }
private val FacetTypeByToken = FacetType.entries.associateBy { it.name.canonicalEnumToken() }
private val ItemRoleByToken = ItemRole.entries.associateBy { it.name.canonicalEnumToken() }

private fun JsonObject.readSchema(): SchemaRead {
    val schemaElement = this["schema"] ?: return SchemaRead.Missing
    val schema = runCatching { schemaElement.jsonPrimitive.contentOrNull }.getOrNull()
    return schema?.trim()?.takeIf(String::isNotBlank)?.let(SchemaRead::Valid) ?: SchemaRead.Invalid
}

private fun String.isKnownSchemaAlias(): Boolean = this in ContentSchemaAliases || this in AuthoringSchemaAliases

private fun JsonObject.looksLikeFlatQuestionDraft(): Boolean =
    "questions" in this && "packageKey" !in this && "packageId" !in this

private fun JsonObject.normalizedAuthoringDraftJson(): String =
    AppJson.encodeToString(
        JsonObject(
            filterKeys { key -> key != "schema" }
                .mapValues { (key, value) -> normalizeAuthoringElement(key, value) },
        ),
    )

private fun JsonObject.contentPackageValidationProfile(requested: ImportValidationProfile): Result<ImportValidationProfile> {
    val modeElement = this["importMode"] ?: return Result.success(requested)
    val mode =
        runCatching { modeElement.jsonPrimitive.contentOrNull }
            .getOrNull()
            ?.trim()
            ?.lowercase()
            ?: return Result.failure(IllegalArgumentException("El campo importMode debe ser texto."))
    return when (mode) {
        "full", "republish", "replace" -> Result.success(requested)
        "additive", "incremental", "append" ->
            Result.success(
                if (requested == ImportValidationProfile.REVIEWED_QUESTION_BANK) {
                    requested
                } else {
                    ImportValidationProfile.ADDITIVE_CONTENT_PACKAGE
                },
            )
        else -> Result.failure(IllegalArgumentException("El importMode '$mode' no está soportado."))
    }
}

private fun JsonObject.contentPackageJson(): String =
    AppJson.encodeToString(JsonObject(filterKeys { key -> key != "schema" && key != "importMode" }))

private fun JsonObject.fieldList(): String = keys.sorted().joinToString(prefix = "[", postfix = "]")

private fun normalizeAuthoringElement(
    key: String,
    element: JsonElement,
): JsonElement =
    when (element) {
        is JsonObject ->
            JsonObject(
                element.mapValues { (childKey, childValue) ->
                    normalizeAuthoringElement(childKey, childValue)
                },
            )
        is JsonArray -> JsonArray(element.map { child -> normalizeAuthoringElement(key, child) })
        is JsonPrimitive -> normalizeAuthoringPrimitive(key, element)
    }

private fun normalizeAuthoringPrimitive(
    key: String,
    primitive: JsonPrimitive,
): JsonPrimitive {
    if (!primitive.isString) return primitive
    val raw = primitive.contentOrNull ?: return primitive
    val normalized =
        when (key) {
            "nodeTypeHint" -> NodeTypeByToken[raw.canonicalEnumToken()]?.name
            "format" -> ItemFormatByToken[raw.canonicalEnumToken()]?.name
            "facet", "facets" -> FacetTypeByToken[raw.canonicalEnumToken()]?.name
            "roleHint" -> ItemRoleByToken[raw.canonicalEnumToken()]?.name
            else -> null
        }
    return normalized?.let(::JsonPrimitive) ?: primitive
}

private fun String.canonicalEnumToken(): String =
    trim()
        .replace('-', '_')
        .replace(' ', '_')
        .uppercase()
