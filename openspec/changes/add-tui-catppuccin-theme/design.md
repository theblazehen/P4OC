# Design: TUI-Style UI with Catppuccin Theming

## Context
P4OC is a mobile client for OpenCode, an AI coding assistant. The target users are developers who appreciate terminal aesthetics and information density. The current Material Design UI, while functional, feels generic and wastes precious vertical screen space on mobile devices.

## Goals / Non-Goals

### Goals
- Dense, TUI-inspired visual aesthetic
- Catppuccin color palette (starting with Mocha)
- Monospace typography throughout
- Compact tool call display (collapsed by default)
- Reduced padding/margins everywhere
- Square corners

### Non-Goals
- Full theme picker UI (just wire Mocha for now)
- Custom font bundling (use system monospace)
- Box-drawing characters for borders (just the "vibe", not literal TUI)

## Decisions

### Decision 1: System Monospace over Custom Fonts
**Choice**: Use `FontFamily.Monospace` instead of bundling JetBrains Mono.
**Rationale**: Saves ~200KB APK size, can revisit later.

### Decision 2: Catppuccin → Material3 Mapping

| Catppuccin | Material3 Role |
|------------|----------------|
| Base | background, surface |
| Mantle | surfaceContainer |
| Surface0/1/2 | surfaceContainerHigh, surfaceVariant |
| Text | onSurface |
| Subtext0/1 | onSurfaceVariant |
| Blue | primary |
| Mauve | secondary |
| Green | tertiary |
| Red | error |

### Decision 3: Collapsed Tool Summary
Format: `✓ Read ×3 | ✓ Edit ×2 | ⟳ Bash | ○ Glob`
- Shows progress at a glance
- Tap to expand for full details
- Status icons: ✓ complete, ⟳ running, ✗ error, ○ pending

### Decision 4: User Message Styling
Surface2 background + 2dp Mauve left border, no header text.

### Decision 5: Pending Tool Buttons
Keep current Allow/Deny buttons, just reduce padding slightly.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Readability at small sizes | Test on devices, keep 44dp touch targets |
| Density vs accessibility | Can add "comfortable" option later |
