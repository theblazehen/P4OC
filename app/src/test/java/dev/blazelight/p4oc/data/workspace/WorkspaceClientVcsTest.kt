package dev.blazelight.p4oc.data.workspace

import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.OpenCodeApi
import dev.blazelight.p4oc.data.server.ActiveServerApiProvider
import dev.blazelight.p4oc.data.vcs.VcsDiffMode
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.workspace.Workspace
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WorkspaceClientVcsTest {
    @Test
    fun `VCS reads use captured directory explicit null workspace and closed diff arguments`() = runTest {
        val api = mockk<OpenCodeApi>()
        coEvery { api.getVcsInfoRaw("/repo/exact", null) } returns jsonResponse(INFO_JSON)
        coEvery { api.getVcsStatusRaw("/repo/exact", null) } returns jsonResponse(STATUS_JSON)
        coEvery { api.getVcsDiffRaw(VcsDiffMode.Branch, 7, "/repo/exact", null) } returns jsonResponse(DIFF_JSON)
        val client = client(api, directory = "/repo/exact")

        assertEquals("feature/changes", client.loadWorkspaceVcsInfo().branch)
        assertEquals("src/雪.kt", client.loadWorkspaceVcsStatus().single().file)
        assertEquals("@@ patch", client.loadWorkspaceVcsDiff(VcsDiffMode.Branch, 7).single().patch)

        coVerify(exactly = 1) { api.getVcsInfoRaw("/repo/exact", null) }
        coVerify(exactly = 1) { api.getVcsStatusRaw("/repo/exact", null) }
        coVerify(exactly = 1) { api.getVcsDiffRaw(VcsDiffMode.Branch, 7, "/repo/exact", null) }
    }

    @Test
    fun `VCS reads preserve intentional global directory without fallback`() = runTest {
        val api = mockk<OpenCodeApi>()
        coEvery { api.getVcsInfoRaw(null, null) } returns jsonResponse(INFO_JSON)
        coEvery { api.getVcsStatusRaw(null, null) } returns jsonResponse("[]")
        coEvery { api.getVcsDiffRaw(VcsDiffMode.Git, 3, null, null) } returns jsonResponse("[]")
        val client = client(api, directory = null)

        client.loadWorkspaceVcsInfo()
        client.loadWorkspaceVcsStatus()
        client.loadWorkspaceVcsDiff(VcsDiffMode.Git, 3)

        coVerify(exactly = 1) { api.getVcsInfoRaw(null, null) }
        coVerify(exactly = 1) { api.getVcsStatusRaw(null, null) }
        coVerify(exactly = 1) { api.getVcsDiffRaw(VcsDiffMode.Git, 3, null, null) }
    }

    @Test
    fun `negative diff context is rejected before API resolution`() = runTest {
        val api = mockk<OpenCodeApi>()
        var providerCalls = 0
        val client = client(api, apiResolved = { providerCalls++ })

        try {
            client.loadWorkspaceVcsDiff(VcsDiffMode.Git, -1)
            fail("Expected negative context to be rejected")
        } catch (error: IllegalArgumentException) {
            assertEquals("context must be non-negative", error.message)
        }

        assertEquals(0, providerCalls)
        coVerify(exactly = 0) { api.getVcsDiffRaw(any(), any(), any(), any()) }
    }

    @Test
    fun `each VCS route rejects declared overflow before reading and closes body`() = runTest {
        Route.entries.forEach { route ->
            val api = mockk<OpenCodeApi>()
            val body = mockk<ResponseBody>(relaxed = true)
            every { body.contentLength() } returns route.limit + 1L
            stubRoute(api, route, Response.success(body))

            assertTooLarge { loadRoute(client(api), route) }

            verify(exactly = 0) { body.source() }
            verify(exactly = 1) { body.close() }
        }
    }

    @Test
    fun `each VCS route stops unknown or understated streams at cap plus one and closes`() = runTest {
        Route.entries.forEachIndexed { index, route ->
            val api = mockk<OpenCodeApi>()
            val source = Buffer().write(ByteArray((route.limit + EXTRA_STREAM_BYTES).toInt()))
            val body = mockk<ResponseBody>(relaxed = true)
            every { body.contentLength() } returns when (index) {
                0 -> -1L
                1 -> 0L
                else -> 1L
            }
            every { body.source() } returns source
            stubRoute(api, route, Response.success(body))

            assertTooLarge { loadRoute(client(api), route) }

            assertEquals(EXTRA_STREAM_BYTES - 1L, source.size)
            verify(exactly = 1) { body.close() }
        }
    }

    @Test
    fun `each VCS route accepts exact raw cap and closes success body`() = runTest {
        Route.entries.forEach { route ->
            val api = mockk<OpenCodeApi>()
            val content = paddedJson(route.validJson, route.limit)
            val source = Buffer().writeUtf8(content)
            val body = mockk<ResponseBody>(relaxed = true)
            every { body.contentLength() } returns route.limit
            every { body.source() } returns source
            stubRoute(api, route, Response.success(body))

            loadRoute(client(api), route)

            assertEquals(0L, source.size)
            verify(exactly = 1) { body.close() }
        }
    }

    @Test
    fun `each VCS route closes non-success error body`() = runTest {
        Route.entries.forEach { route ->
            val api = mockk<OpenCodeApi>()
            val errorBody = mockk<ResponseBody>(relaxed = true)
            stubRoute(api, route, Response.error(403, errorBody))

            try {
                loadRoute(client(api), route)
                fail("Expected HTTP failure for ${route.name}")
            } catch (error: HttpException) {
                assertEquals(403, error.code())
            }

            verify(exactly = 1) { errorBody.close() }
        }
    }

    @Test
    fun `each VCS route closes successful body when strict decoding fails`() = runTest {
        Route.entries.forEach { route ->
            val api = mockk<OpenCodeApi>()
            val source = Buffer().writeUtf8("{malformed")
            val body = mockk<ResponseBody>(relaxed = true)
            every { body.contentLength() } returns source.size
            every { body.source() } returns source
            stubRoute(api, route, Response.success(body))

            try {
                loadRoute(client(api), route)
                fail("Expected malformed JSON for ${route.name}")
            } catch (_: SerializationException) {
                // Strict decoding failure is expected after the bounded read.
            }

            verify(exactly = 1) { body.close() }
        }
    }

    @Test
    fun `each VCS route reports invalid UTF-8 and closes its body`() = runTest {
        Route.entries.forEach { route ->
            val api = mockk<OpenCodeApi>()
            val source = Buffer().write(byteArrayOf(INVALID_UTF8_BYTE.toByte()))
            val body = mockk<ResponseBody>(relaxed = true)
            every { body.contentLength() } returns source.size
            every { body.source() } returns source
            stubRoute(api, route, Response.success(body))

            try {
                loadRoute(client(api), route)
                fail("Expected invalid UTF-8 for ${route.name}")
            } catch (error: SerializationException) {
                assertEquals("Response body is not valid UTF-8", error.message)
            }

            verify(exactly = 1) { body.close() }
        }
    }

    @Test
    fun `cancelling a VCS read closes body and propagates cancellation`() = runTest {
        val api = mockk<OpenCodeApi>()
        val body = BlockingResponseBody()
        coEvery { api.getVcsInfoRaw("/repo", null) } returns Response.success(body)
        val result = async(Dispatchers.IO) {
            client(api).loadWorkspaceVcsInfo()
        }

        try {
            assertTrue(body.readStarted.await(AWAIT_SECONDS, TimeUnit.SECONDS))
            result.cancel()
            assertTrue(body.closed.await(AWAIT_SECONDS, TimeUnit.SECONDS))
            try {
                result.await()
                fail("Expected VCS read cancellation to propagate")
            } catch (_: CancellationException) {
                // Cancellation must never become a domain failure.
            }
        } finally {
            body.close()
            result.cancelAndJoin()
        }
    }

    private fun client(
        api: OpenCodeApi,
        directory: String? = "/repo",
        apiResolved: () -> Unit = {},
    ): WorkspaceClient = WorkspaceClient(
        workspace = Workspace(
            server = ServerRef.fromEndpointKey("http://test.local", "Test server"),
            directory = directory,
        ),
        generation = ServerGeneration(1L),
        apiProvider = ActiveServerApiProvider { _, _ ->
            apiResolved()
            api
        },
        connectionState = MutableStateFlow(ConnectionState.Connected),
    )

    private fun stubRoute(api: OpenCodeApi, route: Route, response: Response<ResponseBody>) {
        when (route) {
            Route.Info -> coEvery { api.getVcsInfoRaw("/repo", null) } returns response
            Route.Status -> coEvery { api.getVcsStatusRaw("/repo", null) } returns response
            Route.Diff -> coEvery { api.getVcsDiffRaw(VcsDiffMode.Git, 3, "/repo", null) } returns response
        }
    }

    private suspend fun loadRoute(client: WorkspaceClient, route: Route) {
        when (route) {
            Route.Info -> client.loadWorkspaceVcsInfo()
            Route.Status -> client.loadWorkspaceVcsStatus()
            Route.Diff -> client.loadWorkspaceVcsDiff(VcsDiffMode.Git, 3)
        }
    }

    private suspend fun assertTooLarge(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected bounded VCS response to be rejected")
        } catch (error: BoundedResponseTooLargeException) {
            assertEquals("VCS response exceeds the allowed size", error.message)
        }
    }

    private fun paddedJson(json: String, byteCount: Long): String {
        val padding = byteCount.toInt() - json.toByteArray(Charsets.UTF_8).size
        require(padding >= 0)
        return json + " ".repeat(padding)
    }

    private fun jsonResponse(content: String): Response<ResponseBody> =
        Response.success(content.toResponseBody(JSON_MEDIA_TYPE))

    private class BlockingResponseBody : ResponseBody() {
        val readStarted = CountDownLatch(1)
        val closed = CountDownLatch(1)
        private val blockingSource = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                readStarted.countDown()
                while (true) {
                    try {
                        closed.await()
                        return -1L
                    } catch (_: InterruptedException) {
                        // Only closing the response may unblock this deliberately resistant source.
                    }
                }
            }

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() {
                closed.countDown()
            }
        }
        private val bufferedSource: BufferedSource = blockingSource.buffer()

        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = -1L
        override fun source(): BufferedSource = bufferedSource
    }

    private enum class Route(val limit: Long, val validJson: String) {
        Info(VCS_INFO_RESPONSE_LIMIT_BYTES, INFO_JSON),
        Status(VCS_STATUS_RESPONSE_LIMIT_BYTES, "[]"),
        Diff(VCS_DIFF_RESPONSE_LIMIT_BYTES, "[]"),
    }

    private companion object {
        const val AWAIT_SECONDS = 5L
        const val EXTRA_STREAM_BYTES = 8L
        const val INVALID_UTF8_BYTE = 0x80
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        const val INFO_JSON = """{"branch":"feature/changes","default_branch":"main"}"""
        const val STATUS_JSON =
            """[{"file":"src/雪.kt","status":"modified","additions":2,"deletions":1}]"""
        const val DIFF_JSON =
            """[{"file":"src/雪.kt","patch":"@@ patch","additions":2,"deletions":1}]"""
    }
}
