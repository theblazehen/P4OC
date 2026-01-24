# Design: Message Architecture Refactor

## Overview

This document describes the technical design for refactoring the message handling system from a list-based approach with multiple sources of truth to a Map-based approach with SSE as the single source of truth.

## Current Architecture (Problematic)

```
┌─────────────────────────────────────────────────────────────────┐
│                     CURRENT DATA FLOW                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  _messages: SnapshotStateList<MessageWithParts>                │
│                                                                 │
│  Sources that WRITE to _messages:                              │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ 1. loadMessages()      - Initial API load               │   │
│  │ 2. sendMessage()       - Optimistic temp message        │   │
│  │ 3. sendMessage()       - API response (real message)    │   │
│  │ 4. handleEvent()       - SSE message.updated            │   │
│  │ 5. handleEvent()       - SSE message.part.updated       │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  Problem: 5 different code paths can add the same message,     │
│  causing duplicates even with synchronized blocks.             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Proposed Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     NEW DATA FLOW                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  _messagesMap: SnapshotStateMap<String, MessageWithParts>      │
│                                                                 │
│  Sources that WRITE to _messagesMap:                           │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ 1. loadMessages()      - Initial population (once)      │   │
│  │ 2. handleEvent()       - SSE events (UPSERT only)       │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  sendMessage() only:                                            │
│  - Clears input, sets isSending=true                           │
│  - Fires API call (response ignored for UI)                    │
│  - SSE events handle all message appearance                    │
│                                                                 │
│  Benefits:                                                      │
│  - Map key = message ID → duplicates impossible                │
│  - UPSERT pattern → no check-then-add race conditions          │
│  - Single writer pattern → predictable state                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Data Structures

### Before
```kotlin
private val _messages: SnapshotStateList<MessageWithParts> = mutableStateListOf()
val messages: SnapshotStateList<MessageWithParts> = _messages
```

### After
```kotlin
private val _messagesMap = mutableStateMapOf<String, MessageWithParts>()

// Derived sorted list for UI consumption
val messages: List<MessageWithParts>
    get() = _messagesMap.values
        .sortedBy { it.message.createdAt }
        .toList()
```

## Key Operations

### 1. Initial Load
```kotlin
private fun loadMessages() {
    viewModelScope.launch {
        val result = safeApiCall { api.getMessages(sessionId, directory = getDirectory()) }
        when (result) {
            is ApiResult.Success -> {
                result.data.forEach { dto ->
                    val msg = messageMapper.mapWrapperToDomain(dto, partMapper)
                    _messagesMap[msg.message.id] = msg
                }
            }
            is ApiResult.Error -> { /* handle error */ }
        }
    }
}
```

### 2. Send Message (Fire-and-Forget)
```kotlin
fun sendMessage() {
    val text = _uiState.value.inputText.trim()
    if (text.isEmpty()) return
    
    _uiState.update { it.copy(inputText = "", isSending = true) }
    
    viewModelScope.launch {
        val api = connectionManager.getApi() ?: return@launch
        val request = SendMessageRequest(parts = listOf(PartInputDto(type = "text", text = text)))
        
        // Fire and forget - don't use response for UI
        safeApiCall { api.sendMessageStreaming(sessionId, request, getDirectory()) }
        
        // isSending will be cleared by session.status SSE event
    }
}
```

### 3. Handle SSE Events (UPSERT Pattern)
```kotlin
private fun handleEvent(event: OpenCodeEvent) {
    when (event) {
        is OpenCodeEvent.MessageUpdated -> {
            if (event.message.sessionID == sessionId) {
                upsertMessage(event.message)
            }
        }
        is OpenCodeEvent.MessagePartUpdated -> {
            if (event.part.sessionID == sessionId) {
                upsertPart(event.part, event.delta)
            }
        }
        is OpenCodeEvent.SessionStatusChanged -> {
            if (event.sessionID == sessionId) {
                val isBusy = event.status is SessionStatus.Busy
                _uiState.update { it.copy(isBusy = isBusy, isSending = false) }
            }
        }
        // ... other events
    }
}

private fun upsertMessage(message: Message) {
    val existing = _messagesMap[message.id]
    _messagesMap[message.id] = if (existing != null) {
        existing.copy(message = message)
    } else {
        MessageWithParts(message, emptyList())
    }
}

private fun upsertPart(part: Part, delta: String?) {
    val messageId = part.messageID
    
    // Get or create message
    val existing = _messagesMap[messageId] ?: MessageWithParts(
        message = createPlaceholderMessage(messageId, part.sessionID),
        parts = emptyList()
    )
    
    // Upsert part
    val partIndex = existing.parts.indexOfFirst { it.id == part.id }
    val updatedParts = if (partIndex >= 0) {
        existing.parts.toMutableList().apply {
            this[partIndex] = applyDelta(this[partIndex], part, delta)
        }
    } else {
        existing.parts + part
    }
    
    _messagesMap[messageId] = existing.copy(parts = updatedParts)
}

private fun applyDelta(existing: Part, incoming: Part, delta: String?): Part {
    return if (delta != null && incoming is Part.Text && existing is Part.Text) {
        incoming.copy(text = existing.text + delta, isStreaming = true)
    } else {
        incoming
    }
}

private fun createPlaceholderMessage(messageId: String, sessionId: String): Message {
    return Message.Assistant(
        id = messageId,
        sessionID = sessionId,
        createdAt = System.currentTimeMillis(),
        agent = "",
        model = ModelRef("", "")
    )
}
```

## UI Integration

### ChatScreen.kt Changes

The LazyColumn currently uses:
```kotlin
LazyColumn(...) {
    items(
        items = viewModel.messages,
        key = { it.message.id }
    ) { messageWithParts ->
        // ...
    }
}
```

With Map-based storage, `viewModel.messages` returns a sorted list derived from the map. The `key` function remains the same, but duplicates are now impossible because the map enforces unique keys.

## Thread Safety

Compose's `mutableStateMapOf()` is thread-safe for individual operations. Since we only do single-key operations (get, put), no additional synchronization is needed.

The derived `messages` list is computed on each access, which is safe because:
1. Map reads are atomic
2. The list is a new immutable snapshot each time
3. Compose recomposition handles the updates

## Migration Path

1. Replace `_messages: SnapshotStateList` with `_messagesMap: SnapshotStateMap`
2. Add derived `messages` property
3. Refactor `loadMessages()` to populate map
4. Refactor `sendMessage()` to fire-and-forget (no optimistic message)
5. Refactor event handlers to use upsert pattern
6. Remove synchronized blocks and helper methods (`addMessageIfAbsent`, etc.)
7. Update ChatScreen if needed (likely minimal changes)

## Testing Strategy

1. **Manual Testing**:
   - Send message, verify it appears via SSE
   - Verify text streams in incrementally
   - Verify no crashes on rapid message sending
   - Verify initial load works correctly

2. **Unit Testing** (future):
   - Test upsertMessage with new/existing messages
   - Test upsertPart with new/existing parts
   - Test delta application
