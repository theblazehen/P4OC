---
id: oa-ina0
status: closed
deps: [oa-gs5t]
links: []
created: 2026-02-22T11:59:36Z
type: feature
priority: 1
assignee: Jasmin Le Roux
---
# ChatViewModel decomposition into focused sub-managers

## Problem

ChatViewModel is a 992-line god object with 9 constructor parameters and a 23-field UiState. It handles 7 distinct concerns: message management, permission/question queuing, model/agent selection, command management, file picker, SSE event routing, and session lifecycle. This makes it untestable and any change to chat/permissions/models/commands requires modifying this single file.

## What To Do

Extract 4 sub-managers, then refactor ChatViewModel as a slim coordinator.

### 1. MessageStore (~100 lines)
New file: `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/MessageStore.kt`

**Responsibility**: Owns message state and all mutation logic.
**State**: `SnapshotStateMap<String, MessageWithParts>`, `_messagesVersion: MutableState<Long>`, `messagesMutex: Mutex`
**Methods to extract from ChatViewModel**:
- `upsertMessage(message: Message)` (lines ~442-455)
- `upsertPart(part: Part, delta: String?)` (lines ~463-489)
- `applyDelta(existing: Part, incoming: Part, delta: String?)` (lines ~491-497)
- `createPlaceholderMessage(messageId: String)` (lines ~499-515)
- `clearStreamingFlags()` (lines ~722-740)
**Exposes**: `val messages: StateFlow<List<MessageWithParts>>`, `fun loadInitial(messages: List<MessageWithParts>)`
**Dependencies**: None (pure state container). Takes `CoroutineScope` for `stateIn()`.
**Risk**: Medium — mutex semantics must be preserved exactly.

### 2. DialogQueueManager (~120 lines)
New file: `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/DialogQueueManager.kt`

**Responsibility**: Permission & question queue management with SavedStateHandle persistence.
**State**: `pendingPermissions: ConcurrentLinkedQueue<Permission>`, `pendingQuestions: ConcurrentLinkedQueue<QuestionRequest>`, current pending permission/question, `pendingPermissionsByCallId` map
**Methods to extract**:
- `restorePendingDialogState()` (lines ~179-227)
- `showNextPermission()` / `showNextQuestion()` (lines ~400-422)
- `persistQuestionsQueue()` / `persistPermissionsQueue()` (lines ~424-440)
- Permission/question enqueue from `handleEvent` (lines ~307-327)
- Permission/question response cleanup from `respondToPermission`, `respondToQuestion`, `dismissQuestion` (lines ~576-612)
- `PermissionReplied` handling (lines ~385-396)
**Exposes**: `StateFlow<Permission?>`, `StateFlow<QuestionRequest?>`, `StateFlow<Map<String, Permission>>`
**Dependencies**: `SavedStateHandle`, `Json`
**Risk**: Medium — SavedStateHandle serialization must be preserved. Process death needs manual testing.

### 3. ModelAgentManager (~100 lines)
New file: `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/ModelAgentManager.kt`

**Responsibility**: Model/agent loading, selection, favorites, recents.
**State**: `availableAgents`, `selectedAgent`, `availableModels`, `selectedModel`
**Methods to extract**:
- `loadAgents()` (lines ~821-848)
- `selectAgent()` (line ~850)
- `loadModels()` (lines ~854-888)
- `selectModel()` (lines ~890-895)
- `toggleFavoriteModel()` (lines ~897-901)
**Exposes**: StateFlows for all state
**Dependencies**: `ConnectionManager`, `SettingsDataStore`, `CoroutineScope`
**Risk**: Low — isolated state, simple API calls.

### 4. FilePickerManager (~70 lines)
New file: `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/FilePickerManager.kt`

**Responsibility**: File browser navigation and attachment list.
**State**: `pickerFiles`, `pickerCurrentPath`, `isPickerLoading`, `attachedFiles`
**Methods to extract**:
- `loadPickerFiles()` (lines ~903-936)
- `attachFile()` / `detachFile()` / `clearAttachedFiles()` (lines ~938-955)
**Exposes**: StateFlows for all state
**Dependencies**: `ConnectionManager`, `CoroutineScope`
**Risk**: Low — self-contained.

### 5. Refactor ChatViewModel as Coordinator (~300 lines)
Modify existing `ChatViewModel.kt`:
- Create sub-managers in init
- `handleEvent()` dispatches to appropriate sub-manager
- Compose `ChatUiState` from sub-manager StateFlows via `combine()`
- Keep: session lifecycle, message sending/queuing, commands/todos, SSE event routing
- Move frequently-updating state (models, agents, picker) to separate exposed flows instead of stuffing everything in ChatUiState

**Post-decomposition ChatViewModel constructor** (~5 params after mapper-to-object conversion):
```kotlin
class ChatViewModel(
    savedStateHandle: SavedStateHandle,
    connectionManager: ConnectionManager,
    directoryManager: DirectoryManager,
    messageMapper: MessageMapper,
    settingsDataStore: SettingsDataStore
)
```

## Implementation Order
1. Extract MessageStore (compile + test)
2. Extract DialogQueueManager (compile + test)  
3. Extract ModelAgentManager (compile + test)
4. Extract FilePickerManager (compile + test)
5. Refactor ChatViewModel as coordinator (compile + full manual test)

Each step should compile independently — no big-bang refactor.

## Acceptance Criteria
- [ ] 4 new sub-manager files created
- [ ] ChatViewModel reduced to ~300 lines (coordinator role)
- [ ] Each sub-manager independently testable
- [ ] SSE events correctly routed to sub-managers
- [ ] SavedStateHandle persistence for permissions/questions preserved
- [ ] `combine()` correctly composes ChatUiState
- [ ] ChatScreen UI unchanged (no visible regressions)
- [ ] `./gradlew :app:compileDebugKotlin` passes

## Acceptance Criteria

4 sub-managers extracted. ChatViewModel ~300 lines. All chat functionality preserved. Compile clean.

