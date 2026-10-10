package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.data.files.FileOperationResult
import dev.blazelight.p4oc.data.files.FileRepositoryFactory
import dev.blazelight.p4oc.data.files.FileUploadRequest
import dev.blazelight.p4oc.data.files.FileWriteRequest
import dev.blazelight.p4oc.data.remote.dto.AddMcpServerRequest
import dev.blazelight.p4oc.data.remote.dto.CreatePtyRequest
import dev.blazelight.p4oc.data.remote.dto.CreateSessionRequest
import dev.blazelight.p4oc.data.remote.dto.ForkSessionRequest
import dev.blazelight.p4oc.data.remote.dto.InitSessionRequest
import dev.blazelight.p4oc.data.remote.dto.McpConfigDto
import dev.blazelight.p4oc.data.remote.dto.ModelInput
import dev.blazelight.p4oc.data.remote.dto.PartInputDto
import dev.blazelight.p4oc.data.remote.dto.PermissionResponseRequest
import dev.blazelight.p4oc.data.remote.dto.RevertSessionRequest
import dev.blazelight.p4oc.data.remote.dto.SendMessageRequest
import dev.blazelight.p4oc.data.remote.dto.UpdatePtyRequest
import dev.blazelight.p4oc.data.remote.dto.UpdateSessionRequest
import dev.blazelight.p4oc.data.remote.mapper.EventMapper
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.data.server.ActiveServerApiProvider
import dev.blazelight.p4oc.data.vcs.WorkspaceChangesRepositoryImpl
import dev.blazelight.p4oc.data.vcs.WorkspaceChangesResult
import dev.blazelight.p4oc.data.vcs.WorkspacePatch
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.workspace.Workspace
import dev.blazelight.p4oc.ui.components.form.initialDrafts
import dev.blazelight.p4oc.ui.components.form.resolveForm
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Opt-in, real HTTP contract checks. Run with OPENCODE_V2_URL and (if needed) credentials set. */
@Suppress("LargeClass")
@OptIn(ExperimentalCoroutinesApi::class)
class OpenCodeV2LiveIntegrationTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var manager: ConnectionManager
    private val mainDispatcher = StandardTestDispatcher()
    private lateinit var url: String
    private val directory get() = System.getenv("OPENCODE_V2_DIRECTORY") ?: "/workspace"

    @Before
    fun setUp() {
        val configuredUrl = System.getenv("OPENCODE_V2_URL")
        assumeTrue("Set OPENCODE_V2_URL to run the live v2 checks", !configuredUrl.isNullOrBlank())
        url = configuredUrl!!
        Dispatchers.setMain(mainDispatcher)
        manager = ConnectionManager(
            json = json,
            eventMapper = EventMapper(json, MessageMapper()),
            settingsDataStore = mockk<SettingsDataStore>(),
            generationIssuer = { ServerGeneration(1L) },
        )
    }

    @After
    fun tearDown() {
        if (::manager.isInitialized) manager.disconnect()
        mainDispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `connect to v2 and discover project directories`() = runBlocking {
        val projects = connect()
        assertTrue("Expected the live workspace project", projects.any { it.worktree == directory })
    }

    @Test
    fun `create read and remove a session in the requested directory`() = runBlocking {
        connect()
        val api = manager.requireApi()
        val created = api.createSession(
            directory,
            null,
            CreateSessionRequest(title = "P4OC integration ${UUID.randomUUID()}"),
        )
        try {
            assertEquals(directory, created.directory)
            assertEquals(created.id, api.getSession(created.id, directory, null).id)
            val sessions = api.listSessions(directory, null, scope = "project", roots = true, limit = Int.MAX_VALUE)
            assertTrue(sessions.any { it.id == created.id })
        } finally {
            assertTrue(api.deleteSession(created.id, directory, null))
        }
        assertSessionDeleted(api, created.id)
    }

    @Test
    fun `rename and fork a session without losing directory ownership`() = runBlocking {
        connect()
        val api = manager.requireApi()
        val parent = api.createSession(
            directory,
            null,
            CreateSessionRequest(title = "P4OC parent ${UUID.randomUUID()}"),
        )
        var childID: String? = null
        try {
            val renamed = "P4OC renamed ${UUID.randomUUID()}"
            val updated = api.updateSession(parent.id, UpdateSessionRequest(title = renamed), directory, null)
            assertEquals(renamed, updated.title)
            assertEquals(renamed, api.getSession(parent.id, directory, null).title)
            V2Http(url, fixtureClient(), json).request(
                "POST",
                "api/session/${parent.id}/synthetic",
                body = buildJsonObject { put("text", "Fork this session") },
            )
            val child = api.forkSession(parent.id, ForkSessionRequest(), directory, null)
            childID = child.id
            // A fork is an independent session (as on v1), never a subagent child of its origin.
            assertEquals(null, child.parentID)
            assertEquals(directory, child.directory)
            assertTrue(api.getSessionChildren(parent.id, directory, null).none { it.id == child.id })
            assertEquals(child.id, api.getSession(child.id, directory, null).id)
        } finally {
            try {
                childID?.let { assertTrue(api.deleteSession(it, directory, null)) }
            } finally {
                assertTrue(api.deleteSession(parent.id, directory, null))
            }
        }
        assertSessionDeleted(api, parent.id)
    }

    @Test
    fun `observe a scoped session creation over the v2 event stream`() = runBlocking {
        connect()
        val events = manager.getEventSource() ?: error("Expected an event source")
        withTimeout(10_000) { events.connectionState.first { it == ConnectionState.Connected } }
        val observed = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(10_000) {
                events.directoryEvents.first { item ->
                    item.directory == directory && item.event is OpenCodeEvent.SessionRefreshRequested
                }
            }
        }
        val api = manager.requireApi()
        val session = api.createSession(directory, null, CreateSessionRequest(title = "P4OC stream integration"))
        try {
            assertEquals(session.id, (observed.await().event as OpenCodeEvent.SessionRefreshRequested).sessionID)
        } finally {
            api.deleteSession(session.id, directory, null)
        }
    }

    @Test
    fun `shell transcript is returned for its exact message id`() = runBlocking {
        connect()
        val api = manager.requireApi() as V2WorkspaceOpenCodeApi
        val session = api.createSession(directory, null, CreateSessionRequest(title = "P4OC shell integration"))
        try {
            assertEquals("P4OC_V2_SHELL_OK\n", api.executeShellForOutput(session.id, "printf 'P4OC_V2_SHELL_OK\\n'"))
            assertTrue(api.getMessages(session.id, 50, null, directory, null).isEmpty())
        } finally {
            api.deleteSession(session.id, directory, null)
        }
    }

    @Test
    fun `project a synthetic v2 message into the chat timeline`() = runBlocking {
        connect()
        val api = manager.requireApi()
        val session = api.createSession(directory, null, CreateSessionRequest(title = "P4OC message integration"))
        try {
            V2Http(url, fixtureClient(), json).request(
                "POST",
                "api/session/${session.id}/synthetic",
                body = buildJsonObject { put("text", "P4OC_V2_CHAT_OK") },
            )
            withTimeout(5_000) {
                var projected = false
                while (!projected) {
                    projected = api.getMessages(session.id, 50, null, directory, null)
                        .any { message -> message.parts.any { part -> part.text == "P4OC_V2_CHAT_OK" } }
                    if (!projected) delay(50)
                }
            }
        } finally {
            api.deleteSession(session.id, directory, null)
        }
    }

    @Test
    fun `stage and undo a v2 session revert at a persisted user message`() = runBlocking {
        val model = System.getenv("OPENCODE_V2_MODEL").orEmpty()
        assumeTrue("Set OPENCODE_V2_MODEL to run the live revert", model.isNotBlank())
        connect()
        val api = manager.requireApi()
        val events = manager.getEventSource() ?: error("Expected an event source")
        withTimeout(10_000) { events.connectionState.first { it == ConnectionState.Connected } }
        val session = api.createSession(directory, null, CreateSessionRequest(title = "P4OC revert integration"))
        val finished = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(120_000) {
                events.directoryEvents.first { item ->
                    item.directory == directory &&
                        (item.event as? OpenCodeEvent.V2ContentChanged)?.let { frame ->
                            frame.sessionID == session.id && frame.kind == "text" && frame.phase == "ended"
                        } == true
                }
            }
        }
        try {
            api.sendMessageAsync(
                session.id,
                SendMessageRequest(
                    model = ModelInput("opencode", model),
                    parts = listOf(PartInputDto(type = "text", text = "Reply exactly: P4OC_V2_REVERT_READY")),
                ),
                directory,
                null,
            )
            finished.await()
            val message = api.getMessages(session.id, 50, null, directory, null)
                .first { item -> item.parts.any { it.text == "Reply exactly: P4OC_V2_REVERT_READY" } }
            assertTrue(api.getSessionDiff(session.id, message.info.id, directory, null).isEmpty())
            val staged = api.revertSession(session.id, RevertSessionRequest(message.info.id), directory, null)
            assertEquals(message.info.id, staged.revert?.messageID)
            val undone = api.unrevertSession(session.id, directory, null)
            assertEquals(null, undone.revert)
        } finally {
            finished.cancel()
            assertTrue(api.deleteSession(session.id, directory, null))
        }
    }

    @Test
    fun `live assistant deltas converge to persisted message text`() = runBlocking {
        val model = System.getenv("OPENCODE_V2_MODEL").orEmpty()
        assumeTrue("Set OPENCODE_V2_MODEL to run the live assistant stream", model.isNotBlank())
        connect()
        val events = manager.getEventSource() ?: error("Expected an event source")
        withTimeout(10_000) { events.connectionState.first { it == ConnectionState.Connected } }
        val api = manager.requireApi()
        val session = api.createSession(
            directory,
            null,
            CreateSessionRequest(title = "P4OC assistant stream integration"),
        )
        val frames = mutableListOf<OpenCodeEvent.V2ContentChanged>()
        val finished = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(120_000) {
                events.directoryEvents.first { item ->
                    val frame = item.event as? OpenCodeEvent.V2ContentChanged
                    if (item.directory != directory || frame?.sessionID != session.id || frame.kind != "text") {
                        false
                    } else {
                        frames += frame
                        frame.phase == "ended"
                    }
                }
            }
        }
        try {
            api.sendMessageAsync(
                session.id,
                SendMessageRequest(
                    agent = "Build",
                    model = ModelInput("opencode", model),
                    parts = listOf(PartInputDto(type = "text", text = "Reply exactly: P4OC_V2_STREAM_OK")),
                ),
                directory,
                null,
            )
            val ended = finished.await().event as OpenCodeEvent.V2ContentChanged
            assertTrue("Expected live text fragments", frames.any { it.phase == "delta" && it.text.isNotEmpty() })
            val persisted = withTimeout(15_000) {
                var text: String? = null
                while (text != ended.text) {
                    text = api.getMessages(session.id, 50, null, directory, null)
                        .flatMap { it.parts }
                        .firstOrNull { it.id == ended.partID }?.text
                    if (text != ended.text) delay(200)
                }
                text
            }
            assertEquals(ended.text, persisted)
            val history = api.getMessages(session.id, 50, null, directory, null)
            assertEquals(listOf("user", "assistant"), history.map { it.info.role })
            assertEquals(history.sortedBy { it.info.time.created }, history)
        } finally {
            finished.cancel()
            api.deleteSession(session.id, directory, null)
        }
    }

    @Suppress("LongMethod")
    @Test
    fun `persist a catalog model variant after a real v2 prompt`() = runBlocking {
        val configuredModel = System.getenv("OPENCODE_V2_MODEL").orEmpty()
        assumeTrue("Set OPENCODE_V2_MODEL to opt into the live variant prompt", configuredModel.isNotBlank())
        connect()
        val api = manager.requireApi()
        val selectedModel = api.getProviders(directory, null).all
            .firstOrNull { it.id == "opencode" }
            ?.models
            ?.get(configuredModel)
        assumeTrue(
            "OPENCODE_V2_MODEL must expose a low variant in GET /api/model (for example space-bunny-free)",
            selectedModel?.variants?.containsKey("low") == true,
        )
        val model = requireNotNull(selectedModel)
        val events = manager.getEventSource() ?: error("Expected an event source")
        withTimeout(10_000) { events.connectionState.first { it == ConnectionState.Connected } }
        val session = api.createSession(
            directory,
            null,
            CreateSessionRequest(title = "P4OC model variant integration ${UUID.randomUUID()}"),
        )
        val finished = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(120_000) {
                events.directoryEvents.first { item ->
                    item.directory == directory &&
                        (item.event as? OpenCodeEvent.V2ContentChanged)?.let { frame ->
                            frame.sessionID == session.id && frame.kind == "text" && frame.phase == "ended"
                        } == true
                }
            }
        }
        val active = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(10_000) {
                var status = api.getSessionStatuses(null, null)[session.id]
                while (status == null) {
                    delay(50)
                    status = api.getSessionStatuses(null, null)[session.id]
                }
                checkNotNull(status)
            }
        }
        try {
            api.sendMessageAsync(
                session.id,
                SendMessageRequest(
                    model = ModelInput(model.providerId, model.id),
                    variant = "low",
                    parts = listOf(PartInputDto(type = "text", text = "Reply exactly: P4OC_V2_VARIANT_OK")),
                ),
                directory,
                null,
            )
            assertEquals("busy", active.await().type)
            val ended = finished.await().event as OpenCodeEvent.V2ContentChanged
            assertTrue(ended.text.contains("P4OC_V2_VARIANT_OK"))
            val persisted = withTimeout(15_000) {
                var text: String? = null
                while (text != ended.text) {
                    text = api.getMessages(session.id, 50, null, directory, null)
                        .flatMap { it.parts }
                        .firstOrNull { it.id == ended.partID }?.text
                    if (text != ended.text) delay(200)
                }
                text
            }
            assertEquals(ended.text, persisted)
            withTimeout(10_000) {
                while (session.id in api.getSessionStatuses(null, null)) delay(50)
            }
            assertTrue(api.getSessionStatuses(null, null).none { it.key == session.id })
            val storedModel = api.getSession(session.id, directory, null).model
            assertEquals(model.id, storedModel?.id)
            assertEquals(model.providerId, storedModel?.providerID)
            assertEquals("low", storedModel?.variant)
        } finally {
            finished.cancel()
            active.cancel()
            assertTrue(api.deleteSession(session.id, directory, null))
        }
        assertSessionDeleted(api, session.id)
    }

    @Test
    fun `read raw file bytes from a v2 location`() = runBlocking {
        val fileDirectory = System.getenv("OPENCODE_V2_FILE_DIRECTORY")
        assumeTrue("Set OPENCODE_V2_FILE_DIRECTORY to a directory containing README.md", !fileDirectory.isNullOrBlank())
        connect()
        val entries = manager.requireApi().listFiles(".", fileDirectory, null)
        assertTrue(entries.any { it.name == "app" && it.type == "directory" })
        assertTrue(entries.any { it.name == "README.md" && it.type == "file" })
        manager.requireApi().readFileRaw("README.md", fileDirectory, null).body()!!.use { body ->
            assertEquals("text/markdown", body.contentType()?.toString())
            assertTrue(body.string().startsWith("# "))
        }
    }

    @Test
    fun `write and read back a unique v2 workspace file`() = runBlocking {
        val fileDirectory = System.getenv("OPENCODE_V2_FILE_DIRECTORY").orEmpty()
        assumeTrue("Set OPENCODE_V2_FILE_DIRECTORY to a writable directory", fileDirectory.isNotBlank())
        connect()
        val api = manager.requireApi() as V2WorkspaceOpenCodeApi
        val session = api.createSession(
            directory,
            null,
            CreateSessionRequest(title = "P4OC file mutation ${UUID.randomUUID()}"),
        )
        val filename = "p4oc-v2-${UUID.randomUUID()}.txt"
        val root = fileDirectory.trimEnd('/')
        val absolutePath = if (root.isEmpty()) "/$filename" else "$root/$filename"
        val contents = "P4OC_V2_FILE_${UUID.randomUUID()}\n"

        try {
            assertEquals(
                "",
                api.executeShellForOutput(
                    session.id,
                    "printf '%s' ${shellQuote(contents)} > ${shellQuote(absolutePath)}",
                ),
            )
            assertTrue(
                "Expected the v2 file listing to include the written file",
                api.listFiles(".", fileDirectory, null).any { it.name == filename && it.type == "file" },
            )
            api.readFileRaw(filename, fileDirectory, null).body()!!.use { body ->
                assertEquals(contents, body.string())
            }
        } finally {
            try {
                api.executeShellForOutput(session.id, "rm -f -- ${shellQuote(absolutePath)}")
                assertTrue(
                    "Expected the disposable file to be removed",
                    api.listFiles(".", fileDirectory, null).none { it.name == filename },
                )
                val missingFile = runCatching { api.readFileRaw(filename, fileDirectory, null) }.exceptionOrNull()
                assertTrue(
                    "Expected the removed file read to return HTTP 404, got $missingFile",
                    (missingFile as? HttpException)?.code() == HTTP_NOT_FOUND,
                )
            } finally {
                assertTrue(api.deleteSession(session.id, directory, null))
            }
        }
    }

    @Test
    fun `read binary v2 workspace content without utf8 corruption`() = runBlocking {
        val fileDirectory = System.getenv("OPENCODE_V2_FILE_DIRECTORY").orEmpty()
        assumeTrue("Set OPENCODE_V2_FILE_DIRECTORY to a writable directory", fileDirectory.isNotBlank())
        connect()
        val api = manager.requireApi() as V2WorkspaceOpenCodeApi
        val session = api.createSession(directory, null, CreateSessionRequest(title = "P4OC binary integration"))
        val filename = "p4oc-v2-${UUID.randomUUID()}.bin"
        val absolutePath = "${fileDirectory.trimEnd('/')}/$filename"
        try {
            api.executeShellForOutput(session.id, "printf '\\000\\377\\001' > ${shellQuote(absolutePath)}")
            val file = api.readFile(filename, fileDirectory, null)
            assertEquals("binary", file.type)
            assertEquals("base64", file.encoding)
            assertTrue(
                byteArrayOf(0, 255.toByte(), 1).contentEquals(
                    java.util.Base64.getDecoder().decode(file.content),
                ),
            )
        } finally {
            try {
                api.executeShellForOutput(session.id, "rm -f -- ${shellQuote(absolutePath)}")
            } finally {
                assertTrue(api.deleteSession(session.id, directory, null))
            }
        }
    }

    @Test
    fun `create answer and settle a v2 session form`() = runBlocking {
        connect()
        val api = manager.requireApi() as V2WorkspaceOpenCodeApi
        val events = manager.getEventSource() ?: error("Expected an event source")
        withTimeout(10_000) { events.connectionState.first { it == ConnectionState.Connected } }
        val session = api.createSession(
            directory,
            null,
            CreateSessionRequest(title = "P4OC form integration ${UUID.randomUUID()}"),
        )
        val refreshed = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(10_000) {
                events.directoryEvents.first { item ->
                    item.directory == directory &&
                        (item.event as? OpenCodeEvent.FormRefreshRequested)?.sessionID == session.id
                }
            }
        }
        try {
            val http = V2Http(url, fixtureClient(), json)
            val payload = buildJsonObject {
                put("title", "Review")
                putJsonArray("fields") {
                    add(
                        buildJsonObject {
                            put("key", "decision")
                            put("type", "string")
                            put("title", "Decision")
                            put("required", true)
                        },
                    )
                }
            }
            val created = http.request("POST", "api/session/${session.id}/form", body = payload)
            assertEquals(session.id, (refreshed.await().event as OpenCodeEvent.FormRefreshRequested).sessionID)
            val formId = created.jsonObject.getValue("data").jsonObject.getValue("id").jsonPrimitive.content
            val forms = api.forms
            assertTrue(forms.listSessionForms(session.id, directory).any { it.id == formId })
            assertEquals("pending", forms.getSessionForm(session.id, formId, directory).state?.status)
            forms.reply(session.id, formId, buildJsonObject { put("decision", "approved") }, directory)
            assertEquals("answered", forms.getSessionForm(session.id, formId, directory).state?.status)
            assertTrue(forms.listSessionForms(session.id, directory).none { it.id == formId })
        } finally {
            refreshed.cancel()
            assertTrue(api.deleteSession(session.id, directory, null))
        }
    }

    @Test
    fun `server accepts the app answer for a hidden required field with a default`() = runBlocking {
        withLiveFormSession {
            val form = create(
                "Hidden default",
                buildJsonArray {
                    add(
                        formField("token", "string") {
                            put("required", true)
                            put("hidden", true)
                            put("default", "preset-token")
                        },
                    )
                    add(formField("note", "string"))
                },
            )
            val answer = resolveForm(form.fields, initialDrafts(form.fields)).answer
            assertEquals(buildJsonObject { put("token", "preset-token") }, answer)
            assertRejected(form, JsonObject(answer - "token"))
            assertSettled(form, answer)
        }
    }

    @Test
    fun `server accepts the app answer for chained conditions behind an inactive controller`() = runBlocking {
        withLiveFormSession {
            val fields = buildJsonArray {
                add(formField("enable", "boolean") { put("default", false) })
                add(formField("mode", "string") { put("when", whenEq("enable", JsonPrimitive(true))) })
                add(
                    formField("detail", "string") {
                        put("required", true)
                        put("when", whenEq("mode", JsonPrimitive("advanced")))
                    },
                )
            }
            val retained = mapOf("mode" to JsonPrimitive("advanced"), "detail" to JsonPrimitive("leftover"))

            val inactive = create("Chained inactive", fields)
            val inactiveAnswer = resolveForm(inactive.fields, initialDrafts(inactive.fields) + retained).answer
            assertEquals(buildJsonObject { put("enable", false) }, inactiveAnswer)
            // The pre-fix answer: the dependent was activated by its inactive controller's retained draft.
            assertRejected(inactive, JsonObject(inactiveAnswer + ("detail" to JsonPrimitive("leftover"))))
            assertSettled(inactive, inactiveAnswer)

            val active = create("Chained active", fields)
            val activeDrafts = initialDrafts(active.fields) + retained + ("enable" to JsonPrimitive(true))
            val activeAnswer = resolveForm(active.fields, activeDrafts).answer
            assertEquals(setOf("enable", "mode", "detail"), activeAnswer.keys)
            assertSettled(active, activeAnswer)
        }
    }

    @Test
    fun `server accepts the app answer once an external step is acknowledged`() = runBlocking {
        withLiveFormSession {
            val form = create(
                "External step",
                buildJsonArray {
                    add(formField("authorize", "external") { put("url", "https://example.com/authorize") })
                },
            )
            val unacknowledged = resolveForm(form.fields, initialDrafts(form.fields)).answer
            assertTrue(unacknowledged.isEmpty())
            assertRejected(form, unacknowledged)
            val acknowledged = resolveForm(form.fields, mapOf("authorize" to JsonPrimitive(true))).answer
            assertEquals(buildJsonObject { put("authorize", true) }, acknowledged)
            assertSettled(form, acknowledged)
        }
    }

    @Suppress("LongMethod")
    @Test
    fun `create list and reply to a live v2 permission request`() = runBlocking {
        connect()
        val api = manager.requireApi()
        val events = manager.getEventSource() ?: error("Expected an event source")
        withTimeout(10_000) { events.connectionState.first { it == ConnectionState.Connected } }
        val http = V2Http(url, fixtureClient(), json)
        val resource = "printf 'P4OC_PERMISSION_${UUID.randomUUID()}'"
        val sessionResponse = http.request(
            "POST",
            "api/session",
            body = buildJsonObject {
                put("title", "P4OC permission integration ${UUID.randomUUID()}")
                put("location", buildJsonObject { put("directory", directory) })
                putJsonArray("permissions") {
                    add(
                        buildJsonObject {
                            put("action", "bash")
                            put("resource", "*")
                            put("effect", "ask")
                        },
                    )
                }
            },
        )
        val sessionId = sessionResponse.jsonObject.getValue("data").jsonObject
            .getValue("id").jsonPrimitive.content
        val requested = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(10_000) {
                events.directoryEvents.first { item ->
                    item.directory == directory &&
                        (item.event as? OpenCodeEvent.PermissionRequested)?.permission?.sessionID == sessionId
                }
            }
        }
        try {
            val created = http.request(
                "POST",
                "api/session/$sessionId/permission",
                body = buildJsonObject {
                    put("action", "bash")
                    putJsonArray("resources") { add(JsonPrimitive(resource)) }
                },
            ).jsonObject.getValue("data").jsonObject
            assertEquals("ask", created.getValue("effect").jsonPrimitive.content)
            val requestId = created.getValue("id").jsonPrimitive.content
            val permissionEvent = requested.await().event as OpenCodeEvent.PermissionRequested
            assertEquals(requestId, permissionEvent.permission.id)
            assertEquals("bash", permissionEvent.permission.type)
            assertEquals(listOf(resource), permissionEvent.permission.patterns)

            val pending = api.listSessionPermissionsV2(sessionId).data.single { it.id == requestId }
            assertTrue(api.listPermissions(directory, null).any { it.id == requestId && it.sessionID == sessionId })
            assertEquals(sessionId, pending.sessionID)
            assertEquals("bash", pending.action)
            assertEquals(listOf(resource), pending.resources)

            val replied = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(10_000) {
                    events.directoryEvents.first { item ->
                        item.directory == directory &&
                            (item.event as? OpenCodeEvent.PermissionReplied)?.let {
                                it.sessionID == sessionId && it.requestID == requestId
                            } == true
                    }
                }
            }
            assertTrue(
                api.respondToPermissionV2(sessionId, requestId, PermissionResponseRequest(reply = "once")).isSuccessful,
            )
            assertEquals("once", (replied.await().event as OpenCodeEvent.PermissionReplied).reply)
            assertTrue(api.listSessionPermissionsV2(sessionId).data.none { it.id == requestId })
        } finally {
            requested.cancel()
            assertTrue(api.deleteSession(sessionId, directory, null))
        }
        assertSessionDeleted(api, sessionId)
    }

    @Test
    fun `add and remove a disabled v2 MCP server`() = runBlocking {
        connect()
        val api = manager.requireApi() as V2WorkspaceOpenCodeApi
        val http = V2Http(url, fixtureClient(), json)
        val name = "p4oc-live-${UUID.randomUUID().toString().replace("-", "")}"
        val query = mapOf("location[directory]" to directory)
        assertTrue(api.getMcpStatus(directory, null).none { it.key == name })
        try {
            val added = api.addMcpServer(
                AddMcpServerRequest(
                    name = name,
                    config = McpConfigDto(
                        type = "local",
                        command = listOf("node", "-e", "process.exit(0)"),
                        enabled = false,
                    ),
                ),
                directory,
                null,
            )
            assertEquals("disabled", added[name]?.status)
            assertEquals("disabled", api.getMcpStatus(directory, null)[name]?.status)
        } finally {
            if (api.getMcpStatus(directory, null).containsKey(name)) {
                http.request("DELETE", "api/experimental/mcp/$name", query)
            }
            assertTrue(api.getMcpStatus(directory, null).none { it.key == name })
        }
    }

    @Test
    fun `load v2 agent model and command catalogs`() = runBlocking {
        connect()
        val api = manager.requireApi()
        assertTrue(api.getAgents(directory, null).isNotEmpty())
        assertTrue(api.getProviders(directory, null).all.isNotEmpty())
        api.getConfig(directory, null)
        api.listCommands(directory, null)
        Unit
    }

    @Suppress("LongMethod")
    @Test
    fun `dispatch v2 init in a disposable project and clean it up`() = runBlocking {
        val model = System.getenv("OPENCODE_V2_MODEL").orEmpty()
        assumeTrue("Set OPENCODE_V2_MODEL to run live command execution", model.isNotBlank())
        val fileDirectory = System.getenv("OPENCODE_V2_FILE_DIRECTORY").orEmpty()
        assumeTrue("Set OPENCODE_V2_FILE_DIRECTORY to an isolated writable directory", fileDirectory.isNotBlank())
        val initDirectory = System.getenv("OPENCODE_V2_INIT_DIRECTORY").orEmpty()
        assumeTrue(
            "Set OPENCODE_V2_INIT_DIRECTORY to a writable directory outside the project's agent instructions",
            initDirectory.isNotBlank(),
        )
        connect()
        val api = manager.requireApi() as V2WorkspaceOpenCodeApi
        assumeTrue(
            "The live v2 command catalog must include init",
            api.listCommands(directory, null).any { it.name == "init" },
        )
        val events = manager.getEventSource() ?: error("Expected an event source")
        withTimeout(10_000) { events.connectionState.first { it == ConnectionState.Connected } }
        val bootstrap = api.createSession(
            directory,
            null,
            CreateSessionRequest(title = "P4OC init fixture ${UUID.randomUUID()}"),
        )
        val repo = "${initDirectory.trimEnd('/')}/p4oc-v2-init-${UUID.randomUUID()}"
        try {
            val path = shellQuote(repo)
            val sourceDirectory = shellQuote("$repo/src")
            val readme = shellQuote("$repo/README.md")
            val source = shellQuote("$repo/src/index.ts")
            val readmeContents = shellQuote("# P4OC init fixture\n\nA tiny disposable project.\n")
            val sourceContents = shellQuote("export const initFixture = true;\n")
            api.executeShellForOutput(
                bootstrap.id,
                "mkdir -p -- $path $sourceDirectory && " +
                    "printf '%s' $readmeContents > $readme && " +
                    "printf '%s' $sourceContents > $source && " +
                    "git -C $path init -q && " +
                    "git -C $path config user.email p4oc@example.invalid && " +
                    "git -C $path config user.name P4OC && " +
                    "git -C $path add README.md src/index.ts && " +
                    "git -C $path commit -qm initial",
            )
            assertTrue(api.listFiles(".", repo, null).none { it.name == "AGENTS.md" })
            val session = api.createSession(
                repo,
                null,
                CreateSessionRequest(title = "P4OC init command ${UUID.randomUUID()}"),
            )
            try {
                assertTrue(api.listCommands(repo, null).any { it.name == "init" })
                val pendingForms = async(start = CoroutineStart.UNDISPATCHED) {
                    answerPendingInitForms(api.forms, session.id, repo)
                }
                val finished = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(120_000) {
                        events.directoryEvents.first { item ->
                            item.directory == repo &&
                                (item.event as? OpenCodeEvent.V2ContentChanged)?.let { frame ->
                                    frame.sessionID == session.id && frame.kind == "text" && frame.phase == "ended"
                                } == true
                        }
                    }
                }
                val active = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(10_000) {
                        var status = api.getSessionStatuses(null, null)[session.id]
                        while (status == null) {
                            delay(50)
                            status = api.getSessionStatuses(null, null)[session.id]
                        }
                        checkNotNull(status)
                    }
                }
                try {
                    assertTrue(
                        api.initSession(
                            session.id,
                            InitSessionRequest(
                                messageID = "msg_${UUID.randomUUID()}",
                                providerID = "opencode",
                                modelID = model,
                            ),
                            repo,
                            null,
                        ),
                    )
                    assertEquals("busy", active.await().type)
                    val ended = finished.await().event as OpenCodeEvent.V2ContentChanged
                    assertTrue("Expected an assistant response from /init", ended.text.isNotBlank())
                    val persisted = withTimeout(15_000) {
                        var text: String? = null
                        while (text != ended.text) {
                            text = api.getMessages(session.id, 50, null, repo, null)
                                .flatMap { it.parts }
                                .firstOrNull { it.id == ended.partID }?.text
                            if (text != ended.text) delay(200)
                        }
                        text
                    }
                    assertEquals(ended.text, persisted)
                    assertEquals(model, api.getSession(session.id, repo, null).model?.id)
                    withTimeout(180_000) {
                        while (session.id in api.getSessionStatuses(null, null)) delay(200)
                    }
                    assertTrue(
                        "Expected /init to create AGENTS.md in the disposable project",
                        api.listFiles(".", repo, null).any { it.name == "AGENTS.md" && it.type == "file" },
                    )
                    api.readFileRaw("AGENTS.md", repo, null).body()!!.use { body ->
                        assertTrue("Expected /init to write agent instructions", body.string().isNotBlank())
                    }
                } finally {
                    pendingForms.cancel()
                    finished.cancel()
                    active.cancel()
                    if (session.id in api.getSessionStatuses(null, null)) {
                        assertTrue(api.abortSession(session.id, repo, null).isSuccessful)
                        withTimeout(15_000) {
                            while (session.id in api.getSessionStatuses(null, null)) delay(100)
                        }
                    }
                }
            } finally {
                assertTrue(api.deleteSession(session.id, repo, null))
            }
            assertSessionDeleted(api, session.id)
        } finally {
            try {
                removeFixture(api, bootstrap.id, repo)
                assertTrue(api.listFiles(".", initDirectory, null).none { it.name == repo.substringAfterLast('/') })
            } finally {
                assertTrue(api.deleteSession(bootstrap.id, directory, null))
            }
        }
        assertSessionDeleted(api, bootstrap.id)
    }

    @Test
    fun `resolve v2 search and changed Git status in the selected location`() = runBlocking {
        val fileDirectory = System.getenv("OPENCODE_V2_FILE_DIRECTORY").orEmpty()
        assumeTrue("Set OPENCODE_V2_FILE_DIRECTORY to a writable directory", fileDirectory.isNotBlank())
        connect()
        val api = manager.requireApi() as V2WorkspaceOpenCodeApi
        assertTrue(api.searchFiles("README", fileDirectory, null).any { it.endsWith("README.md") })
        val session = api.createSession(directory, null, CreateSessionRequest(title = "P4OC Git integration"))
        val repo = "${fileDirectory.trimEnd('/').substringBeforeLast('/')}/p4oc-v2-git-${UUID.randomUUID()}"
        try {
            val path = shellQuote(repo)
            val proof = shellQuote("$repo/proof.txt")
            api.executeShellForOutput(
                session.id,
                "mkdir -p -- $path && git -C $path init -q && " +
                    "git -C $path config user.email p4oc@example.invalid && " +
                    "git -C $path config user.name P4OC && " +
                    "printf 'original\\n' > $proof && git -C $path add proof.txt && " +
                    "git -C $path commit -qm initial && printf 'modified\\n' > $proof",
            )
            assertTrue(api.searchFiles("proof", repo, null).any { it.endsWith("proof.txt") })
            val status = api.getFileStatus(repo, null).single { it.path == "proof.txt" }
            assertEquals("modified", status.status)
            assertEquals(1, status.added)
            assertEquals(1, status.removed)
            assertTrue(api.getFileStatus(fileDirectory, null).none { it.path == "proof.txt" })
            val workspaceClient = WorkspaceClient(
                workspace = Workspace(ServerRef.fromEndpoint(url, "P4OC live"), repo),
                generation = ServerGeneration(1L),
                apiProvider = ActiveServerApiProvider { _, _ -> api },
                connectionState = MutableStateFlow(ConnectionState.Connected),
            )
            val changes = WorkspaceChangesRepositoryImpl(workspaceClient)
            val snapshot = changes.loadSnapshot() as WorkspaceChangesResult.Success
            val modified = snapshot.data.changes.single { it.file == "proof.txt" }
            assertEquals(1L, modified.additions)
            assertEquals(1L, modified.deletions)
            val patches = changes.loadDiff() as WorkspaceChangesResult.Success
            val patch = patches.data["proof.txt"] as WorkspacePatch.Content
            assertTrue(patch.text.contains("-original"))
            assertTrue(patch.text.contains("+modified"))
        } finally {
            try {
                removeFixture(api, session.id, repo)
            } finally {
                assertTrue(api.deleteSession(session.id, directory, null))
            }
        }
    }

    @Test
    fun `summarize a live v2 session and observe completed compaction`() = runBlocking {
        val model = System.getenv("OPENCODE_V2_MODEL").orEmpty()
        assumeTrue("Set OPENCODE_V2_MODEL to run live compaction", model.isNotBlank())
        connect()
        val api = manager.requireApi()
        val http = V2Http(url, fixtureClient(), json)
        val session = api.createSession(
            directory,
            null,
            CreateSessionRequest(title = "P4OC compact ${UUID.randomUUID()}"),
        )
        try {
            // Pin the configured model: compaction runs on the session's model, and the server default can be retired.
            api.sendMessageAsync(
                session.id,
                SendMessageRequest(
                    agent = "Build",
                    model = ModelInput("opencode", model),
                    parts = listOf(PartInputDto(type = "text", text = "The sky is blue. Reply only: acknowledged.")),
                ),
                directory,
                null,
            )
            withTimeout(90_000) {
                while (session.id !in api.getSessionStatuses(null, null)) delay(50)
                while (session.id in api.getSessionStatuses(null, null)) delay(200)
            }
            assertTrue(api.summarizeSession(session.id, directory, null))
            withTimeout(120_000) {
                while (true) {
                    val messages = http.request("GET", "api/session/${session.id}/message")
                        .jsonObject.getValue("data") as kotlinx.serialization.json.JsonArray
                    val compaction = messages.map { it.jsonObject }.firstOrNull {
                        it["type"]?.jsonPrimitive?.content == "compaction"
                    }
                    val status = compaction?.get("status")?.jsonPrimitive?.content
                    check(status != "failed") { "Compaction failed: ${compaction?.get("error")}" }
                    if (status == "completed") {
                        assertTrue(compaction["summary"]?.jsonPrimitive?.content?.isNotBlank() == true)
                        break
                    }
                    delay(200)
                }
            }
        } finally {
            if (session.id in api.getSessionStatuses(null, null)) {
                api.abortSession(session.id, directory, null)
            }
            assertTrue(api.deleteSession(session.id, directory, null))
        }
    }

    @Test
    fun `mutate files through the v2 workspace OFISH repository`() = runBlocking {
        val fileDirectory = System.getenv("OPENCODE_V2_FILE_DIRECTORY").orEmpty()
        assumeTrue("Set OPENCODE_V2_FILE_DIRECTORY to a writable directory", fileDirectory.isNotBlank())
        connect()
        val api = manager.requireApi() as V2WorkspaceOpenCodeApi
        val session = api.createSession(directory, null, CreateSessionRequest(title = "P4OC OFISH integration"))
        val repo = "${fileDirectory.trimEnd('/')}/p4oc-v2-ofish-${UUID.randomUUID()}"
        try {
            api.executeShellForOutput(session.id, "mkdir -p -- ${shellQuote(repo)}")
            val client = WorkspaceClient(
                workspace = Workspace(ServerRef.fromEndpoint(url, "P4OC live"), repo),
                generation = ServerGeneration(1L),
                apiProvider = ActiveServerApiProvider { _, _ -> api },
                connectionState = MutableStateFlow(ConnectionState.Connected),
            )
            val files = FileRepositoryFactory.create(client)
            assertTrue(files.capabilities().canWrite)
            assertTrue(files.createDirectory("nested") is FileOperationResult.Ok)
            assertTrue(files.writeFile(FileWriteRequest("nested/proof.txt", "before\n")) is FileOperationResult.Ok)
            val original = files.readFile("nested/proof.txt") as FileOperationResult.Ok
            assertEquals("before\n", original.data.content)
            val baseline = requireNotNull(original.data.hash)
            val updated = files.writeFile(FileWriteRequest("nested/proof.txt", "after\n", baseline))
            assertTrue(updated is FileOperationResult.Ok)
            val stale = files.writeFile(FileWriteRequest("nested/proof.txt", "stale\n", baseline))
            assertTrue(stale is FileOperationResult.Conflict)
            assertTrue(files.renameFile("nested/proof.txt", "nested/renamed.txt") is FileOperationResult.Ok)
            assertEquals("after\n", (files.readFile("nested/renamed.txt") as FileOperationResult.Ok).data.content)
            val upload = "uploaded through v2 OFISH\n".toByteArray()
            assertTrue(
                files.uploadFile(
                    FileUploadRequest(
                        path = "nested/upload.txt",
                        contentLength = upload.size.toLong(),
                        openStream = { ByteArrayInputStream(upload) },
                        createOnly = true,
                    ),
                ) is FileOperationResult.Ok,
            )
            val uploadedFile = files.readFile("nested/upload.txt") as FileOperationResult.Ok
            assertEquals("uploaded through v2 OFISH\n", uploadedFile.data.content)
            assertTrue(files.deleteFile("nested/upload.txt") is FileOperationResult.Ok)
            assertTrue(files.deleteFile("nested/renamed.txt") is FileOperationResult.Ok)
        } finally {
            try {
                removeFixture(api, session.id, repo)
            } finally {
                assertTrue(api.deleteSession(session.id, directory, null))
            }
        }
    }

    @Test
    fun `connect a v2 terminal with a fresh ticket`() = runBlocking {
        connect()
        val api = manager.requireApi()
        val pty = api.createPtySession(directory, null, CreatePtyRequest(command = "/bin/sh", cwd = directory))
        var socket: WebSocket? = null
        try {
            assertEquals(pty.id, api.getPtySession(pty.id, directory, null).id)
            assertTrue(api.listPtySessions(directory, null).any { it.id == pty.id })
            val title = "P4OC terminal ${UUID.randomUUID()}"
            assertEquals(title, api.updatePtySession(pty.id, directory, null, UpdatePtyRequest(title = title)).title)
            assertEquals(title, api.getPtySession(pty.id, directory, null).title)
            val client = fixtureClient()
            val ticketRequest = buildV2PtyConnectTokenRequest(url, pty.id, directory, null)
            val ticket = client.newCall(ticketRequest).execute().use { response ->
                assertTrue(response.isSuccessful)
                parseV2PtyConnectTicket(response.body.string())
            }
            val received = CountDownLatch(1)
            val failure = AtomicReference<String?>(null)
            val marker = "P4OC_V2_TERMINAL_OK"
            socket = client.newWebSocket(
                Request.Builder().url(buildV2PtyWebSocketUrl(url, pty.id, directory, null, ticket)).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send("printf '%s%s\\n' P4OC_V2_ TERMINAL_OK\n")
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (marker in text) received.countDown()
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        failure.set(t.message)
                        received.countDown()
                    }
                },
            )
            assertTrue("PTY did not return command output: ${failure.get()}", received.await(10, TimeUnit.SECONDS))
            assertEquals(null, failure.get())
        } finally {
            socket?.close(1000, "Done")
            assertTrue(api.deletePtySession(pty.id, directory, null))
        }
        assertTrue(api.listPtySessions(directory, null).none { it.id == pty.id })
    }

    private suspend fun assertSessionDeleted(api: OpenCodeApi, id: String) {
        val error = runCatching { api.getSession(id, directory, null) }.exceptionOrNull()
        assertTrue(
            "Expected deleted session $id to return HTTP 404, got $error",
            (error as? HttpException)?.code() == HTTP_NOT_FOUND,
        )
    }

    private suspend fun answerPendingInitForms(forms: V2Forms, sessionId: String, directory: String) {
        while (true) {
            forms.listSessionForms(sessionId, directory).forEach { form ->
                val answer = buildJsonObject {
                    form.fields.forEach { field ->
                        val recommended = requireNotNull(field.options?.firstOrNull()) {
                            "The disposable /init fixture received an unanswered free-text question"
                        }
                        put(field.key, recommended.value)
                    }
                }
                forms.reply(sessionId, form.id, answer, directory)
            }
            delay(500)
        }
    }

    private suspend fun withLiveFormSession(block: suspend LiveFormSession.() -> Unit) {
        connect()
        val api = manager.requireApi() as V2WorkspaceOpenCodeApi
        val session = api.createSession(
            directory,
            null,
            CreateSessionRequest(title = "P4OC form semantics ${UUID.randomUUID()}"),
        )
        try {
            LiveFormSession(api.forms, session.id, V2Http(url, fixtureClient(), json)).block()
        } finally {
            assertTrue(api.deleteSession(session.id, directory, null))
        }
    }

    private inner class LiveFormSession(
        private val forms: V2Forms,
        private val sessionId: String,
        private val http: V2Http,
    ) {
        suspend fun create(title: String, fields: JsonArray): V2FormInfo {
            val payload = buildJsonObject {
                put("title", title)
                put("fields", fields)
            }
            val created = http.request("POST", "api/session/$sessionId/form", body = payload)
            val formId = created.jsonObject.getValue("data").jsonObject.getValue("id").jsonPrimitive.content
            return forms.getSessionForm(sessionId, formId, directory)
        }

        suspend fun assertRejected(form: V2FormInfo, answer: JsonObject) {
            val error = runCatching { forms.reply(sessionId, form.id, answer, directory) }.exceptionOrNull()
            assertTrue("Expected the server to reject $answer, got $error", error is HttpException)
            assertEquals("pending", forms.getSessionForm(sessionId, form.id, directory).state?.status)
        }

        suspend fun assertSettled(form: V2FormInfo, answer: JsonObject) {
            forms.reply(sessionId, form.id, answer, directory)
            val state = forms.getSessionForm(sessionId, form.id, directory).state
            assertEquals("answered", state?.status)
            assertEquals(answer, state?.answer)
        }
    }

    private fun formField(key: String, type: String, configure: JsonObjectBuilder.() -> Unit = {}) =
        buildJsonObject {
            put("key", key)
            put("type", type)
            configure()
        }

    private fun whenEq(key: String, value: JsonPrimitive) = buildJsonArray {
        add(
            buildJsonObject {
                put("key", key)
                put("op", "eq")
                put("value", value)
            },
        )
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    /**
     * Removes a disposable fixture directory. On NFS the server can hold deleted files (`.nfs*` placeholders,
     * watched `.git` entries) for several seconds, so retry for up to 30s before treating it as a failure.
     */
    private suspend fun removeFixture(api: V2WorkspaceOpenCodeApi, sessionId: String, path: String) {
        api.executeShellForOutput(
            sessionId,
            "for attempt in $(seq 1 30); do rm -rf -- ${shellQuote(path)} && break; sleep 1; done; " +
                "test ! -e ${shellQuote(path)}",
        )
    }

    private fun fixtureClient() = OkHttpClient.Builder().addInterceptor { chain ->
        val authorized = chain.request().newBuilder().header(
            "Authorization",
            Credentials.basic(
                System.getenv("OPENCODE_V2_USERNAME") ?: "opencode",
                System.getenv("OPENCODE_V2_PASSWORD") ?: "",
            ),
        ).build()
        chain.proceed(authorized)
    }.build()

    private suspend fun connect() = manager.connect(
        ServerConfig(url = url, username = System.getenv("OPENCODE_V2_USERNAME") ?: "opencode"),
        password = System.getenv("OPENCODE_V2_PASSWORD"),
    ).getOrThrow()
}
