---
id: opencode_android-5a6
status: closed
deps: []
links: []
created: 2026-01-30T22:12:40.160564637+02:00
type: bug
priority: 3
---
# Force unwrap (!!) in SessionListScreen.kt buildSessionTree

Line 253 uses !! on parentID after filtering for non-null. While safe due to filter, should use mapNotNull for clarity.


