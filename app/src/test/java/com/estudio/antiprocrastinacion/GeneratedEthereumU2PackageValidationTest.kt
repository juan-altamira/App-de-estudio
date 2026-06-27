package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.data.importing.AuthoringDraftCompiler
import com.estudio.antiprocrastinacion.app.data.importing.AuthoringDraftValidator
import com.estudio.antiprocrastinacion.app.data.importing.DefaultImportPreparationService
import com.estudio.antiprocrastinacion.app.data.importing.DefaultImportValidator
import com.estudio.antiprocrastinacion.app.data.local.db.ArchivableNodeRef
import com.estudio.antiprocrastinacion.app.data.local.db.nodeIdsToArchiveOnImport
import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidationProfile
import com.estudio.antiprocrastinacion.app.ui.common.TimeProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class GeneratedEthereumU2PackageValidationTest {
    private val validator = DefaultImportValidator()
    private val preparationService =
        DefaultImportPreparationService(
            importValidator = validator,
            authoringDraftCompiler = AuthoringDraftCompiler(AuthoringDraftValidator()),
            timeProvider = GeneratedPackageFixedTimeProvider,
        )

    @Test
    fun `generated Ethereum unit 2 content package is additive and does not include unit 1`() = runTest {
        val rawJson = generatedPackageFile("ethereum_unidad_2_mapa_transaccion_content_package.json").readText()
        val prepared = preparationService.prepare(rawJson, "generated:ethereum-u2")
        val contentPackage = prepared.contentPackage ?: error("Generated package should prepare as content package.")

        assertThat(prepared.validationProfile).isEqualTo(ImportValidationProfile.ADDITIVE_CONTENT_PACKAGE)
        assertThat(prepared.report.structuralErrors).isEmpty()
        assertThat(prepared.canImport).isTrue()
        assertThat(Json.parseToJsonElement(rawJson).jsonObject["importMode"]?.jsonPrimitive?.content).isEqualTo("additive")
        assertThat(contentPackage.courses.map { it.courseId }).containsExactly("ethereum")
        assertThat(contentPackage.units.map { it.unitId }).containsExactly("eth_unidad_2")
        assertThat(contentPackage.nodes.map { it.nodeId }).doesNotContain("eth_modelo_mental")
        assertThat(contentPackage.items).hasSize(28)

        val archivedByFileImport =
            nodeIdsToArchiveOnImport(
                existingNodesOfOrigin = listOf(ArchivableNodeRef(nodeId = "eth_modelo_mental", courseId = "ethereum")),
                affectedCourseIds = contentPackage.courses.map { it.courseId }.toSet(),
                incomingNodeIds = contentPackage.nodes.map { it.nodeId }.toSet(),
                profile = ImportValidationProfile.ADDITIVE_CONTENT_PACKAGE,
            )

        assertThat(archivedByFileImport).isEmpty()
    }
}

private data object GeneratedPackageFixedTimeProvider : TimeProvider {
    override fun now(): Long = 1L
}

private fun generatedPackageFile(fileName: String): File {
    val start = File(System.getProperty("user.dir") ?: error("No se pudo resolver user.dir.")).absoluteFile
    return generateSequence(start) { it.parentFile }
        .map { File(File(it, "generated-content"), fileName) }
        .firstOrNull { it.isFile }
        ?: error("No se encontró generated-content/$fileName desde ${start.path}.")
}
