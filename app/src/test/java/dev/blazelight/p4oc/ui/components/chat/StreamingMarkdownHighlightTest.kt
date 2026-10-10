package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingMarkdownHighlightTest {
    private val fallback = Color(0xFF123456)
    private val number = Color(0xFFD19A66)
    private val punctuation = Color(0xFFABB2BF)

    @Test
    fun `overlapping number and punctuation ranges keep the code text intact`() {
        val code = "x * 1.20)"
        val numberStart = code.indexOf("1.20")
        // The highlighter reports the literal and the '.' inside it separately (issue #74).
        val highlights = listOf(
            CodeHighlight(numberStart, numberStart + 4, number),
            CodeHighlight(numberStart + 1, numberStart + 2, punctuation),
            CodeHighlight(code.length - 1, code.length, punctuation),
        )

        val styled = styleCode(code, highlights, fallback)

        assertEquals(code, styled.text)
        assertEquals(number, styled.colorAt(numberStart + 1))
        assertEquals(number, styled.colorAt(numberStart + 3))
        assertEquals(punctuation, styled.colorAt(code.length - 1))
        assertEquals(fallback, styled.colorAt(0))
    }

    @Test
    fun `out of range and inverted highlights are ignored`() {
        val styled = styleCode(
            "abc",
            listOf(CodeHighlight(2, 99, number), CodeHighlight(2, 1, punctuation), CodeHighlight(-5, 1, number)),
            fallback,
        )

        assertEquals("abc", styled.text)
        assertEquals(number, styled.colorAt(0))
        assertEquals(fallback, styled.colorAt(1))
        assertEquals(number, styled.colorAt(2))
    }

    /** Compose draws later span styles over earlier ones, so the last covering span decides the color. */
    private fun AnnotatedString.colorAt(index: Int): Color =
        spanStyles.last { index >= it.start && index < it.end }.item.color
}
