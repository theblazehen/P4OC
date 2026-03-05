---
id: oa-xxuy
status: closed
deps: []
links: []
created: 2026-03-05T13:43:16Z
type: bug
priority: 0
assignee: Jasmin Le Roux
tags: [background, terminal, p0]
---
# PtyWebSocketClient has zero reconnection logic

PtyWebSocketClient.kt:132-139 — on failure, nulls everything, no retry. Terminal sessions are irrecoverably lost on background. Need reconnect(ptyId) with backoff.

