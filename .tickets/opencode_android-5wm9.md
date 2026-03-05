---
id: opencode_android-5wm9
status: closed
deps: []
links: []
created: 2026-01-30T22:37:26.141420352+02:00
type: bug
priority: 3
---
# Use safe cast for Activity context in Theme.kt

Theme.kt:60 uses unsafe cast 'view.context as Activity'. This can crash if the context is a ContextThemeWrapper. Use safe cast with 'as?' and findActivity() extension.


