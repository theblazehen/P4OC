package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.core.datastore.SavedServer
import dev.blazelight.p4oc.core.datastore.SavedServerRegistry
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.data.remote.dto.CreateSessionRequest
import dev.blazelight.p4oc.data.remote.dto.ModelInput
import dev.blazelight.p4oc.data.remote.dto.PartInputDto
import dev.blazelight.p4oc.data.remote.dto.SendMessageRequest
import dev.blazelight.p4oc.data.remote.mapper.EventMapper
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.data.server.StaleWorkspaceClientException
import dev.blazelight.p4oc.data.session.SessionRepositoryProvider
import dev.blazelight.p4oc.di.activeServerApiProvider
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.server.ScopedEvent
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.domain.workspace.Workspace
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

/**
 * Opt-in, real HTTP checks with a v1 and a v2 server connected through one registry at the same time.
 * Requires OPENCODE_V1_URL, OPENCODE_V2_URL, and OPENCODE_V2_MODEL (a model both servers offer).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenCodeMixedProtocolLiveIntegrationTest {
    // Connection and repository code posts to Main; a real thread keeps that work running, as on device.
    private val mainDispatcher = Executors.newSingleThreadExecutor { Thread(it, "p4oc-test-main") }
        .asCoroutineDispatcher()
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var registry: ServerConnectionRegistry
    private lateinit var v1: SavedServer
    private lateinit var v2: SavedServer
    private lateinit var model: String
    private val directory get() = System.getenv("OPENCODE_V2_DIRECTORY") ?: "/workspace"

    @Before
    fun setUp() {
        val v1Url = System.getenv("OPENCODE_V1_URL").orEmpty()
        val v2Url = System.getenv("OPENCODE_V2_URL").orEmpty()
        model = System.getenv("OPENCODE_V2_MODEL").orEmpty()
        assumeTrue("Set OPENCODE_V1_URL and OPENCODE_V2_URL to run mixed-protocol checks", v1Url.isNotBlank())
        assumeTrue("Set OPENCODE_V1_URL and OPENCODE_V2_URL to run mixed-protocol checks", v2Url.isNotBlank())
        assumeTrue("Set OPENCODE_V2_MODEL to a model offered by both servers", model.isNotBlank())
        Dispatchers.setMain(mainDispatcher)
        v1 = SavedServerRegistry.fromConnection(v1Url, "P4OC v1", username = username())
        v2 = SavedServerRegistry.fromConnection(v2Url, "P4OC v2", username = username())
        registry = ServerConnectionRegistry(
            settingsDataStore = mockk<SettingsDataStore>(),
            connectionManagerFactory = { _, generationIssuer ->
                ConnectionManager(
                    json = json,
                    eventMapper = EventMapper(json, MessageMapper()),
                    settingsDataStore = mockk<SettingsDataStore>(),
                    generationIssuer = generationIssuer,
                )
            },
            scope = scope,
        )
    }

    @After
    fun tearDown() {
        if (::registry.isInitialized) {
            registry.disconnect(v1.toServerRef())
            registry.disconnect(v2.toServerRef())
        }
        scope.cancel()
        Dispatchers.resetMain()
        mainDispatcher.close()
    }

    @Suppress("LongMethod")
    @Test
    fun `v1 and v2 servers stay isolated while connected at the same time`() = runBlocking {
        listOf(
            async { registry.connectAndAwait(v1, password("OPENCODE_V1_PASSWORD")) },
            async { registry.connectAndAwait(v2, password("OPENCODE_V2_PASSWORD")) },
        ).awaitAll().forEach { it.getOrThrow() }
        val v1Ref = v1.toServerRef()
        val v2Ref = v2.toServerRef()
        assertNotEquals(v1Ref.endpointKey, v2Ref.endpointKey)
        val v1Generation = checkNotNull(registry.generation(v1Ref))
        val v2Generation = checkNotNull(registry.generation(v2Ref))
        assertNotEquals(v1Generation, v2Generation)
        assertFalse(registry.api(v1Ref, v1Generation) is V2WorkspaceOpenCodeApi)
        assertTrue(registry.api(v2Ref, v2Generation) is V2WorkspaceOpenCodeApi)
        assertNull("A generation must never resolve on another server", registry.api(v1Ref, v2Generation))
        assertNull("A generation must never resolve on another server", registry.api(v2Ref, v1Generation))

        val provider = SessionRepositoryProvider(
            activeServerApiProvider = activeServerApiProvider(registry),
            messageMapper = MessageMapper(),
            serverConnectionRegistry = registry,
            json = json,
        )
        // Same project directory on both servers: isolation must come from server identity alone.
        val v1Workspace = Workspace(v1Ref, directory)
        val v2Workspace = Workspace(v2Ref, directory)
        val v1Lease = provider.acquire(v1Workspace, v1Generation)
        val v2Lease = provider.acquire(v2Workspace, v2Generation)
        assertFalse(v1Lease.workspaceClient.supportsV2Forms)
        assertTrue(v1Lease.workspaceClient.supportsSessionSharing)
        assertTrue(v2Lease.workspaceClient.supportsV2Forms)
        assertFalse(v2Lease.workspaceClient.supportsSessionSharing)

        val events = ConcurrentLinkedQueue<ScopedEvent>()
        val eventCollector = scope.launch { registry.scopedEvents.collect { events += it } }
        val v1Session = v1Lease.workspaceClient.createSession(CreateSessionRequest(title = "P4OC mixed v1"))
        val v2Session = v2Lease.workspaceClient.createSession(CreateSessionRequest(title = "P4OC mixed v2"))
        val v1Token = "P4OC_V1_${UUID.randomUUID().toString().take(8).uppercase()}"
        val v2Token = "P4OC_V2_${UUID.randomUUID().toString().take(8).uppercase()}"
        val v1Messages = v1Lease.repository.acquireSession(SessionId(v1Session.id))
        val v2Messages = v2Lease.repository.acquireSession(SessionId(v2Session.id))
        try {
            listOf(
                async { v1Lease.workspaceClient.sendMessageAsync(v1Session.id, prompt(v1Token)) },
                async { v2Lease.workspaceClient.sendMessageAsync(v2Session.id, prompt(v2Token)) },
            ).awaitAll()
            val v1Text = awaitAssistantText(v1Lease, v1Session.id, v1Token)
            val v2Text = awaitAssistantText(v2Lease, v2Session.id, v2Token)
            assertFalse("v2 output leaked into the v1 session", v2Token in v1Text)
            assertFalse("v1 output leaked into the v2 session", v1Token in v2Text)
            assertTrue(v1Lease.repository.messages(SessionId(v2Session.id)).value.isEmpty())
            assertTrue(v2Lease.repository.messages(SessionId(v1Session.id)).value.isEmpty())
            // The finished reply must survive post-run reconciliation, not only appear while streaming.
            delay(RECONCILE_SETTLE_MS)
            assertTrue("v1 reply lost after the run settled", v1Token in assistantText(v1Lease, v1Session.id))
            assertTrue("v2 reply lost after the run settled", v2Token in assistantText(v2Lease, v2Session.id))

            val byServer = events.groupBy { it.serverRef.endpointKey }
            assertTrue("Expected live v1 events", byServer[v1Ref.endpointKey].orEmpty().isNotEmpty())
            assertTrue("Expected live v2 events", byServer[v2Ref.endpointKey].orEmpty().isNotEmpty())
            events.forEach { event ->
                val expected = if (event.serverRef == v1Ref) v1Generation else v2Generation
                assertEquals("Event tagged with another server's generation", expected, event.generation)
            }
            assertTrue(
                "v2-only content events must never be attributed to the v1 server",
                byServer[v1Ref.endpointKey].orEmpty().none { it.event is OpenCodeEvent.V2ContentChanged },
            )

            // Dropping one server leaves the other fully usable.
            assertTrue(v2Lease.workspaceClient.deleteSession(v2Session.id))
            registry.disconnect(v2Ref)
            withTimeout(10_000) { registry.connection(v2Ref).first { it == null } }
            assertEquals(v1Session.id, v1Lease.workspaceClient.getSession(v1Session.id).id)
            assertTrue("Retired v2 client must keep its capabilities", v2Lease.workspaceClient.supportsV2Forms)
            try {
                v2Lease.workspaceClient.getSession(v2Session.id)
                fail("A retired v2 generation must not reach any server")
            } catch (_: StaleWorkspaceClientException) {
                // Expected.
            }
        } finally {
            v1Messages.close()
            v2Messages.close()
            eventCollector.cancel()
            assertTrue(v1Lease.workspaceClient.deleteSession(v1Session.id))
            provider.release(v1Workspace, v1Generation)
            provider.release(v2Workspace, v2Generation)
        }
    }

    private suspend fun awaitAssistantText(
        lease: SessionRepositoryProvider.Lease,
        sessionId: String,
        token: String,
    ): String {
        val found = withTimeoutOrNull(120_000) {
            lease.repository.messages(SessionId(sessionId)).first { assistantText(it).contains(token) }
        }
        if (found == null) {
            fail(
                "No assistant reply with $token reached the " +
                    "${lease.workspaceClient.workspace.server.endpointKey} repository. " +
                    "Repository: ${describe(lease.repository.messages(SessionId(sessionId)).value)}. " +
                    "Server: ${describeServer(lease, sessionId)}",
            )
        }
        return assistantText(lease, sessionId)
    }

    private fun describe(messages: List<MessageWithParts>): String = messages.joinToString { message ->
        "${message.message::class.simpleName}(${message.parts.joinToString { "${it::class.simpleName}" }})"
    }

    private suspend fun describeServer(lease: SessionRepositoryProvider.Lease, sessionId: String): String =
        runCatching {
            lease.workspaceClient.getMessages(sessionId, 20).joinToString { wrapper ->
                "${wrapper.info.role}:${wrapper.parts.joinToString { "${it.type}=${it.text?.take(40)}" }}" +
                    (wrapper.info.error?.let { " error=${it.name}" } ?: "")
            }
        }.getOrElse { "unavailable (${it.javaClass.simpleName}: ${it.message})" }

    private fun assistantText(lease: SessionRepositoryProvider.Lease, sessionId: String): String =
        assistantText(lease.repository.messages(SessionId(sessionId)).value)

    // The prompt itself contains the token, so only assistant output proves a reply arrived.
    private fun assistantText(messages: List<MessageWithParts>): String = messages
        .filter { it.message is Message.Assistant }
        .joinToString("\n") { message -> message.parts.filterIsInstance<Part.Text>().joinToString { it.text } }

    private fun prompt(token: String) = SendMessageRequest(
        // The agent id: v1 silently drops a prompt addressed by display name, v2 accepts either.
        agent = "build",
        model = ModelInput("opencode", model),
        parts = listOf(PartInputDto(type = "text", text = "Reply exactly: $token")),
    )

    private fun username() = System.getenv("OPENCODE_V2_USERNAME") ?: "opencode"

    private fun password(name: String) = System.getenv(name) ?: System.getenv("OPENCODE_V2_PASSWORD")

    private companion object {
        const val RECONCILE_SETTLE_MS = 8_000L
    }
}
