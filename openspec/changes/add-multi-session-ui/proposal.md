# Change: Add Multi-Session UI with Status Bar and Swipe Navigation

## Why

Currently, switching between chat sessions requires navigating back to SessionListScreen, selecting another session, and waiting for it to load. When juggling 2-3 concurrent AI agents working on different tasks, this creates friction and prevents users from monitoring multiple sessions simultaneously. Users need ambient awareness of session states (busy, awaiting input, idle) and instant switching between "hot" sessions.

## What Changes

- **ADDED** Persistent status bar at top of ChatScreen showing hot session indicators with truncated names
- **ADDED** Session connection states: ACTIVE, BUSY, AWAITING_INPUT, IDLE, BACKGROUND, ERROR
- **ADDED** HorizontalPager for swipe navigation between hot sessions
- **ADDED** Priority-based reordering (inbox model): AWAITING_INPUT > BUSY > IDLE
- **ADDED** MultiSessionManager to manage concurrent SSE connections
- **ADDED** Pin/unpin toggle in SessionListScreen to manage hot sessions
- **MODIFIED** ChatScreen to support multi-session viewing via pager
- **MODIFIED** ChatViewModel to delegate SSE management to MultiSessionManager

## Impact

- Affected specs: multi-session (new capability)
- Affected code:
  - `ui/screens/chat/ChatScreen.kt` - Major refactor to add pager and status bar
  - `ui/screens/chat/ChatViewModel.kt` - Delegate to MultiSessionManager
  - `ui/screens/sessions/SessionListScreen.kt` - Add pin/unpin indicators
  - `ui/components/session/SessionStatusBar.kt` - New component
  - `core/session/MultiSessionManager.kt` - New manager class
  - `domain/model/Session.kt` - Add connection state
