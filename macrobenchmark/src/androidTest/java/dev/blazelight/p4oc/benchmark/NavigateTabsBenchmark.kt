package dev.blazelight.p4oc.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigateTabsBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun swipeTabsNoCompilation() = swipeTabs(CompilationMode.None())

    @Test
    fun swipeTabsWithBaselineProfile() = swipeTabs(
        CompilationMode.Partial(BaselineProfileMode.Require)
    )

    private fun swipeTabs(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = "dev.blazelight.p4oc",
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = compilationMode,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        }
    ) {
        // HorizontalPager is used in main screen; perform a few horizontal swipes
        val content = device.findObject(androidx.test.uiautomator.By.res("android", "content"))
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        // Fallback if content view is not found
        if (content != null) {
            content.setGestureMargin(displayWidth / 5)
            content.swipe(Direction.LEFT, 50)
            device.waitForIdle()
            content.swipe(Direction.RIGHT, 50)
            device.waitForIdle()
        } else {
            device.swipe((displayWidth * 0.8).toInt(), displayHeight / 2, (displayWidth * 0.2).toInt(), displayHeight / 2, 30)
            device.waitForIdle()
            device.swipe((displayWidth * 0.2).toInt(), displayHeight / 2, (displayWidth * 0.8).toInt(), displayHeight / 2, 30)
            device.waitForIdle()
        }
    }
}
