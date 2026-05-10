package dev.blazelight.p4oc.data.workspace

import dev.blazelight.p4oc.core.network.OpenCodeApi
import dev.blazelight.p4oc.data.server.ActiveServerApiProvider
import dev.blazelight.p4oc.data.server.StaleWorkspaceClientException
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.workspace.Workspace
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class WorkspaceClientTest {
    @Test
    fun `resolves api through provider on every call`() = runTest {
        val api = mockk<OpenCodeApi>()
        coEvery { api.listProjects() } returns emptyList()
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
        coVerify(exactly = 1) { api.listProjects() }
    }
}
