package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.data.importing.ContentImportRules
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContentImportRulesRepairTest {
    @Test
    fun `open recall card stored as deep only is repaired into the daily review`() {
        val repaired =
            ContentImportRules.repairedSurfacesOrNull(
                format = ItemFormat.ONE_SENTENCE_EXPLANATION,
                role = ItemRole.CORE,
                current = listOf(Surface.IN_APP_DEEP),
            )

        assertThat(repaired).isNotNull()
        assertThat(repaired).containsExactly(Surface.IN_APP_QUICK, Surface.IN_APP_DEEP)
    }

    @Test
    fun `already correct strict card needs no repair`() {
        val current = ContentImportRules.allowedSurfacesFor(ItemFormat.MULTIPLE_CHOICE, ItemRole.CORE)

        val repaired =
            ContentImportRules.repairedSurfacesOrNull(ItemFormat.MULTIPLE_CHOICE, ItemRole.CORE, current)

        assertThat(repaired).isNull()
    }

    @Test
    fun `boss open card stays deep only`() {
        val repaired =
            ContentImportRules.repairedSurfacesOrNull(
                format = ItemFormat.ONE_SENTENCE_EXPLANATION,
                role = ItemRole.BOSS,
                current = listOf(Surface.IN_APP_DEEP),
            )

        assertThat(repaired).isNull()
    }

    @Test
    fun `a mere ordering difference is not treated as drift`() {
        val repaired =
            ContentImportRules.repairedSurfacesOrNull(
                format = ItemFormat.ONE_SENTENCE_EXPLANATION,
                role = ItemRole.CORE,
                current = listOf(Surface.IN_APP_DEEP, Surface.IN_APP_QUICK),
            )

        assertThat(repaired).isNull()
    }
}
