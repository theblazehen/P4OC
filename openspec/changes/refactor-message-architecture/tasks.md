# Tasks: Message Architecture Refactor

## Implementation Checklist

### Phase 1: Core Data Structure Change
- [x] **1.1** Replace `_messages: SnapshotStateList` with `_messagesMap: MutableStateMap<String, MessageWithParts>` in ChatViewModel
- [x] **1.2** Add derived `messages` property that returns sorted list from map values
- [x] **1.3** Remove `messagesLock` and all `synchronized` blocks (no longer needed)
- [x] **1.4** Remove helper methods: `addMessageIfAbsent`, `replaceTempMessageOrAddIfAbsent`

### Phase 2: Refactor Event Handlers
- [x] **2.1** Create `upsertMessage(message: Message)` function with proper UPSERT logic
- [x] **2.2** Create `upsertPart(part: Part, delta: String?)` function with UPSERT logic
- [x] **2.3** Create `createPlaceholderMessage(messageId, sessionId)` helper for when parts arrive before message
- [x] **2.4** Create `applyDelta(existing: Part, incoming: Part, delta: String?)` helper for streaming text
- [x] **2.5** Refactor `handleEvent()` to use new upsert functions
- [x] **2.6** Update `updateMessage()` to call `upsertMessage()`
- [x] **2.7** Update `updatePart()` to call `upsertPart()`

### Phase 3: Refactor Message Loading
- [x] **3.1** Refactor `loadMessages()` to populate map directly (no clear/addAll)
- [x] **3.2** Remove `loadRemainingMessages()` background loading (simplify to single load)
- [x] **3.3** Ensure initial load doesn't conflict with SSE events

### Phase 4: Refactor Send Message
- [x] **4.1** Remove optimistic user message creation from `sendMessage()`
- [x] **4.2** Remove API response handling that adds message to list
- [x] **4.3** Make `sendMessage()` fire-and-forget (only set isSending, clear input)
- [x] **4.4** Rely on `session.status` SSE event to clear isSending/isBusy flags

### Phase 5: Validation
- [x] **5.1** Build and install APK
- [x] **5.2** Test: Send message, verify user message appears via SSE
- [x] **5.3** Test: Verify assistant response streams in (text appears incrementally)
- [x] **5.4** Test: Send multiple rapid messages, verify no crashes
- [x] **5.5** Test: Navigate away and back to chat, verify messages persist
- [x] **5.6** Test: Initial load of existing session with messages

### Phase 6: Cleanup
- [x] **6.1** Remove any dead code from refactor
- [x] **6.2** Update any comments if needed
- [x] **6.3** Verify no compiler warnings (only deprecation warning for Icons.Filled.Chat)

### Phase 7: Fix Question Reply Endpoint (Added Jan 24)
- [x] **7.1** Fix `OpenCodeApi.kt`: Change endpoint from `POST session/{sessionId}/questions/{questionId}` to `POST question/{requestId}/reply`
- [x] **7.2** Update `ChatViewModel.respondToQuestion()`: Remove sessionId parameter, use requestId only
- [x] **7.3** Build and deploy fix

## Completion Status

**All phases completed.** The message architecture refactor is done and the question reply endpoint has been fixed.

## Key Changes Summary

1. **Message Storage**: Changed from `SnapshotStateList` to `SnapshotStateMap` keyed by message ID
2. **SSE as Source of Truth**: Removed optimistic updates; all messages come via SSE events
3. **No Duplicate Keys**: Map-based storage guarantees uniqueness, eliminating LazyColumn crashes
4. **Question Endpoint**: Fixed to use correct `/question/{requestId}/reply` endpoint per OpenAPI spec

## Verification Commands
```bash
# Build
./gradlew assembleDebug

# Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Watch logs
adb logcat -s ChatViewModel:D OpenCodeEventSource:D AndroidRuntime:E

# Watch for crashes
adb logcat -d -s AndroidRuntime:E | grep -A 5 "FATAL EXCEPTION"
```

## Rollback Plan
If issues arise, the changes are isolated to ChatViewModel and OpenCodeApi. Git revert or checkout of the original files will restore functionality.
