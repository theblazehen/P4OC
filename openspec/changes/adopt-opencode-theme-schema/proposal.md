# Change: Adopt OpenCode Theme Schema for Portable Theming

## Why
The current theming system uses hardcoded Catppuccin colors directly in components (`Catppuccin.Blue`, `Catppuccin.Surface0`). This works but has limitations:
1. **No runtime theme switching** - colors are compile-time constants
2. **Not portable** - themes can't be shared with OpenCode CLI
3. **Limited palette** - only Catppuccin Mocha/Latte, no community themes
4. **Inconsistent** - some components use M3 colorScheme, others use Catppuccin directly

OpenCode defines a standard theme.json schema (https://opencode.ai/theme.json) with 49 semantic color tokens. Adopting this schema enables:
- 30+ community themes work out-of-box (Gruvbox, Tokyo Night, Nord, Dracula, etc.)
- Users can import custom themes from OpenCode CLI
- Consistent theming across CLI and mobile app
- Future base16 support as an import format

## What Changes

### Theme Infrastructure (New)
- **OpenCodeTheme data class** - 49 semantic color tokens matching schema
- **ThemeLoader** - Parse theme.json files, resolve color references, handle dark/light
- **Material3Mapper** - Convert OpenCodeTheme → M3 ColorScheme
- **MarkdownMapper** - Convert OpenCodeTheme → fluid-markdown MarkdownStyles
- **Bundled themes** - Ship 8 popular themes as assets

### Shape System Overhaul
- **BREAKING**: All shapes become `RectangleShape` (zero roundness)
- `TuiShapes` replaces default M3 shapes
- Every `RoundedCornerShape(X.dp)` instance → `RectangleShape`
- FAB becomes square

### Color System Migration
- Replace all `Catppuccin.*` references with `LocalOpenCodeTheme.current.*`
- Delete `CatppuccinColors.kt`, `Color.kt`
- Rewrite `ProjectColors.kt` to use theme accent colors
- Wire theme selection through `PocketCodeTheme` composable

### Fluid-Markdown Integration
- Create `OpenCodeMarkdownMapper` using theme tokens
- Map markdown/syntax highlighting tokens to MarkdownStyles
- Replace existing `rememberMarkdownStyles()` implementation

### Settings Integration
- Add theme preference to DataStore
- Add theme picker UI in settings
- Support importing external theme.json files

## Impact
- **Affected specs**: None existing (new capability)
- **Breaking changes**: 
  - All UI corners become sharp (intentional design decision)
  - `Catppuccin` object removed from public API
- **Affected code** (63 shape changes + 93 color changes across ~30 files):
  - `ui/theme/*.kt` - Major rewrites
  - `ui/components/chat/*.kt` - Shape and color migrations
  - `ui/screens/**/*.kt` - Shape migrations
  - `ui/components/chat/MarkdownStyleMapper.kt` - Full rewrite
  - `core/datastore/SettingsDataStore.kt` - Add theme preference
  - New: `ui/theme/opencode/` package (4 files)
  - New: `assets/themes/*.json` (8 theme files)
