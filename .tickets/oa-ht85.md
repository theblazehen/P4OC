---
id: oa-ht85
status: closed
deps: []
links: [oa-6qcn]
created: 2026-02-24T16:15:00Z
type: bug
priority: 1
assignee:
---
# Task tool call widget can't open sub-agent session

## Problem

Tapping a Task tool call widget in the chat doesn't let you view the sub-agent session. The "Open Sub-Agent" button never renders because the sub-session ID extraction looks in the wrong place.

## Root Cause

`ExpandedWidgets.kt` line 459:

```kotlin
val sessionId = extractJsonParam(state.input, "session_id")
```

`state.input` contains the **model's invocation parameters** (`description`, `prompt`, `subagent_type`, `task_id`). The sub-agent's session ID is a server-side concept assigned after the tool is processed — it's NOT in the input. So `sessionId` is always `null`, and the button gate at line 545 always fails:

```kotlin
if (sessionId != null && onOpenSubSession != null) {
    // This never executes
}
```

The card's primary tap action is wired to cycle widget state (oneline -> compact -> expanded), not to open the sub-session.

## Where the sub-session ID likely lives

1. **`ToolState.metadata`** (most likely) — server-side execution context belongs in metadata, available on `Running`, `Completed`, and `Error` states
2. **`Part.Tool.metadata`** — top-level part metadata
3. **Correlation via `SessionCreated` events** — match sessions where `parentID` equals current session, correlate by timing/tool call

Need to inspect actual server SSE payloads to confirm the exact field name (likely `sessionID`, `sessionId`, or `subSessionId` in metadata).

## Proposed Fix

### 1. Extract sub-session ID from metadata (not input)

```kotlin
val sessionId = extractSessionIdFromMetadata(tool, state)

private fun extractSessionIdFromMetadata(tool: Part.Tool, state: ToolState): String? {
    // Check state metadata first (most specific)
    val stateMeta = when (state) {
        is ToolState.Completed -> state.metadata
        is ToolState.Running -> state.metadata
        is ToolState.Error -> state.metadata
        else -> null
    }
    stateMeta?.let { meta ->
        listOf("sessionID", "sessionId", "session_id", "subSessionId").forEach { key ->
            meta[key]?.jsonPrimitive?.content?.let { return it }
        }
    }
    // Fallback to part-level metadata
    tool.metadata?.let { meta ->
        listOf("sessionID", "sessionId", "session_id", "subSessionId").forEach { key ->
            meta[key]?.jsonPrimitive?.content?.let { return it }
        }
    }
    // Last resort: check input (current behavior, unlikely to work)
    return extractJsonParam(state.input, "session_id")
}
```

### 2. Make primary tap action context-aware

Instead of always cycling widget state on tap:
- If sub-session ID exists → tap opens sub-session
- Keep expand/collapse on an explicit chevron/affordance, not the whole card

### 3. Show stateful CTA

- Running: "View live sub-agent" (if ID available)
- Completed: "Open sub-agent"
- No ID: disabled button or no button (current behavior, but intentional)

### 4. Consider making `Part.Subtask` visible

Currently `Part.Subtask` is treated as invisible (`ChatMessage.kt:120`). Consider showing it as a minimal breadcrumb so users understand a child agent exists, even before the task tool completes.

## Key Files
- `app/src/main/java/dev/blazelight/p4oc/ui/components/toolwidgets/ExpandedWidgets.kt` — TaskWidgetExpanded (line 443-570)
- `app/src/main/java/dev/blazelight/p4oc/ui/components/toolwidgets/ToolGroupWidget.kt` — onClick cycles state (line 205)
- `app/src/main/java/dev/blazelight/p4oc/ui/components/toolwidgets/ToolCallWidget.kt` — ToolCallExpanded dispatching (line 232-238)
- `app/src/main/java/dev/blazelight/p4oc/domain/model/ToolState.kt` — metadata fields
- `app/src/main/java/dev/blazelight/p4oc/domain/model/Part.kt` — Part.Tool.metadata, Part.Subtask
- `app/src/main/java/dev/blazelight/p4oc/ui/tabs/TabNavHost.kt` — navigation wiring (line 168-171, works correctly)

## Acceptance Criteria
- [ ] Sub-session ID correctly extracted from server metadata (verify actual field name against SSE payloads)
- [ ] "Open Sub-Agent" button renders for completed/running task tool calls
- [ ] Tapping button navigates to sub-agent session in current tab
- [ ] If sub-session ID unavailable, graceful fallback (no button, not broken)
- [ ] `./gradlew :app:compileDebugKotlin` passes
