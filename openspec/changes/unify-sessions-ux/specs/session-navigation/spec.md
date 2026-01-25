# Session Navigation

## ADDED Requirements

### Requirement: Unified Sessions View
The system SHALL display a unified sessions list after successful server connection, aggregating sessions from all sources (global and all projects) sorted by most recent activity.

#### Scenario: Initial navigation after connection
- **WHEN** user successfully connects to a server
- **THEN** the app navigates directly to the unified Sessions screen
- **AND** the Sessions screen becomes the navigation root

#### Scenario: Session aggregation
- **WHEN** the unified Sessions screen loads
- **THEN** the system fetches global sessions and all project sessions in parallel
- **AND** merges all sessions into a single list
- **AND** sorts the list by `updatedAt` timestamp descending (most recent first)

#### Scenario: Loading state
- **WHEN** sessions are being fetched from multiple sources
- **THEN** a loading indicator is displayed
- **AND** partial results MAY be shown as they become available

---

### Requirement: Project Chip Display
The system SHALL display a colored project chip on session cards to indicate which project each session belongs to.

#### Scenario: Session with project
- **WHEN** a session belongs to a project
- **THEN** a chip with the project name is displayed on the session card
- **AND** the chip has a color derived from the project ID

#### Scenario: Global session
- **WHEN** a session does not belong to any project (global session)
- **THEN** no project chip is displayed on the session card

#### Scenario: Color consistency
- **WHEN** displaying project chips
- **THEN** the same project ID always produces the same chip color
- **AND** colors are selected from a curated 8-color palette

---

### Requirement: Project Chip Navigation
The system SHALL navigate to a project-filtered sessions view when user taps a project chip.

#### Scenario: Tap project chip
- **WHEN** user taps a project chip on a session card
- **THEN** the app navigates to a filtered view showing only that project's sessions
- **AND** the filtered view displays the project name as the title
- **AND** a back button is shown in the top bar

#### Scenario: Back from filtered view
- **WHEN** user taps the back button in the project-filtered sessions view
- **THEN** the app navigates back to the unified Sessions screen

---

### Requirement: Projects Screen Navigation
The system SHALL allow navigation to the Projects screen from both unified and filtered session views.

#### Scenario: Navigate to projects from unified view
- **WHEN** user taps the folder icon in the unified Sessions screen top bar
- **THEN** the app navigates to the Projects screen

#### Scenario: Navigate to projects from filtered view
- **WHEN** user taps the folder icon in the project-filtered Sessions screen top bar
- **THEN** the app navigates to the Projects screen

#### Scenario: Back from projects screen
- **WHEN** user taps the back button in the Projects screen
- **THEN** the app navigates back to the Sessions screen (unified view)

---

### Requirement: New Session Project Selection
The system SHALL allow users to select a project when creating a new session, defaulting to global.

#### Scenario: Create global session (default)
- **WHEN** user opens the new session dialog
- **THEN** the project selection defaults to "Global" (no project)
- **AND** creating a session without changing the selection creates a global session

#### Scenario: Create project session
- **WHEN** user opens the new session dialog
- **AND** selects a project from the dropdown
- **AND** confirms session creation
- **THEN** the new session is created in the context of the selected project
