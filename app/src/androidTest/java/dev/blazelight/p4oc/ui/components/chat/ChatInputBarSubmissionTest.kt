package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.blazelight.p4oc.ui.theme.PocketCodeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatInputBarSubmissionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sendButton_refusedSlashPreservesExactText_andAcceptedSlashClearsOnce() {
        val exactSlash = "/custom exact  arguments "
        var acceptsSubmission by mutableStateOf(false)
        var sendCalls = 0
        var clearedValues = 0

        composeRule.setContent {
            var value by remember { mutableStateOf("") }
            PocketCodeTheme {
                ChatInputBar(
                    value = value,
                    onValueChange = {
                        if (value.isNotEmpty() && it.isEmpty()) clearedValues++
                        value = it
                    },
                    onSend = {
                        sendCalls++
                        acceptsSubmission
                    },
                    isLoading = false,
                    enabled = true,
                )
            }
        }

        val input = composeRule.onNodeWithTag("chat_input")
        input.performTextInput(exactSlash)
        composeRule.onNodeWithTag("send_button").performClick()

        input.assertTextEquals(exactSlash)
        composeRule.runOnIdle {
            assertEquals(1, sendCalls)
            assertEquals(0, clearedValues)
            acceptsSubmission = true
        }

        composeRule.onNodeWithTag("send_button").performClick()

        input.assertTextEquals("")
        composeRule.runOnIdle {
            assertEquals(2, sendCalls)
            assertEquals(1, clearedValues)
        }
    }

    @Test
    fun imeAction_refusedSlashPreservesExactText_andAcceptedSlashClearsOnce() {
        val exactSlash = "/custom keep  exact "
        var acceptsSubmission by mutableStateOf(false)
        var sendCalls = 0
        var clearedValues = 0

        composeRule.setContent {
            var value by remember { mutableStateOf("") }
            PocketCodeTheme {
                ChatInputBar(
                    value = value,
                    onValueChange = {
                        if (value.isNotEmpty() && it.isEmpty()) clearedValues++
                        value = it
                    },
                    onSend = {
                        sendCalls++
                        acceptsSubmission
                    },
                    isLoading = false,
                    enabled = true,
                    enterToSend = true,
                )
            }
        }

        val input = composeRule.onNodeWithTag("chat_input")
        input.performTextInput(exactSlash)
        input.performImeAction()

        input.assertTextEquals(exactSlash)
        composeRule.runOnIdle {
            assertEquals(1, sendCalls)
            assertEquals(0, clearedValues)
            acceptsSubmission = true
        }

        input.performImeAction()

        input.assertTextEquals("")
        composeRule.runOnIdle {
            assertEquals(2, sendCalls)
            assertEquals(1, clearedValues)
        }
    }

    @Test
    fun syncGenerationChange_restoresUnchangedParentSlashAfterAcceptedClear() {
        val exactSlash = "/custom restore  exact "
        var valueSyncGeneration by mutableStateOf(0L)
        var clearedValues = 0

        composeRule.setContent {
            PocketCodeTheme {
                ChatInputBar(
                    value = exactSlash,
                    onValueChange = { if (it.isEmpty()) clearedValues++ },
                    onSend = { true },
                    isLoading = false,
                    enabled = true,
                    valueSyncGeneration = valueSyncGeneration,
                )
            }
        }

        val input = composeRule.onNodeWithTag("chat_input")
        input.assertTextEquals(exactSlash)
        composeRule.onNodeWithTag("send_button").performClick()

        input.assertTextEquals("")
        composeRule.runOnIdle {
            assertEquals(1, clearedValues)
            valueSyncGeneration++
        }

        composeRule.waitForIdle()
        input.assertTextEquals(exactSlash)
    }
}
