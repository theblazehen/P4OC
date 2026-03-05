---
id: oa-47su
status: closed
deps: []
links: []
created: 2026-03-05T13:44:05Z
type: bug
priority: 2
assignee: Jasmin Le Roux
tags: [ui, ai-feel, p2]
---
# Shape inconsistencies: rounded shapes instead of TUI RectangleShape

Convention is RectangleShape/TuiShapes but these use Material rounded shapes: SkillsScreen.kt:265,293, AgentsConfigScreen.kt:291, VisualSettingsScreen.kt:467,478, ModelAgentSelector.kt:232,440, SyntaxHighlighter.kt:454,520, TuiComponents.kt:744.

