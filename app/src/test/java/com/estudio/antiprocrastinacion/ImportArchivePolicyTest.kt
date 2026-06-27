package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.data.local.db.ArchivableNodeRef
import com.estudio.antiprocrastinacion.app.data.local.db.additiveImport
import com.estudio.antiprocrastinacion.app.data.local.db.nodeIdsToArchiveOnImport
import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidationProfile
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImportArchivePolicyTest {
    private val existing =
        listOf(
            ArchivableNodeRef(nodeId = "bio__u1__contenido", courseId = "bio"),
            ArchivableNodeRef(nodeId = "bio__u2__contenido", courseId = "bio"),
            ArchivableNodeRef(nodeId = "mate__u1__contenido", courseId = "mate"),
        )

    @Test
    fun `reviewed question bank import is additive and never archives siblings`() {
        assertThat(additiveImport(ImportValidationProfile.REVIEWED_QUESTION_BANK)).isTrue()

        val toArchive =
            nodeIdsToArchiveOnImport(
                existingNodesOfOrigin = existing,
                affectedCourseIds = setOf("bio"),
                incomingNodeIds = setOf("bio__u3__contenido"),
                profile = ImportValidationProfile.REVIEWED_QUESTION_BANK,
            )

        // Adding a new unit to course "bio" must NOT archive bio__u1 / bio__u2.
        assertThat(toArchive).isEmpty()
    }

    @Test
    fun `explicit additive content package import never archives siblings`() {
        assertThat(additiveImport(ImportValidationProfile.ADDITIVE_CONTENT_PACKAGE)).isTrue()

        val toArchive =
            nodeIdsToArchiveOnImport(
                existingNodesOfOrigin = existing,
                affectedCourseIds = setOf("bio"),
                incomingNodeIds = setOf("bio__u3__contenido"),
                profile = ImportValidationProfile.ADDITIVE_CONTENT_PACKAGE,
            )

        // Adding a unit through Archivo must not archive existing units of the same course.
        assertThat(toArchive).isEmpty()
    }

    @Test
    fun `full package import still archives nodes missing from the affected course`() {
        assertThat(additiveImport(ImportValidationProfile.PEDAGOGICAL_NODE)).isFalse()

        val toArchive =
            nodeIdsToArchiveOnImport(
                existingNodesOfOrigin = existing,
                affectedCourseIds = setOf("bio"),
                incomingNodeIds = setOf("bio__u1__contenido"),
                profile = ImportValidationProfile.PEDAGOGICAL_NODE,
            )

        // Republish semantics: bio__u2 is gone from the package, so it is archived; mate is untouched.
        assertThat(toArchive).containsExactly("bio__u2__contenido")
    }
}
