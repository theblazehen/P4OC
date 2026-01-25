# Tool Widgets Specification

## ADDED Requirements

### Requirement: Three-Tier Tool Display
The system SHALL display tool calls in three progressive tiers:
- **Oneline**: Minimal single-line display showing tool status icon and tool name
- **Compact**: Summary line showing tool name and brief description (~1-2 lines)
- **Expanded**: Full details with tool-specific UI (command output, file content, interactive elements)

#### Scenario: User cycles through display states
- **WHEN** user taps a tool widget in Oneline state
- **THEN** widget transitions to Compact state

#### Scenario: User expands to full details
- **WHEN** user taps a tool widget in Compact state
- **THEN** widget transitions to Expanded state

#### Scenario: User collapses expanded widget
- **WHEN** user taps a tool widget in Expanded state
- **THEN** widget transitions to Oneline state

### Requirement: HITL Tools Auto-Expand
The system SHALL automatically expand Human-In-The-Loop tools (e.g., `question`) to their Expanded state, overriding the user's default preference.

#### Scenario: Question tool appears in chat
- **WHEN** a `question` tool call is rendered in chat
- **THEN** widget displays in Expanded state with interactive UI
- **AND** user can submit responses directly within the widget

### Requirement: Global Default Display Setting
The system SHALL provide a global setting to control the default display state for non-HITL tool widgets.

#### Scenario: User changes default to Oneline
- **WHEN** user sets "Tool Call Display" to "Oneline" in Visual Settings
- **THEN** all new non-HITL tool widgets render in Oneline state by default

#### Scenario: Setting persists across sessions
- **WHEN** user sets a default display preference
- **AND** user restarts the app
- **THEN** the preference is preserved

### Requirement: Widget Height Constraints
Tool widgets SHALL respect height constraints:
- Most widgets: 75-115dp (~2-3cm) when expanded
- Large content widgets (Question, long diffs): Up to available viewport height with internal scrolling

#### Scenario: Bash command with long output
- **WHEN** a Bash widget is expanded
- **AND** stdout exceeds 115dp
- **THEN** widget caps at ~115dp with scrollable content area

#### Scenario: Question with many options
- **WHEN** a Question widget renders with 10+ options
- **THEN** widget expands up to viewport height with scrollable options list

### Requirement: Parallel Tool Calls
The system SHALL render parallel tool calls as separate, independently-expandable widgets.

#### Scenario: Three bash commands run in parallel
- **WHEN** assistant executes 3 bash commands simultaneously
- **THEN** 3 separate widgets render in chat
- **AND** each widget can be expanded/collapsed independently

### Requirement: Widget State Persistence (Session)
Widget display state SHALL persist within a session but NOT across app restarts.

#### Scenario: User scrolls away and back
- **WHEN** user expands a widget
- **AND** scrolls away from it
- **AND** scrolls back to it
- **THEN** widget remains in Expanded state

#### Scenario: App restart resets widget states
- **WHEN** user has expanded multiple widgets
- **AND** restarts the app
- **THEN** all widgets reset to default display state

### Requirement: Bash Widget Display
The Bash widget SHALL display:
- **Oneline**: Status icon + "bash"
- **Compact**: `✓ ./gradlew assembleDebug` (command text, truncated if needed)
- **Expanded**: Command with syntax highlighting + stdout/stderr preview (scrollable)

#### Scenario: Successful command
- **WHEN** bash command exits with code 0
- **THEN** widget shows success icon (green checkmark)

#### Scenario: Failed command
- **WHEN** bash command exits with non-zero code
- **THEN** widget shows error icon (red X)

### Requirement: Read Widget Display
The Read widget SHALL display:
- **Oneline**: Status icon + "read"
- **Compact**: `Read OpenCodeTheme.kt (230 lines)`
- **Expanded**: File path header + syntax-highlighted code preview (scrollable, ~100dp)

#### Scenario: File read success
- **WHEN** file is read successfully
- **THEN** Compact shows filename and line count

### Requirement: Edit Widget Display
The Edit/Write widget SHALL display:
- **Oneline**: Status icon + "edit" or "write"
- **Compact**: `Modified Theme.kt (+45, -12)`
- **Expanded**: File path header + inline diff viewer (scrollable, ~100dp)

#### Scenario: File modification
- **WHEN** file is edited
- **THEN** Compact shows filename and lines added/removed

### Requirement: Question Widget Display
The Question widget SHALL display in Expanded state only (no Oneline/Compact) with:
- Full question text
- Interactive options (radio buttons for single-select, checkboxes for multi-select)
- Text input field if custom input is allowed
- Submit button

#### Scenario: User submits answer
- **WHEN** user selects option(s) and taps Submit
- **THEN** answer is sent to the assistant
- **AND** widget transitions to completed state (non-interactive)
