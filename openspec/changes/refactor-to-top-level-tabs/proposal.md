# Change: Refactor to Top-Level Tab Architecture

## Why

The current multi-session UI uses a status bar and HorizontalPager within ChatScreen to switch between sessions. This works but is limited to chat sessions only. Users have expressed interest in tabbing other screen types (Files, Terminal) and having a more IDE-like experience with independent navigation stacks per tab.

The current architecture also has UX issues:
- Brief flash of "no active sessions" when loading initial session
- Session tabs are hidden inside ChatScreen rather than being a top-level concept
- Can't have a Files tab open alongside Chat tabs
- Navigation between Sessions list and Chat feels disconnected

## What Changes

- **ADDED** Top-level `TabManager` to manage generic workspace tabs
- **ADDED** `TabBar` component as the primary navigation UI at top of screen
- **ADDED** `MainTabScreen` as root container with tab bar + content area
- **ADDED** Per-tab navigation stacks (each tab has its own NavHost)
- **MODIFIED** App starts with one tab showing SessionListScreen
- **MODIFIED** Clicking a session navigates within the same tab to ChatScreen
- **MODIFIED** Opening Files/Terminal from chat creates a new tab
- **MODIFIED** Settings navigates within current tab (can back out)
- **REMOVED** `MultiSessionChatScreen` (replaced by tab system)
- **REMOVED** `SessionStatusBar` (replaced by TabBar)

## Impact

- Affected specs: tab-system (new capability)
- Affected code:
  - `ui/tabs/` - New package for tab infrastructure
  - `ui/navigation/NavGraph.kt` - Simplified, per-tab navigation
  - `ui/screens/chat/ChatScreen.kt` - Simplified, single-session only
  - `ui/screens/sessions/SessionListScreen.kt` - Opens chat in same tab or focuses existing
  - `MainActivity.kt` - Use MainTabScreen as root
- Supersedes: `add-multi-session-ui` change (this is the evolution)

## Key Design Decisions

1. **Tabs are generic** - Any tab can show any screen (Sessions, Chat, Files, Terminal)
2. **New tab defaults to SessionListScreen** - Natural starting point
3. **One chat tab per session** - If session already open, focus that tab
4. **Back navigates within tab** - At ChatScreen, back goes to SessionListScreen
5. **Settings within tab** - Navigate to settings, back returns to previous screen
6. **Minimum 1 tab** - Can't close last tab; closing replaces with fresh SessionListScreen
7. **Warn at 5+ tabs** - Performance warning, no hard limit
8. **No persistence yet** - Tabs reset on app restart
