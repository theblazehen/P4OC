package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.TokenUsage
import dev.blazelight.p4oc.domain.model.ToolState
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.fakes.FakeWorkspaceClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryMessageStateTest {
    private val sessionId = SessionId("s1")

    @Test
    fun `message updated creates new entry`() = runTest {
        val repository = repository()

        repository.acceptEvent(OpenCodeEvent.MessageUpdated(assistantMessage(id = "m1", createdAt = 100)))

        assertEquals(listOf("m1"), repository.messages(sessionId).value.map { it.message.id })
        assertTrue(repository.messages(sessionId).value.first().parts.isEmpty())
    }

    @Test
    fun `message updated preserves existing parts`() = runTest {
        val repository = repository()
        repository.acceptEvent(OpenCodeEvent.MessageUpdated(assistantMessage(id = "m1", createdAt = 100)))
        repository.acceptEvent(OpenCodeEvent.MessagePartUpdated(textPart(id = "p1", messageId = "m1", text = "hello"), delta = null))

        repository.acceptEvent(OpenCodeEvent.MessageUpdated(assistantMessage(id = "m1", createdAt = 200)))

        val saved = repository.messages(sessionId).value.single()
        assertEquals(200, saved.message.createdAt)
        assertEquals(listOf("p1"), saved.parts.map { it.id })
    }

    @Test
    fun `part updated creates placeholder message`() = runTest {
        val repository = repository()

        repository.acceptEvent(OpenCodeEvent.MessagePartUpdated(textPart(id = "p1", messageId = "missing", text = "content"), delta = null))

        val saved = repository.messages(sessionId).value.single()
        assertEquals("missing", saved.message.id)
        assertEquals("s1", saved.message.sessionID)
        assertEquals(listOf("p1"), saved.parts.map { it.id })
    }

    @Test
    fun `part updated replaces existing part by id`() = runTest {
        val repository = repository()
        repository.acceptEvent(OpenCodeEvent.MessageUpdated(assistantMessage(id = "m1", createdAt = 100)))
        repository.acceptEvent(OpenCodeEvent.MessagePartUpdated(textPart(id = "p1", messageId = "m1", text = "old"), delta = null))

        repository.acceptEvent(OpenCodeEvent.MessagePartUpdated(textPart(id = "p1", messageId = "m1", text = "new"), delta = null))

        val part = repository.messages(sessionId).value.single().parts.single() as Part.Text
        assertEquals("new", part.text)
    }

    @Test
    fun `part updated appends delta and marks streaming`() = runTest {
        val repository = repository()
        repository.acceptEvent(OpenCodeEvent.MessageUpdated(assistantMessage(id = "m1", createdAt = 100)))
        repository.acceptEvent(OpenCodeEvent.MessagePartUpdated(textPart(id = "p1", messageId = "m1", text = "Hello"), delta = null))

        repository.acceptEvent(OpenCodeEvent.MessagePartUpdated(textPart(id = "p1", messageId = "m1", text = "ignored"), delta = " world"))

        val part = repository.messages(sessionId).value.single().parts.single() as Part.Text
        assertEquals("Hello world", part.text)
        assertTrue(part.isStreaming)
    }

    @Test
    fun `clear streaming flags sets all text parts non-streaming`() = runTest {
        val repository = repository()
        repository.acceptEvent(OpenCodeEvent.MessageUpdated(assistantMessage(id = "m1", createdAt = 100)))
        repository.acceptEvent(OpenCodeEvent.MessagePartUpdated(textPart(id = "p1", messageId = "m1", text = "a", isStreaming = true), delta = null))
        repository.acceptEvent(OpenCodeEvent.MessagePartUpdated(toolPart(id = "p2", messageId = "m1"), delta = null))

        repository.clearStreamingFlags(sessionId)

        val textParts = repository.messages(sessionId).value.single().parts.filterIsInstance<Part.Text>()
        assertTrue(textParts.isNotEmpty())
        assertTrue(textParts.all { !it.isStreaming })
    }

    @Test
    fun `message and part removal update state`() = runTest {
        val repository = repository()
        repository.acceptEvent(OpenCodeEvent.MessageUpdated(assistantMessage(id = "m1", createdAt = 100)))
        repository.acceptEvent(OpenCodeEvent.MessagePartUpdated(textPart(id = "p1", messageId = "m1", text = "a"), delta = null))

        repository.acceptEvent(OpenCodeEvent.PartRemoved(sessionID = "s1", messageID = "m1", partID = "p1"))
        assertTrue(repository.messages(sessionId).value.single().parts.isEmpty())

        repository.acceptEvent(OpenCodeEvent.MessageRemoved(sessionID = "s1", messageID = "m1"))
        assertTrue(repository.messages(sessionId).value.isEmpty())
    }

    @Test
    fun `messages emit sorted by createdAt`() = runTest {
        val repository = repository()

        repository.acceptEvent(OpenCodeEvent.MessageUpdated(assistantMessage(id = "m3", createdAt = 300)))
        repository.acceptEvent(OpenCodeEvent.MessageUpdated(assistantMessage(id = "m1", createdAt = 100)))
        repository.acceptEvent(OpenCodeEvent.MessageUpdated(assistantMessage(id = "m2", createdAt = 200)))

        assertEquals(listOf("m1", "m2", "m3"), repository.messages(sessionId).value.map { it.message.id })
    }

    @Test
    fun `clear streaming leaves non-text parts untouched`() = runTest {
        val repository = repository()
        repository.acceptEvent(OpenCodeEvent.MessageUpdated(assistantMessage(id = "m1", createdAt = 100)))
        repository.acceptEvent(OpenCodeEvent.MessagePartUpdated(toolPart(id = "tool", messageId = "m1"), delta = null))

        repository.clearStreamingFlags(sessionId)

        assertFalse(repository.messages(sessionId).value.single().parts.single() is Part.Text)
    }

    private fun repository(): SessionRepositoryImpl = SessionRepositoryImpl(FakeWorkspaceClient())

    private fun assistantMessage(id: String, createdAt: Long): Message.Assistant = Message.Assistant(
        id = id,
        sessionID = "s1",
        createdAt = createdAt,
        parentID = "",
        providerID = "provider",
        modelID = "model",
        mode = "chat",
        agent = "assistant",
        cost = 0.0,
        tokens = TokenUsage(input = 0, output = 0),
    )

    private fun textPart(
        id: String,
        messageId: String,
        text: String,
        isStreaming: Boolean = false,
    ): Part.Text = Part.Text(
        id = id,
        sessionID = "s1",
        messageID = messageId,
        text = text,
        isStreaming = isStreaming,
    )

    private fun toolPart(id: String, messageId: String): Part.Tool = Part.Tool(
        id = id,
        sessionID = "s1",
        messageID = messageId,
        callID = "call-$id",
        toolName = "tool",
        state = ToolState.Running(
            input = buildJsonObject { },
            title = null,
            startedAt = 0L,
        ),
    )
}
