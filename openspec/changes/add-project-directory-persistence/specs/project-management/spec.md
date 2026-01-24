## ADDED Requirements

### Requirement: Directory Persistence
The system SHALL persist the user's selected project directory across app restarts using local storage.

#### Scenario: Directory persisted on selection
- **WHEN** user selects a project from ProjectsScreen
- **THEN** the project's worktree path is saved to DataStore
- **AND** DirectoryManager is updated with the new directory

#### Scenario: Directory restored on app launch
- **WHEN** app launches with a previously persisted directory
- **AND** the directory matches an existing project from the server
- **THEN** DirectoryManager is initialized with the persisted directory
- **AND** app navigates directly to that project's session list

### Requirement: Directory Validation
The system SHALL validate the persisted directory against the server's project list on app startup.

#### Scenario: Persisted directory is valid
- **WHEN** app connects to server
- **AND** persisted worktree matches a project from `/project` API
- **THEN** app auto-navigates to that project's sessions

#### Scenario: Persisted directory is invalid
- **WHEN** app connects to server
- **AND** persisted worktree does NOT match any project
- **THEN** persisted directory is cleared
- **AND** app navigates to ProjectsScreen for selection

### Requirement: First Launch Behavior
The system SHALL show the project selector on first launch when no directory is persisted.

#### Scenario: First launch
- **WHEN** app connects for the first time (no persisted directory)
- **THEN** app navigates to ProjectsScreen
- **AND** user selects a project before viewing sessions

### Requirement: Global Fallback
The system SHALL use the server's default context when no projects exist.

#### Scenario: No projects available
- **WHEN** `/project` API returns empty list
- **THEN** DirectoryManager uses `null` directory
- **AND** app navigates to SessionListScreen (global context)

### Requirement: Project Switching
The system SHALL allow users to switch projects, updating persistence and reloading sessions.

#### Scenario: User switches project
- **WHEN** user navigates to ProjectsScreen
- **AND** user taps a different project
- **THEN** new project's worktree is persisted
- **AND** DirectoryManager is updated
- **AND** app navigates to new project's sessions
