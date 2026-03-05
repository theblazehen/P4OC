---
id: opencode_android-ax2t
status: closed
deps: []
links: []
created: 2026-01-30T22:37:27.604016078+02:00
type: bug
priority: 3
---
# Use firstOrNull for safer list access in ChatMessage.kt

ChatMessage.kt:337 uses part.files.first() which will throw NoSuchElementException if files is empty. Use firstOrNull with null-safe access.


