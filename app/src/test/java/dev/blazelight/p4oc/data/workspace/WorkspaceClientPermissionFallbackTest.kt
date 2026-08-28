package dev.blazelight.p4oc.data.workspace

import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.OpenCodeApi
import dev.blazelight.p4oc.data.remote.dto.PermissionResponseRequest
import dev.blazelight.p4oc.data.server.ActiveServerApiProvider
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.workspace.Workspace
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.BufferedSource
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.ByteArrayInputStream
import java.io.IOException

class WorkspaceClientPermissionFallbackTest {
    @Test
    fun `real retrofit transport closes html v2 body then routes one legacy mutation`() = runTest {
        val routes = mutableListOf<Request>()
        val htmlBody = TrackingResponseBody(
            content = "<html>legacy server</html>",
            mediaType = "text/html; charset=utf-8".toMediaType(),
        )
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                routes += request
                when (routes.size) {
                    1 -> transportResponse(request, htmlBody)
                    2 -> transportResponse(
                        request,
                        "true".toResponseBody("application/json".toMediaType()),
                    )
                    else -> throw AssertionError("Unexpected third permission mutation: ${request.url}")
                }
            }
            .build()
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = false
            explicitNulls = false
            coerceInputValues = true
        }
        val api = Retrofit.Builder()
            .baseUrl("http://test.local/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenCodeApi::class.java)
        val request = PermissionResponseRequest(reply = "once")

        assertEquals(true, workspaceClient(api).respondToPermission("ses_1", "per_1", request))

        assertEquals(true, htmlBody.closed)
        assertEquals(
            listOf(
                "/api/session/ses_1/permission/per_1/reply",
                "/permission/per_1/reply",
            ),
            routes.map { it.url.encodedPath },
        )
        assertEquals(listOf("POST", "POST"), routes.map { it.method })
        assertEquals(emptySet<String>(), routes[0].url.queryParameterNames)
        assertEquals(setOf("directory"), routes[1].url.queryParameterNames)
        assertEquals("/repo", routes[1].url.queryParameter("directory"))
        assertEquals(null, routes[1].url.queryParameter("workspace"))
        assertEquals(2, routes.size)
    }

    @Test
    fun `permission v2 ordinary success closes body and does not call legacy`() = runTest {
        val api = mockk<OpenCodeApi>()
        val request = PermissionResponseRequest(reply = "once")
        val sourceBody = mockk<ResponseBody>(relaxed = true)
        val metadataBody = mockk<ResponseBody>(relaxed = true)
        every { metadataBody.contentType() } returns "application/json".toMediaType()
        coEvery { api.respondToPermissionV2("ses_1", "per_1", request) } answers {
            sourceBody.close()
            unitSuccessResponse(metadataBody)
        }
        val client = workspaceClient(api)

        assertEquals(true, client.respondToPermission("ses_1", "per_1", request))

        verify(exactly = 1) { sourceBody.close() }
        verify(exactly = 0) { metadataBody.close() }
        coVerify(exactly = 1) { api.respondToPermissionV2("ses_1", "per_1", request) }
        coVerify(exactly = 0) { api.respondToPermission(any(), any(), any(), any()) }
    }

    @Test
    fun `permission v2 404 closes body before one legacy call with exact workspace scope`() = runTest {
        val api = mockk<OpenCodeApi>()
        val request = PermissionResponseRequest(reply = "always", message = "approved")
        val responseBody = mockk<ResponseBody>(relaxed = true)
        coEvery { api.respondToPermissionV2("ses_1", "per_1", request) } returns
            Response.error(404, responseBody)
        coEvery { api.respondToPermission("per_1", request, "/repo", null) } answers {
            verify(exactly = 1) { responseBody.close() }
            false
        }
        val client = workspaceClient(api)

        assertEquals(false, client.respondToPermission("ses_1", "per_1", request))

        verify(exactly = 1) { responseBody.close() }
        coVerify(exactly = 1) { api.respondToPermissionV2("ses_1", "per_1", request) }
        coVerify(exactly = 1) { api.respondToPermission("per_1", request, "/repo", null) }
        coVerify(exactly = 1) { api.respondToPermission(any(), any(), any(), any()) }
    }

    @Test
    fun `permission v2 successful html header trims whitespace and ignores case then falls back once`() = runTest {
        val api = mockk<OpenCodeApi>()
        val request = PermissionResponseRequest(reply = "once")
        val sourceBody = mockk<ResponseBody>(relaxed = true)
        val metadataBody = mockk<ResponseBody>(relaxed = true)
        every { metadataBody.contentType() } returns null
        coEvery { api.respondToPermissionV2("ses_1", "per_header", request) } answers {
            sourceBody.close()
            unitSuccessResponse(metadataBody, headerContentType = " TeXt/HtMl \t; Charset=UTF-8 ")
        }
        coEvery { api.respondToPermission("per_header", request, "/repo", null) } answers {
            verify(exactly = 1) { sourceBody.close() }
            verify(exactly = 0) { metadataBody.close() }
            true
        }
        val client = workspaceClient(api)

        assertEquals(true, client.respondToPermission("ses_1", "per_header", request))

        verify(exactly = 1) { sourceBody.close() }
        verify(exactly = 0) { metadataBody.close() }
        coVerify(exactly = 1) { api.respondToPermissionV2("ses_1", "per_header", request) }
        coVerify(exactly = 1) { api.respondToPermission("per_header", request, "/repo", null) }
        coVerify(exactly = 1) { api.respondToPermission(any(), any(), any(), any()) }
    }

    @Test
    fun `permission v2 html raw metadata overrides conflicting non-html header`() = runTest {
        val api = mockk<OpenCodeApi>()
        val request = PermissionResponseRequest(reply = "reject")
        val sourceBody = mockk<ResponseBody>(relaxed = true)
        val metadataBody = mockk<ResponseBody>(relaxed = true)
        every { metadataBody.contentType() } returns "TeXt/HtMl; Charset=UTF-8".toMediaType()
        coEvery { api.respondToPermissionV2("ses_1", "per_body", request) } answers {
            sourceBody.close()
            unitSuccessResponse(metadataBody, headerContentType = "application/json; charset=utf-8")
        }
        coEvery { api.respondToPermission("per_body", request, "/repo", null) } answers {
            verify(exactly = 1) { sourceBody.close() }
            verify(exactly = 0) { metadataBody.close() }
            true
        }
        val client = workspaceClient(api)

        assertEquals(true, client.respondToPermission("ses_1", "per_body", request))

        verify(exactly = 1) { sourceBody.close() }
        verify(exactly = 0) { metadataBody.close() }
        coVerify(exactly = 1) { api.respondToPermissionV2("ses_1", "per_body", request) }
        coVerify(exactly = 1) { api.respondToPermission("per_body", request, "/repo", null) }
        coVerify(exactly = 1) { api.respondToPermission(any(), any(), any(), any()) }
    }

    @Test
    fun `permission v2 html server error closes body and propagates without legacy`() = runTest {
        val api = mockk<OpenCodeApi>()
        val request = PermissionResponseRequest(reply = "once")
        val responseBody = mockk<ResponseBody>(relaxed = true)
        every { responseBody.contentType() } returns "text/html; charset=utf-8".toMediaType()
        val response = unitErrorResponse(
            code = 500,
            body = responseBody,
            headerContentType = "text/html; charset=utf-8",
        )
        coEvery { api.respondToPermissionV2("ses_1", "per_1", request) } returns response
        val client = workspaceClient(api)

        assertSame(
            response,
            assertHttpResponse(500) {
                client.respondToPermission("ses_1", "per_1", request)
            },
        )

        verify(exactly = 1) { responseBody.close() }
        coVerify(exactly = 1) { api.respondToPermissionV2("ses_1", "per_1", request) }
        coVerify(exactly = 0) { api.respondToPermission(any(), any(), any(), any()) }
    }

    @Test
    fun `permission v2 ordinary error closes body and propagates without legacy`() = runTest {
        val api = mockk<OpenCodeApi>()
        val request = PermissionResponseRequest(reply = "once")
        val responseBody = mockk<ResponseBody>(relaxed = true)
        val response = Response.error<Unit>(403, responseBody)
        coEvery { api.respondToPermissionV2("ses_1", "per_1", request) } returns response
        val client = workspaceClient(api)

        assertSame(
            response,
            assertHttpResponse(403) {
                client.respondToPermission("ses_1", "per_1", request)
            },
        )

        verify(exactly = 1) { responseBody.close() }
        coVerify(exactly = 1) { api.respondToPermissionV2("ses_1", "per_1", request) }
        coVerify(exactly = 0) { api.respondToPermission(any(), any(), any(), any()) }
    }

    @Test
    fun `permission v2 non-fallback status matrix preserves response and closes error body`() = runTest {
        val cases = listOf(302, 401, 422, 429, 503).map { status ->
            val responseBody = mockk<ResponseBody>(relaxed = true)
            Triple(status, unitErrorResponse(status, responseBody), responseBody)
        }

        cases.forEach { (status, response, responseBody) ->
            val api = mockk<OpenCodeApi>()
            val request = PermissionResponseRequest(reply = "once")
            coEvery { api.respondToPermissionV2("ses_1", "per_1", request) } returns response
            val client = workspaceClient(api)

            assertSame(
                response,
                assertHttpResponse(status) {
                    client.respondToPermission("ses_1", "per_1", request)
                },
            )

            verify(exactly = 1) { responseBody.close() }
            coVerify(exactly = 1) { api.respondToPermissionV2("ses_1", "per_1", request) }
            coVerify(exactly = 0) { api.respondToPermission(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `permission v2 cancellation propagates without legacy fallback`() = runTest {
        val api = mockk<OpenCodeApi>()
        val request = PermissionResponseRequest(reply = "once")
        val cancellation = CancellationException("v2 cancelled")
        coEvery { api.respondToPermissionV2("ses_1", "per_1", request) } throws cancellation
        val client = workspaceClient(api)

        try {
            client.respondToPermission("ses_1", "per_1", request)
            fail("Expected v2 cancellation")
        } catch (error: CancellationException) {
            assertSame(cancellation, error)
        }

        coVerify(exactly = 1) { api.respondToPermissionV2("ses_1", "per_1", request) }
        coVerify(exactly = 0) { api.respondToPermission(any(), any(), any(), any()) }
    }

    @Test
    fun `permission legacy cancellation after fallback is not retried`() = runTest {
        val api = mockk<OpenCodeApi>()
        val request = PermissionResponseRequest(reply = "once")
        val responseBody = mockk<ResponseBody>(relaxed = true)
        val cancellation = CancellationException("legacy cancelled")
        coEvery { api.respondToPermissionV2("ses_1", "per_1", request) } returns
            Response.error(404, responseBody)
        coEvery { api.respondToPermission("per_1", request, "/repo", null) } throws cancellation
        val client = workspaceClient(api)

        try {
            client.respondToPermission("ses_1", "per_1", request)
            fail("Expected legacy cancellation")
        } catch (error: CancellationException) {
            assertSame(cancellation, error)
        }

        verify(exactly = 1) { responseBody.close() }
        coVerify(exactly = 1) { api.respondToPermissionV2("ses_1", "per_1", request) }
        coVerify(exactly = 1) { api.respondToPermission("per_1", request, "/repo", null) }
    }

    @Test
    fun `permission legacy io failure after fallback propagates exactly without retry`() = runTest {
        val api = mockk<OpenCodeApi>()
        val request = PermissionResponseRequest(reply = "once")
        val responseBody = mockk<ResponseBody>(relaxed = true)
        val failure = IOException("legacy permission transport failure")
        coEvery { api.respondToPermissionV2("ses_1", "per_1", request) } returns
            unitErrorResponse(404, responseBody)
        coEvery { api.respondToPermission("per_1", request, "/repo", null) } throws failure
        val client = workspaceClient(api)

        try {
            client.respondToPermission("ses_1", "per_1", request)
            fail("Expected legacy permission transport failure")
        } catch (error: IOException) {
            assertSame(failure, error)
        }

        verify(exactly = 1) { responseBody.close() }
        coVerify(exactly = 1) { api.respondToPermissionV2("ses_1", "per_1", request) }
        coVerify(exactly = 1) { api.respondToPermission("per_1", request, "/repo", null) }
    }

    @Test
    fun `permission fallback preserves intentional null workspace directory`() = runTest {
        val api = mockk<OpenCodeApi>()
        val request = PermissionResponseRequest(reply = "once")
        val responseBody = mockk<ResponseBody>(relaxed = true)
        coEvery { api.respondToPermissionV2("ses_1", "per_1", request) } returns
            Response.error(404, responseBody)
        coEvery { api.respondToPermission("per_1", request, null, null) } returns true
        val client = workspaceClient(api, directory = null)

        assertEquals(true, client.respondToPermission("ses_1", "per_1", request))

        verify(exactly = 1) { responseBody.close() }
        coVerify(exactly = 1) { api.respondToPermissionV2("ses_1", "per_1", request) }
        coVerify(exactly = 1) { api.respondToPermission("per_1", request, null, null) }
    }

    private fun workspaceClient(
        api: OpenCodeApi,
        directory: String? = "/repo",
    ): WorkspaceClient {
        val server = ServerRef.fromEndpointKey("http://test.local")
        return WorkspaceClient(
            workspace = Workspace(server, directory = directory),
            generation = ServerGeneration(1L),
            apiProvider = ActiveServerApiProvider { _, _ -> api },
            connectionState = MutableStateFlow(ConnectionState.Connected),
        )
    }

    private suspend fun assertHttpResponse(
        code: Int,
        block: suspend () -> Unit,
    ): Response<*> = try {
        block()
        fail("Expected HTTP $code")
        error("Unreachable")
    } catch (error: HttpException) {
        assertEquals(code, error.code())
        requireNotNull(error.response())
    }

    private fun unitSuccessResponse(
        metadataBody: ResponseBody,
        headerContentType: String? = null,
    ): Response<Unit> = Response.success(Unit, rawResponse(200, metadataBody, headerContentType))

    private fun unitErrorResponse(
        code: Int,
        body: ResponseBody,
        headerContentType: String? = null,
    ): Response<Unit> = Response.error(
        body,
        rawResponse(code, body = null, headerContentType = headerContentType),
    )

    private fun rawResponse(
        code: Int,
        body: ResponseBody?,
        headerContentType: String?,
    ): okhttp3.Response {
        val builder = okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("http://test.local").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
        if (body != null) builder.body(body)
        if (headerContentType != null) builder.header("Content-Type", headerContentType)
        return builder.build()
    }

    private fun transportResponse(request: Request, body: ResponseBody): okhttp3.Response =
        okhttp3.Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()

    private class TrackingResponseBody(
        content: String,
        private val mediaType: MediaType,
    ) : ResponseBody() {
        var closed = false
            private set
        private val bytes = content.encodeToByteArray()
        private val input = object : ByteArrayInputStream(bytes) {
            override fun close() {
                closed = true
                super.close()
            }
        }
        private val bufferedSource by lazy { input.source().buffer() }

        override fun contentType(): MediaType? = mediaType

        override fun contentLength(): Long = bytes.size.toLong()

        override fun source(): BufferedSource = bufferedSource
    }
}
