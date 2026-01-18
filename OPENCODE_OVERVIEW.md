# OpenCode Implementation Guide
## Comprehensive Overview of OpenCode-Related Patterns, File Structures, and Implementations

---

## Directory Status
**Current Directory:** `/home/jasmin/Projects/android/opencode_android`
**Status:** Empty directory - This is a fresh project location
**Next Steps:** Initialize an Android app implementation for OpenCode

---

## OpenCode Architecture Overview

OpenCode is an open-source AI coding agent built on a **client/server architecture**:

- **Server Component:** Runs headless HTTP server exposing OpenAPI endpoints
- **Client Component:** TUI (Terminal User Interface), CLI, Desktop App, or Mobile App
- **Communication:** REST API with Server-Sent Events (SSE) for real-time streaming
- **Language:** Primarily TypeScript
- **Repository:** https://github.com/anomalyco/opencode

---

## 1. Core Type Definitions

### Session Type
```typescript
interface Session {
  id: string;                    // Unique session identifier
  title: string;                 // Session title
  createdAt: string;              // ISO 8601 timestamp
  updatedAt: string;              // ISO 8601 timestamp
  status?: SessionStatus;          // Current status
  messages?: Message[];            // Associated messages
  metadata?: SessionMetadata;       // Additional metadata
}
```

### Message Type (Discriminated Union)
```typescript
type Message = 
  | UserMessage
  | AssistantMessage
  | SystemMessage;

interface UserMessage {
  id: string;
  type: 'user';
  role: 'user';
  content: string;
  timestamp: string;
}

interface AssistantMessage {
  id: string;
  type: 'assistant';
  role: 'assistant';
  content: Part[];  // Parts array for multi-modal content
  timestamp: string;
}
```

### Part Types (Content Units)
```typescript
type Part = 
  | TextPart
  | ReasoningPart
  | ToolPart
  | FilePart
  | AgentPart
  | StepStartPart
  | StepFinishPart
  | SnapshotPart
  | PatchPart;

// Text Content
interface TextPart {
  type: 'text';
  text: string;
  synthetic?: boolean;  // AI-generated vs real content
  ignored?: boolean;    // Should be ignored in rendering
}

// Model Reasoning/Thinking
interface ReasoningPart {
  type: 'reasoning';
  text: string;
  time?: number;       // Time spent reasoning
}

// Tool Execution
interface ToolPart {
  type: 'tool';
  tool: string;          // Tool name
  callID: string;        // Unique call identifier
  state: ToolState;      // State machine status
}

// File/Image Attachments
interface FilePart {
  type: 'file';
  mime: string;          // MIME type
  url: string;           // File URL
  source: 'user' | 'system';  // Upload source
}

// Agent Switching
interface AgentPart {
  type: 'agent';
  name: string;          // Agent name
  source: string;        // Source of agent
}

// Agentic Step Start
interface StepStartPart {
  type: 'stepStart';
  snapshot?: FileSnapshot;  // File system snapshot
}

// Agentic Step Finish
interface StepFinishPart {
  type: 'stepFinish';
  reason: string;        // Completion reason
  tokens: number;         // Tokens used
  cost: number;          // Cost in USD
}
```

---

## 2. HTTP API Endpoints

### Global Endpoints
```
GET  /global/health       # Server health and version
GET  /global/event        # SSE event stream
```

### Session Management
```
GET    /session                    # List all sessions
POST   /session                    # Create new session
GET    /session/:id                # Get session details
DELETE /session/:id                # Delete session
PATCH  /session/:id                # Update session properties
GET    /session/:id/children       # Get child sessions
POST   /session/:id/fork          # Fork session at message
POST   /session/:id/abort         # Abort running session
POST   /session/:id/share         # Share session
DELETE /session/:id/share         # Unshare session
GET    /session/:id/diff          # Get diff for session
POST   /session/:id/summarize     # Summarize session
```

### Message Operations
```
GET  /session/:id/message         # List messages in session
POST /session/:id/message         # Send message and wait for response
POST /session/:id/prompt_async   # Send asynchronously (no wait)
POST /session/:id/command        # Execute slash command
POST /session/:id/shell          # Run shell command
GET  /session/:id/message/:messageId  # Get message details
```

### File Operations
```
GET  /find?pattern=<regex>          # Search text in files
GET  /find/file?query=<q>         # Find files by name
GET  /find/symbol?query=<q>       # Find workspace symbols
GET  /file?path=<p>               # List files/directories
GET  /file/content?path=<p>         # Read file
GET  /file/status                   # Get tracked file status
```

### Tools & Permissions
```
GET  /experimental/tool/ids          # List all tool IDs
GET  /experimental/tool             # List tools with schemas
POST /session/:id/permissions/:permissionID  # Respond to permission request
```

### Configuration
```
GET    /config                    # Get config
PATCH  /config                    # Update config
GET    /config/providers           # List providers and models
POST   /auth/:id                 # Set auth credentials
```

---

## 3. Server-Sent Events (SSE) Implementation

### SSE Event Format
```
event: message_type
id: message_id
data: {"key": "value"}
```

### Standard SSE Headers
```
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive
```

### SSE Event Types
```typescript
// Connection Events
type ServerConnectedEvent = {
  event: 'server.connected';
  data: {
    version: string;
    hostname: string;
    port: number;
  }
};

// Message Events
type MessageEvent = {
  event: 'message.created' | 'message.updated';
  data: {
    sessionId: string;
    messageId: string;
    parts: Part[];
  }
};

// Tool Execution Events
type ToolEvent = {
  event: 'tool.call' | 'tool.result';
  data: {
    tool: string;
    callID: string;
    status: 'running' | 'completed' | 'failed';
  }
};

// Session Events
type SessionEvent = {
  event: 'session.created' | 'session.updated' | 'session.deleted';
  data: Session;
};
```

### Android SSE Client (OkHttp + OkSse)
```kotlin
// Build OkSse client
val client = OkHttpClient.Builder()
    .readTimeout(0, TimeUnit.SECONDS)  // Infinite timeout for SSE
    .build()

val okSse = OkSse(client)

// Connect to SSE endpoint
val eventSource = okSse.newServerSentEvent(
    Request.Builder()
        .url("http://localhost:4096/global/event")
        .addHeader("Accept", "text/event-stream")
        .build(),
    object : EventSourceListener {
        override fun onOpen(eventSource: EventSource) {
            Log.d("SSE", "Connection opened")
        }

        override fun onComment(comment: String) {
            Log.d("SSE", "Comment: $comment")
        }

        override fun onMessage(event: String, messageEvent: MessageEvent) {
            val eventType = messageEvent.event
            val data = messageEvent.data
            
            when (eventType) {
                "message.created" -> handleMessage(data)
                "tool.call" -> handleToolCall(data)
            }
        }

        override fun onError(eventSource: EventSource, t: Throwable?) {
            Log.e("SSE", "Error: ${t?.message}")
        }
    }
)
```

---

## 4. Tool Call & Permission System

### Permission Levels
```typescript
type PermissionLevel = 
  | 'allow'    // Execute automatically
  | 'ask'      // Prompt for user approval
  | 'deny'     // Block entirely
```

### Permission Configuration
```json
{
  "permission": {
    "edit": "ask",
    "bash": "ask",
    "webfetch": "allow",
    "read": "allow",
    "write": "deny",
    "*": "allow"
  }
}
```

### Agent-Specific Permissions
```json
{
  "agent": {
    "build": {
      "permission": {
        "edit": "ask",
        "bash": "ask"
      }
    },
    "plan": {
      "permission": {
        "edit": "deny",
        "bash": "ask"
      }
    }
  }
}
```

### Wildcard Pattern Matching
```json
{
  "permission": {
    "bash": {
      "git*": "allow",      // Allow all git commands
      "rm -rf": "deny",      // Block dangerous commands
      "*": "ask"            // Ask for everything else
    },
    "edit": {
      "src/**/*.ts": "allow", // Allow edits in src
      "node_modules/**": "deny", // Block in node_modules
      "*": "ask"
    }
  }
}
```

### Permission Request/Response Flow
```typescript
// 1. Agent attempts tool use
await tools.edit({ path: 'file.ts', content: '...' })

// 2. System checks permissions
if (permission === 'ask') {
  // Emit permission request via SSE
  eventSource.emit({
    event: 'permission.request',
    data: {
      permissionID: 'perm_123',
      tool: 'edit',
      pattern: 'file.ts',
      description: 'Edit file.ts',
      title: 'Edit TypeScript file'
    }
  })
}

// 3. User responds via API
POST /session/:id/permissions/perm_123
{
  "response": "allow",  // or "deny"
  "remember": true      // Save decision
}

// 4. Tool execution continues or is blocked
```

---

## 5. File Operation Implementations

### Reading Files
```kotlin
suspend fun readFile(path: String): String {
    return withContext(Dispatchers.IO) {
        val response = httpClient.get("http://localhost:4096/file/content") {
            parameter("path", path)
        }
        return response.body ?: ""
    }
}
```

### Writing Files (via Tool)
```kotlin
suspend fun writeFile(path: String, content: String) {
    val permissionRequest = mapOf(
        "tool" to "write",
        "path" to path
    )
    
    // Check permission if needed
    if (needsPermission("write", path)) {
        val granted = requestPermission(permissionRequest)
        if (!granted) return
    }
    
    // Execute via server
    httpClient.post("http://localhost:4096/session/:id/message") {
        setBody(Part.FilePart(
            type = "file",
            path = path,
            content = content
        ))
    }
}
```

### Searching Files
```kotlin
suspend fun searchFiles(pattern: String): List<MatchResult> {
    return withContext(Dispatchers.IO) {
        val response = httpClient.get("http://localhost:4096/find") {
            parameter("pattern", pattern)
        }
        return parseSearchResults(response.body)
    }
}

data class MatchResult(
    val path: String,
    val lines: List<String>,
    val lineNumber: Int,
    val submatches: List<Submatch>
)
```

---

## 6. Configuration File Structure

### Global Config Location
```
~/.config/opencode/opencode.json
```

### Project Config Location
```
./opencode.json
```

### Example Configuration
```json
{
  "$schema": "https://opencode.ai/config.json",
  "theme": "opencode",
  "model": "anthropic/claude-sonnet-4-5",
  "small_model": "anthropic/claude-haiku-4-5",
  
  "server": {
    "port": 4096,
    "hostname": "127.0.0.1",
    "mdns": false,
    "cors": ["http://localhost:5173"]
  },
  
  "provider": {
    "anthropic": {
      "options": {
        "apiKey": "{env:ANTHROPIC_API_KEY}",
        "timeout": 600000,
        "setCacheKey": true
      }
    }
  },
  
  "permission": {
    "edit": "ask",
    "bash": "ask",
    "read": "allow"
  },
  
  "tools": {
    "write": false,
    "bash": false
  },
  
  "agent": {
    "code-reviewer": {
      "description": "Reviews code for best practices",
      "model": "anthropic/claude-sonnet-4-5",
      "tools": {
        "write": false,
        "edit": false
      }
    }
  },
  
  "autoupdate": true,
  "share": "manual"
}
```

---

## 7. Android App Architecture (Existing Implementations)

### openMode (Flutter Implementation)
**Repository:** https://github.com/easychen/openMode

**Technology Stack:**
- Framework: Flutter
- Language: Dart
- State Management: Provider
- HTTP Client: Dio
- DI: GetIt

**Architecture Layers:**
```
lib/
├── core/              # Core utilities and constants
│   ├── constants/
│   ├── di/
│   ├── errors/
│   ├── network/
│   └── utils/
├── data/              # Data layer
│   ├── datasources/    # Local and remote data sources
│   ├── models/         # Data models
│   └── repositories/  # Repository implementations
├── domain/            # Business logic
│   ├── entities/       # Business entities
│   ├── repositories/   # Repository interfaces
│   └── usecases/      # Business use cases
└── presentation/      # UI layer
    ├── pages/          # App screens
    ├── providers/      # State management
    ├── theme/          # App theming
    └── widgets/       # Reusable UI components
```

**Features Implemented:**
- AI Chat Interface
- Server connection configuration
- Session management
- Material Design 3 UI

**Status:** Work in Progress (WIP)

### Official Mobile App Proposal (Expo/React Native)
**Repository Feature:** https://github.com/anomalyco/opencode/issues/6536

**Tech Stack:**
- Framework: React Native with Expo SDK 54
- Navigation: Expo Router
- Communication: Local IP with SSE support
- Architecture: Remote desktop client pattern

**Key Features:**
- Dedicated Chat interface with terminal styling
- Session management
- Server configuration settings
- Real-time streaming via SSE

---

## 8. API Client Implementation Patterns

### TypeScript SDK Structure
```typescript
// Package: @opencode-ai/sdk
// Installation: npm install @opencode-ai/sdk

import { createOpencode, createOpencodeClient } from "@opencode-ai/sdk"
import type { Session, Message, Part } from "@opencode-ai/sdk"

// Option 1: Start server + client
const { client } = await createOpencode({
  hostname: "127.0.0.1",
  port: 4096,
  config: {
    model: "anthropic/claude-sonnet-4-5"
  }
})

// Option 2: Connect to existing server
const client = createOpencodeClient({
  baseUrl: "http://localhost:4096",
  fetch: customFetch,
  responseStyle: "data",
  throwOnError: true
})
```

### Client API Methods
```typescript
// Session Management
await client.session.create({ body: { title: "My Session" }})
await client.session.list()
await client.session.get({ path: { id: "session-id" }})
await client.session.delete({ path: { id: "session-id" }})

// Send Message
const result = await client.session.prompt({
  path: { id: "session-id" },
  body: {
    model: { providerID: "anthropic", modelID: "claude-sonnet-4-5" },
    parts: [{ type: "text", text: "Hello!" }]
  }
})

// File Operations
const files = await client.find.files({
  query: { query: "*.ts", type: "file", limit: 20 }
})
const content = await client.file.read({
  query: { path: "src/index.ts" }
})

// Event Streaming
const events = await client.event.subscribe()
for await (const event of events.stream) {
  console.log("Event:", event.type, event.properties)
}

// Config
const config = await client.config.get()
const { providers, default: defaults } = await client.config.providers()
```

---

## 9. Error Handling

### Error Types
```typescript
class BadRequestError extends OpencodeError {
  status: 400;
  code: 'BAD_REQUEST';
}

class AuthenticationError extends OpencodeError {
  status: 401;
  code: 'AUTHENTICATION_ERROR';
}

class NotFoundError extends OpencodeError {
  status: 404;
  code: 'NOT_FOUND';
}

class InternalServerError extends OpencodeError {
  status: 500;
  code: 'INTERNAL_ERROR';
}
```

### Error Response Format
```json
{
  "error": {
    "code": "BAD_REQUEST",
    "message": "Invalid request body",
    "details": {
      "field": "model.providerID",
      "reason": "required"
    }
  }
}
```

---

## 10. Existing OpenCode Projects

### Official Repositories
1. **anomalyco/opencode** - Main TypeScript implementation
   - Stars: 76.1k
   - Forks: 6.7k
   - Primary Language: TypeScript
   
2. **anomalyco/opencode-sdk-js** - JavaScript/TypeScript SDK
   - Language: TypeScript (93.4%)
   - License: MIT
   - Features: REST API, SSE streaming, error handling

3. **easychen/openMode** - Flutter Android implementation
   - Technology: Flutter, Dart
   - Status: WIP
   - Architecture: Clean Architecture

### Community Projects
- **syntax-syndicate/opencode-agent-copilot** - VS Code extension
- Various plugins and integrations via `.opencode/plugins/`

---

## 11. Android Implementation Checklist

### Required Components

#### 1. Network Layer
- [ ] HTTP client (OkHttp/Retrofit)
- [ ] SSE client (OkSse or custom)
- [ ] Authentication handling
- [ ] Request/response interceptors
- [ ] Error handling and retry logic

#### 2. Data Layer
- [ ] Session repository
- [ ] Message repository
- [ ] File operation repository
- [ ] Local cache/database
- [ ] Repository interfaces

#### 3. Domain Layer
- [ ] Use cases for:
  - [ ] Create session
  - [ ] Send message
  - [ ] Subscribe to events
  - [ ] Handle permissions
  - [ ] Search files
  - [ ] Read/write files

#### 4. UI Layer
- [ ] Chat interface
- [ ] Session list screen
- [ ] Settings screen
- [ ] Permission dialog
- [ ] Terminal output view
- [ ] Code diff viewer

#### 5. Configuration
- [ ] Server URL configuration
- [ ] Authentication credentials storage
- [ ] Theme settings
- [ ] Permission preferences

#### 6. Permissions
- [ ] Network permissions (Internet)
- [ ] File access permissions (if needed)
- [ ] Background service (for SSE)

---

## 12. Key Design Decisions

### Architecture Choice
**Option A: Native Android (Kotlin/Java)**
- Pros: Best performance, full native access
- Cons: More development time, separate codebase

**Option B: Flutter (Cross-platform)**
- Pros: One codebase for iOS/Android, existing reference (openMode)
- Cons: Larger app size, Flutter learning curve

**Option C: React Native (Expo)**
- Pros: Official proposal, Expo Router, TypeScript support
- Cons: Not yet implemented, less mature for Android

### Communication Protocol
- **Primary:** REST API with SSE for real-time events
- **Alternative:** WebSocket (not officially supported yet)

### State Management
- **ViewModel:** MVVM with LiveData/Flow
- **Repository Pattern:** Clean separation of concerns
- **Coroutines:** Kotlin coroutines for async operations

---

## 13. Recommended Next Steps

1. **Choose Technology Stack**
   - Review options A, B, C above
   - Consider team expertise and project requirements

2. **Set Up Project Structure**
   - Initialize chosen framework
   - Configure build system
   - Add dependencies (OkHttp, OkSse, etc.)

3. **Implement Core Features First**
   - Server connection
   - Session list
   - Basic chat interface

4. **Add Advanced Features**
   - SSE streaming
   - Permission handling
   - File operations
   - Tool execution visualization

5. **Testing & Polish**
   - Unit tests for repositories
   - Integration tests for API
   - UI/UX improvements
   - Performance optimization

---

## References & Resources

### Official Documentation
- OpenCode Docs: https://opencode.ai/docs
- SDK Docs: https://opencode.ai/docs/sdk
- Server API: https://opencode.ai/docs/server
- Config: https://opencode.ai/docs/config
- Permissions: https://opencode.ai/docs/permissions
- Tools: https://opencode.ai/docs/tools
- Agents: https://opencode.ai/docs/agents

### Repositories
- Main: https://github.com/anomalyco/opencode
- JS SDK: https://github.com/anomalyco/opencode-sdk-js
- Flutter App: https://github.com/easychen/openMode

### OpenAPI Spec
- Location: `packages/sdk/openapi.json` in main repo
- Available at: `http://localhost:4096/doc` (when server running)

### Community
- GitHub Issues: https://github.com/anomalyco/opencode/issues
- Contributing: https://github.com/anomalyco/opencode/blob/dev/CONTRIBUTING.md

---

*Document generated: 2026-01-18*
*Based on comprehensive search of OpenCode documentation, GitHub repositories, and implementation patterns*
