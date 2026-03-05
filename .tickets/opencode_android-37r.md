---
id: opencode_android-37r
status: closed
deps: []
links: []
created: 2026-01-30T20:52:11.064531033+02:00
type: bug
priority: 1
---
# Schema export disabled - prevents proper Room migrations

In PocketCodeDatabase.kt:22, exportSchema=false prevents generating schema JSON for migrations. This makes database upgrades risky and untestable.


