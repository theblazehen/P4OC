## ADDED Requirements

### Requirement: Catppuccin Color Palette
The app SHALL provide Catppuccin color palette definitions for all four flavors (Latte, Frappé, Macchiato, Mocha) with all 26 semantic colors per flavor.

#### Scenario: Mocha colors available
- **WHEN** the app uses the Mocha flavor
- **THEN** all Catppuccin Mocha colors (Base, Mantle, Crust, Surface0-2, Overlay0-2, Subtext0-1, Text, and accent colors) SHALL be available

### Requirement: Material3 Color Mapping
The theme system SHALL map Catppuccin colors to Material3 ColorScheme roles to maintain compatibility with Material3 components.

#### Scenario: Dark theme uses Mocha
- **WHEN** dark theme is active
- **THEN** the color scheme SHALL use Catppuccin Mocha colors mapped to Material3 roles

### Requirement: Monospace Typography
The app SHALL use monospace font family for all text styles to achieve TUI aesthetic.

#### Scenario: Body text is monospace
- **WHEN** any text is displayed in the chat
- **THEN** the text SHALL render in the system monospace font
