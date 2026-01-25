## ADDED Requirements

### Requirement: OpenCode Theme Schema Support
The application SHALL support loading themes conforming to the OpenCode theme.json schema (https://opencode.ai/theme.json), enabling portable theming between OpenCode CLI and the Android client.

#### Scenario: Load bundled theme
- **GIVEN** the app includes bundled theme JSON files in assets
- **WHEN** the user selects a theme by name (e.g., "catppuccin", "gruvbox")
- **THEN** the theme loader parses the JSON and provides all 49 semantic color tokens
- **AND** the UI updates to reflect the new theme colors

#### Scenario: Handle dark/light mode variants
- **GIVEN** a theme defines separate dark and light color values
- **WHEN** the system dark mode setting changes
- **THEN** the appropriate color variant is selected for each token
- **AND** the UI re-renders with the correct colors

#### Scenario: Resolve color references
- **GIVEN** a theme JSON uses references in the `defs` block (e.g., `"primary": "darkBlue"`)
- **WHEN** the theme is loaded
- **THEN** references are resolved to their hex color values
- **AND** invalid references cause graceful fallback to default theme

### Requirement: Semantic Color Token System
The theming system SHALL provide 49 semantic color tokens organized into logical groups: core (6), status (4), surfaces (5), diff (12), markdown (14), and syntax (9).

#### Scenario: Access theme colors in composables
- **GIVEN** a composable needs theme-aware colors
- **WHEN** it accesses `LocalOpenCodeTheme.current`
- **THEN** it receives an `OpenCodeTheme` instance with all color tokens
- **AND** colors are Compose `Color` objects ready for use

#### Scenario: Map theme to Material3 ColorScheme
- **GIVEN** an `OpenCodeTheme` is loaded
- **WHEN** `PocketCodeTheme` composable initializes
- **THEN** the theme is mapped to a Material3 `ColorScheme`
- **AND** standard M3 components use the theme colors automatically

### Requirement: Zero-Roundness Shape System
The application SHALL use `RectangleShape` for all UI components to achieve a consistent TUI aesthetic with zero rounded corners.

#### Scenario: All shapes are rectangular
- **GIVEN** the app uses Material3 components (Card, Button, Surface, TextField, Dialog)
- **WHEN** these components are rendered
- **THEN** all corners are sharp (0dp radius)
- **AND** no rounded corners appear anywhere in the UI

#### Scenario: FAB is square
- **GIVEN** the app displays a FloatingActionButton
- **WHEN** the FAB is rendered
- **THEN** it appears as a square button, not circular

### Requirement: Fluid-Markdown Theme Integration
The theming system SHALL propagate OpenCode theme colors to the fluid-markdown library for consistent markdown rendering.

#### Scenario: Markdown uses theme colors
- **GIVEN** markdown content is rendered in chat messages
- **WHEN** the theme changes
- **THEN** headings, links, code blocks, and blockquotes use theme-defined colors
- **AND** inline code uses `markdownCode` token color
- **AND** code block backgrounds use `backgroundElement` token color

#### Scenario: Syntax highlighting uses theme colors
- **GIVEN** a code block with syntax highlighting is rendered
- **WHEN** the theme defines syntax tokens (syntaxKeyword, syntaxString, etc.)
- **THEN** the highlighted code uses those colors

### Requirement: Theme Persistence
The application SHALL persist the user's theme selection and restore it on app launch.

#### Scenario: Save theme preference
- **GIVEN** the user selects a theme in settings
- **WHEN** the selection is confirmed
- **THEN** the theme name is saved to DataStore
- **AND** the preference survives app restarts

#### Scenario: Restore theme on launch
- **GIVEN** a theme preference was previously saved
- **WHEN** the app launches
- **THEN** the saved theme is loaded before first render
- **AND** the UI appears in the correct theme without flash

### Requirement: Bundled Theme Library
The application SHALL include at least 8 popular themes from the OpenCode ecosystem as bundled assets.

#### Scenario: Access bundled themes
- **GIVEN** the app is installed
- **WHEN** the user opens theme settings
- **THEN** they see a list of available themes including: catppuccin, catppuccin-macchiato, catppuccin-frappe, gruvbox, tokyonight, nord, dracula, opencode
- **AND** each theme can be previewed and selected
