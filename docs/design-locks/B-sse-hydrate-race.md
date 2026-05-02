# Lock B — SSE Hydrate-then-Stream Race

**Ticket:** `oa-vvep`
**Status:** Locked

## Decision

1. **SSE collection starts BEFORE REST hydration.** No race window where events fire before the subscription exists.
2. Hydration boundary = "REST snapshot fully unmarshalled and seeded into state, then buffer drained." Single atomic `Hydrating → Live` transition guarded by a `Mutex`.
3. Reducer state shape:
   ```kotlin
   sealed interface RepoState {
       data class Hydrating(val buffer: ArrayDeque<OpenCodeEvent>, val overflowed: Boolean) : RepoState
       data class Live(val snapshot: Snapshot, val seq: Long) : RepoState
       data class Stale(val snapshot: Snapshot, val error: Throwable) : RepoState
   }
   ```
4. **Reducer is pure `(Snapshot, Event) → Snapshot`.** Repository owns the buffer; reducer never sees `Hydrating`.
5. Buffered events replay deduped by **synthesized `EventKey`** because the server protocol carries no native sequence id. Per-event-type keys:
   - `MessageUpdated` → `("msg.upsert", message.id, message.updatedAt-or-createdAt)`
   - `MessagePartUpdated` → `("part.upsert", part.id, delta.hashCode())`
   - `SessionUpdated` → `("session.upsert", session.id, session.updatedAt)`
   - `SessionCreated`/`Deleted` → `("session.create"|"session.delete", session.id)`
   - `SessionStatusChanged` → not deduped; last-write-wins on `(sessionID,)`
   - `PermissionRequested` → `("perm", permission.id)`
   - `QuestionAsked` → `("q", request.id)`
   - The reducer applies events idempotently regardless; dedup is performance/log-noise optimization.
6. Lifecycle events (`Connected`/`Disconnected`/`Error`) bypass the buffer; flow on a separate `connectionState` channel and never enter the session reducer.
7. **Buffer capacity 512.** Overflow policy: **drop-oldest + warn-log (debounced 1 s) + set `snapshotDirty=true` + schedule `hydrate(force=true)` after 250 ms debounce on `Live` transition.**
8. Hydrate failure with previous snapshot → `Stale(previousSnapshot, error)`; continue applying live events. Without previous → `Stale(emptySnapshot, error)`. Streaming-only fallback is allowed because reducer is seedless.
9. Cancellation during hydrate (workspace scope dies) → coroutine cancels, buffer GC'd with scope, no state commit.
10. Retry: user-triggered or auto on next reconnect. `hydrate(force=true)` rebuilds snapshot, then merges with current `Stale.snapshot` taking REST as source of truth for snapshot fields, but preserving any post-hydrate live events received during the new hydrate window (recursive — same buffering rules, capped at 3 retries).
11. App background during hydrate: `WorkspaceViewModel` survives backgrounding; hydrate continues. If SSE drops, on resume `OpenCodeEventSource` reconnects and `Stale` may trigger re-hydrate.

## Rejected alternatives

- **Drop events received during hydrate, rely on REST snapshot completeness.** REST snapshot is point-in-time at request *send*; events fire continuously. Dropping `MessagePartUpdated` mid-hydrate visibly breaks streaming. Buffer-and-replay is mandatory.
- **Single mutable snapshot updated by both hydrate and SSE concurrently with a mutex.** Skips explicit state machine; ordering becomes implicit and untestable; hydrate failure has no clean state.
- **Buffer overflow → fail hydration with `HydrateBufferOverflow` and retry.** More complex state machine. Drop-oldest + dirty + re-hydrate is simpler and the correctness outcome is the same: re-fetch authoritative state.

## Worked examples

| Scenario | Behavior |
|---|---|
| `session.created` arrives before REST snapshot completes | Buffered. After REST commit, replay applies it. Final `Live.snapshot` contains the session exactly once. |
| Same session in REST and SSE buffer | Reducer applies `session.upsert` keyed by id, last-write-wins by `updatedAt`. |
| Hydrate timeout, no previous snapshot | `Stale(emptySnapshot, TimeoutException)`. Buffered events drain into stale snapshot. UI shows banner + retry. |
| User leaves screen during hydrate | Scope cancels → REST jobs cancel → SSE collection cancels → buffer GC'd → no state commit. |
| App backgrounds during hydrate | Hydrate continues. SSE may drop; reconnect on resume; `Stale` triggers re-hydrate. |
| Buffer overflow (>512 events during slow hydrate) | Drop oldest, warn log, `overflowed=true`. On `Live` transition, schedule `hydrate(force=true)`. |
| `Disconnected` during `Hydrating` | `connectionState=Disconnected` immediately, buffer untouched. On subsequent `Connected`, hydrate retries. |

## Files affected

- `app/src/main/java/dev/blazelight/p4oc/core/network/SessionDataCache.kt` — DELETE; port bounded concurrency, freshness, fan-out, dedupe semantics into repository.
- `app/src/main/java/dev/blazelight/p4oc/data/session/SessionRepository.kt` (new) — interface with `state: StateFlow<RepoState>`, `hydrate(force: Boolean = false)`, `connect()`.
- `app/src/main/java/dev/blazelight/p4oc/data/session/SessionRepositoryImpl.kt` (new) — implements rules above.
- `app/src/main/java/dev/blazelight/p4oc/data/session/SessionReducer.kt` (new) — pure `(Snapshot, OpenCodeEvent) -> Snapshot`. Idempotent.
- `app/src/main/java/dev/blazelight/p4oc/data/session/RepoState.kt` (new) — sealed interface above.
- `app/src/main/java/dev/blazelight/p4oc/data/server/ServerEventGateway.kt` (new) — single `/global/event` flow per server, fans out.
- `app/src/main/java/dev/blazelight/p4oc/ui/workspace/WorkspaceViewModel.kt` — subscribe-then-hydrate ordering.
- `app/src/main/java/dev/blazelight/p4oc/ui/screens/sessions/SessionListViewModel.kt` — observe `state`; render banner on `Stale`.
- `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/MessageStore.kt` — DELETE; merge logic moves into reducer.

## Test cases (oa-ja73)

- `eventDuringHydrate_buffered_replayedAfter`: subscribe → fire `MessageUpdated` → resolve REST → drained buffer applied; `Live.snapshot.messages` contains exactly one entry.
- `duplicateEventInBuffer_dedupedByKey`: fire same `MessagePartUpdated` twice → reducer is idempotent under same final state.
- `bufferOverflow_dropsOldestAndMarksDirty`: fire 600 events during fake-slow hydrate → `overflowed=true`, on `Live` a `hydrate(force=true)` is scheduled.
- `hydrateTimeout_transitionsToStale_continuesStreaming`: REST hangs >15 s → state=`Stale`; subsequent live event lands in `Stale.snapshot`.
- `userCancelsDuringHydrate_noLeak`: scope cancels mid-hydrate → no `state` emission after cancel; gateway subscription disposed.
- `connectedDisconnectedNotBuffered`: emit `Disconnected` during `Hydrating` → `connectionState=Disconnected` immediately; buffer untouched.
- `reducerIsPureAndIdempotent`: apply same event N times to a snapshot → equal output every time (property test).
- `restAndSseDisagree_lastWriteWins`: REST seeds `updatedAt=100`; buffered `SessionUpdated` has `updatedAt=200` → `Live.snapshot.session.updatedAt==200`.
- `hydrateRetryAfterStale_preservesLiveEvents`: enter `Stale`, receive 3 live events, retry → new `Live.snapshot` reflects REST data merged with the 3 live events.
- `bufferIsBoundedTo512`: emit 1000 events during `Hydrating` → `buffer.size <= 512`, oldest dropped.
- `hydrateTriggeredOnDirtySnapshot`: after overflow, transition to `Live` → `hydrate(force=true)` scheduled within 250 ms.
