@file:Suppress("ImportOrdering")

package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.core.datastore.SavedServerRegistry
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.server.ScopedEvent
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.server.WorkspaceKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerConnectionRegistryTest {
    @Test
    fun `awaited connection returns success only from registry owned exact server`() = runTest {
        val server = SavedServerRegistry.fromConnection("https://owned.example.com", "Owned")
        val manager = successfulManager(server)
        every { manager.connection } returns MutableStateFlow(
            mockk {
                every { config } returns server.toServerConfig()
                every { generation } returns ServerGeneration(1)
                every { eventSource } returns mockk { every { directoryEvents } returns MutableSharedFlow() }
            },
        )
        val registry = registryFor(backgroundScope) { manager }

        val result = registry.connectAndAwait(server, "password")

        assertTrue(result.isSuccess)
        assertSame(manager.connection.value, registry.connection(server.toServerRef()).value)
        coVerify(exactly = 1) { manager.connect(server.toServerConfig(), "password") }
    }

    @Test
    fun `replaced awaited attempt is cancelled and cannot publish stale success`() = runTest {
        val server = SavedServerRegistry.fromConnection("https://replace.example.com", "Replace")
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val connection = mockk<Connection> {
            every { config } returns server.toServerConfig()
            every { generation } returns ServerGeneration(2)
            every { eventSource } returns mockk { every { directoryEvents } returns MutableSharedFlow() }
        }
        val manager = mockk<ConnectionManager>(relaxed = true)
        every { manager.connection } returns MutableStateFlow(connection)
        every { manager.connectionState } returns MutableStateFlow(ConnectionState.Connected)
        var call = 0
        coEvery { manager.connect(server.toServerConfig(), any()) } coAnswers {
            call += 1
            if (call == 1) {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
            Result.success(emptyList())
        }
        val registry = registryFor(backgroundScope) { manager }

        val first = async { registry.connectAndAwait(server, "first") }
        firstStarted.await()
        val second = async { registry.connectAndAwait(server, "second") }
        runCurrent()
        releaseFirst.complete(Unit)
        runCurrent()

        assertTrue(first.isCancelled)
        assertTrue(second.await().isSuccess)
        coVerify(exactly = 1) { manager.connect(server.toServerConfig(), "first") }
        coVerify(exactly = 1) { manager.connect(server.toServerConfig(), "second") }
    }

    @Test
    fun `terminal transport is isolated by server and rejects stale generation`() = runTest {
        val alpha = SavedServerRegistry.fromConnection("http://alpha-terminal.example.com", "Alpha")
        val beta = SavedServerRegistry.fromConnection("http://beta-terminal.example.com", "Beta")
        val alphaClient = OkHttpClient()
        val betaClient = OkHttpClient()
        val eventSource = mockk<OpenCodeEventSource> {
            every { directoryEvents } returns MutableSharedFlow()
        }
        val alphaConnection = mockk<Connection> {
            every { config } returns alpha.toServerConfig()
            every { generation } returns ServerGeneration(11)
            every { this@mockk.eventSource } returns eventSource
        }
        val betaConnection = mockk<Connection> {
            every { config } returns beta.toServerConfig()
            every { generation } returns ServerGeneration(22)
            every { this@mockk.eventSource } returns eventSource
        }
        val alphaManager = successfulManager(alpha).also {
            every { it.connection } returns MutableStateFlow(alphaConnection)
            every { it.currentGeneration } returns ServerGeneration(11)
            every { it.authOkHttpClient } returns MutableStateFlow(alphaClient)
        }
        val betaManager = successfulManager(beta).also {
            every { it.connection } returns MutableStateFlow(betaConnection)
            every { it.currentGeneration } returns ServerGeneration(22)
            every { it.authOkHttpClient } returns MutableStateFlow(betaClient)
        }
        val registry = registryFor(backgroundScope) { config ->
            when (config.url) {
                alpha.endpoint -> alphaManager
                beta.endpoint -> betaManager
                else -> error("unexpected config $config")
            }
        }

        registry.connect(alpha)
        registry.connect(beta)
        runCurrent()

        val alphaTransport = registry.terminalTransport(alpha.toServerRef(), ServerGeneration(11))
        val betaTransport = registry.terminalTransport(beta.toServerRef(), ServerGeneration(22))
        assertSame(alphaConnection, alphaTransport?.connection)
        assertSame(alphaClient, alphaTransport?.authClient)
        assertSame(betaConnection, betaTransport?.connection)
        assertSame(betaClient, betaTransport?.authClient)
        assertNull(registry.terminalTransport(alpha.toServerRef(), ServerGeneration(22)))
        assertNull(registry.terminalTransport(beta.toServerRef(), ServerGeneration(11)))
    }

    @Test
    fun `two servers expose independent live events exactly once`() = runTest {
        val alpha = SavedServerRegistry.fromConnection("http://alpha-events.example.com", "Alpha")
        val beta = SavedServerRegistry.fromConnection("http://beta-events.example.com", "Beta")
        val alphaEvents = MutableSharedFlow<OpenCodeEventSource.DirectoryEvent>()
        val betaEvents = MutableSharedFlow<OpenCodeEventSource.DirectoryEvent>()
        val alphaManager = successfulManagerWithEvents(alpha, 1, alphaEvents)
        val betaManager = successfulManagerWithEvents(beta, 1, betaEvents)
        val registry = registryFor(backgroundScope) { config ->
            if (config.url == alpha.endpoint) alphaManager else betaManager
        }
        registry.connect(alpha)
        registry.connect(beta)
        runCurrent()
        val alphaReceived = async { registry.events(alpha.toServerRef()).first() }
        val betaReceived = async { registry.events(beta.toServerRef()).first() }
        runCurrent()
        val alphaEvent = scopedEvent(alpha.toServerRef(), 1)
        val betaEvent = scopedEvent(beta.toServerRef(), 1)

        alphaEvents.emit(OpenCodeEventSource.DirectoryEvent(null, alphaEvent.event))
        betaEvents.emit(OpenCodeEventSource.DirectoryEvent(null, betaEvent.event))

        assertEquals(alphaEvent, alphaReceived.await())
        assertEquals(betaEvent, betaReceived.await())
    }

    @Test
    fun `successful probe remains connecting until manager reports connected`() = runTest {
        val server = SavedServerRegistry.fromConnection("http://pending.example.com", "Pending")
        val managerState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        val manager = mockk<ConnectionManager>(relaxed = true)
        every { manager.connection } returns MutableStateFlow(null)
        every { manager.connectionState } returns managerState
        coEvery { manager.connect(server.toServerConfig(), any()) } returns Result.success(emptyList())
        val registry = registryFor(backgroundScope) { manager }

        registry.connect(server)
        runCurrent()

        assertEquals(ConnectionState.Connecting, registry.connectionState(server.toServerRef()).value)

        managerState.value = ConnectionState.Connected
        runCurrent()

        assertEquals(ConnectionState.Connected, registry.connectionState(server.toServerRef()).value)
    }

    @Test
    fun `connection flow obtained before connect follows manager connection`() = runTest {
        val server = SavedServerRegistry.fromConnection("http://flow.example.com", "Flow")
        val managerConnection = MutableStateFlow<Connection?>(null)
        val manager = mockk<ConnectionManager>(relaxed = true)
        every { manager.connection } returns managerConnection
        every { manager.connectionState } returns MutableStateFlow(ConnectionState.Connecting)
        coEvery { manager.connect(server.toServerConfig(), any()) } returns Result.success(emptyList())
        val registry = registryFor(backgroundScope) { manager }
        val connectionFlow = registry.connection(server.toServerRef())
        val connection = mockk<Connection> {
            every { config } returns server.toServerConfig()
            every { generation } returns ServerGeneration(1)
            every { eventSource } returns mockk { every { directoryEvents } returns MutableSharedFlow() }
        }

        registry.connect(server)
        runCurrent()
        managerConnection.value = connection
        runCurrent()

        assertEquals(connection, connectionFlow.value)
    }

    @Test
    fun `local classification uses exact parsed host`() {
        val localhost = SavedServerRegistry.fromConnection("http://localhost:4096", "Local")
        val loopback = SavedServerRegistry.fromConnection("http://127.0.0.1:4096", "Loopback")
        val attacker = SavedServerRegistry.fromConnection("http://localhost.attacker.example", "Attacker")

        assertTrue(localhost.toServerConfig().isLocal)
        assertTrue(loopback.toServerConfig().isLocal)
        assertFalse(attacker.toServerConfig().isLocal)
    }

    @Test
    fun `two saved servers keep independent connection states`() = runTest {
        val alpha = SavedServerRegistry.fromConnection("http://alpha.example.com", "Alpha")
        val beta = SavedServerRegistry.fromConnection("http://beta.example.com", "Beta")
        val alphaManager = successfulManager(alpha)
        val betaManager = successfulManager(beta)
        val registry = registryFor(backgroundScope) { config ->
            when (config.url) {
                alpha.endpoint -> alphaManager
                beta.endpoint -> betaManager
                else -> error("unexpected config $config")
            }
        }

        registry.connect(alpha)
        registry.connect(beta)
        runCurrent()

        assertEquals(ConnectionState.Connected, registry.connectionState(alpha.toServerRef()).value)
        assertEquals(ConnectionState.Connected, registry.connectionState(beta.toServerRef()).value)
        coVerify(exactly = 1) { alphaManager.connect(alpha.toServerConfig(), null) }
        coVerify(exactly = 1) { betaManager.connect(beta.toServerConfig(), null) }
    }

    @Test
    fun `foreground recovery reaches every owned manager exactly once`() = runTest {
        val alpha = SavedServerRegistry.fromConnection("http://alpha.example.com", "Alpha")
        val beta = SavedServerRegistry.fromConnection("http://beta.example.com", "Beta")
        val alphaManager = successfulManager(alpha)
        val betaManager = successfulManager(beta)
        val registry = registryFor(backgroundScope) { config ->
            if (config.url == alpha.endpoint) alphaManager else betaManager
        }
        registry.connect(alpha)
        registry.connect(beta)
        runCurrent()

        registry.onAppForegrounded()

        verify(exactly = 1) { alphaManager.onAppForegrounded() }
        verify(exactly = 1) { betaManager.onAppForegrounded() }
    }

    @Test
    fun `one server failure does not overwrite another server state`() = runTest {
        val alpha = SavedServerRegistry.fromConnection("http://alpha.example.com", "Alpha")
        val beta = SavedServerRegistry.fromConnection("http://beta.example.com", "Beta")
        val alphaManager = successfulManager(alpha)
        val betaManager = failingManager(beta, "auth failed")
        val registry = registryFor(backgroundScope) { config ->
            when (config.url) {
                alpha.endpoint -> alphaManager
                beta.endpoint -> betaManager
                else -> error("unexpected config $config")
            }
        }

        registry.connect(alpha)
        registry.connect(beta)
        runCurrent()

        assertEquals(ConnectionState.Connected, registry.connectionState(alpha.toServerRef()).value)
        assertEquals(ConnectionState.Error("Connection failed"), registry.connectionState(beta.toServerRef()).value)
    }

    @Test
    fun `registry follows manager recovery after connect returns`() = runTest {
        val server = SavedServerRegistry.fromConnection("http://recovering.example.com", "Recovering")
        val managerState = MutableStateFlow<ConnectionState>(ConnectionState.Error("network unavailable"))
        val manager = mockk<ConnectionManager>(relaxed = true)
        every { manager.connection } returns MutableStateFlow(null)
        every { manager.connectionState } returns managerState
        coEvery { manager.connect(server.toServerConfig(), any()) } returns Result.success(emptyList())
        val registry = registryFor(backgroundScope) { manager }

        registry.connect(server)
        runCurrent()
        assertEquals(
            ConnectionState.Error("network unavailable"),
            registry.connectionState(server.toServerRef()).value,
        )

        managerState.value = ConnectionState.Connected
        runCurrent()

        assertEquals(ConnectionState.Connected, registry.connectionState(server.toServerRef()).value)
    }

    @Test
    fun `disconnect only clears the targeted server`() = runTest {
        val alpha = SavedServerRegistry.fromConnection("http://alpha.example.com", "Alpha")
        val beta = SavedServerRegistry.fromConnection("http://beta.example.com", "Beta")
        val alphaManager = successfulManager(alpha)
        val betaManager = successfulManager(beta)
        val registry = registryFor(backgroundScope) { config ->
            when (config.url) {
                alpha.endpoint -> alphaManager
                beta.endpoint -> betaManager
                else -> error("unexpected config $config")
            }
        }
        registry.connect(alpha)
        registry.connect(beta)
        runCurrent()

        registry.disconnect(alpha.toServerRef())

        assertEquals(ConnectionState.Disconnected, registry.connectionState(alpha.toServerRef()).value)
        assertEquals(ConnectionState.Connected, registry.connectionState(beta.toServerRef()).value)
        coVerify(exactly = 1) { alphaManager.disconnect() }
        coVerify(exactly = 0) { betaManager.disconnect() }
    }

    @Test
    fun `global disposed invalidates the exact emitting generation`() = runTest {
        val server = SavedServerRegistry.fromConnection("http://disposed.example.com", "Disposed")
        val events = MutableSharedFlow<OpenCodeEventSource.DirectoryEvent>()
        val manager = successfulManagerWithEvents(server, 7, events)
        every { manager.disconnect(ServerGeneration(7)) } returns true
        val registry = registryFor(backgroundScope) { manager }
        registry.connect(server)
        runCurrent()
        val heldGenerationState = registry.connectionState(server.toServerRef(), ServerGeneration(7))

        events.emit(OpenCodeEventSource.DirectoryEvent(null, OpenCodeEvent.GlobalDisposed))
        runCurrent()

        assertEquals(ConnectionState.Disconnected, registry.connectionState(server.toServerRef()).value)
        assertNull(registry.connection(server.toServerRef()).value)
        assertEquals(
            ConnectionState.Error("Server connection generation is no longer available"),
            heldGenerationState.value,
        )
        verify(exactly = 1) { manager.disconnect(ServerGeneration(7)) }
    }

    @Test
    fun `stale global disposed cannot invalidate replacement generation`() = runTest {
        val server = SavedServerRegistry.fromConnection("http://replacement.example.com", "Replacement")
        val manager = successfulManager(server)
        every { manager.currentGeneration } returns ServerGeneration(8)
        every { manager.disconnect(ServerGeneration(7)) } returns false
        val registry = registryFor(backgroundScope) { manager }
        registry.connect(server)
        runCurrent()

        val invalidated = registry.invalidateGeneration(server.toServerRef(), ServerGeneration(7))

        assertFalse(invalidated)
        assertEquals(ConnectionState.Connected, registry.connectionState(server.toServerRef()).value)
        assertSame(manager.connection.value, registry.connection(server.toServerRef()).value)
        verify(exactly = 1) { manager.disconnect(ServerGeneration(7)) }
        verify(exactly = 0) { manager.disconnect() }
    }

    @Test
    fun `directory scoped server disposal remains an event without disconnecting generation`() = runTest {
        val server = SavedServerRegistry.fromConnection("http://directory-disposed.example.com", "Directory")
        val events = MutableSharedFlow<OpenCodeEventSource.DirectoryEvent>()
        val manager = successfulManagerWithEvents(server, 3, events)
        val registry = registryFor(backgroundScope) { manager }
        registry.connect(server)
        runCurrent()
        val received = async { registry.events(server.toServerRef()).first() }
        runCurrent()

        events.emit(
            OpenCodeEventSource.DirectoryEvent(
                "/workspace",
                OpenCodeEvent.ServerInstanceDisposed("/workspace"),
            ),
        )
        assertTrue(received.await().event is OpenCodeEvent.ServerInstanceDisposed)

        assertEquals(ConnectionState.Connected, registry.connectionState(server.toServerRef()).value)
        verify(exactly = 0) { manager.disconnect(ServerGeneration(3)) }
    }

    @Test
    fun `disconnect cancels an in flight connection attempt`() = runTest {
        val server = SavedServerRegistry.fromConnection("http://slow.example.com", "Slow")
        val manager = mockk<ConnectionManager>(relaxed = true)
        val cancelled = CompletableDeferred<Unit>()
        every { manager.connection } returns MutableStateFlow(null)
        every { manager.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)
        coEvery { manager.connect(server.toServerConfig(), any()) } coAnswers {
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }
        val registry = registryFor(backgroundScope) { manager }

        registry.connect(server)
        runCurrent()
        registry.disconnect(server.toServerRef())
        runCurrent()

        assertTrue(cancelled.isCompleted)
        assertEquals(ConnectionState.Disconnected, registry.connectionState(server.toServerRef()).value)
        coVerify(exactly = 1) { manager.disconnect() }
    }

    @Test
    fun `connect saved server uses persisted password when caller omits one`() = runTest {
        val server = SavedServerRegistry.fromConnection("http://authenticated.example.com", "Authenticated")
        val settings = mockk<SettingsDataStore>()
        coEvery { settings.getSavedServerPassword(server) } returns "persisted-password"
        val manager = successfulManager(server)
        val registry = ServerConnectionRegistry(settings, { _, _ -> manager }, backgroundScope)

        registry.connect(server)
        runCurrent()

        coVerify(exactly = 1) { manager.connect(server.toServerConfig(), "persisted-password") }
    }

    @Test
    fun `connect saved server keeps explicit password authoritative`() = runTest {
        val server = SavedServerRegistry.fromConnection("http://authenticated.example.com", "Authenticated")
        val settings = mockk<SettingsDataStore>()
        val manager = successfulManager(server)
        val registry = ServerConnectionRegistry(settings, { _, _ -> manager }, backgroundScope)

        registry.connect(server, "explicit-password")
        runCurrent()

        coVerify(exactly = 1) { manager.connect(server.toServerConfig(), "explicit-password") }
        coVerify(exactly = 0) { settings.getSavedServerPassword(any()) }
    }

    @Test
    fun `reconnectAll reconnects only saved open-tab servers`() = runTest {
        val alpha = SavedServerRegistry.fromConnection("http://alpha.example.com", "Alpha")
        val beta = SavedServerRegistry.fromConnection("http://beta.example.com", "Beta")
        val missing = ServerRef.fromEndpoint("http://missing.example.com")
        val settings = mockk<SettingsDataStore>()
        coEvery { settings.getSavedServers() } returns listOf(alpha, beta)
        coEvery { settings.getSavedServerPassword(alpha) } returns "alpha-pass"
        coEvery { settings.getSavedServerPassword(beta) } returns "beta-pass"
        val alphaManager = successfulManager(alpha)
        val betaManager = successfulManager(beta)
        val registry = ServerConnectionRegistry(settings, { config, _ ->
            when (config.url) {
                alpha.endpoint -> alphaManager
                beta.endpoint -> betaManager
                else -> error("unexpected config $config")
            }
        }, backgroundScope)

        registry.reconnectAll(setOf(alpha.toServerRef(), missing))
        runCurrent()

        assertEquals(ConnectionState.Connected, registry.connectionState(alpha.toServerRef()).value)
        assertEquals(ConnectionState.Disconnected, registry.connectionState(beta.toServerRef()).value)
        coVerify(exactly = 1) { alphaManager.connect(alpha.toServerConfig(), "alpha-pass") }
        coVerify(exactly = 0) { betaManager.connect(any(), any()) }
    }

    private fun registryFor(
        scope: CoroutineScope,
        factory: (ServerConfig) -> ConnectionManager,
    ): ServerConnectionRegistry {
        val settings = mockk<SettingsDataStore>()
        coEvery { settings.getSavedServerPassword(any()) } returns null
        return ServerConnectionRegistry(settings, { config, _ -> factory(config) }, scope)
    }

    private fun successfulManager(server: dev.blazelight.p4oc.core.datastore.SavedServer): ConnectionManager {
        val manager = mockk<ConnectionManager>(relaxed = true)
        val connection = mockk<Connection> {
            every { config } returns server.toServerConfig()
            every { generation } returns ServerGeneration(1)
            every { eventSource } returns mockk { every { directoryEvents } returns MutableSharedFlow() }
        }
        every { manager.connection } returns MutableStateFlow(connection)
        every { manager.connectionState } returns MutableStateFlow(ConnectionState.Connected)
        coEvery { manager.connect(server.toServerConfig(), any()) } returns Result.success(emptyList())
        return manager
    }

    private fun successfulManagerWithEvents(
        server: dev.blazelight.p4oc.core.datastore.SavedServer,
        generationValue: Long,
        events: MutableSharedFlow<OpenCodeEventSource.DirectoryEvent>,
    ): ConnectionManager {
        val eventSource = mockk<OpenCodeEventSource> { every { directoryEvents } returns events }
        val connection = mockk<Connection> {
            every { config } returns server.toServerConfig()
            every { generation } returns ServerGeneration(generationValue)
            every { this@mockk.eventSource } returns eventSource
        }
        return successfulManager(server).also { every { it.connection } returns MutableStateFlow(connection) }
    }

    private fun failingManager(
        server: dev.blazelight.p4oc.core.datastore.SavedServer,
        message: String,
    ): ConnectionManager {
        val manager = mockk<ConnectionManager>(relaxed = true)
        every { manager.connection } returns MutableStateFlow(null)
        every { manager.connectionState } returns MutableStateFlow(ConnectionState.Error(message))
        coEvery { manager.connect(server.toServerConfig(), any()) } returns Result.failure(
            IllegalStateException(message),
        )
        return manager
    }

    private fun scopedEvent(serverRef: ServerRef, generation: Long) = ScopedEvent(
        serverRef = serverRef,
        generation = ServerGeneration(generation),
        workspaceKey = WorkspaceKey.Global,
        event = OpenCodeEvent.Disconnected(null),
    )
}
