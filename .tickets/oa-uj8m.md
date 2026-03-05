---
id: oa-uj8m
status: closed
deps: []
links: []
created: 2026-03-05T13:44:05Z
type: bug
priority: 2
assignee: Jasmin Le Roux
tags: [ui, ai-feel, p2]
---
# MaterialTheme.colorScheme.primary used instead of LocalOpenCodeTheme

TuiComponents.kt:481 uses MaterialTheme.colorScheme.primary — should be theme.accent or similar per convention.

