---
id: oa-az3q
status: closed
deps: []
links: [oa-2afu, oa-v5os]
created: 2026-03-05T13:43:14Z
type: bug
priority: 0
assignee: Jasmin Le Roux
tags: [background, sse, p0]
---
# Foreground resume reconnect has dead isConnected guard

MainTabScreen.kt:63 — the isConnected check is always false by the time user returns from background because the cascade already fired. The reconnect in repeatOnLifecycle(STARTED) never runs. Fix: remove the isConnected check, always attempt reconnect() on resume.

