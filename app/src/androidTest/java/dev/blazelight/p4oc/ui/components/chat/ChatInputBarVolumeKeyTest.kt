package dev.blazelight.p4oc.ui.components.chat

import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.blazelight.p4oc.ui.theme.PocketCodeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatInputBarVolumeKeyTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun volumeKeysNavigateFocusedComposerWithoutChangingSystemVolume() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val audioManager = instrumentation.targetContext.getSystemService(AudioManager::class.java)
        val draft = "  exact draft  "
        val history = listOf("oldest prompt", "middle prompt", "newest prompt")

        composeRule.setContent {
            var value by remember { mutableStateOf(draft) }
            PocketCodeTheme {
                ChatInputBar(
                    value = value,
                    onValueChange = { value = it },
                    onSend = { true },
                    isLoading = false,
                    enabled = true,
                    requestFocus = true,
                    promptHistory = history,
                    promptHistorySessionId = "volume-key-test-session",
                )
            }
        }

        val input = composeRule.onNodeWithTag("chat_input")
        input.assertIsFocused().assertTextEquals(draft)
        val originalVolumes = readableVolumeSnapshot(audioManager)

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_VOLUME_UP)
        composeRule.waitForIdle()
        input.assertIsFocused().assertTextEquals("newest prompt")
        assertEquals(originalVolumes, readableVolumeSnapshot(audioManager))

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_VOLUME_UP)
        composeRule.waitForIdle()
        input.assertIsFocused().assertTextEquals("middle prompt")
        assertEquals(originalVolumes, readableVolumeSnapshot(audioManager))

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_VOLUME_DOWN)
        composeRule.waitForIdle()
        input.assertIsFocused().assertTextEquals("newest prompt")
        assertEquals(originalVolumes, readableVolumeSnapshot(audioManager))

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_VOLUME_DOWN)
        composeRule.waitForIdle()
        input.assertIsFocused().assertTextEquals(draft)
        assertEquals(originalVolumes, readableVolumeSnapshot(audioManager))
    }

    private fun readableVolumeSnapshot(audioManager: AudioManager): Map<Int, Int> = listOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_ALARM,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_SYSTEM,
    ).associateWith(audioManager::getStreamVolume)
}
