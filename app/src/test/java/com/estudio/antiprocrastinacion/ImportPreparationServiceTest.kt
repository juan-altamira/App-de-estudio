package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.data.importing.AuthoringDraftCompiler
import com.estudio.antiprocrastinacion.app.data.importing.AuthoringDraftValidator
import com.estudio.antiprocrastinacion.app.data.importing.DefaultImportPreparationService
import com.estudio.antiprocrastinacion.app.data.importing.DefaultImportValidator
import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidationProfile
import com.estudio.antiprocrastinacion.app.domain.repository.PreparedImportKind
import com.estudio.antiprocrastinacion.app.model.content.ContentOrigin
import com.estudio.antiprocrastinacion.app.model.content.FacetType
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.NodeType
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.json.ContentPackageDto
import com.estudio.antiprocrastinacion.app.model.json.CourseDto
import com.estudio.antiprocrastinacion.app.model.json.ItemDto
import com.estudio.antiprocrastinacion.app.model.json.ItemOptionDto
import com.estudio.antiprocrastinacion.app.model.json.NodeDto
import com.estudio.antiprocrastinacion.app.model.json.OutcomeDto
import com.estudio.antiprocrastinacion.app.model.json.UnitDto
import com.estudio.antiprocrastinacion.app.ui.common.AppJson
import com.estudio.antiprocrastinacion.app.ui.common.TimeProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Test

class ImportPreparationServiceTest {
    private val service =
        DefaultImportPreparationService(
            importValidator = DefaultImportValidator(),
            authoringDraftCompiler = AuthoringDraftCompiler(AuthoringDraftValidator()),
            timeProvider = ImportPreparationFixedTimeProvider(100L),
        )

    @Test
    fun `prepare keeps content package import backwards compatible`() = runTest {
        val rawJson = AppJson.encodeToString(validContentPackage())

        val result = service.prepare(rawJson, "content:test")

        assertThat(result.kind).isEqualTo(PreparedImportKind.CONTENT_PACKAGE)
        assertThat(result.canImport).isTrue()
        assertThat(result.contentPackage?.packageId).isEqualTo("pkg")
        assertThat(result.validationProfile).isEqualTo(ImportValidationProfile.PEDAGOGICAL_NODE)
    }

    @Test
    fun `prepare marks content package importMode additive as additive profile`() = runTest {
        val rawJson =
            AppJson
                .encodeToString(validContentPackage())
                .replaceFirst("{", """{"importMode":"additive",""")

        val result = service.prepare(rawJson, "content:additive")

        assertThat(result.kind).isEqualTo(PreparedImportKind.CONTENT_PACKAGE)
        assertThat(result.canImport).isTrue()
        assertThat(result.contentPackage?.packageId).isEqualTo("pkg")
        assertThat(result.validationProfile).isEqualTo(ImportValidationProfile.ADDITIVE_CONTENT_PACKAGE)
        assertThat(result.contentPackageJson).doesNotContain("importMode")
    }

    @Test
    fun `prepare rejects unknown content package importMode`() = runTest {
        val rawJson =
            AppJson
                .encodeToString(validContentPackage())
                .replaceFirst("{", """{"importMode":"mystery",""")

        val result = service.prepare(rawJson, "content:bad-mode")

        assertThat(result.kind).isEqualTo(PreparedImportKind.INVALID)
        assertThat(result.canImport).isFalse()
        assertThat(result.report.structuralErrors.single().code).isEqualTo("content_package_import_mode_invalid")
    }

    @Test
    fun `prepare compiles authoring draft before final import`() = runTest {
        val rawJson =
            """
            {
              "packageKey": "eth_moderno",
              "course": {"courseKey": "ethereum", "title": "Ethereum"},
              "units": [
                {
                  "unitKey": "bloque_0",
                  "title": "Bloque 0",
                  "outcomes": [{"outcomeKey": "base", "title": "Base"}],
                  "nodeDrafts": [
                    {
                      "nodeKey": "protocolo",
                      "title": "Ethereum como protocolo",
                      "coreClaim": "Ethereum mantiene un estado global verificable.",
                      "nodeTypeHint": "CONCEPT",
                      "outcomeKeys": ["base"],
                      "facets": ["DEFINICION_FUNCIONAL", "DIFERENCIA_ENTRE_CONCEPTOS", "ERROR_TIPICO"],
                      "mustKnow": ["estado_global"],
                      "commonErrors": ["confundir con app"],
                      "minimumMasteryDefinition": "Lo distingue de una app.",
                      "items": [
                        {
                          "itemKey": "core",
                          "format": "MULTIPLE_CHOICE",
                          "facet": "DEFINICION_FUNCIONAL",
                          "roleHint": "CORE",
                          "stem": "¿Qué es Ethereum?",
                          "options": [{"key": "a", "text": "Una app"}, {"key": "b", "text": "Un protocolo"}],
                          "correct": "b",
                          "feedback": "Es protocolo.",
                          "coversMustKnow": ["estado_global"]
                        },
                        {
                          "itemKey": "tf",
                          "format": "TRUE_FALSE",
                          "facet": "DIFERENCIA_ENTRE_CONCEPTOS",
                          "roleHint": "VARIANT",
                          "stem": "Ethereum es solo una app.",
                          "correct": "false",
                          "feedback": "No es solo una app.",
                          "coversMustKnow": ["estado_global"]
                        },
                        {
                          "itemKey": "trap",
                          "format": "MULTIPLE_CHOICE",
                          "facet": "ERROR_TIPICO",
                          "roleHint": "TRAP",
                          "stem": "Error típico sobre Ethereum",
                          "options": [{"key": "a", "text": "App"}, {"key": "b", "text": "Protocolo"}],
                          "correct": "b",
                          "feedback": "No se reduce a app.",
                          "coversMustKnow": ["estado_global"],
                          "targetsCommonErrors": ["confundir con app"]
                        },
                        {
                          "itemKey": "variant",
                          "format": "MULTIPLE_CHOICE",
                          "facet": "DEFINICION_FUNCIONAL",
                          "roleHint": "VARIANT",
                          "stem": "Elegí la definición",
                          "options": [{"key": "a", "text": "Protocolo"}, {"key": "b", "text": "Exchange"}],
                          "correct": "a",
                          "feedback": "Es protocolo.",
                          "coversMustKnow": ["estado_global"]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent()

        val result = service.prepare(rawJson, "draft:test")

        assertThat(result.kind).isEqualTo(PreparedImportKind.AUTHORING_DRAFT)
        assertThat(result.canImport).isTrue()
        assertThat(result.contentPackage?.packageId).isEqualTo("eth_moderno")
        assertThat(result.contentPackage?.nodes?.single()?.nodeId).isEqualTo("ethereum__bloque_0__protocolo")
        assertThat(result.contentPackageJson).isNotNull()
    }

    @Test
    fun `prepare rejects unknown authoring draft keys strictly`() = runTest {
        val rawJson =
            """
            {
              "packageKey": "bad",
              "unexpected": true,
              "course": {"courseKey": "ethereum", "title": "Ethereum"},
              "units": []
            }
            """.trimIndent()

        val result = service.prepare(rawJson, "draft:bad")

        assertThat(result.canImport).isFalse()
        assertThat(result.kind).isEqualTo(PreparedImportKind.INVALID)
        assertThat(result.report.structuralErrors.single().code).isEqualTo("authoring_draft_parse_error")
    }

    @Test
    fun `prepare accepts authoring schema alias and normalizes enum casing`() = runTest {
        val result =
            service.prepare(
                validAuthoringDraftJson(schema = "authoring_draft", lowercaseEnums = true),
                "draft:alias",
            )

        assertThat(result.kind).isEqualTo(PreparedImportKind.AUTHORING_DRAFT)
        assertThat(result.canImport).isTrue()
        assertThat(result.contentPackage?.nodes?.single()?.type).isEqualTo(NodeType.CONCEPT)
        assertThat(result.contentPackage?.items?.first()?.format).isEqualTo(ItemFormat.MULTIPLE_CHOICE)
    }

    @Test
    fun `prepare accepts QuestionDraftPackage alias only for node centered authoring shape`() = runTest {
        val result =
            service.prepare(
                validAuthoringDraftJson(schema = "QuestionDraftPackage"),
                "draft:question-alias",
            )

        assertThat(result.kind).isEqualTo(PreparedImportKind.AUTHORING_DRAFT)
        assertThat(result.canImport).isTrue()
        assertThat(result.contentPackage?.packageId).isEqualTo("eth_moderno")
    }

    @Test
    fun `prepare rejects flat QuestionDraftPackage without inventing nodes`() = runTest {
        val rawJson =
            """
            {
              "schema": "QuestionDraftPackage",
              "questions": [
                {
                  "stem": "¿Qué es Ethereum?",
                  "correct": "Un protocolo"
                }
              ]
            }
            """.trimIndent()

        val result = service.prepare(rawJson, "draft:flat")
        val error = result.report.structuralErrors.single()

        assertThat(result.kind).isEqualTo(PreparedImportKind.INVALID)
        assertThat(result.canImport).isFalse()
        assertThat(error.code).isEqualTo("question_draft_flat_package")
        assertThat(error.path).isEqualTo("$.questions")
        assertThat(error.hint).contains("no inventa")
    }

    @Test
    fun `prepare rejects authoring schema alias when node centered shape is missing`() = runTest {
        val rawJson =
            """
            {
              "schema": "authoring_draft",
              "course": {"courseKey": "ethereum", "title": "Ethereum"},
              "units": []
            }
            """.trimIndent()

        val result = service.prepare(rawJson, "draft:bad-shape")
        val error = result.report.structuralErrors.single()

        assertThat(result.canImport).isFalse()
        assertThat(error.code).isEqualTo("authoring_draft_shape_missing")
        assertThat(error.path).isEqualTo("$")
        assertThat(error.expected).contains("packageKey")
    }
}

private class ImportPreparationFixedTimeProvider(
    private val now: Long,
) : TimeProvider {
    override fun now(): Long = now
}

private fun validContentPackage(): ContentPackageDto =
    ContentPackageDto(
        packageId = "pkg",
        schemaVersion = 1,
        generatedAt = 1L,
        origin = ContentOrigin.IMPORTED,
        courses = listOf(CourseDto("course", "Curso", null, 1, 1L)),
        units = listOf(UnitDto("unit", "course", "Unidad", null, 1, 1, 1L)),
        outcomes = listOf(OutcomeDto("outcome", "unit", "Outcome", null, 1, 1L)),
        nodes =
            listOf(
                NodeDto(
                    nodeId = "node",
                    courseId = "course",
                    unitId = "unit",
                    outcomeIds = listOf("outcome"),
                    title = "Nodo",
                    coreClaim = "Claim",
                    type = NodeType.CONCEPT,
                    weightExam = 0.7,
                    prerequisites = emptyList(),
                    facets =
                        listOf(
                            FacetType.DEFINICION_FUNCIONAL,
                            FacetType.DIFERENCIA_ENTRE_CONCEPTOS,
                            FacetType.ERROR_TIPICO,
                        ),
                    mustKnow = listOf("mk"),
                    commonErrors = listOf("ce"),
                    minimumMasteryDefinition = "define",
                    surfaceEasyReady = true,
                    surfaceEasyItemCount = 4,
                    sourceRefs = listOf("ref"),
                    version = 1,
                    updatedAt = 1L,
                ),
            ),
        items =
            listOf(
                contentItem("i1", ItemRole.CORE),
                contentItem("i2", ItemRole.VARIANT),
                contentItem("i3", ItemRole.VARIANT),
                contentItem("i4", ItemRole.TRAP, commonErrorSignals = listOf("ce")),
            ),
    )

private fun validAuthoringDraftJson(
    schema: String? = null,
    lowercaseEnums: Boolean = false,
): String {
    val schemaField = schema?.let { """"schema": "$it",""" }.orEmpty()
    val nodeType = if (lowercaseEnums) "concept" else "CONCEPT"
    val multipleChoice = if (lowercaseEnums) "multiple_choice" else "MULTIPLE_CHOICE"
    val trueFalse = if (lowercaseEnums) "true_false" else "TRUE_FALSE"
    val definitionFacet = if (lowercaseEnums) "definicion_funcional" else "DEFINICION_FUNCIONAL"
    val differenceFacet = if (lowercaseEnums) "diferencia_entre_conceptos" else "DIFERENCIA_ENTRE_CONCEPTOS"
    val errorFacet = if (lowercaseEnums) "error_tipico" else "ERROR_TIPICO"
    val coreRole = if (lowercaseEnums) "core" else "CORE"
    val variantRole = if (lowercaseEnums) "variant" else "VARIANT"
    val trapRole = if (lowercaseEnums) "trap" else "TRAP"
    return """
        {
          $schemaField
          "packageKey": "eth_moderno",
          "course": {"courseKey": "ethereum", "title": "Ethereum"},
          "units": [
            {
              "unitKey": "bloque_0",
              "title": "Bloque 0",
              "outcomes": [{"outcomeKey": "base", "title": "Base"}],
              "nodeDrafts": [
                {
                  "nodeKey": "protocolo",
                  "title": "Ethereum como protocolo",
                  "coreClaim": "Ethereum mantiene un estado global verificable.",
                  "nodeTypeHint": "$nodeType",
                  "outcomeKeys": ["base"],
                  "facets": ["$definitionFacet", "$differenceFacet", "$errorFacet"],
                  "mustKnow": ["estado_global"],
                  "commonErrors": ["confundir con app"],
                  "minimumMasteryDefinition": "Lo distingue de una app.",
                  "items": [
                    {
                      "itemKey": "core",
                      "format": "$multipleChoice",
                      "facet": "$definitionFacet",
                      "roleHint": "$coreRole",
                      "stem": "¿Qué es Ethereum?",
                      "options": [{"key": "a", "text": "Una app"}, {"key": "b", "text": "Un protocolo"}],
                      "correct": "b",
                      "feedback": "Es protocolo.",
                      "coversMustKnow": ["estado_global"]
                    },
                    {
                      "itemKey": "tf",
                      "format": "$trueFalse",
                      "facet": "$differenceFacet",
                      "roleHint": "$variantRole",
                      "stem": "Ethereum es solo una app.",
                      "correct": "false",
                      "feedback": "No es solo una app.",
                      "coversMustKnow": ["estado_global"]
                    },
                    {
                      "itemKey": "trap",
                      "format": "$multipleChoice",
                      "facet": "$errorFacet",
                      "roleHint": "$trapRole",
                      "stem": "Error típico sobre Ethereum",
                      "options": [{"key": "a", "text": "App"}, {"key": "b", "text": "Protocolo"}],
                      "correct": "b",
                      "feedback": "No se reduce a app.",
                      "coversMustKnow": ["estado_global"],
                      "targetsCommonErrors": ["confundir con app"]
                    },
                    {
                      "itemKey": "variant",
                      "format": "$multipleChoice",
                      "facet": "$definitionFacet",
                      "roleHint": "$variantRole",
                      "stem": "Elegí la definición",
                      "options": [{"key": "a", "text": "Protocolo"}, {"key": "b", "text": "Exchange"}],
                      "correct": "a",
                      "feedback": "Es protocolo.",
                      "coversMustKnow": ["estado_global"]
                    }
                  ]
                }
              ]
            }
          ]
        }
        """.trimIndent()
}

private fun contentItem(
    id: String,
    role: ItemRole,
    commonErrorSignals: List<String> = emptyList(),
): ItemDto =
    ItemDto(
        itemId = id,
        nodeId = "node",
        facet = if (role == ItemRole.TRAP) FacetType.ERROR_TIPICO else FacetType.DEFINICION_FUNCIONAL,
        format = ItemFormat.MULTIPLE_CHOICE,
        frictionLevel = 1,
        difficultySeed = 0.2,
        itemRole = role,
        allowedSurfaces = listOf(Surface.IN_APP_QUICK, Surface.IN_APP_DEEP, Surface.SOCIAL_GATE),
        cooldownHours = 1.0,
        stem = "Pregunta",
        correctAnswer = "Correcta",
        feedbackShort = "Feedback",
        coversMustKnow = listOf("mk"),
        commonErrorSignals = commonErrorSignals,
        options = listOf(ItemOptionDto("a", "Correcta", true), ItemOptionDto("b", "Incorrecta", false)),
        version = 1,
        updatedAt = 1L,
    )
