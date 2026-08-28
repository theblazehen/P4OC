# Implementation Tasks

## 1. API transport and bounded decoding

- [x] 1.1 Add raw streaming `OpenCodeApi` GET declarations for `/vcs`, `/vcs/status`, and `/vcs/diff`; require explicit `directory` and `workspace` query arguments, and require `mode` plus `context` for diff.
- [x] 1.2 Add a closed `VcsDiffMode` representation whose only serialized values are `git` and `branch`; make the Changes caller use `git` and context `3`.
- [x] 1.3 Reuse/extract the cancellable bounded-response reader so it rejects known and streamed overflow and always closes success/error bodies.
- [x] 1.4 Enforce raw caps of 64 KiB for VCS info, 2 MiB for status, and 12 MiB for diff before JSON decoding.
- [x] 1.5 Add strict transport DTOs for VCS info, status entries, and diff entries, retaining upstream names (`default_branch`, `file`, `patch`, `status`, `additions`, `deletions`).
- [x] 1.6 Expose the reads only through the tab-owned `WorkspaceClient`, forwarding its captured directory (including explicit null) and the existing explicit `workspace = null`; add no global/default/active-tab overload.

## 2. Domain and repository

- [x] 2.1 Add immutable domain models for workspace identity display, branch/default branch, changed path, typed status, checked addition/deletion totals, and bounded patch result.
- [x] 2.2 Add a read-only workspace-changes repository/adapter constructed from `WorkspaceClient`; do not add mutation methods or depend on `FileRepository` write capabilities.
- [x] 2.3 Validate top-level shapes, required field types, nonblank unique paths, the three allowed statuses, integral nonnegative counts, and numeric range before publishing data.
- [x] 2.4 Preserve status response order exactly and reject duplicate paths; do not apply directory-browser sorting or filter by the currently open folder.
- [x] 2.5 Enforce at most 10,000 status/diff entries, 4 KiB per UTF-8 path, and 512 B per branch/default-branch value.
- [x] 2.6 Calculate file/addition/deletion aggregates from the validated status snapshot with checked `Long` addition.
- [x] 2.7 Enforce 1 MiB per retained UTF-8 patch and 8 MiB aggregate decoded patch bytes. Represent a per-file violation locally; reject the whole diff snapshot on raw or aggregate overflow.
- [x] 2.8 Index diff entries by exact workspace-relative `file`; model missing optional patch as unavailable and a selected status path absent from a newer diff as stale, never as an empty patch.
- [x] 2.9 Map 404/405/501 to unsupported; malformed payload, oversized response, oversized patch, authorization/other HTTP, stale workspace, cancellation, and network failures to distinct non-sensitive domain results.
- [x] 2.10 Confirm the repository has no `/vcs/apply`, stage, discard, commit, file-write, session-diff, `/file/status`, filesystem, or shell fallback.

## 3. Presentation state and lifecycle

- [x] 3.1 Add Changes state keyed by the owning tab's immutable server/directory workspace identity and server generation, separate from normal Files folder/search/edit state.
- [x] 3.2 Model initial loading, content, empty, refreshing, unsupported, malformed/general failure, response-too-large, and retry actions without raw exception/protocol text.
- [x] 3.3 On entry, load bounded VCS identity and status for the same client generation; publish the snapshot atomically only after both are valid.
- [x] 3.4 Expose current server/workspace, current/default branch, changed-file count, aggregate additions/deletions, and ordered rows.
- [x] 3.5 Implement manual refresh that retains the old list while loading, atomically replaces it on success, reports refresh failure without erasing it, and clears selection/diff cache only on successful replacement.
- [x] 3.6 Use jobs plus monotonically increasing load generations so workspace replacement, refresh, exit, or a newer selection cancels/suppresses stale completions.
- [x] 3.7 Implement single selection: selecting a row expands it, selecting it again collapses it, and selecting another atomically collapses the prior row.
- [x] 3.8 Lazily request one bounded `mode=git&context=3` diff snapshot on expansion and reuse it only for the unchanged workspace/status generation/mode/context key.
- [x] 3.9 Keep list content available when patch loading fails or is oversized; expose row-local loading, content, unavailable/stale, too-large, and retryable failure states.
- [x] 3.10 Ensure refresh/exit/workspace replacement releases cached patch strings and that no two patch bodies are exposed for composition at once.

## 4. Files UI and accessibility

- [x] 4.1 Add a contextual, accessible Changes action to Files only; do not add persistent chat/session chrome.
- [x] 4.2 Enter Changes without losing the directory browser's folder, back stack, search/symbol filters, or edit state; Back/exit returns to that exact context before normal Files navigation proceeds.
- [x] 4.3 Render a Changes heading plus non-color-only server/workspace and branch/default-branch identity, preserving full accessible text when visual text is ellipsized.
- [x] 4.4 Render ordered rows for every status item, including deleted and out-of-current-folder paths, with full path, textual status, additions, and deletions.
- [x] 4.5 Render changed-file and aggregate addition/deletion statistics derived from the same status snapshot.
- [x] 4.6 Add in-place one-at-a-time patch expansion/collapse. Render bounded patch text in-app; do not navigate through `FileViewer`, resolve a local path, or launch an external viewer.
- [x] 4.7 Add initial loading, announced refresh, clean-workspace empty, unsupported-server, response-too-large, patch-too-large, stale-patch, human-readable failure, and retry UI.
- [x] 4.8 Give entry/exit/refresh controls stable names and standard touch targets; expose each row as one button with full path, status, stats, and expanded state.
- [x] 4.9 Add polite live-region announcements for load/failure transitions, an expanded-patch heading, and plain/selectable patch semantics that do not interpret ANSI/control text as actions.
- [x] 4.10 Confirm no stage, unstage, apply, discard, restore, commit, checkout, edit, or shell action is present in the Changes mode.

## 5. Automated testing

- [x] 5.1 Add API/`WorkspaceClient` tests proving `/vcs/status` and `/vcs/diff?mode=git|branch&context=N` use the captured tab directory and cannot omit/replace it through an active/global fallback.
- [x] 5.2 Test bounded readers for over-limit `Content-Length`, unknown/understated streamed overflow, exact-limit success, cancellation, and body closure for each route class.
- [x] 5.3 Test valid structured decoding for added/modified/deleted files, optional patch/status, current/default branch, Unicode paths, and integer statistics.
- [x] 5.4 Test malformed JSON, wrong top-level shape, missing/wrong fields, blank/duplicate/oversized paths, unknown status, invalid counts, and oversized branch values.
- [x] 5.5 Test exact preservation of server order and inclusion of deleted and out-of-folder paths without basename matching or current-folder filtering.
- [x] 5.6 Test 10,000-entry boundaries, raw response caps, 1 MiB per-patch behavior, 8 MiB aggregate-patch rejection, and checked aggregate statistics.
- [x] 5.7 Test unsupported 404/405/501, authorization/other HTTP, network, stale-workspace, malformed, aggregate-oversize, per-patch-oversize, absent-patch, and stale-selected-path mappings.
- [x] 5.8 Add ViewModel tests for entry, atomic identity/status load, empty/content states, refresh retain/replace/fail behavior, generation cancellation, retry, and cache invalidation.
- [x] 5.9 Add ViewModel tests proving expansion/collapse is one-at-a-time, diff is lazy and `git/context=3`, valid cache reuse is bounded to one status generation, and list state survives patch failure.
- [x] 5.10 Add Compose UI tests for contextual entry/exit, workspace/branch/stats, ordered changed/deleted rows, out-of-folder paths, expansion/collapse, loading, empty, refresh, unsupported, oversize, and concise failure/retry.
- [x] 5.11 Add semantics assertions for control names, row role/description/expanded state, non-color status text, live-region state, and patch heading.
- [x] 5.12 Add an architectural test or equivalent inspection proving Changes code declares/calls only GET VCS reads and contains no mutation endpoint or shell/file/session-diff fallback.

## 6. Verification

- [x] 6.1 Run the focused API, repository/decoder, ViewModel, and Changes Compose tests added above.
- [x] 6.2 Run `./gradlew :app:compileDebugKotlin`.
- [x] 6.3 Run `./gradlew :app:detekt`.
- [x] 6.4 Run `./gradlew :app:testDebugUnitTest`.
- [x] 6.5 With `ANDROID_SERIAL=R58X70XHB9P`, run the focused connected UI tests on the Samsung SM-A155F; never rely on implicit ADB device selection.
- [x] 6.6 On that explicit device, smoke-test Files → Changes → refresh → expand first row → expand second row → collapse → Back, using a workspace containing modified, added, deleted, and out-of-current-folder paths.
- [x] 6.7 Verify unsupported, network retry, empty, aggregate-too-large, and per-patch-too-large fixtures show human-readable states without raw payloads.
- [x] 6.8 Verify no Changes interaction mutates the working tree and no request other than the specified GET VCS routes is emitted during entry, refresh, expansion, collapse, or exit.
