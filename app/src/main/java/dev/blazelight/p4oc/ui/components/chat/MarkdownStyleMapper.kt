package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import com.fluid.afm.styles.MarkdownStyles

/**
 * Maps Material3 theme colors to FluidMarkdown's MarkdownStyles.
 */
@Composable
fun rememberMarkdownStyles(): MarkdownStyles {
    val colorScheme = MaterialTheme.colorScheme
    
    return remember(colorScheme) {
        MarkdownStyles.getDefaultStyles().apply {
            // Paragraph text color
            paragraphStyle().fontColor(colorScheme.onSurface.toArgb())
            
            // Link styling
            linkStyle().fontColor(colorScheme.primary.toArgb())
            
            // Code styling (both inline and block)
            codeStyle()
                .inlineFontColor(colorScheme.primary.toArgb())
                .inlineCodeBackgroundColor(colorScheme.surfaceContainerHighest.toArgb())
                .codeFontColor(colorScheme.onSurface.toArgb())
            
            // Table styling
            tableStyle()
                .borderColor(colorScheme.outline.toArgb())
                .headerBackgroundColor(colorScheme.surfaceContainerHigh.toArgb())
                .bodyBackgroundColor(colorScheme.surface.toArgb())
                .fontColor(colorScheme.onSurface.toArgb())
            
            // Blockquote styling
            blockQuoteStyle()
                .lineColor(colorScheme.outline.toArgb())
                .fontColor(colorScheme.onSurfaceVariant.toArgb())
        }
    }
}

/**
 * Creates MarkdownStyles for tertiary-themed content (like reasoning blocks).
 */
@Composable
fun rememberTertiaryMarkdownStyles(): MarkdownStyles {
    val colorScheme = MaterialTheme.colorScheme
    
    return remember(colorScheme) {
        MarkdownStyles.getDefaultStyles().apply {
            paragraphStyle().fontColor(colorScheme.onTertiaryContainer.toArgb())
            linkStyle().fontColor(colorScheme.tertiary.toArgb())
            codeStyle()
                .inlineFontColor(colorScheme.tertiary.toArgb())
                .inlineCodeBackgroundColor(colorScheme.tertiaryContainer.toArgb())
                .codeFontColor(colorScheme.onTertiaryContainer.toArgb())
        }
    }
}
