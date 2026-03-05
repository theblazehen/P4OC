---
id: oa-6qcn
status: closed
deps: []
links: []
created: 2026-02-24T16:15:00Z
type: bug
priority: 0
assignee:
---
# Subagent permission/question events silently dropped

## Problem

When a subagent (task tool) runs and needs permission (e.g., bash execute, file edit) or asks a question, the `permission.asked` / `question.asked` SSE events carry the **subagent's session ID**, not the parent session's. `ChatViewModel.handleEvent()` filters all events with strict `== sessionId` matching against the parent session ID, so subagent permissions and questions are silently dropped. The subagent hangs indefinitely waiting for a reply the user never sees.

## Root Cause

`ChatViewModel.kt` lines 222-231:

```kotlin
is OpenCodeEvent.PermissionRequested -> {
    if (event.permission.sessionID == sessionId) {   // ← only matches parent
        dialogManager.enqueuePermission(event.permission)
    }
}
is OpenCodeEvent.QuestionAsked -> {
    if (event.request.sessionID == sessionId) {      // ← only matches parent
        dialogManager.enqueueQuestion(event.request)
    }
}
```

`sessionId` is the parent session from nav args. Subagent events have a different session ID. No child-session tracking exists.

Same issue in `PermissionReplied` handler (line 280-283) — if a permission is answered from another client, the cleanup won't fire for subagent permissions.

## Secondary Bug: `clearPermissionByRequestId` incomplete

`DialogQueueManager.clearPermissionByRequestId()` only clears `_pendingPermissionsByCallId` (inline map). It does NOT:
- Clear `_pendingPermission` (modal dialog state)
- Remove from `SavedStateHandle` persistence
- Call `showNextPermission()` to advance the queue

If a permission is replied to externally (via `PermissionReplied` event), the modal dialog stays stuck on screen.

## Proposed Fix

### 1. Track child session IDs in ChatViewModel

```kotlin
private val childSessionIds = mutableSetOf<String>()

private fun isOwnedSession(eventSessionId: String): Boolean =
    eventSessionId == sessionId || eventSessionId in childSessionIds
```

### 2. Listen for SessionCreated events with parentID

```kotlin
is OpenCodeEvent.SessionCreated -> {
    if (event.session.parentID == sessionId) {
        childSessionIds.add(event.session.id)
    }
}
```

### 3. Replace `== sessionId` with `isOwnedSession()` for:
- `PermissionRequested` (line 222)
- `QuestionAsked` (line 227)
- `PermissionReplied` (line 280)

### 4. Fix `clearPermissionByRequestId`

```kotlin
fun clearPermissionByRequestId(requestId: String) {
    _pendingPermissionsByCallId.update { map ->
        map.filterValues { it.id != requestId }
    }
    if (_pendingPermission.value?.id == requestId) {
        _pendingPermission.value = null
        savedStateHandle.remove<String>(KEY_PENDING_PERMISSION)
        showNextPermission()
    }
}
```

## Key Files
- `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/ChatViewModel.kt` — event filtering (lines 222-283)
- `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/DialogQueueManager.kt` — clearPermissionByRequestId (line 119)
- `app/src/main/java/dev/blazelight/p4oc/domain/model/Session.kt` — `parentID` field (line 49)
- `app/src/main/java/dev/blazelight/p4oc/domain/model/Event.kt` — SessionCreated event (line 10)

## Infrastructure already present
- `Session.parentID: String? = null` — parent-child hierarchy exists in the model
- `OpenCodeEvent.SessionCreated` — fires when subagent sessions are created
- Permission reply API (`POST permission/{requestId}/reply`) is session-agnostic — reply side works fine, display side is the blocker

## Acceptance Criteria
- [ ] Subagent permission prompts appear in parent session UI
- [ ] Subagent question prompts appear in parent session UI
- [ ] Responding to subagent permissions works (already session-agnostic)
- [ ] `clearPermissionByRequestId` properly clears modal + queue
- [ ] Update `ChatViewModelTest` — the test at line 126-136 intentionally asserts the broken behavior
- [ ] `./gradlew :app:compileDebugKotlin` passes
