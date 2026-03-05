---
id: oa-2afu
status: closed
deps: []
links: [oa-az3q, oa-v5os]
created: 2026-03-05T13:43:15Z
type: bug
priority: 0
assignee: Jasmin Le Roux
tags: [background, sse, p0]
---
# Error state escalation too aggressive

MainTabScreen.kt:84-93 waits only 15s then hard disconnects. OpenCodeEventSource.kt:258,271 only 5 consecutive errors before Disconnected. Background + doze = 5 errors in seconds leading to permanent disconnect. Should increase tolerance and reset error count on foreground resume.

