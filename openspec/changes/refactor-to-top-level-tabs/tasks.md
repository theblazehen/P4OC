# Tasks: Refactor to Top-Level Tab Architecture

## Phase 1: Core Tab Infrastructure

- [x] 1.1 Create `TabState` data class in `ui/tabs/TabState.kt`
  - Properties: id (UUID), sessionId (nullable), navController reference
  - No stored title/icon (derived from current screen)

- [x] 1.2 Create `TabManager` ViewModel in `ui/tabs/TabManager.kt`
  - `tabs: StateFlow<List<TabState>>`
  - `activeTabId: StateFlow<String>`
  - `createTab(): TabState` - creates new tab with fresh NavController
  - `closeTab(tabId: String)` - removes tab, handles minimum 1 tab rule
  - `focusTab(tabId: String)` - switches active tab
  - `findTabBySessionId(sessionId: String): TabState?` - for uniqueness check
  - `updateTabSessionId(tabId: String, sessionId: String?)` - when navigating to/from chat

- [x] 1.3 Create `TabIndicator` composable in `ui/tabs/TabBar.kt`
  - Reuse visual style from SessionStatusBar indicators
  - Show session state dot for chat tabs (busy, waiting, idle)
  - Close (×) button
  - Tap to focus

- [x] 1.4 Create `TabBar` composable in `ui/tabs/TabBar.kt`
  - Scrollable horizontal row of TabIndicators
  - Active tab highlighted
  - [+] button at end for new tab
  - ~28dp height, below system status bar

- [x] 1.5 Add Koin binding for TabManager in `di/KoinModules.kt`
  - Singleton scope (lives for app lifetime)

## Phase 2: Main Tab Container

- [x] 2.1 Create `MainTabScreen` composable in `ui/tabs/MainTabScreen.kt`
  - Column layout: TabBar + TabContentArea
  - TabContentArea shows only the active tab's content
  - Handle system insets (status bar padding on TabBar)

- [x] 2.2 Create per-tab `NavHost` in `ui/tabs/TabNavHost.kt`
  - Routes: Sessions, Chat, Files, FileViewer, DiffViewer, Terminal, Settings, Projects
  - Start destination: Sessions
  - Accept NavController from TabState

- [x] 2.3 Implement tab content switching
  - Only compose active tab's NavHost (or use visibility for performance)
  - Preserve inactive tab state

- [x] 2.4 Implement tab title/icon resolution
  - Observe current route from NavController
  - For chat: get session title from route args or ViewModel
  - Return appropriate icon for each screen type

## Phase 3: Session Integration

- [x] 3.1 Update `SessionListScreen` to use TabManager
  - Inject TabManager
  - On session click: check `findTabBySessionId()`
    - Found → `focusTab(existingTab.id)`
    - Not found → navigate within current tab, update sessionId

- [x] 3.2 Simplify `ChatScreen` to single-session mode
  - Remove HorizontalPager (tabs handle multi-session now)
  - Remove status bar integration
  - Keep existing message display, input, question/permission handling
  - Add callback to notify TabManager of session title changes

- [x] 3.3 Update ChatScreen navigation callbacks
  - `onOpenFiles` → TabManager.createTab() + navigate to Files
  - `onOpenTerminal` → TabManager.createTab() + navigate to Terminal
  - `onNavigateBack` → navController.popBackStack() (within tab)

- [x] 3.4 Handle session state in tab indicator
  - Chat tabs show session connection state (busy/waiting/idle)
  - Observe from MultiSessionManager or ChatViewModel
  - Update TabIndicator color/animation accordingly

## Phase 4: New Tab Actions

- [x] 4.1 Implement [+] button behavior
  - Creates new tab via TabManager.createTab()
  - New tab starts at SessionListScreen
  - Focuses the new tab

- [x] 4.2 Implement Files button in ChatScreen
  - Creates new tab
  - Navigates new tab to FileExplorerScreen
  - Focuses the new tab

- [x] 4.3 Implement Terminal button in ChatScreen
  - Creates new tab
  - Navigates new tab to TerminalScreen
  - Focuses the new tab

- [x] 4.4 Implement Settings navigation
  - Navigate within current tab to SettingsScreen
  - Back button returns to previous screen in same tab

- [x] 4.5 Implement tab close behavior
  - Close tab removes from list
  - If closing active tab, focus previous tab (or next if first)
  - If closing last tab, create fresh SessionListScreen tab

- [x] 4.6 Add 5+ tabs performance warning
  - Show snackbar/toast when creating 5th tab
  - "Multiple tabs may affect performance"
  - Only show once per session

## Phase 5: Cleanup & Migration

- [x] 5.1 Update `MainActivity` to use `MainTabScreen`
  - Replace current NavHost with MainTabScreen
  - Remove old root navigation setup

- [x] 5.2 Remove `MultiSessionChatScreen`
  - Delete file
  - Remove from NavGraph
  - Remove Screen.MultiSessionChat route

- [x] 5.3 Remove `SessionStatusBar`
  - Delete file (or repurpose parts for TabIndicator)
  - Remove from imports

- [x] 5.4 Simplify or remove `MultiSessionManager`
  - Evaluate if still needed for SSE connection management
  - If keeping, remove UI-specific logic (hot sessions concept)
  - If removing, move SSE logic to ChatViewModel

- [x] 5.5 Remove old navigation routes
  - Clean up NavGraph.kt
  - Remove unused Screen definitions

- [x] 5.6 Update `SessionListScreen` to remove pin/unpin indicators
  - Remove SessionPinIndicator usage
  - Remove hotSessionIds observation
  - Simplify SessionCard (remove isHot, onPinToggle params)

## Phase 6: Polish & Testing

- [x] 6.1 Test tab switching performance
  - Verify smooth transitions between tabs
  - Check memory usage with 5+ tabs

- [x] 6.2 Test session uniqueness
  - Verify clicking same session focuses existing tab
  - Verify session state updates reflect in tab indicator

- [x] 6.3 Test back navigation within tabs
  - Chat → back → SessionList
  - Settings → back → previous screen
  - Nested file navigation → back works correctly

- [x] 6.4 Test tab close edge cases
  - Close middle tab
  - Close first tab
  - Close last remaining tab

- [x] 6.5 Verify no flash of empty state
  - New tab immediately shows SessionListScreen
  - No loading flicker

- [x] 6.6 Build verification
  - `./gradlew :app:compileDebugKotlin` passes
  - `./gradlew :app:assembleDebug` produces working APK

## Dependencies

- Phase 2 depends on Phase 1
- Phase 3 depends on Phase 2
- Phase 4 depends on Phase 3
- Phase 5 depends on Phase 4
- Phase 6 can run in parallel with Phase 5

## Parallelizable Work

Within Phase 1: Tasks 1.1-1.4 can be done in parallel
Within Phase 4: Tasks 4.1-4.4 can be done in parallel after 4.5 is planned
