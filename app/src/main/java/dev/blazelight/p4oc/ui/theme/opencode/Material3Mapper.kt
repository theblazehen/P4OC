package dev.blazelight.p4oc.ui.theme.opencode

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * Maps OpenCodeTheme to Material3 ColorScheme.
 * Enables standard M3 components to use our theme colors automatically.
 */
fun OpenCodeTheme.toMaterial3ColorScheme(): ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = background,
            primaryContainer = backgroundPanel,
            onPrimaryContainer = primary,
            
            secondary = secondary,
            onSecondary = background,
            secondaryContainer = backgroundPanel,
            onSecondaryContainer = secondary,
            
            tertiary = accent,
            onTertiary = background,
            tertiaryContainer = backgroundPanel,
            onTertiaryContainer = accent,
            
            error = error,
            onError = background,
            errorContainer = backgroundPanel,
            onErrorContainer = error,
            
            background = background,
            onBackground = text,
            
            surface = background,
            onSurface = text,
            surfaceVariant = backgroundPanel,
            onSurfaceVariant = textMuted,
            surfaceTint = primary,
            
            inverseSurface = text,
            inverseOnSurface = background,
            inversePrimary = primary,
            
            outline = border,
            outlineVariant = borderSubtle,
            
            scrim = background.copy(alpha = 0.5f)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = background,
            primaryContainer = backgroundPanel,
            onPrimaryContainer = primary,
            
            secondary = secondary,
            onSecondary = background,
            secondaryContainer = backgroundPanel,
            onSecondaryContainer = secondary,
            
            tertiary = accent,
            onTertiary = background,
            tertiaryContainer = backgroundPanel,
            onTertiaryContainer = accent,
            
            error = error,
            onError = background,
            errorContainer = backgroundPanel,
            onErrorContainer = error,
            
            background = background,
            onBackground = text,
            
            surface = background,
            onSurface = text,
            surfaceVariant = backgroundPanel,
            onSurfaceVariant = textMuted,
            surfaceTint = primary,
            
            inverseSurface = text,
            inverseOnSurface = background,
            inversePrimary = primary,
            
            outline = border,
            outlineVariant = borderSubtle,
            
            scrim = background.copy(alpha = 0.5f)
        )
    }
}
