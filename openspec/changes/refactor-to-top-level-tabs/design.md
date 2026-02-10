# Design: Top-Level Tab Architecture

## Context

The app currently has a multi-session UI implemented with `SessionStatusBar` and `HorizontalPager` inside `MultiSessionChatScreen`. This approach works for chat sessions but has limitations:

1. **Chat-only** - Only chat sessions can be tabbed
2. **Nested complexity** - Tab logic embedded in chat screen
3. **Flash of empty state** - Loading initial session shows brief "no sessions" screen
4. **No extensibility** - Can't tab Files, Terminal, or other screens

Users want an IDE-like experience where any screen type can be a tab, with independent navigation per tab.

**Stakeholders:** Power users, developers familiar with VS Code/IDE tab patterns
**Constraints:** Mobile UX (limited horizontal space for tabs), memory (multiple nav stacks)

## Goals / Non-Goals

**Goals:**
- Generic tab system supporting any screen type
- Per-tab navigation with independent back stacks
- One tab per chat session (no duplicates)
- Familiar IDE-like tab bar with close buttons
- Seamless migration from current multi-session UI

**Non-Goals:**
- Tab persistence across app restarts (future work)
- Tab reordering via drag-and-drop (future work)
- Split view / side-by-side tabs (future work)
- Tab groups / folders (future work)

## Decisions

### 1. Tab as Generic Container

**Decision:** Tabs are generic containers, not typed. Any tab can show any screen.

```kotlin
data class TabState(
    val id: String,                     // UUID
    val navController: NavHostController, // Per-tab navigation
    val sessionId: String? = null,      // If showing chat, which session (for uniqueness check)
)
```

**Why:** Maximum flexibility. A tab that starts on Sessions can navigate to Chat, then to Settings, all within the same tab. No artificial restrictions.

**Alternative considered:** Typed tabs (ChatTab, FilesTab, etc.) - rejected as overly restrictive and requires more maintenance.

### 2. Tab Title/Icon Derived from Current Screen

**Decision:** Tab title and icon are derived from what the tab is currently showing, not stored as static properties.

- SessionListScreen → "Sessions" + list icon
- ChatScreen → session.title + chat icon (with state indicator)
- FileExplorerScreen → current directory name + folder icon
- TerminalScreen → "Terminal" + terminal icon
- SettingsScreen → "Settings" + settings icon

**Why:** Titles naturally update as navigation changes. When a tab navigates from Sessions to Chat, the title updates automatically.

### 3. Session Uniqueness Enforcement

**Decision:** Only one tab can show a given chat session. If user tries to open an already-open session:
1. Find existing tab with that sessionId
2. Focus that tab
3. Don't create duplicate

```kotlin
fun openSession(sessionId: String, directory: String?) {
    val existingTab = tabs.find { it.sessionId == sessionId }
    if (existingTab != null) {
        focusTab(existingTab.id)
    } else {
        // Navigate current tab to chat
        activeTab.navController.navigate(Screen.Chat.createRoute(sessionId, directory))
        activeTab.sessionId = sessionId
    }
}
```

**Why:** Prevents confusion from having the same session open in multiple places with potentially divergent state.

### 4. Tab Bar Visual Design

**Decision:** Reuse the visual language from `SessionStatusBar`:
- Scrollable horizontal row at top (below system status bar)
- ~28dp height
- Indicator dots showing session state (for chat tabs)
- Close (×) button on each tab
- [+] button at end to create new tab
- Active tab has distinct background

**Why:** Consistency with existing UI. Users already familiar with the status bar indicators.

### 5. Navigation Within Tabs

**Decision:** Each tab has its own `NavController` with a mini NavHost containing:
- SessionListScreen (root for new tabs)
- ChatScreen
- FileExplorerScreen → FileViewerScreen → DiffViewerScreen
- TerminalScreen
- SettingsScreen (and sub-screens)
- ProjectsScreen

**Back behavior:**
- Back navigates within the tab's back stack
- At root (SessionListScreen), back does nothing (use tab close button)

**Why:** Independent navigation stacks give true tab isolation. Opening settings in one tab doesn't affect other tabs.

### 6. Opening New Tabs from Chat

**Decision:** "Open in new tab" actions in ChatScreen:
- Files button → creates new tab, navigates to FileExplorerScreen
- Terminal button → creates new tab, navigates to TerminalScreen

The new tab is focused immediately.

**Why:** These are logically separate workspaces. User can flip between chat and files tabs.

### 7. Minimum/Maximum Tabs

**Decision:**
- Minimum: 1 tab (closing last tab creates fresh SessionListScreen tab)
- Maximum: Soft limit with warning at 5+ tabs ("Performance may be affected")
- No hard limit

**Why:** Always need at least one tab. Warning educates users without restricting power users.

## Architecture

### Component Structure

```
┌─────────────────────────────────────────────────────────┐
│                     MainActivity                         │
│                          │                               │
│                          ▼                               │
│                   MainTabScreen                          │
│              ┌───────────┴───────────┐                   │
│              ▼                       ▼                   │
│          TabBar              TabContentArea              │
│     (scrollable row)        (shows active tab)          │
│              │                       │                   │
│              │                       ▼                   │
│              │              TabContent(tab)              │
│              │                       │                   │
│              │                       ▼                   │
│              │                NavHost(tab.navController) │
│              │                       │                   │
│              │          ┌────────────┼────────────┐      │
│              │          ▼            ▼            ▼      │
│              │   SessionList    ChatScreen    FilesScreen│
│              │                                           │
│              └──────────► TabIndicator × N               │
└─────────────────────────────────────────────────────────┘
```

### TabManager State

```kotlin
class TabManager : ViewModel() {
    private val _tabs = MutableStateFlow<List<TabState>>(listOf(createInitialTab()))
    val tabs: StateFlow<List<TabState>> = _tabs.asStateFlow()
    
    private val _activeTabId = MutableStateFlow<String>(tabs.value.first().id)
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()
    
    val activeTab: TabState get() = tabs.value.first { it.id == activeTabId.value }
    
    fun createTab(): TabState { ... }
    fun closeTab(tabId: String) { ... }
    fun focusTab(tabId: String) { ... }
    fun findTabBySessionId(sessionId: String): TabState? { ... }
}
```

### Tab Title Resolution

```kotlin
@Composable
fun TabState.resolveTitle(): String {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    return when {
        currentRoute?.startsWith("chat/") == true -> {
            // Extract session title from ChatViewModel or passed state
            sessionTitle ?: "Chat"
        }
        currentRoute == Screen.Sessions.route -> "Sessions"
        currentRoute == Screen.Files.route -> currentDirectory ?: "Files"
        currentRoute == Screen.Terminal.route -> "Terminal"
        currentRoute?.startsWith("settings") == true -> "Settings"
        else -> "Tab"
    }
}
```

### Data Flow

```
User taps session in SessionListScreen
           │
           ▼
TabManager.openSession(sessionId, directory)
           │
           ├─── Check: findTabBySessionId(sessionId)
           │
           ▼
    ┌──────┴──────┐
    │             │
 Found?        Not Found
    │             │
    ▼             ▼
focusTab()   activeTab.navController.navigate(Chat)
             activeTab.sessionId = sessionId
```

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Memory usage with many tabs | Warn at 5+ tabs; NavHost only composes visible content |
| Complex state management | Single TabManager as source of truth |
| Tab title synchronization | Derive titles from current route/state, don't store |
| Migration from multi-session UI | Clean removal of old code, no feature flags |

## Migration Plan

### Phase 1: Core Infrastructure
1. Create `TabState` data class
2. Create `TabManager` ViewModel
3. Create `TabBar` composable (reuse SessionStatusBar visuals)
4. Create `MainTabScreen` container

### Phase 2: Per-Tab Navigation
5. Create per-tab NavHost with screen routes
6. Wire tab switching to show/hide NavHosts
7. Implement tab title resolution

### Phase 3: Session Integration
8. Update SessionListScreen to use TabManager for navigation
9. Implement session uniqueness check
10. Update ChatScreen to simplified single-session mode

### Phase 4: New Tab Actions
11. Wire Files/Terminal buttons to create new tabs
12. Wire Settings to navigate within tab
13. Implement [+] button for new tab

### Phase 5: Cleanup
14. Remove MultiSessionChatScreen
15. Remove SessionStatusBar
16. Remove MultiSessionManager (or repurpose for SSE management)
17. Update MainActivity to use MainTabScreen

## Open Questions

None - all decisions locked in during design discussion.
