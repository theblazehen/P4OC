---
id: opencode_android-65z
status: closed
deps: []
links: []
created: 2026-01-30T20:53:20.156818866+02:00
type: bug
priority: 2
---
# DataStore cache updated before edit completes - inconsistency on failure

In SettingsDataStore.kt:172-184, cache is updated BEFORE DataStore edit completes. If edit fails, cache is inconsistent with persisted data. Update after success.


