package dev.blazelight.p4oc.ui.theme.opencode

import androidx.compose.ui.graphics.Color

/**
 * Represents a parsed OpenCode theme with all semantic color tokens.
 * Compatible with https://opencode.ai/theme.json schema.
 */
data class OpenCodeTheme(
    val name: String,
    val isDark: Boolean,
    
    // Core (6 required)
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val text: Color,
    val textMuted: Color,
    val background: Color,
    
    // Status (4)
    val error: Color,
    val warning: Color,
    val success: Color,
    val info: Color,
    
    // Surfaces (5)
    val backgroundPanel: Color,
    val backgroundElement: Color,
    val border: Color,
    val borderActive: Color,
    val borderSubtle: Color,
    
    // Diff (12 tokens)
    val diffAdded: Color,
    val diffRemoved: Color,
    val diffContext: Color,
    val diffHunkHeader: Color,
    val diffHighlightAdded: Color,
    val diffHighlightRemoved: Color,
    val diffAddedBg: Color,
    val diffRemovedBg: Color,
    val diffContextBg: Color,
    val diffLineNumber: Color,
    val diffAddedLineNumberBg: Color,
    val diffRemovedLineNumberBg: Color,
    
    // Markdown (14 tokens)
    val markdownText: Color,
    val markdownHeading: Color,
    val markdownLink: Color,
    val markdownLinkText: Color,
    val markdownCode: Color,
    val markdownBlockQuote: Color,
    val markdownEmph: Color,
    val markdownStrong: Color,
    val markdownHorizontalRule: Color,
    val markdownListItem: Color,
    val markdownListEnumeration: Color,
    val markdownImage: Color,
    val markdownImageText: Color,
    val markdownCodeBlock: Color,
    
    // Syntax (9 tokens)
    val syntaxComment: Color,
    val syntaxKeyword: Color,
    val syntaxFunction: Color,
    val syntaxVariable: Color,
    val syntaxString: Color,
    val syntaxNumber: Color,
    val syntaxType: Color,
    val syntaxOperator: Color,
    val syntaxPunctuation: Color
)
