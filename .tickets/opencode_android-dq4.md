---
id: opencode_android-dq4
status: closed
deps: []
links: []
created: 2026-01-30T20:52:06.825449441+02:00
type: bug
priority: 0
---
# ChatViewModel race condition in message updates - parts can be lost

In ChatViewModel.kt:212-246, upsertPart() has read-modify-write pattern on SnapshotStateMap that is not atomic. If two SSE events arrive rapidly for same message, parts can be lost. Need mutex.


