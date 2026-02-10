# Spec: Tab System

## Overview

The tab system provides a top-level workspace management UI for the app. Users can have multiple tabs open, each containing an independent navigation stack. Tabs can show any screen type (Sessions, Chat, Files, Terminal, Settings).

## ADDED Requirements

### Requirement: Tab Container

The app SHALL display a tab bar at the top of the screen (below system status bar) with a content area below showing the active tab's content.

#### Scenario: App launch with fresh state
- **Given** the app is launched for the first time (or after data clear)
- **When** the main screen is displayed
- **Then** one tab exists showing SessionListScreen
- **And** the tab bar shows one tab indicator labeled "Sessions"
- **And** no [+] button animation plays

#### Scenario: Tab bar layout
- **Given** the app is running
- **When** viewing any screen
- **Then** the tab bar is visible at the top (~28dp height)
- **And** tab indicators are scrollable horizontally if they overflow
- **And** the [+] button is always visible at the right end of the tab bar

---

### Requirement: Tab Creation

The system SHALL allow users to create new tabs via the [+] button, with new tabs starting at SessionListScreen.

#### Scenario: Create new tab via [+] button
- **Given** the user is viewing any screen in any tab
- **When** the user taps the [+] button in the tab bar
- **Then** a new tab is created
- **And** the new tab shows SessionListScreen
- **And** the new tab becomes the active tab
- **And** the new tab appears at the right end of the tab list (before [+])

#### Scenario: Performance warning at 5+ tabs
- **Given** the user has 4 tabs open
- **When** the user creates a 5th tab
- **Then** a warning message is shown: "Multiple tabs may affect performance"
- **And** the tab is still created
- **And** the warning is only shown once per app session

---

### Requirement: Tab Switching

The system SHALL allow users to switch between tabs by tapping tab indicators.

#### Scenario: Switch to another tab
- **Given** the user has multiple tabs open
- **And** Tab A is active showing ChatScreen
- **And** Tab B exists showing FileExplorerScreen
- **When** the user taps Tab B's indicator
- **Then** Tab B becomes active
- **And** Tab B's content (FileExplorerScreen) is displayed
- **And** Tab A's state is preserved (scroll position, input text, etc.)

#### Scenario: Return to previously active tab
- **Given** the user switched from Tab A to Tab B
- **When** the user taps Tab A's indicator
- **Then** Tab A is displayed with its previous state intact

---

### Requirement: Tab Closing

The system SHALL allow users to close tabs via the close (×) button on tab indicators, maintaining at least one tab.

#### Scenario: Close a tab
- **Given** the user has 3 tabs open (A, B, C)
- **And** Tab B is active
- **When** the user taps the × button on Tab B
- **Then** Tab B is removed
- **And** Tab A or Tab C becomes active (previous tab preferred)
- **And** the tab list shows only A and C

#### Scenario: Close the last remaining tab
- **Given** the user has only 1 tab open
- **When** the user taps the × button on that tab
- **Then** a new tab is created showing SessionListScreen
- **And** the original tab is removed
- **And** effectively the tab is "replaced" with a fresh Sessions tab

#### Scenario: Close inactive tab
- **Given** Tab A is active, Tab B exists
- **When** the user taps × on Tab B (inactive)
- **Then** Tab B is removed
- **And** Tab A remains active
- **And** no content switch occurs

---

### Requirement: Per-Tab Navigation

Each tab SHALL have an independent navigation stack, with back button navigating within the active tab.

#### Scenario: Navigate within a tab
- **Given** a tab is showing SessionListScreen
- **When** the user taps a session
- **Then** the same tab navigates to ChatScreen for that session
- **And** the tab indicator updates to show the session title

#### Scenario: Back navigation within tab
- **Given** a tab navigated from SessionListScreen to ChatScreen
- **When** the user presses the back button
- **Then** the tab navigates back to SessionListScreen
- **And** no tab is closed

#### Scenario: Back at root of tab
- **Given** a tab is showing SessionListScreen (root)
- **When** the user presses the back button
- **Then** nothing happens (or app-level back behavior, e.g., minimize)
- **And** the tab is not closed

#### Scenario: Deep navigation within tab
- **Given** a tab navigated: Sessions → Chat → Settings → Provider Config
- **When** the user presses back repeatedly
- **Then** navigation goes: Provider Config → Settings → Chat → Sessions
- **And** all within the same tab

---

### Requirement: Session Tab Uniqueness

The system SHALL ensure only one tab can display a given chat session, focusing the existing tab when opening an already-open session.

#### Scenario: Open session that is already open in another tab
- **Given** Tab A is showing ChatScreen for session "fix-bug"
- **And** Tab B is showing SessionListScreen
- **And** Tab B is active
- **When** the user taps session "fix-bug" in SessionListScreen
- **Then** Tab A becomes active
- **And** Tab B remains on SessionListScreen (no navigation)
- **And** no duplicate tab is created

#### Scenario: Open session not currently open
- **Given** no tab is showing session "new-feature"
- **And** Tab B is active on SessionListScreen
- **When** the user taps session "new-feature"
- **Then** Tab B navigates to ChatScreen for "new-feature"
- **And** Tab B's indicator updates to show "new-feature" title

---

### Requirement: New Tab from Chat Actions

Files and Terminal buttons in ChatScreen SHALL open in new tabs.

#### Scenario: Open Files from Chat
- **Given** the user is in ChatScreen in Tab A
- **When** the user taps the Files button
- **Then** a new Tab B is created
- **And** Tab B shows FileExplorerScreen
- **And** Tab B becomes active
- **And** Tab A remains on ChatScreen (preserved)

#### Scenario: Open Terminal from Chat
- **Given** the user is in ChatScreen in Tab A
- **When** the user taps the Terminal button
- **Then** a new Tab B is created
- **And** Tab B shows TerminalScreen
- **And** Tab B becomes active

---

### Requirement: Settings Navigation

Settings SHALL navigate within the current tab rather than creating a new tab.

#### Scenario: Open Settings from SessionListScreen
- **Given** the user is in SessionListScreen in Tab A
- **When** the user taps the Settings button
- **Then** Tab A navigates to SettingsScreen
- **And** no new tab is created
- **And** back button returns to SessionListScreen

#### Scenario: Open Settings from ChatScreen
- **Given** the user is in ChatScreen in Tab A
- **When** the user opens Settings (via menu or command)
- **Then** Tab A navigates to SettingsScreen
- **And** back button returns to ChatScreen

---

### Requirement: Tab Indicator State

Tab indicators for chat sessions SHALL reflect the session's connection state visually.

#### Scenario: Chat tab shows busy state
- **Given** a tab is showing ChatScreen
- **And** the session's agent is processing (busy)
- **When** viewing the tab bar
- **Then** that tab's indicator shows a pulsing primary color dot

#### Scenario: Chat tab shows awaiting input state
- **Given** a tab is showing ChatScreen
- **And** the session has a pending question or permission
- **When** viewing the tab bar
- **Then** that tab's indicator shows a pulsing warning color dot

#### Scenario: Chat tab shows idle state
- **Given** a tab is showing ChatScreen
- **And** the session is idle (not busy, no pending input)
- **When** viewing the tab bar
- **Then** that tab's indicator shows a static muted color dot

#### Scenario: Non-chat tab indicator
- **Given** a tab is showing FileExplorerScreen
- **When** viewing the tab bar
- **Then** that tab's indicator shows no state dot (or a neutral icon)
