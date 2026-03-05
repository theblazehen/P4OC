---
id: opencode_android-xov
status: closed
deps: []
links: []
created: 2026-01-30T20:52:55.536027204+02:00
type: bug
priority: 1
---
# API: searchFiles uses wrong query param - type vs dirs

In OpenCodeApi.kt:217-222, searchFiles uses 'type' param but server spec uses 'dirs' param. Causes file search to not filter correctly.


