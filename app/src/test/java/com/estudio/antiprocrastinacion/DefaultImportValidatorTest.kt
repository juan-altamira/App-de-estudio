package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.data.importing.DefaultImportValidator
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
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultImportValidatorTest {
    private val validator = DefaultImportValidator()

    @Test
    fun `valid package imports with warnings only when authoring is weak`() = runTest {
        val report = validator.validate(validPackage())

        assertThat(report.canImport).isTrue()
        assertThat(report.structuralErrors).isEmpty()
        assertThat(report.authoringWarnings).isNotEmpty()
    }

    @Test
    fun `surface easy ready requires four easy items`() = runTest {
        val broken =
            validPackage().copy(
                items = validPackage().items.take(3),
            )

        val report = validator.validate(broken)

        assertThat(report.canImport).isFalse()
        assertThat(report.structuralErrors.any { it.code.startsWith("node_surface_easy_insufficient") }).isTrue()
    }

    @Test
    fun `duplicate ids block import`() = runTest {
        val duplicated =
            validPackage().copy(
                nodes = validPackage().nodes + validPackage().nodes.first(),
            )

        val report = validator.validate(duplicated)

        assertThat(report.canImport).isFalse()
        assertThat(report.structuralErrors.any { it.code == "node_ids_duplicate" }).isTrue()
    }
}

private fun validPackage(): ContentPackageDto =
    ContentPackageDto(
        packageId = "demo",
        schemaVersion = 1,
        generatedAt = 1L,
        contentHash = "hash",
        origin = ContentOrigin.DEMO,
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
                    facets = listOf(FacetType.DEFINICION_FUNCIONAL, FacetType.ERROR_TIPICO),
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
                sampleItem("i1", ItemRole.CORE, ItemFormat.MULTIPLE_CHOICE, 1),
                sampleItem("i2", ItemRole.VARIANT, ItemFormat.TRUE_FALSE, 1),
                sampleItem("i3", ItemRole.VARIANT, ItemFormat.FILL_ONE_WORD, 1),
                sampleItem("i4", ItemRole.RESCUE, ItemFormat.MULTIPLE_CHOICE, 1),
                sampleItem("i5", ItemRole.TRAP, ItemFormat.MINI_SCENARIO_MCQ, 2),
            ),
    )

private fun sampleItem(id: String, role: ItemRole, format: ItemFormat, friction: Int): ItemDto {
    val options =
        if (format == ItemFormat.MULTIPLE_CHOICE || format == ItemFormat.MINI_SCENARIO_MCQ) {
            listOf(
                ItemOptionDto(id = "a", text = "Correcta", isCorrect = true),
                ItemOptionDto(id = "b", text = "Incorrecta", isCorrect = false),
            )
        } else {
            emptyList()
        }
    val correctAnswer = if (format == ItemFormat.TRUE_FALSE) "Verdadero" else "Correcta"
    return ItemDto(
        itemId = id,
        nodeId = "node",
        facet = FacetType.DEFINICION_FUNCIONAL,
        format = format,
        frictionLevel = friction,
        difficultySeed = 0.2,
        itemRole = role,
        allowedSurfaces = listOf(Surface.IN_APP_QUICK, Surface.IN_APP_DEEP, Surface.BACK_MICRO),
        cooldownHours = 1.0,
        stem = "Pregunta",
        correctAnswer = correctAnswer,
        feedbackShort = "Feedback",
        coversMustKnow = listOf("mk"),
        options = options,
        version = 1,
        updatedAt = 1L,
    )
}
