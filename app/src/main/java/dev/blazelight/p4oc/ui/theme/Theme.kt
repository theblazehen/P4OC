package dev.blazelight.p4oc.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.blazelight.p4oc.ui.theme.opencode.OpenCodeTheme
import dev.blazelight.p4oc.ui.theme.opencode.ThemeLoader
import dev.blazelight.p4oc.ui.theme.opencode.toMaterial3ColorScheme

/**
 * CompositionLocal for accessing the current OpenCode theme.
 * Use `LocalOpenCodeTheme.current` to access theme colors in composables.
 */
val LocalOpenCodeTheme = staticCompositionLocalOf<OpenCodeTheme> {
    error("No OpenCodeTheme provided - wrap content in PocketCodeTheme")
}

/**
 * TUI shapes - ZERO roundness, full terminal aesthetic.
 * All shapes use RoundedCornerShape(0.dp) for consistent sharp corners.
 */
val TuiShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

@Composable
fun PocketCodeTheme(
    themeName: String = "catppuccin",
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    val openCodeTheme = remember(themeName, darkTheme) {
        ThemeLoader.loadBundledTheme(context, themeName, darkTheme)
    }
    
    val colorScheme = remember(openCodeTheme) {
        openCodeTheme.toMaterial3ColorScheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalOpenCodeTheme provides openCodeTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = TuiShapes,
            typography = Typography,
            content = content
        )
    }
}
