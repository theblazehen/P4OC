---
id: opencode_android-atl
status: closed
deps: []
links: []
created: 2026-01-30T20:52:02.514054078+02:00
type: bug
priority: 0
---
# Force-unwrap (!!) on nullable timestamps in MessageRepositoryImpl causes crashes

In MessageRepositoryImpl.kt:241,247-248,257-258, force-unwraps on toolStateStartedAt!! and toolStateEndedAt!! will crash if database has null values. Need safe defaults.


