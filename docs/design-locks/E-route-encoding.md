# Lock E — Route Encoding for Workspace + SessionId

**Ticket:** `oa-blgp`
**Status:** Locked

## Decision

Routes are **plain strings** containing `tabId` (UUID) and screen-local IDs (`sessionId`, `projectId`). `Workspace` lives in the tab-scoped `WorkspaceViewModel` and never appears in route strings.

```kotlin
data object Chat        : Screen("tab/{tabId}/chat/{sessionId}")
data object SessionDiff : Screen("tab/{tabId}/session_diff/{sessionId}")
data object Sessions    : Screen("tab/{tabId}/sessions")
data object Files       : Screen("tab/{tabId}/files")
data object FileViewer  : Screen("tab/{tabId}/files/view?path={path}")
data object Git         : Screen("tab/{tabId}/git")
```

### Rules

1. **Route templates** include `tabId` as the workspace handle. Path arguments use `Uri.encode(...)`; UUIDs are URL-safe so encoding is a no-op.
2. **`Workspace`** is owned by `WorkspaceViewModel` (tab-scoped), initialized from `TabState.content.workspace`, mirrored into the tab graph's `SavedStateHandle` under key `"workspace"`.
3. **`SessionId`** lives in route args for chat/diff screens. One back-stack entry per session — each session is a Compose-Nav destination, not a VM-internal stack.
4. **`Workspace.directory`** (filesystem path with spaces / unicode / `?` / `#`) **never appears in any route**. Encoding hazard eliminated by construction.
5. **Persisted tab JSON** carries `Workspace` (not the route):
   ```json
   {
     "id": "tab-1",
     "content": {
       "type": "workspace",
       "workspace": {
         "server": { "endpointKey": "https://foo.local:9999" },
         "directory": "/some/path with spaces"
       },
       "activeSessionId": "ses_123",
       "startRouteKind": "chat"
     }
   }
   ```
6. **Sub-session in same tab** → push new `tab/{tabId}/chat/{subSessionId}` entry; back-press uses normal Compose-Nav back stack. Same `WorkspaceViewModel`, new `ChatViewModel`.
7. **Sub-session in new tab** (current `openSubAgentInNewTab` setting) → `tabManager.createTab(workspace = parentWorkspace, startRoute = chat/{newTabId}/{subSessionId})`. New tab inherits parent's `Workspace`.
8. **Old `?directory=` deep links** → `Screen.LegacyLinkRetired` (Lock A). No silent migration.
9. **AndroidManifest** — no new deep-link intent filters. Currently has only the launcher filter; keep it that way. External deep links are out of scope; future external links must construct `tabId` at the activity entry point.
10. **`SavedStateHandle` vs route-args** — route args hold IDs (`tabId`, `sessionId`, `projectId`). `SavedStateHandle` is used only inside `WorkspaceViewModel` for config-change persistence. Process-death restoration is JSON in DataStore, not `SavedStateHandle`.

### Route builders

```kotlin
fun chat(tabId: String, sessionId: SessionId): String =
    "tab/${Uri.encode(tabId)}/chat/${Uri.encode(sessionId.value)}"

fun sessionDiff(tabId: String, sessionId: SessionId): String =
    "tab/${Uri.encode(tabId)}/session_diff/${Uri.encode(sessionId.value)}"
```

There is no overload that accepts `directory`. Compile-time enforcement.

## Rejected alternatives

- **Typed Compose-Nav routes** (Navigation 2.8 `@Serializable` data classes carrying `Workspace`). `ServerRef` (with `generation` if generation-equality were used; even with endpointKey-equality, server identity is runtime-resolved) is not safely round-trippable through process death — must re-resolve against live `ConnectionManager`. Two persistence paths is one too many. Routes also lock the schema; adding a `Workspace` field becomes a migration. `tabId` strings are immune.
- **URL-encoded JSON workspace/session in nav args.** All disadvantages of typed routes plus none of the type-safety. Forces filesystem paths into URLs — exactly the bug class we're killing.
- **Plain `tabId` only**, with `sessionId` held inside `WorkspaceViewModel` as a session stack. Conflates Compose-Nav back stack with VM state. Replicating back-stack semantics in a VM duplicates the abstraction; harder to debug ("what session am I on?" requires VM introspection).
- **Hash-based WorkspaceId in route**, looked up via registry. Functionally identical to `tabId` but with a separate id namespace. Just use `tabId`.

## Worked examples

| Scenario | Behavior |
|---|---|
| Open chat from session list | `navController.navigate(Screen.Chat.createRoute(tabId, sessionId))`. ChatScreen reads `sessionId` from route args; resolves `Workspace` from `WorkspaceViewModel`. |
| Open child session in same tab | `navController.navigate(Screen.Chat.createRoute(tabId, childSessionId))`. Workspace unchanged. |
| Open child session in new tab | `tabManager.createWorkspaceTab(workspace = parent.workspace, startRoute = chat/{newTabId}/{childId})`. New tab inherits parent's workspace. |
| Restore tab list after process death | `TabManager.restore()` reads JSON. For each tab: validate `workspace.server == activeRef`; construct `WorkspaceViewModel` and `NavHost` start route. |
| Old deep link `?directory=/some/path%20with%20spaces` | `Screen.LegacyLinkRetired` error. No `Uri.decode` of directory into a workspace. No active-connection fallback. |
| Diff screen | `Screen.SessionDiff.createRoute(tabId, sessionId)`. `SessionDiffScreen` resolves `WorkspaceSession` from `WorkspaceViewModel + sessionId`. |
| Workspace path with reserved characters (`/tmp/a b/%25?x#frag/日本語`) | Persisted in tab JSON. Never appears in any route. File viewer queries inside that workspace use `Uri.encode(path)` only for the relative file path query arg. |

## Files affected

- `app/src/main/java/dev/blazelight/p4oc/ui/navigation/Screen.kt` — replace `chat/{sessionId}?directory={directory}` and `session_diff/{sessionId}` with tab-scoped routes; remove `ARG_DIRECTORY`.
- `app/src/main/java/dev/blazelight/p4oc/ui/tabs/TabState.kt` — replace `sessionId`/`sessionTitle` with `TabContent.WorkspaceTab(workspace, activeSessionId, title, startRouteKind)`.
- `app/src/main/java/dev/blazelight/p4oc/ui/tabs/TabManager.kt` — `createWorkspaceTab(...)`; persist/restore tab JSON; validate server on restore.
- `app/src/main/java/dev/blazelight/p4oc/ui/tabs/TabNavHost.kt` — pass `tabId` to every workspace route builder; remove directory route arg; fix sub-session route construction (currently drops directory).
- `app/src/main/java/dev/blazelight/p4oc/ui/screens/sessions/SessionListScreen.kt` — change callbacks from `(sessionId, directory)` to `SessionId`.
- `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/ChatScreen.kt` — receive `WorkspaceSession` via `WorkspaceViewModel + sessionId`.
- `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/ChatViewModel.kt` — remove route-directory fallback; no `directory` route arg.
- `app/src/main/java/dev/blazelight/p4oc/ui/screens/diff/SessionDiffScreen.kt` — receive `WorkspaceSession`, not bare `sessionId`.
- `app/src/main/AndroidManifest.xml` — no new deep-link filters; sweep for legacy filters.
- `app/src/main/java/dev/blazelight/p4oc/core/datastore/SettingsDataStore.kt` — store persisted tab JSON.

## Test cases (oa-ja73)

- `chatRoute_pattern_isTabAndSession`: `Screen.Chat.route == "tab/{tabId}/chat/{sessionId}"` exactly.
- `chatRoute_createRoute_returnsLiteralWithoutDirectory`: `Screen.Chat.createRoute(tabId, sessionId)` does not contain `directory=`.
- `chatRoute_uuidArgsRoundTrip`: 100 random UUIDs → encoded route → decoded args match input.
- `chatRoute_oldDirectoryRouteRejected`: `chat/sess1?directory=/foo` → `LegacyLinkRetired` error screen.
- `workspaceViewModel_scopedByTabId`: same `tabId` returns same instance; different `tabId` returns distinct instances.
- `workspaceViewModel_torndownOnTabClose`: close tab → `onCleared()` invoked; subsequent request yields new VM.
- `workspaceViewModel_setActiveSession_persistsToSavedStateHandle`: set session, simulate config change → activeSessionId preserved.
- `tabManager_restore_endpointKeyMatch_buildsLiveWorkspace`: persisted server == active → live `Workspace` constructed.
- `tabManager_restore_endpointKeyMismatch_placeholder`: mismatch → tab present with `TabContent.Placeholder(persistedEndpointKey)`; no `Workspace` constructed.
- `sessionList_onSessionClick_doesNotPassDirectory`: callback signature is `(SessionId) -> Unit` (compile-time check).
- `subAgent_newTab_inheritsWorkspaceFromParent`: open sub-agent with new-tab setting → new tab `Workspace.directory == parent.directory`, same `ServerRef`.
- `subAgent_sameTab_inheritsWorkspaceFromParent`: open sub-agent in same tab → same workspace, navigates to `tab/{tabId}/chat/{subSessionId}`.
- `fileViewer_routeHasNoPathArg`: `Screen.FileViewer.route` does not contain `path=` as a path segment (only as query arg encoded with `Uri.encode`).
- `manifest_noChatDeepLinkFilter`: parse `AndroidManifest.xml`, assert no `<data>` element references `chat/`.
- `workspacePath_notEncodedInChatRoute`: workspace directory `/tmp/a b/%25?x#frag/日本語`, open chat → route contains none of that directory.
- `sessionDiff_routeIncludesTabId`: opening diff for `ses_1` in tab `tab-A` → route `tab/tab-A/session_diff/ses_1`.
