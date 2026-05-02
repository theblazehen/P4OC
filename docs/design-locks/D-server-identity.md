# Lock D — Server Identity / `ServerRef` Equality

**Ticket:** `oa-cemz`
**Status:** Locked

## Decision

`ServerRef` is **equal by normalized `endpointKey`** (via `ServerUrl.endpointKey()`), not by raw URL or generation.

A separate **monotonic `connectionGeneration: Long`** is carried on every `Connection` and every `WorkspaceClient`, minted by `ConnectionManager` on each fresh `connect()`.

Stale-client rejection requires **both** identity (`ServerRef`) and freshness (`generation`).

```kotlin
@JvmInline value class ServerGeneration(val value: Long)

data class ServerRef(
    val endpointKey: String,        // identity (ServerUrl.endpointKey)
    val displayName: String,        // descriptive only
)

data class Connection(
    val config: ServerConfig,
    val serverRef: ServerRef,
    val generation: ServerGeneration,
    val api: OpenCodeApi,
    val eventSource: OpenCodeEventSource,
    val gateway: ServerEventGateway,
)
```

### Rules

1. **`ServerRef` equality** is data-class equality on `endpointKey`. `displayName` is metadata, not part of equality.
2. **Generation source.** `ConnectionManager` owns `private val generationCounter = AtomicLong(0)`. Every successful `connect()` increments and stamps the new `Connection`. `disconnect()` clears active. `reconnectSse()` (SSE-only restart) keeps the same generation.
3. **Reconnect to same URL** → same `ServerRef`, new `generation`. Old `WorkspaceClient`s fail generation check; persisted tabs remain eligible to restore.
4. **Server crash + restart at same URL** → same `ServerRef`, new `generation`. Same identity, fresh lease.
5. **Re-auth with new credentials, same URL** → same `ServerRef`, new `generation`. Auth is transport state, not identity.
6. **Different normalized endpoint** → different `ServerRef`. Examples: scheme/port/host change.
7. **`ActiveServerApiProvider`** checks both:
   ```kotlin
   active.serverRef == requested.serverRef &&
   active.generation == requested.generation
   ```
   Mismatch on either → throws `StaleWorkspaceClientException`. The API object alone is never exposed.
8. **Persistence validation** — when restoring tabs, "workspace is on the active server" means `persisted.workspace.server == activeConnection.serverRef`. Generation is **not** persisted (runtime-only).
9. **Persisted-but-mismatched tab** — restored as **placeholder**: visible in tab bar, content shows "This tab was on `https://x:9999` — reconnect to view." Never silently rebind.
10. **Multi-server future-proofing** — `ConnectionManager._connection` is shaped as `_connections: StateFlow<Map<ServerGeneration, Connection>>` constrained to size ≤ 1 today by `connect()` (which calls `disconnect()` first). Lifting the constraint later is ticket-sized, not redesign-sized.

## Rejected alternatives

- **Equal-by-generation** (`ServerRef` identity = generation). Persisted tabs need a stable identity that survives reconnect; generation invalidates them every connect. Defeats the persistence story (commit 9, non-negotiable).
- **Equal-by-baseUrl-string** (raw URL). Trivially broken: `http://foo:9999` vs `http://foo:9999/` mismatch.
- **Equal-by-config-id from `SettingsDataStore`.** Ties identity to a recents-list row that may not exist. Survives server-restart at same URL with same identity, which is *correct* — but `endpointKey` already does that more cheaply.
- **Server-instance probe** (e.g. `/global/health` returning a stable instance UUID). Server doesn't expose one today; would survive server-restart but URLs change too. Out of scope; revisit if server adds an identity endpoint.

## Worked examples

| Scenario | `ServerRef` | `generation` |
|---|---|---|
| Connect to `https://foo.local:9999` | `endpointKey="https://foo.local:9999"` | 1 |
| Disconnect, reconnect to same URL | same | 2 |
| Server crashes + restarts at same URL | same (after reconnect) | 3 |
| Re-auth same URL with new creds | same | 4 |
| Connect to `https://bar.local:9999` | new `endpointKey` | 5 (new identity, new lease) |
| mDNS dual-route: Tailscale vs LAN | **different** `endpointKey` (different normalized URL) | independent generations |
| Persisted tab on `ServerRef X`, user connects to `ServerRef Y` | tab restored as **placeholder**; never silently retargeted | n/a |
| Open `WorkspaceClient(serverRef=X, gen=4)`, `ConnectionManager` switches to `(Y, gen=5)` mid-call | next `apiFor(...)` call throws `StaleWorkspaceClientException` | n/a |

## Files affected

- `app/src/main/java/dev/blazelight/p4oc/domain/server/ServerRef.kt` (new) — types above.
- `app/src/main/java/dev/blazelight/p4oc/core/network/ServerUrl.kt` — use existing `endpointKey(...)`; no semantic changes beyond current normalization.
- `app/src/main/java/dev/blazelight/p4oc/core/network/Connection.kt` — `Connection` gains `serverRef`, `generation`, `gateway`.
- `app/src/main/java/dev/blazelight/p4oc/core/network/ConnectionManager.kt` — construct `ServerRef`, mint generation, expose `activeRef: StateFlow<ServerRef?>`; ensure `reconnectSse()` reuses generation; convert `_connection` to map shape (size ≤ 1 enforced).
- `app/src/main/java/dev/blazelight/p4oc/data/server/ActiveServerApiProvider.kt` (new) — enforces `serverRef + generation` checks.
- `app/src/main/java/dev/blazelight/p4oc/data/workspace/WorkspaceClient.kt` — captures immutable `Workspace` and connection generation at construction; routes every call through `ActiveServerApiProvider`.
- `app/src/main/java/dev/blazelight/p4oc/domain/workspace/Workspace.kt` — `Workspace(server: ServerRef, directory: String?)`.
- `app/src/main/java/dev/blazelight/p4oc/ui/tabs/TabState.kt` — persisted tab carries `Workspace` with `ServerRef`, not bare `sessionId`.
- `app/src/main/java/dev/blazelight/p4oc/ui/tabs/TabManager.kt` — restore tabs only when persisted `workspace.server == activeRef`; placeholder otherwise; observe `activeRef` for disconnect cascade.
- `app/src/main/java/dev/blazelight/p4oc/ui/screens/server/ServerViewModel.kt` — `initializeProjectContext` deleted; disconnect path triggers cascade via `ConnectionManager.disconnect()`.
- `app/src/main/java/dev/blazelight/p4oc/core/datastore/SettingsDataStore.kt` — store persisted tab JSON with `endpointKey`; remove dead keys (plan §4).

## Test cases (oa-ja73)

- `serverRefEquality_normalizesDefaultPort`: `foo.local`, `http://foo.local:4096` → equal `ServerRef`.
- `serverRefEquality_normalizesCaseAndTrailingSlash`: `HTTPS://FOO.LOCAL:9999/`, `https://foo.local:9999` → equal.
- `serverRefEquality_differentSchemeNotEqual`: `http://foo:4096`, `https://foo:4096` → not equal.
- `serverRefEquality_differentAliasNotEqual`: `https://foo.tailnet.ts.net:9999`, `https://192.168.1.9:9999` → not equal.
- `connectionManager_reconnectSameUrlIncrementsGeneration`: connect URL X, disconnect, connect URL X → same `serverRef`, generation increments.
- `connectionManager_reauthSameUrlIncrementsGeneration`: connect URL X creds A, connect URL X creds B → same `serverRef`, generation increments.
- `connectionManager_reconnectSseKeepsGeneration`: connect (gen=1), `reconnectSse()` → generation == 1.
- `activeServerApiProvider_rejectsDifferentServer`: client has `ServerRef X`, active is `Y` → throws.
- `activeServerApiProvider_rejectsOldGenerationSameServer`: client has `(X, gen=1)`, active has `(X, gen=2)` → throws.
- `tabRestore_acceptsMatchingServerRef`: persisted server == active `serverRef` → tab restored live.
- `tabRestore_rejectsDifferentServerRef`: mismatch → tab in placeholder state, no `WorkspaceClient` constructed.
- `tabManager_disconnectCascadeTearsDownAllTabWorkspaceVMs`: 3 chat tabs live, call `disconnect()` → each `WorkspaceViewModel.onCleared()` invoked exactly once.
- `connectionManager_connectionsMapSizeAtMostOne`: repeated connect → internal map never exceeds 1 entry.
- `staleWorkspaceClient_switchMidCall`: acquire client for X/gen1, switch to Y/gen2 before API call → provider rejects call.
