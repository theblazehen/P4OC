---
id: opencode_android-9nb
status: closed
deps: []
links: []
created: 2026-01-30T20:53:11.999671178+02:00
type: bug
priority: 2
---
# ServerViewModel stores password in UI state - security concern

In ServerViewModel.kt:257-258, ServerUiState stores password as plain String. UI state can be logged/serialized. Keep password in separate non-data-class holder.


