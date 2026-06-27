# android-kotlin-docs-mcp

Standalone MCP server for live Android/Kotlin documentation access in Android Studio Agent Mode.

This tool is intentionally outside the Android `app` module so the product architecture stays unchanged.

## What it serves

The server exposes three read-only MCP tools over Streamable HTTP:

- `search_official_docs`
- `fetch_official_doc`
- `fetch_release_notes`

It is restricted to these official sources:

- `https://developer.android.com`
- `https://kotlinlang.org/docs`
- `https://developer.android.com/jetpack/androidx/releases`

## Run

From the repository root:

```bash
source ./android-env.sh
./gradlew -p tools/android-kotlin-docs-mcp test
./gradlew -p tools/android-kotlin-docs-mcp run
```

Default endpoint:

```text
http://127.0.0.1:3000/mcp
```

To use a different port:

```bash
source ./android-env.sh
./gradlew -p tools/android-kotlin-docs-mcp run --args="3001"
```

## Android Studio configuration

Add this MCP server in Android Studio with streamable HTTP:

```json
{
  "mcpServers": {
    "docs": {
      "httpUrl": "http://127.0.0.1:3000/mcp",
      "timeout": 20000,
      "enabled": true
    }
  }
}
```

Then verify inside chat with:

```text
/mcp
```

## Notes

- The tool fetches live official docs and converts page content into markdown-like text for the agent.
- `search_official_docs` uses the official sitemap indexes plus page title/snippet scoring.
- This server is deliberately closed-world: arbitrary web fetch is not allowed.
