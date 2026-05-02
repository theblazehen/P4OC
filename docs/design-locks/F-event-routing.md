# Lock F — SSE Event → Workspace Routing

**Ticket:** `oa-ww0m`
**Status:** Locked

## Decision

The `/global/event` stream is consumed by **`ServerEventGateway`** (one per `ServerRef`, owned by `Connection`) which wraps every event into a `ScopedEvent` carrying server identity, workspace key, and the inner `OpenCodeEvent`. Per-workspace stores subscribe with a filter on `(serverRef, workspaceKey)`.

**`OpenCodeEventSource.parseAndEmitEvent` currently throws away `GlobalEventDto.directory` (line 192–200). This is a real latent bug.** Fix: emit a `RawScopedEvent(directory, event)` rather than a bare `OpenCodeEvent`.

```kotlin
data class ScopedEvent(
    val serverRef: ServerRef,
    val generation: ServerGeneration,
    val workspaceKey: WorkspaceKey,
    val event: OpenCodeEvent,
)

sealed interface WorkspaceKey {
    data class Directory(val value: String) : WorkspaceKey
    data object Global : WorkspaceKey
    data class SessionScoped(val sessionId: SessionId) : WorkspaceKey  // unresolved orphan
}
```

### Routing rules

1. **Wrapper `directory` non-empty** → `WorkspaceKey.Directory(directory)`.
2. **Wrapper empty + event has `sessionID`** → look up `SessionRepository.directoryForSession(sessionID)`.
   - Hit → `WorkspaceKey.Directory(...)`.
   - Miss → `WorkspaceKey.SessionScoped(sessionID)` (orphan; not delivered to any workspace store; logged at debug).
3. **Wrapper empty + lifecycle event** (Connected/Disconnected/Error/InstallationUpdated/PTY/LSP) → `WorkspaceKey.Global`. Only the global app shell subscribes.

### Subscription filter

```kotlin
gateway.events
    .filter {
        it.serverRef == workspace.server &&
        it.generation == activeConnection.generation &&
        it.workspaceKey is Directory &&
        it.workspaceKey.value == workspace.directory
    }
```

`null` workspace directory matches `Directory(null)` only or `Global`. A project workspace `/foo` does not receive global events.

### No broadcast-to-all-workspaces fallback

Plan §9 forbids it. Three tabs on three worktrees seeing the same `vcs.branch.updated` would all flicker incorrectly. Events with no resolvable workspace are dropped, not broadcast.

### Permission/question routing

Permission/question events route by wrapper directory first, then sessionID lookup. Per-tab `DialogQueueManager` (workspace-scoped per Lock A) only receives perms for its workspace. Two tabs both on `/foo` → both receive; queue dedupes by permission ID.

### Sub-agent / child-session events

`SessionRepository` tracks parent/child via `getSessionChildren()` (replaces `ChatViewModel.childSessionIds` in-memory set). Child events route to parent's workspace because they share `directory`. Cross-workspace child-tracking is explicitly not supported.

### Server identity scoping

`ServerEventGateway` discards events where `event.serverRef != active.serverRef || event.generation != active.generation || connectionState !is Connected`. Reconnect → fresh gateway, no replay of old-generation events.

### `SessionWorkspaceIndex`

Populated from:
- session list hydration
- `session.created` / `session.updated` events (carry `directory`)
- explicit `getSession(...)` results
- `getSessionChildren(...)` for child sessions

Synchronous `directoryForSession(sessionId)` lookup. Returns `null` on miss.

## Rejected alternatives

- **Broadcast events with no `directory` to all workspaces.** Recreates ambient context at the routing layer.
- **Route by inner payload `directory` field on a per-event-type basis.** Would require special-cases per event type. The wrapper `GlobalEventDto.directory` is uniform and authoritative.
- **Nullable directory string on every event.** More implicit than `WorkspaceKey` sealed type. `Global` and `SessionScoped` are intentional cases that deserve to be names, not magic null.

## Worked examples

| Scenario | Behavior |
|---|---|
| `session.created` with wrapper `directory="/foo"`; tab A on `/foo`, tab B global (null) | A receives; B does not. A's `SessionWorkspaceIndex` registers `sessionID → "/foo"`. |
| `message.updated` for child whose parent is in tab A | If wrapper `directory="/foo"`, A receives. If wrapper empty, gateway uses `SessionWorkspaceIndex[childSessionId] == "/foo"` (populated via `getSessionChildren()`). A's reducer applies. |
| `permission.asked` with `sessionID` but no wrapper directory | Gateway resolves via `SessionWorkspaceIndex[sessionID]`. Enqueued only in matching workspace's `DialogQueueManager`. If no mapping, log + drop. |
| Two tabs both on `/foo`, perm event arrives | Both receive; both enqueue; shared workspace-scoped `DialogQueueManager` dedupes by permission ID; modal shown once. |
| Event arrives during workspace switch within a tab | Old VM unsubscribes; new VM filter is `/bar`. Event for `/foo` arriving in the gap is unmatched and dropped. |
| Event arrives 100 ms after disconnect | Gateway scope cancelled; no subscribers. Event is GC'd. |
| `file.edited` with wrapper `directory="/foo"` | Routes to `/foo` workspace only. Mapped event has no sessionID; wrapper directory is mandatory. |
| Bare fallback `EventDataDto` with neither directory nor sessionID | Not delivered to any workspace reducer. May reach app-level diagnostics. |
| Server switches X → Y while old-gen event buffered | Gateway discards old-gen event. |

## Files affected

- `app/src/main/java/dev/blazelight/p4oc/data/server/ServerEventGateway.kt` (new) — wraps `OpenCodeEventSource.events`; attaches `ServerRef + generation + WorkspaceKey`. Lifecycle tied to `Connection`.
- `app/src/main/java/dev/blazelight/p4oc/core/network/OpenCodeEventSource.kt` — change inner emission type to `RawScopedEvent(directory, event)`. Stop discarding `globalEvent.directory` at line 192. Public `events: SharedFlow` becomes `SharedFlow<RawScopedEvent>`.
- `app/src/main/java/dev/blazelight/p4oc/data/remote/dto/EventDtos.kt` — `GlobalEventDto.directory` semantics confirmed.
- `app/src/main/java/dev/blazelight/p4oc/core/network/Connection.kt` — `Connection` gains `gateway: ServerEventGateway`; disconnect cancels gateway scope.
- `app/src/main/java/dev/blazelight/p4oc/core/network/ConnectionManager.kt` — construct `ServerEventGateway(serverRef, generation, eventSource, sessionRepo)`; pass into `Connection`.
- `app/src/main/java/dev/blazelight/p4oc/data/session/SessionRepository.kt` (interface) + `SessionRepositoryImpl.kt` — add `directoryForSession(sessionID): String?` synchronous lookup.
- `app/src/main/java/dev/blazelight/p4oc/data/session/SessionWorkspaceIndex.kt` (new) — `Map<(ServerRef, SessionId), Workspace.directory>`.
- `app/src/main/java/dev/blazelight/p4oc/domain/server/WorkspaceKey.kt` (new) — sealed `Directory` / `Global` / `SessionScoped`.
- `app/src/main/java/dev/blazelight/p4oc/domain/server/ScopedEvent.kt` (new) — envelope.
- `app/src/main/java/dev/blazelight/p4oc/data/workspace/WorkspaceClient.kt` — exposes `events: Flow<OpenCodeEvent>` filtered from gateway.
- `app/src/main/java/dev/blazelight/p4oc/ui/workspace/WorkspaceViewModel.kt` — collects `workspaceClient.events` into `SessionRepository.observe(...)`.
- `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/ChatViewModel.kt` — drops `childSessionIds: Set<String>` field; reads from `SessionRepository.children(parentId)` instead.

## Test cases (oa-ja73)

- `gateway_routesByWrapperDirectory`: feed event with `wrapperDir="/foo"` payload `session.created` → emitted `ScopedEvent.workspaceKey == Directory("/foo")`.
- `gateway_globalKeyForLifecycleEvents`: feed `Connected` with empty wrapperDir → `WorkspaceKey.Global`.
- `gateway_resolvesSessionScopedViaRepo`: index maps `s1 → "/foo"`, event with empty wrapperDir + `sessionID=s1` → `Directory("/foo")`.
- `gateway_orphanWhenRepoMissing`: no index for `ses_unknown`, event with empty wrapperDir → `SessionScoped(ses_unknown)`; not delivered to any subscriber.
- `gateway_serverRefStampedFromConnection`: gen=7 connection's gateway emits `ScopedEvent`s carrying `generation==7`.
- `workspaceClient_filtersByDirectory`: client for `/foo` sees event with `Directory("/foo")`; does not see `Directory("/bar")` or `Global`.
- `workspaceClient_filtersByGeneration`: client for gen=7 does not see events with gen=8.
- `workspaceClient_globalNotDeliveredToWorkspaces`: `Global` event never reaches any `WorkspaceClient.events`.
- `disconnect_terminatesGatewayFlow`: collect events; call `connection.disconnect()`; flow completes. New connect → fresh flow, no replay.
- `reconnect_doesNotReplayOldGenerationEvents`: emit on gen=1, disconnect, connect to gen=2, subscribe → no gen=1 events arrive.
- `permissionEvent_routedToOwningWorkspaceOnly`: tab A on `/foo`, tab B on `/bar`. `permission.asked` wrapper `/foo` → A's `DialogQueueManager` receives; B does not.
- `permissionEvent_twoTabsSameDirectory_dedupedById`: two tabs on `/foo`, permission `id=p1` → workspace-scoped queue size = 1 (deduped by permission id).
- `childSessionEvent_routesToParentWorkspace`: `getSessionChildren` registers `c → /foo`. `message.updated(sessionID=c, wrapperDir="/foo")` → A's reducer applies.
- `sessionCreatedChild_registersChildWorkspace`: `session.created(directory="/foo", parentID="parent")` → index maps child to `/foo`.
- `noMatch_loggedNotDelivered`: event for unknown workspace → no workspace receives; orphan diagnostic flow has 1 entry; no tab created.
- `disconnect_dropsLateEvent`: disconnect, then event arrives 100 ms later → no reducer receives.
- `serverSwitch_dropsBufferedOldServerEvent`: buffered event has `serverRef=X`, active is `Y` → discarded.
- `workspaceSwitch_doesNotMutateOldStore`: tab switches `/foo` → `/bar`; event for `/foo` arrives after switch → old store not mutated; new store not mutated (filter mismatch).
- `hydrate_buffersMatchingEventsOnly`: while `/foo` hydrates, events for `/foo` and `/bar` arrive → only `/foo` event buffered/replayed (Lock B).
- `eventSource_keepsWrapperDirectory`: feed raw JSON `{"directory":"/foo","payload":{...}}` → `OpenCodeEventSource.events` emits `RawScopedEvent(directory="/foo", ...)`. Catches the bug at OpenCodeEventSource.kt:192.
- `lifecycleConnected_notReducedAsWorkspaceMutation`: `Connected` event → app-level connection observer may receive; session reducer does not mutate.
