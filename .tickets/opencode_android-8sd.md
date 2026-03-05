---
id: opencode_android-8sd
status: closed
deps: []
links: []
created: 2026-01-30T20:52:45.153720521+02:00
type: bug
priority: 2
---
# Fragile delimiter-based serialization in SettingsDataStore

In SettingsDataStore.kt:225-256 and 306-329, recent servers/models use ||| and ::: delimiters. If URL contains these chars, parsing breaks silently. Use JSON serialization.


