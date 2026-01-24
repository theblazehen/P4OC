# Design: Chat Scroll UX

## Context
The chat screen uses a `LazyColumn` with `rememberLazyListState()`. Currently, a `LaunchedEffect` triggers `animateScrollToItem(messages.size - 1)` whenever `messages.size` changes. This unconditionally scrolls to the bottom, interrupting users who are reading earlier messages.

## Goals
- Auto-scroll to new content when user is "at the bottom"
- Stop auto-scroll immediately when user scrolls up
- Provide clear affordance to jump back to streaming content
- Smooth, jank-free experience even during fast token streaming

## Non-Goals
- Persist scroll position across session switches
- Implement infinite scroll / pagination for very long chats
- Add read receipts or message-level unread tracking

## Decisions

### 1. "Near Bottom" Threshold
**Decision**: Consider user "at bottom" if the last visible item index is within 2 items of the end, OR if `canScrollForward` is false.

**Rationale**: Using item count rather than pixel offset is more reliable with variable-height chat messages. The 2-item buffer prevents micro-scrolls from disabling auto-scroll.

**Alternative considered**: Pixel-based threshold (e.g., 100px from bottom). Rejected because message heights vary significantly.

### 2. State Management Location
**Decision**: Keep scroll state logic entirely in `ChatScreen.kt` using Compose state. ViewModel only exposes `isStreaming: Boolean`.

**Rationale**: Scroll position is pure UI concern. ViewModel already exposes `isBusy` which indicates streaming. No new ViewModel changes needed beyond potentially renaming for clarity.

### 3. Jump Button Placement
**Decision**: Position the jump button above the input bar, right-aligned, below the existing TodoTrackerFab.

**Rationale**: Avoids collision with TodoTrackerFab. Placing near input keeps it accessible while typing.

### 4. Animation Approach
**Decision**: Use `animateScrollToItem` for jump-to-bottom, let natural list updates handle streaming auto-scroll.

**Rationale**: `animateScrollToItem` provides smooth scrolling. For streaming, we want immediate scroll without animation lag.

## Implementation Approach

```kotlin
// Derived state for "at bottom" detection
val isAtBottom by remember {
    derivedStateOf {
        val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val totalItems = listState.layoutInfo.totalItemsCount
        lastVisibleItem >= totalItems - 2 || !listState.canScrollForward
    }
}

// Track if user has scrolled away
var userScrolledAway by remember { mutableStateOf(false) }

// Detect user scroll gesture
LaunchedEffect(listState.isScrollInProgress) {
    if (listState.isScrollInProgress && isAtBottom.not()) {
        userScrolledAway = true
    }
}

// Auto-scroll only when at bottom and not scrolled away
LaunchedEffect(messages.size) {
    if (messages.isNotEmpty() && !userScrolledAway) {
        listState.animateScrollToItem(messages.size - 1)
    }
}
```

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Jank during fast streaming | Batch scroll updates, use `scrollToItem` (no animation) for high-frequency updates |
| Button collision with TodoFab | Position jump button lower, use offset or separate anchor |
| Threshold too sensitive/insensitive | Make threshold configurable, start with 2 items |

## Open Questions
- Should the jump button show a count of new messages? (Suggest: no, keep it simple)
- Should auto-scroll resume automatically after a timeout? (Suggest: no, only on explicit action)
