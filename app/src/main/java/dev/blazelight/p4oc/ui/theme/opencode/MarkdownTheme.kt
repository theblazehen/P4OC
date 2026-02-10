package dev.blazelight.p4oc.ui.theme.opencode

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme

/**
 * Maps OpenCodeTheme to mikepenz's MarkdownColors.
 * Uses semantic color tokens for consistent theming.
 */
@Composable
fun rememberOpenCodeMarkdownColors(): MarkdownColors {
    val theme = LocalOpenCodeTheme.current
    
    return markdownColor(
        text = theme.markdownText,
        codeBackground = theme.backgroundElement,
        inlineCodeBackground = theme.backgroundPanel,
        dividerColor = theme.border,
    )
}

/**
 * Creates MarkdownColors for tertiary-themed content (like reasoning blocks).
 */
@Composable
fun rememberTertiaryMarkdownColors(): MarkdownColors {
    val theme = LocalOpenCodeTheme.current
    
    return markdownColor(
        text = theme.success,
        codeBackground = theme.backgroundElement,
        inlineCodeBackground = theme.backgroundPanel,
        dividerColor = theme.border,
    )
}

/**
 * Maps OpenCodeTheme to mikepenz's MarkdownTypography.
 * Glow-inspired: subtle headings, compact spacing, minimal link decoration.
 */
@Composable
fun rememberOpenCodeMarkdownTypography(): MarkdownTypography {
    val materialTypography = MaterialTheme.typography
    
    // Body style - reuse for consistency
    val bodyStyle = materialTypography.bodyMedium.copy(
        lineHeight = 20.sp,
    )
    
    return markdownTypography(
        // Headings - subtle size differences (Glow-inspired)
        // Distinguished by weight more than massive size jumps
        h1 = materialTypography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            lineHeight = 28.sp,
        ),
        h2 = materialTypography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            lineHeight = 24.sp,
        ),
        h3 = materialTypography.titleSmall.copy(
            fontWeight = FontWeight.SemiBold,
            lineHeight = 22.sp,
        ),
        h4 = materialTypography.bodyMedium.copy(
            fontWeight = FontWeight.SemiBold,
            lineHeight = 20.sp,
        ),
        h5 = materialTypography.bodyMedium.copy(
            fontWeight = FontWeight.Medium,
            lineHeight = 20.sp,
        ),
        h6 = materialTypography.bodySmall.copy(
            fontWeight = FontWeight.Medium,
            lineHeight = 18.sp,
        ),
        
        // Body text - compact
        text = bodyStyle,
        paragraph = bodyStyle,
        
        // Code - monospace
        code = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        ),
        inlineCode = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        ),
        
        // Lists - same as body
        list = bodyStyle,
        ordered = bodyStyle,
        bullet = bodyStyle,
        
        // Quotes - same as body (italic handled by renderer)
        quote = bodyStyle,
        
        // Table - slightly smaller
        table = materialTypography.bodySmall.copy(
            lineHeight = 18.sp,
        ),
        
        // Links - subtle underline, slightly bolder (Glow-inspired)
        // Same size as body text, not huge like default
        textLink = TextLinkStyles(
            style = SpanStyle(
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
            )
        ),
    )
}
