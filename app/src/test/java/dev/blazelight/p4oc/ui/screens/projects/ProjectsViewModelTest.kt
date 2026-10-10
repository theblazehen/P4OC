package dev.blazelight.p4oc.ui.screens.projects

import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.OpenCodeApi
import dev.blazelight.p4oc.core.network.ServerConnectionRegistry
import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import dev.blazelight.p4oc.data.remote.dto.ProjectTimeDto
import dev.blazelight.p4oc.data.server.ActiveServerApiProvider
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.server.ScopedEvent
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.server.WorkspaceKey
import dev.blazelight.p4oc.domain.workspace.Workspace
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun twoWorkspaceClients_callOnlyTheirExactServerApis() = runTest(dispatcher) {
        val firstApi = mockk<OpenCodeApi>()
        val secondApi = mockk<OpenCodeApi>()
        val firstProject = project("first", "/first", created = 1)
        val secondProject = project("second", "/second", created = 2)
        coEvery { firstApi.listProjects(null, null) } returns listOf(firstProject)
        coEvery { secondApi.listProjects(null, null) } returns listOf(secondProject)
        coEvery { firstApi.listFiles(".", "/first", null) } returns emptyList()
        coEvery { secondApi.listFiles(".", "/second", null) } returns emptyList()

        val first = ProjectsViewModel(client("https://first.test", 1, firstApi), emptyRegistry())
        val second = ProjectsViewModel(client("https://second.test", 2, secondApi), emptyRegistry())
        advanceUntilIdle()

        assertEquals(listOf(firstProject), first.uiState.value.projects)
        assertEquals(listOf(secondProject), second.uiState.value.projects)
        coVerify(exactly = 1) { firstApi.listProjects(null, null) }
        coVerify(exactly = 1) { firstApi.listFiles(".", "/first", null) }
        coVerify(exactly = 1) { secondApi.listProjects(null, null) }
        coVerify(exactly = 1) { secondApi.listFiles(".", "/second", null) }
        coVerify(exactly = 0) { firstApi.listFiles(".", "/second", null) }
        coVerify(exactly = 0) { secondApi.listFiles(".", "/first", null) }
    }

    @Test
    fun loadProjects_sortsAndFiltersUsingReturnedProjectDirectories() = runTest(dispatcher) {
        val api = mockk<OpenCodeApi>()
        val older = project("older", "/older", created = 1)
        val newer = project("newer", "/newer", created = 2)
        coEvery { api.listProjects(null, null) } returns listOf(older, newer)
        coEvery { api.listFiles(".", "/older", null) } throws IllegalStateException("missing")
        coEvery { api.listFiles(".", "/newer", null) } returns emptyList()

        val viewModel = ProjectsViewModel(client("https://server.test", 1, api), emptyRegistry())
        advanceUntilIdle()

        assertEquals(listOf(newer), viewModel.uiState.value.projects)
        assertEquals(1, viewModel.uiState.value.staleProjectCount)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun projectEvents_refreshOnlyExactOwnerAndCoalesceBurst() = runTest(dispatcher) {
        val api = mockk<OpenCodeApi>()
        val initial = project("initial", "/initial", created = 1)
        val refreshed = project("refreshed", "/refreshed", created = 2)
        coEvery { api.listProjects(null, null) } returnsMany listOf(listOf(initial), listOf(refreshed))
        coEvery { api.listFiles(".", any(), null) } returns emptyList()
        val client = client("https://server.test", 7, api)
        val events = MutableSharedFlow<ScopedEvent>(extraBufferCapacity = 8)
        val registry = mockk<ServerConnectionRegistry>()
        every { registry.events(client.workspace.server) } returns events

        val viewModel = ProjectsViewModel(client, registry)
        advanceUntilIdle()

        events.emit(scoped(client, ServerGeneration(8), OpenCodeEvent.ProjectDirectoriesUpdated("wrong-generation")))
        events.emit(
            ScopedEvent(
                serverRef = ServerRef.fromEndpointKey("https://other.test"),
                generation = client.generation,
                workspaceKey = client.workspace.key,
                event = OpenCodeEvent.ProjectDirectoriesUpdated("wrong-server"),
            )
        )
        events.emit(
            ScopedEvent(
                serverRef = client.workspace.server,
                generation = client.generation,
                workspaceKey = WorkspaceKey.Directory("/other"),
                event = OpenCodeEvent.ProjectDirectoriesUpdated("wrong-workspace"),
            )
        )
        advanceTimeBy(200)
        coVerify(exactly = 1) { api.listProjects(null, null) }

        events.emit(scoped(client, client.generation, OpenCodeEvent.ProjectDirectoriesUpdated("initial")))
        events.emit(scoped(client, client.generation, OpenCodeEvent.ProjectDirectoriesUpdated("initial")))
        advanceTimeBy(151)
        advanceUntilIdle()

        assertEquals(listOf(refreshed), viewModel.uiState.value.projects)
        coVerify(exactly = 2) { api.listProjects(null, null) }
    }

    @Test
    fun v2ProjectRefreshEvent_reloadsProjects() = runTest(dispatcher) {
        val api = mockk<OpenCodeApi>()
        val initial = project("initial", "/initial", created = 1)
        val refreshed = project("refreshed", "/refreshed", created = 2)
        coEvery { api.listProjects(null, null) } returnsMany listOf(listOf(initial), listOf(refreshed))
        coEvery { api.listFiles(".", any(), null) } returns emptyList()
        val client = client("https://server.test", 7, api)
        val events = MutableSharedFlow<ScopedEvent>(extraBufferCapacity = 8)
        val registry = mockk<ServerConnectionRegistry>()
        every { registry.events(client.workspace.server) } returns events

        val viewModel = ProjectsViewModel(client, registry)
        advanceUntilIdle()

        events.emit(scoped(client, client.generation, OpenCodeEvent.ProjectRefreshRequested("refreshed")))
        advanceTimeBy(151)
        advanceUntilIdle()

        assertEquals(listOf(refreshed), viewModel.uiState.value.projects)
    }

    private fun scoped(
        client: WorkspaceClient,
        generation: ServerGeneration,
        event: OpenCodeEvent,
    ) = ScopedEvent(client.workspace.server, generation, client.workspace.key, event)

    private fun emptyRegistry(): ServerConnectionRegistry = mockk {
        every { events(any()) } returns emptyFlow()
    }

    private fun client(endpoint: String, generation: Long, api: OpenCodeApi): WorkspaceClient {
        val server = ServerRef.fromEndpointKey(endpoint)
        return WorkspaceClient(
            workspace = Workspace(server, directory = null),
            generation = ServerGeneration(generation),
            apiProvider = ActiveServerApiProvider { requestedServer, requestedGeneration ->
                check(requestedServer == server)
                check(requestedGeneration == ServerGeneration(generation))
                api
            },
            connectionState = MutableStateFlow(ConnectionState.Disconnected),
        )
    }

    private fun project(id: String, worktree: String, created: Long) = ProjectDto(
        id = id,
        worktree = worktree,
        time = ProjectTimeDto(created = created),
    )
}
