---
id: opencode_android-1nm
status: closed
deps: []
links: []
created: 2026-01-30T22:10:49.509814036+02:00
type: bug
priority: 2
---
# Force unwrap (!!) in FileViewerScreen.kt can crash on race condition

Lines 77 and 87 use !! on fileContent and error which could crash if state changes between check and access.


