# Design: FluidMarkdown Integration

## Context
The app currently uses `compose-markdown:0.5.4` which is a Compose-native markdown renderer. However, it has bugs with heading styles and tables, and isn't optimized for streaming LLM output. FluidMarkdown is a View-based library from AntGroup specifically designed for AI chat streaming.

## Goals
- Proper markdown rendering (headings, tables, code blocks, lists)
- Streaming-aware incremental updates without full re-renders
- Maintain compact visual style with Material 3 theming
- Reliable height change callbacks for scroll coordination

## Non-Goals
- Full markdown spec compliance (we only need LLM-common subset)
- LaTeX/math rendering (defer to future if needed)
- Image embedding in markdown (handled separately via Part.File)

## Decisions

### 1. Integration Approach: AndroidView Interop
**Decision**: Wrap `PrinterMarkDownTextView` in Compose `AndroidView` rather than porting to pure Compose.

**Rationale**: 
- FluidMarkdown is battle-tested for streaming; rewriting would lose that
- `AndroidView` interop is well-supported in Compose
- Allows us to benefit from FluidMarkdown updates

**Trade-off**: Slight complexity in bridging Compose state to View updates.

### 2. Module Inclusion
**Decision**: Clone FluidMarkdown and include `fluid-markdown` as a local module.

**Rationale**:
- FluidMarkdown is not published to Maven Central or JitPack
- Local module allows customization if needed
- Can track upstream updates via git

**Structure**:
```
project/
├── app/
├── fluid-markdown/  # Copied from FluidMarkdown/Android/AntFluid/fluid-markdown
└── settings.gradle.kts  # include(":fluid-markdown")
```

### 3. Style Mapping Strategy
**Decision**: Create `MarkdownStyleMapper` that reads `MaterialTheme.colorScheme` and produces `MarkdownStyles`.

```kotlin
@Composable
fun rememberMarkdownStyles(): MarkdownStyles {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    
    return remember(colors, typography) {
        MarkdownStyles.getDefaultStyles().apply {
            // Map colors
            textStyle().color(colors.onSurface.toArgb())
            codeBlockStyle().backgroundColor(colors.surfaceContainerHighest.toArgb())
            linkStyle().color(colors.primary.toArgb())
            // etc.
        }
    }
}
```

**Rationale**: Keeps theming centralized, styles update on theme change.

### 4. Streaming Mode
**Decision**: Use FluidMarkdown's `startPrinting()` / `addStreamContent()` API for streaming parts, `setMarkdownText()` for completed parts.

```kotlin
// In AndroidView update block
if (part.isStreaming) {
    if (!isStreamingStarted) {
        view.startPrinting(part.text)
        isStreamingStarted = true
    } else {
        view.addStreamContent(delta)
    }
} else {
    view.setMarkdownText(part.text)
}
```

**Rationale**: Matches FluidMarkdown's intended usage, enables incremental rendering.

### 5. Scroll Coordination
**Decision**: Use content height changes to trigger scroll updates instead of message count.

```kotlin
// In ChatScreen
val lastContentHeight = remember { mutableStateOf(0) }

StreamingMarkdown(
    text = part.text,
    isStreaming = part.isStreaming,
    onHeightChange = { newHeight ->
        if (newHeight > lastContentHeight.value && !userScrolledAway) {
            // Trigger scroll to bottom
            coroutineScope.launch {
                listState.scrollToItem(messages.size - 1, scrollOffset = Int.MAX_VALUE)
            }
        }
        lastContentHeight.value = newHeight
    }
)
```

**Rationale**: Height-based scrolling is more accurate than message-count-based, especially during streaming when a single message grows.

## Component Structure

```
ui/components/chat/
├── ChatMessage.kt          # Uses StreamingMarkdown for text parts
├── StreamingMarkdown.kt    # New: Compose wrapper for FluidMarkdown
└── MarkdownStyleMapper.kt  # New: Material3 → FluidMarkdown styles
```

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| FluidMarkdown module size | Only include necessary submodules |
| View/Compose interop complexity | Encapsulate in single `StreamingMarkdown` component |
| Style parity with current look | Create comprehensive style mapping, test side-by-side |
| Library maintenance/updates | Pin to specific commit, update manually |

## Migration Plan
1. Clone FluidMarkdown, copy Android module to project
2. Add module to settings.gradle.kts
3. Create `StreamingMarkdown` component with style mapping
4. Replace `MarkdownText` usages one by one
5. Update scroll logic to use height callbacks
6. Remove `compose-markdown` dependency
7. Delete `compactMarkdown()` helper

## Open Questions
- None currently - proceeding with implementation
