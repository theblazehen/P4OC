package dev.blazelight.p4oc.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

/**
 * TUI animation tokens - snappy, not floaty.
 * 
 * Design principles:
 * - Fast response to user input
 * - Minimal decorative animation
 * - Functional motion only
 */
object Motion {
    // Durations (ms)
    const val instant: Int = 0
    const val fast: Int = 100      // Micro-interactions (button press, toggle)
    const val normal: Int = 200    // Standard transitions (fade, slide)
    const val slow: Int = 350      // Reveals, expansions
    const val emphasis: Int = 500  // Onboarding, empty→content transitions
    
    // Easing curves
    /** Standard easing for enter animations - fast start, slow end */
    val easeOut: Easing = FastOutSlowInEasing
    
    /** Exit animations - slow start, fast end */
    val easeIn: Easing = FastOutLinearInEasing
    
    /** Emphasis animations - slow both ends */
    val easeInOut: Easing = LinearOutSlowInEasing
    
    /** Progress indicators, continuous animations */
    val linear: Easing = LinearEasing
    
    // Pre-built animation specs
    
    /** Fade in/out animations */
    fun <T> fadeSpec(): TweenSpec<T> = tween(fast, easing = easeOut)
    
    /** Slide animations */
    fun <T> slideSpec(): TweenSpec<T> = tween(normal, easing = easeOut)
    
    /** Expand/collapse animations */
    fun <T> expandSpec(): TweenSpec<T> = tween(slow, easing = easeOut)
    
    /** Content change animations */
    fun <T> contentSpec(): TweenSpec<T> = tween(normal, easing = easeInOut)
    
    /** Quick feedback (button press, checkbox) */
    fun <T> quickSpec(): TweenSpec<T> = tween(fast, easing = easeOut)
    
    // Float-specific specs for common use cases
    val fadeSpecFloat: TweenSpec<Float> = tween(fast, easing = easeOut)
    val slideSpecFloat: TweenSpec<Float> = tween(normal, easing = easeOut)
    val expandSpecFloat: TweenSpec<Float> = tween(slow, easing = easeOut)
}
