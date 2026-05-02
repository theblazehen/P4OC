# OpenSpec Agent Guidance

## Workspace Cutover Forbidden Patterns

These patterns are forbidden after the workspace/session cutover. Specs and proposals must not introduce them as compatibility shims, transitional helpers, or fallback paths.

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
