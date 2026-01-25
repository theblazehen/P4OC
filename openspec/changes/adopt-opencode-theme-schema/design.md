## Context

The app currently uses hardcoded Catppuccin colors with no runtime theme switching. OpenCode CLI defines a standard theme.json schema with 49 semantic color tokens that enables portable theming across tools. This change adopts that schema to enable theme portability and community theme support.

### Constraints
- Must maintain Material 3 component behavior (just restyle, not replace)
- Must integrate with fluid-markdown library (Java-based, uses `MarkdownStyles`)
- Zero roundness requirement - all shapes must be `RectangleShape`
- Theme loading must be fast enough to not cause visual flash on startup

### Stakeholders
- Users who want theme customization
- Users who use OpenCode CLI and want consistent theming
- Developers maintaining the theme system

## Goals / Non-Goals

### Goals
- Adopt OpenCode theme.json schema for all 49 semantic tokens
- Enable runtime theme switching without app restart
- Ship 8+ bundled themes from OpenCode ecosystem
- Integrate theming with fluid-markdown library
- Enforce zero-roundness TUI aesthetic globally

### Non-Goals
- Custom theme editor UI (import JSON only)
- Base16 theme import (future consideration)
- Dynamic color (Material You) support
- Per-screen theme overrides

## Decisions

### Decision: Use CompositionLocal for Theme Distribution
**What:** Provide `OpenCodeTheme` via `LocalOpenCodeTheme` CompositionLocal
**Why:** 
- Compose-idiomatic pattern for cross-cutting concerns
- Enables recomposition when theme changes
- Works with `remember` for performance
**Alternatives considered:**
- Singleton object: No recomposition, harder to test
- ViewModel: Overkill, theme is UI concern

### Decision: Map OpenCodeTheme → M3 ColorScheme
**What:** Convert our 49-token theme to M3's ~30 color roles
**Why:**
- Standard M3 components "just work" with our theme
- Don't need to wrap every component
- Gradual migration - can use either system
**Mapping strategy:**
| OpenCode Token | M3 Role |
|----------------|---------|
| primary | primary |
| secondary | secondary |
| accent | tertiary |
| error | error |
| background | background, surface |
| backgroundPanel | surfaceContainer |
| backgroundElement | surfaceContainerLow |
| text | onSurface, onBackground |
| textMuted | onSurfaceVariant |
| border | outline |
| borderSubtle | outlineVariant |

### Decision: Bundled Themes as JSON Assets
**What:** Copy theme JSON files to `assets/themes/` directory
**Why:**
- Simple file loading via `AssetManager`
- Easy to add/remove themes
- Same format as OpenCode CLI
**Alternatives considered:**
- Kotlin data objects: Not portable, compile-time only
- Remote fetch: Requires network, slower startup

### Decision: TuiShapes with All RectangleShape
**What:** Override M3 Shapes to use `RectangleShape` everywhere
**Why:**
- User requirement: "not a single bit of roundness"
- Sessions screen already uses `RectangleShape` - extend everywhere
- Gives app distinctive TUI character
**Components affected:**
- Card, Button, Surface, TextField, Dialog, FAB, Chip, etc.

### Decision: Rewrite fluid-markdown Integration
**What:** Create new `MarkdownMapper` that maps OpenCodeTheme → MarkdownStyles
**Why:**
- Current `MarkdownStyleMapper.kt` hardcodes Catppuccin colors
- Need to use markdown-specific tokens (markdownHeading, markdownCode, etc.)
- Need to use syntax tokens for code highlighting
**Integration points:**
- `MarkdownStyles.paragraphStyle()` ← theme.markdownText
- `MarkdownStyles.linkStyle()` ← theme.markdownLink
- `MarkdownStyles.codeStyle()` ← theme.markdownCode, theme.backgroundElement
- `MarkdownStyles.blockQuoteStyle()` ← theme.markdownBlockQuote

## Risks / Trade-offs

### Risk: Theme Loading Performance
**Risk:** Parsing JSON on startup could delay first frame
**Mitigation:** 
- Parse in `remember` block, cached across recompositions
- Themes are small (~5KB), parsing is fast
- Could add LRU cache if needed

### Risk: Incomplete Token Coverage
**Risk:** Some UI elements might not have appropriate theme tokens
**Mitigation:**
- OpenCode schema is comprehensive (49 tokens)
- Can derive missing colors (e.g., `success.copy(alpha = 0.3f)` for success background)
- Document any gaps for future schema updates

### Risk: Breaking Visual Consistency
**Risk:** Migrating 30+ files could introduce visual bugs
**Mitigation:**
- Systematic migration by file (shapes first, then colors)
- Visual QA pass on all screens
- Test with 3+ themes to catch hardcoded colors

### Trade-off: M3 ColorScheme Mapping Limitations
**Trade-off:** M3 has fewer color roles than OpenCode's 49 tokens
**Accepted because:**
- Most components only need primary/secondary/surface colors
- Specialized components (diff viewer, code blocks) use OpenCodeTheme directly
- Hybrid approach works well in practice

## Migration Plan

### Phase 1: Infrastructure (No Visual Changes)
1. Create OpenCodeTheme data class
2. Create ThemeLoader
3. Create Material3Mapper
4. Add bundled theme files
5. **Checkpoint:** Unit tests pass

### Phase 2: Wire Up Theme System
1. Update Theme.kt with LocalOpenCodeTheme
2. Add TuiShapes
3. Wire theme loading
4. **Checkpoint:** App compiles, uses catppuccin theme

### Phase 3: Shape Migration
1. Migrate all 15 files with RoundedCornerShape
2. **Checkpoint:** All corners are sharp

### Phase 4: Color Migration
1. Migrate Catppuccin references to theme tokens
2. Delete old color files
3. **Checkpoint:** No Catppuccin imports outside theme package

### Phase 5: Markdown Integration
1. Create MarkdownMapper
2. Update StreamingMarkdown
3. **Checkpoint:** Markdown renders with theme colors

### Phase 6: Settings & Persistence
1. Add DataStore preference
2. Add theme picker UI
3. **Checkpoint:** Theme selection persists

### Rollback
- Each phase is independently revertible via git
- No database migrations required
- No API changes required

## Open Questions

1. **Theme preview in picker:** Show live preview or just color swatches?
   - Recommendation: Color swatches initially, live preview as enhancement

2. **Custom theme import:** File picker or paste JSON?
   - Recommendation: File picker via Storage Access Framework

3. **Theme for terminal screen:** Use app theme or terminal-specific palette?
   - Recommendation: Use app theme, terminal has its own ANSI color handling
