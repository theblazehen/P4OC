package dev.blazelight.p4oc.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * TUI sizing tokens for consistent dimensions.
 * 
 * Design principles:
 * - Smaller icons than Material defaults (20dp vs 24dp)
 * - Compact touch targets (44dp vs 48dp)
 * - Dense list items and buttons
 */
object Sizing {
    // Icons - TUI uses smaller icons
    val iconXs: Dp = 14.dp   // Inline indicators, badges
    val iconSm: Dp = 18.dp   // List item secondary icons
    val iconMd: Dp = 20.dp   // Standard icons (default)
    val iconLg: Dp = 24.dp   // Emphasis icons, action buttons
    val iconXl: Dp = 32.dp   // Section headers
    val iconHero: Dp = 48.dp // Empty state icons

    // Touch targets - Android minimum is 48dp, we use 44dp for density
    val minTouchTarget: Dp = 44.dp
    val touchTargetSm: Dp = 36.dp  // Compact buttons (with hit area extension)
    
    // Buttons
    val buttonHeightSm: Dp = 32.dp
    val buttonHeightMd: Dp = 36.dp
    val buttonHeightLg: Dp = 44.dp
    
    // List items
    val listItemHeightSm: Dp = 40.dp  // Single line
    val listItemHeightMd: Dp = 52.dp  // Two lines
    val listItemHeightLg: Dp = 64.dp  // Three lines / with thumbnail
    
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
