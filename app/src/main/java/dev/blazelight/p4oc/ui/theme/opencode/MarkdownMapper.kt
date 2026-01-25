package dev.blazelight.p4oc.ui.theme.opencode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import com.fluid.afm.styles.MarkdownStyles
import com.fluid.afm.utils.ParseUtil
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme

/**
 * Maps OpenCodeTheme to fluid-markdown's MarkdownStyles.
 * Uses semantic color tokens for consistent theming.
 */
@Composable
fun rememberOpenCodeMarkdownStyles(): MarkdownStyles {
    val theme = LocalOpenCodeTheme.current
    
    return remember(theme) {
        MarkdownStyles.getDefaultStyles().apply {
            // Paragraph - tight spacing for TUI density
            paragraphStyle()
                .fontColor(theme.markdownText.toArgb())
                .lineHeight(ParseUtil.parseDp("24px"))  // Increased from 20px to prevent overlap
                .paragraphSpacing(ParseUtil.parseDp("6px"))  // Increased from 4px
            
            // Link styling
            linkStyle().fontColor(theme.markdownLink.toArgb())
            
            // Code styling - use theme tokens
            codeStyle().apply {
                // Inline code (`backticks`)
                inlineFontColor(theme.markdownCode.toArgb())
                inlineCodeBackgroundColor(theme.backgroundPanel.toArgb())
                inlineCodeBackgroundRadius(ParseUtil.parseDp("0px"))  // Sharp corners
                inlinePaddingVertical(ParseUtil.parseDp("1px"))
                inlinePaddingHorizontal(ParseUtil.parseDp("3px"))
                
                // Code blocks
                codeFontColor(theme.markdownCodeBlock.toArgb())
                codeBackgroundColor(theme.backgroundElement.toArgb())
                codeBackgroundRadius(ParseUtil.parseDp("0px"))  // Sharp corners
                titleFontColor(theme.textMuted.toArgb())
                titleBackgroundColor(theme.backgroundPanel.toArgb())
                borderColor(theme.borderSubtle.toArgb())
                borderWidth(ParseUtil.parseDp("0px"))  // No border
                blockLeading(ParseUtil.parseDp("4px"))
                lightIcon(false)
                drawBorder(false)
                setShowTitle(false)  // No title bar for density
            }
            
            // Table styling
            tableStyle()
                .borderColor(theme.border.toArgb())
                .headerBackgroundColor(theme.backgroundPanel.toArgb())
                .bodyBackgroundColor(theme.background.toArgb())
                .fontColor(theme.text.toArgb())
            
            // Blockquote styling
            blockQuoteStyle()
                .lineColor(theme.borderActive.toArgb())
                .fontColor(theme.markdownBlockQuote.toArgb())
            
            // Headings - tight spacing for TUI
            setTitleStyle(0, com.fluid.afm.styles.TitleStyle.create(1.4f)
                .paragraphSpacing(ParseUtil.parseDp("4px"))
                .paragraphSpacingBefore(ParseUtil.parseDp("6px")))
            setTitleStyle(1, com.fluid.afm.styles.TitleStyle.create(1.3f)
                .paragraphSpacing(ParseUtil.parseDp("3px"))
                .paragraphSpacingBefore(ParseUtil.parseDp("5px")))
            setTitleStyle(2, com.fluid.afm.styles.TitleStyle.create(1.2f)
                .paragraphSpacing(ParseUtil.parseDp("3px"))
                .paragraphSpacingBefore(ParseUtil.parseDp("4px")))
            setTitleStyle(3, com.fluid.afm.styles.TitleStyle.create(1.1f)
                .paragraphSpacing(ParseUtil.parseDp("2px"))
                .paragraphSpacingBefore(ParseUtil.parseDp("3px")))
            setTitleStyle(4, com.fluid.afm.styles.TitleStyle.create(1.05f)
                .paragraphSpacing(ParseUtil.parseDp("2px"))
                .paragraphSpacingBefore(ParseUtil.parseDp("2px")))
            setTitleStyle(5, com.fluid.afm.styles.TitleStyle.create(1.0f)
                .paragraphSpacing(ParseUtil.parseDp("2px"))
                .paragraphSpacingBefore(ParseUtil.parseDp("2px")))
        }
    }
}

/**
 * Creates MarkdownStyles for tertiary-themed content (like reasoning blocks).
 * Uses the success/info colors for a distinct visual style.
 */
@Composable
fun rememberTertiaryOpenCodeMarkdownStyles(): MarkdownStyles {
    val theme = LocalOpenCodeTheme.current
    
    return remember(theme) {
        MarkdownStyles.getDefaultStyles().apply {
            paragraphStyle()
                .fontColor(theme.success.toArgb())
                .lineHeight(ParseUtil.parseDp("22px"))  // Increased from 18px
                .paragraphSpacing(ParseUtil.parseDp("4px"))  // Increased from 3px
            
            linkStyle().fontColor(theme.info.toArgb())
            
            codeStyle()
                .inlineFontColor(theme.success.toArgb())
                .inlineCodeBackgroundColor(theme.backgroundPanel.toArgb())
                .inlineCodeBackgroundRadius(ParseUtil.parseDp("0px"))
                .codeFontColor(theme.success.toArgb())
                .codeBackgroundColor(theme.backgroundElement.toArgb())
                .codeBackgroundRadius(ParseUtil.parseDp("0px"))
        }
    }
}
