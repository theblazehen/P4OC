package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.core.datastore.SavedServerRegistry
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.network.Connection
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.OpenCodeApi
import dev.blazelight.p4oc.core.network.OpenCodeEventSource
import dev.blazelight.p4oc.core.network.ServerConfig
import dev.blazelight.p4oc.core.network.ServerConnectionRegistry
import dev.blazelight.p4oc.core.network.toServerConfig
import dev.blazelight.p4oc.core.network.toServerRef
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.data.server.ActiveServerApiProvider
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.model.TokenUsage
import dev.blazelight.p4oc.domain.server.ScopedEvent
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.domain.workspace.Workspace
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SessionRepositoryProviderTest {
    private val server = ServerRef.fromEndpointKey("http://fake.test")
    private val workspace = Workspace(server = server, directory = "/repo")
    private val generation = ServerGeneration(1)

    private data class Bound(
        val api: OpenCodeApi,
        val epoch: MutableStateFlow<Long>,
        val epochCollectors: java.util.concurrent.atomic.AtomicInteger,
    )

    /**
     * A [MutableStateFlow] that counts its active collectors so a test can assert that a
     * released repository's reconnect epoch subscription has been torn down.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private class CountingStateFlow(
        private val delegate: MutableStateFlow<Long> = MutableStateFlow(0L),
    ) : MutableStateFlow<Long> {
        val collectorCount = java.util.concurrent.atomic.AtomicInteger(0)

        override var value: Long
            get() = delegate.value
            set(value) {
                delegate.value = value
            }

        override val replayCache: List<Long>
            get() = delegate.replayCache

        override val subscriptionCount: StateFlow<Int>
            get() = delegate.subscriptionCount

        override suspend fun emit(value: Long) {
            delegate.emit(value)
        }

        override fun tryEmit(value: Long): Boolean = delegate.tryEmit(value)

        override fun resetReplayCache() {
            delegate.resetReplayCache()
        }

        override suspend fun collect(collector: FlowCollector<Long>): Nothing {
            collectorCount.incrementAndGet()
            try {
                return delegate.collect(collector)
            } finally {
                collectorCount.decrementAndGet()
            }
        }

        override fun compareAndSet(expect: Long, update: Long): Boolean =
            delegate.compareAndSet(expect, update)
    }

    /** Registers an exact (server, generation) connection epoch and API for the harness. */
    private fun harness(): Harness = Harness()

    private inner class Harness {
        val scopedEvents = MutableSharedFlow<ScopedEvent>()
        private val registry = mockk<ServerConnectionRegistry>(relaxed = true)
        private val byKey = mutableMapOf<Pair<ServerRef, ServerGeneration>, Bound>()
        private val defaultApi = mockk<OpenCodeApi>(relaxed = true)

        fun bind(
            serverRef: ServerRef = server,
            generation: ServerGeneration = this@SessionRepositoryProviderTest.generation,
        ): Bound = byKey.getOrPut(serverRef to generation) {
            val epoch = CountingStateFlow()
            val bound = Bound(
                api = mockk<OpenCodeApi>(relaxed = true),
                epoch = epoch,
                epochCollectors = epoch.collectorCount,
            )
            // ServerGeneration is a value class (erased to Long), so MockK's dynamic
            // any()/secondArg() matching cannot be used on it. Install exact stubs per pair.
            every { registry.connectionState(serverRef, generation) } returns
                MutableStateFlow(ConnectionState.Disconnected)
            every { registry.connectionEpoch(serverRef, generation) } returns epoch
            bound
        }

        fun provider(
            scopedEvents: Flow<ScopedEvent> = this.scopedEvents,
            dispatcher: CoroutineDispatcher = StandardTestDispatcher(),
            json: Json = Json.Default,
        ): SessionRepositoryProvider {
            // Ensure the default (server, generation) binding exists so a relaxed registry mock
            // never returns Nothing for connectionEpoch/connectionState.
            bind(server, generation)
            // ServerRef is not a value class, so dynamic matching is safe for the event stream.
            every { registry.events(any()) } returns scopedEvents
            return SessionRepositoryProvider(
                activeServerApiProvider = ActiveServerApiProvider { serverRef, generation ->
                    byKey[serverRef to generation]?.api ?: defaultApi
                },
                messageMapper = MessageMapper(),
                serverConnectionRegistry = registry,
                dispatcher = dispatcher,
                repositoryDispatcher = dispatcher,
                json = json,
            )
        }
    }

    @Test
    fun `provider forwards configured json to workspace client legacy question decoding`() = runTest {
        val harness = harness()
        val bound = harness.bind()
        val provider = harness.provider(json = Json { ignoreUnknownKeys = true })
        val response = """
            [
              {
                "id": "question-1",
                "sessionID": "session-1",
                "questions": [
                  {
                    "header": "Confirm",
                    "question": "Continue?",
                    "options": [],
                    "unknownQuestionField": "ignored"
                  }
                ],
                "unknownRequestField": "ignored"
              }
            ]
        """.trimIndent()
        coEvery { bound.api.listPendingQuestions("/repo", null) } returns Response.success(
            response.toResponseBody("application/json".toMediaType()),
        )

        val lease = provider.acquire(workspace, generation)
        try {
            val questions = lease.workspaceClient.listSessionQuestions("session-1")

            assertEquals(listOf("question-1"), questions.map { it.id })
            assertEquals("Continue?", questions.single().questions.single().question)
        } finally {
            provider.release(workspace, generation)
        }
    }

    @Test
    fun `acquire reuses repository for same workspace generation`() {
        val provider = harness().provider()

        val first = provider.acquire(workspace, generation)
        val second = provider.acquire(workspace, generation)

        assertSame(first.repository, second.repository)
        assertSame(first.workspaceClient, second.workspaceClient)
    }

    @Test
    fun `repository remains retained until final matching release`() {
        val provider = harness().provider()
        val first = provider.acquire(workspace, generation)
        provider.acquire(workspace, generation)

        provider.release(workspace, generation)
        val afterSingleRelease = provider.acquire(workspace, generation)

        assertSame(first.repository, afterSingleRelease.repository)
    }

    @Test
    fun `final release closes repository and next acquire creates replacement`() {
        val provider = harness().provider()
        val first = provider.acquire(workspace, generation)

        provider.release(workspace, generation)
        val second = provider.acquire(workspace, generation)

        assertNotSame(first.repository, second.repository)
        assertNotSame(first.workspaceClient, second.workspaceClient)
    }

    @Test
    fun `different generation gets separate repository`() {
        val harness = harness()
        val provider = harness.provider()
        harness.bind(server, ServerGeneration(2))

        val first = provider.acquire(workspace, generation)
        val second = provider.acquire(workspace, ServerGeneration(2))

        assertNotSame(first.repository, second.repository)
        assertNotSame(first.workspaceClient, second.workspaceClient)
    }

    @Test
    fun `same directory on different servers gets separate repositories`() {
        val harness = harness()
        val provider = harness.provider()
        val otherServer = ServerRef.fromEndpoint("http://other.test:4096")
        val otherWorkspace = Workspace(server = otherServer, directory = workspace.directory.orEmpty())
        harness.bind(otherServer, generation)

        val first = provider.acquire(workspace, generation)
        val second = provider.acquire(otherWorkspace, generation)

        assertNotSame(first.repository, second.repository)
        assertNotSame(first.workspaceClient, second.workspaceClient)
    }

    @Test
    fun `reconnect generation recreates only affected server workspace owner`() {
        val harness = harness()
        val provider = harness.provider()
        val otherServer = ServerRef.fromEndpoint("http://other.test:4096")
        val otherWorkspace = Workspace(server = otherServer, directory = workspace.directory.orEmpty())
        harness.bind(otherServer, generation)
        val first = provider.acquire(workspace, generation)
        val other = provider.acquire(otherWorkspace, generation)

        provider.release(workspace, generation)
        harness.bind(server, ServerGeneration(2))
        val afterReconnect = provider.acquire(workspace, ServerGeneration(2))
        val otherAgain = provider.acquire(otherWorkspace, generation)

        assertNotSame(first.repository, afterReconnect.repository)
        assertSame(other.repository, otherAgain.repository)
    }

    @Test
    fun `provider routes scoped events to shared repository`() = runTest {
        val event = sessionCreatedEvent("s1")
        val harness = harness()
        val provider = harness.provider(
            scopedEvents = flowOf(
                ScopedEvent(
                    serverRef = server,
                    generation = generation,
                    workspaceKey = workspace.key,
                    event = event,
                ),
            ),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val lease = provider.acquire(workspace, generation)
        testScheduler.advanceUntilIdle()

        assertTrue(lease.repository.state.value is RepoState.Hydrating)
    }

    @Test
    fun `scoped events from a different server are not delivered`() = runTest {
        val otherServer = ServerRef.fromEndpoint("http://other.test:4096")
        val harness = harness()
        val provider = harness.provider(
            scopedEvents = flowOf(
                ScopedEvent(
                    serverRef = otherServer,
                    generation = generation,
                    workspaceKey = workspace.key,
                    event = OpenCodeEvent.MessageUpdated(assistantMessage("m1")),
                ),
            ),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val lease = provider.acquire(workspace, generation)
        testScheduler.advanceUntilIdle()

        // The event targets another server's repository; this workspace must not ingest it.
        assertEquals(
            emptyList<MessageWithParts>(),
            lease.repository.messages(SessionId("s1")).value,
        )
    }

    @Test
    fun `throwing event does not permanently kill workspace event collection`() = runTest {
        val bad = mockk<Message> { every { sessionID } throws RuntimeException("boom") }
        val harness = harness()
        val provider = harness.provider(
            scopedEvents = flowOf(
                scoped(OpenCodeEvent.MessageUpdated(bad)),
                scoped(OpenCodeEvent.MessageUpdated(assistantMessage("m1"))),
            ),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val lease = provider.acquire(workspace, generation)
        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf("m1"),
            lease.repository.messages(SessionId("s1")).value.map { it.message.id },
        )
    }

    @Test
    fun `initial non-zero epoch does not emit a redundant reconnect`() = runTest {
        val harness = harness()
        val bound = harness.bind()
        bound.epoch.value = 1L
        val provider = harness.provider(dispatcher = StandardTestDispatcher(testScheduler))

        val lease = provider.acquire(workspace, generation)
        lease.repository.acquireSession(SessionId("s1"))
        testScheduler.advanceUntilIdle()

        // The epoch is already non-zero when the collector starts: the first emission is the
        // baseline (no reconnect), so the repository must not run reconnect message recovery.
        coVerify(exactly = 0) { bound.api.getMessages("s1", 100, null, "/repo", null) }
    }

    @Test
    fun `reconnect delivers message recovery on exact epoch increase`() = runTest {
        val harness = harness()
        val bound = harness.bind()
        val provider = harness.provider(dispatcher = StandardTestDispatcher(testScheduler))

        val lease = provider.acquire(workspace, generation)
        lease.repository.acquireSession(SessionId("s1"))
        testScheduler.advanceUntilIdle()

        bound.epoch.value = 1L
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { bound.api.getMessages("s1", 100, null, "/repo", null) }
    }

    @Test
    fun `reconnect recovery repeats on every epoch increase`() = runTest {
        val harness = harness()
        val bound = harness.bind()
        val provider = harness.provider(dispatcher = StandardTestDispatcher(testScheduler))

        val lease = provider.acquire(workspace, generation)
        lease.repository.acquireSession(SessionId("s1"))
        testScheduler.advanceUntilIdle()

        bound.epoch.value = 1L
        testScheduler.advanceUntilIdle()
        bound.epoch.value = 2L
        testScheduler.advanceUntilIdle()
        bound.epoch.value = 3L
        testScheduler.advanceUntilIdle()

        // Message recovery must fire on every reconnect, not only the first (issue #14).
        coVerify(atLeast = 3) { bound.api.getMessages("s1", 100, null, "/repo", null) }
    }

    @Test
    fun `reconnect is scoped to the exact server and generation that transitioned`() = runTest {
        val harness = harness()
        val primary = harness.bind(serverRef = server, generation = generation)
        val otherServer = ServerRef.fromEndpoint("http://other.test:4096")
        val otherWorkspace = Workspace(server = otherServer, directory = workspace.directory.orEmpty())
        val other = harness.bind(serverRef = otherServer, generation = generation)
        val provider = harness.provider(dispatcher = StandardTestDispatcher(testScheduler))

        val primaryLease = provider.acquire(workspace, generation)
        primaryLease.repository.acquireSession(SessionId("s1"))
        val otherLease = provider.acquire(otherWorkspace, generation)
        otherLease.repository.acquireSession(SessionId("s1"))
        testScheduler.advanceUntilIdle()

        // Only the primary server's epoch increases; the other server must stay silent.
        primary.epoch.value = 1L
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { primary.api.getMessages("s1", 100, null, "/repo", null) }
        coVerify(exactly = 0) { other.api.getMessages("s1", 100, null, "/repo", null) }
    }

    @Test
    fun `scoped raw Connected event does not duplicate reconnect recovery`() = runTest {
        val harness = harness()
        val bound = harness.bind()
        val provider = harness.provider(dispatcher = StandardTestDispatcher(testScheduler))

        val lease = provider.acquire(workspace, generation)
        lease.repository.acquireSession(SessionId("s1"))
        testScheduler.advanceUntilIdle()

        // Real reconnect (epoch increase) triggers one recovery.
        bound.epoch.value = 1L
        testScheduler.advanceUntilIdle()

        // A raw OpenCodeEvent.Connected in the scoped stream must NOT trigger a second recovery.
        harness.scopedEvents.emit(scoped(OpenCodeEvent.Connected))
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { bound.api.getMessages("s1", 100, null, "/repo", null) }
    }

    @Test
    fun `final release cancels reconnect epoch subscription`() = runTest {
        val harness = harness()
        val bound = harness.bind()
        val provider = harness.provider(dispatcher = StandardTestDispatcher(testScheduler))

        val lease = provider.acquire(workspace, generation)
        lease.repository.acquireSession(SessionId("s1"))
        testScheduler.advanceUntilIdle()
        assertEquals(1, bound.epochCollectors.get())

        // One active consumer -> epoch increase recovers messages.
        bound.epoch.value = 1L
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { bound.api.getMessages("s1", 100, null, "/repo", null) }

        // Final release cancels the reconnect job.
        provider.release(workspace, generation)
        testScheduler.advanceUntilIdle()

        bound.epoch.value = 2L
        testScheduler.advanceUntilIdle()

        // The epoch collector is torn down with the release, so no further recovery occurs.
        assertEquals(0, bound.epochCollectors.get())
        coVerify(exactly = 1) { bound.api.getMessages(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `real registry epoch bridges to provider message recovery across reconnect`() = runTest {
        val saved = SavedServerRegistry.fromConnection(
            "http://integration.example.com",
            "Integration",
        )
        val serverRef = saved.toServerRef()
        val integrationWorkspace = Workspace(server = serverRef, directory = "/repo")
        val integrationGeneration = ServerGeneration(1)
        val managerConnection = MutableStateFlow<Connection?>(null)
        val managerState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        val manager = mockk<ConnectionManager>(relaxed = true) {
            every { connection } returns managerConnection
            every { connectionState } returns managerState
        }
        coEvery { manager.connect(any(), any()) } returns Result.failure(
            IllegalStateException("unused"),
        )
        val settings = mockk<SettingsDataStore>()
        coEvery { settings.getSavedServerPassword(any()) } returns null
        val registry = ServerConnectionRegistry(settings, { _, _ -> manager }, backgroundScope)
        val api = mockk<OpenCodeApi>(relaxed = true)
        val provider = SessionRepositoryProvider(
            activeServerApiProvider = ActiveServerApiProvider { _, _ -> api },
            messageMapper = MessageMapper(),
            serverConnectionRegistry = registry,
            dispatcher = StandardTestDispatcher(testScheduler),
            repositoryDispatcher = StandardTestDispatcher(testScheduler),
        )

        // Acquire the provider for the exact generation BEFORE the registry initializes/connects:
        // this exercises the pre-publication orphan path — the stable per-generation epoch flow must
        // be created (0) and later bridged once the generation activates, not lost.
        val lease = provider.acquire(integrationWorkspace, integrationGeneration)
        lease.repository.acquireSession(SessionId("s1"))
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 0) { api.getMessages(any(), any(), any(), any(), any()) }

        registry.connect(saved)
        runCurrent()

        // Publish generation 1 with epoch 1; the provider must deliver a reconnect recovery.
        val epochFlow = MutableStateFlow(1L)
        managerConnection.value = connection(saved.toServerConfig(), 1, epochFlow)
        runCurrent()
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { api.getMessages("s1", 100, null, "/repo", null) }

        // Bump the source epoch to 2 (a reconnect); the provider must recover again.
        epochFlow.value = 2L
        runCurrent()
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 2) { api.getMessages("s1", 100, null, "/repo", null) }
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

    private fun scoped(event: OpenCodeEvent): ScopedEvent = ScopedEvent(
        serverRef = server,
        generation = generation,
        workspaceKey = workspace.key,
        event = event,
    )

    private fun sessionCreatedEvent(id: String): OpenCodeEvent.SessionCreated = OpenCodeEvent.SessionCreated(
        session = Session(
            id = id,
            projectID = "project-$id",
            directory = workspace.directory.orEmpty(),
            title = id,
            version = "1",
            createdAt = 1L,
            updatedAt = 1L,
        ),
    )

    private fun assistantMessage(id: String): Message.Assistant = Message.Assistant(
        id = id,
        sessionID = "s1",
        createdAt = 1L,
        parentID = "",
        providerID = "provider",
        modelID = "model",
        mode = "chat",
        agent = "assistant",
        cost = 0.0,
        tokens = TokenUsage(input = 0, output = 0),
    )
}
