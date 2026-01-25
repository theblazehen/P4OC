package dev.blazelight.p4oc.ui.theme.opencode

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class ThemeLoaderTest {

    private val catppuccinThemeJson = """
    {
      "name": "catppuccin",
      "defs": {
        "rosewater": "#f5e0dc",
        "flamingo": "#f2cdcd",
        "pink": "#f5c2e7",
        "mauve": "#cba6f7",
        "red": "#f38ba8",
        "maroon": "#eba0ac",
        "peach": "#fab387",
        "yellow": "#f9e2af",
        "green": "#a6e3a1",
        "teal": "#94e2d5",
        "sky": "#89dceb",
        "sapphire": "#74c7ec",
        "blue": "#89b4fa",
        "lavender": "#b4befe",
        "text": "#cdd6f4",
        "subtext1": "#bac2de",
        "subtext0": "#a6adc8",
        "overlay2": "#9399b2",
        "overlay1": "#7f849c",
        "overlay0": "#6c7086",
        "surface2": "#585b70",
        "surface1": "#45475a",
        "surface0": "#313244",
        "base": "#1e1e2e",
        "mantle": "#181825",
        "crust": "#11111b"
      },
      "theme": {
        "primary": "blue",
        "secondary": "mauve",
        "accent": "pink",
        "text": "text",
        "textMuted": "subtext0",
        "background": "base",
        "error": "red",
        "warning": "yellow",
        "success": "green",
        "info": "sapphire",
        "backgroundPanel": "mantle",
        "backgroundElement": "surface0",
        "border": "surface1",
        "borderActive": "surface2",
        "borderSubtle": "surface0",
        "diffAdded": "green",
        "diffRemoved": "red",
        "diffContext": "subtext0",
        "diffHunkHeader": "overlay0",
        "diffHighlightAdded": "teal",
        "diffHighlightRemoved": "maroon",
        "diffAddedBg": "#1a2f1a",
        "diffRemovedBg": "#2f1a1a",
        "diffContextBg": "base",
        "diffLineNumber": "overlay0",
        "diffAddedLineNumberBg": "#1a3f1a",
        "diffRemovedLineNumberBg": "#3f1a1a",
        "markdownText": "text",
        "markdownHeading": "lavender",
        "markdownLink": "blue",
        "markdownLinkText": "sapphire",
        "markdownCode": "peach",
        "markdownBlockQuote": "overlay1",
        "markdownEmph": "text",
        "markdownStrong": "text",
        "markdownHorizontalRule": "surface2",
        "markdownListItem": "text",
        "markdownListEnumeration": "overlay0",
        "markdownImage": "blue",
        "markdownImageText": "sapphire",
        "markdownCodeBlock": "text",
        "syntaxComment": "overlay0",
        "syntaxKeyword": "mauve",
        "syntaxFunction": "blue",
        "syntaxVariable": "red",
        "syntaxString": "green",
        "syntaxNumber": "peach",
        "syntaxType": "yellow",
        "syntaxOperator": "sky",
        "syntaxPunctuation": "overlay2"
      }
    }
    """.trimIndent()

    @Test
    fun `parseTheme parses catppuccin theme correctly`() {
        val theme = ThemeLoader.parseTheme(catppuccinThemeJson, "catppuccin", isDark = true)
        
        assertEquals("catppuccin", theme.name)
        assertTrue(theme.isDark)
        // primary references "blue" -> #89b4fa
        assertEquals(Color(0xFF89b4fa), theme.primary)
        // secondary references "mauve" -> #cba6f7
        assertEquals(Color(0xFFcba6f7), theme.secondary)
        // text references "text" -> #cdd6f4
        assertEquals(Color(0xFFcdd6f4), theme.text)
    }

    @Test
    fun `parseTheme resolves color references from defs`() {
        val theme = ThemeLoader.parseTheme(catppuccinThemeJson, "catppuccin", isDark = true)
        
        // background references "base" -> #1e1e2e
        assertEquals(Color(0xFF1e1e2e), theme.background)
        // backgroundPanel references "mantle" -> #181825
        assertEquals(Color(0xFF181825), theme.backgroundPanel)
        // error references "red" -> #f38ba8
        assertEquals(Color(0xFFf38ba8), theme.error)
    }

    @Test
    fun `parseTheme handles direct hex values`() {
        val theme = ThemeLoader.parseTheme(catppuccinThemeJson, "catppuccin", isDark = true)
        
        // diffAddedBg is a direct hex: #1a2f1a
        assertEquals(Color(0xFF1a2f1a), theme.diffAddedBg)
        // diffRemovedBg is a direct hex: #2f1a1a
        assertEquals(Color(0xFF2f1a1a), theme.diffRemovedBg)
    }

    @Test
    fun `parseTheme parses all syntax colors`() {
        val theme = ThemeLoader.parseTheme(catppuccinThemeJson, "catppuccin", isDark = true)
        
        // syntaxKeyword references "mauve" -> #cba6f7
        assertEquals(Color(0xFFcba6f7), theme.syntaxKeyword)
        // syntaxString references "green" -> #a6e3a1
        assertEquals(Color(0xFFa6e3a1), theme.syntaxString)
        // syntaxComment references "overlay0" -> #6c7086
        assertEquals(Color(0xFF6c7086), theme.syntaxComment)
    }

    @Test
    fun `parseTheme parses all markdown colors`() {
        val theme = ThemeLoader.parseTheme(catppuccinThemeJson, "catppuccin", isDark = true)
        
        // markdownLink references "blue" -> #89b4fa
        assertEquals(Color(0xFF89b4fa), theme.markdownLink)
        // markdownCode references "peach" -> #fab387
        assertEquals(Color(0xFFfab387), theme.markdownCode)
    }

    @Test
    fun `parseTheme parses all diff colors`() {
        val theme = ThemeLoader.parseTheme(catppuccinThemeJson, "catppuccin", isDark = true)
        
        // diffAdded references "green" -> #a6e3a1
        assertEquals(Color(0xFFa6e3a1), theme.diffAdded)
        // diffRemoved references "red" -> #f38ba8
        assertEquals(Color(0xFFf38ba8), theme.diffRemoved)
    }

    @Test
    fun `parseTheme handles dark mode boolean`() {
        val darkTheme = ThemeLoader.parseTheme(catppuccinThemeJson, "test", isDark = true)
        val lightTheme = ThemeLoader.parseTheme(catppuccinThemeJson, "test", isDark = false)
        
        assertTrue(darkTheme.isDark)
        assertFalse(lightTheme.isDark)
    }

    @Test
    fun `parseTheme preserves theme name`() {
        val theme1 = ThemeLoader.parseTheme(catppuccinThemeJson, "custom-name", isDark = true)
        val theme2 = ThemeLoader.parseTheme(catppuccinThemeJson, "another-name", isDark = true)
        
        assertEquals("custom-name", theme1.name)
        assertEquals("another-name", theme2.name)
    }
}
