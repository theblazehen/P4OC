# Lock C — Mutation-on-Failure (Optimistic Apply + Refetch)

**Ticket:** `oa-0f4m`
**Status:** Locked

## Decision

The reducer has **no `Apply`/`Confirm`/`Reject` machinery**. There is no `pendingMutations` map, no `prevSessions` inverse-patch map, and no SSE-contradiction logic.

The mutation flow is two simple rules:

1. **Optimistic local apply** — repository emits a synthetic local event into the reducer (e.g., `SessionDeleted(id)`) before the HTTP call.
2. **On HTTP failure** — repository emits a snackbar and triggers `refetch(workspace)`. The reducer applies the REST snapshot as truth; whatever was optimistically removed/changed reappears or corrects automatically.

Server is the source of truth. SSE events are always applied as truth, never compared against a "pending" intent.

### Mutation table

| Mutation | Optimistic local event? | On HTTP failure |
|---|---|---|
| Delete session | `SessionDeleted(id)` | snackbar + `refetch(workspace)` |
| Rename session | `SessionUpdated(id, title=newTitle)` | snackbar + `refetch(workspace)` |
| Share session | `SessionUpdated(id, sharePending=true)` (no fake URL) | snackbar + `refetch(workspace)` |
| Unshare session | `SessionUpdated(id, shareUrl=null)` | snackbar + `refetch(workspace)` |
| Summarize session | none (server computes summary) | snackbar; no refetch needed |
| Create session | none (response-driven; server returns id) | snackbar |

### Refetch granularity

**Coarse: full workspace session list re-hydrate via the existing bounded-concurrency path (`Semaphore(10)`).** No targeted `getSession(id)` path. Reasons:

- Failure is rare; refetch cost is bounded by existing fan-out semantics.
- Always doing the same thing on failure is easier to reason about and test.
- `SessionRepository.refetch(workspace)` is also the auto-recovery path for `Stale` snapshots (Lock B), `dirty` snapshots after overflow, and reconnect scenarios. One mechanism, three triggers.

### Failure modes

| Trigger | Behavior |
|---|---|
| HTTP 4xx, 5xx, network exception, timeout (10 s) | snackbar with retry action + `refetch(workspace)` |
| Workspace scope death mid-flight (tab close, server switch) | coroutine cancels; no UI; snapshot is GC'd with VM |
| SSE event during pending optimistic state | applied as truth; no contradiction handling |
| Connection lost mid-flight | snackbar `"Server disconnected — refreshing"` + `refetch` once reconnected |

### User-visible feedback

- Snackbar with `Retry` action for HTTP failures.
- Multiple concurrent failures → coalesce when possible (`"Couldn't delete 3 sessions — try again"`), else queue separately.
- The 100–300 ms gap between optimistic apply and refetch returning is the only user-visible cost.

## Why no rollback?

Rollback was solving "put the row back if HTTP failed" with an inverse-patch mechanism living next to SSE-driven state. Two state-restoration mechanisms is worse than one. With server-as-source-of-truth, refetch is the same recovery path used everywhere else (hydrate failure, dirty snapshot, reconnect). Inverse patches are also exactly the kind of code that drifts: add a field to `Session`, forget the inverse patch, get subtly wrong rollback. Refetch can't drift.

## Rejected alternatives

- **Inverse-patch rollback** (the original Lock C). Two mechanisms; drift risk; reentrant SSE-contradiction logic; large test matrix. Rejected.
- **Pessimistic mutations (no optimism).** Sessions list responds slowly enough on rural network that rename/share feel laggy. Optimism is worth the ~200 ms refetch flash on failure.
- **Targeted `getSession(id)` refetch.** Faster on failure but adds a code path; failure is rare; coarse refetch is the same path used for hydrate recovery.
- **SSE contradiction triggers refetch.** Wasteful and pointless: if SSE arrives with the truth, the reducer already has the truth. No refetch needed.

## Worked examples

| Scenario | Behavior |
|---|---|
| Delete A; HTTP 500 | Local: A removed. HTTP fails → snackbar + `refetch(workspace)`. REST returns A → reducer reapplies snapshot → A reappears (~200 ms flash). |
| Rename A to "X"; HTTP succeeds; SSE later delivers `SessionUpdated(A, title="Y")` from another client | Local: title="X". HTTP success: no-op. SSE: title="Y" applied as truth. Final title="Y". |
| Share A; HTTP succeeds with real URL; SSE never arrives | Local: pending share flag, no fake URL. HTTP success: reducer applies returned authoritative session including `shareUrl`. Pending flag cleared. |
| Delete A; before HTTP, SSE delivers `SessionDeleted(A)` | Local: A removed (synthetic event). SSE: `SessionDeleted(A)` applied — idempotent, A still removed. HTTP success: no-op. HTTP failure: refetch fires; REST excludes A; reducer matches. |
| Disconnect mid-delete | Coroutine cancels; HTTP cancels. On reconnect: refetch as part of normal reconnect flow; whatever the server has wins. |
| Delete 3 rapidly; all return 503 | All 3 optimistically removed. 3 HTTP failures → 1 coalesced snackbar + 1 refetch (deduped) → all 3 reappear. |
| Summarize A; HTTP timeout | Local: pending summarize spinner. HTTP timeout → snackbar; spinner cleared. No refetch needed (no list mutation). |

## Files affected

- `app/src/main/java/dev/blazelight/p4oc/ui/screens/sessions/SessionListViewModel.kt` — replace direct API calls with `repo.dispatch(MutationIntent.X)`; remove all rollback logic.
- `app/src/main/java/dev/blazelight/p4oc/data/session/SessionRepository.kt` — add `dispatch(intent)`, `refetch(workspace)`, `refetch(workspaceSession)` methods.
- `app/src/main/java/dev/blazelight/p4oc/data/session/SessionRepositoryImpl.kt` — emit synthetic local event into reducer, then call HTTP, then on failure trigger refetch + snackbar.
- `app/src/main/java/dev/blazelight/p4oc/data/session/SessionReducer.kt` — applies `OpenCodeEvent`s only. No pending-intent state. No `Apply`/`Confirm`/`Reject` events.
- `app/src/main/java/dev/blazelight/p4oc/data/session/MutationIntent.kt` (new) — sealed type for repository inputs (Delete/Rename/Share/Unshare/Summarize). Pure descriptor; no rollback bookkeeping.
- `app/src/main/java/dev/blazelight/p4oc/ui/screens/sessions/SessionListUiState.kt` — add `snackbarMessages: List<SnackbarMessage>` consumable flow. Remove pending-id tracking.
- `app/src/main/java/dev/blazelight/p4oc/ui/components/Snackbar*.kt` — host for actionable snackbars with retry.

## Test cases (oa-ja73)

- `delete_optimisticallyRemovesRow`: dispatch `Delete(A)` → row absent immediately.
- `delete_http500_refetchesAndRestoresFromServer`: dispatch `Delete(A)` → mock 500 → snackbar emitted + `refetch` invoked → REST returns `[A]` → reducer state contains A.
- `delete_sseConfirmsBeforeHttp_idempotent`: dispatch `Delete(A)`, fire `SessionDeleted(A)` event, then resolve HTTP 2xx → reducer state excludes A; no double-removal artifacts.
- `delete_sseConfirmsBeforeHttp_httpErrorTriggersRefetch`: dispatch `Delete(A)`, fire `SessionDeleted(A)`, then HTTP 500 → snackbar + refetch; REST excludes A; reducer state excludes A.
- `rename_httpSuccess_thenSseWithDifferentTitleFromOtherClient`: dispatch `Rename(A, "X")`, HTTP 2xx, later SSE `SessionUpdated(A, "Y")` → final title=`"Y"`.
- `rename_httpTimeout_refetches`: dispatch `Rename(A, "X")` → 11 s no response → snackbar + refetch; reducer state matches REST.
- `share_optimisticPlaceholder_thenHttpReplaces`: dispatch `Share(A)` → pending flag set, `shareUrl` unchanged from null. HTTP returns real URL → reducer applies authoritative session; pending cleared.
- `unshare_http400_refetches`: dispatch `Unshare(A)` (currently shared) → 400 → snackbar + refetch; REST returns A with shareUrl → reducer applies.
- `summarize_doesNotApplyOptimistic`: dispatch summarize → snapshot unchanged. HTTP error → snackbar; no refetch.
- `staleWorkspace_mutationCancelled`: dispatch `Delete(A)`, then `viewModelScope.cancel()` mid-flight → HTTP cancelled, no `MutationOutcome` emitted, no leak.
- `tripleDeleteAllRefetchOnce`: dispatch 3 Deletes, all 503 → at most 2 refetch calls (deduped within 250 ms window), single coalesced snackbar.
- `reducerIsPureUnderMutationFlow`: property test — `(snapshot) -> applyEvents(...) -> snapshot'` is deterministic.
- `sseWinsAlways`: dispatch `Rename(A, "X")`, fire SSE `SessionUpdated(A, "Z")` while HTTP pending → final title=`"Z"`. No "rollback snackbar".
