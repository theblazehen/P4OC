package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import dev.blazelight.p4oc.data.remote.dto.ProjectTimeDto
import dev.blazelight.p4oc.data.remote.dto.SessionDto
import dev.blazelight.p4oc.data.remote.dto.SessionStatusDto
import dev.blazelight.p4oc.data.remote.dto.TimeDto
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDataCacheTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var connectionManager: ConnectionManager
    private lateinit var api: FakeOpenCodeApi
    private lateinit var connectionFlow: MutableStateFlow<Connection?>
    private lateinit var currentBaseUrlFlow: MutableStateFlow<String?>

    @Before
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.d(any(), any<String>()) } returns Unit
        every { AppLog.d(any(), any<() -> String>()) } returns Unit
        every { AppLog.v(any(), any<String>()) } returns Unit
        every { AppLog.v(any(), any<() -> String>()) } returns Unit
        every { AppLog.i(any(), any<String>()) } returns Unit
        every { AppLog.i(any(), any<() -> String>()) } returns Unit
        every { AppLog.w(any(), any<String>()) } returns Unit
        every { AppLog.w(any(), any<String>(), any()) } returns Unit
        every { AppLog.e(any(), any<String>()) } returns Unit
        every { AppLog.e(any(), any<String>(), any()) } returns Unit

        connectionManager = mockk(relaxed = true)
        api = FakeOpenCodeApi()
        currentBaseUrlFlow = MutableStateFlow("https://server-a")
        connectionFlow = MutableStateFlow(connectionFor(api, currentBaseUrlFlow.value!!))

        every { connectionManager.connection } returns connectionFlow
        every { connectionManager.currentBaseUrl } answers { currentBaseUrlFlow.value }
        every { connectionManager.getApi() } answers { if (currentBaseUrlFlow.value == null) null else api }
    }

    @After
    fun tearDown() {
        unmockkObject(AppLog)
    }

    @Test
    fun `prewarm twice returns same Deferred`() = runTest {
        val projects = listOf(project("p1", "/repo/p1"))
        api.statusBlocker = CompletableDeferred()
        val cache = SessionDataCache(
            connectionManager,
            nowMs = { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val first = cache.prewarm(projects)
        val second = cache.prewarm(projects)
        advanceUntilIdle()

        assertSame(first, second)
        assertEquals(listOf(null, "/repo/p1"), api.listSessionsCalls)

        api.statusBlocker?.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `awaitOrFetch joins in-flight Deferred from prewarm`() = runTest {
        val projects = listOf(project("p1", "/repo/p1"), project("p2", "/repo/p2"))
        api.statusBlocker = CompletableDeferred()
        val cache = SessionDataCache(
            connectionManager,
            nowMs = { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val prewarm = cache.prewarm(projects)
        val awaiting = async { cache.awaitOrFetch() }
        advanceUntilIdle()

        assertFalse(awaiting.isCompleted)
        assertEquals(3, api.listSessionsCalls.size)
        assertTrue(api.listSessionsCalls.containsAll(listOf(null, "/repo/p1", "/repo/p2")))
        assertEquals(3, api.getSessionStatusesCalls.size)
        assertTrue(api.getSessionStatusesCalls.containsAll(listOf(null, "/repo/p1", "/repo/p2")))
        assertEquals(0, api.listProjectsCallCount)

        api.statusBlocker?.complete(Unit)
        val prewarmResult = prewarm.await()
        val awaitResult = awaiting.await()

        assertEquals(prewarmResult.getOrNull(), awaitResult.getOrNull())
        assertEquals(1, api.listSessionsCalls.count { it == null })
        assertEquals(1, api.listSessionsCalls.count { it == "/repo/p1" })
        assertEquals(1, api.listSessionsCalls.count { it == "/repo/p2" })
    }

    @Test
    fun `awaitOrFetch cold start fetches`() = runTest {
        val projects = listOf(project("p1", "/repo/p1"))
        api.projects = projects
        val cache = SessionDataCache(
            connectionManager,
            nowMs = { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = cache.awaitOrFetch()

        assertTrue(result.isSuccess)
        assertEquals(1, api.listProjectsCallCount)
        assertEquals(listOf(null, "/repo/p1"), api.listSessionsCalls)
    }

    @Test
    fun `peek returns non-null within 30s same server`() = runTest {
        val projects = listOf(project("p1", "/repo/p1"))
        api.projects = projects
        val cache = SessionDataCache(
            connectionManager,
            nowMs = { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        cache.awaitOrFetch()
        advanceTimeBy(29_000)

        assertNotNull(cache.peek())
    }

    @Test
    fun `peek returns null after 30s`() = runTest {
        val projects = listOf(project("p1", "/repo/p1"))
        api.projects = projects
        val cache = SessionDataCache(
            connectionManager,
            nowMs = { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        cache.awaitOrFetch()
        advanceTimeBy(30_001)

        assertNull(cache.peek())
    }

    @Test
    fun `peek returns null on server switch`() = runTest {
        val projects = listOf(project("p1", "/repo/p1"))
        api.projects = projects
        val cache = SessionDataCache(
            connectionManager,
            nowMs = { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        cache.awaitOrFetch()
        currentBaseUrlFlow.value = "https://server-b"

        assertNull(cache.peek())
    }

    @Test
    fun `invalidate cancels in-flight and clears lastSuccess`() = runTest {
        val projects = listOf(project("p1", "/repo/p1"))
        api.statusBlocker = CompletableDeferred()
        val cache = SessionDataCache(
            connectionManager,
            nowMs = { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val deferred = cache.prewarm(projects)
        advanceUntilIdle()
        cache.invalidate()

        assertTrue(deferred.isCancelled)
        assertNull(cache.peek())
    }

    @Test
    fun `disconnect observer invalidates`() = runTest {
        val projects = listOf(project("p1", "/repo/p1"))
        api.projects = projects
        api.statusBlocker = CompletableDeferred()
        val cache = SessionDataCache(
            connectionManager,
            nowMs = { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val deferred = cache.prewarm(projects)
        advanceUntilIdle()
        connectionFlow.value = null
        currentBaseUrlFlow.value = null
        advanceUntilIdle()

        assertTrue(deferred.isCancelled)
        assertNull(cache.peek())
    }

    @Test
    fun `awaitOrFetch converts in-flight cancellation to Result_failure`() = runTest {
        val projects = listOf(project("p1", "/repo/p1"))
        api.statusBlocker = CompletableDeferred()
        val cache = SessionDataCache(
            connectionManager,
            nowMs = { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        cache.prewarm(projects)
        advanceUntilIdle()

        val awaiting = async { cache.awaitOrFetch() }
        advanceUntilIdle()

        cache.invalidate()
        advanceUntilIdle()

        val result = awaiting.await()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CancellationException)
    }

    @Test
    fun `server switch mid-fetch discards result`() = runTest {
        val projects = listOf(project("p1", "/repo/p1"))
        api.projects = projects
        api.beforeStatusRequest = { currentBaseUrlFlow.value = "https://server-b" }
        val cache = SessionDataCache(
            connectionManager,
            nowMs = { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = cache.awaitOrFetch()

        assertTrue(result.isFailure)
        assertNull(cache.peek())
    }

    @Test
    fun `empty projects seeds correctly`() = runTest {
        api.projects = emptyList()
        val cache = SessionDataCache(
            connectionManager,
            nowMs = { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = cache.prewarm(emptyList()).await()

        assertTrue(result.isSuccess)
        assertEquals(listOf(null), api.listSessionsCalls)
        assertEquals(listOf(null), api.getSessionStatusesCalls)
        assertEquals(0, api.listProjectsCallCount)
    }

    @Test
    fun `semaphore bounds concurrent to 10`() = runTest {
        val projects = (1..20).map { index -> project("p$index", "/repo/$index") }
        api.projects = projects
        api.trackStatusConcurrency = true
        val cache = SessionDataCache(
            connectionManager,
            nowMs = { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = cache.awaitOrFetch()

        assertTrue(result.isSuccess)
        assertTrue(api.maxObservedStatusConcurrency <= 10)
    }

    private fun project(id: String, worktree: String): ProjectDto = ProjectDto(
        id = id,
        worktree = worktree,
        time = ProjectTimeDto(created = 1L, initialized = 2L),
    )

    private fun session(id: String, directory: String): SessionDto = SessionDto(
        id = id,
        projectID = directory,
        directory = directory,
        title = id,
        version = "1",
        time = TimeDto(created = 1L, updated = 2L),
    )

    private fun connectionFor(api: OpenCodeApi, url: String): Connection {
        val eventSource = mockk<OpenCodeEventSource>(relaxed = true)
        every { eventSource.shutdown() } just runs
        return Connection(
            config = ServerConfig(url = url, name = "test", isLocal = false),
            api = api,
            eventSource = eventSource,
        )
    }

    private inner class FakeOpenCodeApi : OpenCodeApi {
        var projects: List<ProjectDto> = emptyList()
        val listSessionsCalls = mutableListOf<String?>()
        val getSessionStatusesCalls = mutableListOf<String?>()
        var listProjectsCallCount = 0
        var listSessionsBlocker: CompletableDeferred<Unit>? = null
        var statusBlocker: CompletableDeferred<Unit>? = null
        var beforeStatusRequest: (() -> Unit)? = null
        var trackStatusConcurrency: Boolean = false
        private val activeStatusCalls = AtomicInteger(0)
        var maxObservedStatusConcurrency: Int = 0

        override suspend fun health() = throw UnsupportedOperationException()

        override suspend fun listProjects(): List<ProjectDto> {
            listProjectsCallCount += 1
            return projects
        }

        override suspend fun getCurrentProject() = throw UnsupportedOperationException()
        override suspend fun getPath() = throw UnsupportedOperationException()
        override suspend fun getVcsInfo(directory: String?) = throw UnsupportedOperationException()

        override suspend fun listSessions(
            directory: String?,
            roots: Boolean?,
            start: Long?,
            search: String?,
            limit: Int?
        ): List<SessionDto> {
            listSessionsCalls += directory
            listSessionsBlocker?.await()
            return listOf(session(id = "session-${directory ?: "global"}", directory = directory ?: "/global"))
        }

        override suspend fun createSession(directory: String?, request: dev.blazelight.p4oc.data.remote.dto.CreateSessionRequest) = throw UnsupportedOperationException()
        override suspend fun getSession(id: String, directory: String?) = throw UnsupportedOperationException()
        override suspend fun deleteSession(id: String, directory: String?) = throw UnsupportedOperationException()
        override suspend fun updateSession(id: String, request: dev.blazelight.p4oc.data.remote.dto.UpdateSessionRequest, directory: String?) = throw UnsupportedOperationException()

        override suspend fun getSessionStatuses(directory: String?): Map<String, SessionStatusDto> {
            getSessionStatusesCalls += directory
            beforeStatusRequest?.invoke()
            statusBlocker?.await()
            if (trackStatusConcurrency) {
                val now = activeStatusCalls.incrementAndGet()
                maxObservedStatusConcurrency = maxOf(maxObservedStatusConcurrency, now)
                try {
                    kotlinx.coroutines.delay(1)
                } finally {
                    activeStatusCalls.decrementAndGet()
                }
            }
            return mapOf("session-${directory ?: "global"}" to SessionStatusDto(type = "idle"))
        }

        override suspend fun abortSession(id: String, directory: String?) = throw UnsupportedOperationException()
        override suspend fun forkSession(id: String, request: dev.blazelight.p4oc.data.remote.dto.ForkSessionRequest, directory: String?) = throw UnsupportedOperationException()
        override suspend fun getSessionChildren(id: String, directory: String?) = throw UnsupportedOperationException()
        override suspend fun getSessionTodos(id: String, directory: String?) = throw UnsupportedOperationException()
        override suspend fun initSession(id: String, request: dev.blazelight.p4oc.data.remote.dto.InitSessionRequest, directory: String?) = throw UnsupportedOperationException()
        override suspend fun shareSession(id: String, directory: String?) = throw UnsupportedOperationException()
        override suspend fun unshareSession(id: String, directory: String?) = throw UnsupportedOperationException()
        override suspend fun getSessionDiff(id: String, messageID: String?, directory: String?) = throw UnsupportedOperationException()
        override suspend fun summarizeSession(id: String, directory: String?) = throw UnsupportedOperationException()
        override suspend fun revertSession(id: String, request: dev.blazelight.p4oc.data.remote.dto.RevertSessionRequest, directory: String?) = throw UnsupportedOperationException()
        override suspend fun unrevertSession(id: String, directory: String?) = throw UnsupportedOperationException()
        override suspend fun getMessages(sessionId: String, limit: Int?, directory: String?) = throw UnsupportedOperationException()
        override suspend fun getMessage(sessionId: String, messageId: String, directory: String?) = throw UnsupportedOperationException()
        override suspend fun sendMessageAsync(sessionId: String, request: dev.blazelight.p4oc.data.remote.dto.SendMessageRequest, directory: String?) = throw UnsupportedOperationException()
        override suspend fun executeCommand(sessionId: String, request: dev.blazelight.p4oc.data.remote.dto.ExecuteCommandRequest, directory: String?) = throw UnsupportedOperationException()
        override suspend fun executeShellCommand(sessionId: String, request: dev.blazelight.p4oc.data.remote.dto.ShellCommandRequest, directory: String?) = throw UnsupportedOperationException()
        override suspend fun respondToPermission(requestId: String, request: dev.blazelight.p4oc.data.remote.dto.PermissionResponseRequest, directory: String?) = throw UnsupportedOperationException()
        override suspend fun respondToQuestion(requestId: String, request: dev.blazelight.p4oc.data.remote.dto.QuestionReplyRequest, directory: String?) = throw UnsupportedOperationException()
        override suspend fun listCommands(directory: String?) = throw UnsupportedOperationException()
        override suspend fun listFiles(path: String) = throw UnsupportedOperationException()
        override suspend fun readFile(path: String) = throw UnsupportedOperationException()
        override suspend fun getFileStatus() = throw UnsupportedOperationException()
        override suspend fun searchText(pattern: String) = throw UnsupportedOperationException()
        override suspend fun searchFiles(query: String, type: String?, limit: Int?) = throw UnsupportedOperationException()
        override suspend fun searchSymbols(query: String) = throw UnsupportedOperationException()
        override suspend fun getConfig() = throw UnsupportedOperationException()
        override suspend fun updateConfig(config: dev.blazelight.p4oc.data.remote.dto.ConfigDto) = throw UnsupportedOperationException()
        override suspend fun getProviders() = throw UnsupportedOperationException()
        override suspend fun getProviderAuthMethods() = throw UnsupportedOperationException()
        override suspend fun getAgents() = throw UnsupportedOperationException()
        override suspend fun setActiveModel(request: dev.blazelight.p4oc.data.remote.dto.SetActiveModelRequest) = throw UnsupportedOperationException()
        override suspend fun getLspStatus() = throw UnsupportedOperationException()
        override suspend fun getFormatterStatus() = throw UnsupportedOperationException()
        override suspend fun getMcpStatus() = throw UnsupportedOperationException()
        override suspend fun authorizeProvider(id: String) = throw UnsupportedOperationException()
        override suspend fun oauthCallback(id: String, request: dev.blazelight.p4oc.data.remote.dto.OAuthCallbackRequest) = throw UnsupportedOperationException()
        override suspend fun setAuth(id: String, auth: dev.blazelight.p4oc.data.remote.dto.AuthDto) = throw UnsupportedOperationException()
        override suspend fun disposeInstance() = throw UnsupportedOperationException()
        override suspend fun addMcpServer(request: dev.blazelight.p4oc.data.remote.dto.AddMcpServerRequest) = throw UnsupportedOperationException()
        override suspend fun log(request: dev.blazelight.p4oc.data.remote.dto.LogRequest) = throw UnsupportedOperationException()
        override suspend fun listPtySessions() = throw UnsupportedOperationException()
        override suspend fun createPtySession(request: dev.blazelight.p4oc.data.remote.dto.CreatePtyRequest) = throw UnsupportedOperationException()
        override suspend fun getPtySession(id: String) = throw UnsupportedOperationException()
        override suspend fun deletePtySession(id: String) = throw UnsupportedOperationException()
        override suspend fun updatePtySession(id: String, request: dev.blazelight.p4oc.data.remote.dto.UpdatePtyRequest) = throw UnsupportedOperationException()
        override suspend fun getToolIds() = throw UnsupportedOperationException()
        override suspend fun getTools(provider: String, model: String) = throw UnsupportedOperationException()
        override suspend fun getConfigProviders() = throw UnsupportedOperationException()
    }

    class MainDispatcherRule(
        private val dispatcher: TestDispatcher = StandardTestDispatcher(),
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
