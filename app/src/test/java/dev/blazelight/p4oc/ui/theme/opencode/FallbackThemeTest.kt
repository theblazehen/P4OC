package dev.blazelight.p4oc.ui.theme.opencode

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackThemeTest {

    @Test
    fun `createFallbackTheme dark returns complete theme`() {
        val theme = createFallbackTheme(isDark = true)

        assertEquals("catppuccin", theme.name)
        assertTrue(theme.isDark)
        assertNotEquals(Color.Magenta, theme.background)
        assertNotEquals(Color.Magenta, theme.text)
        assertNotEquals(Color.Magenta, theme.primary)
        assertNotEquals(Color.Magenta, theme.border)
        assertNotEquals(Color.Magenta, theme.error)
        assertNotEquals(Color.Magenta, theme.syntaxPunctuation)
        assertNotEquals(Color.Magenta, theme.markdownCodeBlock)
        assertNotEquals(Color.Magenta, theme.diffRemovedLineNumberBg)
    }

    @Test
    fun `createFallbackTheme light returns complete theme`() {
        val theme = createFallbackTheme(isDark = false)

        assertEquals("catppuccin", theme.name)
        assertFalse(theme.isDark)
        assertNotEquals(Color.Magenta, theme.background)
        assertNotEquals(Color.Magenta, theme.text)
        assertNotEquals(Color.Magenta, theme.primary)
        assertNotEquals(Color.Magenta, theme.border)
        assertNotEquals(Color.Magenta, theme.error)
        assertNotEquals(Color.Magenta, theme.syntaxPunctuation)
        assertNotEquals(Color.Magenta, theme.markdownCodeBlock)
        assertNotEquals(Color.Magenta, theme.diffRemovedLineNumberBg)
    }

    @Test
    fun `createFallbackTheme dark matches expected catppuccin values`() {
        val theme = createFallbackTheme(isDark = true)

        assertEquals(Color(0xFF1E1E2E), theme.background)
        assertEquals(Color(0xFFCDD6F4), theme.text)
        assertEquals(Color(0xFF89B4FA), theme.primary)
        assertEquals(Color(0xFF313244), theme.border)
        assertEquals(Color(0xFFF38BA8), theme.error)
    }

    @Test
    fun `createFallbackTheme light matches expected catppuccin values`() {
        val theme = createFallbackTheme(isDark = false)

        assertEquals(Color(0xFFEFF1F5), theme.background)
        assertEquals(Color(0xFF4C4F69), theme.text)
        assertEquals(Color(0xFF1E66F5), theme.primary)
        assertEquals(Color(0xFFCCD0DA), theme.border)
        assertEquals(Color(0xFFD20F39), theme.error)
    }
}
