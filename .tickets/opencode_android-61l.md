---
id: opencode_android-61l
status: closed
deps: []
links: []
created: 2026-01-30T20:53:03.147495603+02:00
type: bug
priority: 2
---
# API: Missing dedicated PTY resize endpoint

Server spec has POST /pty/{id}/resize but we use PATCH /pty/{id} with size field. May not work if server expects dedicated endpoint. Verify and add if needed.


