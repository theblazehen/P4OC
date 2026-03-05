---
id: opencode_android-a1d
status: closed
deps: []
links: []
created: 2026-01-30T20:52:38.313550425+02:00
type: bug
priority: 2
---
# TermuxBridge.checkStatus() ignores CountDownLatch timeout result

In TermuxBridge.kt:206, latch.await(5, SECONDS) return value is ignored. If timeout occurs, reports OpenCodeNotInstalled even if just slow. Should return Unknown on timeout.


