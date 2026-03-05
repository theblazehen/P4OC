---
id: opencode_android-sob
status: closed
deps: []
links: []
created: 2026-01-30T20:52:53.679922337+02:00
type: bug
priority: 1
---
# API: setActiveModel sends raw String instead of JSON object

In OpenCodeApi.kt:245-246, setActiveModel(@Body modelId: String) sends raw string. Server expects JSON object like {modelId: '...'}. Need SetActiveModelRequest DTO wrapper.


