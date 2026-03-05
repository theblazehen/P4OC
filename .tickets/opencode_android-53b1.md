---
id: opencode_android-53b1
status: closed
deps: []
links: []
created: 2026-01-30T22:16:32.572385337+02:00
type: bug
priority: 2
---
# Empty catch blocks silently swallow exceptions

MessageRepositoryImpl.kt:242,259 and ModelAgentSelector.kt:35 have empty catch blocks that silently swallow exceptions. Should at minimum log the error.


