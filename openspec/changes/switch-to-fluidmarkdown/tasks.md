# Tasks: Switch to FluidMarkdown

## 1. Dependency Setup
- [x] 1.1 Clone FluidMarkdown repository
- [x] 1.2 Copy `Android/AntFluid/fluid-markdown` module to project root
- [x] 1.3 Copy required `markwon-*` modules as dependencies
- [x] 1.4 Add module includes to `settings.gradle.kts`
- [x] 1.5 Add `implementation(project(":fluid-markdown"))` to `app/build.gradle.kts`
- [x] 1.6 Remove `compose-markdown` dependency
- [x] 1.7 Verify build succeeds

## 2. Style Mapping
- [x] 2.1 Create `MarkdownStyleMapper.kt` in `ui/components/chat/`
- [x] 2.2 Map Material3 `colorScheme` to FluidMarkdown colors
- [x] 2.3 Map Material3 `typography` to FluidMarkdown font sizes
- [x] 2.4 Configure code block, table, and heading styles for compact look

## 3. Compose Interop Component
- [x] 3.1 Create `StreamingMarkdown.kt` composable with `AndroidView`
- [x] 3.2 Initialize `PrinterMarkDownTextView` in factory block with styles
- [x] 3.3 Handle streaming vs static content in update block
- [x] 3.4 Implement height change callback via `setSizeChangedListener`
- [x] 3.5 Handle recomposition correctly (avoid duplicate streams)

## 4. Integration
- [x] 4.1 Replace `MarkdownText` in `TextPart()` with `StreamingMarkdown`
- [x] 4.2 Replace `MarkdownText` in `UserMessage()` with `StreamingMarkdown`
- [x] 4.3 Replace `MarkdownText` in `ReasoningPart()` with `StreamingMarkdown`
- [x] 4.4 Remove `compactMarkdown()` helper function
- [x] 4.5 Remove unused `MarkdownText` import

## 5. Scroll Fix
- [x] 5.1 Add content hash tracking to `ChatScreen.kt`
- [x] 5.2 Update scroll trigger to use content changes, not just message count
- [x] 5.3 Test scroll behavior during streaming (requires manual testing)

## 6. Cleanup
- [x] 6.1 Update `openspec/project.md` markdown library reference
- [x] 6.2 Verify no remaining references to old markdown library

## 7. Validation
- [x] 7.1 Build debug APK successfully
- [ ] 7.2 Test heading rendering (H1-H6) - verify no size leak (manual)
- [ ] 7.3 Test table rendering with various column counts (manual)
- [ ] 7.4 Test code block rendering with syntax highlighting (manual)
- [ ] 7.5 Test streaming text appearance (no jank) (manual)
- [ ] 7.6 Test auto-scroll during streaming (manual)
- [ ] 7.7 Test theme switching (light/dark mode) (manual)
