package dev.blazelight.p4oc.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * TUI-style spacing system for dense, terminal-like layouts.
 * 
 * Design principles:
 * - Minimal padding (4-8dp instead of 16-24dp)
 * - Tight vertical spacing between elements
 * - Compact cards and list items
 * - Maximum information density
 */
@Immutable
data class TuiSpacing(
    // Base units
    val none: Dp = 0.dp,
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 6.dp,
    val md: Dp = 8.dp,
    val lg: Dp = 12.dp,
    val xl: Dp = 16.dp,  // Use sparingly - only for major sections
    
    // Semantic spacing
    /** Internal padding for cards/containers */
    val cardPadding: Dp = 8.dp,
    /** Padding for empty state containers */
    val emptyStatePadding: Dp = 12.dp,
    /** Padding for dialogs */
    val dialogPadding: Dp = 12.dp,
    /** Screen edge padding */
    val screenPadding: Dp = 8.dp,
    /** Bottom sheet padding */
    val sheetPadding: Dp = 8.dp,
    /** List item internal padding */
    val listItemPadding: Dp = 8.dp,
    /** Input field padding */
    val inputPadding: Dp = 8.dp,
    
    // Vertical arrangements
    /** Between items in a list */
    val listItemSpacing: Dp = 4.dp,
    /** Between sections */
    val sectionSpacing: Dp = 8.dp,
    /** Between elements in a card */
    val cardContentSpacing: Dp = 6.dp,
    /** Between form fields */
    val formFieldSpacing: Dp = 6.dp,
    /** Empty state element spacing */
    val emptyStateSpacing: Dp = 8.dp,
    
    // Horizontal arrangements
    /** Between inline elements */
    val inlineSpacing: Dp = 6.dp,
    /** Between icon and text */
    val iconTextSpacing: Dp = 6.dp,
    /** Between buttons */
    val buttonSpacing: Dp = 6.dp
) {
    // Convenience functions for common patterns
    
    /** Content padding for lazy lists */
    val listContentPadding: PaddingValues
        get() = PaddingValues(screenPadding)
    
    /** Vertical arrangement for list items */
    val listArrangement: Arrangement.Vertical
        get() = Arrangement.spacedBy(listItemSpacing)
    
    /** Vertical arrangement for card content */
    val cardArrangement: Arrangement.Vertical
        get() = Arrangement.spacedBy(cardContentSpacing)
    
    /** Vertical arrangement for form fields */
    val formArrangement: Arrangement.Vertical
        get() = Arrangement.spacedBy(formFieldSpacing)
    
    /** Vertical arrangement for sections */
    val sectionArrangement: Arrangement.Vertical
        get() = Arrangement.spacedBy(sectionSpacing)
    
    /** Horizontal arrangement for inline elements */
    val inlineArrangement: Arrangement.Horizontal
        get() = Arrangement.spacedBy(inlineSpacing)
    
    /** Horizontal arrangement for buttons */
    val buttonArrangement: Arrangement.Horizontal
        get() = Arrangement.spacedBy(buttonSpacing)
}

/**
 * Default TUI spacing values - dense terminal style
 */
val TuiDefaults = TuiSpacing()

/**
 * CompositionLocal for accessing TUI spacing throughout the app.
 * Use `LocalTuiSpacing.current` to access spacing values.
 */
val LocalTuiSpacing = staticCompositionLocalOf { TuiDefaults }

/**
 * Convenience accessor for TUI spacing.
 * Usage: `Spacing.cardPadding`, `Spacing.listArrangement`, etc.
 */
object Spacing {
    val current: TuiSpacing
        @Composable
        get() = LocalTuiSpacing.current
    
    // Direct accessors for common values (non-composable contexts)
    val none = TuiDefaults.none
    val xxs = TuiDefaults.xxs
    val xs = TuiDefaults.xs
    val sm = TuiDefaults.sm
    val md = TuiDefaults.md
    val lg = TuiDefaults.lg
    val xl = TuiDefaults.xl
    
    val cardPadding = TuiDefaults.cardPadding
    val screenPadding = TuiDefaults.screenPadding
    val dialogPadding = TuiDefaults.dialogPadding
    val listItemPadding = TuiDefaults.listItemPadding
    val emptyStatePadding = TuiDefaults.emptyStatePadding
    
    val listItemSpacing = TuiDefaults.listItemSpacing
    val sectionSpacing = TuiDefaults.sectionSpacing
    val cardContentSpacing = TuiDefaults.cardContentSpacing
    val inlineSpacing = TuiDefaults.inlineSpacing
}
