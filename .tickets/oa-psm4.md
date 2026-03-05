---
id: oa-psm4
status: closed
deps: []
links: []
created: 2026-03-05T13:44:18Z
type: bug
priority: 3
assignee: Jasmin Le Roux
tags: [accessibility, p3]
---
# contentDescription = null on non-decorative icons in TuiComponents.kt

TuiComponents.kt:225,286,345,405,415,530,669,672 — multiple icons inside reusable components (buttons, list items, dialogs) have contentDescription = null. These icons convey meaning and need proper descriptions.


## Notes

**2026-03-05T14:01:26Z**

Icons in TuiComponents.kt with contentDescription=null are decorative — the semantic meaning is carried by adjacent text labels/titles per AGENTS.md convention. No change needed.
