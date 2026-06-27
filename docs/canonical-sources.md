# Canonical sources

## Kotlin
- Kotlin coding conventions: https://kotlinlang.org/docs/coding-conventions.html
- Kotlin official docs home: https://kotlinlang.org/docs/home.html

## Android architecture
- Architecture recommendations: https://developer.android.com/topic/architecture/recommendations
- Android app architecture overview: https://developer.android.com/topic/architecture
- Data layer guidance: https://developer.android.com/topic/architecture/data-layer
- Offline-first guidance: https://developer.android.com/topic/architecture/data-layer/offline-first
- UI layer guidance: https://developer.android.com/topic/architecture/ui-layer
- State holders and ViewModel guidance: https://developer.android.com/topic/architecture/ui-layer/stateholders

## Compose
- Compose docs home: https://developer.android.com/develop/ui/compose/documentation
- Navigation with Compose: https://developer.android.com/develop/ui/compose/navigation
- Compose lint: https://developer.android.com/develop/ui/compose/tooling/lint
- Compose API/style guidance: https://developer.android.com/develop/ui/compose/designsystems
- Compose state guidance: https://developer.android.com/develop/ui/compose/state

## Persistence
- Room docs: https://developer.android.com/training/data-storage/room
- DataStore docs: https://developer.android.com/topic/libraries/architecture/datastore

## Quality
- Android lint docs: https://developer.android.com/studio/write/lint
- Android testing docs: https://developer.android.com/training/testing
- Compose testing docs: https://developer.android.com/develop/ui/compose/testing

## Agent workflow
- AGENTS.md support: https://developer.android.com/studio/gemini/agent-files
- Add MCP server: https://developer.android.com/studio/gemini/add-mcp-server

## Preferred live-doc access in this repo
- Preferred MCP server name: `docs`
- Preferred implementation path: `tools/android-kotlin-docs-mcp`
- The MCP server must only fetch from the official hosts and paths covered by the categories above.

## Mandatory rule

If a task touches any category above, the agent must explicitly state:
1. which official source was re-checked,
2. what part of the implementation depends on it,
3. whether the source changes any previous assumption.
