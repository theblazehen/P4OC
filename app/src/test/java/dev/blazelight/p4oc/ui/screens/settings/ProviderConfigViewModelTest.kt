package dev.blazelight.p4oc.ui.screens.settings

import dev.blazelight.p4oc.data.remote.dto.ConfigDto
import dev.blazelight.p4oc.data.remote.dto.ModelInput
import dev.blazelight.p4oc.data.remote.dto.OAuthCallbackRequest
import dev.blazelight.p4oc.data.remote.dto.ProviderAuthAuthorizationDto
import dev.blazelight.p4oc.data.remote.dto.ProviderAuthAuthorizeRequest
import dev.blazelight.p4oc.data.remote.dto.ProviderDto
import dev.blazelight.p4oc.data.remote.dto.ProvidersResponseDto
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.workspace.Workspace
import dev.blazelight.p4oc.ui.screens.chat.ModelSelectionCoordinator
import dev.blazelight.p4oc.ui.screens.chat.ScopedModelSelectionChange
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderConfigViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val workspaceClient: WorkspaceClient = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { workspaceClient.workspace } returns
            Workspace(ServerRef.fromEndpointKey("http://test.local"), "/test")
        every { workspaceClient.generation } returns ServerGeneration(2L)
        every { workspaceClient.supportsGlobalModelConfig } returns true
        coEvery { workspaceClient.getProviderAuthMethods() } returns emptyMap()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setModel_updatesCurrentModelOnlyAfterUpdateConfigSucceeds() = runTest(dispatcher) {
        coEvery { workspaceClient.getProviders() } returns providersResponse()
        coEvery { workspaceClient.getConfig() } returns ConfigDto(model = "openai/gpt-4")
        coEvery { workspaceClient.updateConfig(ConfigDto(model = "anthropic/claude-3")) } returns
            ConfigDto(model = "anthropic/claude-3")
        val viewModel = ProviderConfigViewModel(workspaceClient)
        advanceUntilIdle()

        viewModel.setModel("anthropic", "claude-3")
        assertEquals("openai/gpt-4", viewModel.uiState.value.currentModel)
        advanceUntilIdle()

        assertEquals("anthropic/claude-3", viewModel.uiState.value.currentModel)
        assertNull(viewModel.uiState.value.error)
        coVerify { workspaceClient.updateConfig(ConfigDto(model = "anthropic/claude-3")) }
    }

    @Test
    fun setModel_publishesCoordinatorChangeOnlyAfterUpdateConfigSucceeds() = runTest(dispatcher) {
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
        coEvery { workspaceClient.getConfig() } returns ConfigDto(model = "openai/gpt-4")
        coEvery { workspaceClient.updateConfig(ConfigDto(model = "anthropic/claude-3")) } throws
            IllegalStateException("config write failed")
        val viewModel = ProviderConfigViewModel(workspaceClient, coordinator)
        advanceUntilIdle()

        viewModel.setModel("anthropic", "claude-3")
        runCurrent()
        assertEquals(emptyList<ScopedModelSelectionChange>(), publishedModels)

        coEvery { workspaceClient.updateConfig(ConfigDto(model = "anthropic/claude-3")) } returns
            ConfigDto(model = "anthropic/claude-3")
        viewModel.setModel("anthropic", "claude-3")
        runCurrent()

        assertEquals(
            listOf(
                ScopedModelSelectionChange(
                    workspace = workspace,
                    generation = generation,
                    model = ModelInput(providerID = "anthropic", modelID = "claude-3"),
                )
            ),
            publishedModels,
        )
        collectJob.cancel()
    }

    @Test
    fun setModel_updateConfigExceptionPreservesPreviousModelAndShowsMessage() = runTest(dispatcher) {
        coEvery { workspaceClient.getProviders() } returns providersResponse()
        coEvery { workspaceClient.getConfig() } returns ConfigDto(model = "openai/gpt-4")
        coEvery { workspaceClient.updateConfig(ConfigDto(model = "anthropic/claude-3")) } throws
            IllegalStateException("config write failed")
        val viewModel = ProviderConfigViewModel(workspaceClient)
        advanceUntilIdle()

        viewModel.setModel("anthropic", "claude-3")
        advanceUntilIdle()

        assertEquals("openai/gpt-4", viewModel.uiState.value.currentModel)
        assertEquals("Could not set the model. Try again.", viewModel.uiState.value.error)
    }

    @Test
    fun startAndCompleteOAuth_usesSelectedMethodAndRefreshesProviders() = runTest(dispatcher) {
        coEvery { workspaceClient.getProviders() } returns providersResponse()
        coEvery { workspaceClient.getConfig() } returns ConfigDto(model = "openai/gpt-4")
        coEvery {
            workspaceClient.authorizeProvider("anthropic", ProviderAuthAuthorizeRequest(method = 2))
        } returns ProviderAuthAuthorizationDto(
            url = "https://example.test/oauth",
            method = "code",
            instructions = "Paste the authorization code"
        )
        coEvery {
            workspaceClient.completeProviderOAuth("anthropic", OAuthCallbackRequest(method = 2, code = "secret-code"))
        } returns true
        val viewModel = ProviderConfigViewModel(workspaceClient)
        advanceUntilIdle()

        viewModel.startOAuth("anthropic", 2)
        advanceUntilIdle()
        assertEquals("anthropic", viewModel.uiState.value.pendingAuthorization?.providerId)

        viewModel.completeOAuth("  secret-code  ")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingAuthorization)
        assertNull(viewModel.uiState.value.error)
        coVerify {
            workspaceClient.completeProviderOAuth("anthropic", OAuthCallbackRequest(method = 2, code = "secret-code"))
        }
        coVerify(exactly = 2) { workspaceClient.getProviders() }
    }

    @Test
    fun startOAuth_failureDoesNotExposeBackendError() = runTest(dispatcher) {
        coEvery { workspaceClient.getProviders() } returns providersResponse()
        coEvery { workspaceClient.getConfig() } returns ConfigDto(model = "openai/gpt-4")
        coEvery {
            workspaceClient.authorizeProvider("anthropic", ProviderAuthAuthorizeRequest(method = 0))
        } throws IllegalStateException("raw backend secret")
        val viewModel = ProviderConfigViewModel(workspaceClient)
        advanceUntilIdle()

        viewModel.startOAuth("anthropic", 0)
        advanceUntilIdle()

        assertEquals(
            "Could not start provider authentication. Try again.",
            viewModel.uiState.value.error
        )
        assertNull(viewModel.uiState.value.pendingAuthorization)
    }

    @Test
    fun v2CatalogLoadsWithoutLegacyProviderAuthEndpoint() = runTest(dispatcher) {
        every { workspaceClient.supportsGlobalModelConfig } returns false
        coEvery { workspaceClient.getProviders() } returns providersResponse()
        coEvery { workspaceClient.getConfig() } returns ConfigDto(model = "openai/gpt-4")
        coEvery { workspaceClient.getProviderAuthMethods() } throws
            UnsupportedOperationException("No v2 provider auth endpoint")

        val viewModel = ProviderConfigViewModel(workspaceClient)
        advanceUntilIdle()

        assertEquals(listOf("openai", "anthropic"), viewModel.uiState.value.providers.map { it.id })
        assertEquals("openai/gpt-4", viewModel.uiState.value.currentModel)
        assertEquals(false, viewModel.uiState.value.canSetDefaultModel)
        assertNull(viewModel.uiState.value.error)
        coVerify(exactly = 0) { workspaceClient.getProviderAuthMethods() }
    }

    private fun providersResponse() = ProvidersResponseDto(
        all = listOf(
            ProviderDto(
                id = "openai",
                name = "OpenAI",
                source = "env"
            ),
            ProviderDto(
                id = "anthropic",
                name = "Anthropic",
                source = "env"
            )
        ),
        default = mapOf("openai" to "gpt-4"),
        connected = listOf("openai", "anthropic")
    )
}
