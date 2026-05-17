### Fixed
- Restored saved self-signed/insecure certificate settings, username, and stored password into the server connection form after app recreation.
- Persisted the selected agent per OpenCode session and restored the agent's configured model after process death or screen recreation.
- Updated the selected model immediately when switching agents, preventing stale manually selected models from overriding agent defaults.
- Hid stale project entries automatically when their saved worktree can no longer be listed through the server file API.
- Fixed explicit HTTP/HTTPS server URL normalization so Tailscale and HTTPS URLs without ports use their standard ports.

### Verification
- Added regression coverage for agent/model synchronization and HTTP/HTTPS URL normalization.
- Verified agent/model restoration and stale project filtering on-device with UIAutomator XML inspection.

**Full Changelog**: https://github.com/theblazehen/P4OC/compare/v0.11.0...v0.11.1
