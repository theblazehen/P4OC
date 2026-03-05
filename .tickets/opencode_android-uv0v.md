---
id: opencode_android-uv0v
status: closed
deps: []
links: []
created: 2026-01-31T21:27:53.792806554+02:00
type: task
priority: 2
---
# Keyboard closes when sending message - should stay open to allow queueing messages

When sending a message, the keyboard input closes. This happens because we disable input while processing. Instead, input should remain enabled so users can queue up additional messages while waiting for a response.


