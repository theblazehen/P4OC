---
id: oa-orcl
status: closed
deps: []
links: [oa-nbhb]
created: 2026-03-05T13:43:27Z
type: bug
priority: 1
assignee: Jasmin Le Roux
tags: [ui, ai-feel, p1]
---
# Skill toggles do nothing server-side

SkillsScreen.kt:102-113 — toggleSkill() only calls _state.update, no API call. Same as agents — purely cosmetic interaction. Either wire to server API or remove Switch and show read-only status.

