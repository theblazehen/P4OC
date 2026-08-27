package dev.blazelight.p4oc.data.workspace

import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.OpenCodeApi
import dev.blazelight.p4oc.data.remote.dto.ConfigDto
import dev.blazelight.p4oc.data.remote.dto.ForkSessionRequest
import dev.blazelight.p4oc.data.remote.dto.ProvidersResponseDto
import dev.blazelight.p4oc.data.remote.dto.QuestionDto
import dev.blazelight.p4oc.data.remote.dto.QuestionOptionDto
import dev.blazelight.p4oc.data.remote.dto.QuestionReplyRequest
import dev.blazelight.p4oc.data.remote.dto.QuestionRequestDto
import dev.blazelight.p4oc.data.remote.dto.QuestionV2RequestListResponseDto
import dev.blazelight.p4oc.data.server.ActiveServerApiProvider
import dev.blazelight.p4oc.data.server.StaleWorkspaceClientException
import dev.blazelight.p4oc.di.appModule
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.workspace.Workspace
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.koin.dsl.koinApplication
import retrofit2.HttpException
import retrofit2.Response
import java.net.SocketTimeoutException

class WorkspaceClientTest {
    @Test
    fun `fork request omits null message id with production json`() {
        val application = koinApplication { modules(appModule) }

        try {
            val json = application.koin.get<Json>()

            assertEquals("{}", json.encodeToString(ForkSessionRequest(messageID = null)))
        } finally {
            application.close()
        }
    }

    @Test
    fun `question operations use legacy endpoints without v2 requests`() = runTest {
        val api = mockk<OpenCodeApi>()
        val question = questionRequest("ses_1")
        val otherQuestion = questionRequest("ses_other")
        val reply = QuestionReplyRequest(listOf(listOf("Yes")))
        coEvery { api.listPendingQuestions("/repo", null) } returns Response.success(listOf(question, otherQuestion))
        coEvery { api.respondToQuestion("que_1", reply, "/repo", null) } returns Response.success(true)
        coEvery { api.rejectQuestion("que_1", "/repo", null) } returns Response.success(true)
        val client = questionClient(api)

        assertEquals(listOf(question), client.listSessionQuestions("ses_1"))
        assertEquals(true, client.respondToQuestion("ses_1", "que_1", reply))
        assertEquals(true, client.rejectQuestion("ses_1", "que_1"))

        coVerify(exactly = 0) { api.listSessionQuestionsV2(any()) }
        coVerify(exactly = 0) { api.respondToQuestionV2(any(), any(), any()) }
        coVerify(exactly = 0) { api.rejectQuestionV2(any(), any()) }
    }

    @Test
    fun `question operations use v2 only when legacy endpoint is unavailable`() = runTest {
        val api = mockk<OpenCodeApi>()
        val question = questionRequest("ses_1")
        val reply = QuestionReplyRequest(listOf(listOf("Yes")))
        coEvery { api.listPendingQuestions("/repo", null) } returns
            Response.error(404, "{}".toResponseBody("application/json".toMediaType()))
        coEvery { api.respondToQuestion("que_1", reply, "/repo", null) } returns
            Response.error(404, "{}".toResponseBody("application/json".toMediaType()))
        coEvery { api.rejectQuestion("que_1", "/repo", null) } returns
            Response.error(404, "{}".toResponseBody("application/json".toMediaType()))
        coEvery { api.listSessionQuestionsV2("ses_1") } returns
            Response.success(QuestionV2RequestListResponseDto(listOf(question)))
        coEvery { api.respondToQuestionV2("ses_1", "que_1", reply) } returns Response.success(Unit)
        coEvery { api.rejectQuestionV2("ses_1", "que_1") } returns Response.success(Unit)
        val client = questionClient(api)

        assertEquals(listOf(question), client.listSessionQuestions("ses_1"))
        assertEquals(true, client.respondToQuestion("ses_1", "que_1", reply))
        assertEquals(true, client.rejectQuestion("ses_1", "que_1"))
    }

    @Test
    fun `question legacy http errors propagate without v2 fallback`() = runTest {
        val api = mockk<OpenCodeApi>()
        val reply = QuestionReplyRequest(listOf(listOf("Yes")))
        coEvery { api.respondToQuestion("que_1", reply, "/repo", null) } returns
            Response.error(401, "{}".toResponseBody("application/json".toMediaType()))
        coEvery { api.rejectQuestion("que_1", "/repo", null) } returns
            Response.error(422, "{}".toResponseBody("application/json".toMediaType()))
        coEvery { api.listPendingQuestions("/repo", null) } returns
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))
        val client = questionClient(api)

        assertHttpError(401) { client.respondToQuestion("ses_1", "que_1", reply) }
        assertHttpError(422) { client.rejectQuestion("ses_1", "que_1") }
        assertHttpError(500) { client.listSessionQuestions("ses_1") }

        coVerify(exactly = 0) { api.respondToQuestionV2(any(), any(), any()) }
        coVerify(exactly = 0) { api.rejectQuestionV2(any(), any()) }
        coVerify(exactly = 0) { api.listSessionQuestionsV2(any()) }
    }

    @Test
    fun `question legacy timeout propagates exactly without v2 fallback`() = runTest {
        val api = mockk<OpenCodeApi>()
        val reply = QuestionReplyRequest(listOf(listOf("Yes")))
        val timeout = SocketTimeoutException("legacy timeout")
        coEvery { api.respondToQuestion("que_1", reply, "/repo", null) } throws timeout
        val client = questionClient(api)

        try {
            client.respondToQuestion("ses_1", "que_1", reply)
            fail("Expected legacy timeout")
        } catch (error: SocketTimeoutException) {
            assertEquals(timeout, error)
        }

        coVerify(exactly = 0) { api.respondToQuestionV2(any(), any(), any()) }
    }

    @Test
    fun `update current model preserves config and scopes request to workspace directory`() = runTest {
        val api = mockk<OpenCodeApi>()
        val existing = ConfigDto(
            theme = "opencode",
            model = "openai/gpt-4",
            username = "user",
            enabledProviders = listOf("openai", "anthropic"),
            instructions = listOf("CONTRIBUTING.md"),
        )
        val updated = existing.copy(model = "anthropic/claude-3")
        coEvery { api.getConfig("/repo", null) } returns existing
        coEvery { api.updateConfig(updated, "/repo", null) } returns updated
        val server = ServerRef.fromEndpointKey("http://test.local")
        val client = WorkspaceClient(
            workspace = Workspace(server, directory = "/repo"),
            generation = ServerGeneration(1L),
            apiProvider = ActiveServerApiProvider { _, _ -> api },
            connectionState = MutableStateFlow(ConnectionState.Connected),
        )

        assertEquals(updated, client.updateCurrentModel("anthropic/claude-3"))

        coVerify(exactly = 1) { api.getConfig("/repo", null) }
        coVerify(exactly = 1) { api.updateConfig(updated, "/repo", null) }
    }

    @Test
    fun `clients with identical session ids keep distinct api and connection authority`() = runTest {
        val firstApi = mockk<OpenCodeApi>()
        val secondApi = mockk<OpenCodeApi>()
        val providers = ProvidersResponseDto(emptyList(), emptyMap(), emptyList())
        coEvery { firstApi.getProviders(any(), null) } returns providers
        coEvery { secondApi.getProviders(any(), null) } returns providers
        val firstState = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
        val secondState = MutableStateFlow<ConnectionState>(ConnectionState.Error("second unavailable"))
        val firstServer = ServerRef.fromEndpointKey("http://first.test")
        val secondServer = ServerRef.fromEndpointKey("http://second.test")
        val provider = ActiveServerApiProvider { server, _ ->
            when (server) {
                firstServer -> firstApi
                secondServer -> secondApi
                else -> error("Unexpected server ${server.endpointKey}")
            }
        }
        val firstClient = WorkspaceClient(
            Workspace(firstServer, directory = "/same-session"),
            ServerGeneration(1L),
            provider,
            firstState,
        )
        val secondClient = WorkspaceClient(
            Workspace(secondServer, directory = "/same-session"),
            ServerGeneration(1L),
            provider,
            secondState,
        )

        firstClient.getProviders()
        secondClient.getProviders()

        coVerify(exactly = 1) { firstApi.getProviders(any(), null) }
        coVerify(exactly = 1) { secondApi.getProviders(any(), null) }
        assertEquals(firstState.value, firstClient.connectionState.value)
        assertEquals(secondState.value, secondClient.connectionState.value)
    }

    @Test
    fun `resolves api through provider on every call`() = runTest {
        val api = mockk<OpenCodeApi>()
        coEvery { api.listProjects(null, null) } returns emptyList()
        var activeGeneration = ServerGeneration(1L)
        var providerCalls = 0
        val workspace = Workspace(
            server = ServerRef.fromEndpointKey("http://test.local"),
            directory = "/repo",
        )
        val client = WorkspaceClient(
            workspace = workspace,
            generation = ServerGeneration(1L),
            apiProvider = ActiveServerApiProvider { serverRef, generation ->
                providerCalls++
                if (serverRef != workspace.server || generation != activeGeneration) {
                    throw StaleWorkspaceClientException("Workspace generation ${generation.value} is stale")
                }
                api
            },
            connectionState = kotlinx.coroutines.flow.MutableStateFlow(
                dev.blazelight.p4oc.core.network.ConnectionState.Disconnected
            ),
        )

        assertEquals(emptyList<Any>(), client.listProjects())
        activeGeneration = ServerGeneration(2L)

        try {
            client.listProjects()
            fail("Expected stale workspace client failure")
        } catch (_: StaleWorkspaceClientException) {
            // Expected.
        }
        assertEquals(2, providerCalls)
        coVerify(exactly = 1) { api.listProjects(null, null) }
    }

    private fun questionClient(api: OpenCodeApi): WorkspaceClient {
        val server = ServerRef.fromEndpointKey("http://test.local")
        return WorkspaceClient(
            workspace = Workspace(server, directory = "/repo"),
            generation = ServerGeneration(1L),
            apiProvider = ActiveServerApiProvider { _, _ -> api },
            connectionState = MutableStateFlow(ConnectionState.Connected),
        )
    }

    private suspend fun assertHttpError(code: Int, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected HTTP $code")
        } catch (error: HttpException) {
            assertEquals(code, error.code())
        }
    }

    private fun questionRequest(sessionId: String) = QuestionRequestDto(
        id = if (sessionId == "ses_1") "que_1" else "que_other",
        sessionID = sessionId,
        questions = listOf(
            QuestionDto(
                header = "Confirm",
                question = "Continue?",
                options = listOf(QuestionOptionDto("Yes", "Continue")),
            )
        ),
    )
}
