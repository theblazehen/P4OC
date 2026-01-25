package dev.blazelight.p4oc.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
     * Returns appropriate text color for project chip.
     */
    @Composable
    fun textColorForProject(projectId: String): Color {
        val theme = LocalOpenCodeTheme.current
        return theme.text
    }
    
    /**
     * Get color palette based on current theme for dynamic project colors.
     */
    @Composable
    fun themedColorForProject(projectId: String): Color {
        return colorForProject(projectId)
    }
}
