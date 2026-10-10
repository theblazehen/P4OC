package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.data.remote.mapper.EventMapper
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class OpenCodeEventSourceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.d(any(), any<String>()) } returns Unit
        every { AppLog.d(any(), any<() -> String>()) } returns Unit
        every { AppLog.v(any(), any<String>()) } returns Unit
        every { AppLog.v(any(), any<() -> String>()) } returns Unit
        every { AppLog.w(any(), any<String>()) } returns Unit
        every { AppLog.w(any(), any<String>(), any()) } returns Unit
        every { AppLog.e(any(), any<String>()) } returns Unit
        every { AppLog.e(any(), any<String>(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(AppLog)
    }

    @Test
    fun `slow collector receives more than previous delta buffer capacity without loss`() = runBlocking {
        val source = OpenCodeEventSource(
            okHttpClient = OkHttpClient(),
            json = json,
            baseUrl = "http://127.0.0.1:1",
            eventMapper = EventMapper(json, MessageMapper()),
        )
        val collected = mutableListOf<String>()
        var collector: Job? = null
        val subscribed = CompletableDeferred<Unit>()

        try {
            collector = launch {
                source.events.onSubscription { subscribed.complete(Unit) }.collect { event ->
                    val delta = (event as? OpenCodeEvent.MessagePartUpdated)?.delta ?: return@collect
                    delay(1)
                    collected += delta
                }
            }
            // Barrier: production delivery runs on Dispatchers.IO into a replay=0 SharedFlow,
            // so we must not emit until the collector is actually subscribed, else events are
            // dropped before collection and the no-loss assertion can never hold.
            subscribed.await()
            val emit = source.javaClass.getDeclaredMethod(
                "parseAndEmitEvent",
                String::class.java,
                Long::class.javaPrimitiveType,
            )
                .apply { isAccessible = true }
            val generation = source.javaClass.getDeclaredField("generation")
                .apply { isAccessible = true }
            generation.setLong(source, 1L)

            repeat(EVENT_COUNT) { index ->
                emit.invoke(source, globalPartDeltaJson(delta = index.toString()), 1L)
            }

            withTimeout(30_000) {
                while (collected.size < EVENT_COUNT) delay(10)
            }
        } finally {
            collector?.cancel()
            collector?.join()
            source.shutdown()
        }

        assertEquals((0 until EVENT_COUNT).map { it.toString() }, collected)
    }

    @Test
    fun `disconnect keeps event pump available but shutdown terminates it and its channel`() {
        val source = createSource()
        val pumpScope = source.readPrivateField<CoroutineScope>("eventPumpScope")
        val channel = source.readPrivateField<Channel<*>>("eventChannel")

        source.disconnect()

        assertTrue(pumpScope.coroutineContext[Job]!!.isActive)
        assertFalse(channel.isClosedForSend)

        source.shutdown()

        assertFalse(pumpScope.coroutineContext[Job]!!.isActive)
        assertTrue(channel.isClosedForSend)
    }

    @Test
    fun `connection error handler stops retries at terminal error cap`() {
        val source = createSource()
        try {
            source.javaClass.getDeclaredField("generation")
                .apply { isAccessible = true }
                .setLong(source, 1L)
            source.readPrivateField<AtomicInteger>("consecutiveErrors").set(MAX_ERRORS)

            val actionMethod = source.javaClass.getDeclaredMethod(
                "connectionErrorAction",
                Throwable::class.java,
                Long::class.javaPrimitiveType,
            ).apply { isAccessible = true }

            source.readPrivateField<AtomicInteger>("consecutiveErrors").set(MAX_ERRORS - 1)
            val retryAction = actionMethod.invoke(source, IllegalStateException("offline"), 1L)

            source.readPrivateField<AtomicInteger>("consecutiveErrors").set(MAX_ERRORS)
            val terminalAction = actionMethod.invoke(source, IllegalStateException("offline"), 1L)

            assertEquals("PROCEED", retryAction.toString())
            assertEquals("SHUTDOWN", terminalAction.toString())
        } finally {
            source.shutdown()
        }
    }

    @Test
    fun `oversized event data is rejected before JSON decoding`() {
        val source = createSource()
        try {
            val emit = source.javaClass.getDeclaredMethod(
                "parseAndEmitEvent",
                String::class.java,
                Long::class.javaPrimitiveType,
            ).apply { isAccessible = true }

            emit.invoke(source, "x".repeat(OpenCodeEventSource.MAX_EVENT_DATA_CHARS + 1), 1L)

            io.mockk.verify(exactly = 1) {
                AppLog.w(any(), match<String> { it.startsWith("Rejecting oversized SSE event") })
            }
            io.mockk.verify(exactly = 0) {
                AppLog.e(any(), match<String> { it.startsWith("Failed to parse event") }, any())
            }
        } finally {
            source.shutdown()
        }
    }

    @Test
    fun `v2 assistant streams content without REST reads per token and finalizes at ended`() {
        val mapper = V2EventMapper(json, EventMapper(json, MessageMapper()))
        val started = """{"type":"session.text.started","data":{
            "sessionID":"ses_1","assistantMessageID":"msg_1","ordinal":0}}"""
        val delta = """{"type":"session.text.delta","data":{
            "sessionID":"ses_1","assistantMessageID":"msg_1","ordinal":0,"delta":"Hello"}}"""
        val ended = """{"type":"session.text.ended","location":{"directory":"/workspace/project"},"data":{
            "sessionID":"ses_1","assistantMessageID":"msg_1","ordinal":0,"text":"Hello!"}}"""
        val id = v2AssistantPartId("msg_1", "text", 0)

        assertEquals(
            OpenCodeEvent.V2ContentChanged("ses_1", "msg_1", id, "text", "", "started"),
            mapper.map(started)?.event,
        )
        assertEquals(
            OpenCodeEvent.V2ContentChanged("ses_1", "msg_1", id, "text", "Hello", "delta"),
            mapper.map(delta)?.event,
        )
        val mapped = mapper.map(ended)
        assertEquals("/workspace/project", mapped?.directory)
        assertEquals(
            OpenCodeEvent.V2ContentChanged("ses_1", "msg_1", id, "text", "Hello!", "ended"),
            mapped?.event,
        )
    }

    @Test
    fun `v2 text and reasoning ordinals identify independent content parts`() {
        val mapper = V2EventMapper(json, EventMapper(json, MessageMapper()))
        val reasoning = mapper.map(
            """{"type":"session.reasoning.delta","data":{
            "sessionID":"ses_1","assistantMessageID":"msg_1","ordinal":0,"delta":"Think"}}""",
        )
        val secondText = mapper.map(
            """{"type":"session.text.delta","data":{
            "sessionID":"ses_1","assistantMessageID":"msg_1","ordinal":1,"delta":"Next"}}""",
        )
        assertEquals(
            OpenCodeEvent.V2ContentChanged(
                "ses_1",
                "msg_1",
                "p4oc.v2.msg_1.reasoning.0",
                "reasoning",
                "Think",
                "delta",
            ),
            reasoning?.event,
        )
        assertEquals(
            OpenCodeEvent.V2ContentChanged(
                "ses_1",
                "msg_1",
                "p4oc.v2.msg_1.text.1",
                "text",
                "Next",
                "delta",
            ),
            secondText?.event,
        )
    }

    @Test
    fun `persisted interleaved content shares stream identity despite server ids`() {
        val message = V2SessionDtoMapper(json).toMessageWrapper(
            json.parseToJsonElement(
                """{"id":"msg_1","sessionID":"ses_1","type":"assistant","time":{"created":1},
                "content":[{"type":"reasoning","id":"srv_reason_0","text":"Think"},
                {"type":"text","id":"srv_text_0","text":"First"},
                {"type":"reasoning","id":"srv_reason_1","text":"More"},
                {"type":"tool","id":"call_1","name":"read","state":{"status":"running","input":{}}},
                {"type":"text","id":"srv_text_1","text":"Next"}]}""",
            ),
            expectedSessionId = "ses_1",
        )
        assertEquals(
            listOf(
                "p4oc.v2.msg_1.reasoning.0",
                "p4oc.v2.msg_1.text.0",
                "p4oc.v2.msg_1.reasoning.1",
                "call_1",
                "p4oc.v2.msg_1.text.1",
            ),
            message.parts.map { it.id },
        )
        val delta = V2EventMapper(json, EventMapper(json, MessageMapper())).map(
            """{"type":"session.text.delta","data":{"sessionID":"ses_1",
            "assistantMessageID":"msg_1","ordinal":1,"delta":"!"}}""",
        )?.event as OpenCodeEvent.V2ContentChanged
        assertEquals(message.parts.last().id, delta.partID)
    }

    @Test
    fun `v2 permissions retain resource and tool source identity`() {
        val mapped = V2EventMapper(json, EventMapper(json, MessageMapper())).map(
            """
            {"id":"evt_2","type":"permission.asked","location":{"directory":"/workspace/project"},
             "data":{"id":"per_1","sessionID":"ses_1","action":"shell","resources":["npm test"],
             "save":["npm *"],"source":{"type":"tool","messageID":"msg_1","id":"call_1"}}}
            """.trimIndent(),
        )

        val permission = (mapped?.event as OpenCodeEvent.PermissionRequested).permission
        assertEquals("shell", permission.type)
        assertEquals(listOf("npm test"), permission.patterns)
        assertEquals(listOf("npm *"), permission.always)
        assertEquals("msg_1", permission.messageID)
        assertEquals("call_1", permission.callID)
    }

    @Test
    fun `v2 status retry and terminal events retain their payloads`() {
        val mapper = V2EventMapper(json, EventMapper(json, MessageMapper()))
        val status = mapper.map(
            """{"type":"session.status","data":{"sessionID":"ses_1","status":{
                "type":"retry","attempt":2,"message":"rate limited","next":1000}}}""",
        )
        val retry = (status?.event as OpenCodeEvent.SessionStatusChanged).status
        assertEquals(dev.blazelight.p4oc.domain.model.SessionStatus.Retry(2, "rate limited", 1000), retry)
        assertEquals(
            OpenCodeEvent.PtyExited("pty_1", 7),
            mapper.map(
                """{"type":"pty.exited","data":{"id":"pty_1","exitCode":7}}""",
            )?.event,
        )
        assertEquals(
            OpenCodeEvent.InstallationUpdateAvailable("2.1.0"),
            mapper.map(
                """{"type":"installation.update-available","data":{"version":"2.1.0"}}""",
            )?.event,
        )
        assertEquals(
            OpenCodeEvent.VcsBranchUpdated("feature"),
            mapper.map(
                """{"type":"vcs.branch.updated","data":{"branch":"feature"}}""",
            )?.event,
        )
    }

    @Test
    fun `v2 execution failure and unscoped location shutdown remain visible`() {
        val mapper = V2EventMapper(json, EventMapper(json, MessageMapper()))
        val failure = mapper.map(
            """{"type":"session.execution.failed","data":{"sessionID":"ses_1","error":{
                "type":"ProviderError","message":"denied","status":403}}}""",
        )?.event as OpenCodeEvent.SessionError
        assertEquals("ses_1", failure.sessionID)
        assertEquals("denied", failure.error?.message)
        assertEquals(403, failure.error?.statusCode)
        assertEquals(
            OpenCodeEvent.LocationShutdown(null),
            mapper.map(
                """{"type":"location.shutdown","data":{}}""",
            )?.event,
        )
    }

    @Test
    fun `v2 catalog and MCP changes refresh their consumers`() {
        val mapper = V2EventMapper(json, EventMapper(json, MessageMapper()))
        assertEquals(
            OpenCodeEvent.ModelsRefreshed,
            mapper.map(
                """{"type":"models-dev.refreshed","data":{}}""",
            )?.event,
        )
        assertEquals(
            OpenCodeEvent.McpToolsChanged("filesystem"),
            mapper.map(
                """{"type":"mcp.status.changed","data":{"server":"filesystem"}}""",
            )?.event,
        )
        assertEquals(
            OpenCodeEvent.McpToolsChanged("filesystem"),
            mapper.map(
                """{"type":"mcp.resources.changed","data":{"server":"filesystem"}}""",
            )?.event,
        )
    }

    private fun createSource() = OpenCodeEventSource(
        okHttpClient = OkHttpClient(),
        json = json,
        baseUrl = "http://127.0.0.1:1",
        eventMapper = EventMapper(json, MessageMapper()),
    )

    @Suppress("UNCHECKED_CAST")
    private fun <T> OpenCodeEventSource.readPrivateField(name: String): T =
        javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this) as T

    private fun globalPartDeltaJson(delta: String): String =
        """
        {
          "directory": "/workspace",
          "payload": {
            "type": "message.part.updated",
            "properties": {
              "part": {
                "id": "part-1",
                "sessionID": "session-1",
                "messageID": "message-1",
                "type": "text",
                "text": "ignored"
              },
              "delta": "$delta"
            }
          }
        }
        """.trimIndent()

    private companion object {
        const val EVENT_COUNT = 300
        const val MAX_ERRORS = 15
    }
}
