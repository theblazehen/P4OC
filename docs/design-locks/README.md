# Design Locks — Workspace Cutover

These are the load-bearing decisions for the workspace-cutover migration
(epic `oa-gt0g`). Each lock is one concrete decision with worked examples
and test cases. Once locked, downstream tickets implement against the
lock; revisiting a lock means re-opening its design ticket.

| Lock | Topic | Ticket |
|---|---|---|
| [A](./A-deep-links-and-prompts.md) | Deep link migration + permission/question modality | `oa-7ysx` |
| [B](./B-sse-hydrate-race.md) | SSE hydrate-then-stream race semantics | `oa-vvep` |
| [C](./C-mutation-on-failure.md) | Mutation-on-failure contract (optimistic apply + refetch) | `oa-0f4m` |
| [D](./D-server-identity.md) | Server identity / `ServerRef` equality | `oa-cemz` |
| [E](./E-route-encoding.md) | Route encoding for Workspace + SessionId | `oa-blgp` |
| [F](./F-event-routing.md) | SSE event → workspace routing | `oa-ww0m` |

The full migration plan lives at
[`/tmp/workspace-cutover-plan.html`](file:///tmp/workspace-cutover-plan.html)
(temporary; will be moved into `docs/` if it survives the cutover).

## Cross-lock invariants

Every lock honours these globally:

- **No ambient context.** No global mutable `currentWorkspace`/`currentSession`/`currentDirectory` of any shape, regardless of layer.
- **Server is the source of truth.** Local state is a cache of server state; on conflict, server wins.
- **Identity is explicit and typed.** `ServerRef`, `Workspace`, `WorkspaceSession`, `SessionId`, `RelativePath`, `WorkspacePath` are the only context primitives.
- **Lifecycle owns scope.** A `WorkspaceViewModel` lives for the lifetime of one tab; closing the tab disposes everything in scope. No leak-by-key-eviction.
- **Old deep links are rejected, not migrated.** No best-effort guess of `Workspace` from a URL.
