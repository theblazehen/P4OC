# Multi-Session Capability

## ADDED Requirements

### Requirement: Session Connection States

The system SHALL track each session's connection state using the following states:
- **ACTIVE**: Currently viewing, SSE connected
- **BUSY**: SSE connected, agent is processing (streaming response)
- **AWAITING_INPUT**: SSE connected, waiting for user action (question or permission)
- **IDLE**: SSE connected, no activity
- **BACKGROUND**: Not connected, requires reload from server
- **ERROR**: Connection failed

#### Scenario: Session becomes busy when agent starts processing
- **GIVEN** a session in IDLE state
- **WHEN** the agent begins processing a request
- **THEN** the session state changes to BUSY

#### Scenario: Session requires input when question received
- **GIVEN** a session in BUSY state
- **WHEN** a question event is received from the server
- **THEN** the session state changes to AWAITING_INPUT

#### Scenario: Session becomes idle after response completes
- **GIVEN** a session in BUSY state
- **WHEN** the agent finishes processing
- **THEN** the session state changes to IDLE

### Requirement: Hot Session Management

The system SHALL maintain a list of "hot" sessions that have active SSE connections and can be switched between instantly without reload.

#### Scenario: Loading a session into hot list
- **GIVEN** a session that is not in the hot list
- **WHEN** the user navigates to that session or pins it
- **THEN** the session is added to the hot list and an SSE connection is established

#### Scenario: Unloading a session from hot list
- **GIVEN** a session in the hot list
- **WHEN** the user unpins it or closes it via the status bar
- **THEN** the SSE connection is closed and the session is removed from the hot list

#### Scenario: User manages hot session count
- **GIVEN** multiple sessions in the hot list
- **WHEN** the user adds or removes sessions
- **THEN** the system allows any number of hot sessions (user-managed, no artificial limit)

### Requirement: Session Status Bar

The system SHALL display a persistent status bar at the top of ChatScreen showing all hot sessions with visual state indicators and truncated names (4-6 characters).

#### Scenario: Status bar displays hot sessions
- **GIVEN** 3 sessions in the hot list
- **WHEN** the user views the ChatScreen
- **THEN** the status bar shows 3 indicators with truncated session names

#### Scenario: Indicator shows busy state with pulse
- **GIVEN** a session in BUSY state
- **WHEN** displayed in the status bar
- **THEN** the indicator pulses with an animation

#### Scenario: Indicator shows awaiting input with badge
- **GIVEN** a session in AWAITING_INPUT state
- **WHEN** displayed in the status bar
- **THEN** the indicator pulses and shows a warning badge

#### Scenario: Active session shows close button
- **GIVEN** a session is the currently active/viewed session
- **WHEN** displayed in the status bar
- **THEN** the indicator shows a close (×) button

#### Scenario: Tap indicator to switch session
- **GIVEN** multiple sessions in the status bar
- **WHEN** the user taps a non-active indicator
- **THEN** the view switches to that session

### Requirement: Priority-Based Session Ordering

The system SHALL order sessions in the status bar by priority, with sessions needing attention appearing leftmost (inbox model).

Priority order (left to right):
1. AWAITING_INPUT (highest priority)
2. BUSY
3. IDLE / ACTIVE (lowest priority)

New sessions appear on the left. Viewing a session does NOT reorder; only state changes trigger reordering.

#### Scenario: Session needing input moves to front
- **GIVEN** sessions ordered as [IDLE-A, BUSY-B, IDLE-C]
- **WHEN** session C receives a question event
- **THEN** the order becomes [AWAITING_INPUT-C, BUSY-B, IDLE-A]

#### Scenario: New session appears on left
- **GIVEN** sessions ordered as [IDLE-A, IDLE-B]
- **WHEN** a new session C is loaded
- **THEN** the order becomes [IDLE-C, IDLE-A, IDLE-B]

#### Scenario: Switching sessions does not reorder
- **GIVEN** sessions ordered as [BUSY-A, IDLE-B, IDLE-C]
- **WHEN** the user switches to session C
- **THEN** the order remains [BUSY-A, IDLE-B, IDLE-C]

### Requirement: Swipe Navigation Between Sessions

The system SHALL support horizontal swipe gestures on the chat content to switch between hot sessions.

#### Scenario: Swipe left to go to quieter session
- **GIVEN** viewing a session that is not the rightmost in the status bar
- **WHEN** the user swipes left on the chat content
- **THEN** the view switches to the next session to the right (lower priority)

#### Scenario: Swipe right to go to priority session
- **GIVEN** viewing a session that is not the leftmost in the status bar
- **WHEN** the user swipes right on the chat content
- **THEN** the view switches to the next session to the left (higher priority)

#### Scenario: Swipe at edge does nothing
- **GIVEN** viewing the leftmost session
- **WHEN** the user swipes right
- **THEN** the view bounces back and no switch occurs

### Requirement: Session Rendering Performance

The system SHALL keep multiple sessions rendered in memory for instant switching, using HorizontalPager with `beyondBoundsPageCount = 2`.

#### Scenario: Adjacent sessions stay composed
- **GIVEN** 3 hot sessions
- **WHEN** viewing the middle session
- **THEN** all 3 sessions remain composed in memory

#### Scenario: Scroll position preserved on switch
- **GIVEN** a session scrolled to a specific position
- **WHEN** the user switches away and back
- **THEN** the scroll position is preserved

### Requirement: Session Pin/Unpin in Session List

The system SHALL display a tappable pin indicator on each session card in SessionListScreen to add or remove sessions from the hot list.

#### Scenario: Hot session shows filled indicator
- **GIVEN** a session in the hot list
- **WHEN** displayed in SessionListScreen
- **THEN** the session card shows a filled pin indicator

#### Scenario: Cold session shows hollow indicator
- **GIVEN** a session not in the hot list
- **WHEN** displayed in SessionListScreen
- **THEN** the session card shows a hollow pin indicator

#### Scenario: Tap indicator to pin session
- **GIVEN** a cold session with hollow indicator
- **WHEN** the user taps the indicator
- **THEN** the session is added to the hot list (SSE connection started)

#### Scenario: Tap indicator to unpin session
- **GIVEN** a hot session with filled indicator
- **WHEN** the user taps the indicator
- **THEN** the session is removed from the hot list (SSE connection closed)

#### Scenario: Tapping session row still navigates
- **GIVEN** any session in the list
- **WHEN** the user taps the session row (not the indicator)
- **THEN** the app navigates to ChatScreen showing that session

### Requirement: Title Bar Updates on Settle

The system SHALL update the ChatScreen title bar to show the current session's title only after the pager settles on a page, not during swipe animation.

#### Scenario: Title updates after swipe completes
- **GIVEN** viewing session A with title "Project A"
- **WHEN** the user swipes to session B with title "Bug Fix"
- **THEN** the title remains "Project A" during swipe and changes to "Bug Fix" after settling
