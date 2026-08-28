# Workspace Changes Specification

## ADDED Requirements

### Requirement: Contextual read-only Changes mode

The application SHALL provide a Changes mode from Files for reviewing the owning tab's workspace working tree. The entry SHALL be contextual to Files, SHALL NOT occupy persistent chat/session chrome, and SHALL restore the prior Files context on exit.

#### Scenario: Enter and exit from Files

- **GIVEN** Files is showing folder `src/main` with its folder back stack and filters
- **WHEN** the user activates the Changes action
- **THEN** Files SHALL show the workspace Changes mode without resetting that browser context
- **AND** Back or the explicit exit action SHALL leave Changes before performing normal folder navigation
- **AND** the prior folder, folder back stack, search/symbol filters, and edit state SHALL be restored unchanged

#### Scenario: Changes is not a chat action

- **GIVEN** the user is viewing chat or a session timeline
- **WHEN** no Files surface is active
- **THEN** the application SHALL NOT reserve persistent Changes chrome on that surface

### Requirement: Tab-owned workspace identity and routing

Every Changes request SHALL use only the `WorkspaceClient` owned by the current tab scope. The routing identity SHALL be the client's immutable server reference, server generation, and `Workspace.directory`. The caller SHALL pass the captured directory argument explicitly, including intentional `null`, and the existing explicit `workspace = null` value. It SHALL NOT derive routing from active-tab state, the browser's current folder, a session, settings, a global/default workspace helper, or a fallback chain.

#### Scenario: Directory workspace is routed exactly

- **GIVEN** tab A owns a `WorkspaceClient` for server A and directory `/work/alpha`
- **AND** another active or recently used tab points at `/work/beta`
- **WHEN** tab A loads Changes status or a patch
- **THEN** the request SHALL use tab A's server connection and `directory=/work/alpha`
- **AND** it SHALL NOT inspect or use `/work/beta`

#### Scenario: Intentionally global workspace remains explicit

- **GIVEN** a tab-owned `WorkspaceClient` whose `Workspace.directory` is `null`
- **WHEN** Changes issues a VCS read
- **THEN** the client SHALL forward the intentional null directory through its explicit API parameter contract
- **AND** it SHALL NOT substitute a session directory, current folder, last project, or default directory

#### Scenario: Workspace changes during a load

- **GIVEN** status or diff is loading for one workspace/server generation
- **WHEN** the tab's workspace is replaced or its server generation becomes stale
- **THEN** the old load SHALL be cancelled or its completion SHALL be ignored
- **AND** no old-workspace row or patch SHALL appear in the replacement workspace

### Requirement: Exact workspace VCS read contract

The API layer SHALL expose bounded workspace-routed GET reads for `GET /vcs`, `GET /vcs/status`, and `GET /vcs/diff`. Diff mode SHALL be a closed value with exactly `git` and `branch`; diff context SHALL be an explicit nonnegative integer. The Changes presentation SHALL request the current working tree with `mode=git` and `context=3`.

The accepted structured shapes SHALL be:

- VCS info: `{ branch?: string, default_branch?: string }`.
- Status item: `{ file: string, status: "added"|"modified"|"deleted", additions: integer, deletions: integer }`.
- Diff item: `{ file: string, patch?: string, status?: "added"|"modified"|"deleted", additions: integer, deletions: integer }`.

#### Scenario: Working-tree diff request is exact

- **GIVEN** the user expands a changed row and no valid diff snapshot is cached
- **WHEN** the patch load starts
- **THEN** the client SHALL issue `GET /vcs/diff` with `mode=git` and `context=3`
- **AND** it SHALL include the same explicit workspace routing arguments used by VCS info and status

#### Scenario: Diff modes cannot be arbitrary

- **GIVEN** code constructs a workspace VCS diff request
- **WHEN** it chooses a comparison mode
- **THEN** the API/domain contract SHALL permit only `git` or `branch`
- **AND** the Changes UI SHALL expose no branch-comparison selector under this specification

### Requirement: Visible workspace and branch identity

The Changes header SHALL show enough identity to distinguish the reviewed target: server display identity, full workspace directory or an explicit global-workspace label, current branch, and default branch when supplied. If either branch value is absent, the UI SHALL state that it is unavailable rather than guessing. Visual ellipsis SHALL NOT remove the full identity from accessibility semantics.

#### Scenario: Branch identity is available

- **GIVEN** `GET /vcs` returns branch `feature/changes` and default branch `main`
- **WHEN** Changes content is shown
- **THEN** the header SHALL identify the owning server and workspace directory
- **AND** it SHALL show `feature/changes` and `main` as current and default branch respectively

#### Scenario: Branch identity is absent

- **GIVEN** valid VCS info omits branch and default branch
- **WHEN** Changes content is shown
- **THEN** the UI SHALL use an explicit branch-unavailable label
- **AND** it SHALL NOT reuse a branch from another tab, session, cached workspace, or local Git command

### Requirement: Complete structured workspace status

On entry and refresh, the application SHALL load VCS info and the full `GET /vcs/status` array for the tab workspace. It SHALL publish content only after both responses form one valid load generation. Each row SHALL display the exact workspace-relative path, textual status, additions, and deletions. Summary counts SHALL be checked sums over that same status snapshot.

#### Scenario: Show status and aggregate statistics

- **GIVEN** status returns a modified row with 5 additions and 2 deletions and an added row with 3 additions and 0 deletions
- **WHEN** the snapshot is displayed
- **THEN** Changes SHALL show two changed files, 8 additions, and 2 deletions
- **AND** each row SHALL retain its own server-provided status and counts

#### Scenario: Clean workspace

- **GIVEN** VCS info is valid and status returns an empty array
- **WHEN** initial loading completes
- **THEN** Changes SHALL show a clean-workspace empty state
- **AND** it SHALL offer refresh without displaying a fabricated row or session diff

#### Scenario: Invalid statistics

- **GIVEN** a status item contains a negative, fractional, non-finite, wrongly typed, or out-of-range addition/deletion count
- **WHEN** the response is decoded
- **THEN** the entire status snapshot SHALL be rejected as malformed
- **AND** no partial totals or rows SHALL be published

### Requirement: Deterministic server ordering

The application SHALL preserve the exact array order returned by `GET /vcs/status`. It SHALL NOT sort by path, path case, locale, status, folder/file type, or the directory browser's display rules. Duplicate status paths SHALL make the snapshot malformed rather than producing ambiguous rows.

#### Scenario: Preserve a non-alphabetical response

- **GIVEN** the server returns `z.kt`, `A.kt`, `deleted.txt` in that order
- **WHEN** Changes renders the list
- **THEN** the rows SHALL appear as `z.kt`, `A.kt`, `deleted.txt`
- **AND** device locale SHALL NOT alter the order

#### Scenario: Reject duplicate paths

- **GIVEN** two status items contain the exact same `file` value
- **WHEN** status decoding completes
- **THEN** the status snapshot SHALL be rejected as malformed
- **AND** the application SHALL NOT merge the rows or choose one arbitrarily

### Requirement: Deleted and out-of-current-folder paths

Changes SHALL include every valid status item regardless of the Files browser's current folder. Deleted paths SHALL remain visible and reviewable through VCS diff data without reading file content. Paths SHALL be treated as untrusted workspace-relative display identifiers, not local filesystem paths or URIs.

#### Scenario: Changed path is outside the open folder

- **GIVEN** Files entered Changes from `src/main`
- **AND** status includes `README.md` and `src/test/AppTest.kt`
- **WHEN** Changes displays the status snapshot
- **THEN** both paths SHALL be shown
- **AND** neither SHALL be filtered or rewritten relative to `src/main`

#### Scenario: Deleted path is reviewed

- **GIVEN** status contains `{ file: "old/config.json", status: "deleted" }`
- **WHEN** the user expands that row
- **THEN** the application SHALL obtain the patch from the VCS diff response
- **AND** it SHALL NOT call file-content read, require the deleted file to exist, or hide the row

#### Scenario: Path resembles an external URI or command

- **GIVEN** the server returns a path containing URI punctuation, shell metacharacters, or control characters
- **WHEN** the row is displayed or selected
- **THEN** the value SHALL remain inert display/match data
- **AND** it SHALL NOT be launched, executed, interpolated into a command, or resolved against the Android filesystem

### Requirement: One-at-a-time in-app patch expansion

Status SHALL load without loading patches. The first expansion for a status generation SHALL lazily load a bounded diff snapshot and index entries by exact `file`. Exactly one row MAY be selected/expanded. Patch rendering SHALL remain in-app and read-only.

A diff snapshot MAY be reused only while workspace identity, server generation, status generation, mode, and context remain unchanged. Refresh or workspace replacement SHALL invalidate it. Missing optional `patch` SHALL be represented as unavailable; it SHALL NOT be replaced with an empty string that implies no changes.

#### Scenario: Expand one row

- **GIVEN** Changes shows collapsed rows `a.kt` and `b.kt`
- **WHEN** the user selects `a.kt`
- **THEN** `a.kt` SHALL enter a row-local loading state until its exact-path patch result is known
- **AND** at most the `a.kt` patch SHALL be composed and visible

#### Scenario: Select another row

- **GIVEN** `a.kt` is expanded
- **WHEN** the user selects `b.kt`
- **THEN** `a.kt` SHALL collapse atomically with selecting `b.kt`
- **AND** the UI SHALL never expose both patch bodies at once

#### Scenario: Collapse selected row

- **GIVEN** `a.kt` is expanded
- **WHEN** the user selects `a.kt` again
- **THEN** the row SHALL collapse
- **AND** collapsing SHALL NOT initiate a network request or mutation

#### Scenario: Exact path match only

- **GIVEN** status contains `src/a.kt` and the diff contains `test/a.kt` but no `src/a.kt`
- **WHEN** the user expands `src/a.kt`
- **THEN** the UI SHALL report that the patch is no longer available and suggest refresh
- **AND** it SHALL NOT use `test/a.kt` based on basename matching

#### Scenario: Patch field is absent

- **GIVEN** a valid diff entry for the selected path omits optional `patch`
- **WHEN** expansion resolves
- **THEN** the row SHALL show a concise Patch unavailable state
- **AND** it SHALL NOT fabricate an empty patch, call another diff source, or open the file viewer

### Requirement: Aggregate and per-patch bounds

The implementation SHALL enforce these UTF-8 byte and count limits:

| Boundary | Maximum |
| --- | ---: |
| VCS info raw response | 64 KiB |
| status raw response | 2 MiB |
| diff raw response | 12 MiB |
| status entries | 10,000 |
| diff entries | 10,000 |
| one `file` value | 4 KiB |
| branch or default-branch value | 512 B |
| one retained patch | 1 MiB |
| aggregate decoded patch strings | 8 MiB |

The response reader SHALL reject a declared oversize before reading and SHALL read an unknown or understated body only through maximum plus one byte before closing and rejecting it. `context` SHALL be fixed at 3 for this view. An individual patch over 1 MiB SHALL be discarded and represented only by a row-local too-large marker. A raw diff over 12 MiB or decoded aggregate patches over 8 MiB SHALL reject the diff snapshot while retaining the valid status list.

#### Scenario: Declared response exceeds its route cap

- **GIVEN** a VCS response declares `Content-Length` greater than the applicable raw-response maximum
- **WHEN** the client receives headers
- **THEN** it SHALL reject the body before general JSON decoding
- **AND** it SHALL close the response body

#### Scenario: Unknown or understated response crosses its cap

- **GIVEN** a response has unknown or understated length
- **WHEN** streaming reaches the applicable maximum plus one byte
- **THEN** the client SHALL stop reading, close the body, and return a response-too-large failure
- **AND** it SHALL NOT retain or decode the oversized body

#### Scenario: Entry count or metadata exceeds its cap

- **GIVEN** status/diff has more than 10,000 entries, a path exceeds 4 KiB, or a branch value exceeds 512 B
- **WHEN** structured validation runs
- **THEN** the snapshot SHALL be rejected as too large
- **AND** no truncated list, path, or branch SHALL be shown as complete

#### Scenario: One patch is too large

- **GIVEN** the raw diff and aggregate patch data are within their limits
- **AND** one decoded patch exceeds 1 MiB
- **WHEN** the user expands that file
- **THEN** the row SHALL show Patch too large to display
- **AND** the oversized patch string SHALL not be retained in presentation state
- **AND** other within-limit patches SHALL remain reviewable

#### Scenario: Aggregate patches are too large

- **GIVEN** the raw diff body is within 12 MiB but decoded patch strings total more than 8 MiB
- **WHEN** diff validation completes
- **THEN** the diff snapshot SHALL be rejected as too large
- **AND** the status list SHALL remain visible
- **AND** the UI SHALL NOT present a partial diff snapshot as complete

### Requirement: Refresh and asynchronous result ownership

Initial load, refresh, and patch load SHALL use cancellable jobs and monotonically increasing generations. Only the current operation for the same workspace/status key may update presentation state. Manual refresh SHALL keep valid prior status visible while loading, replace it atomically on success, and collapse/invalidate patch state only when the replacement succeeds.

#### Scenario: Successful refresh

- **GIVEN** a valid status snapshot and an expanded patch are visible
- **WHEN** refresh returns a newer valid identity/status snapshot
- **THEN** the list and aggregates SHALL be replaced atomically
- **AND** the expanded row and cached diff SHALL be cleared

#### Scenario: Failed refresh retains reviewed snapshot

- **GIVEN** a valid status snapshot is visible
- **WHEN** manual refresh fails due to network or HTTP error
- **THEN** the prior list and aggregates SHALL remain visible
- **AND** the UI SHALL announce refresh failure and offer retry
- **AND** it SHALL NOT label the retained data as newly refreshed

#### Scenario: Superseded load finishes late

- **GIVEN** load generation 1 is in flight
- **WHEN** generation 2 starts and generation 1 later completes
- **THEN** generation 1 SHALL NOT change list, branch, selection, patch, loading, or error state

### Requirement: Explicit bounded failure states

The application SHALL distinguish unsupported server, malformed data, response too large, patch too large, unavailable/stale patch, authorization/other HTTP failure, stale workspace, and network failure. HTTP 404, 405, or 501 from a required VCS route SHALL map to unsupported server. Initial retryable failure SHALL offer a list-level Retry action; row-local retryable diff failure SHALL offer a row-local retry; unsupported and size states SHALL not automatically retry.

User-facing failures SHALL be concise and human-readable and SHALL NOT include raw response bodies, stack traces, credential-bearing URLs, serialized payloads, or arbitrary server exception text. The system SHALL NOT run an unbounded automatic retry loop.

#### Scenario: Server does not support VCS status

- **GIVEN** `/vcs/status` returns 404, 405, or 501
- **WHEN** initial load maps the failure
- **THEN** Changes SHALL show a server-version unsupported state
- **AND** it SHALL NOT fall back to `/file/status`, session diffs, file content, directory scans, or shell Git

#### Scenario: Malformed status response

- **GIVEN** `/vcs/status` returns invalid JSON, a non-array body, a missing required field, a blank path, duplicate paths, an unknown status, or an invalid count
- **WHEN** decoding or validation fails
- **THEN** no partial status snapshot SHALL be published
- **AND** the UI SHALL say that the server returned changes data the app could not read
- **AND** raw payload content SHALL not be shown

#### Scenario: Network failure can be retried

- **GIVEN** the initial status request fails because the server is unreachable
- **WHEN** Changes displays the failure
- **THEN** it SHALL show a concise connection failure and a Retry action
- **AND** Retry SHALL repeat the same tab-owned workspace operation rather than selecting another workspace

#### Scenario: Patch request fails

- **GIVEN** a valid status list is visible
- **WHEN** diff loading fails due to a retryable network or HTTP error
- **THEN** the failure SHALL remain local to the selected row
- **AND** the status list SHALL remain usable
- **AND** row Retry SHALL retry only the bounded diff operation for the current snapshot key

### Requirement: Accessible non-color-only review

Changes SHALL remain operable and understandable through accessibility services. Entry, exit, and refresh SHALL have stable accessible names and standard touch targets. Each row SHALL expose one button role with full path, textual status, addition/deletion counts, and expanded/collapsed state. Workspace/branch identity SHALL expose full semantic text even when visually ellipsized. Loading and failure changes SHALL use polite live-region announcements. Expanded patch content SHALL have an identifiable heading and remain selectable inert text.

#### Scenario: Screen reader describes a changed row

- **GIVEN** a modified collapsed row `src/App.kt` with 4 additions and 1 deletion
- **WHEN** accessibility services focus the row
- **THEN** they SHALL receive the full path, the word Modified, 4 additions, 1 deletion, button role, and collapsed state
- **AND** color SHALL not be the only status indicator

#### Scenario: Expanded state is announced

- **GIVEN** accessibility focus is on a collapsed row
- **WHEN** the user activates it and patch loading completes
- **THEN** the row SHALL expose expanded state
- **AND** loading and any resulting failure SHALL be politely announced
- **AND** the patch region SHALL have a discoverable heading associated with the selected path

### Requirement: Strictly read-only behavior

Workspace Changes SHALL declare and invoke only VCS GET reads needed for identity, status, and bounded diff. It SHALL provide no stage, unstage, apply, discard, restore, commit, checkout, write, or shell action. Patch/path data SHALL never be executed, used as an external URI, or interpolated into a command. The feature SHALL NOT invoke mutation endpoints, file-write APIs, shell sessions, or local Git processes as a fallback.

#### Scenario: Review all interactions without mutation

- **GIVEN** the user enters Changes, refreshes, expands multiple rows one at a time, collapses a row, retries a failed patch, and exits
- **WHEN** network and repository calls are observed
- **THEN** only the specified VCS GET reads SHALL occur for Changes data
- **AND** workspace files, index, HEAD, and branch state SHALL remain unchanged

#### Scenario: Mutation route exists upstream

- **GIVEN** the connected server advertises a VCS patch-apply or other mutation endpoint
- **WHEN** Changes is constructed or used
- **THEN** the feature SHALL NOT declare, surface, or invoke that endpoint
- **AND** it SHALL NOT display apply, stage, discard, or commit controls

#### Scenario: Read API is unsupported

- **GIVEN** a required VCS GET route is unavailable
- **WHEN** the user enters Changes
- **THEN** the application SHALL show the unsupported state
- **AND** it SHALL NOT execute shell Git, traverse a local filesystem, use `/file/status`, or substitute session/file-viewer diffs
