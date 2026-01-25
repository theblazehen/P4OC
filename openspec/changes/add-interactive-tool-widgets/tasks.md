# Implementation Tasks

## 1. Infrastructure
- [x] 1.1 Create `ToolWidgetState` enum (Oneline, Compact, Expanded)
- [x] 1.2 Create `ToolCallWidget.kt` wrapper component with state cycling
- [x] 1.3 Add `toolWidgetDefaultState` setting to `SettingsDataStore.kt`
- [x] 1.4 Create base components: `ToolCallOneline.kt`, `ToolCallCompact.kt`, `ToolCallExpanded.kt`

## 2. Question Widget (Interactive)
- [x] 2.1 Refactor `InlineQuestionCard.kt` into `QuestionWidget.kt`
- [x] 2.2 Make Question tool always default to Expanded state
- [x] 2.3 Wire Question widget into `ToolCallWidget`

## 3. Read-Only Widgets (Phase 1)
- [x] 3.1 Create `BashWidget.kt` (command + stdout/stderr preview)
- [x] 3.2 Create `ReadWidget.kt` (file path + syntax-highlighted code preview)
- [x] 3.3 Create `FileEditWidget.kt` (file path + inline diff preview)
- [x] 3.4 Create `DefaultExpandedWidget.kt` (fallback for unimplemented tools)

## 4. ChatScreen Integration
- [x] 4.1 Replace tool rendering in `ChatScreen.kt` with `ToolCallWidget`
- [x] 4.2 Handle parallel tool calls (render separate widgets)
- [x] 4.3 Manage widget state per-message (not persisted across restarts)
- [x] 4.4 Ensure widget state preserved when scrolling

## 5. Settings UI
- [x] 5.1 Add "Tool Call Display" section to `VisualSettingsScreen.kt`
- [x] 5.2 Add radio group: Oneline / Compact / Expanded
- [x] 5.3 Set default to Compact

## 6. Testing & Polish
- [x] 6.1 Test all 8 themes with widgets
- [x] 6.2 Test widget cycling (tap behavior)
- [x] 6.3 Test Question widget interaction (submit answers)
- [x] 6.4 Test parallel tool calls rendering
- [x] 6.5 Verify widgets height constraints (~75-115dp for most, up to viewport for large)
- [x] 6.6 Accessibility check (tap targets, contrast)
