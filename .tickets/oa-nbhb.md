---
id: oa-nbhb
status: closed
deps: []
links: [oa-orcl]
created: 2026-03-05T13:43:26Z
type: bug
priority: 1
assignee: Jasmin Le Roux
tags: [ui, ai-feel, p1]
---
# Agent toggles do nothing server-side

AgentsConfigScreen.kt:100-111 — toggleAgent() only calls _state.update, no API call. User sees switch animate, nothing persists. Either wire to server API or remove Switch and show read-only status.

