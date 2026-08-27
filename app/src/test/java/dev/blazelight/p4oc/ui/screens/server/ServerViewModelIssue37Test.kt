@file:Suppress("ImportOrdering")

package dev.blazelight.p4oc.ui.screens.server

import dev.blazelight.p4oc.core.datastore.ConnectionSettings
import dev.blazelight.p4oc.core.datastore.RecentServer
import dev.blazelight.p4oc.core.datastore.SavedServer
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.network.DiscoveryState
import dev.blazelight.p4oc.core.network.MdnsDiscoveryManager
import dev.blazelight.p4oc.core.network.ServerConfig
import dev.blazelight.p4oc.core.network.ServerConnectionRegistry
import dev.blazelight.p4oc.core.security.CredentialStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.match
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
class ServerViewModelIssue37Test {
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
    fun `allowed reconnect respects stored false and still loads server inventory`() = runTest(dispatcher) {
        val recentServers = listOf(
            RecentServer(
                url = "http://recent.local:4096",
                name = "Recent server",
            )
        )
        val savedServers = listOf(savedServer())
        val settingsDataStore = mockk<SettingsDataStore>()
        val connectionRegistry = mockk<ServerConnectionRegistry>()
        val discoveryManager = discoveryManager()
        every { settingsDataStore.recentServers } returns flowOf(recentServers)
        every { settingsDataStore.savedServers } returns flowOf(savedServers)
        every { settingsDataStore.connectionSettings } returns flowOf(
            ConnectionSettings(autoReconnect = false)
        )
        val viewModel = viewModel(settingsDataStore, connectionRegistry, discoveryManager)

        viewModel.start(autoReconnect = true)
        advanceUntilIdle()

        assertEquals(recentServers, viewModel.uiState.value.recentServers)
        assertEquals(savedServers, viewModel.uiState.value.savedServers)
        assertFalse(viewModel.uiState.value.isConnecting)
        assertFalse(viewModel.uiState.value.isConnected)
        assertNull(viewModel.uiState.value.connectingEndpointKey)
        verify(exactly = 1) { settingsDataStore.connectionSettings }
        coVerify(exactly = 0) { settingsDataStore.getLastConnection() }
        coVerify(exactly = 0) { connectionRegistry.connectAndAwait(any(), any()) }
    }

    @Test
    fun `hard-disabled reconnect wins over stored true and start stays one-shot`() = runTest(dispatcher) {
        val settingsDataStore = mockk<SettingsDataStore>()
        val connectionRegistry = mockk<ServerConnectionRegistry>()
        val discoveryManager = discoveryManager()
        every { settingsDataStore.recentServers } returns flowOf(emptyList())
        every { settingsDataStore.savedServers } returns flowOf(listOf(savedServer()))
        every { settingsDataStore.connectionSettings } returns flowOf(
            ConnectionSettings(autoReconnect = true)
        )
        val viewModel = viewModel(settingsDataStore, connectionRegistry, discoveryManager)

        viewModel.start(autoReconnect = false)
        viewModel.start(autoReconnect = true)
        advanceUntilIdle()

        assertEquals(listOf(savedServer()), viewModel.uiState.value.savedServers)
        assertFalse(viewModel.uiState.value.isConnecting)
        assertFalse(viewModel.uiState.value.isConnected)
        verify(exactly = 0) { settingsDataStore.connectionSettings }
        coVerify(exactly = 0) { settingsDataStore.getLastConnection() }
        coVerify(exactly = 0) { connectionRegistry.connectAndAwait(any(), any()) }
    }

    @Test
    fun `allowed reconnect with stored true reconnects the last server`() = runTest(dispatcher) {
        val settingsDataStore = mockk<SettingsDataStore>()
        val connectionRegistry = mockk<ServerConnectionRegistry>()
        val discoveryManager = discoveryManager()
        val config = ServerConfig(
            url = "http://saved.local:4096",
            name = "Saved server",
            username = "opencode",
        )
        every { settingsDataStore.recentServers } returns flowOf(emptyList())
        every { settingsDataStore.savedServers } returns flowOf(listOf(savedServer()))
        every { settingsDataStore.connectionSettings } returns flowOf(
            ConnectionSettings(autoReconnect = true)
        )
        coEvery { settingsDataStore.getLastConnection() } returns (config to "secret")
        coEvery { connectionRegistry.connectAndAwait(any(), any()) } returns Result.success(emptyList())
        val viewModel = viewModel(settingsDataStore, connectionRegistry, discoveryManager)

        viewModel.start(autoReconnect = true)
        advanceUntilIdle()

        verify(exactly = 1) { settingsDataStore.connectionSettings }
        coVerify(exactly = 1) { settingsDataStore.getLastConnection() }
        coVerify(exactly = 1) {
            connectionRegistry.connectAndAwait(
                match {
                    it.endpoint == config.url &&
                        it.displayName == config.name &&
                        it.username == config.username
                },
                "secret",
            )
        }
        assertFalse(viewModel.uiState.value.isConnecting)
        assertTrue(viewModel.uiState.value.isConnected)
        assertEquals("http://saved.local:4096", viewModel.uiState.value.connectedEndpointKey)
        assertEquals(NavigationDestination.Sessions, viewModel.uiState.value.navigationDestination)
    }

    private fun viewModel(
        settingsDataStore: SettingsDataStore,
        connectionRegistry: ServerConnectionRegistry,
        discoveryManager: MdnsDiscoveryManager,
    ) = ServerViewModel(
        settingsDataStore = settingsDataStore,
        serverConnectionRegistry = connectionRegistry,
        credentialStore = mockk<CredentialStore>(),
        mdnsDiscoveryManager = discoveryManager,
    )

    private fun discoveryManager() = mockk<MdnsDiscoveryManager> {
        every { discoveredServers } returns MutableStateFlow(emptyList())
        every { discoveryState } returns MutableStateFlow(DiscoveryState.IDLE)
    }

    private fun savedServer() = SavedServer(
        id = "http://saved.local:4096",
        endpoint = "http://saved.local:4096",
        endpointKey = "http://saved.local:4096",
        displayName = "Saved server",
        username = "opencode",
    )
}
