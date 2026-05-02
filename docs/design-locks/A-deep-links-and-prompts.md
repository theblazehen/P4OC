# Lock A — Deep Links + Permission/Question Modality

**Ticket:** `oa-7ysx`
**Status:** Locked

## Decision

1. Old `chat/{sessionId}?directory={directory}` deep links route to an explicit error screen `Screen.LegacyLinkRetired`. **No best-effort migration.** Constructing `Workspace(activeServer, oldDirectory)` from a URL is forbidden.
2. Permission and question dialogs are **per-tab modal**. There is no app-global queue and no app-modal interrupt.
3. Cross-tab perm/question events for tab A while user is on tab B are **enqueued on tab A**, surfaced as a tab-bar dot indicator, and shown only when tab A becomes active.
4. Closing the owning tab with prompts pending → best-effort `respondToPermission(id, "reject", workspace.directory)` / `respondToQuestion` are POSTed, then the queue is dropped.
5. Disconnect mid-prompt → flush all queues, snackbar `"Prompt cancelled — disconnected"`. No reply attempt after disconnect.
6. Workspace switch within a tab → tear down old VM (auto-rejects pending), new VM has empty queue.
7. Orphan event (perm for unknown session) → buffer 5 s in `ServerEventGateway` keyed by `sessionID`. If `SessionRepository` resolves owner within window, replay. Else drop with warn log.
8. `DialogQueueManager` ownership moves from `ChatViewModel` to `WorkspaceViewModel`.

## Rejected alternative

**App-modal global dialog queue.** Recreates ambient context: prompt's "answer" call needs `workspace.directory`, so a global queue would itself need to carry workspace identity. Per-tab is mechanically simpler and matches the workspace-isolation model.

## Worked examples

| Scenario | Behavior |
|---|---|
| Old deep link from notification (`chat/abc?directory=%2Ftmp%2Fproj`) | Routes to `LegacyLinkRetired`. Message: "This shortcut was created with an older app version. Open the session from the Sessions list." No REST lookup. No tab created. |
| Perm event for background tab while user is in different workspace | Enqueued on owning tab; tab-bar dot. Active tab is not interrupted. |
| User closes tab with pending perm | `WorkspaceViewModel.onCleared()` POSTs `respondToPermission(id, "reject", workspace.directory)` then disposes queue. |
| Two tabs both have prompts queued; user answers one | Queues are per-tab and independent. No cross-tab interaction. |
| Server disconnects with prompt visible | Modal closes, snackbar shown. No reply attempted. Reconnect does not restore prompts (server reissues if still active). |
| Workspace switch within tab while prompt pending | Old VM auto-rejects pending; new VM has empty queue. |
| Permission for unknown session (race) | Buffered 5 s. If `SessionRepository` resolves owning workspace within window, replay. Else dropped with warn log. |

## Files affected

- `app/src/main/java/dev/blazelight/p4oc/ui/navigation/Screen.kt` — remove `?directory=` from chat route; add `Screen.LegacyLinkRetired`.
- `app/src/main/AndroidManifest.xml` — confirmed no existing `NavDeepLink` for `chat/...`. Sweep to confirm.
- `app/src/main/java/dev/blazelight/p4oc/MainActivity.kt` — legacy intent handler routes to `LegacyLinkRetired`.
- `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/DialogQueueManager.kt` — workspace-scoped; add `flushAllOnDisconnect()` and `rejectAllAndDispose(api, workspace)`.
- `app/src/main/java/dev/blazelight/p4oc/ui/workspace/WorkspaceViewModel.kt` (new in commit 2) — owns `DialogQueueManager`; `onCleared()` calls `rejectAllAndDispose`.
- `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/ChatViewModel.kt` — delete `dialogManager` field and the `enqueuePermission`/`enqueueQuestion` branches in `handleEvent`.
- `app/src/main/java/dev/blazelight/p4oc/data/server/ServerEventGateway.kt` (new) — adds 5 s orphan-event buffer for permission/question.
- `app/src/main/java/dev/blazelight/p4oc/data/workspace/WorkspaceClient.kt` — adds permission/question response methods bound to immutable workspace.

## Test cases (oa-ja73)

- `legacyDeepLinkRoutes_toRetiredScreen`: input `chat/abc?directory=%2Ftmp` → `NavController.currentRoute == "legacy_retired?reason=…"`.
- `permissionForOtherTab_doesNotInterruptActiveTab`: tab A active, perm event for sessionID owned by tab B → tab A `pendingPermission == null`, tab B has it, tab-bar dot on B.
- `closingTabWithPendingPrompt_postsRejectReply`: enqueue perm, call `viewModel.onCleared()` → `MockApi.respondToPermission(id, reject, workspace.directory)` invoked once.
- `disconnectMidPrompt_clearsAndShowsSnackbar`: enqueue prompt, emit `Disconnected` → `pendingPermission == null`, snackbar event.
- `workspaceSwitchInTab_rejectsStalePrompt`: tab has prompt for W1, switch to W2 → `respondToPermission(id, reject, W1.directory)` posted; new dialogManager empty.
- `orphanPermissionEventBuffered_replaysWithinWindow`: perm for unknown session, hydrate within 2 s → enqueued correctly. Same with 6 s wait → dropped, warn logged.
- `twoTabsAnswerIndependently`: queue perm in A and B, answer A → B unchanged, only one API call posted.
