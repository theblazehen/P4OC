---
id: opencode_android-6pnm
status: closed
deps: []
links: []
created: 2026-01-30T22:18:44.415292124+02:00
type: feature
priority: 3
---
# Use collectAsStateWithLifecycle instead of collectAsState

24 usages of collectAsState() should migrate to collectAsStateWithLifecycle() for better lifecycle awareness and preventing unnecessary updates when the app is in background.


