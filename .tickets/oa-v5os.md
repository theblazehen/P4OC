---
id: oa-v5os
status: closed
deps: []
links: [oa-2afu, oa-az3q]
created: 2026-03-05T13:43:13Z
type: bug
priority: 0
assignee: Jasmin Le Roux
tags: [background, sse, p0]
---
# Disconnect kicks user to server screen instead of attempting reconnection

MainTabScreen.kt:76-81 — ConnectionState.Disconnected immediately calls onDisconnect() which navigates away. Should show a Reconnecting banner and attempt reconnection first. Only escalate after multiple failed attempts.

