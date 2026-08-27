package dev.blazelight.p4oc.ui.tabs

import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.OpenCodeApi
import dev.blazelight.p4oc.data.remote.dto.ForkSessionRequest
import dev.blazelight.p4oc.data.server.ActiveServerApiProvider
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.workspace.Workspace
import dev.blazelight.p4oc.fakes.FakeWorkspaceClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingSessionForkStepTest {
    @Test
    fun `enqueue accepts first request and rejects distinct second without overwriting`() {
        val first = PendingSessionFork(
            sourceSessionId = "first-session",
            directory = "/project",
        )
        val second = PendingSessionFork(
            sourceSessionId = "second-session",
            directory = "/other-project",
        )

        val accepted = decidePendingSessionForkEnqueue(current = null, requested = first)
        val rejected = decidePendingSessionForkEnqueue(current = accepted.pending, requested = second)

        assertTrue(accepted.accepted)
        assertSame(first, accepted.pending)
        assertFalse(rejected.accepted)
        assertSame(first, rejected.pending)
    }

    @Test
    fun `same-directory fork executes and is consumed exactly once`() {
        val pending = PendingSessionFork(
            sourceSessionId = "source-session",
            directory = "/project",
        )

        val ready = pendingSessionForkStep(pending, currentDirectory = "/project")
        val consumed = pendingSessionForkStep(ready.remaining, currentDirectory = "/project")

        assertEquals("source-session", ready.sourceSessionIdToFork)
        assertNull(ready.remaining)
        assertNull(consumed.sourceSessionIdToFork)
        assertNull(consumed.remaining)
    }

    @Test
    fun `rejected second fork cannot execute before first cutover and first executes scoped once`() = runTest {
        val first = PendingSessionFork(
            sourceSessionId = "first-session",
            directory = "/project",
        )
        val second = PendingSessionFork(
            sourceSessionId = "second-session",
            directory = "/other-project",
        )
        val accepted = decidePendingSessionForkEnqueue(current = null, requested = first)
        val rejected = decidePendingSessionForkEnqueue(current = accepted.pending, requested = second)
        val request = ForkSessionRequest(messageID = null)
        val api = mockk<OpenCodeApi>()
        coEvery {
            api.forkSession("first-session", request, "/project", null)
        } returns FakeWorkspaceClient.sessionDto(
            id = "forked-session",
            directory = "/project",
            parentID = "first-session",
        )

        val beforeCutover = pendingSessionForkStep(
            pending = rejected.pending,
            currentDirectory = "/other-project",
        )
        assertFalse(rejected.accepted)
        assertNull(beforeCutover.sourceSessionIdToFork)
        assertSame(first, beforeCutover.remaining)
        coVerify(exactly = 0) { api.forkSession(any(), any(), any(), any()) }

        val server = ServerRef.fromEndpointKey("http://test.local")
        val projectClient = WorkspaceClient(
            workspace = Workspace(server, directory = "/project"),
            generation = ServerGeneration(1L),
            apiProvider = ActiveServerApiProvider { _, _ -> api },
            connectionState = MutableStateFlow(ConnectionState.Connected),
        )
        val afterCutover = pendingSessionForkStep(beforeCutover.remaining, currentDirectory = "/project")
        afterCutover.sourceSessionIdToFork?.let { sourceSessionId ->
            projectClient.forkSession(sourceSessionId, request)
        }
        val consumed = pendingSessionForkStep(afterCutover.remaining, currentDirectory = "/project")
        consumed.sourceSessionIdToFork?.let { sourceSessionId ->
            projectClient.forkSession(sourceSessionId, request)
        }

        assertEquals("first-session", afterCutover.sourceSessionIdToFork)
        assertNull(afterCutover.remaining)
        assertNull(consumed.sourceSessionIdToFork)
        assertNull(consumed.remaining)
        coVerify(exactly = 1) {
            api.forkSession("first-session", request, "/project", null)
        }
        coVerify(exactly = 0) {
            api.forkSession("first-session", request, null, null)
        }
        coVerify(exactly = 0) {
            api.forkSession("second-session", any(), any(), any())
        }
    }
}
