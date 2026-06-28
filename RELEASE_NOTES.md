### Added
- Added in-chat message search with next/previous result navigation.
- Added session-list search, including server-backed lookup for sessions beyond the currently loaded list.
- Added OLED black mode for dark themes.
- Added chat display preferences for Enter-to-send, auto-scroll behavior, and directory upload visibility.

### Fixed
- Recovered pending question prompts when opening or reconnecting to a session.
- Recovered pending tool permission prompts after missed events, reconnects, or app/session restoration.
- Cleared question and permission prompts when they are resolved remotely.
- Sent Skip actions to the OpenCode server instead of clearing question prompts locally only.
- Fixed workspace repository handoff so tabs do not lose their active session repository.
- Included worktree and forked sessions in project-scoped session pickers.
- Tolerated OpenCode sync mirror events so live updates keep flowing.
- Kept session search scoped to the selected project while preserving fast local filtering.

### Verification
- Added regression coverage for question recovery, permission recovery, and server-backed session search.
- Verified debug compile, focused session-search tests, full unit tests, and detekt locally.

**Full Changelog**: https://github.com/theblazehen/P4OC/compare/v0.12.0...v0.13.0
