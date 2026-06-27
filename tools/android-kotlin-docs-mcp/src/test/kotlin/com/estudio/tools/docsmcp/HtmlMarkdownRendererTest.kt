package com.estudio.tools.docsmcp

import kotlin.test.Test
import kotlin.test.assertTrue
import org.jsoup.Jsoup

class HtmlMarkdownRendererTest {
    @Test
    fun `renders headings paragraphs lists and code blocks`() {
        val document = Jsoup.parse(
            """
            <html>
              <body>
                <main>
                  <h1>Compose State</h1>
                  <p>State should be hoisted.</p>
                  <ul>
                    <li>Remember UI state</li>
                    <li>Avoid business logic in composables</li>
                  </ul>
                  <pre><code>val count = 1</code></pre>
                </main>
              </body>
            </html>
            """.trimIndent()
        )

        val markdown = HtmlMarkdownRenderer.render(document)

        assertTrue(markdown.contains("# Compose State"))
        assertTrue(markdown.contains("State should be hoisted."))
        assertTrue(markdown.contains("- Remember UI state"))
        assertTrue(markdown.contains("```"))
        assertTrue(markdown.contains("val count = 1"))
    }
}
