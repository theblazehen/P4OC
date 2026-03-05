---
id: opencode_android-9eh
status: closed
deps: []
links: []
created: 2026-01-30T20:52:09.305100351+02:00
type: bug
priority: 1
---
# TermuxResultService callback memory leak - callbacks never cleaned up on timeout

In TermuxResultService.kt:26, callbacks in static ConcurrentHashMap are only removed on result. If Termux fails to respond, callbacks leak forever. Need timeout cleanup.


