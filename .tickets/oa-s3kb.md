---
id: oa-s3kb
status: closed
deps: [oa-ina0]
links: []
created: 2026-02-22T12:01:13Z
type: task
priority: 2
assignee: Jasmin Le Roux
---
# Design system consistency: new tokens + hardcoded dp migration + accessibility

## Problem

The app has well-defined design tokens (Spacing.kt, Sizing.kt) but many UI files use hardcoded .dp values instead. Additionally, 19 clickable modifiers lack semantic Role, 6 functional icons have null contentDescription, and zero testTag usage.

## What To Do

### Part A: Add New Tokens

**Spacing.kt** — add:
- `hairline = 1.dp` — for fine pixel-perfect TUI layouts (borders, tiny padding). Currently `Arrangement.spacedBy(1.dp)` appears 4+ times.

**Sizing.kt** — add:
- `indicatorDot = 6.dp` — small status dots (currently mixed 5/6/8dp)
- `indicatorDotActive = 8.dp` — active state for status dots
- `diffGutterWidth = 40.dp` — line number gutters in diff views
- `panelWidthSm = 80.dp` — small fixed-width columns
- `panelWidthMd = 120.dp` — medium panels
- `panelWidthLg = 180.dp` — large panels (agent selectors)

### Part B: Migrate Hardcoded .dp Values

For each file, replace hardcoded values with tokens. Key migrations:

**TabBar.kt** (9 violations):
- `height(28.dp)` → `Sizing.chipHeight`
- `size(8.dp)` / `size(6.dp)` → `Sizing.indicatorDotActive` / `Sizing.indicatorDot`
- `widthIn(max = 80.dp)` → `Sizing.panelWidthSm`

**DiffViewerScreen.kt** (9 violations):
- `padding(vertical = 1.dp)` → `Spacing.hairline`
- `width(40.dp)` → `Sizing.diffGutterWidth`
- `thickness = 1.dp` → `Sizing.strokeMd`

**ModelAgentSelector.kt** (9 violations):
- `BorderStroke(1.dp)` → `Sizing.strokeMd`
- `height(36.dp)` → `Sizing.buttonHeightMd`
- `widthIn(max = 180.dp)` → `Sizing.panelWidthLg`
- `thickness = 0.5.dp` → `Sizing.dividerThickness`

**ChatInputBar.kt** (7 violations):
- `height(32.dp)` → `Sizing.buttonHeightSm`
- `border(1.dp)` → `Sizing.strokeMd`
- `widthIn(max = 120.dp)` → `Sizing.panelWidthMd`
- `heightIn(min = 40.dp)` → `Sizing.textFieldHeightSm`

**ExpandedWidgets.kt** (9 violations):
- `height(32.dp)` → `Sizing.buttonHeightSm`
- `height(36.dp)` → `Sizing.buttonHeightMd`

**Other files** (InlineQuestionCard, ToolComponents, SlashCommandsPopup, PermissionDialogEnhanced, ToolGroupWidget, TodoTracker, ContextUsageDisplay): Apply same pattern — match values to existing tokens or new ones above.

**ChatScreen.kt**: Replace `Arrangement.spacedBy(1.dp)` → `Arrangement.spacedBy(Spacing.hairline)`

### Part C: Accessibility Fixes

**Clickable Roles** (19 instances):
- Add `role = Role.Button` to actionable clickable modifiers
- Add `role = Role.Tab` for tab bar items
- Files to audit: ServerScreen.kt, TabBar.kt, SessionListScreen.kt, ChatScreen.kt, ToolComponents.kt, and others with `Modifier.clickable`

**Content Descriptions** (6 null icons):
- ChatInputBar.kt: Send button icon needs description
- TabBar.kt: Tab icons need descriptions
- ToolComponents.kt: Expand/collapse chevrons need descriptions
- Add `stringResource()` descriptions for all functional icons

**Test Tags** (key elements):
- `Modifier.testTag("chat_input")` on ChatInputBar
- `Modifier.testTag("message_list")` on ChatScreen LazyColumn
- `Modifier.testTag("agent_selector")` on ModelAgentSelector
- `Modifier.testTag("send_button")` on send button

### Part D: Update AGENTS.md Convention
- Clarify that `MaterialTheme.typography` IS the custom typography and is fine to use
- Add `Spacing.hairline` to the tokens reference
- Add new Sizing tokens to the reference

## Acceptance Criteria
- [ ] New tokens added to Spacing.kt and Sizing.kt
- [ ] Hardcoded .dp values replaced with tokens across all listed files
- [ ] 19 clickable modifiers have semantic Role
- [ ] 6 functional icons have contentDescription
- [ ] Key test tags added
- [ ] AGENTS.md conventions updated
- [ ] `./gradlew :app:compileDebugKotlin` passes

## Acceptance Criteria

New tokens added. Hardcoded dp migrated. Accessibility fixed. Conventions updated. Compile clean.

