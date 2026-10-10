package dev.blazelight.p4oc.terminal

/** Terminal text size bounds and pinch-to-zoom stepping, in sp. */
object TerminalFontSize {
    const val DEFAULT_SP = 14
    const val MIN_SP = 8
    const val MAX_SP = 32

    /** Pinch distance (as a scale factor away from 1.0) that changes the size by one step. */
    private const val PINCH_STEP_THRESHOLD = 0.1f

    data class PinchStep(val scaleFactor: Float, val sizeSp: Int)

    fun clamp(sizeSp: Int): Int = sizeSp.coerceIn(MIN_SP, MAX_SP)

    /**
     * Termux's TerminalView accumulates the pinch into [scaleFactor] and keeps whatever the client
     * returns. Once the pinch passes the threshold, step the size by 1sp and reset the factor to 1.0
     * so the next step needs another deliberate pinch.
     */
    fun pinchStep(scaleFactor: Float, currentSp: Int): PinchStep = when {
        scaleFactor > 1f + PINCH_STEP_THRESHOLD -> PinchStep(1f, clamp(currentSp + 1))
        scaleFactor < 1f - PINCH_STEP_THRESHOLD -> PinchStep(1f, clamp(currentSp - 1))
        else -> PinchStep(scaleFactor, currentSp)
    }
}
