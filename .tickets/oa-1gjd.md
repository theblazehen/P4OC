---
id: oa-1gjd
status: closed
deps: []
links: []
created: 2026-03-05T13:44:05Z
type: bug
priority: 2
assignee: Jasmin Le Roux
tags: [i18n, ai-feel, p2]
---
# Visual Settings screen uses hardcoded English instead of string resources

VisualSettingsScreen.kt lines 184-246: 15 hardcoded strings (Theme, Text, Code Display, etc.) have matching string resources in strings.xml:178-205 that are not being used. Pure oversight.

