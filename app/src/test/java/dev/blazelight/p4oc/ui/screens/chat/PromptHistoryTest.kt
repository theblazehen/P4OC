package dev.blazelight.p4oc.ui.screens.chat

import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.ModelRef
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.TokenUsage
import dev.blazelight.p4oc.ui.components.chat.PromptHistoryNavigator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PromptHistoryTest {

    @Test
    fun `older walks newest to oldest and newer restores exact draft`() {
        val navigator = PromptHistoryNavigator()
        val history = listOf("oldest", "middle", "newest")

        assertEquals("newest", navigator.older(history, currentText = "  unsent draft  "))
        assertEquals("middle", navigator.older(history, currentText = "newest"))
        assertEquals("oldest", navigator.older(history, currentText = "middle"))
        assertEquals("oldest", navigator.older(history, currentText = "oldest"))
        assertEquals("middle", navigator.newer(history, currentText = "oldest"))
        assertEquals("newest", navigator.newer(history, currentText = "middle"))
        assertEquals("  unsent draft  ", navigator.newer(history, currentText = "newest"))
        assertNull(navigator.newer(history, currentText = "  unsent draft  "))
    }

    @Test
    fun `newer immediately after editing recalled prompt exits history and preserves edit as draft`() {
        val navigator = PromptHistoryNavigator()
        val history = listOf("older", "newest")

        assertEquals("newest", navigator.older(history, currentText = "original draft"))
        assertNull(navigator.newer(history, currentText = "edited recalled prompt"))
        assertEquals("newest", navigator.older(history, currentText = "edited recalled prompt"))
        assertEquals("edited recalled prompt", navigator.newer(history, currentText = "newest"))
    }

    @Test
    fun `older immediately after editing recalled prompt restarts from newest with edit as draft`() {
        val navigator = PromptHistoryNavigator()
        val history = listOf("older", "newest")

        assertEquals("newest", navigator.older(history, currentText = "original draft"))
        assertEquals("newest", navigator.older(history, currentText = "edited recalled prompt"))
        assertEquals("edited recalled prompt", navigator.newer(history, currentText = "newest"))
    }

    @Test
    fun `empty history leaves both directions unhandled`() {
        val navigator = PromptHistoryNavigator("draft")

        assertNull(navigator.older(emptyList(), currentText = "draft"))
        assertNull(navigator.newer(emptyList(), currentText = "draft"))
    }

    @Test
    fun `history contains only visible nonblank user text and retains recent duplicates`() {
        val messages = listOf(
            userMessage(
                id = "user-1",
                textParts = listOf(textPart("user-1", "  first prompt  ")),
            ),
            assistantMessage(id = "assistant-1", text = "assistant response"),
            userMessage(
                id = "user-hidden",
                textParts = listOf(
                    textPart("user-hidden", "synthetic protocol", synthetic = true),
                    textPart("user-hidden", "ignored protocol", ignored = true),
                    textPart("user-hidden", "   "),
                ),
            ),
            userMessage(
                id = "user-2",
                textParts = listOf(
                    textPart("user-2", "second prompt"),
                    textPart("user-2", "ignored suffix", ignored = true),
                    textPart("user-2", "continued"),
                ),
            ),
            userMessage(
                id = "user-3",
                textParts = listOf(textPart("user-3", "  first prompt  ")),
            ),
        )

        assertEquals(listOf("second prompt\ncontinued", "  first prompt  "), messages.toPromptHistory())
    }

    private fun userMessage(id: String, textParts: List<Part.Text>): MessageWithParts = MessageWithParts(
        message = Message.User(
            id = id,
            sessionID = "session",
            createdAt = 1,
            agent = "agent",
            model = ModelRef(providerID = "provider", modelID = "model"),
        ),
        parts = textParts,
    )

    private fun assistantMessage(id: String, text: String): MessageWithParts = MessageWithParts(
        message = Message.Assistant(
            id = id,
            sessionID = "session",
            createdAt = 1,
            parentID = "user-1",
            providerID = "provider",
            modelID = "model",
            mode = "chat",
            agent = "agent",
            cost = 0.0,
            tokens = TokenUsage(input = 0, output = 0),
        ),
        parts = listOf(textPart(id, text)),
    )

    private fun textPart(
        messageId: String,
        text: String,
        synthetic: Boolean = false,
        ignored: Boolean = false,
    ): Part.Text = Part.Text(
        id = "part-$messageId-$text",
        sessionID = "session",
        messageID = messageId,
        text = text,
        synthetic = synthetic,
        ignored = ignored,
    )
}
