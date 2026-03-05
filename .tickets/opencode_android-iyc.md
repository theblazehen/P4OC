---
id: opencode_android-iyc
status: closed
deps: []
links: []
created: 2026-01-30T20:53:29.638333312+02:00
type: feature
priority: 3
---
# Hardcoded strings throughout UI - no i18n support

All user-facing strings are hardcoded in Kotlin. Extract to strings.xml for internationalization support.

## Notes

Large refactoring task - 71+ hardcoded Text() calls need extraction to strings.xml. Recommend doing incrementally per-screen.


