package com.estudio.tools.docsmcp

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

internal class OfficialDocsService(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val pageCache = ConcurrentHashMap<String, CacheEntry<DocPage>>()
    private val sitemapCache = ConcurrentHashMap<String, CacheEntry<List<URI>>>()

    suspend fun fetchDocument(
        url: String,
        maxChars: Int = 6_000,
        startIndex: Int = 0,
    ): String = withContext(Dispatchers.IO) {
        val allowed = AllowedSources.requireAllowedUrl(url)
        val page = fetchPage(allowed.uri)
        formatPageExcerpt(page, maxChars, startIndex)
    }

    suspend fun searchDocs(
        query: String,
        domain: DocDomain,
        limit: Int = 5,
    ): String = withContext(Dispatchers.IO) {
        val tokens = tokenize(query)
        require(tokens.isNotEmpty()) { "Query must contain at least one searchable term." }

        val candidates = loadDomainUrls(domain)
            .mapNotNull { uri ->
                val baseScore = scoreText(AllowedSources.normalizedSearchText(uri), tokens)
                if (baseScore <= 0) {
                    null
                } else {
                    RankedCandidate(uri = uri, score = baseScore)
                }
            }
            .sortedByDescending { it.score }
            .take(limit.coerceIn(1, 10) * 4)

        if (candidates.isEmpty()) {
            return@withContext buildString {
                appendLine("# Official docs search")
                appendLine("Query: $query")
                appendLine("Domain: ${domain.displayName}")
                appendLine()
                append("No matching URLs were found in the official sitemap index.")
            }
        }

        val enriched = candidates.map { candidate ->
            val page = fetchPage(candidate.uri)
            val titleScore = scoreText(page.title.lowercase(Locale.ROOT), tokens) * 2
            val snippetScore = scoreText(page.snippet.lowercase(Locale.ROOT), tokens)
            candidate.copy(page = page, score = candidate.score + titleScore + snippetScore)
        }

        val topResults = enriched
            .sortedByDescending { it.score }
            .distinctBy { it.uri }
            .take(limit.coerceIn(1, 10))

        buildString {
            appendLine("# Official docs search")
            appendLine("Query: $query")
            appendLine("Domain: ${domain.displayName}")
            appendLine()
            topResults.forEachIndexed { index, result ->
                appendLine("${index + 1}. ${result.page!!.title}")
                appendLine("   URL: ${result.uri}")
                appendLine("   Snippet: ${result.page.snippet}")
                appendLine()
            }
        }.trim()
    }

    suspend fun fetchReleaseNotes(
        component: String,
        version: String?,
        maxChars: Int = 6_000,
    ): String = withContext(Dispatchers.IO) {
        val uri = AllowedSources.releaseNotesUri(component)
        val page = fetchPage(uri)
        val requestedVersion = version?.trim().orEmpty()

        val excerpt = if (requestedVersion.isBlank()) {
            clip(page.markdown, maxChars)
        } else {
            extractVersionExcerpt(page.markdown, requestedVersion, maxChars)
                ?: clip(page.markdown, maxChars)
        }

        buildString {
            appendLine("# AndroidX release notes")
            appendLine("Component: $component")
            if (requestedVersion.isNotBlank()) {
                appendLine("Version filter: $requestedVersion")
            }
            appendLine("URL: ${uri}")
            appendLine()
            append(excerpt)
        }.trim()
    }

    private fun loadDomainUrls(domain: DocDomain): List<URI> {
        val cacheKey = domain.id
        val cached = sitemapCache[cacheKey]
        val now = Instant.now(clock)
        if (cached != null && cached.expiresAt.isAfter(now)) {
            return cached.value
        }

        val urls = domain.sitemapUrls
            .flatMap { sitemapUrl -> loadSitemapUrls(URI(sitemapUrl), domain, 0) }
            .distinct()

        sitemapCache[cacheKey] = CacheEntry(
            value = urls,
            expiresAt = now.plus(Duration.ofMinutes(30)),
        )
        return urls
    }

    private fun loadSitemapUrls(
        sitemapUri: URI,
        domain: DocDomain,
        depth: Int,
    ): List<URI> {
        require(depth <= 3) { "Sitemap recursion depth exceeded." }

        val xml = fetchText(sitemapUri)
        val locs = LOC_REGEX.findAll(xml)
            .mapNotNull { match ->
                runCatching {
                    Parser.unescapeEntities(match.groupValues[1], false)
                }.getOrNull()
            }
            .mapNotNull { loc -> runCatching { URI(loc.trim()) }.getOrNull() }
            .toList()

        return if (xml.contains("<sitemapindex", ignoreCase = true)) {
            locs.take(512).flatMap { nestedUri -> loadSitemapUrls(nestedUri, domain, depth + 1) }
        } else {
            locs.filter { uri -> AllowedSources.isAllowed(uri, domain) }
        }
    }

    private fun fetchPage(uri: URI): DocPage {
        val cacheKey = uri.toString()
        val cached = pageCache[cacheKey]
        val now = Instant.now(clock)
        if (cached != null && cached.expiresAt.isAfter(now)) {
            return cached.value
        }

        val html = fetchText(uri)
        val document = Jsoup.parse(html, uri.toString())
        val markdown = HtmlMarkdownRenderer.render(document)
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?: document.title().takeIf { it.isNotBlank() }
            ?: document.selectFirst("h1")?.text()
            ?: uri.toString()
        val snippet = markdown
            .replace(Regex("\\s+"), " ")
            .trim()
            .let { text -> clip(text, 220) }

        return DocPage(
            uri = uri,
            title = title,
            markdown = markdown,
            snippet = snippet,
        ).also { page ->
            pageCache[cacheKey] = CacheEntry(
                value = page,
                expiresAt = now.plus(Duration.ofHours(6)),
            )
        }
    }

    private fun fetchText(uri: URI): String {
        val request = HttpRequest.newBuilder(uri)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,text/xml;q=0.9,*/*;q=0.8")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) {
            "Request failed for $uri with status ${response.statusCode()}."
        }
        return response.body()
    }

    private fun formatPageExcerpt(page: DocPage, maxChars: Int, startIndex: Int): String {
        val safeStart = startIndex.coerceAtLeast(0).coerceAtMost(page.markdown.length)
        val excerpt = clip(page.markdown.drop(safeStart), maxChars)
        val end = (safeStart + excerpt.length).coerceAtMost(page.markdown.length)
        val hasMore = end < page.markdown.length

        return buildString {
            appendLine("# Official documentation")
            appendLine("Title: ${page.title}")
            appendLine("URL: ${page.uri}")
            appendLine("Range: $safeStart..$end of ${page.markdown.length} chars")
            if (hasMore) {
                appendLine("More content available: true")
            }
            appendLine()
            append(excerpt)
        }.trim()
    }

    private fun extractVersionExcerpt(
        markdown: String,
        version: String,
        maxChars: Int,
    ): String? {
        val index = markdown.lowercase(Locale.ROOT).indexOf(version.lowercase(Locale.ROOT))
        if (index < 0) {
            return null
        }

        val start = (index - 300).coerceAtLeast(0)
        val end = (index + maxChars).coerceAtMost(markdown.length)
        return markdown.substring(start, end).trim()
    }

    private fun tokenize(query: String): List<String> {
        return query.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() && it.length >= 2 }
            .distinct()
    }

    private fun scoreText(text: String, tokens: List<String>): Int {
        var score = 0
        tokens.forEach { token ->
            if (text.contains(token)) {
                score += 3
            }
        }
        if (tokens.all { token -> text.contains(token) }) {
            score += 8
        }
        return score
    }

    private fun clip(text: String, maxChars: Int): String {
        val safeLimit = maxChars.coerceIn(200, 20_000)
        if (text.length <= safeLimit) {
            return text
        }
        return text.take(safeLimit).trimEnd() + "\n\n[truncated]"
    }

    private data class CacheEntry<T>(
        val value: T,
        val expiresAt: Instant,
    )

    private data class DocPage(
        val uri: URI,
        val title: String,
        val markdown: String,
        val snippet: String,
    )

    private data class RankedCandidate(
        val uri: URI,
        val score: Int,
        val page: DocPage? = null,
    )

    private companion object {
        private val LOC_REGEX = Regex("<loc>(.*?)</loc>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private const val USER_AGENT = "android-kotlin-docs-mcp/0.1.0"
    }
}
