@./docs/project-context-fixed.md
@./docs/canonical-sources.md
@./docs/testing-policy.md
@./docs/maintenance-rule.md
@./docs/roadmap-master.md
@./docs/authoring-draft-v1.md

# Read this first

You are working on a local Android MVP for an anti-procrastination study app.

Current repo scope now also includes local cognitive notification scheduling, configuration, exact first-interaction notification-to-study handoff for `Tarjetas pendientes` (real pending opener or `anzuelo` when that is the visible opener), an implemented social gate built on AccessibilityService overlay that reuses/opens the real `Tarjetas pendientes` QUICK session, blocks only when real pending debt exists, never creates gate-only filler questions, preserves `DEEP`/`DRAIN` sessions in their own slots, has a foreground-only escape token with visible remaining uses, per-app settings with daily frequency and time windows, a legacy real-only `SOCIAL_GATE` scheduler path kept for compatibility, an AI-safe `AuthoringDraftPackage` import preparation pipeline that compiles structured pedagogical drafts into the final `ContentPackageDto`, an explicit additive `content_package` file mode for adding units/questions without archiving sibling nodes, and a reviewed editable text import flow that turns natural text into a human-checked question-bank draft before compiling it into `ContentPackageDto`.
Alarm and widget remain outside the implemented scope until the roadmap says otherwise.

Active-session contract now enforced in the repo:
- one persisted active session per sector: `Tarjetas pendientes`, `Modo profundo`, and `Vaciar`
- entering one sector must never clear or replace the other sectors
- re-entering the same sector resumes exactly without discard confirmation
- `Tarjetas pendientes` resumes exactly only within the same local day; the first entry of a new local day must rebuild real pending debt instead of resuming the previous-day session verbatim
- when `Tarjetas pendientes` has no real pending debt, the app must navigate to a dedicated full-screen completion state, never a floating modal
- `Modo profundo` and `Vaciar` resume exactly until explicit confirmed `Terminar sesión`
- `Modo profundo` and `Vaciar` run over a fixed local session set with progressive first pass, live incorrect counter, and correction phase until local errors reach zero, without auxiliary parallels
- `Terminar sesión` for `Modo profundo` and `Vaciar` must stay in a persistent bottom action area, separated from the scrollable study content
- `Contenido` must expose one hierarchy layer at a time, with upper context reduced to breadcrumb/header form, user-visible terminology limited to `Curso`, `Unidad`, `Pregunta`/`Tarjeta`, and administrative actions moved into contextual menus
- AI-assisted content should enter through `AuthoringDraftPackage`; `ContentPackageDto` remains the final internal/importer contract
- natural text may enter only through the reviewed editable import screen; that screen must expose a single source-text input for extraction, then show an editable detected draft where the user can correct visible course/unit/question fields, add questions, or remove questions before compilation, and the compiler may import only complete fields present in that reviewed draft
- import preflight may accept recognized `schema` aliases only when the payload already satisfies the corresponding contract; a flat `QuestionDraftPackage`/`questions[]` package must fail instead of being converted by guessing nodes
- the compiler may derive runtime mechanics, but must not invent missing node semantics, facets, common errors, roles or coverage
- reviewed editable imports use a separate validation profile; `ContentPackageDto` must not receive source/import profile fields
- direct `content_package` file imports may use root metadata `importMode: "additive"` to add content without archiving sibling nodes; this metadata is stripped before `ContentPackageDto` persistence and full/absent mode keeps republish semantics

You must read this file and all imported documents before proposing code, changing architecture, modifying persistence, touching scheduler logic, or changing UI behavior.

Project-specific rules are binding.
Do not replace them with generic advice.
Do not silently simplify them.
Do not infer permission to refactor unrelated areas.

If generic best practices conflict with explicit project invariants, preserve explicit project invariants first and then adapt the implementation.

# Canonical official sources

Use the canonical sources listed in `docs/canonical-sources.md`.

# Documentation freshness protocol

Before changing anything related to:
- Kotlin language conventions
- Android architecture
- Jetpack Compose UI/state
- Navigation Compose
- Room
- DataStore
- lint or Compose lint
- testing
- Android Studio agent workflow

you must:
1. read the imported project documents,
2. identify the relevant project invariants,
3. re-check the current official documentation,
4. base the implementation on:
   - the fixed project context
   - the latest official documentation

If current official documentation differs from an older assumption, prefer the current official documentation unless doing so would break an explicit project invariant.

If live documentation access is available through browsing or MCP, use it.
If it is not available, state that limitation explicitly before proposing the change.
For this repository, the preferred live-doc MCP server is `docs`, served by `tools/android-kotlin-docs-mcp`, when it is running in the IDE.
That server must stay restricted to the canonical official sources.

# Required quality gates

Before considering any task complete, evaluate whether it requires:
- lint
- Compose lint
- unit tests
- instrumented tests
- migration tests
- session restoration tests

Operational safeguard for this repo:
- Do not run instrumented tests or reinstall/replace the app package on the user's primary device without explicit approval or a fresh exported snapshot outside the app.
- Treat `connectedDebugAndroidTest` and equivalent reinstall flows as potentially destructive for this local-only MVP.

Never accept schema changes without migration planning.
Never accept persistence changes without tests.
Never accept scheduler changes without tests.
StrictMode must remain enabled in debug.

# Mandatory task workflow

For every task, follow this sequence exactly:

1. Read AGENTS.md and all imported docs files.
2. Re-check `docs/roadmap-master.md` and identify where the task fits in the roadmap.
   This also applies before answering behavioral/product questions, even if you are only explaining and not coding yet.
3. Identify the affected layer(s).
4. Identify which project invariants may be affected.
5. Decide whether current official documentation must be re-checked.
6. List the files you intend to change.
7. State whether schema, scheduler, session semantics, or navigation are affected.
8. State which tests must be added or updated.
9. Only then propose or write code.
10. Keep changes minimal and local.
11. After the change, report:
   - files changed
   - invariants preserved
   - tests added/updated
   - remaining risks
12. If the task changes roadmap status, update `docs/roadmap-master.md` in the same task.

# Forbidden without explicit approval

Do not do any of the following without explicit approval:
- change Room schema without migration strategy
- change `node_id` or `item_id` semantics
- reset progress implicitly during import
- remove event tables
- move business logic into composables
- replace Room or DataStore
- introduce multiple activities
- rewrite navigation architecture
- change scheduler formula semantics
- remove `archived_candidate` behavior
- change `Vaciar tema` to include rescues
- change the `back_exit` 8-second rule
- make `Tarjetas pendientes` advance to the next unit while real errors from the current unit are still alive
- make auxiliary parallel questions affect or consume real spaced repetition debt
- make external-entry or auxiliary prompts use non-rapid/heavy formats instead of `friction 1`-appropriate prompts
- make `Modo profundo` or `Vaciar` alter the real pending scheduler
- make notification diverge from the exact first visible `Tarjetas pendientes` interaction
- make notification collapse into a generic pending reminder when an exact first visible interaction exists
- make `SOCIAL_GATE` count attempts instead of required correct answers
- make the social gate create gate-only filler questions or block when `Tarjetas pendientes` has no real pending debt
- make Quick schedule `rescue` directly in its initial packet
- make Quick repeat the same node/item before first-pass scheduled coverage when unseen scheduled items remain
- make manual edit silently reset identity, history, or real spaced repetition
- make quick flow start heavy
- silently delete content on reimport

If a task seems to require one of these changes, stop and state it explicitly.

# Output requirements for every task

Before making changes, always state:
- files you plan to touch
- invariants that apply
- whether schema, scheduler, session semantics, or navigation are affected
- which tests must be added or updated
- whether current official documentation must be re-checked

After making changes, always state:
- files changed
- invariants preserved
- tests added/updated
- any remaining risk

# Project instruction maintenance

This instruction system is a living contract.

If any of the following change:
- project scope
- architecture boundaries
- data contracts
- scheduler rules
- session semantics
- persistence rules
- testing requirements
- canonical sources
- forbidden rules
- roadmap status or execution order

then the corresponding docs file and this AGENTS.md must be updated in the same task.

If project behavior changes and this instruction system is not updated, the task is incomplete.
