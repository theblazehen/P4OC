# Design: Multi-Session UI

## Context

The app currently supports one active chat session at a time. Users working with multiple AI agents (e.g., one fixing bugs, one writing tests, one refactoring) must navigate back to the session list to switch. This breaks flow and prevents monitoring concurrent agent activity.

**Stakeholders:** Power users juggling 2-4 concurrent sessions
**Constraints:** Mobile screen real estate, battery life (SSE connections), memory (keeping sessions rendered)

## Goals / Non-Goals

**Goals:**
- Instant switching between 2-4 "hot" sessions without reload
- Ambient awareness of session states (busy, needs input, idle)
- Inbox-like prioritization (sessions needing attention bubble up)
- Gesture-native navigation (swipe between sessions)

**Non-Goals:**
- Split-screen/picture-in-picture (deferred)
- Unlimited hot sessions (user manages, but 2-4 typical)
- Background notifications for session activity (separate feature)
- Offline session support

## Decisions

### 1. Hybrid Status Bar + Swipe Navigation

**Decision:** Combine a thin persistent status bar (28dp) with HorizontalPager for swipe navigation.

**Why:** Status bar provides at-a-glance state visibility and tap-to-switch. Swipe provides gesture-native quick switching. Together they serve both glancers and power users.

**Alternatives considered:**
- Bottom tabs: Eats vertical space, conflicts with input bar
- Drawer: Hidden by default, loses ambient awareness
- Floating pills: Extra tap required, less discoverable

### 2. Session Connection States

**Decision:** Six states managed by MultiSessionManager:

```kotlin
enum class SessionConnectionState {
    ACTIVE,          // Currently viewing, SSE connected
    BUSY,            // SSE connected, agent processing
    AWAITING_INPUT,  // SSE connected, needs user (question/permission)
    IDLE,            // SSE connected, nothing happening
    BACKGROUND,      // Disconnected, needs reload
    ERROR            // Connection failed
}
```

**Why:** Distinguishes "hot" (SSE connected) from "cold" (background) sessions. AWAITING_INPUT is critical for inbox model.

### 3. Priority-Based Reordering (Inbox Model)

**Decision:** Status bar reorders by priority: AWAITING_INPUT (leftmost) > BUSY > IDLE/ACTIVE

**Why:** Treats sessions like a notification inbox - items needing attention float to top/left. Swipe RIGHT to see what needs you.

**Ordering details:**
- New sessions appear on LEFT
- State changes trigger reorder (AWAITING_INPUT always jumps left)
- User switching does NOT reorder (only state changes do)

### 4. Rendering Strategy

**Decision:** HorizontalPager with `beyondBoundsPageCount = 2` to keep up to 3 pages composed.

**Why:** For typical 2-3 hot sessions, all stay rendered for instant switching. Fourth+ session recomposes on switch but scroll position is preserved via `rememberSaveable`.

**Trade-offs:**
- More memory usage (3 LazyColumns in memory)
- Faster switching (no recomposition)
- Acceptable for target use case (2-4 sessions)

### 5. Session Selection UX

**Decision:** Tappable pin/unpin indicator in SessionListScreen:
- `[●]` filled = hot, tap to unpin (remove from hot list)
- `[○]` hollow = cold, tap to pin (add to hot list)
- Tapping row still navigates to chat

**Why:** Explicit control over hot list without changing navigation behavior. Users see which sessions are "hot" at a glance.

### 6. Unloading Sessions

**Decision:** Two mechanisms:
- `×` button on active session indicator in status bar
- Pin/unpin toggle in SessionListScreen

**Why:** Quick action available in chat, full management in session list. No accidental dismissal (× only on active).

## Architecture

### MultiSessionManager

Central coordinator for hot sessions:

```
┌─────────────────────────────────────────────────────────┐
│                  MultiSessionManager                     │
├─────────────────────────────────────────────────────────┤
│ hotSessions: StateFlow<List<HotSession>>                │
│ activeSessionId: StateFlow<String?>                     │
│ sessionStates: StateFlow<Map<String, ConnectionState>>  │
├─────────────────────────────────────────────────────────┤
│ loadSession(id) → adds to hot, starts SSE              │
│ unloadSession(id) → stops SSE, removes from hot        │
│ setActiveSession(id) → switches view                    │
└─────────────────────────────────────────────────────────┘
         │
         │ owns multiple
         ▼
┌─────────────────────────────────────────────────────────┐
│                     HotSession                           │
├─────────────────────────────────────────────────────────┤
│ session: Session                                         │
│ connectionState: StateFlow<SessionConnectionState>       │
│ messages: StateFlow<List<MessageWithParts>>              │
│ eventSource: OpenCodeEventSource                         │
│ pendingQuestion: StateFlow<Question?>                    │
│ pendingPermission: StateFlow<Permission?>                │
└─────────────────────────────────────────────────────────┘
```

### Data Flow

```
SessionListScreen          ChatScreen
      │                        │
      │ tap pin ───────────────┤
      │                        │
      ▼                        ▼
MultiSessionManager ◄──────► HorizontalPager
      │                        │
      │ manages SSE            │ displays
      ▼                        ▼
  HotSession ─────────────► ChatPageContent
      │                        │
      │ emits states           │ shows messages
      ▼                        ▼
SessionStatusBar ◄───────── pagerState
```

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Memory pressure with 4+ hot sessions | User manages count; LazyColumn virtualizes messages |
| Battery drain from multiple SSE | SSE is lightweight; connections idle when no activity |
| Swipe conflicts with other gestures | Pager handles edge detection; no horizontal swipe in chat content |
| Complexity of state synchronization | Single source of truth in MultiSessionManager |

## Migration Plan

1. **Phase 1:** Add MultiSessionManager, HotSession, SessionConnectionState (no UI changes)
2. **Phase 2:** Add SessionStatusBar component (can test in isolation)
3. **Phase 3:** Refactor ChatScreen to use HorizontalPager
4. **Phase 4:** Update SessionListScreen with pin/unpin indicators
5. **Phase 5:** Wire everything together, migrate ChatViewModel

**Rollback:** Feature flag to disable multi-session UI and use single-session mode.

## Open Questions

None - all decisions locked in during design discussion.
