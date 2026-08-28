package dev.blazelight.p4oc.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * TUI sizing tokens for consistent dimensions.
 *
 * Design principles:
 * - Smaller icons than Material defaults (20dp vs 24dp)
 * - Compact visuals inside Android's 48dp minimum touch targets
 * - Dense list items and buttons
 */
object Sizing {
    // Icons - TUI uses smaller icons
    val iconXxs: Dp = 10.dp // Tiny status indicators
    val iconXs: Dp = 14.dp // Inline indicators, badges
    val iconSm: Dp = 18.dp // List item secondary icons
    val iconMd: Dp = 20.dp // Standard icons (default)
    val iconAction: Dp = 22.dp // Action bar icons
    val iconLg: Dp = 24.dp // Emphasis icons, action buttons
    val iconXl: Dp = 32.dp // Section headers
    val iconHero: Dp = 64.dp // Empty state icons
    val iconHeroLg: Dp = 96.dp // Large decorative icons

    // Touch targets - Android accessibility minimum is 48dp.
    val minTouchTarget: Dp = 48.dp
    val touchTargetSm: Dp = 36.dp // Compact buttons (with hit area extension)

    // Buttons
    val buttonHeightSm: Dp = 32.dp
    val buttonHeightMd: Dp = 36.dp
    val buttonHeightLg: Dp = 44.dp

    // List items
    val listItemHeightSm: Dp = 40.dp // Single line
    val listItemHeightMd: Dp = 52.dp // Two lines
    val listItemHeightLg: Dp = 64.dp // Three lines / with thumbnail

    // Status indicators
    val indicatorDot: Dp = 6.dp
    val indicatorDotActive: Dp = 8.dp

    // Avatars / Badges
    val avatarXs: Dp = 20.dp
    val avatarSm: Dp = 24.dp
    val avatarMd: Dp = 32.dp
    val avatarLg: Dp = 40.dp

    // Chips / Tags
    val chipHeight: Dp = 28.dp

    // Dividers
    val dividerThickness: Dp = 0.5.dp

    // Input fields
    val textFieldHeight: Dp = 48.dp
    val textFieldHeightSm: Dp = 40.dp

    // IconButton sizes
    val iconButtonSm: Dp = 36.dp
    val iconButtonMd: Dp = 40.dp
    val iconButtonLg: Dp = 44.dp

    // Fixed-width panels
    val diffGutterWidth: Dp = 40.dp
    val panelWidthSm: Dp = 80.dp
    val panelWidthMd: Dp = 120.dp
    val panelWidthLg: Dp = 180.dp
    val serverFilterCardWidth: Dp = 104.dp

    // Scrollable embedded content (e.g. inline full-text blocks)
    val embeddedScrollMaxHeight: Dp = 360.dp

    // Chat media
    val chatAttachmentPreviewMaxHeight: Dp = 240.dp

    // Component-specific
    // Knob-slider switch (design): 36x20 track, 14x14 sliding knob, 2dp inset
    val switchTrackWidth: Dp = 36.dp
    val switchTrackHeight: Dp = 20.dp
    val switchKnobSize: Dp = 14.dp
    val switchKnobInset: Dp = 2.dp
    val tabBarHeight: Dp = 30.dp // Tab bar strip height (design)
    val treeIndent: Dp = 24.dp // Session tree indentation per level
    val chipMaxWidth: Dp = 150.dp // Project chip max width

    // Progress indicators
    val progressBarHeight: Dp = 8.dp // Standard LinearProgressIndicator
    val progressBarHeightSm: Dp = 4.dp // Compact LinearProgressIndicator
    val progressStrokeWidth: Dp = 3.dp // Circular progress arc stroke

    // Corner radius (we use 0 for TUI, but keep tokens for potential future use)
    val radiusNone: Dp = 0.dp
    val radiusSm: Dp = 2.dp
    val radiusMd: Dp = 4.dp
    val radiusLg: Dp = 8.dp

    // Stroke widths
    val strokeThin: Dp = 0.5.dp
    val strokeMd: Dp = 1.dp
    val strokeThick: Dp = 2.dp
}
