---
id: opencode_android-97v
status: closed
deps: []
links: []
created: 2026-01-30T22:10:51.125791412+02:00
type: bug
priority: 2
---
# Force unwrap (!!) in InlineDiffViewer.kt

Lines 199 and 265 use !! on expandedFile and diffContent which could cause crashes.


