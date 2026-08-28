package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.core.datastore.SavedServerRegistry
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
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
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves [ServerConnectionRegistry.connectionEpoch] returns a stable per-generation flow that
 * bridges to the live event-source epoch once that generation activates, and pins stale or
 * wrong generations to 0.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServerConnectionRegistryEpochTest {
    @Test
    fun `subscription before publication observes later activation and reconnect increments`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.registry.connect(fixture.server)
        runCurrent()

        // Subscribe before the generation's connection is published.
        val observed = fixture.registry.connectionEpoch(fixture.server.toServerRef(), ServerGeneration(1))
        assertEquals(0L, observed.value)

        // The generation activates with epoch 1; the bridged flow must surface it.
        val epochFlow = MutableStateFlow(1L)
        fixture.connection.value = connection(fixture.server.toServerConfig(), 1, epochFlow)
        runCurrent()
        assertEquals(1L, observed.value)

        // A reconnect bumps the source epoch; the bridged flow must follow without any
        // connection-state observation.
        epochFlow.value = 2L
        runCurrent()
        assertEquals(2L, observed.value)
    }

    @Test
    fun `pre-connect epoch subscription is not orphaned when generation later activates`() = runTest {
        // Subscribe before the registry's collectors/manager are initialized (no connect/runCurrent).
        val fixture = fixture(backgroundScope)
        val observed = fixture.registry.connectionEpoch(fixture.server.toServerRef(), ServerGeneration(1))
        assertEquals(0L, observed.value)

        // Now connect and publish gen 1; the previously-requested flow must not be orphaned.
        fixture.registry.connect(fixture.server)
        runCurrent()
        val epochFlow = MutableStateFlow(1L)
        fixture.connection.value = connection(fixture.server.toServerConfig(), 1, epochFlow)
        runCurrent()
        assertEquals(1L, observed.value)

        epochFlow.value = 2L
        runCurrent()
        assertEquals(2L, observed.value)
    }

    @Test
    fun `active generation bridges to the live epoch and observes increases`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.registry.connect(fixture.server)
        runCurrent()
        val epochFlow = MutableStateFlow(1L)
        fixture.connection.value = connection(fixture.server.toServerConfig(), 1, epochFlow)
        runCurrent()

        val observed = fixture.registry.connectionEpoch(fixture.server.toServerRef(), ServerGeneration(1))

        // The registry returns a stable per-generation flow, not the live source directly.
        assertNotSame(epochFlow, observed)
        assertEquals(1L, observed.value)

        // Advance the epoch without touching connection state: the bridged flow must surface it.
        epochFlow.value = 2L
        runCurrent()
        assertEquals(2L, observed.value)
    }

    @Test
    fun `stale generation cannot observe the live epoch and stays at 0`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.registry.connect(fixture.server)
        runCurrent()
        val epochFlow = MutableStateFlow(3L)
        fixture.connection.value = connection(fixture.server.toServerConfig(), 2, epochFlow)
        runCurrent()

        // Query an old generation whose source was torn down.
        val stale = fixture.registry.connectionEpoch(fixture.server.toServerRef(), ServerGeneration(1))

        assertEquals(0L, stale.value)
        // Advancing the live epoch must not reach the stale flow.
        epochFlow.value = 4L
        runCurrent()
        assertEquals(0L, stale.value)
    }

    @Test
    fun `held generation resets to zero and stops following source when it becomes non-active`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.registry.connect(fixture.server)
        runCurrent()
        val firstEpoch = MutableStateFlow(1L)
        fixture.connection.value = connection(fixture.server.toServerConfig(), 1, firstEpoch)
        runCurrent()

        // Hold gen-1 while it is active; it tracks the live epoch.
        val held = fixture.registry.connectionEpoch(fixture.server.toServerRef(), ServerGeneration(1))
        assertEquals(1L, held.value)
        firstEpoch.value = 2L
        runCurrent()
        assertEquals(2L, held.value)

        // The connection rotates to gen 2; the held gen-1 flow must reset to 0 and stop following.
        val secondEpoch = MutableStateFlow(7L)
        fixture.connection.value = connection(fixture.server.toServerConfig(), 2, secondEpoch)
        runCurrent()
        assertEquals(0L, held.value)

        // Advancing the old source must not leak into the reset held flow.
        firstEpoch.value = 3L
        runCurrent()
        assertEquals(0L, held.value)
    }

    @Test
    fun `repeated rotations keep only the active generation epoch entry and bridge`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.registry.connect(fixture.server)
        runCurrent()

        // Hold the flow for each generation across rotations.
        repeat(5) { index ->
            val generationValue = (index + 1).toLong()
            fixture.connection.value = connection(
                fixture.server.toServerConfig(),
                generationValue,
                MutableStateFlow(generationValue),
            )
            runCurrent()
            fixture.registry.connectionEpoch(fixture.server.toServerRef(), ServerGeneration(generationValue))
            assertEquals(1, fixture.registry.generationEpochCount)
            assertEquals(1, fixture.registry.generationEpochBridgeCount)
        }
    }

    @Test
    fun `wrong server cannot observe the live epoch`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.registry.connect(fixture.server)
        runCurrent()
        fixture.connection.value = connection(fixture.server.toServerConfig(), 1, MutableStateFlow(5L))
        runCurrent()

        val other = fixture.registry.connectionEpoch(
            ServerRef.fromEndpoint("http://other.example.com"),
            ServerGeneration(1),
        )

        assertEquals(0L, other.value)
    }

    @Test
    fun `no active connection returns stable zero epoch`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.registry.connect(fixture.server)
        runCurrent()

        val observed = fixture.registry.connectionEpoch(fixture.server.toServerRef(), ServerGeneration(1))

        assertEquals(0L, observed.value)
    }

    @Test
    fun `disconnect resets held epoch flow to zero and cleans up`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.registry.connect(fixture.server)
        runCurrent()
        fixture.connection.value = connection(fixture.server.toServerConfig(), 1, MutableStateFlow(3L))
        runCurrent()
        val held = fixture.registry.connectionEpoch(fixture.server.toServerRef(), ServerGeneration(1))
        assertEquals(3L, held.value)

        fixture.registry.disconnect(fixture.server.toServerRef())
        runCurrent()

        assertEquals(0L, held.value)
        assertEquals(0, fixture.registry.generationEpochCount)
        assertEquals(0, fixture.registry.generationEpochBridgeCount)
    }

    @Test
    fun `invalidateGeneration resets held active epoch to zero and cleans up`() = runTest {
        val fixture = fixture(backgroundScope)
        every { fixture.manager.disconnect(ServerGeneration(1)) } returns true
        fixture.registry.connect(fixture.server)
        runCurrent()
        fixture.connection.value = connection(fixture.server.toServerConfig(), 1, MutableStateFlow(4L))
        runCurrent()
        val held = fixture.registry.connectionEpoch(fixture.server.toServerRef(), ServerGeneration(1))
        assertEquals(4L, held.value)

        val invalidated = fixture.registry.invalidateGeneration(fixture.server.toServerRef(), ServerGeneration(1))
        runCurrent()

        assertTrue(invalidated)
        assertEquals(0L, held.value)
        assertEquals(0, fixture.registry.generationEpochCount)
        assertEquals(0, fixture.registry.generationEpochBridgeCount)
    }

    private fun fixture(scope: CoroutineScope): Fixture {
        val server = SavedServerRegistry.fromConnection("http://epoch.example.com", "Epoch")
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
            manager = manager,
            registry = ServerConnectionRegistry(settings, { _, _ -> manager }, scope),
        )
    }

    private fun connection(
        config: ServerConfig,
        generation: Long,
        epoch: MutableStateFlow<Long>,
    ): Connection {
        val eventSource = mockk<OpenCodeEventSource> {
            every { directoryEvents } returns MutableSharedFlow()
            every { connectionEpoch } returns epoch
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
        val manager: ConnectionManager,
        val registry: ServerConnectionRegistry,
    )
}
