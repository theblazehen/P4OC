---
id: opencode_android-wr31
status: closed
deps: []
links: []
created: 2026-01-30T22:27:34.742663936+02:00
type: task
priority: 3
---
# Extract hardcoded colors to theme constants

FileExplorerScreen.kt and GitScreen.kt have ~30 hardcoded Color(0x...) values for git status and file type indicators. Extract to theme for consistency and dark mode support.


