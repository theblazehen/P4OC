---
id: opencode_android-c1h
status: closed
deps: []
links: []
created: 2026-01-30T20:52:27.764018553+02:00
type: bug
priority: 2
---
# SSE response body not closed on error - resource leak

In OpenCodeEventSource.kt:68-89, when SSE connection fails or throws exception, response body may not be properly closed. Need response.close() before throwing.


