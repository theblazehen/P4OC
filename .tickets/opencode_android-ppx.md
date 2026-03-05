---
id: opencode_android-ppx
status: closed
deps: []
links: []
created: 2026-01-30T20:52:04.404596464+02:00
type: bug
priority: 0
---
# TerminalViewModel memory leak - terminalEmulators map never cleared in onCleared()

In TerminalViewModel.kt:43, terminalEmulators map grows unbounded and is never cleared in onCleared(). Can hold Context references and cause memory leaks.


