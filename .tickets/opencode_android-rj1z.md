---
id: opencode_android-rj1z
status: closed
deps: []
links: []
created: 2026-01-31T18:23:12.687827071+02:00
type: bug
priority: 1
---
# Fix markdown streaming finalization - last words getting cut off

Fixed race condition in StreamingMarkdown where final text updates could be dropped when streaming ends. Added isStreaming to LaunchedEffect keys to ensure final text is always captured.


