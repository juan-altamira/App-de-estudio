package com.estudio.tools.docsmcp

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal fun Application.configureDocsMcpServer(
    service: OfficialDocsService = OfficialDocsService(),
) {
    install(ContentNegotiation) {
        json(McpJson)
    }

    mcpStreamableHttp {
        createDocsMcpServer(service)
    }
}

private fun createDocsMcpServer(service: OfficialDocsService): Server {
    val server = Server(
        serverInfo = Implementation(
            name = "android-kotlin-docs-mcp",
            version = "0.1.0",
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
            ),
        ),
    )

    server.addTool(
        name = "search_official_docs",
        description = "Search official Android Developers, Kotlin docs, or AndroidX release-note URLs from whitelisted sources only.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "Search terms to match against official sitemap URLs and page titles.")
                }
                putJsonObject("domain") {
                    put("type", "string")
                    put("description", "One of: android, kotlin, androidx.")
                }
                putJsonObject("limit") {
                    put("type", "integer")
                    put("description", "Maximum number of results to return. Defaults to 5.")
                }
            },
            required = listOf("query", "domain"),
        ),
        toolAnnotations = READ_ONLY_TOOL,
    ) { request ->
        runTool {
            val query = request.requireString("query")
            val domain = AllowedSources.parseDomain(request.requireString("domain"))
                ?: error("Unsupported domain. Use one of: android, kotlin, androidx.")
            val limit = request.optionalInt("limit") ?: 5
            toolSuccess(service.searchDocs(query, domain, limit))
        }
    }

    server.addTool(
        name = "fetch_official_doc",
        description = "Fetch a single official documentation page from the whitelisted Android/Kotlin sources and return markdown-like text.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("url") {
                    put("type", "string")
                    put("description", "HTTPS URL from developer.android.com or kotlinlang.org/docs.")
                }
                putJsonObject("max_chars") {
                    put("type", "integer")
                    put("description", "Maximum number of characters to return. Defaults to 6000.")
                }
                putJsonObject("start_index") {
                    put("type", "integer")
                    put("description", "Character offset to start reading from. Defaults to 0.")
                }
            },
            required = listOf("url"),
        ),
        toolAnnotations = READ_ONLY_TOOL,
    ) { request ->
        runTool {
            val url = request.requireString("url")
            val maxChars = request.optionalInt("max_chars") ?: 6_000
            val startIndex = request.optionalInt("start_index") ?: 0
            toolSuccess(service.fetchDocument(url, maxChars, startIndex))
        }
    }

    server.addTool(
        name = "fetch_release_notes",
        description = "Fetch AndroidX release notes by component slug, optionally focusing on a specific version string.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("component") {
                    put("type", "string")
                    put("description", "AndroidX component slug, for example room, navigation, compose-foundation.")
                }
                putJsonObject("version") {
                    put("type", "string")
                    put("description", "Optional version filter, for example 2.8.3.")
                }
                putJsonObject("max_chars") {
                    put("type", "integer")
                    put("description", "Maximum number of characters to return. Defaults to 6000.")
                }
            },
            required = listOf("component"),
        ),
        toolAnnotations = READ_ONLY_TOOL,
    ) { request ->
        runTool {
            val component = request.requireString("component")
            val version = request.optionalString("version")
            val maxChars = request.optionalInt("max_chars") ?: 6_000
            toolSuccess(service.fetchReleaseNotes(component, version, maxChars))
        }
    }

    return server
}

private suspend fun runTool(block: suspend () -> CallToolResult): CallToolResult {
    return runCatching { block() }
        .getOrElse { error -> toolError(error.message ?: "Unexpected error while executing tool.") }
}

private fun io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest.requireString(name: String): String {
    return optionalString(name) ?: error("Missing required string argument: $name")
}

private fun io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest.optionalString(name: String): String? {
    return arguments?.get(name)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
}

private fun io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest.optionalInt(name: String): Int? {
    return arguments?.get(name)?.jsonPrimitive?.contentOrNull?.toIntOrNull()
}

private val READ_ONLY_TOOL = ToolAnnotations(
    readOnlyHint = true,
    destructiveHint = false,
    idempotentHint = true,
    openWorldHint = false,
)

private fun toolSuccess(text: String): CallToolResult {
    return CallToolResult(
        content = listOf(TextContent(text)),
        isError = false,
    )
}

private fun toolError(text: String): CallToolResult {
    return CallToolResult(
        content = listOf(TextContent(text)),
        isError = true,
    )
}
