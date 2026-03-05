---
id: opencode_android-eef
status: closed
deps: []
links: []
created: 2026-01-19T16:58:28.876453846+02:00
type: bug
priority: 1
---
# Remote server URL ignored - connects to localhost instead of entered URL

When entering a remote server URL (e.g. http://192.168.24.25:4096) and tapping Connect, the app attempts to connect to localhost:4096 instead. Error shown: 'Failed to connect to localhost/[::1]:4096'. The entered URL is displayed correctly in the UI but not used for the actual connection.


