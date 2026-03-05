---
id: opencode_android-nit
status: closed
deps: []
links: []
created: 2026-01-30T20:53:16.349248012+02:00
type: bug
priority: 3
---
# SessionDao has duplicate queries - getAllSessions and getActiveSessions identical

In SessionDao.kt:9-13, getAllSessions() and getActiveSessions() have identical queries. getActiveSessions should have filter condition or be removed.


