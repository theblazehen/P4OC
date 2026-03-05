---
id: oa-i7ba
status: closed
deps: []
links: []
created: 2026-02-22T11:57:11Z
type: task
priority: 1
assignee: Jasmin Le Roux
---
# Logging safety: gate HTTP logging + AppLog wrapper

## Problem

Production builds dump ALL HTTP request/response bodies to Logcat via `HttpLoggingInterceptor.Level.BODY` in `ConnectionManager.kt:109`. This includes auth headers (Basic auth credentials), chat messages, code content, and API payloads. The SSE client also logs at `HEADERS` level (`ConnectionManager.kt:125`), leaking auth headers.

Additionally, 72 `Log.d/v/i` calls across production code are never gated by `BuildConfig.DEBUG`.

## What To Do

### 1. Gate HTTP logging in ConnectionManager.kt
- Line 108-110: Change `level = HttpLoggingInterceptor.Level.BODY` to `level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE`
- Line 124-125: Change `level = HttpLoggingInterceptor.Level.HEADERS` to `level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE`
- Add `import dev.blazelight.p4oc.BuildConfig`

### 2. Create AppLog wrapper
New file: `app/src/main/java/dev/blazelight/p4oc/core/log/AppLog.kt`
- `AppLog.d(tag, msg)` / `AppLog.v(tag, msg)` / `AppLog.i(tag, msg)` — no-op in release (check `BuildConfig.DEBUG`)
- `AppLog.w(tag, msg)` / `AppLog.e(tag, msg, throwable?)` — always log (errors are useful in prod)
- Use lambda message variants to avoid string interpolation cost: `AppLog.d(tag) { "expensive $computation" }`

### 3. Replace all Log.* calls with AppLog
Files with Log calls to replace (72 total):
- `ChatViewModel.kt` — part IDs, message IDs, delta lengths
- `OpenCodeEventSource.kt` — SSE connection/parsing logs  
- `PtyWebSocketClient.kt` — WebSocket message content (truncated)
- `ServerViewModel.kt` — server URLs, connection status
- `TerminalViewModel.kt` — terminal session logs
- `SessionListViewModel.kt` — session counts
- `TermuxTerminalView.kt` — terminal view logs
- `PtyTerminalClient.kt` — PTY client logs
- `MainTabScreen.kt` — tab management logs
- `NotificationEventObserver.kt` — notification logs
- `Mappers.kt` — mapper error logs

## Acceptance Criteria
- [ ] `HttpLoggingInterceptor` level is `NONE` in release builds
- [ ] All 72 `Log.d/v/i` calls replaced with `AppLog` equivalents
- [ ] `AppLog.w` and `AppLog.e` still log in release
- [ ] `./gradlew :app:compileDebugKotlin` passes
- [ ] No remaining direct `android.util.Log` imports in production code (except AppLog itself)

## Acceptance Criteria

HTTP logging gated behind BuildConfig.DEBUG. All Log.d/v/i replaced with AppLog. Compiles clean.

