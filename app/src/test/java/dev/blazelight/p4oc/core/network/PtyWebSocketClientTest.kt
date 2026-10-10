package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.core.datastore.SavedServerRegistry
import dev.blazelight.p4oc.domain.server.ServerGeneration
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PtyWebSocketClientTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `delayed PTY A retry cannot replace newer PTY B after ticket failure`() = runTest {
        val server = SavedServerRegistry.fromConnection("https://terminal.example.com", "Terminal")
        val serverRef = server.toServerRef()
        val serverGeneration = ServerGeneration(1)
        val connection = Connection(
            config = server.toServerConfig(),
            generation = serverGeneration,
            api = mockk<V2WorkspaceOpenCodeApi>(relaxed = true),
            eventSource = mockk(relaxed = true),
        )
        val requestedPtys = mutableListOf<String>()
        val authClient = mockk<OkHttpClient>()
        stubConnectTickets(authClient, requestedPtys)

        val webSocket = mockk<WebSocket>(relaxed = true)
        val listeners = mutableListOf<WebSocketListener>()
        every { authClient.newWebSocket(any(), any()) } answers {
            listeners += secondArg<WebSocketListener>()
            webSocket
        }

        val registry = mockk<ServerConnectionRegistry>()
        every { registry.terminalTransport(serverRef, serverGeneration) } returns
            TerminalTransport(connection, authClient)
        val client = PtyWebSocketClient(
            serverConnectionRegistry = registry,
            serverRef = serverRef,
            serverGeneration = serverGeneration,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        try {
            client.connect("pty-a", directory = null, workspace = null)
            runCurrent()
            assertEquals(listOf("pty-a"), requestedPtys)
            assertEquals(1, listeners.size)

            // Drive the real failure callback; it schedules PTY A's delayed retry.
            listeners.single().onFailure(webSocket, IOException("PTY A disconnected"), null)
            runCurrent()

            // B becomes the newer generation, but its v2 ticket request fails before a socket
            // exists, leaving the old callback's timer with no current PTY/WebSocket to inspect.
            client.connect("pty-b", directory = null, workspace = null)
            runCurrent()
            assertEquals(listOf("pty-a", "pty-b"), requestedPtys)
            assertEquals(
                "PTY connect-token request failed (HTTP 503)",
                (client.connectionState.value as PtyWebSocketClient.ConnectionState.Error).message,
            )
            assertEquals(null, client.getCurrentPtyId())

            // At 1s only A's timer is due (B's own retry is scheduled later). The generation
            // fence must discard it rather than issuing another ticket request for PTY A.
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(listOf("pty-a", "pty-b"), requestedPtys)
            assertEquals(null, client.getCurrentPtyId())
            assertTrue(client.connectionState.value is PtyWebSocketClient.ConnectionState.Error)
        } finally {
            client.close()
        }
    }

    @Test
    fun `websocket URL preserves v1 path and encodes PTY id as one segment`() {
        val url = buildPtyWebSocketUrl(
            baseUrl = "https://terminal.example.com/opencode/",
            ptyId = "id/with?reserved%chars",
            directory = "/repo/with spaces?and=query",
            workspace = null,
        )

        assertEquals(
            "wss://terminal.example.com/opencode/pty/id%2Fwith%3Freserved%25chars/connect" +
                "?directory=%2Frepo%2Fwith%20spaces%3Fand%3Dquery",
            url,
        )
    }

    @Test
    fun `websocket URL omits explicit null v1 workspace scope`() {
        val url = buildPtyWebSocketUrl(
            baseUrl = "http://terminal.example.com/",
            ptyId = "pty-1",
            directory = null,
            workspace = null,
        )

        assertEquals("ws://terminal.example.com/pty/pty-1/connect", url)
    }

    @Test
    fun `v2 connect-token request is location scoped and carries required header`() {
        val request = buildV2PtyConnectTokenRequest(
            baseUrl = "https://terminal.example.com/opencode/",
            ptyId = "id/with spaces",
            directory = "/repo/with spaces?and=query",
            workspace = "workspace&one",
        )

        assertEquals("POST", request.method)
        assertEquals("/opencode/api/pty/id%2Fwith%20spaces/connect-token", request.url.encodedPath)
        assertEquals("/repo/with spaces?and=query", request.url.queryParameter("location[directory]"))
        assertEquals("workspace&one", request.url.queryParameter("location[workspace]"))
        assertEquals("1", request.header("x-opencode-ticket"))
    }

    @Test
    fun `v2 websocket URL carries fresh ticket and location using encoded query parameters`() {
        val url = buildV2PtyWebSocketUrl(
            baseUrl = "https://terminal.example.com/opencode/",
            ptyId = "id/with?reserved%chars",
            directory = "/repo/with spaces?and=query",
            workspace = "workspace&one",
            ticket = "ticket/with?reserved&chars",
        )
        val parsedUrl = url.replaceFirst("wss://", "https://").toHttpUrl()

        assertEquals("wss", url.substringBefore("://"))
        assertEquals("/opencode/api/pty/id%2Fwith%3Freserved%25chars/connect", parsedUrl.encodedPath)
        assertEquals("/repo/with spaces?and=query", parsedUrl.queryParameter("location[directory]"))
        assertEquals("workspace&one", parsedUrl.queryParameter("location[workspace]"))
        assertEquals("ticket/with?reserved&chars", parsedUrl.queryParameter("ticket"))
    }

    @Test
    fun `v2 token parser reads the ticket from the documented location envelope`() {
        val ticket = parseV2PtyConnectTicket(
            """{"location":{"directory":"/repo"},"data":{"ticket":"fresh-ticket","expires_in":60}}""",
        )

        assertEquals("fresh-ticket", ticket)
    }

    @Test
    fun `v2 token parser rejects a response without the documented data envelope`() {
        val failure = runCatching { parseV2PtyConnectTicket("""{"ticket":"not-a-ticket"}""") }
            .exceptionOrNull()

        assertEquals("OpenCode returned an invalid PTY connect ticket", failure?.message)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `disconnecting while a v2 ticket request is stalled cancels the http call`() = runTest {
        val server = SavedServerRegistry.fromConnection("https://terminal.example.com", "Terminal")
        val serverRef = server.toServerRef()
        val serverGeneration = ServerGeneration(1)
        val connection = Connection(
            config = server.toServerConfig(),
            generation = serverGeneration,
            api = mockk<V2WorkspaceOpenCodeApi>(relaxed = true),
            eventSource = mockk(relaxed = true),
        )
        val ticketTimeout = Timeout()
        val stalledCall = mockk<Call>(relaxed = true) {
            every { timeout() } returns ticketTimeout
            every { enqueue(any()) } just Runs // the server never answers
        }
        val authClient = mockk<OkHttpClient> { every { newCall(any()) } returns stalledCall }
        val registry = mockk<ServerConnectionRegistry>()
        every { registry.terminalTransport(serverRef, serverGeneration) } returns
            TerminalTransport(connection, authClient)
        val client = PtyWebSocketClient(
            serverConnectionRegistry = registry,
            serverRef = serverRef,
            serverGeneration = serverGeneration,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        try {
            client.connect("pty-a", directory = null, workspace = null)
            runCurrent()
            verify(exactly = 1) { stalledCall.enqueue(any()) }
            assertTrue("ticket call needs its own deadline", ticketTimeout.timeoutNanos() > 0)

            client.disconnect()
            runCurrent()

            verify(exactly = 1) { stalledCall.cancel() }
        } finally {
            client.close()
        }
    }

    private fun stubConnectTickets(authClient: OkHttpClient, requestedPtys: MutableList<String>) {
        every { authClient.newCall(any()) } answers {
            val request = firstArg<Request>()
            val ptyId = request.url.encodedPath.substringAfter("/api/pty/").substringBefore("/connect-token")
            requestedPtys += ptyId
            val statusCode = if (ptyId == "pty-b") 503 else 200
            val body = if (statusCode == 200) {
                """{"data":{"ticket":"ticket-$ptyId"}}"""
            } else {
                """{"error":"temporarily unavailable"}"""
            }
            val response = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message(if (statusCode == 200) "OK" else "Unavailable")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
            respondingCall(response)
        }
    }

    private fun respondingCall(response: Response): Call = mockk(relaxed = true) {
        every { timeout() } returns Timeout()
        every { enqueue(any()) } answers { firstArg<Callback>().onResponse(self as Call, response) }
    }
}
