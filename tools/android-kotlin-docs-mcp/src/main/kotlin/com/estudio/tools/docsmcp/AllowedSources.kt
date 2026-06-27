package com.estudio.tools.docsmcp

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

internal enum class DocDomain(
    val id: String,
    val displayName: String,
    val allowedHosts: Set<String>,
    val allowedPathPrefixes: List<String>,
    val sitemapUrls: List<String>,
) {
    ANDROID(
        id = "android",
        displayName = "Android Developers",
        allowedHosts = setOf("developer.android.com"),
        allowedPathPrefixes = listOf("/"),
        sitemapUrls = listOf("https://developer.android.com/sitemap.xml"),
    ),
    KOTLIN(
        id = "kotlin",
        displayName = "Kotlin Docs",
        allowedHosts = setOf("kotlinlang.org"),
        allowedPathPrefixes = listOf("/docs"),
        sitemapUrls = listOf("https://kotlinlang.org/sitemap.xml"),
    ),
    ANDROIDX(
        id = "androidx",
        displayName = "AndroidX release notes",
        allowedHosts = setOf("developer.android.com"),
        allowedPathPrefixes = listOf("/jetpack/androidx/releases"),
        sitemapUrls = listOf("https://developer.android.com/sitemap.xml"),
    ),
}

internal data class AllowedSource(
    val domain: DocDomain,
    val uri: URI,
)

internal object AllowedSources {
    private val domainsById = DocDomain.entries.associateBy { it.id }

    fun parseDomain(raw: String?): DocDomain? {
        if (raw.isNullOrBlank()) {
            return null
        }

        return domainsById[raw.trim().lowercase(Locale.ROOT)]
    }

    fun requireAllowedUrl(rawUrl: String, requestedDomain: DocDomain? = null): AllowedSource {
        val uri = try {
            URI(rawUrl.trim())
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid URL: ${error.message}")
        }

        val matchingDomain = requestedDomain ?: DocDomain.entries.firstOrNull { isAllowed(uri, it) }
        if (matchingDomain == null || !isAllowed(uri, matchingDomain)) {
            throw IllegalArgumentException(
                "URL is not allowed. Only official Android/Kotlin documentation URLs are accepted."
            )
        }

        return AllowedSource(matchingDomain, uri.normalize())
    }

    fun releaseNotesUri(component: String): URI {
        val slug = component.trim().lowercase(Locale.ROOT)
        require(slug.matches(Regex("[a-z0-9._-]+"))) {
            "Component must contain only lowercase letters, digits, dots, underscores, or dashes."
        }

        return URI("https://developer.android.com/jetpack/androidx/releases/$slug")
    }

    fun isAllowed(uri: URI, requestedDomain: DocDomain? = null): Boolean {
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        if (scheme != "https") {
            return false
        }

        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        val path = uri.path.orEmpty().ifBlank { "/" }
        val domains = requestedDomain?.let(::listOf) ?: DocDomain.entries

        return domains.any { domain ->
            host in domain.allowedHosts &&
                domain.allowedPathPrefixes.any { prefix -> path.startsWith(prefix) }
        }
    }

    fun normalizedSearchText(uri: URI): String {
        val path = URLDecoder.decode(uri.path.orEmpty(), StandardCharsets.UTF_8)
        return buildString {
            append(uri.host.orEmpty().lowercase(Locale.ROOT))
            append(' ')
            append(path.lowercase(Locale.ROOT).replace('-', ' ').replace('_', ' '))
        }
    }
}
