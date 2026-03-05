---
id: oa-wmrj
status: closed
deps: [oa-s3kb]
links: []
created: 2026-02-22T12:01:37Z
type: task
priority: 2
assignee: Jasmin Le Roux
---
# UI performance + infra: scroll, SelectionContainer, keys, CI

## Problem

Several Compose performance issues and missing infrastructure:
1. Chat scroll uses `scrollToItem(0)` instead of `animateScrollToItem(0)` during streaming — causes jumpy UX
2. `SelectionContainer` wraps entire LazyColumn — interferes with recycling
3. 5 `items()` calls in LazyLists lack `key` parameter — destroys scroll state
4. `MessageWithParts` may not be `@Stable` — causes unnecessary recompositions during streaming
5. No CI pipeline for detekt/compile/test

## What To Do

### 1. Chat Scroll Smoothness (P1)
File: `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/ChatScreen.kt`
- Change `scrollToItem(0)` → `animateScrollToItem(0)` when distance is small (<3 items)
- For larger jumps, keep instant scroll to avoid long animation
- The current logic tracks `userScrolledAway` — keep that, just smooth the auto-scroll

### 2. SelectionContainer Optimization (P2)
File: `ChatScreen.kt`
- Move `SelectionContainer` from wrapping the entire `LazyColumn` to wrapping individual message text content only
- This allows LazyColumn to properly recycle items
- Apply inside `MessageBlockView` or `ChatMessage` — wrap just the text body

### 3. LazyList Key Audit (P1)
Find all `items()` calls without `key` parameter in LazyColumn/LazyRow across the app.
Files to check:
- `ChatScreen.kt` — message list
- `SessionListScreen.kt` — session list  
- `FileExplorerScreen.kt` — file list
- `SlashCommandsPopup.kt` — command list
- Any other LazyList usage
Add stable unique keys (message ID, session ID, etc.)

### 4. Stability Annotations (P2)
- Check if `MessageWithParts` is stable (if it contains `List<Part>`, it's unstable)
- Options: annotate with `@Immutable` or use `kotlinx.collections.immutable.ImmutableList`
- Check other data classes passed to composables for stability

### 5. Large File Splits (P2)
Consider splitting if time allows:
- `TuiComponents.kt` (792 lines) → `TuiCards.kt`, `TuiButtons.kt`, `TuiInputs.kt`, `TuiDialogs.kt`
- `SessionListScreen.kt` (674 lines) → extract `SessionListItem.kt`

### 6. CI Pipeline (P2)
If GitHub Actions workflow exists (`.github/workflows/`), add:
- `./gradlew detekt` step
- `./gradlew :app:compileDebugKotlin` step  
- `./gradlew test` step (after testing ticket is done)

## Acceptance Criteria
- [ ] Chat streaming scroll is smooth (animateScrollToItem)
- [ ] LazyList items have stable keys
- [ ] SelectionContainer moved to message level (not LazyColumn level)
- [ ] `./gradlew :app:compileDebugKotlin` passes
- [ ] CI pipeline added (if .github/workflows exists)

## Acceptance Criteria

Smooth streaming scroll. LazyList keys. SelectionContainer optimized. Compile clean.

