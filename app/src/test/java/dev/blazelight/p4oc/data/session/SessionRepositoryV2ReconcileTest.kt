package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.OpenCodeApi
import dev.blazelight.p4oc.core.network.V2WorkspaceOpenCodeApi
import dev.blazelight.p4oc.core.network.v2AssistantPartId
import dev.blazelight.p4oc.data.remote.dto.MessageInfoDto
import dev.blazelight.p4oc.data.remote.dto.MessageTimeDto
import dev.blazelight.p4oc.data.remote.dto.MessageWrapperDto
import dev.blazelight.p4oc.data.remote.dto.PartDto
import dev.blazelight.p4oc.data.remote.dto.PartTimeDto
import dev.blazelight.p4oc.data.remote.dto.ToolStateDto
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.data.server.ActiveServerApiProvider
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.SessionStatus
import dev.blazelight.p4oc.domain.model.ToolState
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.domain.workspace.Workspace
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v2 text streams only over SSE; REST reconciles must never truncate it, but still own tool state. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryV2ReconcileTest {
    private val sessionId = SessionId("s1")
    private val textPartId = v2AssistantPartId("m1", "text", 0)

    @Test
    fun `stale REST snapshot does not truncate streamed v2 text but imports REST tool state`() = runTest {
        val (repo, api) = repository(testScheduler, mockk<V2WorkspaceOpenCodeApi>(relaxed = true))
        val lease = repo.acquireSession(sessionId)
        stream("started", "", "delta", "Hello", "delta", " world", "ended", "Hello world").forEach(repo::acceptEvent)
        coEvery { api.getMessages("s1", 100, null, "/test", null) } returns listOf(
            wrapper("m1", textDto("Hello"), toolDto("completed")),
        )

        repo.acceptEvent(OpenCodeEvent.MessageRefreshRequested("s1"))
        advanceUntilIdle()

        val message = repo.messages(sessionId).value.single()
        assertEquals("Hello world", message.text().text)
        assertTrue(message.tool().state is ToolState.Completed)
        lease.close()
    }

    @Test
    fun `mid-stream reconcile keeps the unprojected text part so later deltas extend it`() = runTest {
        val (repo, api) = repository(testScheduler, mockk<V2WorkspaceOpenCodeApi>(relaxed = true))
        val lease = repo.acquireSession(sessionId)
        stream("started", "", "delta", "Hel").forEach(repo::acceptEvent)
        coEvery { api.getMessages("s1", 100, null, "/test", null) } returns listOf(
            wrapper("m1", toolDto("running")),
        )

        repo.acceptEvent(OpenCodeEvent.MessageRefreshRequested("s1"))
        advanceUntilIdle()
        stream("delta", "lo").forEach(repo::acceptEvent)

        val message = repo.messages(sessionId).value.single()
        assertEquals("Hello", message.text().text)
        assertTrue(message.text().isStreaming)
        assertTrue(message.tool().state is ToolState.Running)
        lease.close()
    }

    @Test
    fun `reconcile racing live deltas still takes REST tool state while keeping streamed text`() = runTest {
        val (repo, api) = repository(testScheduler, mockk<V2WorkspaceOpenCodeApi>(relaxed = true))
        val lease = repo.acquireSession(sessionId)
        coEvery { api.getMessages("s1", 100, null, "/test", null) } returns listOf(
            wrapper("m1", textDto(""), toolDto("running")),
        )
        repo.acceptEvent(OpenCodeEvent.MessageRefreshRequested("s1"))
        advanceUntilIdle()
        stream("started", "", "delta", "Hello").forEach(repo::acceptEvent)

        // Every fetch is raced by a new delta, forcing the non-destructive merge fallback.
        var racedFetches = 0
        coEvery { api.getMessages("s1", 100, null, "/test", null) } coAnswers {
            racedFetches += 1
            stream("delta", "!").forEach(repo::acceptEvent)
            listOf(wrapper("m1", textDto("Hello"), toolDto("completed")))
        }
        repo.acceptEvent(OpenCodeEvent.MessageRefreshRequested("s1"))
        advanceUntilIdle()

        val message = repo.messages(sessionId).value.single()
        assertTrue(racedFetches > 1)
        assertEquals("Hello" + "!".repeat(racedFetches), message.text().text)
        assertTrue(message.tool().state is ToolState.Completed)
        lease.close()
    }

    @Test
    fun `session idle triggers one reconcile that fills in final text missed by the stream`() = runTest {
        val (repo, api) = repository(testScheduler, mockk<V2WorkspaceOpenCodeApi>(relaxed = true))
        val lease = repo.acquireSession(sessionId)
        // The "ended" event was missed; only a partial delta reached the client.
        stream("started", "", "delta", "Hel").forEach(repo::acceptEvent)
        coEvery { api.getMessages("s1", 100, null, "/test", null) } returns listOf(
            wrapper("m1", textDto("Hello world"), toolDto("completed")),
        )

        // v2 reports settling twice (status idle, then session.idle); both share one trailing reconcile.
        repo.acceptEvent(OpenCodeEvent.SessionStatusChanged("s1", SessionStatus.Idle))
        repo.acceptEvent(OpenCodeEvent.SessionIdle("s1"))
        advanceUntilIdle()

        coVerify(exactly = 1) { api.getMessages("s1", 100, null, "/test", null) }
        val message = repo.messages(sessionId).value.single()
        assertEquals("Hello world", message.text().text)
        assertFalse(message.text().isStreaming)
        assertTrue(message.tool().state is ToolState.Completed)
        lease.close()
    }

    @Test
    fun `session idle does not reconcile a session without an active lease`() = runTest {
        val (repo, api) = repository(testScheduler, mockk<V2WorkspaceOpenCodeApi>(relaxed = true))
        stream("started", "", "delta", "Hel").forEach(repo::acceptEvent)

        repo.acceptEvent(OpenCodeEvent.SessionIdle("s1"))
        advanceUntilIdle()

        coVerify(exactly = 0) { api.getMessages(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `v1 session idle keeps relying on authoritative SSE and does not reconcile`() = runTest {
        val (repo, api) = repository(testScheduler, mockk<OpenCodeApi>(relaxed = true))
        val lease = repo.acquireSession(sessionId)

        repo.acceptEvent(OpenCodeEvent.SessionIdle("s1"))
        advanceUntilIdle()

        coVerify(exactly = 0) { api.getMessages(any(), any(), any(), any(), any()) }
        lease.close()
    }

    private fun stream(vararg phaseAndText: String): List<OpenCodeEvent> =
        phaseAndText.toList().chunked(2).map { (phase, text) ->
            OpenCodeEvent.V2ContentChanged(
                sessionID = "s1",
                messageID = "m1",
                partID = textPartId,
                kind = "text",
                text = text,
                phase = phase,
            )
        }

    private fun MessageWithParts.text(): Part.Text = parts.filterIsInstance<Part.Text>().single()

    private fun MessageWithParts.tool(): Part.Tool = parts.filterIsInstance<Part.Tool>().single()

    private fun <T : OpenCodeApi> repository(
        scheduler: TestCoroutineScheduler,
        api: T,
    ): Pair<SessionRepositoryImpl, T> {
        val client = WorkspaceClient(
            workspace = Workspace(server = ServerRef.fromEndpointKey("http://test.local"), directory = "/test"),
            generation = ServerGeneration(0L),
            apiProvider = ActiveServerApiProvider { _, _ -> api },
            connectionState = MutableStateFlow(ConnectionState.Disconnected),
        )
        val repo = SessionRepositoryImpl(
            client,
            messageMapper = MessageMapper(),
            dispatcher = StandardTestDispatcher(scheduler),
        )
        return repo to api
    }

    private fun wrapper(id: String, vararg parts: PartDto): MessageWrapperDto = MessageWrapperDto(
        info = MessageInfoDto(
            id = id,
            sessionID = "s1",
            time = MessageTimeDto(created = 1L),
            role = "assistant",
            parentID = "",
            providerID = "provider",
            modelID = "model",
            agent = "assistant",
            mode = "chat",
        ),
        parts = parts.toList(),
    )

    private fun textDto(text: String): PartDto = PartDto(
        id = textPartId,
        sessionID = "s1",
        messageID = "m1",
        type = "text",
        text = text,
    )

    private fun toolDto(status: String): PartDto = PartDto(
        id = "tool-1",
        sessionID = "s1",
        messageID = "m1",
        type = "tool",
        callID = "call-1",
        toolName = "bash",
        state = ToolStateDto(
            status = status,
            input = JsonObject(emptyMap()),
            title = "bash",
            output = if (status == "completed") "done" else null,
            time = PartTimeDto(start = 1L, end = if (status == "completed") 2L else null),
        ),
    )
}
