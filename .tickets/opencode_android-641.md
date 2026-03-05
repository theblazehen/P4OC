---
id: opencode_android-641
status: closed
deps: []
links: []
created: 2026-01-30T20:52:42.696061784+02:00
type: bug
priority: 2
---
# Missing indices on SessionEntity - slow queries

In SessionEntity.kt, no indices on projectID or updatedAt columns. Queries with ORDER BY updatedAt DESC cause full table scans. Add Index annotations.


