package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.data.local.db.invalidJsonImportResult
import com.estudio.antiprocrastinacion.app.data.local.db.safeDecodeContentPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportParsingTest {
    @Test
    fun safeDecodeContentPackage_returnsFailureForInvalidJson() {
        val result = safeDecodeContentPackage("invalid json")

        assertTrue(result.isFailure)
    }

    @Test
    fun invalidJsonImportResult_buildsStructuralError() {
        val result = invalidJsonImportResult(
            sourceLabel = "manual:text-entry",
            error = IllegalArgumentException("Unexpected JSON token"),
        )

        assertFalse(result.imported)
        assertEquals("manual:text-entry", result.packageId)
        assertEquals("json_parse_error", result.report.structuralErrors.single().code)
    }
}
