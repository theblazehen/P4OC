# Change: Unify Sessions Screen with Project Aggregation

## Why
Currently, the app navigates users to either a project selector or a project-filtered sessions view based on persisted state. This creates navigation dead-ends (can't return to project selector once in a project) and fragments the session browsing experience. Users want to see all their recent work across all projects in one place.

## What Changes
- **BREAKING**: Boot/connection now navigates directly to a unified Sessions screen (not Projects)
- Sessions screen aggregates all sessions: global sessions + sessions from all projects
- Sessions sorted by most recent (updatedAt) across all sources
- Session cards display a colored project chip (tappable to filter)
- Each project gets a deterministic color from a curated palette for quick visual scanning
- Tapping a project chip navigates to project-filtered sessions view
- Project-filtered view has working back button returning to unified sessions
- New session dialog defaults to global, with option to select a project
- Remove persisted project preference logic (no longer needed)

## Impact
- Affected specs: session-navigation (new capability)
- Affected code:
  - `ServerViewModel.kt` - Remove project context initialization, simplify navigation
  - `SessionListViewModel.kt` - Add multi-source session aggregation logic
  - `SessionListScreen.kt` - Add project chips to cards, fix back navigation
  - `NavGraph.kt` - Fix navigation routes and back stack handling
  - `DirectoryManager.kt` - Remove persisted directory preference (or make optional)
  - New: `ProjectColors.kt` - Deterministic color palette for projects
