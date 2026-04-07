package dev.blazelight.p4oc.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScrollJankBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollChatNoCompilation() = scrollChat(CompilationMode.None())

    @Test
    fun scrollChatWithBaselineProfile() = scrollChat(
        CompilationMode.Partial(BaselineProfileMode.Require)
    )

    private fun scrollChat(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
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
        val chatList = device.findObject(By.scrollable(true))
        if (chatList != null) {
            chatList.setGestureMargin(device.displayWidth / 5)
            chatList.fling(Direction.DOWN)
            device.waitForIdle()
            chatList.fling(Direction.UP)
            device.waitForIdle()
        }
    }
}
