# Workspace Cutover Verification Runbook

This runbook is the release gate for the workspace/session cutover. Mark each item with pass/fail evidence before release.

## Automated Gates

### Compile

Command:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk && ./gradlew :app:compileDebugKotlin
```

Pass: command exits successfully with `BUILD SUCCESSFUL`.

Evidence: PASS, final run completed with `BUILD SUCCESSFUL`.

### Unit Tests

Command:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk && ./gradlew :app:testDebugUnitTest
```

Pass: command exits successfully with `BUILD SUCCESSFUL`.

Evidence: PASS, final run completed with `BUILD SUCCESSFUL`.

### Debug APK

Command:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk && ./gradlew :app:assembleDebug
```

Pass: command exits successfully with `BUILD SUCCESSFUL` and produces a debug APK.

Evidence: PASS, final run completed with `BUILD SUCCESSFUL`.

## Manual Hotspots

### Two-Tab Workspace Isolation

Setup: connect to a server with at least two projects. Open Tab A in project A and Tab B in project B.

Pass criteria: session lists, chat sends, files, diffs, and model/agent actions in Tab A use project A only; Tab B uses project B only. Switching projects in one tab does not change the other tab.

Evidence: screenshot or recording showing both tabs with different project/workspace context after refresh and navigation.

### Session List Hydration

Setup: open sessions after connecting to a server with global sessions and project sessions.

Pass criteria: list loads once without duplicate session rows; project sessions win over duplicate global session IDs; status dots appear after hydrate; refresh preserves workspace context.

Evidence: screenshot of unified session list plus log or screen recording showing refresh.

### Hydrate-Then-Stream Race

Setup: trigger chat streaming while opening/restoring the relevant session list or chat screen.

Pass criteria: streamed SSE message/part events are replayed over the REST snapshot and are not lost or duplicated.

Evidence: recording showing message content present after hydrate completes.

### Chat Event Flow

Setup: send a normal prompt, a tool prompt, and an abort.

Pass criteria: messages stream in order, streaming flags clear on idle, abort summary reflects interrupted state, queued sends preserve FIFO behavior.

Evidence: recording of send, stream, idle, abort, and queued send behavior.

### Optimistic Mutation Failure Under 5xx

Setup: force or mock a 5xx for delete/rename/share-style mutation.

Pass criteria: local optimistic state does not become permanent rollback fiction; repository refetches server snapshot and shows failure feedback.

Evidence: test server log or recording showing failed mutation followed by restored server truth.

### Permission/Question Dialog Cross-Tab Behavior

Setup: open two tabs in different workspaces. Trigger permission/question events in one workspace.

Pass criteria: dialog appears only in the matching session/workspace tab according to design lock A; no broadcast prompt leaks into unrelated tabs.

Evidence: recording showing both tabs while one receives the prompt.

### Deep Links And Stale Routes

Setup: restore or launch a legacy/stale chat route whose workspace cannot be validated against the active server.

Pass criteria: app shows an explicit mismatch/error path and does not silently guess a workspace from old preferences or active global state.

Evidence: screenshot or recording of explicit mismatch/error UI.

### Files And Attachments With Special Characters

Setup: use files whose relative paths contain spaces, unicode, `?`, `#`, dots, and nested directories.

Pass criteria: file explorer opens them correctly; symbol URI navigation preserves the relative path; attachments send `file://` URLs generated from relative workspace paths and round-trip correctly.

Evidence: screenshot/recording showing file open and attached file in composer.

### Server Disconnect And Stale Clients

Setup: open multiple workspace tabs, disconnect the server, reconnect or switch server.

Pass criteria: old workspace-scoped clients/ViewModels are torn down; no zombie SSE/API usage targets the stale server or workspace.

Evidence: logs showing `WorkspaceViewModel.onCleared` for old workspaces and no stale endpoint requests after reconnect.

### Persistence Restore

Setup: open two tabs with different workspaces and active sessions. Force-stop the app and reopen to the same server.

Pass criteria: both tabs restore with the correct workspace and session. Active tab is restored. No old `lastSessionId` or `project_worktree` behavior appears.

Evidence: before/after recording showing force-stop and restored tabs.

### Persistence Server Mismatch

Setup: save tab state on server A, change active server URL to server B, then reopen.

Pass criteria: app shows explicit stale saved-tab/server mismatch feedback and starts fresh. It does not silently resurrect tabs under server B.

Evidence: recording or screenshot of mismatch feedback.

## Forbidden Pattern Scan

Pass criteria: source has no reintroduced forbidden patterns from `AGENTS.md`, except migration literals that intentionally remove old persisted keys.

Evidence: final grep results in ticket notes.
