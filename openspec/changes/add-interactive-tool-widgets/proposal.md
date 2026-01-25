# Change: Add Interactive Tool Widgets

## Why
Tool calls in chat currently render as static log entries. Users can't easily see what a tool did without reading walls of text. HITL tools like `question` require modal overlays that break flow. We want tool calls to be **glanceable, interactive widgets** with progressive disclosure.

## What Changes
- Add three-tier display mode for tool calls: **Oneline** → **Compact** → **Expanded**
- Tapping cycles through states (oneline→compact→expanded→oneline)
- HITL tools (e.g., `question`) auto-expand and embed interactive UI
- Global setting to control default display mode
- Widgets are compact (~2-3cm / 75-115dp tall) except for large content (up to viewport height)

## Impact
- Affected specs: `tool-widgets` (new capability)
- Affected code:
  - `ui/screens/chat/ChatScreen.kt` - Tool rendering
  - `ui/components/question/InlineQuestionCard.kt` - Refactor into widget
  - `ui/components/toolwidgets/` - New widget components
  - `data/local/SettingsDataStore.kt` - Default state setting
  - `ui/screens/settings/VisualSettingsScreen.kt` - Setting UI
