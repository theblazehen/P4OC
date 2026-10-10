package dev.blazelight.p4oc.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalFontSizeTest {
    @Test
    fun `small pinch movement keeps accumulating without resizing`() {
        assertEquals(TerminalFontSize.PinchStep(1.05f, 14), TerminalFontSize.pinchStep(1.05f, 14))
        assertEquals(TerminalFontSize.PinchStep(0.95f, 14), TerminalFontSize.pinchStep(0.95f, 14))
    }

    @Test
    fun `pinching past the threshold steps one sp and resets the gesture`() {
        assertEquals(TerminalFontSize.PinchStep(1f, 15), TerminalFontSize.pinchStep(1.2f, 14))
        assertEquals(TerminalFontSize.PinchStep(1f, 13), TerminalFontSize.pinchStep(0.8f, 14))
    }

    @Test
    fun `pinching stops at the size limits`() {
        assertEquals(TerminalFontSize.PinchStep(1f, TerminalFontSize.MAX_SP), TerminalFontSize.pinchStep(2f, 32))
        assertEquals(TerminalFontSize.PinchStep(1f, TerminalFontSize.MIN_SP), TerminalFontSize.pinchStep(0.5f, 8))
    }

    @Test
    fun `out of range stored sizes are clamped`() {
        assertEquals(TerminalFontSize.MIN_SP, TerminalFontSize.clamp(2))
        assertEquals(TerminalFontSize.MAX_SP, TerminalFontSize.clamp(99))
        assertEquals(18, TerminalFontSize.clamp(18))
    }
}
