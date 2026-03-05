---
id: oa-vcaf
status: closed
deps: [oa-ina0]
links: []
created: 2026-02-22T12:00:11Z
type: task
priority: 1
assignee: Jasmin Le Roux
---
# Testing suite: P0 + P1 tests for critical code paths

## Problem

1 test file for 26,800 LOC. Test dependencies (junit, mockk, coroutines-test, espresso, compose-test) are declared but unused. The most critical logic — SSE event handling, message delta accumulation, permission queuing across process death, mappers — has zero coverage.

## What To Do

Write tests in priority order. Each test class should be created after its corresponding component exists (some depend on ChatViewModel decomposition ticket oa-ina0).

### P0 Tests (will catch real bugs)

#### 1. MessageStoreTest
File: `app/src/test/java/dev/blazelight/p4oc/ui/screens/chat/MessageStoreTest.kt`
Test MessageStore directly (no mocks needed — pure state container).
Tests:
- `upsertMessage creates new entry when message not in map`
- `upsertMessage updates existing entry preserving parts`
- `upsertPart creates placeholder message when message not found`
- `upsertPart replaces existing part by id`
- `upsertPart appends new part when id not found`
- `applyDelta appends text to existing Part_Text when delta provided`
- `applyDelta sets isStreaming true during delta accumulation`
- `clearStreamingFlags sets isStreaming false on all text parts`
- `concurrent upsertMessage calls do not corrupt state` (runTest + multiple coroutines)
- `messages flow emits sorted by createdAt`

#### 2. DialogQueueManagerTest
File: `app/src/test/java/dev/blazelight/p4oc/ui/screens/chat/DialogQueueManagerTest.kt`
Use `SavedStateHandle()` constructor directly (accepts initial state map).
Tests:
- `enqueuePermission shows immediately when no current permission`
- `enqueuePermission queues when permission already showing`
- `onPermissionResponded advances to next in queue`
- `onPermissionResponded clears when queue empty`
- `permission persisted to SavedStateHandle for process death`
- `restores pending permission from SavedStateHandle on init`
- `restores permission queue from SavedStateHandle on init`
- `enqueuePermission adds to pendingPermissionsByCallId map`
- `corrupt SavedStateHandle data does not crash`

#### 3. ChatViewModelTest (coordinator integration)
File: `app/src/test/java/dev/blazelight/p4oc/ui/screens/chat/ChatViewModelTest.kt`
Mock `ConnectionManager`, use real or mock mappers, `SavedStateHandle(mapOf("session_id" to "test"))`.
Tests:
- `handleEvent routes MessageUpdated to MessageStore`
- `handleEvent routes PermissionRequested to DialogQueueManager`
- `handleEvent filters events by sessionId`
- `SessionStatusChanged busy sets isBusy true`
- `SessionStatusChanged idle clears streaming flags`
- `sendMessage clears input and sets isSending`
- `sendMessage restores input on API error`
- `queueMessage stores message and clears input`
- `abortSession clears streaming flags and busy state`

### P1 Tests (should have)

#### 4. EventMapperTest
File: `app/src/test/java/dev/blazelight/p4oc/data/remote/mapper/EventMapperTest.kt`
Use real Json, real sub-mappers.
Tests:
- `maps message_updated event correctly`
- `maps message_part_updated with delta`
- `maps permission_asked with tool callID`
- `maps session_status idle/busy/retry`
- `maps unknown event type returns null`
- `maps malformed event data returns null without crash`

#### 5. MapperTests (Session, Part, Message)
File: `app/src/test/java/dev/blazelight/p4oc/data/remote/mapper/MapperTests.kt`
Tests:
- SessionMapper: maps SessionDto with/without optional fields, status types
- PartMapper: maps text/tool/file/patch parts, unknown type fallback
- MessageMapper: maps user/assistant messages with tokens

#### 6. ModelAgentManagerTest
File: `app/src/test/java/dev/blazelight/p4oc/ui/screens/chat/ModelAgentManagerTest.kt`
Mock ConnectionManager.getApi().
Tests:
- `loadAgents filters to primary non-hidden agents`
- `loadModels selects last used model when available`
- `selectModel adds to recent models`
- `handles API error gracefully`

### P2 Tests (nice to have)
#### 7. FilePickerManagerTest — attach/detach/clear/load

## Dependencies
- Tests 1-2 depend on ChatViewModel decomposition (ticket oa-ina0)
- Tests 4-5 can be written immediately (no decomposition needed)
- Test 3 depends on full decomposition being done

## Acceptance Criteria
- [ ] All P0 tests written and passing
- [ ] All P1 tests written and passing
- [ ] P2 tests written if time allows
- [ ] `./gradlew test` passes
- [ ] Tests use coroutines-test properly (runTest, TestDispatcher)

## Acceptance Criteria

P0+P1 tests written and passing. Test coverage on critical paths. gradlew test passes.

