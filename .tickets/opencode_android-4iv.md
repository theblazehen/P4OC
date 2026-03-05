---
id: opencode_android-4iv
status: closed
deps: []
links: []
created: 2026-01-30T20:52:57.559197603+02:00
type: bug
priority: 3
---
# API: Duplicate getAgents/listAgents methods for same endpoint

In OpenCodeApi.kt:239-243, both getAgents() and listAgents() point to GET /agent. Consolidate to single method.


