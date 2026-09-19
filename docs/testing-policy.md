# Testing policy

## Unit tests required for
- validator logic
- import logic
- explicit additive `content_package` file imports do not archive sibling nodes
- reviewed editable import extraction, validation, compilation and validation-profile behavior
- manual builder readiness without a four-question minimum, including reveal-only units in `Tarjetas pendientes` and rejection of empty or incomplete units
- manual builder additions to existing units use fresh outcome/node/item identities, preserve course/unit metadata, and keep the import additive so prior questions and progress remain untouched
- upsert by ID
- progress preservation on wording changes
- scheduler scoring
- recentTopicPenalty
- session engine transitions
- one active persisted session per sector without cross-sector replacement
- `Tarjetas pendientes` exact same-day resume and new-day backlog recomposition
- snapshot/user-state preservation of multiple active sessions
- separation between real pending questions and auxiliary parallels
- `Tarjetas pendientes` unit micro-session: first pass, local error registry, and final correction
- `anzuelo` / `rescue` do not alter spaced repetition or real pending debt
- `anzuelo` appears only when there is no coherent easy real opener in the unit
- rescue trigger after 2 consecutive local errors
- failed auxiliaries become locally vetoed when the flow depends on that local eligibility
- notification uses the exact first visible `Tarjetas pendientes` interaction
- notification is the exact first interaction payload (real opener or `anzuelo`), not a generic pending-reminder banner
- notification advances with real `Tarjetas pendientes` progress instead of restarting a separate queue
- social gate requires correct answers, not just attempts
- social gate opens or reuses the real `Tarjetas pendientes` QUICK session instead of a separate gate lane
- social gate does not block when `Tarjetas pendientes` has no real pending debt
- social gate never creates gate-only filler questions, uses future/not-due cards, or turns `RESCUE` into gate debt
- social gate escape unlocks only the current foreground opportunity and exposes the exact remaining-use count
- social gate reimposes the single study Activity when an unresolved gate loses focus, including
  while `UsageStats` briefly reports a stale own-package value
- social gate relaunch throttling prevents a `startActivity` storm and always yields to calls,
  screen-off and lock-screen states
- `Tarjetas pendientes` light-entry rules
- back_exit timing
- archived_candidate exclusion
- manual archive preserves state/history
- manual edit preserves identity/metadata
- deep mode does not alter real pending scheduler state
- vaciar tema excluding rescue
- vaciar tema does not alter real pending scheduler state

## Instrumented / Compose tests required for
- first launch with seed
- import via SAF
- home -> study navigation
- home sector buttons resume only their own sector session
- full-screen `Tarjetas pendientes` flow
- continuity without returning home
- feedback visibility
- local progress visibility
- back intercept
- notification handoff lands on the same first visible interaction
- notification UI does not degrade into a generic backlog reminder when an exact first interaction exists
- second notification reflects real progress made after the first one
- gate social continuity has no intermediate screen
- gate social keeps course/unit/unlock progress visible
- gate social is hosted inside the existing Activity, consumes Back while active, and exposes no
  granted-state shortcut back to the special-access revocation screen
- Activity recreation and exact restoration, including local pending phase and auxiliary veto when applicable
- `Contenido` shows only one hierarchy layer at a time with breadcrumb-style context
- reset node progress confirmation
- reviewed editable import screen single source-text input, editable detected draft/preview, and visible expandable validation diagnostics

## Migration tests required for
- any Room schema change

## Debug safeguards
- StrictMode enabled in debug
- no main-thread database or file I/O
- exported Room schema tracked from first version

## Device-specific safeguard
- The Xiaomi Redmi Note 13 Pro 5G gate no longer uses AccessibilityService or an alert-window overlay.
- Do not execute gate instrumented tests against the primary phone: they can surface and consume real unseen pending questions. Use the isolated instrumentation database on an emulator or separate device.
- On the primary validation phone for this local-only MVP, do not run `connectedDebugAndroidTest`, reinstall the app, or otherwise replace the installed package unless the user explicitly approves it for that run or a fresh snapshot/user-state export already exists outside the app.
- Before any approved package replacement, also preserve a raw private-data archive including the Room DB/WAL/SHM and verify content counts and ID digests before/after `adb install -r`. Never uninstall or clear package data as part of validation.
- Reason: uninstalling or reinstalling incorrectly can wipe local-only data, and Android backup/restore is not guaranteed to recover it on adb-driven test flows.

## Task completeness rule

A task is incomplete if:
- it changes persistence and adds no tests,
- it changes scheduler logic and adds no tests,
- it changes session semantics and adds no restoration tests,
- it changes Room schema without migration planning,
- it changes project behavior without updating the repo documentation.
