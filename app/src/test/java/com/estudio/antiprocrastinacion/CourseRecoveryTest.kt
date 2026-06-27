package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.data.local.db.RecoveryNoticeStore
import com.estudio.antiprocrastinacion.app.data.local.db.computeMissingPackages
import com.estudio.antiprocrastinacion.app.model.content.ContentOrigin
import com.estudio.antiprocrastinacion.app.model.content.FacetType
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.NodeType
import com.estudio.antiprocrastinacion.app.model.json.ContentPackageDto
import com.estudio.antiprocrastinacion.app.model.json.CourseDto
import com.estudio.antiprocrastinacion.app.model.json.ItemDto
import com.estudio.antiprocrastinacion.app.model.json.NodeDto
import com.estudio.antiprocrastinacion.app.model.json.OutcomeDto
import com.estudio.antiprocrastinacion.app.model.json.UnitDto
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CourseRecoveryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `nothing missing returns no packages`() {
        val result =
            computeMissingPackages(
                packages = listOf(samplePackage()),
                currentCourseIds = setOf("c1"),
                currentUnitIds = setOf("u1"),
                currentOutcomeIds = setOf("o1"),
                currentNodeIds = setOf("n1"),
                currentItemIds = setOf("i1", "i2"),
            )

        assertThat(result).isEmpty()
    }

    @Test
    fun `a fully deleted course is returned in full`() {
        val result =
            computeMissingPackages(
                packages = listOf(samplePackage()),
                currentCourseIds = emptySet(),
                currentUnitIds = emptySet(),
                currentOutcomeIds = emptySet(),
                currentNodeIds = emptySet(),
                currentItemIds = emptySet(),
            )

        assertThat(result).hasSize(1)
        assertThat(result[0].courses.map { it.courseId }).containsExactly("c1")
        assertThat(result[0].units.map { it.unitId }).containsExactly("u1")
        assertThat(result[0].nodes.map { it.nodeId }).containsExactly("n1")
        assertThat(result[0].items.map { it.itemId }).containsExactly("i1", "i2")
    }

    @Test
    fun `only the missing items are returned for a surviving course (additive, never touches survivors)`() {
        // El curso/unidad/outcome/nodo y la tarjeta i1 sobreviven; solo falta i2.
        val result =
            computeMissingPackages(
                packages = listOf(samplePackage()),
                currentCourseIds = setOf("c1"),
                currentUnitIds = setOf("u1"),
                currentOutcomeIds = setOf("o1"),
                currentNodeIds = setOf("n1"),
                currentItemIds = setOf("i1"),
            )

        assertThat(result).hasSize(1)
        // Nada de lo que sobrevivió se reincluye → no se vuelve a tocar.
        assertThat(result[0].courses).isEmpty()
        assertThat(result[0].units).isEmpty()
        assertThat(result[0].outcomes).isEmpty()
        assertThat(result[0].nodes).isEmpty()
        // Solo la tarjeta faltante.
        assertThat(result[0].items.map { it.itemId }).containsExactly("i2")
    }

    @Test
    fun `recovery notice persists until acknowledged`() {
        val store = RecoveryNoticeStore(File(tempFolder.root, "recovery-notice.json"))

        assertThat(store.activeNotice()).isNull()

        store.setNotice("Se borraron tus cursos y se recuperaron.", now = 123L)
        assertThat(store.activeNotice()?.message).isEqualTo("Se borraron tus cursos y se recuperaron.")

        store.acknowledge()
        assertThat(store.activeNotice()).isNull()
    }

    private fun samplePackage(): ContentPackageDto =
        ContentPackageDto(
            packageId = "p1",
            schemaVersion = 1,
            generatedAt = 1L,
            origin = ContentOrigin.IMPORTED,
            courses = listOf(CourseDto(courseId = "c1", title = "Modelo mental de Ethereum", version = 1, updatedAt = 1L)),
            units = listOf(UnitDto(unitId = "u1", courseId = "c1", title = "u", orderIndex = 1, version = 1, updatedAt = 1L)),
            outcomes = listOf(OutcomeDto(outcomeId = "o1", unitId = "u1", title = "o", version = 1, updatedAt = 1L)),
            nodes = listOf(nodeDto("n1")),
            items = listOf(itemDto("i1"), itemDto("i2")),
        )

    private fun nodeDto(id: String): NodeDto =
        NodeDto(
            nodeId = id,
            courseId = "c1",
            unitId = "u1",
            outcomeIds = listOf("o1"),
            title = "t-$id",
            coreClaim = "claim",
            type = NodeType.CONCEPT,
            weightExam = 0.5,
            prerequisites = emptyList(),
            facets = listOf(FacetType.DEFINICION_FUNCIONAL),
            mustKnow = emptyList(),
            commonErrors = emptyList(),
            minimumMasteryDefinition = "m",
            surfaceEasyReady = true,
            surfaceEasyItemCount = 1,
            sourceRefs = emptyList(),
            version = 1,
            updatedAt = 1L,
        )

    private fun itemDto(id: String): ItemDto =
        ItemDto(
            itemId = id,
            nodeId = "n1",
            facet = FacetType.DEFINICION_FUNCIONAL,
            format = ItemFormat.MULTIPLE_CHOICE,
            frictionLevel = 1,
            difficultySeed = 0.2,
            itemRole = ItemRole.CORE,
            allowedSurfaces = emptyList(),
            cooldownHours = 1.0,
            stem = "s-$id",
            correctAnswer = "a",
            feedbackShort = "f",
            coversMustKnow = emptyList(),
            version = 1,
            updatedAt = 1L,
        )
}
