# Capability: Chat Scroll Behavior

## ADDED Requirements

### Requirement: Auto-Scroll During Streaming
The chat screen SHALL automatically scroll to show new content as it arrives, but only when the user is positioned at or near the bottom of the message list.

#### Scenario: User at bottom receives new content
- **WHEN** the user is viewing the bottom of the chat (last 2 messages visible)
- **AND** new message content arrives via SSE streaming
- **THEN** the view automatically scrolls to keep the latest content visible

#### Scenario: User scrolled up receives new content
- **WHEN** the user has scrolled up to read earlier messages
- **AND** new message content arrives via SSE streaming
- **THEN** the view does NOT auto-scroll
- **AND** the user's scroll position is preserved

### Requirement: User Scroll Detection
The system SHALL detect when the user manually scrolls away from the bottom and disable auto-scroll until the user returns to the bottom.

#### Scenario: User scrolls up during streaming
- **WHEN** the assistant is streaming a response
- **AND** the user performs a scroll gesture upward
- **THEN** auto-scroll is immediately disabled
- **AND** the user can freely read earlier content

#### Scenario: User manually scrolls back to bottom
- **WHEN** auto-scroll has been disabled due to user scroll
- **AND** the user manually scrolls back to the bottom of the chat
- **THEN** auto-scroll is re-enabled
- **AND** new content will again trigger automatic scrolling

### Requirement: Jump to Bottom Button
The system SHALL display a floating button that allows the user to quickly return to the bottom of the chat when they have scrolled away during an active streaming session.

#### Scenario: Button appears when scrolled away during streaming
- **WHEN** the user has scrolled away from the bottom
- **AND** the assistant is actively streaming a response
- **THEN** a "Jump to Bottom" button appears
- **AND** the button is positioned above the input bar

#### Scenario: Button hidden when at bottom
- **WHEN** the user is at the bottom of the chat
- **THEN** the "Jump to Bottom" button is NOT displayed

#### Scenario: Button hidden when not streaming
- **WHEN** no assistant response is being streamed
- **AND** the user scrolls up to read history
- **THEN** the "Jump to Bottom" button is NOT displayed

#### Scenario: Tapping jump button scrolls to bottom
- **WHEN** the user taps the "Jump to Bottom" button
- **THEN** the chat smoothly scrolls to the bottom
- **AND** auto-scroll is re-enabled
- **AND** the button disappears

### Requirement: Near-Bottom Threshold
The system SHALL use a threshold to determine "near bottom" status, allowing for small scroll offsets without disabling auto-scroll.

#### Scenario: Small scroll offset maintains auto-scroll
- **WHEN** the user's view is within 2 items of the last message
- **AND** new content arrives
- **THEN** auto-scroll remains active
- **AND** the view scrolls to show new content

#### Scenario: Large scroll offset disables auto-scroll
- **WHEN** the user's view is more than 2 items away from the last message
- **AND** the user initiated this scroll
- **THEN** auto-scroll is disabled
