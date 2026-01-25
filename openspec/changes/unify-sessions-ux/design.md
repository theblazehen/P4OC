# Design: Unified Sessions UX

## Context
The current navigation flow has structural issues:
1. When auto-navigating to a remembered project's sessions, the back stack is empty (Server is popped inclusively)
2. The folder icon is hidden when viewing filtered sessions, leaving no way to switch projects
3. Users can't see all recent work at a glance - they must navigate between projects

This change unifies the session browsing experience while preserving the ability to focus on a single project when needed.

## Goals
- Single entry point for all sessions after connection
- Visual distinction between projects via colored chips
- Seamless navigation between unified and filtered views
- Maintain ability to focus on single project's sessions

## Non-Goals
- Offline session caching (future work)
- Project management (create/delete projects) - existing functionality preserved
- Session search/filtering beyond project filter

## Decisions

### 1. Session Aggregation Strategy
**Decision**: Fetch all sources in parallel, merge and sort client-side

- Fetch global sessions (1 API call)
- Fetch project list (1 API call)  
- For each project, fetch its sessions (N API calls in parallel)
- Merge all sessions, sort by `updatedAt` descending

**Alternatives considered**:
- Server-side aggregation endpoint: Would require API changes, out of scope
- Sequential fetching: Slower, no benefit

**Trade-off**: More API calls, but they're parallelized. Acceptable for typical project counts (<10).

### 2. Project Color Assignment
**Decision**: Hash-based index into curated 8-color palette

```kotlin
val palette = listOf(
    Color(0xFF4A90D9), // blue
    Color(0xFFE67E22), // orange  
    Color(0xFF2ECC71), // green
    Color(0xFF9B59B6), // purple
    Color(0xFF1ABC9C), // teal
    Color(0xFFE74C3C), // red
    Color(0xFFE91E63), // pink
    Color(0xFF5C6BC0), // indigo
)

fun colorForProject(projectId: String): Color {
    return palette[projectId.hashCode().absoluteValue % palette.size]
}
```

**Alternatives considered**:
- Persisted color per project: More complexity, marginal benefit
- Random colors: Not deterministic, confusing across sessions
- User-selected colors: Nice but out of scope

**Trade-off**: Possible color collisions with many projects. Acceptable since colors are hints, not identifiers.

### 3. Navigation Architecture
**Decision**: Sessions screen is the root after connection

```
Server (connection)
    ↓
Sessions (unified - root destination)
    ├── [tap chip] → SessionsFiltered (for that project)
    │                   ↓ back button
    │               Sessions (unified)
    │
    ├── [folder icon] → Projects (list all)
    │                       ↓ back button
    │                   Sessions (unified)
    │
    └── [tap session] → Chat
                           ↓ back button
                       Sessions (unified or filtered, whichever was source)
```

**Key change**: `popUpTo(Screen.Server.route) { inclusive = true }` navigates to Sessions, and Sessions becomes the logical root. SessionsFiltered and Projects both have Sessions in their back stack.

### 4. Global Sessions Chip Display
**Decision**: No chip for global sessions

- Global sessions have no project chip displayed
- Keeps cards clean for the common case
- Trade-off: Can't filter to "only global" - acceptable for MVP
- Future: Could add filter bar with "Global" option if needed

### 5. New Session Default
**Decision**: Default to global, with project picker

- New session dialog shows project dropdown
- Defaults to "Global" (no project filter)
- User can select a specific project before creating
- Simplifies the common case while preserving flexibility

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Many API calls for projects | Parallelize fetches, show loading state per-section |
| Color collisions | 8 distinct colors, acceptable for typical use |
| Breaking change to navigation | Clear migration: just navigate to Sessions instead of Projects |

## Open Questions
- Should we cache the aggregated session list for faster subsequent loads?
- Consider adding a "last updated" indicator to show data freshness?
