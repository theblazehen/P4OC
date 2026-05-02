package dev.blazelight.p4oc.core.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.blazelight.p4oc.core.datastore.VibrationPattern
import dev.blazelight.p4oc.core.log.AppLog

private const val TAG = "HapticFeedback"
private const val THROTTLE_MS = 2000L
private const val TICK_DURATION_MS = 35L
private const val CLICK_DURATION_MS = 50L
private const val HEAVY_CLICK_DURATION_MS = 90L
private const val LONG_PULSE_DURATION_MS = 200L

/**
 * Best-effort haptic feedback. Honors system vibrate settings (DND/silent).
 * Throttled to avoid double-buzz when multiple completion signals fire.
 */
class HapticFeedback(private val context: Context) {

    @Volatile
    private var lastVibrateMs: Long = 0L

    fun vibrate(pattern: VibrationPattern, throttle: Boolean = true) {
        if (pattern == VibrationPattern.None) return

        val now = System.currentTimeMillis()
        if (throttle && now - lastVibrateMs < THROTTLE_MS) return

        try {
            val vibrator = resolveVibrator() ?: return
            if (!vibrator.hasVibrator()) return
            if (throttle) lastVibrateMs = now

            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    vibrator.vibrate(pattern.toEffect())
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    vibrator.vibrate(pattern.toFallbackEffect())
                }
                else -> {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern.legacyDurationMs())
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "vibrate failed: ${e.message}", e)
        }
    }

    fun preview(pattern: VibrationPattern) {
        vibrate(pattern, throttle = false)
    }

    private fun resolveVibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private fun VibrationPattern.toEffect(): VibrationEffect = when (this) {
        VibrationPattern.None -> VibrationEffect.createOneShot(0L, 0)
        VibrationPattern.Tick -> predefinedOrFallback(VibrationEffect.EFFECT_TICK, TICK_DURATION_MS)
        VibrationPattern.Click -> predefinedOrFallback(VibrationEffect.EFFECT_CLICK, CLICK_DURATION_MS)
        VibrationPattern.HeavyClick -> predefinedOrFallback(VibrationEffect.EFFECT_HEAVY_CLICK, HEAVY_CLICK_DURATION_MS)
        VibrationPattern.DoubleClick -> predefinedOrFallback(
            VibrationEffect.EFFECT_DOUBLE_CLICK,
            fallbackTimings = longArrayOf(0L, 45L, 80L, 45L),
        )
        VibrationPattern.LongPulse -> VibrationEffect.createOneShot(
            LONG_PULSE_DURATION_MS,
            VibrationEffect.DEFAULT_AMPLITUDE,
        )
        VibrationPattern.DoubleLongPulse -> VibrationEffect.createWaveform(
            longArrayOf(0L, LONG_PULSE_DURATION_MS, 120L, LONG_PULSE_DURATION_MS),
            -1,
        )
    }

    private fun VibrationPattern.toFallbackEffect(): VibrationEffect = when (this) {
        VibrationPattern.None -> VibrationEffect.createOneShot(0L, 0)
        VibrationPattern.Tick -> VibrationEffect.createOneShot(TICK_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        VibrationPattern.Click -> VibrationEffect.createOneShot(CLICK_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        VibrationPattern.HeavyClick -> VibrationEffect.createOneShot(HEAVY_CLICK_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        VibrationPattern.DoubleClick -> VibrationEffect.createWaveform(longArrayOf(0L, 45L, 80L, 45L), -1)
        VibrationPattern.LongPulse -> VibrationEffect.createOneShot(LONG_PULSE_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        VibrationPattern.DoubleLongPulse -> VibrationEffect.createWaveform(
            longArrayOf(0L, LONG_PULSE_DURATION_MS, 120L, LONG_PULSE_DURATION_MS),
            -1,
        )
    }

    private fun predefinedOrFallback(effectId: Int, fallbackDurationMs: Long): VibrationEffect = runCatching {
        VibrationEffect.createPredefined(effectId)
    }.getOrElse {
        VibrationEffect.createOneShot(fallbackDurationMs, VibrationEffect.DEFAULT_AMPLITUDE)
    }

    private fun predefinedOrFallback(effectId: Int, fallbackTimings: LongArray): VibrationEffect = runCatching {
        VibrationEffect.createPredefined(effectId)
    }.getOrElse {
        VibrationEffect.createWaveform(fallbackTimings, -1)
    }

    private fun VibrationPattern.legacyDurationMs(): Long = when (this) {
        VibrationPattern.None -> 0L
        VibrationPattern.Tick -> TICK_DURATION_MS
        VibrationPattern.Click -> CLICK_DURATION_MS
        VibrationPattern.HeavyClick -> HEAVY_CLICK_DURATION_MS
        VibrationPattern.DoubleClick -> CLICK_DURATION_MS
        VibrationPattern.LongPulse -> LONG_PULSE_DURATION_MS
        VibrationPattern.DoubleLongPulse -> LONG_PULSE_DURATION_MS
    }
}
