---
id: opencode_android-pnc
status: closed
deps: []
links: []
created: 2026-01-30T20:52:21.22770374+02:00
type: bug
priority: 1
---
# DataStore cache inconsistency - getCachedServerUrl returns stale values

In SettingsDataStore.kt:64-73, cachedServerUrl initialized to default but not synced until Flow collected. getCachedServerUrl() returns wrong values before first collection.


