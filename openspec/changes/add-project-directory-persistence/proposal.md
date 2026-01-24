# Change: Project Directory Persistence

## Why

The session list shows "No sessions yet" even when sessions exist because `DirectoryManager` doesn't persist state across app restarts. On first launch, `directoryManager.getDirectory()` returns `null`, causing the server to use its `process.cwd()` as project context - often the wrong project.

OpenCode sessions are **project-scoped**. Each git repository has a unique project ID, and sessions are stored per-project. The Android app needs to remember which project the user was working on.

## What Changes

### Directory Persistence
- Add DataStore persistence for last selected project worktree path
- Load persisted directory on app startup
- Validate against server's project list

### Initialization Strategy  
- On startup: fetch `/project` list from server
- If persisted worktree matches a project → use it, go to sessions
- If persisted worktree is invalid/missing → show project selector
- If no projects exist → use `null` directory (global/server default)

### Navigation Flow Change
- **New home screen:** ProjectsScreen becomes the entry point after connection
- Each project card shows session count preview
- Tapping a project → SessionListScreen filtered to that project
- Last selected project is persisted and auto-selected on next launch

### Project Cards Enhancement
- Show session count per project
- Show last activity timestamp
- Quick-tap to jump into most recent session

## Impact

- **New capability spec:** `project-management`
- **Modified files:**
  - `DirectoryManager.kt` - Add DataStore persistence
  - `NavGraph.kt` - Change post-connection destination
  - `ProjectsScreen.kt` - Add session counts, persist selection
  - `SessionListViewModel.kt` - Ensure directory is set before loading
- **New files:**
  - `PreferencesDataStore.kt` - DataStore wrapper
