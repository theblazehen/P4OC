## ADDED Requirements

### Requirement: Collapsed Tool Summary
Tool calls within a message SHALL be displayed in a collapsed one-liner format by default.

#### Scenario: Multiple completed tools collapsed
- **WHEN** an assistant message contains multiple tool calls
- **THEN** they SHALL be displayed as a single line like `✓ Read ×3 | ✓ Edit ×2`
- **AND** tools SHALL be grouped by name with occurrence counts

#### Scenario: Mixed status tools
- **WHEN** tools have different states (running, completed, error)
- **THEN** status SHALL be indicated with icons: ✓ (complete), ⟳ (running), ✗ (error), ○ (pending)

### Requirement: Expandable Tool Details
The collapsed tool summary SHALL expand to show full details on user tap.

#### Scenario: Expand tool details
- **WHEN** user taps the collapsed tool summary
- **THEN** full tool details (name, description, output preview) SHALL be shown
- **AND** a collapse control SHALL be available to return to summary view

### Requirement: Pending Tool Approval
Pending tools requiring approval SHALL display Allow/Deny buttons.

#### Scenario: Pending tool in collapsed view
- **WHEN** a tool is in pending state requiring approval
- **THEN** the collapsed summary SHALL show ○ indicator
- **AND** expanding SHALL reveal Allow/Deny buttons with reduced padding
