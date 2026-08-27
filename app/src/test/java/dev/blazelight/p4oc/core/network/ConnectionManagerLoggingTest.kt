package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.data.remote.mapper.EventMapper
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionManagerLoggingTest {

    @Test
    fun `authenticated client never follows redirects`() {
        val client = manager.buildBaseOkHttpClient(
            ServerConfig(url = "https://opencode.test", username = "user"),
            password = "secret",
        )

        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    @Test
    fun `sse client uses a finite read timeout to detect silent dead sockets`() {
        val base = manager.buildBaseOkHttpClient(
            ServerConfig(url = "https://opencode.test", username = "user"),
            password = "secret",
        )
        val sseClient = manager.buildSseOkHttpClient(base)

        // 60s finite read timeout so a half-open SSE socket that stops producing
        // frames (heartbeats) eventually errors and the library reconnects (issue #14).
        assertEquals(60_000, sseClient.readTimeoutMillis)
    }

    private val dispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var manager: ConnectionManager

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        manager = ConnectionManager(
            json = json,
            eventMapper = EventMapper(json, MessageMapper()),
            settingsDataStore = mockk<SettingsDataStore>(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `debug diagnostics never log provider auth or oauth bodies`() {
        val logLines = mutableListOf<String>()
        val loggingInterceptor = manager.createDiagnosticLoggingInterceptor(
            debugLoggingEnabled = true,
            logger = { logLines += it },
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{\"access\":\"response-access-token\"}".toResponseBody(JSON))
                    .build()
            }
            .build()

        listOf(
            "/provider/openai/auth" to "{\"apiKey\":\"request-api-secret\"}",
            "/provider/github/auth/callback" to "{\"code\":\"request-oauth-code\"}",
        ).forEach { (path, requestJson) ->
            val request = Request.Builder()
                .url("https://opencode.test$path")
                .header("Authorization", "Bearer request-header-token")
                .post(requestJson.toRequestBody(JSON))
                .build()

            client.newCall(request).execute().use { response ->
                // Consume the body so logging behavior cannot depend on the caller ignoring it.
                response.body.string()
            }
        }

        val output = logLines.joinToString("\n")
        assertTrue(output.contains("POST https://opencode.test/provider/openai/auth"))
        assertTrue(output.contains("Authorization: ██"))
        assertFalse(output.contains("request-api-secret"))
        assertFalse(output.contains("request-oauth-code"))
        assertFalse(output.contains("request-header-token"))
        assertFalse(output.contains("response-access-token"))
    }

    @Test
    fun `release diagnostics are disabled`() {
        val logLines = mutableListOf<String>()
        val interceptor = manager.createDiagnosticLoggingInterceptor(
            debugLoggingEnabled = false,
            logger = { logLines += it },
        )

        assertTrue(logLines.isEmpty())
        assertTrue(interceptor.level == okhttp3.logging.HttpLoggingInterceptor.Level.NONE)
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
