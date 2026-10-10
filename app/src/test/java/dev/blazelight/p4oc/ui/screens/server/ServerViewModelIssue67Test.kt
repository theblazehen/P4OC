package dev.blazelight.p4oc.ui.screens.server

import dev.blazelight.p4oc.core.datastore.SavedServer
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.network.DiscoveredServer
import dev.blazelight.p4oc.core.network.DiscoverySource
import dev.blazelight.p4oc.core.network.DiscoveryState
import dev.blazelight.p4oc.core.network.MdnsDiscoveryManager
import dev.blazelight.p4oc.core.network.ServerConnectionRegistry
import dev.blazelight.p4oc.core.security.CredentialStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.net.ssl.SSLHandshakeException

@OptIn(ExperimentalCoroutinesApi::class)
class ServerViewModelIssue67Test {
    private val dispatcher = StandardTestDispatcher()
    private val attempts = mutableListOf<SavedServer>()
    private val registry = mockk<ServerConnectionRegistry> {
        coEvery { connectAndAwait(capture(attempts), any()) } returns
            Result.failure(SSLHandshakeException("certificate not trusted"))
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `tapping a discovered server verifies TLS even when its seed was trusted`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.connectToDiscoveredServer(trustedSeedRediscoveredOverMdns())
        advanceUntilIdle()

        assertEquals(listOf(false), attempts.map(SavedServer::allowInsecure))
        val state = viewModel.uiState.value
        assertFalse(state.allowInsecure)
        assertTrue("TLS failure must reveal the explicit toggle", state.showTlsOptions)
    }

    @Test
    fun `turning TLS checks off after a discovered TLS failure is honored`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.connectToDiscoveredServer(trustedSeedRediscoveredOverMdns())
        advanceUntilIdle()

        viewModel.setAllowInsecure(true)
        viewModel.connectToRemote()
        advanceUntilIdle()

        assertEquals(listOf(false, true), attempts.map(SavedServer::allowInsecure))
    }

    private fun trustedSeedRediscoveredOverMdns() = DiscoveredServer(
        serviceName = "opencode",
        host = "192.168.1.20",
        port = 4096,
        url = "https://192.168.1.20:4096",
        source = DiscoverySource.MDNS,
        allowInsecure = true,
    )

    private fun viewModel() = ServerViewModel(
        settingsDataStore = mockk<SettingsDataStore>(relaxed = true) {
            every { recentServers } returns flowOf(emptyList())
            every { savedServers } returns flowOf(emptyList())
        },
        serverConnectionRegistry = registry,
        credentialStore = mockk<CredentialStore> { every { getServerPassword(any()) } returns null },
        mdnsDiscoveryManager = mockk<MdnsDiscoveryManager> {
            every { discoveredServers } returns MutableStateFlow(emptyList())
            every { discoveryState } returns MutableStateFlow(DiscoveryState.IDLE)
        },
    )
}
