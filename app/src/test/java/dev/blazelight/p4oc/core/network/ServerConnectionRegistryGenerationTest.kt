package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.core.datastore.SavedServerRegistry
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.domain.server.ServerGeneration
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerConnectionRegistryGenerationTest {
    @Test
    fun `generation remains unique across reconnect manager eviction and other servers`() = runTest {
        val server = SavedServerRegistry.fromConnection("http://generation.example.com", "Generation")
        val otherServer = SavedServerRegistry.fromConnection("http://other-generation.example.com", "Other")
        val settings = settings()
        val managers = mutableListOf<ConnectionManager>()
        val api = mockk<OpenCodeApi>()
        val registry = generationRegistry(settings, api, managers, backgroundScope)
        val serverRef = server.toServerRef()

        assertTrue(registry.connectAndAwait(server).isSuccess)
        val firstGeneration = requireNotNull(registry.generation(serverRef))
        assertSame(api, registry.api(serverRef, firstGeneration))

        assertTrue(registry.connectAndAwait(server).isSuccess)
        val liveReconnectGeneration = requireNotNull(registry.generation(serverRef))
        assertNotEquals(firstGeneration, liveReconnectGeneration)
        assertTrue(liveReconnectGeneration.value > firstGeneration.value)
        assertNull(registry.api(serverRef, firstGeneration))

        registry.disconnect(serverRef)
        assertNull(registry.api(serverRef, liveReconnectGeneration))
        assertEquals(
            ConnectionState.Error("Server connection generation is no longer available"),
            registry.connectionState(serverRef, liveReconnectGeneration).value,
        )

        assertTrue(registry.connectAndAwait(server).isSuccess)
        val replacementGeneration = requireNotNull(registry.generation(serverRef))
        assertNotEquals(liveReconnectGeneration, replacementGeneration)
        assertTrue(replacementGeneration.value > liveReconnectGeneration.value)
        assertNull(registry.api(serverRef, firstGeneration))
        assertNull(registry.api(serverRef, liveReconnectGeneration))
        assertSame(api, registry.api(serverRef, replacementGeneration))

        assertTrue(registry.connectAndAwait(otherServer).isSuccess)
        val otherGeneration = requireNotNull(registry.generation(otherServer.toServerRef()))
        assertNotEquals(replacementGeneration, otherGeneration)
        assertTrue(otherGeneration.value > replacementGeneration.value)
        assertEquals(3, managers.size)
        verify(exactly = 1) { managers.first().disconnect() }
    }

    @Test
    fun `generation sequence remains monotonic across registry instances`() = runTest {
        val server = SavedServerRegistry.fromConnection("http://registry-replacement.example.com", "Replacement")
        val settings = settings()
        val api = mockk<OpenCodeApi>()
        val firstRegistry = generationRegistry(settings, api, mutableListOf(), backgroundScope)

        assertTrue(firstRegistry.connectAndAwait(server).isSuccess)
        val firstGeneration = requireNotNull(firstRegistry.generation(server.toServerRef()))
        firstRegistry.disconnect(server.toServerRef())

        val replacementRegistry = generationRegistry(settings, api, mutableListOf(), backgroundScope)
        assertTrue(replacementRegistry.connectAndAwait(server).isSuccess)
        val replacementGeneration = requireNotNull(replacementRegistry.generation(server.toServerRef()))

        assertTrue(replacementGeneration.value > firstGeneration.value)
        assertNull(replacementRegistry.api(server.toServerRef(), firstGeneration))
        assertSame(api, replacementRegistry.api(server.toServerRef(), replacementGeneration))
    }

    @Test
    fun `exact api lookup ignores split generation and current api accessors`() = runTest {
        val server = SavedServerRegistry.fromConnection("http://api-race.example.com", "API Race")
        val oldGeneration = ServerGeneration(7L)
        val replacementGeneration = ServerGeneration(8L)
        val replacementApi = mockk<OpenCodeApi>()
        val connection = connection(server.toServerConfig(), replacementGeneration, replacementApi)
        val manager = connectedManager(server.toServerConfig(), MutableStateFlow(connection))
        every { manager.currentGeneration } returns oldGeneration
        every { manager.getApi() } returns replacementApi
        val registry = registry(settings(), { manager }, backgroundScope)
        registry.connect(server)
        runCurrent()

        assertNull(registry.api(server.toServerRef(), oldGeneration))
        assertSame(replacementApi, registry.api(server.toServerRef(), replacementGeneration))
        verify(exactly = 0) { manager.getApi() }
        verify(exactly = 0) { manager.currentGeneration }
    }

    @Test
    fun `same manager replacement rejects old generation and serves only replacement`() = runTest {
        val server = SavedServerRegistry.fromConnection("http://same-manager.example.com", "Same Manager")
        val oldGeneration = ServerGeneration(7L)
        val replacementGeneration = ServerGeneration(8L)
        val oldApi = mockk<OpenCodeApi>()
        val replacementApi = mockk<OpenCodeApi>()
        val managerConnection = MutableStateFlow<Connection?>(
            connection(server.toServerConfig(), oldGeneration, oldApi),
        )
        val manager = connectedManager(server.toServerConfig(), managerConnection)
        val registry = registry(settings(), { manager }, backgroundScope)
        registry.connect(server)
        runCurrent()
        managerConnection.value = connection(
            server.toServerConfig(),
            replacementGeneration,
            replacementApi,
        )

        assertNull(registry.api(server.toServerRef(), oldGeneration))
        assertSame(replacementApi, registry.api(server.toServerRef(), replacementGeneration))
    }

    private fun generationRegistry(
        settings: SettingsDataStore,
        api: OpenCodeApi,
        managers: MutableList<ConnectionManager>,
        scope: CoroutineScope,
    ): ServerConnectionRegistry = ServerConnectionRegistry(
        settingsDataStore = settings,
        connectionManagerFactory = { config, issueGeneration ->
            generationIssuingManager(config, issueGeneration, api, managers)
        },
        scope = scope,
    )

    private fun generationIssuingManager(
        config: ServerConfig,
        issueGeneration: () -> ServerGeneration,
        api: OpenCodeApi,
        managers: MutableList<ConnectionManager>,
    ): ConnectionManager {
        val managerConnection = MutableStateFlow<Connection?>(null)
        val managerState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        val manager = mockk<ConnectionManager>(relaxed = true)
        every { manager.connection } returns managerConnection
        every { manager.connectionState } returns managerState
        every { manager.currentGeneration } answers { managerConnection.value?.generation }
        coEvery { manager.connect(config, any()) } coAnswers {
            managerConnection.value = connection(config, issueGeneration(), api)
            managerState.value = ConnectionState.Connected
            Result.success(emptyList())
        }
        managers += manager
        return manager
    }

    private fun connectedManager(
        config: ServerConfig,
        connection: MutableStateFlow<Connection?>,
    ): ConnectionManager = mockk(relaxed = true) {
        every { this@mockk.connection } returns connection
        every { connectionState } returns MutableStateFlow(ConnectionState.Connected)
        every { currentGeneration } answers { connection.value?.generation }
        coEvery { connect(config, any()) } returns Result.success(emptyList())
    }

    private fun connection(
        config: ServerConfig,
        generation: ServerGeneration,
        api: OpenCodeApi,
    ): Connection = Connection(
        config = config,
        generation = generation,
        api = api,
        eventSource = mockk {
            every { directoryEvents } returns MutableSharedFlow()
        },
    )

    private fun registry(
        settings: SettingsDataStore,
        factory: (ServerConfig) -> ConnectionManager,
        scope: CoroutineScope,
    ): ServerConnectionRegistry = ServerConnectionRegistry(
        settingsDataStore = settings,
        connectionManagerFactory = { config, _ -> factory(config) },
        scope = scope,
    )

    private fun settings(): SettingsDataStore = mockk {
        coEvery { getSavedServerPassword(any()) } returns null
    }
}
