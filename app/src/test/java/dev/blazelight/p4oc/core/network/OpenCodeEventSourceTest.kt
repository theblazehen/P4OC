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
                Long::class.javaPrimitiveType
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
