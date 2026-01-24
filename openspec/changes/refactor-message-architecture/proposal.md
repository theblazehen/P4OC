# Proposal: Refactor Message Architecture

## Change ID
`refactor-message-architecture`

## Summary
Refactor the chat message handling architecture to use SSE events as the single source of truth, replacing the current race-condition-prone list-based approach with a Map-based upsert pattern.

## Problem Statement

### Current Issues
1. **Duplicate Key Crashes**: LazyColumn crashes with `IllegalArgumentException: Key "msg_xxx" was already used` due to duplicate messages in the list
2. **Race Conditions**: Multiple sources add messages concurrently:
   - Optimistic user message (temp ID)
   - API response (real ID)
   - SSE `message.updated` events
   - SSE `message.part.updated` events
3. **Slow Initial Load**: Multiple API calls and list clearing cause UI jank
4. **Synchronized blocks insufficient**: Lock-based synchronization doesn't prevent the fundamental issue of multiple code paths adding the same message

### Root Cause
The app treats both API responses AND SSE events as sources of truth for messages. This creates race conditions where the same message can be added multiple times before the duplicate check runs.

## Proposed Solution

Adopt the pattern used by Openchamber (the reference React implementation):

1. **SSE as Single Source of Truth**: All message updates come through SSE events, not API responses
2. **Map-based Storage**: Use `Map<String, MessageWithParts>` instead of `List<MessageWithParts>` for O(1) upsert operations
3. **No Optimistic UI for Messages**: Don't add temp user message; wait for SSE `message.updated` event
4. **Fire-and-Forget API Calls**: `POST /message` triggers the flow but its response is not used to update UI

## Event Flow (After Refactor)

```
User Types Message
    ↓
POST /session/{id}/message (fire-and-forget)
    ↓
Set isBusy = true, clear input
    ↓
SSE: message.updated (user) → UPSERT to map
    ↓
SSE: message.updated (assistant, empty) → UPSERT to map
    ↓
SSE: message.part.updated (text delta) → UPSERT part to message
    ↓
... more part.updated events ...
    ↓
SSE: message.updated (assistant, completed) → UPSERT to map
    ↓
SSE: session.status (idle) → Set isBusy = false
```

## Scope

### In Scope
- `ChatViewModel.kt` - Complete refactor of message handling
- `ChatScreen.kt` - Update to consume Map-derived list
- `OpenCodeEventSource.kt` - Already connects to `/global/event` (no changes needed)
- Domain models - No changes needed

### Out of Scope
- Database/Room persistence (future enhancement)
- Offline support
- Other screens (sessions, terminal, settings)

## Success Criteria
- No duplicate key crashes in LazyColumn
- Messages appear via SSE streaming (text flows in incrementally)
- User message appears after SSE event (slight delay acceptable)
- No race conditions between event handlers

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| User sees delay before their message appears | Show "Sending..." indicator; message arrives within ~100ms via SSE |
| Part events arrive before message exists | Create placeholder message when first part arrives |
| Initial load doesn't populate map correctly | Load messages once, SSE handles all updates after |

## Related Fixes

During implementation, the following additional issues were discovered and fixed:

1. **SSE Endpoint**: Changed from `/event` to `/global/event` (uses GlobalBus for all events)
2. **Question Reply Endpoint**: Fixed from `POST /session/{sessionId}/questions/{questionId}` to `POST /question/{requestId}/reply` per OpenAPI spec

## References
- Openchamber implementation: `/home/jasmin/Projects/forks/openchamber/packages/ui/src/stores/messageStore.ts`
- OpenCode server event flow: Documented in session exploration
