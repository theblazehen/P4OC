package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.TokenUsage
import dev.blazelight.p4oc.ui.theme.PocketCodeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatMessageTokenUsageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun completedAssistantDisplaysTokenUsageAndCost() {
        setAssistantUsage(
            tokens = TokenUsage(
                input = Int.MAX_VALUE,
                output = 1,
                reasoning = 2,
                cacheRead = 3,
                cacheWrite = 4,
            ),
            cost = 0.0123,
        )

        assertUsageRow("2147483657 total", "2147483647/1", "\$0.0123")
    }

    @Test
    fun reasoningOnlyUsageDisplaysNonzeroTotalAndInputOutputDetail() {
        setAssistantUsage(
            tokens = TokenUsage(input = 0, output = 0, reasoning = 17),
            cost = 0.0,
        )

        assertUsageRow("17 total", "0/0")
    }

    @Test
    fun cacheOnlyUsageDisplaysCombinedNonzeroTotalAndInputOutputDetail() {
        setAssistantUsage(
            tokens = TokenUsage(input = 0, output = 0, cacheRead = 19, cacheWrite = 23),
            cost = 0.0,
        )

        assertUsageRow("42 total", "0/0")
    }

    @Test
    fun positiveCostOnlyDisplaysUsageRowAndLocaleStableCost() {
        setAssistantUsage(
            tokens = TokenUsage(input = 0, output = 0),
            cost = 0.5,
        )

        assertUsageRow("0 total", "0/0", "\$0.5000")
    }

    @Test
    fun tinyPositiveCostDisplaysMinimumCostIndicator() {
        setAssistantUsage(
            tokens = TokenUsage(input = 0, output = 0),
            cost = 0.00001,
        )

        assertUsageRow("0 total", "0/0", "<\$0.0001")
    }

    @Test
    fun assistantWithZeroUsageOmitsUsageRow() {
        setAssistantUsage(
            tokens = TokenUsage(
                input = 0,
                output = 0,
                reasoning = 0,
                cacheRead = 0,
                cacheWrite = 0,
            ),
            cost = 0.0,
        )

        composeRule.onNodeWithTag("assistant_token_usage").assertDoesNotExist()
    }

    private fun setAssistantUsage(tokens: TokenUsage, cost: Double) {
        composeRule.setContent {
            PocketCodeTheme {
                AssistantMessages(
                    messagesWithParts = listOf(
                        completedAssistantMessage(
                            tokens = tokens,
                            cost = cost,
                        ),
                    ),
                    onToolApprove = {},
                    onToolDeny = {},
                    onToolAlways = {},
                )
            }
        }
    }

    private fun assertUsageRow(total: String, inputOutput: String, cost: String? = null) {
        composeRule.onNodeWithTag("assistant_token_usage").assertIsDisplayed()
        composeRule.onNodeWithText(total, substring = false).assertIsDisplayed()
        composeRule.onNodeWithText(inputOutput, substring = false).assertIsDisplayed()
        cost?.let { composeRule.onNodeWithText(it, substring = false).assertIsDisplayed() }
    }

    private fun completedAssistantMessage(
        tokens: TokenUsage,
        cost: Double,
    ) = MessageWithParts(
        message = Message.Assistant(
            id = "assistant-message",
            sessionID = "session",
            createdAt = 1L,
            completedAt = 2L,
            parentID = "user-message",
            providerID = "provider",
            modelID = "model",
            mode = "build",
            agent = "build",
            cost = cost,
            tokens = tokens,
            finish = "stop",
        ),
        parts = emptyList(),
    )
}
