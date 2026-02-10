package dev.blazelight.p4oc.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.absoluteValue

/**
 * Curated color palette for project chips.
 * Colors are derived from the current OpenCode theme accent colors.
 */
object ProjectColors {
    
    /**
     * Returns a deterministic color for a given project ID using theme accent colors.
     * The same project ID will always return the same color within a theme.
     */
    @Composable
    fun colorForProject(projectId: String): Color {
        val theme = LocalOpenCodeTheme.current
        val palette = listOf(
            theme.primary,
            theme.secondary,
            theme.accent,
            theme.success,
            theme.warning,
            theme.info,
            theme.error,
            theme.syntaxFunction,
        )
        return palette[projectId.hashCode().absoluteValue % palette.size]
    }
    
    /**
     * Returns appropriate text color for project chip based on background luminance.
     * Uses luminance check on the actual tag color to pick contrasting text.
     */
    @Composable
    fun textColorForProject(projectId: String): Color {
        val bgColor = colorForProject(projectId)
        val theme = LocalOpenCodeTheme.current
        // Use luminance to pick contrasting text - dark text on bright bg, light text on dark bg
        return if (bgColor.luminance() > 0.4f) {
            theme.background // Dark text for bright backgrounds
        } else {
            theme.text // Light text for dark backgrounds
        }
    }
    
    /**
     * Get color palette based on current theme for dynamic project colors.
     */
    @Composable
    fun themedColorForProject(projectId: String): Color {
        return colorForProject(projectId)
    }
}
