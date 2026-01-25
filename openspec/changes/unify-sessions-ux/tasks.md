# Tasks: Unify Sessions UX

## 1. Project Colors Utility
- [x] 1.1 Create `ui/theme/ProjectColors.kt` with 8-color palette
- [x] 1.2 Add `colorForProject(projectId: String): Color` function using hash-based selection
- [x] 1.3 Add unit test for color determinism (same ID = same color)

## 2. Session Card UI Updates
- [x] 2.1 Add `projectId` and `projectName` fields to session UI state/model
- [x] 2.2 Create `ProjectChip` composable with colored background and project name
- [x] 2.3 Update `SessionCard` to show chip inline with timestamp (right-aligned)
- [x] 2.4 Make chip tappable with `onProjectClick: (projectId: String) -> Unit` callback
- [x] 2.5 Hide chip when `projectId` is null (global sessions)

## 3. Session Aggregation Logic
- [x] 3.1 Update `SessionListViewModel` to fetch global sessions + all projects in parallel
- [x] 3.2 For each project, fetch its sessions (parallel coroutines)
- [x] 3.3 Merge all sessions into single list with project metadata attached
- [x] 3.4 Sort merged list by `updatedAt` descending
- [x] 3.5 Expose loading state per-source (optional: show partial results as they arrive)

## 4. Navigation Flow Changes
- [x] 4.1 Update `ServerViewModel.initializeProjectContext()` to always navigate to Sessions
- [x] 4.2 Remove persisted project auto-navigation logic
- [x] 4.3 Update `NavGraph.kt`: Server → Sessions with `popUpTo { inclusive = true }`
- [x] 4.4 Update `NavGraph.kt`: SessionsFiltered back button → navigate to Sessions (not popBackStack)
- [x] 4.5 Update `NavGraph.kt`: Projects back button → navigate to Sessions
- [x] 4.6 Wire up chip tap in SessionListScreen to navigate to SessionsFiltered

## 5. Session List Screen UI Updates
- [x] 5.1 Always show folder icon in top bar (remove `filterProjectId == null` condition)
- [x] 5.2 Pass `onProjectClick` callback through to session cards
- [x] 5.3 Update title: "Sessions" for unified view, project name for filtered view
- [x] 5.4 Ensure back button appears in filtered view and navigates correctly

## 6. New Session Dialog Updates
- [x] 6.1 Add project picker dropdown to new session dialog
- [x] 6.2 Default selection to "Global" (null project)
- [x] 6.3 Populate dropdown with fetched projects list
- [x] 6.4 Pass selected project to session creation

## 7. Cleanup
- [x] 7.1 Remove or deprecate `DirectoryManager.loadPersistedDirectory()` usage in ServerViewModel
- [x] 7.2 Remove `NavigationDestination.Sessions(projectId, worktree)` variant if no longer needed
- [ ] 7.3 Update any tests affected by navigation changes (pre-existing test issues found)
- [ ] 7.4 Manual testing: verify all navigation paths work correctly
