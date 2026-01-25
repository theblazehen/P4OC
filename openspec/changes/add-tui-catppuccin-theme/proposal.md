# Change: TUI-Style UI with Catppuccin Theming

## Why
The current UI uses standard Material Design with generous padding, rounded corners, and verbose message headers. This wastes vertical space on mobile and doesn't match the terminal-focused aesthetic expected from an AI coding assistant client. Users want a dense, TUI-inspired look with modern color schemes.

## What Changes

### Theme System
- Add Catppuccin color palette definitions (all 4 flavors: Latte, Frappé, Macchiato, Mocha)
- Default to Mocha (darkest variant) for dark theme
- Map Catppuccin colors to Material3 color scheme
- Add theme selector in settings (future: just wire up Mocha for now)

### Typography & Density
- Switch to monospace typography throughout (system `FontFamily.Monospace`)
- Reduce line heights for tighter text
- Reduce padding/margins globally (4-12dp → 1-4dp)
- Square corners instead of rounded (0dp radius)

### Message Display
- Remove per-message headers ("You", "claude-sonnet-4-...")
- User messages: Surface2 background + colored left border (Mauve accent)
- Assistant messages: Plain text on Base background, no decorations
- Remove or minimize dividers between messages

### Tool Call Display (Claude HUD style)
- Collapsed by default: one-liner summary like `✓ Read ×3 | ✓ Edit ×2 | ⟳ Bash`
- Expandable to show full tool details on tap
- Group tools by name with occurrence counts
- Status indicators: ✓ (complete), ⟳ (running), ✗ (error), ○ (pending)
- Pending tools keep current Allow/Deny buttons (slightly smaller)

### Markdown Tables
- Fix "light mode" table styling with Catppuccin dark colors
- Configure TablePlugin with Surface0/Surface2 for row backgrounds

## Impact
- Affected specs: None existing (new capabilities)
- Affected code:
  - `ui/theme/Color.kt` - Replace with Catppuccin colors
  - `ui/theme/Theme.kt` - Map to new color scheme
  - `ui/theme/Typography.kt` - Monospace + tight line heights
  - `ui/components/chat/ChatMessage.kt` - Remove headers, add user styling
  - `ui/components/chat/ToolComponents.kt` - Compact collapsed view
  - `ui/components/chat/MarkdownStyleMapper.kt` - Table dark theme
  - New: `ui/theme/CatppuccinColors.kt`
  - New: `ui/components/chat/CollapsedToolSummary.kt`
