# Message Streaming Capability

## Overview
Real-time message streaming from OpenCode server via SSE events with Map-based storage for crash-free UI updates.

## ADDED Requirements

### Requirement: Map-Based Message Storage
The chat system SHALL store messages in a Map keyed by message ID to prevent duplicate entries.

#### Scenario: Message upsert on SSE event
- GIVEN a chat session is active
- WHEN a `message.updated` SSE event arrives
- THEN the message is inserted if new OR updated if existing (UPSERT)
- AND no duplicate messages exist in the collection

#### Scenario: Part upsert on SSE event
- GIVEN a chat session is active
- WHEN a `message.part.updated` SSE event arrives
- THEN the part is inserted into the message if new OR updated if existing
- AND if the message doesn't exist, a placeholder message is created first

### Requirement: SSE as Single Source of Truth
The chat UI SHALL receive all message updates exclusively through SSE events, not API responses.

#### Scenario: User sends message
- GIVEN a user has typed a message
- WHEN the user taps send
- THEN the input is cleared and sending indicator shown
- AND the API call is made but its response is NOT used to update the message list
- AND the user message appears when SSE `message.updated` event arrives

#### Scenario: Assistant responds
- GIVEN a user message was sent
- WHEN the assistant generates a response
- THEN text appears incrementally via SSE `message.part.updated` events with delta
- AND the message is marked complete when `message.updated` with completion time arrives

### Requirement: Streaming Text Display
The chat UI SHALL display streaming text incrementally as delta events arrive.

#### Scenario: Text streams in
- GIVEN an assistant message is being generated
- WHEN `message.part.updated` events arrive with `delta` field
- THEN the delta text is appended to the existing part text
- AND the UI updates to show the growing text in real-time

#### Scenario: Part without delta replaces existing
- GIVEN an assistant message has a tool part
- WHEN `message.part.updated` arrives without delta (full replacement)
- THEN the existing part is replaced with the new part data

## MODIFIED Requirements

### Requirement: Initial Message Loading
The chat system SHALL load existing messages once on session open, with SSE handling all subsequent updates.

#### Scenario: Open existing session
- GIVEN a session with existing messages
- WHEN the chat screen opens
- THEN messages are fetched via API and populated into the map
- AND subsequent updates come via SSE events only

## Related Capabilities
- `chat-session`: Session management and lifecycle
- `sse-events`: SSE event source and connection management
