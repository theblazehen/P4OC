---
id: opencode_android-wty
status: closed
deps: []
links: []
created: 2026-01-30T20:52:40.686830035+02:00
type: bug
priority: 2
---
# TerminalView client not updated on recomposition - stale callbacks

In TermuxTerminalView.kt:179-227, terminalViewClient is set in factory but NOT in update lambda. If onKeyInput changes, old callback is still used.


