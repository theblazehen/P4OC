# Pocket Code - Implementation Specification

> Native Android client for OpenCode AI agent with Termux integration

## Project Overview

**App Name:** Pocket Code  
**Package:** `com.pocketcode`  
**Min SDK:** 26 (Android 8.0)  
**Target SDK:** 35  
**Language:** Kotlin 2.0  
**UI:** Jetpack Compose + Material 3  
**Architecture:** MVVM + Clean Architecture + Repository Pattern

---

## Core Features

### 1. Server Connection Modes
- **Local (Termux):** Run OpenCode server on-device via Termux
- **Remote:** Connect to OpenCode server on LAN/internet

### 2. Chat Interface
- Session management (list, create, fork, delete)
- Message streaming with SSE
- Markdown rendering
- Code syntax highlighting
- Tool execution cards (pending/running/completed/error)
- Permission approval dialogs
- Question dialogs (interactive LLM questions)
- Branching conversations

### 3. Terminal Integration
- Execute commands via Termux RUN_COMMAND intent
- View command output
- Start/stop OpenCode server
- PTY-based terminal emulation

### 4. File Operations
- File explorer tree view
- File content viewing with syntax highlighting
- Diff viewer (side-by-side)
- Search (text, files, symbols)

---

## Project Structure

```
app/src/main/java/com/pocketcode/
├── PocketCodeApp.kt                    # Application class with Hilt
├── MainActivity.kt                     # Single activity entry point
│
├── di/                                 # Dependency Injection
│   ├── AppModule.kt
│   ├── NetworkModule.kt
│   ├── DatabaseModule.kt
│   └── RepositoryModule.kt
│
├── core/                               # Core infrastructure
│   ├── network/
│   │   ├── OpenCodeApi.kt              # Retrofit API interface
│   │   ├── OpenCodeEventSource.kt      # SSE client for real-time events
│   │   ├── ApiResult.kt
│   │   ├── ConnectionState.kt
│   │   └── PtyWebSocketClient.kt
│   │
│   ├── database/
│   │   ├── PocketCodeDatabase.kt
│   │   ├── Converters.kt
│   │   ├── dao/                        # SessionDao, MessageDao, ServerConfigDao
│   │   └── entity/                     # SessionEntity, MessageEntity, etc.
│   │
│   ├── datastore/
│   │   └── SettingsDataStore.kt
│   │
│   └── termux/
│       ├── TermuxBridge.kt
│       ├── TermuxResultService.kt
│       └── TermuxConstants.kt
│
├── domain/                             # Domain layer
│   ├── model/                          # See "Domain Models" section
│   └── repository/                     # Repository interfaces
│
├── data/                               # Data layer
│   ├── remote/
│   │   ├── dto/Dtos.kt                 # All API DTOs
│   │   └── mapper/Mappers.kt           # DTO <-> Domain mappers
│   └── repository/                     # Repository implementations
│
├── terminal/                           # Terminal emulation
│   ├── PtyTerminalClient.kt
│   └── WebSocketTerminalOutput.kt
│
└── ui/                                 # UI layer
    ├── theme/
    ├── navigation/
    ├── components/
    │   ├── chat/
    │   ├── question/                   # QuestionDialog.kt
    │   └── ...
    └── screens/
        ├── setup/
        ├── server/
        ├── sessions/
        ├── chat/
        ├── terminal/
        ├── files/
        └── settings/
```

---

## Domain Models

All domain models are defined in `app/src/main/java/com/pocketcode/domain/model/`.

### Core Types

| File | Types | Description |
|------|-------|-------------|
| `Session.kt` | `Session`, `SessionSummary`, `FileDiff`, `SessionRevert`, `Project`, `VcsInfo`, `PathInfo` | Session and project management |
| `Message.kt` | `Message.User`, `Message.Assistant`, `ModelRef`, `TokenUsage`, `MessageError`, `ApiError`, `MessageWithParts`, `MessageSummary`, `MessagePath` | Chat messages |
| `Part.kt` | 12 part types (see below), `PartTime`, `FilePartSource`, `AgentPartSource` | Message content parts |
| `ToolState.kt` | `ToolState.Pending`, `.Running`, `.Completed`, `.Error` | Tool execution states |
| `Event.kt` | `OpenCodeEvent` (28 event types), `SessionStatus` | SSE events |
| `Permission.kt` | `Permission` | Tool permission requests |
| `Question.kt` | `QuestionRequest`, `Question`, `QuestionOption`, `QuestionReply`, `QuestionData` | Interactive questions |
| `Provider.kt` | `Provider`, `Model` | AI provider configuration |
| `FileNode.kt` | `FileNode` | File tree structure |
| `SearchResult.kt` | `SearchResult` | Text search results |
| `ServerConfig.kt` | `ServerConfig` | Server connection config |

### Part Types (12 total)

Defined in `Part.kt`:
- `Text` - Text content with streaming support
- `Reasoning` - Model reasoning/thinking
- `Tool` - Tool execution with state
- `File` - File/image attachments
- `Patch` - Code patches
- `StepStart` - Agentic step start
- `StepFinish` - Agentic step completion
- `Snapshot` - File system snapshots
- `Agent` - Agent switching
- `Retry` - Retry attempts
- `Compaction` - Session compaction
- `Subtask` - Subtask delegation

### Event Types (28 total)

Defined in `Event.kt`:
- Message: `MessageUpdated`, `MessagePartUpdated`, `MessageRemoved`, `PartRemoved`
- Session: `SessionCreated`, `SessionUpdated`, `SessionDeleted`, `SessionStatusChanged`, `SessionDiff`, `SessionError`, `SessionCompacted`, `SessionIdle`
- Permission: `PermissionRequested`, `PermissionReplied`
- Question: `QuestionAsked`
- Todo: `TodoUpdated`
- Command: `CommandExecuted`
- File: `FileEdited`, `FileWatcherUpdated`
- VCS: `VcsBranchUpdated`
- Installation: `InstallationUpdated`, `InstallationUpdateAvailable`
- LSP: `LspClientDiagnostics`, `LspUpdated`
- PTY: `PtyCreated`, `PtyUpdated`, `PtyExited`, `PtyDeleted`
- Server: `ServerInstanceDisposed`
- Connection: `Connected`, `Disconnected`, `Error`

---

## API Layer

### OpenCode API

Defined in `core/network/OpenCodeApi.kt`. Key endpoints:

| Category | Endpoints |
|----------|-----------|
| Health | `GET /global/health` |
| Sessions | `GET/POST/DELETE/PATCH /session`, `/session/{id}/abort`, `/session/{id}/fork` |
| Messages | `GET/POST /session/{id}/message`, `/session/{id}/prompt_async` |
| Permissions | `POST /session/{id}/permissions/{permissionId}` |
| Questions | `POST /session/{id}/questions/{questionId}` |
| Files | `GET /file`, `/file/content`, `/file/status` |
| Search | `GET /find`, `/find/file` |
| Config | `GET/PATCH /config`, `/config/providers` |
| Agents | `GET /agent` |
| PTY | `GET/POST/DELETE /pty`, `/pty/{id}/resize` |

### SSE Events

Defined in `core/network/OpenCodeEventSource.kt`. Connects to `/global/event` for real-time updates.

### DTOs and Mappers

- DTOs: `data/remote/dto/Dtos.kt`
- Mappers: `data/remote/mapper/Mappers.kt`

---

## Key Implementation Files

### Chat System
- `ui/screens/chat/ChatScreen.kt` - Main chat UI
- `ui/screens/chat/ChatViewModel.kt` - Chat state management
- `ui/components/chat/ChatMessage.kt` - Message rendering
- `ui/components/chat/ChatInputBar.kt` - Input field
- `ui/components/chat/PermissionDialog.kt` - Permission approval
- `ui/components/question/QuestionDialog.kt` - Interactive questions

### Terminal
- `ui/screens/terminal/TerminalScreen.kt` - Terminal UI
- `ui/screens/terminal/TerminalViewModel.kt` - Terminal state
- `terminal/PtyTerminalClient.kt` - PTY client
- `core/termux/TermuxBridge.kt` - Termux IPC

### Navigation
- `ui/navigation/Screen.kt` - Route definitions
- `ui/navigation/NavGraph.kt` - Navigation graph

---

## Implementation Status

### Completed
- [x] Project setup and structure
- [x] Core network layer (API, SSE, WebSocket)
- [x] Domain models (all 12 Part types, 28 Event types)
- [x] Database layer (Room)
- [x] Repository pattern
- [x] Chat UI with message streaming
- [x] Permission dialogs
- [x] Question dialogs
- [x] Terminal integration (PTY + Termux)
- [x] File explorer
- [x] Session management

### In Progress
- [ ] Offline support
- [ ] Theme customization
- [ ] Performance optimization

### Future
- [ ] Voice input
- [ ] Widget support
- [ ] Wear OS companion

---

## Related Documentation

- `QUESTION_IMPLEMENTATION.md` - Question tool implementation details
- `OPENCODE_OVERVIEW.md` - OpenCode server API reference

## External Resources

- [OpenCode Documentation](https://opencode.ai/docs/)
- [OpenCode SDK](https://opencode.ai/docs/sdk/)
- [OpenCode Server API](https://opencode.ai/docs/server/)
- [Termux RUN_COMMAND Intent](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material 3 Components](https://m3.material.io/components)

---

*Last updated: January 2026*
