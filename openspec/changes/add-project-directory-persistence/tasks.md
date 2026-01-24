## 1. DataStore Setup

- [x] 1.1 Add DataStore dependency to build.gradle.kts (already present)
- [x] 1.2 Add `projectWorktree` key to `SettingsDataStore.kt`
- [x] 1.3 DataStore already provided via Hilt module

## 2. DirectoryManager Persistence

- [x] 2.1 Inject SettingsDataStore into DirectoryManager
- [x] 2.2 Add `suspend fun loadPersistedDirectory(): String?`
- [x] 2.3 Add `suspend fun setDirectoryAndPersist(worktree: String?)`
- [x] 2.4 Update `clearDirectory()` to also clear persisted value

## 3. Startup Initialization

- [x] 3.1 Add `initializeProjectContext()` to ServerViewModel
- [x] 3.2 After connection: fetch projects, load persisted worktree
- [x] 3.3 Validate worktree against project list
- [x] 3.4 Set DirectoryManager if valid
- [x] 3.5 Determine navigation destination (projects vs sessions vs global)

## 4. Navigation Updates

- [x] 4.1 Update ServerScreen to use `NavigationDestination` sealed class
- [x] 4.2 Add conditional navigation: ProjectsScreen vs SessionsFiltered vs Sessions
- [x] 4.3 Pass auto-selected projectId when navigating to sessions

## 5. ProjectsScreen Enhancements

- [ ] 5.1 Add session count per project (deferred - nice to have)
- [x] 5.2 Persist worktree on project selection via `selectProject()`
- [x] 5.3 Update DirectoryManager on selection
- [x] 5.4 Navigate to SessionsFiltered after selection
- [x] 5.5 Make back button optional (null when coming from Server)

## 6. SessionList Updates

- [x] 6.1 Directory is set by ServerViewModel before navigation
- [x] 6.2 SessionListViewModel already uses directoryManager.getDirectory()

## 7. Testing

- [ ] 7.1 Fresh install → ProjectsScreen shown
- [ ] 7.2 App restart with valid project → auto-navigate to sessions
- [ ] 7.3 App restart with deleted project → ProjectsScreen shown
- [ ] 7.4 Project switch → sessions reload correctly
- [ ] 7.5 No projects → global context used
