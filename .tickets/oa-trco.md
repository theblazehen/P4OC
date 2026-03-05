---
id: oa-trco
status: closed
deps: [oa-b388]
links: []
created: 2026-02-22T12:00:42Z
type: feature
priority: 1
assignee: Jasmin Le Roux
---
# SSE migration: replace hand-rolled parser with LaunchDarkly okhttp-eventsource

## Problem

The app hand-rolls an SSE parser in `OpenCodeEventSource.kt` (189 lines) despite having `com.launchdarkly:okhttp-eventsource:4.2.0` already as a dependency. The hand-rolled version:
- Only handles `data:` lines (ignores `event:`, `id:`, `retry:` per SSE spec)
- Uses fixed 3-second reconnect delay (no exponential backoff/jitter)
- Drops events silently under load (`BufferOverflow.DROP_OLDEST` with buffer of 64)
- Doesn't track `lastEventId` or send `Last-Event-ID` on reconnect
- Doesn't handle multiline `data:` fields correctly (concatenates without newline)

LaunchDarkly's library is well-maintained (166 stars, 204 commits), spec-compliant, handles reconnection with backoff, and already ships in the APK unused.

## What To Do

### 1. Understand current OpenCodeEventSource interface
Current public API used by consumers:
```kotlin
class OpenCodeEventSource(...) {
    val events: SharedFlow<OpenCodeEvent>  // consumers collect this
    val connectionState: StateFlow<ConnectionState>  // Connected/Disconnected/Reconnecting/Error
    fun connect(baseUrl: String)  // starts SSE connection to /global/event
    fun shutdown()  // stops and prevents reconnect
    fun reconnect()  // manual reconnect trigger
}
```

Consumers: `ConnectionManager.kt` creates it, `ChatViewModel` + other VMs collect `events`.

### 2. Rewrite OpenCodeEventSource using LaunchDarkly EventSource
- Use `com.launchdarkly.eventsource.EventSource` as the underlying SSE client
- Implement `EventHandler` interface: `onOpen`, `onMessage`, `onComment`, `onError`, `onClosed`
- In `onMessage`: parse the event data JSON and emit to `_events: MutableSharedFlow<OpenCodeEvent>`
- The library handles: reconnection with backoff, `Last-Event-ID`, full SSE spec parsing
- Configure via `EventSource.Builder`:
  - Set OkHttp client (pass existing SSE OkHttpClient from ConnectionManager)
  - Set initial reconnect delay
  - Set max reconnect time
  - Set read timeout (0 for SSE)

### 3. Preserve the existing public interface
- Keep `events: SharedFlow<OpenCodeEvent>` — same flow, same event types
- Keep `connectionState: StateFlow<ConnectionState>` — map library lifecycle callbacks to existing states
- Keep `connect(baseUrl)`, `shutdown()`, `reconnect()` methods
- Keep the event parsing logic (`parseAndEmitEvent`) — the JSON→domain mapping stays the same
- Consumers should not need ANY changes

### 4. Handle auth
Current: OkHttpClient has auth interceptor already. The LaunchDarkly EventSource can use the same OkHttpClient, so auth is inherited.

### 5. Buffer strategy
- Consider increasing buffer from 64 (or make it configurable)
- The library's built-in reconnection reduces the likelihood of buffer overflow
- Consider logging when events are dropped (if `tryEmit` returns false)

## Key Files
- `app/src/main/java/dev/blazelight/p4oc/core/network/OpenCodeEventSource.kt` — rewrite
- `app/src/main/java/dev/blazelight/p4oc/core/network/ConnectionManager.kt` — may need minor changes to how EventSource is created
- `app/src/main/java/dev/blazelight/p4oc/data/remote/mapper/Mappers.kt` — EventMapper stays as-is

## What NOT to change
- Event types / domain model (OpenCodeEvent sealed class)
- EventMapper (JSON parsing stays the same)
- Any ViewModel event handling code
- The SharedFlow consumers

## Acceptance Criteria
- [ ] `OpenCodeEventSource` uses LaunchDarkly `EventSource` internally
- [ ] SSE spec fully supported (event/id/retry/multiline data)
- [ ] Reconnection uses exponential backoff with jitter
- [ ] `Last-Event-ID` sent on reconnect
- [ ] Public interface unchanged (events flow, connectionState, connect/shutdown/reconnect)
- [ ] Auth works (inherited from OkHttpClient)
- [ ] No consumer code changes needed
- [ ] Dropped events logged (when tryEmit returns false)
- [ ] `./gradlew :app:compileDebugKotlin` passes
- [ ] SSE connection establishes and events flow correctly

## Acceptance Criteria

OpenCodeEventSource uses LaunchDarkly library. Spec compliant. Backoff reconnect. Public API unchanged. Compile clean.

