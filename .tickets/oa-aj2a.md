---
id: oa-aj2a
status: closed
deps: []
links: []
created: 2026-03-05T13:44:05Z
type: chore
priority: 2
assignee: Jasmin Le Roux
tags: [dead-code, ai-feel, p2]
---
# VisualSettingsViewModel has 4 unused methods

VisualSettingsScreen.kt:108 updateLineSpacing(), :112 updateFontFamily(), :128 toggleCompactMode(), :132 updateMessageSpacing() — no UI controls exist for these. Either expose in UI or remove dead code.

