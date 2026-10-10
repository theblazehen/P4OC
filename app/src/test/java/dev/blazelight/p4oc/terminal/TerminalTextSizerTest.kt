package dev.blazelight.p4oc.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalTextSizerTest {
    private val rendered = mutableListOf<Int>()
    private val saved = mutableListOf<Int>()
    private val sizer = TerminalTextSizer(
        initialSp = 14,
        applySize = { sp -> rendered.add(sp) },
        onPinchSize = { sp -> saved += sp },
    )

    @Test
    fun `late echoes of earlier pinch saves never snap the size back`() {
        sizer.onPinch(PINCH_OUT)
        sizer.onPinch(PINCH_OUT)
        sizer.request(15) // echo of the first save arrives after the second pinch
        sizer.request(16)

        assertEquals(listOf(15, 16), rendered)
        assertEquals(listOf(15, 16), saved)
        assertEquals(16, sizer.appliedSp)
    }

    @Test
    fun `a pinch after a late echo keeps advancing from the rendered size`() {
        sizer.onPinch(PINCH_OUT)
        sizer.onPinch(PINCH_OUT)
        sizer.request(15)
        sizer.onPinch(PINCH_OUT)

        assertEquals(17, sizer.appliedSp)
        assertEquals(listOf(15, 16, 17), saved)
    }

    @Test
    fun `a conflated echo of only the latest save clears older pending saves`() {
        sizer.onPinch(PINCH_OUT)
        sizer.onPinch(PINCH_OUT)
        sizer.request(16)
        sizer.request(15) // a later genuine settings change back to 15 must apply

        assertEquals(listOf(15, 16, 15), rendered)
    }

    @Test
    fun `an update rerun with the same echoed value does not drop newer pending saves`() {
        sizer.onPinch(PINCH_OUT)
        sizer.onPinch(PINCH_OUT)
        sizer.onPinch(PINCH_IN)
        sizer.request(15)
        sizer.request(15) // unrelated recomposition reruns the update block
        sizer.request(16)

        assertEquals(listOf(15, 16, 15), rendered)
        assertEquals(15, sizer.appliedSp)
    }

    @Test
    fun `a settings change applies and the same value again is ignored`() {
        sizer.request(20)
        sizer.request(20)

        assertEquals(listOf(20), rendered)
        assertEquals(emptyList<Int>(), saved)
    }

    @Test
    fun `nothing is saved when no view can render the pinch`() {
        val detached = TerminalTextSizer(14, applySize = { false }, onPinchSize = { saved += it })

        detached.onPinch(PINCH_OUT)

        assertEquals(14, detached.appliedSp)
        assertEquals(emptyList<Int>(), saved)
    }

    private companion object {
        const val PINCH_OUT = 1.3f
        const val PINCH_IN = 0.7f
    }
}
