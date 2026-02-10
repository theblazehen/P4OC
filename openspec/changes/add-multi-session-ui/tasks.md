# Tasks: Add Multi-Session UI

## 1. Domain Layer

- [x] 1.1 Create `SessionConnectionState` enum in `domain/model/`
- [x] 1.2 Create `HotSession` data class wrapping Session with connection state and flows
- [x] 1.3 Define `MultiSessionManager` interface in `domain/session/`

## 2. Data Layer

- [x] 2.1 Implement `MultiSessionManagerImpl` in `data/session/`
- [x] 2.2 Handle SSE connection lifecycle per hot session
- [x] 2.3 Aggregate session states into priority-sorted flow
- [x] 2.4 Implement session loading/unloading with SSE start/stop
- [x] 2.5 Add Koin module bindings for MultiSessionManager

## 3. UI Components

- [x] 3.1 Create `SessionStatusBar` composable with indicators
- [x] 3.2 Implement indicator states (filled, pulsing, badge, dim)
- [x] 3.3 Add pulse animation for BUSY state
- [x] 3.4 Add pulse + badge for AWAITING_INPUT state
- [x] 3.5 Implement tap-to-switch on indicators
- [x] 3.6 Implement close button (x) on active indicator
- [x] 3.7 Add (+) button to open NewSessionDialog
- [x] 3.8 Create `SessionIndicator` composable for reuse
- [x] 3.9 Create `SessionPinIndicator` for SessionListScreen

## 4. ChatScreen Refactor

- [x] 4.1 Add HorizontalPager wrapping chat content
- [x] 4.2 Integrate SessionStatusBar above title bar
- [x] 4.3 Sync pager state with status bar selection
- [x] 4.4 Update title bar to show current session title (on settle)
- [x] 4.5 Configure `beyondBoundsPageCount = 2`
- [x] 4.6 Preserve scroll position per session with rememberSaveable
- [x] 4.7 Extract `ChatPageContent` composable for pager pages
- [x] 4.8 Extract `MessageBlockUtils.kt` for shared message grouping logic

## 5. ChatViewModel Refactor

- [ ] 5.1 Inject MultiSessionManager instead of direct SSE handling (DEFERRED - existing ChatViewModel works alongside MultiSessionChatScreen)
- [ ] 5.2 Expose active session from manager
- [ ] 5.3 Delegate message flow to active HotSession
- [ ] 5.4 Delegate question/permission handling to active HotSession
- [ ] 5.5 Update send/abort to use active session context

Note: Phase 5 is deferred. The new `MultiSessionChatScreen` works independently using `MultiSessionManager` directly, while the existing `ChatScreen` continues to use `ChatViewModel`. This allows gradual migration.

## 6. SessionListScreen Updates

- [x] 6.1 Add pin/unpin indicator to session cards
- [x] 6.2 Show filled indicator for hot sessions
- [x] 6.3 Show hollow indicator for cold sessions
- [x] 6.4 Handle tap on indicator to pin/unpin
- [x] 6.5 Observe hot session list from MultiSessionManager

## 7. Navigation Updates

- [x] 7.1 Add `Screen.MultiSessionChat` route definition
- [x] 7.2 Wire `MultiSessionChatScreen` in NavGraph with arguments
- [x] 7.3 Handle initial session loading on navigate
- [x] 7.4 Add onNavigateToSessions callback for empty state

## 8. Testing

- [ ] 8.1 Unit tests for MultiSessionManager state management
- [ ] 8.2 Unit tests for priority sorting logic
- [ ] 8.3 UI tests for SessionStatusBar interactions
- [ ] 8.4 Integration test for session switching flow

## 9. Polish

- [ ] 9.1 Add haptic feedback on state changes (optional, user pref)
- [x] 9.2 Tune pulse animation timing (1000ms cycle)
- [x] 9.3 Handle edge cases (last session closed -> EmptyMultiSessionView)
- [ ] 9.4 Add loading state for session being loaded into hot list

## Files Created/Modified

### New Files
- `app/src/main/java/dev/blazelight/p4oc/domain/model/SessionConnectionState.kt`
- `app/src/main/java/dev/blazelight/p4oc/domain/model/HotSession.kt`
- `app/src/main/java/dev/blazelight/p4oc/domain/session/MultiSessionManager.kt`
- `app/src/main/java/dev/blazelight/p4oc/data/session/MultiSessionManagerImpl.kt`
- `app/src/main/java/dev/blazelight/p4oc/ui/components/session/SessionStatusBar.kt`
- `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/MultiSessionChatScreen.kt`
- `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/MessageBlockUtils.kt`

### Modified Files
- `app/src/main/java/dev/blazelight/p4oc/di/KoinModules.kt` - Added MultiSessionManager binding
- `app/src/main/java/dev/blazelight/p4oc/ui/navigation/Screen.kt` - Added MultiSessionChat route
- `app/src/main/java/dev/blazelight/p4oc/ui/navigation/NavGraph.kt` - Wired MultiSessionChatScreen
- `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/ChatScreen.kt` - Extracted shared utils
- `app/src/main/java/dev/blazelight/p4oc/ui/screens/sessions/SessionListScreen.kt` - Added pin/unpin indicator
