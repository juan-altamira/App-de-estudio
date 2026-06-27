package com.estudio.tools.docsmcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AllowedSourcesTest {
    @Test
    fun `allows official android docs`() {
        val allowed = AllowedSources.requireAllowedUrl(
            "https://developer.android.com/topic/architecture/recommendations"
        )

        assertEquals(DocDomain.ANDROID, allowed.domain)
        assertTrue(AllowedSources.isAllowed(allowed.uri))
    }

    @Test
    fun `rejects unsupported hosts`() {
        assertFailsWith<IllegalArgumentException> {
            AllowedSources.requireAllowedUrl("https://example.com/not-allowed")
        }
    }

    @Test
    fun `rejects kotlin pages outside docs section`() {
        assertFailsWith<IllegalArgumentException> {
            AllowedSources.requireAllowedUrl(
                "https://kotlinlang.org/community/",
                requestedDomain = DocDomain.KOTLIN,
            )
        }
    }

    @Test
    fun `builds release notes url from validated component slug`() {
        val uri = AllowedSources.releaseNotesUri("room")

        assertEquals(
            "https://developer.android.com/jetpack/androidx/releases/room",
            uri.toString(),
        )
    }
}
