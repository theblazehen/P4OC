## Context

OpenCode server is directory-scoped. Each API request can include a `directory` query parameter to switch project context. The Android app has `DirectoryManager` but it only holds in-memory state that resets on app restart.

**Key insight:** We always have server connection, so we can validate persisted directory against live project list on every startup.

## Goals / Non-Goals

**Goals:**
- Persist last selected project directory across app restarts
- Validate persisted directory against server on startup
- Projects as home screen (TUI-style)
- Seamless project switching

**Non-Goals:**
- Offline support (always connected)
- Multi-project view (sessions from all projects at once)
- Arbitrary directory picker (only server-known projects)

## Decisions

### Decision 1: DataStore for persistence
- Modern, coroutine-friendly, type-safe
- Single key: `project_worktree`

### Decision 2: Projects as home screen
- Matches TUI mental model (project → sessions → chat)
- Clear hierarchy, no ambiguity about which project is active
- Auto-navigate to last project if still valid

### Decision 3: Store worktree path
- Worktree is what API expects in `directory` param
- Stable identifier across server restarts

### Decision 4: Global fallback for empty project list
- If `/project` returns empty, use `null` directory
- Server will use its `process.cwd()` as context
- Edge case, but handles fresh server with no git repos

## Initialization Flow

```
App Start
    ↓
Connected to Server?
    ↓ yes
Fetch /project list
    ↓
Load persisted worktree from DataStore
    ↓
Worktree matches a project?
    ├─ yes → Set DirectoryManager, navigate to SessionsFiltered
    └─ no  → Navigate to ProjectsScreen (user picks)
             └─ No projects? → Use null directory, go to Sessions
```

## API Usage

- `GET /project` - List all projects (already implemented)
- `GET /session?directory=X` - Sessions for project (already implemented)
- All other APIs already accept `directory` param
