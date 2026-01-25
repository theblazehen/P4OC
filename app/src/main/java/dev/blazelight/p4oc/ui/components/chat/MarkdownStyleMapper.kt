package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.runtime.Composable
import com.fluid.afm.styles.MarkdownStyles
import dev.blazelight.p4oc.ui.theme.opencode.rememberOpenCodeMarkdownStyles
import dev.blazelight.p4oc.ui.theme.opencode.rememberTertiaryOpenCodeMarkdownStyles

/**
 * Maps theme colors to FluidMarkdown's MarkdownStyles.
 * Delegates to OpenCodeTheme-based mapper for consistent theming.
 */
@Composable
fun rememberMarkdownStyles(): MarkdownStyles = rememberOpenCodeMarkdownStyles()

/**
 * Creates MarkdownStyles for tertiary-themed content (like reasoning blocks).
 */
@Composable
fun rememberTertiaryMarkdownStyles(): MarkdownStyles = rememberTertiaryOpenCodeMarkdownStyles()
