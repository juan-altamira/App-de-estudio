package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.data.importing.DefaultImportValidator
import com.estudio.antiprocrastinacion.app.data.local.db.safeDecodeContentPackage
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DemoSeedValidationTest {
    private val validator = DefaultImportValidator()

    @Test
    fun `demo seed remains structurally importable`() = runTest {
        val seedFile =
            File("app/src/main/assets/seed/demo_content.json")
                .takeIf { it.exists() }
                ?: File("src/main/assets/seed/demo_content.json")
        val decoded = safeDecodeContentPackage(seedFile.readText())

        assertThat(decoded.isSuccess).isTrue()

        val contentPackage = decoded.getOrThrow()
        val report = validator.validate(contentPackage)

        assertThat(report.structuralErrors).isEmpty()
        assertThat(contentPackage.courses.map { it.courseId }).contains("systems_and_eth")
        assertThat(contentPackage.units.map { it.unitId }).containsAtLeast("eth_consensus", "eth_execution_gas")
    }
}
