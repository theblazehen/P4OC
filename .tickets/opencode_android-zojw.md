---
id: opencode_android-zojw
status: closed
deps: []
links: []
created: 2026-01-30T22:39:55.947976331+02:00
type: task
priority: 4
---
# Migrate java.text.SimpleDateFormat in MessageBranching to kotlinx.datetime

MessageBranching.kt:433 uses java.text.SimpleDateFormat while the rest of the codebase uses kotlinx.datetime. Migrate for consistency and better API.


