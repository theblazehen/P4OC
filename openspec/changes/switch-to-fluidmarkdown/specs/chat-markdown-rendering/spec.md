## ADDED Requirements

### Requirement: Streaming Markdown Rendering
The chat UI SHALL render markdown content from LLM responses using a streaming-aware renderer that handles incremental updates without full re-renders.

#### Scenario: Heading rendering
- **WHEN** markdown contains headings (H1-H6)
- **THEN** headings render with distinct sizes and do not affect subsequent text sizing

#### Scenario: Table rendering
- **WHEN** markdown contains a table
- **THEN** the table renders with visible borders, aligned columns, and readable content

#### Scenario: Code block rendering
- **WHEN** markdown contains fenced code blocks
- **THEN** code renders with monospace font, syntax highlighting, and distinct background

#### Scenario: Streaming text updates
- **WHEN** a text part is streaming (`isStreaming = true`)
- **THEN** new content appends incrementally without re-rendering existing content

#### Scenario: Height change notification
- **WHEN** rendered markdown content height changes during streaming
- **THEN** the component notifies the parent via callback for scroll coordination

### Requirement: Theme Integration
The markdown renderer SHALL use Material 3 theme colors and typography for consistent visual styling.

#### Scenario: Color scheme mapping
- **WHEN** the app theme changes (light/dark mode)
- **THEN** markdown text, links, code blocks, and tables update to match the new color scheme

#### Scenario: Typography mapping
- **WHEN** markdown renders body text
- **THEN** text uses the app's configured body text size and line height

### Requirement: Scroll Coordination
The chat scroll behavior SHALL respond to content height changes during streaming to keep the latest content visible.

#### Scenario: Auto-scroll during streaming
- **WHEN** a message is streaming AND user has not scrolled away
- **THEN** the view scrolls to keep the latest content visible as height increases

#### Scenario: Scroll interruption respected
- **WHEN** user scrolls up during streaming
- **THEN** auto-scroll stops and does not resume until user returns to bottom
