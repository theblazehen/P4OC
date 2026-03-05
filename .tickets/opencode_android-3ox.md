---
id: opencode_android-3ox
status: closed
deps: []
links: []
created: 2026-01-30T20:52:47.443230714+02:00
type: bug
priority: 2
---
# Parts ordered by String ID instead of creation order

In MessageDao.kt:31-35, parts ORDER BY id ASC uses String comparison. UUIDs give arbitrary order, not insertion order. Add createdAt or position column.


