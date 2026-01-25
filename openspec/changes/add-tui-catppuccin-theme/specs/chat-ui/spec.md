## ADDED Requirements

### Requirement: Compact Message Display
Messages SHALL be displayed without per-message header labels to maximize vertical density.

#### Scenario: User message without header
- **WHEN** a user message is displayed
- **THEN** no "You" label or icon SHALL appear above the message
- **AND** the message SHALL have a Surface2 background with a Mauve left border

#### Scenario: Assistant message without header  
- **WHEN** an assistant message is displayed
- **THEN** no model name label SHALL appear above the message content
- **AND** token usage info MAY appear on the first message only

### Requirement: Reduced UI Density
The chat UI SHALL use minimal padding and spacing to maximize content density.

#### Scenario: Tight vertical spacing
- **WHEN** messages are rendered in the chat list
- **THEN** vertical padding between messages SHALL be 2dp or less
- **AND** corner radii SHALL be 0dp (square corners)

### Requirement: Minimal Dividers
Message dividers SHALL be removed or made extremely subtle.

#### Scenario: No visible dividers
- **WHEN** consecutive messages are displayed
- **THEN** dividers between them SHALL either be removed or have opacity ≤ 10%
