package dev.blazelight.p4oc.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

/**
 * Curated color palette for project chips.
 * Colors are chosen to be visually distinct and work on both light and dark backgrounds.
 */
object ProjectColors {
    
    private val palette = listOf(
        Color(0xFF4A90D9), // blue
        Color(0xFFE67E22), // orange
        Color(0xFF2ECC71), // green
        Color(0xFF9B59B6), // purple
        Color(0xFF1ABC9C), // teal
        Color(0xFFE74C3C), // red
        Color(0xFFE91E63), // pink
        Color(0xFF5C6BC0), // indigo
    )
    
    /**
     * Returns a deterministic color for a given project ID.
     * The same project ID will always return the same color.
     */
    fun colorForProject(projectId: String): Color {
        return palette[projectId.hashCode().absoluteValue % palette.size]
    }
    
    /**
     * Returns a slightly darkened version of the project color for text on light chip backgrounds.
     */
    fun textColorForProject(projectId: String): Color {
        return Color.White
    }
    
    /**
     * Get color palette based on current theme for dynamic project colors.
     * Can be extended in the future to use theme accent colors.
     */
    @Composable
    fun themedColorForProject(projectId: String): Color {
        // For now, use the static palette
        // Future: Could derive from LocalOpenCodeTheme.current
        return colorForProject(projectId)
    }
}
