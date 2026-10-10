package dev.blazelight.p4oc.terminal

/**
 * Owns a terminal's rendered text size. Pinch steps apply immediately and are reported for saving.
 * Each save comes back later as a requested size; those echoes are matched against the pinch sizes
 * still in flight so an older echo can never snap the size back over a newer pinch. Any requested
 * size that is not an echo is a genuine settings change and applies.
 */
internal class TerminalTextSizer(
    initialSp: Int,
    /** Applies the size to the view; false when no view is attached yet. */
    private val applySize: (Int) -> Boolean,
    private val onPinchSize: (Int) -> Unit,
) {
    var appliedSp = TerminalFontSize.clamp(initialSp)
        private set
    private var requestedSp = appliedSp
    private val pendingPinchSaves = ArrayDeque<Int>()

    fun request(sizeSp: Int) {
        val clamped = TerminalFontSize.clamp(sizeSp)
        // The view update block can rerun with an unchanged value; only new values are emissions.
        if (clamped == requestedSp) return
        requestedSp = clamped
        val echoIndex = pendingPinchSaves.indexOf(clamped)
        if (echoIndex >= 0) {
            // Our own save (and any older ones it supersedes) came back: nothing to apply.
            repeat(echoIndex + 1) { pendingPinchSaves.removeFirst() }
            return
        }
        pendingPinchSaves.clear()
        if (clamped != appliedSp) apply(clamped)
    }

    fun onPinch(scaleFactor: Float): Float {
        val step = TerminalFontSize.pinchStep(scaleFactor, appliedSp)
        if (step.sizeSp != appliedSp && apply(step.sizeSp)) {
            pendingPinchSaves.addLast(step.sizeSp)
            onPinchSize(step.sizeSp)
        }
        return step.scaleFactor
    }

    private fun apply(sizeSp: Int): Boolean {
        if (!applySize(sizeSp)) return false
        appliedSp = sizeSp
        return true
    }
}
