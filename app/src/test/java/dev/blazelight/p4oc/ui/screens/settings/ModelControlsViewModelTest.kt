package dev.blazelight.p4oc.ui.screens.settings

import dev.blazelight.p4oc.core.network.ServerConnectionRegistry
import dev.blazelight.p4oc.data.remote.dto.ConfigDto
import dev.blazelight.p4oc.data.remote.dto.ModelDto
import dev.blazelight.p4oc.data.remote.dto.ModelInput
import dev.blazelight.p4oc.data.remote.dto.ProviderDto
import dev.blazelight.p4oc.data.remote.dto.ProvidersResponseDto
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.server.ScopedEvent
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.server.WorkspaceKey
import dev.blazelight.p4oc.domain.workspace.Workspace
import dev.blazelight.p4oc.ui.screens.chat.ModelSelectionCoordinator
import dev.blazelight.p4oc.ui.screens.chat.ScopedModelSelectionChange
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelControlsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val workspaceClient: WorkspaceClient = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { workspaceClient.workspace } returns
            Workspace(ServerRef.fromEndpointKey("http://test.local"), "/test")
        every { workspaceClient.generation } returns ServerGeneration(2L)
        every { workspaceClient.supportsGlobalModelConfig } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectModel_successUpdatesSelectedStateAndClearsPreviousError() = runTest(dispatcher) {
        coEvery { workspaceClient.getProviders() } returns providersResponse()
        coEvery { workspaceClient.updateCurrentModel("anthropic/claude-3") } throws
            IllegalStateException("config write failed")
        val viewModel = ModelControlsViewModel(workspaceClient)
        advanceUntilIdle()
        viewModel.selectModel("claude-3")
        advanceUntilIdle()
        assertEquals("Could not update the model. Try again.", viewModel.state.value.error)

        coEvery { workspaceClient.updateCurrentModel("openai/gpt-4") } returns ConfigDto(model = "openai/gpt-4")
        viewModel.selectModel("gpt-4")
        advanceUntilIdle()

        assertEquals("gpt-4", viewModel.state.value.selectedModelId)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun selectModel_publishesCoordinatorChangeOnlyAfterSuccessfulApiUpdate() = runTest(dispatcher) {
        val coordinator = ModelSelectionCoordinator()
        val workspace = Workspace(ServerRef.fromEndpointKey("http://test.local"), "/test")
        val generation = ServerGeneration(2L)
        every { workspaceClient.workspace } returns workspace
        every { workspaceClient.generation } returns generation
        val publishedModels = mutableListOf<ScopedModelSelectionChange>()
        val collectJob = backgroundScope.launch {
            coordinator.activeModelChanges.collect(publishedModels::add)
        }
        runCurrent()
        coEvery { workspaceClient.getProviders() } returns providersResponse()
        coEvery { workspaceClient.updateCurrentModel("anthropic/claude-3") } throws
            IllegalStateException("config write failed")
        coEvery { workspaceClient.updateCurrentModel("openai/gpt-4") } returns ConfigDto(model = "openai/gpt-4")
        val viewModel = ModelControlsViewModel(workspaceClient, coordinator)
        advanceUntilIdle()

        viewModel.selectModel("claude-3")
        runCurrent()
        assertEquals(emptyList<ScopedModelSelectionChange>(), publishedModels)

        viewModel.selectModel("gpt-4")
        runCurrent()

        assertEquals(
            listOf(
                ScopedModelSelectionChange(
                    workspace = workspace,
                    generation = generation,
                    model = ModelInput(providerID = "openai", modelID = "gpt-4"),
                )
            ),
            publishedModels,
        )
        collectJob.cancel()
    }

    @Test
    fun selectModel_apiErrorPreservesPreviousSelectionAndShowsMessage() = runTest(dispatcher) {
        coEvery { workspaceClient.getProviders() } returns providersResponse()
        coEvery { workspaceClient.updateCurrentModel("openai/gpt-4") } returns ConfigDto(model = "openai/gpt-4")
        coEvery { workspaceClient.updateCurrentModel("anthropic/claude-3") } throws
            IllegalStateException("server rejected model")
        val viewModel = ModelControlsViewModel(workspaceClient)
        advanceUntilIdle()
        viewModel.selectModel("gpt-4")
        advanceUntilIdle()

        viewModel.selectModel("claude-3")
        advanceUntilIdle()

        assertEquals("gpt-4", viewModel.state.value.selectedModelId)
        assertEquals("Could not update the model. Try again.", viewModel.state.value.error)
    }

    @Test
    fun selectModel_configFailurePreservesPreviousSelectionAndShowsMessage() = runTest(dispatcher) {
        coEvery { workspaceClient.getProviders() } returns providersResponse()
        coEvery { workspaceClient.updateCurrentModel("openai/gpt-4") } returns ConfigDto(model = "openai/gpt-4")
        coEvery { workspaceClient.updateCurrentModel("anthropic/claude-3") } throws
            IllegalStateException("config write failed")
        val viewModel = ModelControlsViewModel(workspaceClient)
        advanceUntilIdle()
        viewModel.selectModel("gpt-4")
        advanceUntilIdle()

        viewModel.selectModel("claude-3")
        advanceUntilIdle()

        assertEquals("gpt-4", viewModel.state.value.selectedModelId)
        assertEquals("Could not update the model. Try again.", viewModel.state.value.error)
    }

    @Test
    fun selectModel_staleClientFailurePreservesSelection() = runTest(dispatcher) {
        coEvery { workspaceClient.getProviders() } returns providersResponse()
        coEvery { workspaceClient.updateCurrentModel("openai/gpt-4") } returns ConfigDto(model = "openai/gpt-4")
        coEvery { workspaceClient.updateCurrentModel("anthropic/claude-3") } throws
            IllegalStateException("stale workspace generation")
        val viewModel = ModelControlsViewModel(workspaceClient)
        advanceUntilIdle()
        viewModel.selectModel("gpt-4")
        advanceUntilIdle()
        viewModel.selectModel("claude-3")
        advanceUntilIdle()

        assertEquals("gpt-4", viewModel.state.value.selectedModelId)
        assertEquals("Could not update the model. Try again.", viewModel.state.value.error)
    }

    @Test
    fun selectModel_missingModelDoesNotLeaveOptimisticSelection() = runTest(dispatcher) {
        coEvery { workspaceClient.getProviders() } returns providersResponse()
        coEvery { workspaceClient.updateCurrentModel("openai/gpt-4") } returns ConfigDto(model = "openai/gpt-4")
        val viewModel = ModelControlsViewModel(workspaceClient)
        advanceUntilIdle()
        viewModel.selectModel("gpt-4")
        advanceUntilIdle()

        viewModel.selectModel("missing-model")
        advanceUntilIdle()

        assertEquals("gpt-4", viewModel.state.value.selectedModelId)
        assertEquals("Model not available", viewModel.state.value.error)
        coVerify(exactly = 0) {
            workspaceClient.updateCurrentModel("anthropic/missing-model")
        }
    }

    @Test
    fun loadModels_failureExposesRetryableLoadStateAndRetryClearsIt() = runTest(dispatcher) {
        coEvery { workspaceClient.getProviders() } throws IllegalStateException("offline")
        val viewModel = ModelControlsViewModel(workspaceClient)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals("Could not load models. Try again.", viewModel.state.value.error)

        coEvery { workspaceClient.getProviders() } returns providersResponse()
        viewModel.loadModels()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.loadFailed)
        assertNull(viewModel.state.value.error)
        assertEquals(2, viewModel.state.value.models.size)
    }

    @Test
    fun contentState_distinguishesNoModelsFromFilteredNoResults() {
        assertEquals(ModelListContentState.EMPTY, modelListContentState(ModelControlsState()))

        val populated = ModelControlsState(
            models = listOf(ModelInfo(id = "gpt-4", name = "GPT-4", providerId = "openai")),
            searchQuery = "claude"
        )
        assertEquals(ModelListContentState.NO_RESULTS, modelListContentState(populated))
        assertEquals(
            ModelListContentState.NO_RESULTS,
            modelListContentState(populated.copy(searchQuery = "", filterProvider = "anthropic"))
        )
        assertEquals(ModelListContentState.MODELS, modelListContentState(populated.copy(searchQuery = "gpt")))
    }

    @Test
    fun clearSearchAndFilter_restoresAllModels() = runTest(dispatcher) {
        coEvery { workspaceClient.getProviders() } returns providersResponse()
        val viewModel = ModelControlsViewModel(workspaceClient)
        advanceUntilIdle()
        viewModel.updateSearchQuery("missing")
        viewModel.setFilterProvider("missing-provider")

        viewModel.clearSearchAndFilter()

        assertEquals("", viewModel.state.value.searchQuery)
        assertNull(viewModel.state.value.filterProvider)
        assertEquals(2, filteredModels(viewModel.state.value).size)
    }

    @Test
    fun catalogEvents_refreshOnlyOwnedWorkspaceAndCoalesceBursts() = runTest(dispatcher) {
        val server = ServerRef.fromEndpoint("https://example.test")
        val generation = ServerGeneration(3)
        val workspace = Workspace(server, "/owned")
        val events = MutableSharedFlow<ScopedEvent>()
        val registry = mockk<ServerConnectionRegistry>()
        coEvery { workspaceClient.getProviders() } returns providersResponse()
        every { workspaceClient.workspace } returns workspace
        every { workspaceClient.generation } returns generation
        every { registry.events(server) } returns events
        ModelControlsViewModel(workspaceClient, serverConnectionRegistry = registry)
        advanceUntilIdle()
        coVerify(exactly = 1) { workspaceClient.getProviders() }

        events.emit(ScopedEvent(server, generation, WorkspaceKey.Directory("/other"), OpenCodeEvent.CatalogUpdated))
        events.emit(ScopedEvent(server, generation, workspace.key, OpenCodeEvent.CatalogUpdated))
        events.emit(ScopedEvent(server, generation, workspace.key, OpenCodeEvent.ModelsRefreshed))
        advanceTimeBy(151)
        runCurrent()

        coVerify(exactly = 2) { workspaceClient.getProviders() }
    }

    private fun providersResponse() = ProvidersResponseDto(
        all = listOf(
            ProviderDto(
                id = "openai",
                name = "OpenAI",
                source = "env",
                models = mapOf("gpt-4" to model("gpt-4", "openai"))
            ),
            ProviderDto(
                id = "anthropic",
                name = "Anthropic",
                source = "env",
                models = mapOf("claude-3" to model("claude-3", "anthropic"))
            )
        ),
        default = mapOf("openai" to "gpt-4"),
        connected = listOf("openai", "anthropic")
    )

    private fun model(id: String, providerId: String) = ModelDto(
        id = id,
        providerId = providerId,
        name = "Model $id"
    )
}
