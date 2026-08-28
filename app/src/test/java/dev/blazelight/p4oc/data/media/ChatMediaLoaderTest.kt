package dev.blazelight.p4oc.data.media

import dev.blazelight.p4oc.core.network.Connection
import dev.blazelight.p4oc.core.network.ServerConfig
import dev.blazelight.p4oc.core.network.ServerConnectionRegistry
import dev.blazelight.p4oc.core.network.TerminalTransport
import dev.blazelight.p4oc.data.remote.dto.FileContentDto
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.workspace.Workspace
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.buffer
import okio.source
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ChatMediaLoaderTest {
    @Test
    fun `loads bounded base64 image data URLs`() = runTest {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val loader = loader()

        val result = loader.load(
            filePart(
                url = "data:image/png;base64,${Base64.getEncoder().encodeToString(bytes)}",
                mime = "image/png",
            )
        ) as ChatMediaLoadResult.Loaded

        assertArrayEquals(bytes, result.bytes)
        assertEquals("image/png", result.mimeType)
    }

    @Test
    fun `rejects malformed oversized and non-image data URLs`() = runTest {
        val loader = loader()
        val cases = listOf(
            filePart("data:image/png;base64,%%%"),
            filePart("data:text/plain;base64,AQID"),
            filePart("data:image/png,AQID"),
            filePart("data:image/png;base64,${"A".repeat(MAX_CHAT_MEDIA_BASE64_CHARS + 1)}"),
            filePart("data:image/png;base64,AQID", mime = "text/plain"),
        )

        cases.forEach { assertUnavailable(loader.load(it)) }
    }

    @Test
    fun `loads contained workspace file through bounded reader`() = runTest {
        val expected = byteArrayOf(9, 8, 7)
        var readPath: String? = null
        var responseCap: Long? = null
        val loader = loader(
            workspaceDirectory = "/repo/project",
            workspaceFileReader = { path, maxResponseBytes ->
                readPath = path
                responseCap = maxResponseBytes
                imageFileContent(expected)
            },
        )

        val result = loader.load(
            filePart("file:///repo/project/images/My%20Image.png")
        ) as ChatMediaLoadResult.Loaded

        assertEquals("images/My Image.png", readPath)
        assertEquals(MAX_CHAT_MEDIA_FILE_RESPONSE_BYTES, responseCap)
        assertArrayEquals(expected, result.bytes)
        assertEquals("image/png", result.mimeType)
    }

    @Test
    fun `rejects escaped outside and remote-authority file URIs before reading`() = runTest {
        val reads = AtomicInteger()
        val loader = loader(
            workspaceDirectory = "/repo/project",
            workspaceFileReader = { _, _ ->
                reads.incrementAndGet()
                imageFileContent(byteArrayOf(1))
            },
        )
        val cases = listOf(
            "file:///repo/project/../secret.png",
            "file:///other/image.png",
            "file://remote/repo/project/image.png",
            "file:///repo/project",
        )

        cases.forEach { assertUnavailable(loader.load(filePart(it))) }
        assertEquals(0, reads.get())
    }

    @Test
    fun `rejects unsafe workspace file responses`() = runTest {
        val loader = loader(
            workspaceDirectory = "/repo",
            workspaceFileReader = { path, _ ->
                when (path) {
                    "wrong-encoding.png" -> imageFileContent(byteArrayOf(1)).copy(encoding = "utf-8")
                    "wrong-mime.png" -> imageFileContent(byteArrayOf(1)).copy(mimeType = "text/plain")
                    "malformed.png" -> imageFileContent(byteArrayOf()).copy(content = "***")
                    else -> imageFileContent(byteArrayOf()).copy(
                        content = "A".repeat(MAX_CHAT_MEDIA_BASE64_CHARS + 1),
                    )
                }
            },
        )

        listOf("wrong-encoding.png", "wrong-mime.png", "malformed.png", "oversized.png")
            .forEach { path -> assertUnavailable(loader.load(filePart("file:///repo/$path"))) }
    }

    @Test
    fun `production lookup uses connection origin and exact-generation auth client atomically`() = runTest {
        val expected = byteArrayOf(4, 5, 6)
        val body = TrackingByteArrayBody(expected, "image/webp".toMediaType())
        var authorization: String? = null
        val server = ServerRef.fromEndpoint("https://server.test:4096")
        val generation = ServerGeneration(7)
        val workspaceClient = mockk<WorkspaceClient> {
            every { workspace } returns Workspace(server, "/repo")
            every { this@mockk.generation } returns generation
        }
        val authClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer exact-generation")
                        .build()
                )
            }
            .addInterceptor { chain ->
                authorization = chain.request().header("Authorization")
                syntheticResponse(chain.request(), body = body)
            }
            .build()
        val transport = terminalTransport(
            authClient = authClient,
            serverUrl = "https://server.test",
        )
        val registry = mockk<ServerConnectionRegistry> {
            every { terminalTransport(server, generation) } returns transport
        }
        val loader = WorkspaceChatMediaLoader(workspaceClient, registry)

        val result = loader.load(
            filePart("https://server.test/media/image.webp", mime = "image/webp")
        ) as ChatMediaLoadResult.Loaded
        val explicitPort = loader.load(
            filePart("https://server.test:4096/media/image.webp", mime = "image/webp")
        )

        assertEquals("Bearer exact-generation", authorization)
        assertArrayEquals(expected, result.bytes)
        assertEquals("image/webp", result.mimeType)
        assertUnavailable(explicitPort)
        assertTrue(body.closed)
        verify(exactly = 2) { registry.terminalTransport(server, generation) }
    }

    @Test
    fun `rejects cross-origin credentials and unsupported schemes without a request`() = runTest {
        val lookups = AtomicInteger()
        val requests = AtomicInteger()
        val client = syntheticClient {
            requests.incrementAndGet()
            syntheticResponse(it, body = TrackingByteArrayBody(byteArrayOf(1), IMAGE_PNG))
        }
        val loader = loader(
            terminalTransportLookup = {
                lookups.incrementAndGet()
                terminalTransport(client)
            },
        )
        val cases = listOf(
            "https://other.test/image.png",
            "https://user:secret@server.test/image.png",
            "ftp://server.test/image.png",
            "content://server.test/image.png",
            "javascript:alert(1)",
        )

        cases.forEach { assertUnavailable(loader.load(filePart(it))) }
        assertEquals(1, lookups.get())
        assertEquals(0, requests.get())
    }

    @Test
    fun `rejects redirect and non-image HTTP responses and closes their bodies`() = runTest {
        val requests = mutableListOf<String>()
        val redirectBody = TrackingByteArrayBody(byteArrayOf(), IMAGE_PNG)
        val textBody = TrackingByteArrayBody("not an image".toByteArray(), TEXT_PLAIN)
        val client = syntheticClient { request ->
            requests += request.url.encodedPath
            when (request.url.encodedPath) {
                "/redirect" -> syntheticResponse(
                    request = request,
                    code = 302,
                    body = redirectBody,
                    location = "https://server.test/final.png",
                )
                else -> syntheticResponse(request, body = textBody)
            }
        }
        val loader = loader(terminalTransportLookup = { terminalTransport(client) })

        assertUnavailable(loader.load(filePart("https://server.test/redirect")))
        assertUnavailable(loader.load(filePart("https://server.test/not-image")))

        assertEquals(listOf("/redirect", "/not-image"), requests)
        assertTrue(redirectBody.closed)
        assertTrue(textBody.closed)
    }

    @Test
    fun `rejects HTTP when exact-generation transport is missing stale or has no origin`() = runTest {
        // The registry lookup returns null for both an absent transport and a stale generation.
        val unavailableTransportLoader = loader(terminalTransportLookup = { null })
        val malformedOriginLoader = loader(
            terminalTransportLookup = {
                terminalTransport(
                    authClient = OkHttpClient(),
                    serverUrl = "not a server URL",
                )
            },
        )

        assertUnavailable(unavailableTransportLoader.load(filePart("https://server.test/image.png")))
        assertUnavailable(malformedOriginLoader.load(filePart("https://server.test/image.png")))
    }

    @Test
    fun `rejects known oversized and unknown-length HTTP bodies crossing cap`() = runTest {
        val knownBody = GeneratingResponseBody(
            declaredLength = MAX_CHAT_MEDIA_BYTES.toLong() + 1L,
            bytesToEmit = MAX_CHAT_MEDIA_BYTES.toLong() + 1L,
        )
        val unknownBody = GeneratingResponseBody(
            declaredLength = -1L,
            bytesToEmit = MAX_CHAT_MEDIA_BYTES.toLong() + 1L,
        )
        val client = syntheticClient { request ->
            val body = if (request.url.encodedPath == "/known") knownBody else unknownBody
            syntheticResponse(request, body = body)
        }
        val loader = loader(terminalTransportLookup = { terminalTransport(client) })

        assertUnavailable(loader.load(filePart("https://server.test/known")))
        assertUnavailable(loader.load(filePart("https://server.test/unknown")))

        assertEquals(0, knownBody.readCount.get())
        assertTrue(unknownBody.readCount.get() > 0)
        assertTrue(knownBody.closed)
        assertTrue(unknownBody.closed)
    }

    @Test
    fun `cancellation cancels in-flight HTTP call and is not converted to unavailable`() = runTest {
        val started = CountDownLatch(1)
        val canceled = CountDownLatch(1)
        val call = mockk<Call>(relaxed = true)
        every { call.enqueue(any()) } answers { started.countDown() }
        every { call.cancel() } answers { canceled.countDown() }
        val loader = loader(
            terminalTransportLookup = { terminalTransport(OkHttpClient()) },
            httpCallFactory = { _, _ -> call },
        )
        val result = async { loader.load(filePart("https://server.test/slow.png")) }
        runCurrent()

        assertTrue(started.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        result.cancelAndJoin()

        assertTrue(result.isCancelled)
        assertTrue(canceled.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        verify(exactly = 1) { call.cancel() }
    }

    @Test
    fun `derived HTTP client replaces inherited infinite read and call timeouts`() = runTest {
        val body = TrackingByteArrayBody(byteArrayOf(1), IMAGE_PNG)
        val authClient = syntheticClient { request -> syntheticResponse(request, body = body) }
            .newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
        var derivedClient: OkHttpClient? = null
        val loader = loader(
            terminalTransportLookup = { terminalTransport(authClient) },
            httpCallFactory = { client, request ->
                derivedClient = client
                client.newCall(request)
            },
        )

        val result = loader.load(filePart("https://server.test/image.png"))

        assertTrue(result is ChatMediaLoadResult.Loaded)
        assertEquals(0, authClient.readTimeoutMillis)
        assertEquals(0, authClient.callTimeoutMillis)
        assertEquals(EXPECTED_MEDIA_TIMEOUT_MILLIS, derivedClient?.readTimeoutMillis)
        assertEquals(EXPECTED_MEDIA_TIMEOUT_MILLIS, derivedClient?.callTimeoutMillis)
        assertTrue(body.closed)
    }

    private fun loader(
        workspaceDirectory: String? = "/repo",
        workspaceFileReader: suspend (String, Long) -> FileContentDto = { _, _ ->
            error("Unexpected workspace read")
        },
        terminalTransportLookup: () -> TerminalTransport? = { null },
        httpCallFactory: (OkHttpClient, Request) -> Call = { client, request -> client.newCall(request) },
    ): WorkspaceChatMediaLoader = WorkspaceChatMediaLoader(
        workspaceDirectory = workspaceDirectory,
        workspaceFileReader = workspaceFileReader,
        terminalTransportLookup = terminalTransportLookup,
        httpCallFactory = httpCallFactory,
    )

    private fun terminalTransport(
        authClient: OkHttpClient,
        serverUrl: String = "https://server.test/api",
    ): TerminalTransport = TerminalTransport(
        connection = mockk<Connection> {
            every { config } returns ServerConfig(url = serverUrl)
        },
        authClient = authClient,
    )

    private fun filePart(url: String, mime: String = "image/png"): Part.File = Part.File(
        id = "part-1",
        sessionID = "session-1",
        messageID = "message-1",
        mime = mime,
        filename = null,
        url = url,
    )

    private fun imageFileContent(bytes: ByteArray): FileContentDto = FileContentDto(
        type = "text",
        content = Base64.getEncoder().encodeToString(bytes),
        encoding = "base64",
        mimeType = "image/png",
    )

    private fun assertUnavailable(result: ChatMediaLoadResult) {
        assertSame(ChatMediaLoadResult.Unavailable, result)
    }

    private fun syntheticClient(handler: (Request) -> Response): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain -> handler(chain.request()) }
        .build()

    private fun syntheticResponse(
        request: Request,
        code: Int = 200,
        body: ResponseBody,
        location: String? = null,
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("Synthetic")
        .body(body)
        .apply { location?.let { header("Location", it) } }
        .build()

    private class TrackingByteArrayBody(
        bytes: ByteArray,
        private val mediaType: MediaType,
    ) : ResponseBody() {
        var closed = false
            private set
        private val input = object : ByteArrayInputStream(bytes) {
            override fun close() {
                closed = true
                super.close()
            }
        }
        private val bufferedSource by lazy { input.source().buffer() }

        override fun contentType(): MediaType = mediaType

        override fun contentLength(): Long = input.available().toLong()

        override fun source(): BufferedSource = bufferedSource
    }

    private class GeneratingResponseBody(
        private val declaredLength: Long,
        bytesToEmit: Long,
    ) : ResponseBody() {
        val readCount = AtomicInteger()
        var closed = false
            private set
        private val input = GeneratingInputStream(bytesToEmit, readCount) { closed = true }
        private val bufferedSource by lazy { input.source().buffer() }

        override fun contentType(): MediaType = IMAGE_PNG

        override fun contentLength(): Long = declaredLength

        override fun source(): BufferedSource = bufferedSource
    }

    private class GeneratingInputStream(
        private var remaining: Long,
        private val readCount: AtomicInteger,
        private val onClose: () -> Unit,
    ) : InputStream() {
        override fun read(): Int {
            if (remaining == 0L) return -1
            remaining -= 1L
            readCount.incrementAndGet()
            return 0
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining == 0L) return -1
            val count = minOf(remaining, length.toLong()).toInt()
            buffer.fill(0, offset, offset + count)
            remaining -= count
            readCount.incrementAndGet()
            return count
        }

        override fun close() {
            onClose()
        }
    }

    private companion object {
        val IMAGE_PNG: MediaType = "image/png".toMediaType()
        val TEXT_PLAIN: MediaType = "text/plain".toMediaType()
        const val AWAIT_SECONDS = 5L
        const val EXPECTED_MEDIA_TIMEOUT_MILLIS = 60_000
    }
}
