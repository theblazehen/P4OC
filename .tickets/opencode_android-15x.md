---
id: opencode_android-15x
status: closed
deps: []
links: []
created: 2026-01-30T20:53:14.520870009+02:00
type: bug
priority: 2
---
# SkillsViewModel/AgentsConfigViewModel toggle not persisted to server

In SkillsScreen.kt:93-104 and AgentsConfigScreen.kt:91-102, toggleSkill/toggleAgent only update local state. Changes lost on refresh. Either implement API call or mark as local-only.


