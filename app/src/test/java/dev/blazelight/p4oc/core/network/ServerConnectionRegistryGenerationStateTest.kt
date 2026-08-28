package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.core.datastore.SavedServerRegistry
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.domain.server.ServerGeneration
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerConnectionRegistryGenerationStateTest {
    @Test
    fun `repeated lookup for active generation returns the same state flow`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.registry.connect(fixture.server)
        runCurrent()
        fixture.connection.value = connection(fixture.server.toServerConfig(), 1)
        fixture.state.value = ConnectionState.Connected
        runCurrent()

        val first = fixture.registry.connectionState(fixture.server.toServerRef(), ServerGeneration(1))
        val second = fixture.registry.connectionState(fixture.server.toServerRef(), ServerGeneration(1))

        assertSame(first, second)
        assertEquals(ConnectionState.Connected, first.value)
        assertEquals(1, fixture.registry.generationStateCount)
    }

    @Test
    fun `held generation flow becomes permanently stale when generation changes`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.registry.connect(fixture.server)
        runCurrent()
        fixture.connection.value = connection(fixture.server.toServerConfig(), 1)
        fixture.state.value = ConnectionState.Connected
        runCurrent()
        val oldFlow = fixture.registry.connectionState(fixture.server.toServerRef(), ServerGeneration(1))

        fixture.connection.value = connection(fixture.server.toServerConfig(), 2)
        runCurrent()
        val newFlow = fixture.registry.connectionState(fixture.server.toServerRef(), ServerGeneration(2))
        fixture.state.value = ConnectionState.Connecting
        runCurrent()

        assertEquals(STALE_ERROR, oldFlow.value)
        assertEquals(ConnectionState.Connecting, newFlow.value)
        assertEquals(1, fixture.registry.generationStateCount)
    }

    @Test
    fun `disconnect makes held generation flow stale and evicts registry entry`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.registry.connect(fixture.server)
        runCurrent()
        fixture.connection.value = connection(fixture.server.toServerConfig(), 1)
        fixture.state.value = ConnectionState.Connected
        runCurrent()
        val heldFlow = fixture.registry.connectionState(fixture.server.toServerRef(), ServerGeneration(1))

        fixture.registry.disconnect(fixture.server.toServerRef())

        assertEquals(STALE_ERROR, heldFlow.value)
        assertEquals(0, fixture.registry.generationStateCount)
        assertEquals(ConnectionState.Disconnected, fixture.registry.connectionState(fixture.server.toServerRef()).value)
    }

    @Test
    fun `repeated generations keep only the active generation entry`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.registry.connect(fixture.server)
        runCurrent()

        repeat(50) { index ->
            val generation = ServerGeneration(index.toLong() + 1)
            fixture.connection.value = connection(fixture.server.toServerConfig(), generation.value)
            fixture.state.value = ConnectionState.Connected
            runCurrent()
            fixture.registry.connectionState(fixture.server.toServerRef(), generation)
            assertEquals(1, fixture.registry.generationStateCount)
        }
    }

    private fun fixture(scope: CoroutineScope): Fixture {
        val server = SavedServerRegistry.fromConnection("http://generation-state.example.com", "Generation")
        val managerConnection = MutableStateFlow<Connection?>(null)
        val managerState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        val manager = mockk<ConnectionManager>(relaxed = true) {
            every { connection } returns managerConnection
            every { connectionState } returns managerState
        }
        coEvery { manager.connect(server.toServerConfig(), any()) } returns Result.failure(
            IllegalStateException("unused"),
        )
        val settings = mockk<SettingsDataStore>()
        coEvery { settings.getSavedServerPassword(any()) } returns null
        return Fixture(
            server = server,
            connection = managerConnection,
            state = managerState,
            registry = ServerConnectionRegistry(settings, { _, _ -> manager }, scope),
        )
    }

    private fun connection(config: ServerConfig, generation: Long): Connection {
        val eventSource = mockk<OpenCodeEventSource> {
            every { directoryEvents } returns MutableSharedFlow()
        }
        return mockk {
            every { this@mockk.config } returns config
            every { this@mockk.generation } returns ServerGeneration(generation)
            every { this@mockk.eventSource } returns eventSource
        }
    }

    private data class Fixture(
        val server: dev.blazelight.p4oc.core.datastore.SavedServer,
        val connection: MutableStateFlow<Connection?>,
        val state: MutableStateFlow<ConnectionState>,
        val registry: ServerConnectionRegistry,
    )

    private companion object {
        val STALE_ERROR = ConnectionState.Error("Server connection generation is no longer available")
    }
}
