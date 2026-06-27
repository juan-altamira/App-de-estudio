# Testing policy

## Unit tests required for
- validator logic
- import logic
- explicit additive `content_package` file imports do not archive sibling nodes
- reviewed editable import extraction, validation, compilation and validation-profile behavior
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
- On the Xiaomi Redmi Note 13 Pro 5G used for local validation, do not reinstall the app or run instrumented tests while the social gate AccessibilityService is enabled.
- If the process is killed during install or instrumentation with the service active, Android can mark the service as crashed and MIUI may keep showing "this service is not working correctly" until the phone is rebooted and the service is enabled again manually.
- On the primary validation phone for this local-only MVP, do not run `connectedDebugAndroidTest`, reinstall the app, or otherwise replace the installed package unless the user explicitly approves it for that run or a fresh snapshot/user-state export already exists outside the app.
- Reason: reinstalling a local-only build can wipe app data, and Android backup/restore is not guaranteed to recover it on adb-driven test flows.

## Task completeness rule

A task is incomplete if:
- it changes persistence and adds no tests,
- it changes scheduler logic and adds no tests,
- it changes session semantics and adds no restoration tests,
- it changes Room schema without migration planning,
- it changes project behavior without updating the repo documentation.
