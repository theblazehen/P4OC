---
id: opencode_android-9ap
status: closed
deps: []
links: []
created: 2026-01-30T20:52:30.252110383+02:00
type: bug
priority: 2
---
# PtyWebSocketClient race condition in connect() - not thread-safe

In PtyWebSocketClient.kt:63-72, connect() has check-then-act race. Multiple threads could both see null and create duplicate connections. Need mutex.


