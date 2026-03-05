---
id: opencode_android-xnp
status: closed
deps: []
links: []
created: 2026-01-30T22:10:52.240795215+02:00
type: bug
priority: 2
---
# Force unwrap (!!) in CommandPalette.kt

Lines 76 and 81 use !! on selectedCommand which could crash if dialog state changes.


