---
id: oa-bdr4
status: closed
deps: []
links: []
created: 2026-02-24T16:30:00Z
type: feature
priority: 2
assignee:
---
# Abort summary card in chat after Stop button

## Problem

When the user hits the Stop button, the session silently goes idle. There's no visual feedback confirming what happened or summarizing what was interrupted. The user is left guessing whether the abort worked and what partial work was done.

## Design

An inline card rendered in the chat `LazyColumn` (same pattern as `InlineQuestionCard`) that shows:
- Confirmation the abort registered
- Which tools were interrupted (name + brief context)
- Whether text was mid-stream
- Token/cost summary from the partial turn

### Visual mockup

```
┌──────────────────────────────────────────────────────┐
│ ■ Aborted                                            │
│   ◐ bash — git diff HEAD~3                           │
│   ◐ text mid-stream · in:12K out:3K · $0.0042        │
└──────────────────────────────────────────────────────┘
```

Multiple tools:
```
┌──────────────────────────────────────────────────────┐
│ ■ Aborted                                            │
│   ◐ bash — npm install · ◐ edit — src/App.kt         │
│   in:18K out:5K · $0.0087                            │
└──────────────────────────────────────────────────────┘
```

Minimal (nothing running):
```
┌──────────────────────────────────────────────────────┐
│ ■ Aborted                                            │
└──────────────────────────────────────────────────────┘
```

### Style
- Background: `theme.error.copy(alpha = 0.1f)` — matches `RetryPartDisplay` pattern
- Shape: `RectangleShape` (TUI, no rounded corners)
- Header: `"■"` in `theme.error`, `"Aborted"` in `theme.error` + `FontWeight.Bold`
- Tool prefix: `"◐"` in `theme.warning`, tool name in `theme.text`, context in `theme.textMuted`
- Stats: `theme.textMuted`, `labelSmall`, separated by `" · "`
- Padding: `Spacing.md` outer, `Spacing.sm` between rows
- Max 3 tools shown inline, then `"+N more"`

## Data Model

```kotlin
@Immutable
data class AbortSummary(
    val interruptedTools: List<InterruptedTool>,
    val wasTextStreaming: Boolean,
    val tokens: TokenUsage?,
    val cost: Double?,
    val abortedAt: Long = System.currentTimeMillis()
)

@Immutable
data class InterruptedTool(
    val toolName: String,
    val context: String?  // Running.title or extracted from input, truncated to ~40 chars
)
```

Place in `domain/model/` or colocate with `ChatUiState` in `ChatViewModel.kt`.

## Implementation Plan

### 1. Add `snapshotMessages()` to `MessageStore`

Mutex-guarded snapshot for safe reads during abort (parts may be mid-mutation from SSE):

```kotlin
suspend fun snapshotMessages(): List<MessageWithParts> =
    messagesMutex.withLock {
        _messagesMap.values.sortedBy { it.message.createdAt }
    }
```

### 2. Update `ChatViewModel.abortSession()`

Capture state BEFORE clearing flags, store summary in `ChatUiState`:

```kotlin
private fun buildAbortSummary(snapshot: List<MessageWithParts>): AbortSummary {
    val runningTools = snapshot.flatMap { it.parts }
        .filterIsInstance<Part.Tool>()
        .filter { it.state is ToolState.Running }
        .map { tool ->
            val running = tool.state as ToolState.Running
            InterruptedTool(
                toolName = tool.toolName,
                context = running.title?.take(40)
            )
        }
    val wasStreaming = snapshot.flatMap { it.parts }
        .any { it is Part.Text && it.isStreaming }
    val lastAssistant = snapshot.map { it.message }
        .filterIsInstance<Message.Assistant>().lastOrNull()
    
    return AbortSummary(
        interruptedTools = runningTools,
        wasTextStreaming = wasStreaming,
        tokens = lastAssistant?.tokens,
        cost = lastAssistant?.cost
    )
}
```

Add `abortSummary: AbortSummary? = null` to `ChatUiState`.

### 3. Add `AbortSummaryCard` composable

New file: `app/src/main/java/dev/blazelight/p4oc/ui/components/chat/AbortSummaryCard.kt`

Follow `RetryPartDisplay` pattern in `PartVisualizations.kt`:
- `Surface(color = theme.error.copy(alpha = 0.1f), shape = RectangleShape)`
- Header row: `"■"` icon + `"Aborted"` text
- Tool interruption line (if tools were running)
- Stats line: streaming indicator + `in:Xk out:Yk` + `$0.NNNN`
- Add `Modifier.testTag("abort_summary_card")`
- Add `contentDescription` via `Modifier.semantics`

### 4. Render in `ChatScreen` LazyColumn

Insert as synthetic item, same pattern as pending question card:

```kotlin
uiState.abortSummary?.let { summary ->
    item(key = "abort_summary_${summary.abortedAt}") {
        AbortSummaryCard(
            summary = summary,
            modifier = Modifier.padding(vertical = Spacing.xs)
        )
    }
}
```

Position: after pending question, before message blocks (in reversed layout this means visually just above the most recent messages).

### 5. Lifecycle: clear on next send

```kotlin
fun sendMessage() {
    _uiState.update { it.copy(abortSummary = null) }
    // ... existing send logic
}
```

No `SavedStateHandle` persistence — this is ephemeral UI feedback.

## String Resources

```xml
<string name="aborted">Aborted</string>
<string name="cd_abort_summary">Abort summary</string>
```

Tool names are not localized (protocol values rendered as-is).

## Key Files
- `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/ChatViewModel.kt` — capture + state
- `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/MessageStore.kt` — add `snapshotMessages()`
- `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/ChatScreen.kt` — render inline item
- **New**: `app/src/main/java/dev/blazelight/p4oc/ui/components/chat/AbortSummaryCard.kt`
- `app/src/main/res/values/strings.xml` — new strings

No changes to: `Part.kt`, `ToolState.kt`, `Message.kt`, DTOs, mappers, or API layer. Purely UI-layer.

## Edge Cases
- No running tools, no streaming → show minimal `"■ Aborted"` header only (still valuable as confirmation)
- Multiple tools (>3) → show first 3 inline, then `"+N more"`
- No assistant message yet → `tokens`/`cost` are null, skip stats line
- Very long tool title → truncated to 40 chars at build time + `maxLines = 1`
- Abort during pending question → both cards coexist with different keys

## Acceptance Criteria
- [ ] Abort summary card appears in chat after hitting Stop
- [ ] Shows interrupted tools with name + context
- [ ] Shows token/cost stats when available
- [ ] Card clears when user sends next message
- [ ] Follows TUI design system (Spacing/Sizing tokens, theme colors, RectangleShape)
- [ ] Accessibility: semantics contentDescription on card
- [ ] `Modifier.testTag("abort_summary_card")` present
- [ ] `./gradlew :app:compileDebugKotlin` passes
