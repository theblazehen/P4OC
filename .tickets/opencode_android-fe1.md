---
id: opencode_android-fe1
status: closed
deps: []
links: []
created: 2026-01-30T20:52:00.250905076+02:00
type: bug
priority: 0
---
# CancellationException swallowed in safeApiCall breaks structured concurrency

In ApiResult.kt:42-48, safeApiCall catches all exceptions including CancellationException. This breaks Kotlin coroutine structured concurrency and can cause coroutines to hang. Must re-throw CancellationException.


