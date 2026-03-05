---
id: opencode_android-txt
status: closed
deps: []
links: []
created: 2026-01-30T20:52:23.530258939+02:00
type: bug
priority: 1
---
# Password not saved in saveLastConnection() - breaks auto-reconnect with auth

In SettingsDataStore.kt:190-202, saveLastConnection() saves username but NOT password. Auto-reconnect fails for password-protected servers.


