package dev.blazelight.p4oc.ui.screens.chat

import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.TokenUsage
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MessageStoreTest {

    @get:Rule
    val mainDispatcherRule = MessageStoreMainDispatcherRule()

    @Before
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.d(any(), any<String>()) } returns Unit
        every { AppLog.d(any(), any<() -> String>()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(AppLog)
    }

    @Test
    fun upsertMessage_createsNewEntry_whenMessageNotInMap() = runTest {
        val store = MessageStore(sessionId = "s1", scope = this)
        val message = assistantMessage(id = "m1", createdAt = 100)

        store.upsertMessage(message)
        flushStore()

        assertEquals(1, store.currentMessages().size)
        assertEquals("m1", store.currentMessages().first().message.id)
        assertTrue(store.currentMessages().first().parts.isEmpty())
        coroutineContext.cancelChildren()
    }

    @Test
    fun upsertMessage_updatesExistingEntry_preservingParts() = runTest {
        val store = MessageStore(sessionId = "s1", scope = this)
        val initial = assistantMessage(id = "m1", createdAt = 100)
        val updated = assistantMessage(id = "m1", createdAt = 200)
        val part = textPart(id = "p1", messageId = "m1", text = "hello")

        store.loadInitial(listOf(MessageWithParts(initial, listOf(part))))
        store.upsertMessage(updated)
        flushStore()

        val saved = store.currentMessages().single()
        assertEquals(200, saved.message.createdAt)
        assertEquals(1, saved.parts.size)
        assertEquals("p1", saved.parts.single().id)
        coroutineContext.cancelChildren()
    }

    @Test
    fun upsertPart_createsPlaceholderMessage_whenMessageNotFound() = runTest {
        val store = MessageStore(sessionId = "session-x", scope = this)
        val part = textPart(id = "p1", messageId = "missing", text = "content")

        store.upsertPart(part, delta = null)
        flushStore()

        val saved = store.currentMessages().single()
        assertEquals("missing", saved.message.id)
        assertEquals("session-x", saved.message.sessionID)
        assertEquals(1, saved.parts.size)
        assertEquals("p1", saved.parts.single().id)
        coroutineContext.cancelChildren()
    }

    @Test
    fun upsertPart_replacesExistingPartById() = runTest {
        val store = MessageStore(sessionId = "s1", scope = this)
        val message = assistantMessage(id = "m1", createdAt = 100)

        store.upsertMessage(message)
        flushStore()
        store.upsertPart(textPart(id = "p1", messageId = "m1", text = "old"), delta = null)
        flushStore()
        store.upsertPart(textPart(id = "p1", messageId = "m1", text = "new"), delta = null)
        flushStore()

        val parts = store.currentMessages().single().parts
        assertEquals(1, parts.size)
        assertEquals("new", (parts.single() as Part.Text).text)
        coroutineContext.cancelChildren()
    }

    @Test
    fun upsertPart_appendsNewPart_whenIdNotFound() = runTest {
        val store = MessageStore(sessionId = "s1", scope = this)
        val message = assistantMessage(id = "m1", createdAt = 100)

        store.upsertMessage(message)
        flushStore()
        store.upsertPart(textPart(id = "p1", messageId = "m1", text = "one"), delta = null)
        store.upsertPart(toolPart(id = "p2", messageId = "m1"), delta = null)
        flushStore()

        val parts = store.currentMessages().single().parts
        assertEquals(2, parts.size)
        assertTrue(parts.any { it.id == "p1" })
        assertTrue(parts.any { it.id == "p2" })
        coroutineContext.cancelChildren()
    }

    @Test
    fun applyDelta_appendsTextToExistingPartText_whenDeltaProvided() = runTest {
        val store = MessageStore(sessionId = "s1", scope = this)
        store.upsertMessage(assistantMessage(id = "m1", createdAt = 100))
        flushStore()

        store.upsertPart(textPart(id = "p1", messageId = "m1", text = "Hello"), delta = null)
        flushStore()
        store.upsertPart(textPart(id = "p1", messageId = "m1", text = "ignored"), delta = " world")
        flushStore()

        val text = (store.currentMessages().single().parts.single() as Part.Text).text
        assertEquals("Hello world", text)
        coroutineContext.cancelChildren()
    }

    @Test
    fun applyDelta_setsIsStreamingTrue_duringDeltaAccumulation() = runTest {
        val store = MessageStore(sessionId = "s1", scope = this)
        store.upsertMessage(assistantMessage(id = "m1", createdAt = 100))
        flushStore()

        store.upsertPart(textPart(id = "p1", messageId = "m1", text = "Hello"), delta = null)
        flushStore()
        store.upsertPart(textPart(id = "p1", messageId = "m1", text = "ignored"), delta = "!")
        flushStore()

        val part = store.currentMessages().single().parts.single() as Part.Text
        assertTrue(part.isStreaming)
        coroutineContext.cancelChildren()
    }

    @Test
    fun clearStreamingFlags_setsIsStreamingFalse_onAllTextParts() = runTest {
        val store = MessageStore(sessionId = "s1", scope = this)
        val message = assistantMessage(id = "m1", createdAt = 100)
        val parts = listOf(
            textPart(id = "p1", messageId = "m1", text = "a", isStreaming = true),
            textPart(id = "p2", messageId = "m1", text = "b", isStreaming = false),
            toolPart(id = "p3", messageId = "m1")
        )
        store.loadInitial(listOf(MessageWithParts(message, parts)))
        flushStore()
        store.clearStreamingFlags()
        flushStore()

        val updated = store.currentMessages().single().parts
        val textParts = updated.filterIsInstance<Part.Text>()
        assertTrue(textParts.isNotEmpty())
        assertTrue(textParts.all { !it.isStreaming })
        coroutineContext.cancelChildren()
    }

    @Test
    fun concurrentUpsertMessage_callsDoNotCorruptState() = runTest {
        val store = MessageStore(sessionId = "s1", scope = this)

        (1..50).map { idx ->
            async {
                store.upsertMessage(assistantMessage(id = "m$idx", createdAt = idx.toLong()))
            }
        }.awaitAll()
        flushStore()

        assertEquals(50, store.currentMessages().size)
        assertEquals(50, store.currentMessages().map { it.message.id }.toSet().size)
        coroutineContext.cancelChildren()
    }

    @Test
    fun messagesFlow_emitsSortedByCreatedAt() = runTest {
        val store = MessageStore(sessionId = "s1", scope = this)

        store.upsertMessage(assistantMessage(id = "m3", createdAt = 300))
        store.upsertMessage(assistantMessage(id = "m1", createdAt = 100))
        store.upsertMessage(assistantMessage(id = "m2", createdAt = 200))
        flushStore()

        val ids = store.currentMessages().map { it.message.id }
        assertEquals(listOf("m1", "m2", "m3"), ids)
        coroutineContext.cancelChildren()
    }

    private fun TestScope.flushStore() {
        advanceUntilIdle()
        advanceUntilIdle()
    }

    private fun MessageStore.currentMessages(): List<MessageWithParts> =
        currentMessagesSnapshot()

    private fun assistantMessage(id: String, createdAt: Long): Message.Assistant {
        return Message.Assistant(
            id = id,
            sessionID = "s1",
            createdAt = createdAt,
            parentID = "",
            providerID = "provider",
            modelID = "model",
            mode = "chat",
            agent = "assistant",
            cost = 0.0,
            tokens = TokenUsage(input = 0, output = 0)
        )
    }

    private fun textPart(
        id: String,
        messageId: String,
        text: String,
        isStreaming: Boolean = false
    ): Part.Text {
        return Part.Text(
            id = id,
            sessionID = "s1",
            messageID = messageId,
            text = text,
            isStreaming = isStreaming
        )
    }

    private fun toolPart(id: String, messageId: String): Part.Tool {
        return Part.Tool(
            id = id,
            sessionID = "s1",
            messageID = messageId,
            callID = "call-$id",
            toolName = "tool",
            state = dev.blazelight.p4oc.domain.model.ToolState.Running(
                input = buildJsonObject { },
                title = null,
                startedAt = 0L
            )
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MessageStoreMainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
