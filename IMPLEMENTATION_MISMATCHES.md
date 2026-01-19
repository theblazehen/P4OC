# Android vs SDK Alignment Analysis

**Source of truth:** [`packages/sdk/js/src/gen/types.gen.ts`](https://github.com/anomalyco/opencode/blob/dev/packages/sdk/js/src/gen/types.gen.ts) (SDK)
**Server docs:** https://opencode.ai/docs/server/
**Android paths:** `app/src/main/java/com/pocketcode/...`

---

## Table of Contents

1. [Events](#1-events)
2. [Messages](#2-messages)
3. [Parts & Tool State](#3-parts--tool-state)
4. [Sessions](#4-sessions)
5. [Files & Search](#5-files--search)
6. [Agent & Permission](#6-agent--permission)
7. [Provider & Model](#7-provider--model)
8. [Config](#8-config)
9. [Tools (Experimental)](#9-tools-experimental)
10. [Auth](#10-auth)
11. [API Surface](#11-api-surface)
12. [Questions](#12-questions)
13. [Architectural Issues](#13-architectural-issues)
14. [Known Bugs](#14-known-bugs)

---

## 1. Events

### SDK Event Types (32 total in union)
```typescript
Event = EventServerInstanceDisposed | EventInstallationUpdated | EventInstallationUpdateAvailable
      | EventLspClientDiagnostics | EventLspUpdated | EventMessageUpdated | EventMessageRemoved
      | EventMessagePartUpdated | EventMessagePartRemoved | EventPermissionUpdated | EventPermissionReplied
      | EventSessionStatus | EventSessionIdle | EventSessionCompacted | EventFileEdited | EventTodoUpdated
      | EventCommandExecuted | EventSessionCreated | EventSessionUpdated | EventSessionDeleted
      | EventSessionDiff | EventSessionError | EventFileWatcherUpdated | EventVcsBranchUpdated
      | EventTuiPromptAppend | EventTuiCommandExecute | EventTuiToastShow | EventPtyCreated
      | EventPtyUpdated | EventPtyExited | EventPtyDeleted | EventServerConnected
```

### Mismatches

| Issue | SDK | Android | Location | Priority |
|-------|-----|---------|----------|----------|
| Missing TUI events | `tui.prompt.append`, `tui.command.execute`, `tui.toast.show` defined | No DTOs or domain events | `Event.kt`, `Dtos.kt` | Low (TUI-specific) |
| `server.connected` payload | `properties: { [key: string]: unknown }` | `OpenCodeEvent.Connected` has no payload | `Event.kt:166` | Medium |
| Android-only events | N/A | `Disconnected`, `Error` defined for client-side connection state | `Event.kt` | OK (intentional) |
| `QuestionAsked` event | Not in SDK Event union | Android emits `OpenCodeEvent.QuestionAsked` | `Event.kt:157` | See [Questions](#12-questions) |
| `permission.updated` vs `permission.requested` | SDK uses `permission.updated` | Android maps to `PermissionRequested` | `Mappers.kt` | OK (semantic rename) |

---

## 2. Messages

### SDK Types
```typescript
UserMessage = {
  id, sessionID, role: "user", time: { created: number },
  summary?: { title?, body?, diffs: Array<FileDiff> },  // diffs REQUIRED in summary
  agent, model: { providerID, modelID }, system?, tools?
}

AssistantMessage = {
  id, sessionID, role: "assistant", time: { created, completed? },
  error?: ProviderAuthError | UnknownError | MessageOutputLengthError | MessageAbortedError | ApiError,
  parentID, modelID, providerID, mode, path: { cwd, root },
  summary?: boolean,  // boolean, not object
  cost, tokens: { input, output, reasoning, cache: { read, write } }, finish?
}

ApiError.data = { message, statusCode?, isRetryable, responseHeaders?, responseBody? }
```

### Mismatches

| Issue | SDK | Android | Location | Priority |
|-------|-----|---------|----------|----------|
| `UserMessage.summary.diffs` | Required `Array<FileDiff>` | Mapper sets to empty list, ignores SDK diffs | `Mappers.kt:mapMessageSummaryToDomain` | High |
| `AssistantMessage.summary` | `boolean \| undefined` | Parsed from `JsonElement`, nullable Boolean | `Mappers.kt:mapToDomain` | Medium |
| `ApiError.responseHeaders` | `{ [key: string]: string }` | Not mapped in `MessageError` | `Message.kt`, `Mappers.kt` | Medium |
| `ApiError.responseBody` | `string` | Not mapped in `MessageError` | `Message.kt`, `Mappers.kt` | Medium |
| Error union types | 5 distinct error types with `name` discriminator | Flattened to single `MessageError` | `Message.kt` | High |
| `AssistantMessage.mode` | Required `string` | Android has `agent` field (SDK: User has agent) | `Message.kt` | Medium |

---

## 3. Parts & Tool State

### SDK Part Types (12 in union)
```typescript
Part = TextPart | SubtaskPart | ReasoningPart | FilePart | ToolPart 
     | StepStartPart | StepFinishPart | SnapshotPart | PatchPart 
     | AgentPart | RetryPart | CompactionPart
```

### SDK ToolState Types
```typescript
ToolStatePending  = { status: "pending", input: {}, raw: string }
ToolStateRunning  = { status: "running", input: {}, title?, metadata?, time: { start } }  // time REQUIRED
ToolStateCompleted = { status: "completed", input: {}, output, title, metadata, time: { start, end, compacted? }, attachments?: FilePart[] }
ToolStateError    = { status: "error", input: {}, error, metadata?, time: { start, end } }  // time REQUIRED
```

### Mismatches

| Issue | SDK | Android | Location | Priority |
|-------|-----|---------|----------|----------|
| `RetryPart.time` | `{ created: number }` | Uses `PartTimeDto` with `start/end/compacted`, maps `createdAt` from `time.start` | `Mappers.kt`, `Part.kt` | High |
| `RetryPart.error` | Required `ApiError` type | Android `error` is nullable `ApiError?` | `Part.kt:Retry` | Medium |
| `ToolStateRunning.time` | Required `{ start: number }` | `ToolStateDto.time` is nullable | `Dtos.kt` | High |
| `ToolStateCompleted.title` | Required `string` | Optional in `ToolStateDto` | `Dtos.kt` | Medium |
| `ToolStateCompleted.metadata` | Required `{}` | Optional in `ToolStateDto` | `Dtos.kt` | Medium |
| `ToolStateCompleted.attachments` | `Array<FilePart>` | `List<Part>` (any part type) | `ToolState.kt` | Medium |
| `ToolStateError.time` | Required `{ start, end }` | Nullable in DTO | `Dtos.kt` | High |
| `FilePart.source` | `FileSource \| SymbolSource` discriminated union | Raw `JsonObject?`, ignored in mapper | `Dtos.kt`, `Mappers.kt` | High |
| `ReasoningPart.time` | Required `{ start, end? }` | Nullable `PartTime?` | `Part.kt` | Medium |
| `StepFinishPart.tokens` | Required `TokenUsage` | Nullable in `Part.StepFinish` | `Part.kt` | Medium |
| `TextPart.time` | Optional `{ start, end? }` | Correctly optional | - | OK |

### FilePartSource (SDK)
```typescript
FileSource = { text: { value, start, end }, type: "file", path }
SymbolSource = { text: { value, start, end }, type: "symbol", path, range: Range, name, kind }
```

**Android Issue:** `PartDto.source` is `JsonObject?` and `PartMapper` ignores it entirely, losing file/symbol context.

---

## 4. Sessions

### SDK Session Type
```typescript
Session = {
  id, projectID, directory, parentID?, title, version,
  summary?: { additions, deletions, files, diffs?: FileDiff[] },
  share?: { url },
  time: { created, updated, compacting? },  // updated REQUIRED
  revert?: { messageID, partID?, snapshot?, diff? }
}

SessionStatus = { type: "idle" } | { type: "retry", attempt, message, next } | { type: "busy" }
```

### Mismatches

| Issue | SDK | Android | Location | Priority |
|-------|-----|---------|----------|----------|
| `Session.time.updated` | Required `number` | `TimeDto.updated` is nullable | `Dtos.kt` | Medium |
| Mapper fallback | N/A | Falls back to `created` if `updated` is null | `Mappers.kt` | OK (workaround) |

---

## 5. Files & Search

### SDK Types
```typescript
FileNode = { name, path, absolute, type: "file" | "directory", ignored }
FileContent = { type: "text", content, diff?, patch?: PatchObject, encoding?: "base64", mimeType? }
FindTextResponse = { path, lines: [{ text }], line_number, absolute_offset, submatches }
```

### Mismatches

| Issue | SDK | Android | Location | Priority |
|-------|-----|---------|----------|----------|
| `FileContent.patch` | Full patch object with hunks | Not in domain `FileContent` | `FileNode.kt` | Medium |
| `FileContent.encoding` | `"base64"` literal | Not in domain model | `FileNode.kt` | Low |
| `FindTextResponse.lines` | `Array<{ text: string }>` | `SearchResultDto.lines` is plain `String?` | `Dtos.kt` | High |
| `/file` path param | SDK: `path` query required | Android: nullable `path` | `OpenCodeApi.kt` | Low |
| `/find/file` params | SDK: `dirs?: "true" \| "false"` | Android: `type`, `limit` params | `OpenCodeApi.kt` | Medium |

---

## 6. Agent & Permission

### SDK Agent Type
```typescript
Agent = {
  name, description?, mode: "subagent" | "primary" | "all", builtIn,
  topP?, temperature?, color?,
  permission: {  // REQUIRED object
    edit: "ask" | "allow" | "deny",
    bash: { [pattern: string]: "ask" | "allow" | "deny" },  // Map, not single value
    webfetch?, doom_loop?, external_directory?
  },
  model?: { modelID, providerID }, prompt?,
  tools: { [key: string]: boolean },
  options: {}, maxSteps?
}

Permission = {
  id, type, pattern?: string | string[],  // string OR array
  sessionID, messageID, callID?, title,
  metadata: {},  // REQUIRED
  time: { created }  // REQUIRED
}
```

### Mismatches

| Issue | SDK | Android | Location | Priority |
|-------|-----|---------|----------|----------|
| `Agent.permission` | Required structured object | `JsonElement?`, `AgentPermissionDto` not defined | `Dtos.kt` | Critical |
| `Agent.permission.bash` | `{ [pattern]: permission }` map | Android expects string or `JsonElement` | `Provider.kt` | High |
| `Permission.pattern` | `string \| string[]` | Mapper collapses to `List<String>?` | `Mappers.kt` | Medium |
| `Permission.metadata` | Required `{}` | Nullable in DTO | `Dtos.kt` | Medium |
| `Permission.time` | Required `{ created }` | Nullable in DTO | `Dtos.kt` | Medium |
| `AgentConfigDto.permission` | References `AgentPermissionDto` | DTO not defined | `Dtos.kt` | Critical |
| `ConfigDto.permission` | References `AgentPermissionDto` | DTO not defined | `Dtos.kt` | Critical |

---

## 7. Provider & Model

### SDK Model Type
```typescript
Model = {
  id, providerID, name,
  api: { id, url, npm },  // REQUIRED
  capabilities: {  // REQUIRED
    temperature, reasoning, attachment, toolcall,
    input: { text, audio, image, video, pdf },
    output: { text, audio, image, video, pdf }
  },
  cost: { input, output, cache: { read, write }, experimentalOver200K? },  // REQUIRED
  limit: { context, output },  // REQUIRED
  status: "alpha" | "beta" | "deprecated" | "active",  // REQUIRED
  options: {},  // REQUIRED
  headers: {}   // REQUIRED
}

Provider = { id, name, source: "env" | "config" | "custom" | "api", env, key?, options: {}, models: {} }
```

### Mismatches

| Issue | SDK | Android | Location | Priority |
|-------|-----|---------|----------|----------|
| `Model.api` | Required object | Optional `ModelApiDto?` | `Dtos.kt` | High |
| `Model.capabilities` | Required object | Optional `ModelCapabilitiesDto?` | `Dtos.kt` | High |
| `Model.cost` | Required object | Optional `ModelCostDto?` | `Dtos.kt` | High |
| `Model.limit` | Required object | Optional `ModelLimitDto?` | `Dtos.kt` | High |
| `Model.status` | Required enum | Optional `String?` | `Dtos.kt` | Medium |
| `Model.options` | Required `{}` | Optional `JsonObject?` | `Dtos.kt` | Medium |
| `Model.headers` | Required `{}` | Optional `Map?` | `Dtos.kt` | Medium |
| Non-SDK fields | N/A | `contextLength`, `inputCostPer1k`, `outputCostPer1k`, `supportsTools`, `supportsReasoning` | `Dtos.kt` | Low (legacy?) |
| `cost.experimentalOver200K` | Optional nested object | Not in `ModelCostDto` | `Dtos.kt` | Low |
| `ProviderConfig.options.timeout` | `number \| false` | `Int?` only | `Dtos.kt` | Medium |
| `ProviderConfig.models.*.modalities` | Input/output modality arrays | Not in `ModelConfigDto` | `Dtos.kt` | Medium |
| `ProviderConfig.models.*.provider.npm` | Nested npm field | Not represented | `Dtos.kt` | Low |

---

## 8. Config

### SDK Config Type (partial)
```typescript
Config = {
  $schema?, theme?, keybinds?: KeybindsConfig, logLevel?,
  tui?: { scroll_speed?, scroll_acceleration?, diff_style? },
  command?: { [name]: { template, description?, agent?, model?, subtask? } },
  watcher?: { ignore? }, plugin?, snapshot?, share?, autoshare?, autoupdate?,
  disabled_providers?, enabled_providers?, model?, small_model?, username?,
  mode?, agent?: { [name]: AgentConfig },
  provider?: { [id]: ProviderConfig },
  mcp?: { [name]: McpLocalConfig | McpRemoteConfig },
  formatter?, lsp?, instructions?, layout?, permission?, tools?, enterprise?,
  experimental?: { hook?, chatMaxRetries?, disable_paste_summary?, batch_tool?, openTelemetry?, primary_tools? }
}
```

### Mismatches

| Issue | SDK | Android | Location | Priority |
|-------|-----|---------|----------|----------|
| Missing fields | `keybinds`, `tui`, `command`, `watcher`, `plugin`, `snapshot`, `layout`, most of `experimental` | Not in `ConfigDto` | `Dtos.kt` | Medium |
| `permission.doom_loop` | SDK naming | Android uses `doomLoop` | `Provider.kt` | Low |
| `permission.external_directory` | SDK naming | Android uses `externalDirectory` | `Provider.kt` | Low |

---

## 9. Tools (Experimental)

### SDK Types
```typescript
ToolListItem = { id, description, parameters: unknown }
ToolList = Array<ToolListItem>
ToolIds = Array<string>
```

### Mismatches

| Issue | SDK | Android | Location | Priority |
|-------|-----|---------|----------|----------|
| Field naming | `id` | `name` | `Dtos.kt:ToolDto` | Medium |
| Field naming | `parameters` | `inputSchema` | `Dtos.kt:ToolDto` | Medium |
| Response shape | `ToolList` is array | `ToolListDto { tools: List<ToolDto> }` wrapper | `Dtos.kt` | Medium |

---

## 10. Auth

### SDK Types
```typescript
OAuth = { type: "oauth", refresh, access, expires, enterpriseUrl? }
ApiAuth = { type: "api", key }
WellKnownAuth = { type: "wellknown", key, token }
Auth = OAuth | ApiAuth | WellKnownAuth  // Discriminated union
```

### Mismatches

| Issue | SDK | Android | Location | Priority |
|-------|-----|---------|----------|----------|
| Discriminated union | 3 separate types | Single flattened `AuthDto` with all fields optional | `Dtos.kt` | Medium |

---

## 11. API Surface

### Mismatches

| Issue | SDK | Android | Location | Priority |
|-------|-----|---------|----------|----------|
| `/model/active` | Not in SDK types | Android calls via `setActiveModel` | `OpenCodeApi.kt` | Low (may be undocumented) |
| Duplicate endpoint | One `GET /agent` | Both `getAgents` and `listAgents` | `OpenCodeApi.kt` | Low (cleanup) |
| Question endpoint | Not in SDK | `POST /session/{id}/questions/{questionId}` | `OpenCodeApi.kt` | See [Questions](#12-questions) |

---

## 12. Questions

### Status: **Not in SDK**

The SDK `types.gen.ts` has **no** `Question*` types or events. However, the Android app implements:

- `QuestionRequestDto`, `QuestionDto`, `QuestionOptionDto`, `QuestionReplyRequest` in `Dtos.kt`
- `QuestionRequest`, `Question`, `QuestionOption`, `QuestionReply` in `Question.kt`
- `OpenCodeEvent.QuestionAsked` in `Event.kt`
- `POST /session/{id}/questions/{questionId}` endpoint

**Analysis:** Questions may be delivered via tool calls in the SDK model (the `question` tool), rather than as first-class events. The Android implementation appears to be a custom extension or based on an older/internal API version.

**Recommendation:** Verify with upstream whether questions have a dedicated event channel or should be handled purely through tool call state.

---

## 13. Architectural Issues

### 13.1 Termux Dependency Chain (Critical)

**Files:** `TermuxBridge.kt`, `TermuxResultService.kt`

**Issue:** The app uses `JobIntentService` which is deprecated and broken on Android 12+ (API 31). Background service restrictions cause:
- Service fails to start or is killed immediately
- Command results (stdout/stderr) are lost
- Node.js/opencode CLI installation via shell injection is extremely brittle

**Impact:** Local server mode is unreliable on modern Android devices.

### 13.2 Streaming Recomposition Hell (High)

**Files:** `ChatViewModel.kt`, `MessageRepositoryImpl.kt`

**Issue:** `updatePart` creates a copy of the entire `List<MessageWithParts>` for every SSE token delta (20-50 times/second during generation).

**Impact:** 
- Massive UI jank during streaming
- High battery drain
- Compose recomposes entire chat list per token

**Fix:** Use `SnapshotStateList` with targeted updates, or implement diffing.

### 13.3 Base URL Interceptor Deadlock Risk (Medium)

**File:** `NetworkModule.kt`

**Issue:** `@Named("baseUrl")` interceptor reads DataStore using `runBlocking` on the OkHttp interceptor thread.

**Impact:** If DataStore read hangs, network thread pool deadlocks.

**Fix:** Cache URL in memory, update asynchronously.

---

## 14. Known Bugs

### 14.1 JSON Serialization Mismatch (High)

**File:** `AppModule.kt`

**Issue:** `explicitNulls = false` and `encodeDefaults = false` causes fields to be omitted when null. The TypeScript server may expect explicit nulls or specific defaults.

**Impact:** 400 Bad Request errors on some API calls (documented as BUG-003 in TEST_RESULTS.md).

### 14.2 Permission Race Condition (High)

**File:** `ChatViewModel.kt`

**Issue:** `pendingPermission` is a single nullable state. If two permission requests arrive rapidly:
1. Second event overwrites first
2. First tool call hangs indefinitely on server
3. UI shows wrong permission dialog

**Fix:** Use a queue or map of pending permissions.

### 14.3 Clipboard SecurityException (Medium)

**File:** `PtyTerminalClient.kt`

**Issue:** `clipboardManager.primaryClip` access fails on Android 10+ when app loses focus (split-screen, background).

**Impact:** Terminal paste crashes the session.

**Fix:** Wrap in try-catch, check focus state.

---

## Priority Summary

| Priority | Count | Categories |
|----------|-------|------------|
| Critical | 3 | AgentPermissionDto missing, Termux deprecated |
| High | 12 | ToolState time requirements, FilePart.source, Error union, Search response, Streaming perf |
| Medium | 20+ | Various field optionality, naming, missing fields |
| Low | 8 | TUI events, legacy fields, minor naming |

---

*Last updated: January 2026*
*SDK commit: dev branch*
