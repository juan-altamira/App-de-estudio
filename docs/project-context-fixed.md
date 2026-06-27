# Project fixed context

## 1. Product definition

This project is an anti-procrastination spaced-repetition study app.

Its purpose is to:
- minimize entry friction into study
- maximize real adherence
- keep important knowledge nodes alive over time
- distribute study into short, repeatable in-app review flows
- also support deeper study sessions when the user actively wants to study more

This project is NOT:
- a classic flashcard app
- a pure memorization-only app
- a generic quiz app
- a multi-user product in this phase
- a sync-first or web-first product in this phase
- a hook-first app in this phase; notification and social gate already exist in the Android MVP, while widget and alarm remain outside the implemented scope

## 2. Current MVP scope

Current scope:
- Android local MVP only
- single module `app`
- single user
- local-only storage
- JSON content import through final `ContentPackageDto`, explicit additive `content_package` file imports, AI-safe `AuthoringDraftPackage` preparation, and reviewed editable text import that compiles a human-checked question bank into `ContentPackageDto`
- idempotent demo seed
- quick review flow
- deep mode
- drain topic mode
- local scheduler
- local session engine
- exact per-sector session restoration
- configurable cognitive notification scheduling
- direct notification-to-study handoff for the exact first visible `Tarjetas pendientes` interaction
- implemented social gate via AccessibilityService overlay, per-app settings with daily frequency and time window, per-app daily quota tracking, pending-backed unlock progress (`0/N` while locked), direct reuse/open of the real `Tarjetas pendientes` QUICK session, no blocking when there is no real pending debt, no gate-only filler questions, a foreground-only escape token with visible remaining uses, and a legacy real-only `SOCIAL_GATE` scheduler path kept for compatibility
- Room for structured persistence
- DataStore for lightweight app settings

Functional organization of the current product contract:
- three internal study modes: `Tarjetas pendientes` (`quick` internally), `Deep mode`, and `Vaciar tema`
- two critical external surfaces: `notification` and `social gate`
- real pending-backed questions are always kept separate from auxiliary parallels that regulate flow only

Active-session contract:
- one persisted active session per sector: `Tarjetas pendientes`, `Modo profundo`, and `Vaciar tema`
- `Tarjetas pendientes` resumes exactly only within the same local calendar day; the first entry of a new local day must rebuild the real pending backlog instead of resuming the previous-day session verbatim
- `Modo profundo` and `Vaciar tema` resume exactly until the user explicitly confirms `Terminar sesión`
- entering one sector must not clear, replace, or degrade the active session of the other sectors

Explicitly out of scope in this phase:
- alarm
- widget
- device-owner / dedicated-device blocking of third-party apps
- auth
- cloud sync
- Supabase
- web app
- multi-user logic

## 3. Conceptual content architecture

Content hierarchy is:

program -> unit -> outcome -> node -> facets -> items

Definitions:

### Program
Source of truth of a study domain.

### Unit
Large thematic block.

### Outcome
Concrete skill or understanding that must be achieved.

### Node
The real review unit of the system.
A node is:
- a central idea
- bounded
- evaluable
- important enough to reappear independently
- small enough to be reviewed repeatedly

### Facet
A specific angle of a node that can be reviewed or evaluated independently.

### Item
A concrete visible prompt, question, or exercise used to review or evaluate a facet of a node.

## 4. Node definition

Every node must:
- represent one central bounded idea
- not be trivial
- not be huge
- map to real outcomes
- support multiple item formats
- support meaningful measurement of mastery

Every node includes:
- `node_id`
- `course_id`
- `unit_id`
- `outcome_ids`
- `title`
- `core_claim`
- `type`
- `weight_exam`
- `prerequisites`
- `facets`
- `must_know`
- `common_errors`
- `minimum_mastery_definition`
- `surface_easy_ready`
- `surface_easy_item_count`
- `source_refs`
- `version`
- `updated_at`

## 5. Valid facet families

Valid facet families are:
- `definicion_funcional`
- `proceso_paso_a_paso`
- `diferencia_entre_conceptos`
- `causa_efecto`
- `escenario_aplicado`
- `caso_limite`
- `error_tipico`
- `integracion_con_otro_nodo`

Each node should usually have 3 to 6 meaningful facets.

## 6. Item definition

An item is a concrete review/evaluation prompt for part of a node.

Every item includes:
- `item_id`
- `node_id`
- `facet`
- `format`
- `friction_level`
- `difficulty_seed`
- `item_role`
- `allowed_surfaces`
- `cooldown_hours`
- `stem`
- `correct_answer`
- `feedback_short`
- `covers_must_know`
- `variant_group_id`
- `rescue_group_id`
- `version`
- `updated_at`
- `source_refs`

Recommended additional fields:
- `node_complexity`
- `facet_complexity`
- `distractor_similarity`
- `prerequisite_depth`
- `targets_error_ids`
- `common_error_signals`
- `options`

## 7. Identity rules

These are hard rules.

- same `node_id` means same node identity
- same `item_id` means same item identity
- wording changes do NOT create a new item
- wording changes do NOT reset progress
- option text changes do NOT reset progress
- feedback text changes do NOT reset progress
- reference/source text changes do NOT reset progress
- the system must NEVER automatically infer that "meaning changed too much" and silently reset progress
- if something should be a new node, it must get a new `node_id`
- if progress must reset, it must be a manual explicit action

## 8. Friction and difficulty

Friction means:
- how hard it is to start or respond to an item

Difficulty means:
- how likely the user is to fail even when seriously trying

Friction is mostly related to item format.
Difficulty is not inferred only from format.

Friction levels:
- `1` = minimal
- `2` = low-medium
- `3` = medium
- `4` = high

For external-entry surfaces and auxiliary parallels, `friction 1` is narrower:
- rapid response only
- no typed free text
- no heavy self-evaluation as the normal entry policy
- not every content item labeled `friction 1` is automatically eligible for `notification`, `social gate`, `anzuelo`, or `rescue`

## 9. Item roles

Valid item roles:
- `core`
- `variant`
- `trap`
- `integration`
- `boss`
- `rescue`

Definitions:

### core
Main content of the node.

### variant
A useful alternate way to ask about the same node.
Not just fallback.

### trap
An item explicitly designed to detect a real conceptual confusion from `common_errors`.

### integration
An item that connects multiple facets or multiple nodes.

### boss
A deep mastery or full-process item.

### rescue
A friction-reduction fallback item used after failures.

## 10. Trap / integration / boss / rescue rules

### trap
- must come from real conceptual confusions
- must not rely on trick wording
- must live inside the affected node
- should map to `common_errors`

### integration
- should appear in deeper study
- can combine facets or neighboring nodes
- should not be used as low-friction entry

### boss
- represents full understanding, full process, or deep synthesis
- should exist for important topics
- should not be the opening move in low-friction review

### rescue
- is a fallback mechanism
- reduces friction after failure
- is NOT part of the normal main content of a topic
- must NOT appear in `Vaciar tema`

### auxiliary parallels
- `anzuelo`, `rescue`, and equivalent prompts are auxiliary parallels
- they always run at `friction 1`
- they regulate flow only
- they do NOT change real spaced repetition or real pending debt

## 11. archived_candidate

When a node previously imported is missing in a later import:
- it must not be auto-deleted
- it must be marked `archived_candidate = true`

Hard rule:
- `archived_candidate = true` excludes that node from:
  - scheduler candidates
  - active study flows
  - active aggregate stats by default

It may still appear in content management for:
- review
- restore
- manual archive
- manual deletion later

## 12. Tarjetas pendientes (`quick` internally)

`Tarjetas pendientes` is the main study mode.

It uses spaced repetition.
The user does not choose course or unit manually.
The global scheduler chooses the next unit with real pending debt, and the visible experience becomes a local micro-session for that unit.

Hard rules:
- the unit must open with the lowest-friction real pending content available in that unit
- if the unit has no easy real opener, it may begin with one auxiliary `anzuelo` at `friction 1`
- `anzuelo` smooths entry only; it does not change spaced repetition
- the first pass must show all real pending items of the unit before final correction begins
- the unit then enters a final correction phase over the real failed items until that local error counter reaches zero
- the mode must not move to the next unit until the first pass is complete and the local real-error counter is zero
- two consecutive local errors must trigger one auxiliary `rescue`
- `rescue` is `friction 1`, lowers frustration, and never closes real debt by itself
- failing an `anzuelo` or `rescue` counts as a local unit/session error, but not as spaced-repetition debt
- a failed auxiliary becomes locally vetoed for reuse as auxiliary inside the current unit and resets only when that unit closes
- if the selected unit has no real pending debt at entry time, the app must navigate to a dedicated success screen with a single return action instead of showing a modal or inline notice

## 12.1 Answer evaluation policy

Hard rules:
- the app must never ask the user to type free-text answers during study flows
- machine validation is allowed only for fixed-answer interaction types such as:
  - `true_false`
  - multiple-choice style options
  - other explicit fixed-choice prompts
- formats with multiple valid phrasings must not be machine-validated by string comparison
- this includes:
  - open-ended explanation formats
  - development formats
  - fill-style text formats such as `fill_one_word` and `fill_short_blank`
- for these formats, the flow must be:
  - user thinks the answer
  - taps `Ver respuesta`
  - reads the expected answer
  - self-assesses with `La acerté` or `La fallé`

Rationale:
- typed free-text validation is not reliable enough for this product without a real evaluator
- false negatives on valid answers are worse than explicit self-assessment in this phase

## 13. Deep mode

Deep mode is a manual, free, voluntary in-app study mode for longer, more deliberate sessions.

It should prioritize:
- important nodes
- weak facets
- traps
- integration
- bosses

It is not minimal-friction mode.
It should still remain coherent and thematic.

Hard rule:
- it does NOT use spaced repetition
- it does NOT consume real pending debt
- it should not use `anzuelo`, `rescue`, or equivalent auxiliary parallels
- it may record manual-study traces, but without changing the real pending scheduler
- it must run on a fixed session set selected at session start
- its first pass must traverse that fixed set with progressive friction
- wrong answers stay alive in a local correction set and raise a visible live-error counter
- after the first pass ends, only the live incorrect items repeat, preserving their session-relative order until each is corrected
- the session ends only when the full first pass is finished and the local incorrect counter has returned to zero
- `Terminar sesión` belongs to a fixed bottom action area, visually separated from the scrollable study content

## 14. Vaciar tema

`Vaciar tema` means traversing the full normal bank of a topic.

It includes:
- `core`
- `variant`
- `trap`
- `integration`
- `boss`

It excludes:
- `rescue`

Hard rule:
- `Vaciar tema` must never include rescues by default
- it does NOT use spaced repetition
- it does NOT consume real pending debt
- it must also exclude `anzuelo` and equivalent auxiliary parallels
- it may record manual-study traces, but without changing the real pending scheduler
- it must run on the fixed normal-bank set chosen at session start
- its first pass must traverse that set with progressive friction
- wrong answers stay alive in a local correction set and raise a visible live-error counter
- after the first pass ends, only the live incorrect items repeat, preserving their session-relative order until each is corrected
- the session ends only when the full first pass is finished and the local incorrect counter has returned to zero
- `Terminar sesión` belongs to a fixed bottom action area, visually separated from the scrollable study content

## 14.1 External low-friction surfaces

### notification
- must expose the exact first visible interaction from `Tarjetas pendientes`
- that first visible interaction may be a real pending-backed prompt or an auxiliary `anzuelo` when the unit has no coherent easy real opener
- must render that interaction itself, not a generic "you have pending cards" reminder
- touching it must land on that same interaction inside the in-app flow
- it must not invent a separate queue or use auxiliary filler as the normal policy

### social gate
- must reuse or open the real `Tarjetas pendientes` QUICK session
- must block only when that session has real pending debt
- must not invent a separate queue, gate-only filler, or future/not-due card
- keeps the same `anzuelo` and `rescue` behavior that the QUICK session itself triggers
- should traverse the real pending debt exactly as `Tarjetas pendientes` would
- must keep course/unit context and local unlock progress visible
- must follow the full pending session semantics of `Tarjetas pendientes`
- must require correct answers, not just attempts

## 15. Content management UI

The visible navigation of `Contenido` is a three-level product UI:
- `Curso`
- `Unidad`
- `Pregunta` / `Tarjeta`

Hard rules:
- each level replaces the previous one visually instead of expanding underneath it
- the only persistent control shared across levels is the `Activos / Archivados` toggle
- the toggle filters the current level only: courses, units of the selected course, or cards of the selected unit
- upper context may remain only as compact breadcrumb/header information
- internal model words such as `nodo`, `faceta`, or `outcome` must never appear in the visible UI
- course, unit, and card administration actions belong in contextual menus, not as permanently expanded inline buttons
- the question list must scroll cleanly below its compact context header without clipping, overlap, or nested-scroll artifacts
- real gate questions consume real pending debt because the gate is the pending session; auxiliary `anzuelo`/`rescue` prompts still must not
- continuity after each solved gate question must stay immediate, with no intermediate screen

## 14.2 Manual archive and edit

Manual archive:
- may apply to cards, units, and courses
- removes content from active flow without deleting it
- preserves metadata, history, and real spaced-repetition state while the content still exists
- must be restorable later from the archive surface

Manual edit:
- preserves identity and metadata by default
- must stay inside the current format's structural boundaries
- should not silently reset real spaced-repetition state
- if the user wants a materially different card, the correct action is to create a new one

## 15. TopicPacket

`TopicPacket` is the selection unit for a coherent study sequence.

It must fix:
- `topicUnitId`
- `surface`
- `mode`
- `goalCorrectCount`
- `priorityScore`
- contextual queue
- enough local pending-flow context to restore visible state when needed

Hard rule:
- a packet never changes topic mid-event

## 16. NodeState

`NodeState` is the aggregate user state for a node.

Continuous fields must be normalized in `0.0..1.0`:
- `memoryScore`
- `retrievability`
- `frictionUser`
- `difficultyUser`
- `coverageScore`
- `errorRate`
- `abandonRate`

Hard rule:
- these real spaced-repetition fields are updated only by real pending-backed reviews
- auxiliary parallels, `Deep mode`, and `Vaciar tema` must not mutate the real pending scheduler state

`stabilityHours` must be a `Double`
Default initial value:
- `24.0`

## 17. JSON import rules

This phase uses JSON as the main content creation/import mechanism.

Rules:
- AI-assisted content should be authored as `AuthoringDraftPackage`, centered on nodes and pedagogical intent
- the app compiles `AuthoringDraftPackage` into final `ContentPackageDto` before import
- natural language source text may enter through the reviewed editable import screen, where the user starts with exactly one source-text input: the complete source text. Course, units, stable IDs, questions, format, study role, answer and feedback must be declared in or conservatively inferred from that text, then shown as an editable detected draft before approval. The detected draft may be corrected by editing fields, adding questions, or removing questions before compilation.
- when the source text is unstructured prose, the reviewed editable extractor may infer course/unit labels and convert meaningful paragraphs into `Ver respuesta` questions using only the original paragraph text as answer and mechanical feedback; it must not invent options, roles, trap confusions, facets or node semantics.
- the reviewed editable import preview is an end-user review surface: it must show editable course, unit, questions, options, expected answers and explanations. Technical metadata such as IDs, stable keys, source refs, parser line numbers, generated-key state, roles and internal package details must appear only inside diagnostics/debug detail, never as primary preview content.
- reviewed editable import is a question-bank profile, not a replacement for node-centered authoring; it can compile multiple detected units from one source text, creates one deterministic content node per unit, and derives only runtime mechanics plus conservative must-know coverage from completed question fields
- reviewed editable import must never create `ItemRole.RESCUE`; rescue remains an auxiliary fallback mechanism generated by study/session logic, not a normal authoring role in the editable question bank
- reviewed editable import may allow empty `commonErrors` when there are no trap questions, but every `TRAP` question must explicitly declare the confusion it corrects and compile that into `commonErrorSignals`
- `ContentPackageDto` remains clean; validation/import profile is passed to validation/import services and must not be stored as a field in the final content package
- import preflight may accept recognized root `schema` metadata aliases (`authoring_draft`, `AuthoringDraftPackage`, `QuestionDraftPackage`, `content_package`) only when the underlying shape matches the same node-centered or final contract
- direct `ContentPackageDto` file imports may include root metadata `importMode: "additive"` when the file is only adding content, such as a new unit inside an existing course; this mode must not archive sibling nodes that are absent from the incoming package
- `importMode` is root import metadata, not persisted content; the importer strips it before decoding/storing `ContentPackageDto`
- `QuestionDraftPackage` is an alias only for a full node-centered authoring draft; flat root `questions[]` packages must be rejected
- direct `ContentPackageDto` import remains supported as the advanced/backward-compatible final format
- `ContentPackageDto` is the only format the importer persists
- import uses upsert by ID
- same `node_id` updates node content without resetting progress
- same `item_id` updates item content without resetting progress
- import validation is 2-stage:
  - structural validation = fatal blocking errors
  - authoring validation = warnings only
- missing nodes in full/republish reimport must become `archived_candidate=true`
- missing sibling nodes in an explicit additive import must remain active
- no automatic deletion of missing content
- the compiler derives IDs, timestamps, hash, surfaces, friction, `surfaceEasyReady`, `surfaceEasyItemCount`, final `correctAnswer` and option correctness
- the compiler must not invent missing node semantics, facets, common errors, roles or coverage
- the preflight may normalize only mechanical enum token casing/spacing; stable keys and semantic fields must remain explicit
- import failures must expose structured diagnostics with code, JSON path when known, expected value/shape, received value/shape and an actionable hint

## 18. Demo seed rules

A demo seed must exist and must be idempotent.

It must use:
- fixed `packageId`
- `schemaVersion`
- `seedVersion`
- `contentHash`

Rules:
- if DB is empty -> seed
- if same `packageId + seedVersion` already exists -> do nothing
- if same `packageId` but different `seedVersion` -> upsert

Additional rule:
- if real user-imported content exists, demo content must not be prioritized in Home
- demo content may only enter scheduler if still active and not archived

## 19. Abandon rules

Abandon must be recorded when:
- the user presses back from a visible unanswered question
- the user closes the app from a visible unanswered question
- 5 minutes pass with the question visible and unanswered

Do NOT record immediate abandon on short ambiguous foreground loss.

## 20. Back exit rules

Exact rule:
- first back press -> show a micro-question from the same context
- if user answers it -> continue session
- after answering it -> set `isExitArmed = true`
- set `exitArmedUntil = now + 8 seconds`
- if user presses back again within that 8-second window -> exit
- if the window expires -> next back press must trigger a new micro-question again

Exit semantics:
- deliberate exit after the armed 8-second window flow must preserve the incomplete active session for later continuation
- abandon by back from an unanswered visible prompt still records abandon and closes the active session
- completed sessions still clear the active session

## 21. Scheduler and scoring

Exact priority formula:

`priority = 0.40*dueScore + 0.25*fragilityScore + 0.20*frictionScore + 0.15*abandonmentScore - 0.20*recentTopicPenalty`

Definitions:
- `dueScore = clamp(overdueHours / 72.0, 0.0, 1.0)`
- `fragilityScore = 1.0 - memoryScore`
- `frictionScore = frictionUser`
- `abandonmentScore = clamp(recentAbandonsLast14d / 3.0, 0.0, 1.0)`
- `recentTopicPenalty = 1.0` if last topic, `0.5` if previous topic, `0.0` otherwise

Rules:
- exclude `archived_candidate`
- exclude nodes without valid items for current surface/mode
- exclude rescues unless explicitly requested by local session logic

## 22. Exact session restoration

Study session restoration must be exact, not approximate.

It must restore:
- packet identity
- queue
- rescueQueue
- `currentNodeId`
- `currentItemId`
- `goalCorrectCount`
- `stepIndex`
- `isExitArmed`
- `exitArmedUntil`
- `currentTopicTitle`
- thematic context

Visible relaunch rule:
- if the app is opened from launcher/process restart and a real active session exists in `active_sessions`, startup must return directly to the study route for that session
- Home may still expose `Continuar sesión activa`, but relaunching the app must not strand the user in Home when the real intent is to continue an active session

## 22.1 Starting a new session while another is active

Hard rule:
- entry points must never silently replace an active session
- if the user tries to start a new quick, deep, or drain session while another session is active:
  - the UI must require explicit confirmation
  - the existing active session must be discarded deliberately before the new one starts

Discard semantics:
- discarding an active session must clear `active_sessions`
- it must record a `SESSION_EXITED` event
- it is not the same as abandon
- it must not create an `abandon_event`

## 23. Mandatory transactions

These operations must be atomic:

### submitAnswer
Must:
- insert `review_event`
- update `node_state`
- update `node_format_stats`
inside the same transaction

### recordAbandon
Must:
- insert `abandon_event`
- update `node_state`
- close active session
inside the same transaction

### importContentPackage
Must:
- upsert content
- insert `import_event`
inside the same transaction

## 24. Testing policy

Tests are mandatory for changes affecting:
- JSON validation
- import logic
- upsert by ID
- progress preservation across wording changes
- scheduler
- recentTopicPenalty
- session engine
- quick flow
- back_exit
- archived_candidate
- vaciar tema
- exact session restoration

Migration tests:
- mandatory for any Room schema change

StrictMode:
- must remain enabled in debug

Room schema:
- must be exported from the first commit

## 25. App module architecture rules

The `app` module uses these package boundaries:
- `di`
- `model.content`
- `model.state`
- `model.event`
- `model.json`
- `data.local.db`
- `data.local.dao`
- `data.local.store`
- `data.importing`
- `domain.scheduler`
- `domain.session`
- `domain.repository`
- `ui.home`
- `ui.content`
- `ui.study.quick`
- `ui.study.deep`
- `ui.common`

Architecture rules:
- single activity
- single NavHost
- no multiple activities for this MVP
- no fragments for this MVP
- no business logic in composables
- no persistence logic in UI
- no scheduler logic in viewmodels
- no garbage packages like generic helpers/managers/utils with unclear ownership

## 26. Documentation maintenance rule

This file is living project truth.

It must be updated whenever any of these change:
- MVP scope
- architecture
- data contracts
- scheduler rules
- session semantics
- persistence semantics
- testing obligations
- forbidden architectural rules

If project behavior changes and this file is not updated, the task is incomplete.
