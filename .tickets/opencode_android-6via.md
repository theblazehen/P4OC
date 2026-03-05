---
id: opencode_android-6via
status: closed
deps: []
links: []
created: 2026-01-30T22:39:54.605852472+02:00
type: task
priority: 4
---
# Migrate java.util.Date to kotlinx.datetime in ProjectsScreen

ProjectsScreen.kt uses java.util.Date and SimpleDateFormat while the rest of the codebase uses kotlinx.datetime. Migrate to kotlinx.datetime.Instant for consistency.


