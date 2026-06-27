package com.estudio.tools.docsmcp

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal object HtmlMarkdownRenderer {
    fun render(document: Document): String {
        val root = document.selectFirst(
            "main, article, [role=main], .devsite-article-body, .page-content, .main-content"
        ) ?: document.body()

        val lines = mutableListOf<String>()
        renderBlock(root, lines, 0)

        return lines
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun renderBlock(element: Element, lines: MutableList<String>, depth: Int) {
        when (element.normalName()) {
            "script", "style", "nav", "footer", "header", "aside", "noscript", "form" -> return
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = element.normalName().removePrefix("h").toIntOrNull()?.coerceIn(1, 6) ?: 1
                val text = normalizeInlineText(element.text())
                if (text.isNotEmpty()) {
                    lines += "${"#".repeat(level)} $text"
                    lines += ""
                }
            }
            "p" -> {
                val text = normalizeInlineText(element.text())
                if (text.isNotEmpty()) {
                    lines += text
                    lines += ""
                }
            }
            "ul", "ol" -> {
                element.children().forEach { child ->
                    renderBlock(child, lines, depth)
                }
                if (lines.lastOrNull()?.isNotEmpty() == true) {
                    lines += ""
                }
            }
            "li" -> {
                val prefix = "  ".repeat(depth) + "- "
                val text = normalizeInlineText(element.ownText().ifBlank { element.text() })
                if (text.isNotEmpty()) {
                    lines += prefix + text
                }

                element.children()
                    .filter { child -> child.normalName() == "ul" || child.normalName() == "ol" }
                    .forEach { nested ->
                        renderBlock(nested, lines, depth + 1)
                    }
            }
            "pre" -> {
                val code = element.wholeText().trim('\n', '\r')
                if (code.isNotBlank()) {
                    lines += "```"
                    lines += code
                    lines += "```"
                    lines += ""
                }
            }
            "table" -> {
                element.select("tr").forEach { row ->
                    val cells = row.select("th, td")
                        .map { normalizeInlineText(it.text()) }
                        .filter { it.isNotEmpty() }
                    if (cells.isNotEmpty()) {
                        lines += cells.joinToString(" | ")
                    }
                }
                if (lines.lastOrNull()?.isNotEmpty() == true) {
                    lines += ""
                }
            }
            else -> {
                element.children().forEach { child ->
                    renderBlock(child, lines, depth)
                }
            }
        }
    }

    private fun normalizeInlineText(text: String): String {
        return text.replace(Regex("\\s+"), " ").trim()
    }
}
