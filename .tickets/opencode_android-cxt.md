---
id: opencode_android-cxt
status: closed
deps: []
links: []
created: 2026-01-30T20:53:05.417806349+02:00
type: bug
priority: 2
---
# Unmanaged CoroutineScopes in singletons - potential leaks

OpenCodeEventSource.kt:40, PtyWebSocketClient.kt:35, MessageRepositoryImpl.kt:101 create CoroutineScope with SupervisorJob but scopes are never cancelled. Need cleanup methods tied to lifecycle.


