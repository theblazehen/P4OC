<!-- OPENSPEC:START -->
# OpenSpec Instructions

These instructions are for AI assistants working in this project.

Always open `@/openspec/AGENTS.md` when the request:
- Mentions planning or proposals (words like proposal, spec, change, plan)
- Introduces new capabilities, breaking changes, architecture shifts, or big performance/security work
- Sounds ambiguous and you need the authoritative spec before coding

Use `@/openspec/AGENTS.md` to learn:
- How to create and apply change proposals
- Spec format and conventions
- Project structure and guidelines

Keep this managed block so 'openspec update' can refresh the instructions.

<!-- OPENSPEC:END -->

# Agent instructions

This project uses **tk** (ticket) for issue tracking. Run `tk` to get started.

## Build verification

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew :app:compileDebugKotlin
```

## Key source locations

| What | Where |
|------|-------|
| Domain models | `app/src/main/java/dev/blazelight/p4oc/domain/model/` |
| API interface | `app/src/main/java/dev/blazelight/p4oc/core/network/OpenCodeApi.kt` |
| SSE events | `app/src/main/java/dev/blazelight/p4oc/core/network/OpenCodeEventSource.kt` |
| DTOs | `app/src/main/java/dev/blazelight/p4oc/data/remote/dto/` |
| Mappers | `app/src/main/java/dev/blazelight/p4oc/data/remote/mapper/Mappers.kt` |
| Chat UI | `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/` |
| Terminal | `app/src/main/java/dev/blazelight/p4oc/ui/screens/terminal/` + `terminal/` |
| Theme system | `app/src/main/java/dev/blazelight/p4oc/ui/theme/` |

## Code conventions

- Use `LocalOpenCodeTheme.current` for colors, not `MaterialTheme.colorScheme`
- `MaterialTheme.typography` IS the custom typography and is fine to use
- Use `Spacing.*` and `Sizing.*` tokens, not hardcoded `.dp` values
  - `Spacing.hairline` (1dp) — fine pixel-perfect TUI borders, tiny padding
  - `Sizing.indicatorDot` / `indicatorDotActive` — status dot sizes
  - `Sizing.diffGutterWidth` — line number gutters in diff views
  - `Sizing.panelWidthSm/Md/Lg` — fixed-width columns (80/120/180dp)
  - `Sizing.strokeMd` / `strokeThin` / `dividerThickness` — border/divider widths
  - `Sizing.buttonHeightSm/Md/Lg` — button heights (32/36/44dp)
- Use `TuiShapes` for shapes (all 0dp corners)
- Add `role = Role.Button` / `Role.Tab` to actionable `.clickable` modifiers
- Add meaningful `contentDescription` to functional icons (not decorative ones)
- Add `Modifier.testTag(...)` to key interactive elements for UI testing
- Package: `dev.blazelight.p4oc`
- Debug builds use package/application id suffix `.debug`; account for this when using adb, deep links, app inspection, or external test tooling.

## Ticket Quality

When creating or updating `tk` tickets, include enough context for another agent to implement the work without rediscovering the conversation.

- Do not write "first scope", "phase 1", or partial-delivery language unless the ticket is explicitly a spike or the user asks for staged delivery.
- Describe the complete user-facing behavior in the ticket; implementation can still be incremental internally.
- Include the problem, evidence/repro, UX constraints, expected behavior, acceptance criteria, and verification notes.
- Include workspace-scoping requirements when touching sessions, files, commands, terminals, or project state.
- Mention failure states and human-readable error handling; do not allow raw protocol/JSON payloads to surface in UI.

Suggested ticket shape:

```markdown
Problem:
Evidence:
UX Constraint:
Expected Behavior:
Acceptance Criteria:
Verification:
```

## Agent-Space UI Rule

The app's core value is the agent/chat/code workspace. UI chrome must be heavily justified because it reduces agent and file viewing space on phones.

- Prefer contextual, transient, collapsible, or overflow UI over persistent bars and badges.
- Put row-specific actions in long-press or overflow menus.
- Put creation actions in list headers, overflow menus, and empty states; do not add floating/persistent controls unless the workflow is frequent enough to justify the space.
- Slash/autocomplete popups are justified only while typing and must not cover the typed command or cursor.
- Workspace/project identity should be visible enough to prevent wrong-directory mistakes, but avoid full persistent chips in cramped chat headers unless a compact treatment proves usable.
- Branch and secondary metadata should live in overflow or compact text when space is constrained.

## Status Dot Semantics

Use one consistent status language across tabs, sessions, sub-agents, chat, files, and settings help. Prefer centralized mappings over scattered raw `Text("●")` glyphs.

| State | Visual |
|-------|--------|
| Connected / idle | muted or success dot, no motion |
| Running / busy | accent/primary dot or spinner, pulse allowed |
| Awaiting user input | warning dot/badge, pulse allowed |
| Retrying / reconnecting | warning or error refresh/spinner, depending severity |
| Error | error color, no fake progress |
| Background / cold | muted/subtle dot, no motion |
| Dirty / unsaved | warning/accent marker near the edited file title |

- Do not show fake percentages for agent or sub-agent work.
- Prefer real run state with a spinner, pulse, or concise text.
- Functional status indicators need content descriptions.
- Add or maintain a status legend in Settings -> Help when dot semantics change.

## In-App Editing Constraint

Do not rely on intenting out to other apps for core editing workflows. External editors break the app's tabbed workspace model and make conflict/workspace scoping harder to reason about.

- Keep file viewing and editing tabbed inside P4OC by default.
- Do not add a sidecar requirement for core file operations; use the existing opencode/OFISH path unless a future spec explicitly changes this.
- Android app virtualization/Parallel Space-style embedding is not an acceptable default architecture for editor integration.
- If external editor interop is explored, treat it as optional import/export, not the primary workflow.

## Workspace Cutover Forbidden Patterns

These patterns are forbidden after the workspace/session cutover. They reintroduce the old global-directory bugs, hide compile failures, or make multi-tab behavior ambiguous.

### 1. No Global Or Default Workspace

Bad:

```kotlin
val workspace = Workspace.DEFAULT
val workspace = Workspace.global(server)
```

Good:

```kotlin
val workspace = Workspace(server = serverRef, directory = tab.workspaceDirectory)
```

Why: a workspace must be owned by a tab and server. A global/default workspace silently loses the directory and tab identity needed for correct routing.

### 2. No Nullable Directory Defaults In API Methods

Bad:

```kotlin
@Query("directory") directory: String? = null
```

Good:

```kotlin
@Query("directory") directory: String?
```

Why: defaults let legacy callers omit workspace identity. Callers must pass an explicit workspace-scoped directory, even when it is intentionally `null`.

### 3. No Directory Fallback Chains

Bad:

```kotlin
val directory = routeDirectory ?: session.directory ?: settings.lastProject ?: null
```

Good:

```kotlin
val directory = workspace.directory
```

Why: fallback chains guess context and can cross projects/tabs. The current `Workspace` is the only source of directory truth.

### 4. No Active Tab Workspace Access From Data Layer

Bad:

```kotlin
repository.load(tabManager.activeTabWorkspace)
```

Good:

```kotlin
class SessionRepositoryImpl(private val client: SessionWorkspaceClient)
```

Why: repositories must be constructed with their workspace-scoped client. Pulling active tab state from data code creates hidden UI coupling and races.

### 5. No Mutable Workspace Variables

Bad:

```kotlin
var workspace: Workspace = initialWorkspace
workspace = workspace.copy(directory = selectedProject)
```

Good:

```kotlin
tabManager.updateTabWorkspace(tabId, selectedProject)
```

Why: mutable workspace references let existing clients/repositories keep stale identity. Change the tab workspace so scoped ViewModels are recreated.

### 6. No App-Global Current Workspace Singleton

Bad:

```kotlin
CurrentWorkspace.set(workspace)
api.listSessions(CurrentWorkspace.directory)
```

Good:

```kotlin
workspaceClient.listSessions(directory = workspace.directory)
```

Why: a singleton cannot represent multiple tabs or server generations. Workspace identity must travel through `WorkspaceClient`/repository instances.

### 7. No Parallel Chat Message Buffers

Bad:

```kotlin
class ChatMessageBuffer { val messages = MutableStateFlow(...) }
```

Good:

```kotlin
val messages = sessionRepository.messages(SessionId(sessionId))
```

Why: duplicate buffers diverge from SSE/repository state. Message state belongs in `SessionRepositoryImpl`.

### 8. No Compatibility Route Silent Guessing

Bad:

```kotlin
val directory = routeDirectory ?: restoreFromOldPrefs()
```

Good:

```kotlin
val restored = tabManager.restoreState(persistedState, activeServer)
```

Why: legacy routes may lack workspace identity. Restoration must use versioned tab state and show explicit mismatch errors instead of guessing.

### 9. No Global API Variants

Bad:

```kotlin
api.listSessionsGlobal()
repository.refreshGlobal()
```

Good:

```kotlin
workspaceClient.listSessions(directory = workspace.directory)
sessionRepository.refresh()
```

Why: global variants become escape hatches around workspace scoping. Server-global behavior is represented by `workspace.directory == null`.

### 10. No Nullable `withWorkspace` Escape Hatches

Bad:

```kotlin
withWorkspace { workspace: Workspace? -> api.listSessions(workspace?.directory) }
```

Good:

```kotlin
withWorkspace(workspace) { scopedWorkspace ->
    workspaceClient(scopedWorkspace).listSessions(directory = scopedWorkspace.directory)
}
```

Why: nullable workspace callbacks recreate the old “maybe there is context” behavior. Workspace-required code must require a non-null `Workspace`.

## Skills

| Skill | Invocation | What it does |
|-------|-----------|--------------|
| Play Release | `/p:play-release [version]` | Full Play Store release prep: preflight checks, version bump, changelog, signed AAB/APK build, git tag |

## Quick reference

```bash
tk ready              # Find available work
tk show <id>          # View issue details
tk start <id>         # Claim work
tk close <id>         # Complete work
```
