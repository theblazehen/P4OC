---
id: opencode_android-8cc
status: closed
deps: []
links: []
created: 2026-01-30T20:53:18.00584295+02:00
type: bug
priority: 3
---
# Silent exception swallowing in EventMapper hides bugs

In Mappers.kt:743-745, all exceptions during event mapping silently return null. Makes debugging SSE parsing issues very difficult. Add logging.


