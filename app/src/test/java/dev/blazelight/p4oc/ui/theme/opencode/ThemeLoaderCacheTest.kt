package dev.blazelight.p4oc.ui.theme.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ThemeLoaderCacheTest {

    @Before
    fun setUp() {
        ThemeLoader.clearCacheForTest()
    }

    @Test
    fun `getCachedTheme returns null when absent`() {
        assertNull(ThemeLoader.getCachedTheme("catppuccin", isDark = true))
    }

    @Test
    fun `cache key distinguishes dark and light`() {
        val darkTheme = createFallbackTheme(isDark = true)
        val lightTheme = createFallbackTheme(isDark = false)

        ThemeLoader.cacheThemeForTest("catppuccin", isDark = true, theme = darkTheme)
        ThemeLoader.cacheThemeForTest("catppuccin", isDark = false, theme = lightTheme)

        assertEquals(darkTheme, ThemeLoader.getCachedTheme("catppuccin", isDark = true))
        assertEquals(lightTheme, ThemeLoader.getCachedTheme("catppuccin", isDark = false))
        assertNotSame(
            ThemeLoader.getCachedTheme("catppuccin", isDark = true),
            ThemeLoader.getCachedTheme("catppuccin", isDark = false),
        )
    }

    @Test
    fun `cached theme round trips correctly`() {
        val theme = createFallbackTheme(isDark = true).copy(name = "custom")

        ThemeLoader.cacheThemeForTest("custom", isDark = true, theme = theme)

        assertEquals(theme, ThemeLoader.getCachedTheme("custom", isDark = true))
    }
}
