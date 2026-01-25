## 1. Theme Infrastructure

- [x] 1.1 Create `ui/theme/opencode/OpenCodeTheme.kt` - data class with 49 semantic color tokens
- [x] 1.2 Create `ui/theme/opencode/ThemeLoader.kt` - JSON parsing, reference resolution, dark/light handling
- [x] 1.3 Create `ui/theme/opencode/Material3Mapper.kt` - OpenCodeTheme → M3 ColorScheme extension
- [x] 1.4 Copy bundled theme JSON files to `assets/themes/` (8 themes)
- [x] 1.5 Write unit tests for ThemeLoader (parse, resolve refs, dark/light)

## 2. Core Theme System

- [x] 2.1 Rewrite `ui/theme/Theme.kt` - add LocalOpenCodeTheme, TuiShapes, wire ThemeLoader
- [x] 2.2 Create TuiShapes (all RoundedCornerShape(0.dp)) in Theme.kt
- [x] 2.3 Update `ui/theme/Typography.kt` - tighten line heights for TUI density
- [x] 2.4 Delete `ui/theme/CatppuccinColors.kt` (replaced by OpenCodeTheme)
- [x] 2.5 Delete `ui/theme/Color.kt` (replaced by OpenCodeTheme)
- [x] 2.6 Rewrite `ui/theme/ProjectColors.kt` to use theme accent colors

## 3. Shape Migration (RoundedCornerShape → RectangleShape)

- [x] 3.1 Migrate `PartVisualizations.kt` (7 instances)
- [x] 3.2 Migrate `TodoTracker.kt` (6 instances)
- [x] 3.3 Migrate `CommandPalette.kt` (6 instances)
- [x] 3.4 Migrate `FileExplorerScreen.kt` (5 instances)
- [x] 3.5 Migrate `ToolComponents.kt` (5 instances)
- [x] 3.6 Migrate `FilePickerDialog.kt` (5 instances)
- [x] 3.7 Migrate `MultiAgentRuns.kt` (4 instances)
- [x] 3.8 Migrate `CollapsedToolSummary.kt` (4 instances)
- [x] 3.9 Migrate `ChatMessage.kt` (4 instances)
- [x] 3.10 Migrate `ProviderConfigScreen.kt` (3 instances)
- [x] 3.11 Migrate `DiffViewerScreen.kt` (3 instances)
- [x] 3.12 Migrate `InlineDiffViewer.kt` (3 instances)
- [x] 3.13 Migrate `FileAttachment.kt` (3 instances)
- [x] 3.14 Migrate `ChatInputBarWithAutocomplete.kt` (3 instances)
- [x] 3.15 Migrate `GitScreen.kt` (2 instances)

## 4. Color Migration (Catppuccin.* → LocalOpenCodeTheme.current.*)

- [x] 4.1 Migrate `CollapsedToolSummary.kt` (15 Catppuccin refs → theme tokens)
- [x] 4.2 Rewrite `MarkdownStyleMapper.kt` to use OpenCodeTheme (19 refs)
- [x] 4.3 Migrate `ChatMessage.kt` (2 refs)
- [x] 4.4 Migrate remaining files using Catppuccin directly

## 5. Fluid-Markdown Integration

- [x] 5.1 Create `ui/theme/opencode/MarkdownMapper.kt` - OpenCodeTheme → MarkdownStyles
- [x] 5.2 Update `StreamingMarkdown.kt` to use new mapper (via MarkdownStyleMapper delegation)
- [ ] 5.3 Verify markdown rendering with multiple themes (catppuccin, gruvbox, tokyonight)

## 6. Settings Integration

- [x] 6.1 Add `themeName: Flow<String>` to SettingsDataStore
- [x] 6.2 Add `setThemeName(name: String)` to SettingsDataStore
- [x] 6.3 Create theme picker UI in VisualSettingsScreen (8 themes available)
- [x] 6.4 Wire theme preference through MainActivity → PocketCodeTheme

## 7. Validation & Testing

- [x] 7.1 Build and run on device (build successful)
- [ ] 7.2 Test theme switching (catppuccin → gruvbox → tokyonight)
- [ ] 7.3 Verify all screens render correctly with sharp corners
- [ ] 7.4 Verify markdown/code blocks use theme colors
- [ ] 7.5 Verify theme persists across app restart
- [ ] 7.6 Visual QA pass on all major screens
