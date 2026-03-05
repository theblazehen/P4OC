---
id: opencode_android-ag2
status: closed
deps: []
links: []
created: 2026-01-30T20:52:25.507624717+02:00
type: bug
priority: 1
---
# ChatViewModel force non-null on SavedStateHandle - crashes if nav arg missing

In ChatViewModel.kt:53, force non-null assertion (!!) on SavedStateHandle navigation argument. Crashes if navigated to without sessionId.


