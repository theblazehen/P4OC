# Change: Add a read-only workspace Changes view

## Why

GitHub issue #63 identifies a review gap in Files. P4OC currently uses `GET /file/status` only to decorate rows in the directory that is already open. Those badges cannot reveal deleted files or changed paths outside that folder. Session diffs answer a different question: they describe changes attributed to one OpenCode session, not the workspace's current working tree. `FileViewer` also discards the diff returned with file content, and loading a file cannot discover deleted paths.

A user therefore cannot answer “what is currently changed in this tab's workspace?” without leaving P4OC. The missing view is especially risky with multiple tabs or servers because a branch name without the owning server and directory is not sufficient workspace identity.

## What Changes

Add a contextual **Changes** mode inside Files. It is a cleanly separated, strictly read-only review surface; it does not alter the normal directory browser or add persistent chat chrome.

The mode will:

- Enter from an accessible Changes action in Files and exit back to the exact prior Files folder/filter context.
- Show the tab-owned server, workspace directory (or an explicit global-workspace label), current branch, default branch when available, changed-file count, and aggregate additions/deletions.
- Load the complete structured status list for the workspace, including added, modified, deleted, and out-of-current-folder paths.
- Preserve the deterministic order supplied by `GET /vcs/status`; it will not apply the directory browser's folder-first or locale-dependent client sort.
- Show each workspace-relative path, textual status, additions, and deletions.
- Expand or collapse a row in place. At most one row can be expanded, and only that row's bounded patch is rendered.
- Support explicit initial loading, background refresh, clean/empty, patch loading, retryable failure, unsupported-server, malformed-response, stale-snapshot, response-too-large, and patch-too-large states.
- Refresh branch and status as one workspace snapshot, invalidate cached patches, and suppress results from cancelled or superseded loads.

## API and Data Contract

All calls originate from the `WorkspaceClient` captured by the Files scope for the owning tab. The implementation must use its immutable `Workspace(server, directory)` and server generation. It must pass the captured directory explicitly, including an intentional `null`, and use the existing explicit `workspace = null` routing value. It must never read an active tab, a global/default workspace, a last-used project, a session directory, or the directory browser's current folder to reconstruct routing.

The client adds bounded, workspace-routed reads for:

- `GET /vcs` for `{ branch?: string, default_branch?: string }`.
- `GET /vcs/status` for an ordered array of `{ file, status, additions, deletions }`, where `status` is `added`, `modified`, or `deleted`.
- `GET /vcs/diff?mode=git|branch&context=N` for an array of `{ file, patch?, status?, additions, deletions }`.

`mode` is a closed API/domain value with exactly `git` and `branch`. This Changes UI reviews the current working tree and therefore requests `mode=git` with `context=3`. A branch-comparison selector is not part of this change; `branch` remains represented so the upstream route cannot be called with an arbitrary string and can be used by a separately specified feature later.

Status is loaded before any diff. On first expansion, one bounded `git` diff snapshot is loaded and indexed by exact workspace-relative `file`; subsequent expansions may reuse that snapshot only while the status generation, workspace identity, mode, and context are unchanged. Refresh or workspace replacement clears it. Paths are matched exactly—never by basename, current folder, URI decoding, or filesystem probing.

The implementation uses raw streaming responses so limits are enforced before general JSON decoding. Limits are normative and measured in UTF-8 bytes:

| Data | Limit |
| --- | ---: |
| `GET /vcs` response | 64 KiB |
| `GET /vcs/status` response | 2 MiB |
| `GET /vcs/diff` response | 12 MiB |
| status or diff entries | 10,000 |
| one `file` value | 4 KiB |
| one branch/default-branch value | 512 B |
| one retained patch | 1 MiB |
| all decoded patch strings in one diff snapshot | 8 MiB |

A known `Content-Length` over its route cap is rejected before reading. An unknown-length or understated body is read only through cap plus one byte, then closed and rejected. A per-patch violation affects that row while other valid patches remain reviewable. A raw diff response or aggregate-patch violation rejects the diff snapshot but leaves the already loaded status list visible.

Structured decoding rejects ambiguous or unsafe data: a non-array list body; missing or wrongly typed required fields; blank, over-limit, or duplicate paths; an unknown status; negative, fractional, non-finite, or out-of-range counts; or invalid JSON. Optional missing diff `patch` is represented as “Patch unavailable,” not fabricated as an empty patch. Aggregate statistics are checked sums of the status snapshot, not values inferred from patch text.

## Presentation and Interaction

The Changes presentation state is independent of normal Files directory/search/edit state and is keyed by the tab workspace identity. It has:

- A list-level state: initial loading, content, empty, refreshing, unsupported, too large, or human-readable failure.
- Rows containing path, status, additions, deletions, and expansion state.
- A single selected path and a row-local patch state: loading, content, unavailable/stale, too large, or retryable failure.

Selecting a collapsed row selects and expands it; selecting it again collapses it. Selecting another row atomically collapses the old row and expands the new row, so two patch bodies are never composed at once. Deleted paths expand from the VCS patch and never require `GET /file/content`.

Initial load may publish content only after both workspace identity (`GET /vcs`) and status are valid. During manual refresh the prior list remains visible with an announced progress indicator. A successful refresh replaces it atomically and collapses any patch. A failed refresh retains the prior snapshot, clearly reports that refresh failed, and offers retry. Initial failure has a full-state retry action.

HTTP 404, 405, or 501 from a required VCS route means the connected server does not support workspace Changes. Malformed data, size violations, authorization/other HTTP failures, stale workspace clients, and network failures remain distinct domain failures. UI copy is concise and actionable; it must not expose response bodies, stack traces, URLs containing credentials, or raw protocol payloads. No failure automatically starts an unbounded retry loop.

## Accessibility

The Changes entry, exit, and refresh controls have stable accessible names and standard touch targets. Workspace/server and branch identity remain available to accessibility services even if visually ellipsized. A row exposes its full path, textual status, addition/deletion counts, expanded/collapsed state, and button role in one coherent description. Status is never color-only. Loading and failure transitions are politely announced; headings identify the Changes surface and the expanded patch. Patch content remains selectable plain text but is not treated as executable markup or one mutation action.

## Security and Workspace Constraints

- The tab-owned `WorkspaceClient` is the sole routing authority.
- Workspace replacement or server-generation invalidation cancels/invalidate in-flight status and diff results.
- Server paths and patch text are untrusted display data. They are not executed, resolved locally, opened as external URIs, or interpolated into a command.
- Patch rendering is in-app plain text with bounded memory; ANSI/control sequences do not become actions or styled executable content.
- There is no shell Git fallback and no fallback to `/file/status`, session diffs, `FileViewer` content, filesystem traversal, or guessed directories.
- The feature declares and invokes no VCS mutation API. It does not stage, unstage, apply, discard, restore, commit, checkout, or write files.

## Exclusions

This change does not add staging, commit, discard, restore, patch application, conflict resolution, branch switching, branch comparison UI, inline commenting, editing from a Changes row, background polling, push/pull/fetch, shell commands, or an external diff viewer. It does not reinterpret session diffs as workspace state and does not modify existing Files editing/upload behavior.

## Impact

- **Network:** add bounded raw GET declarations for VCS status/diff (and bounded VCS identity loading), with explicit workspace query arguments.
- **Domain/data:** add strict VCS DTO decoding, typed status/diff mode, immutable review models, checked aggregate statistics, exact-path matching, and explicit failure mapping.
- **Presentation:** add a workspace-keyed Changes state holder with cancellation/generation ownership, refresh, one-selection expansion, and bounded diff caching.
- **UI/navigation:** add the contextual Files action and read-only Changes content while preserving normal Files context on exit.
- **Tests:** add API, decoder, domain, ViewModel, and Compose coverage for routing, ordering, bounds, state transitions, accessibility semantics, and the absence of mutation paths.
- **Compatibility:** older servers remain usable for all existing features; entering Changes shows an explicit unsupported state rather than silently substituting incomplete data.
