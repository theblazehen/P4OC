package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.data.remote.dto.SessionStatusDto
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.fakes.FakeWorkspaceClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryImplTest {
    @Test
    fun `prewarm twice returns same Deferred`() = runTest {
        val client = FakeWorkspaceClient().apply {
            projects = listOf(FakeWorkspaceClient.projectDto("p1", "/repo/p1"))
            statusBlocker = CompletableDeferred()
        }
        val repository = SessionRepositoryImpl(client, nowMs = { testScheduler.currentTime }, dispatcher = StandardTestDispatcher(testScheduler))

        val first = repository.prewarm(client.projects)
        val second = repository.prewarm(client.projects)
        advanceUntilIdle()

        assertSame(first, second)
        assertEquals(listOf(null, "/repo/p1"), client.listSessionsDirectories)

        client.statusBlocker?.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `awaitOrFetch joins in-flight Deferred from prewarm`() = runTest {
        val client = FakeWorkspaceClient().apply {
            projects = listOf(FakeWorkspaceClient.projectDto("p1", "/repo/p1"), FakeWorkspaceClient.projectDto("p2", "/repo/p2"))
            statusBlocker = CompletableDeferred()
        }
        val repository = SessionRepositoryImpl(client, nowMs = { testScheduler.currentTime }, dispatcher = StandardTestDispatcher(testScheduler))

        val prewarm = repository.prewarm(client.projects)
        val awaiting = async { repository.awaitOrFetch() }
        advanceUntilIdle()

        assertFalse(awaiting.isCompleted)
        assertEquals(3, client.listSessionsCalls)
        assertEquals(3, client.getSessionStatusesCalls)
        assertEquals(0, client.listProjectsCalls)

        client.statusBlocker?.complete(Unit)
        assertEquals(prewarm.await().getOrNull(), awaiting.await().getOrNull())
    }

    @Test
    fun `awaitOrFetch cold start fetches projects`() = runTest {
        val client = FakeWorkspaceClient().apply {
            projects = listOf(FakeWorkspaceClient.projectDto("p1", "/repo/p1"))
        }
        val repository = SessionRepositoryImpl(client, nowMs = { testScheduler.currentTime }, dispatcher = StandardTestDispatcher(testScheduler))

        val result = repository.awaitOrFetch()

        assertTrue(result.isSuccess)
        assertEquals(1, client.listProjectsCalls)
        assertEquals(listOf(null, "/repo/p1"), client.listSessionsDirectories)
    }

    @Test
    fun `peek returns non-null within 30s and null after`() = runTest {
        val client = FakeWorkspaceClient().apply {
            projects = listOf(FakeWorkspaceClient.projectDto("p1", "/repo/p1"))
        }
        val repository = SessionRepositoryImpl(client, nowMs = { testScheduler.currentTime }, dispatcher = StandardTestDispatcher(testScheduler))

        repository.awaitOrFetch()
        advanceTimeBy(29_000)
        assertNotNull(repository.peek())

        advanceTimeBy(1_001)
        assertNull(repository.peek())
    }

    @Test
    fun `invalidate cancels in-flight and clears lastSuccess`() = runTest {
        val client = FakeWorkspaceClient().apply {
            projects = listOf(FakeWorkspaceClient.projectDto("p1", "/repo/p1"))
            statusBlocker = CompletableDeferred()
        }
        val repository = SessionRepositoryImpl(client, nowMs = { testScheduler.currentTime }, dispatcher = StandardTestDispatcher(testScheduler))

        val deferred = repository.prewarm(client.projects)
        advanceUntilIdle()
        repository.invalidate()

        assertTrue(deferred.isCancelled)
        assertNull(repository.peek())
    }

    @Test
    fun `empty projects seeds global session and status requests`() = runTest {
        val client = FakeWorkspaceClient()
        val repository = SessionRepositoryImpl(client, nowMs = { testScheduler.currentTime }, dispatcher = StandardTestDispatcher(testScheduler))

        val result = repository.prewarm(emptyList()).await()

        assertTrue(result.isSuccess)
        assertEquals(listOf(null), client.listSessionsDirectories)
        assertEquals(listOf(null), client.getSessionStatusesDirectories)
        assertEquals(0, client.listProjectsCalls)
    }

    @Test
    fun `semaphore bounds concurrent status requests to 10`() = runTest {
        val projects = (1..20).map { index -> FakeWorkspaceClient.projectDto("p$index", "/repo/$index") }
        val client = FakeWorkspaceClient().apply {
            this.projects = projects
            trackStatusConcurrency = true
        }
        val repository = SessionRepositoryImpl(client, nowMs = { testScheduler.currentTime }, dispatcher = StandardTestDispatcher(testScheduler))

        val result = repository.awaitOrFetch()

        assertTrue(result.isSuccess)
        assertTrue(client.maxObservedStatusConcurrency <= 10)
    }

    @Test
    fun `global sessions are deduped when project session has same id`() = runTest {
        val global = FakeWorkspaceClient.sessionDto(id = "same", directory = "/global", updatedAt = 1L)
        val project = FakeWorkspaceClient.sessionDto(id = "same", directory = "/repo/p1", updatedAt = 2L)
        val client = FakeWorkspaceClient().apply {
            projects = listOf(FakeWorkspaceClient.projectDto("p1", "/repo/p1"))
            sessionsByDirectory = mapOf(null to listOf(global), "/repo/p1" to listOf(project))
        }
        val repository = SessionRepositoryImpl(client, nowMs = { testScheduler.currentTime }, dispatcher = StandardTestDispatcher(testScheduler))

        repository.refresh()

        val sessions = repository.state.value.snapshot.sessions
        assertEquals(1, sessions.size)
        assertEquals("/repo/p1", sessions.getValue("same").session.directory)
    }

    @Test
    fun `SSE event during hydrate appears in final state`() = runTest {
        val client = FakeWorkspaceClient().apply {
            projects = listOf(FakeWorkspaceClient.projectDto("p1", "/repo/p1"))
            statusBlocker = CompletableDeferred()
        }
        val repository = SessionRepositoryImpl(client, nowMs = { testScheduler.currentTime }, dispatcher = StandardTestDispatcher(testScheduler))

        val prewarm = repository.prewarm(client.projects)
        advanceUntilIdle()
        repository.acceptEvent(OpenCodeEvent.SessionCreated(session("streamed")))
        client.statusBlocker?.complete(Unit)
        prewarm.await()

        assertTrue(repository.state.value.snapshot.sessions.containsKey("streamed"))
    }

    @Test
    fun `delete http failure refetches server truth instead of rollback map`() = runTest {
        val client = FakeWorkspaceClient().apply {
            projects = emptyList()
            setSessions(FakeWorkspaceClient.sessionDto(id = "s1", title = "Server Truth"))
            deleteSessionFailure = RuntimeException("HTTP 5xx")
        }
        val repository = SessionRepositoryImpl(client, nowMs = { testScheduler.currentTime }, dispatcher = StandardTestDispatcher(testScheduler))
        repository.refresh()

        try {
            repository.deleteSession(SessionId("s1"))
        } catch (_: RuntimeException) {
        }

        assertEquals(1, client.deleteSessionCalls)
        assertEquals("Server Truth", repository.state.value.snapshot.sessions.getValue("s1").session.title)
        assertTrue(repository.state.value is RepoState.Live)
    }

    @Test
    fun `hydrates statuses into snapshot`() = runTest {
        val client = FakeWorkspaceClient().apply {
            projects = emptyList()
            statusesByDirectory = mapOf(null to mapOf("s1" to SessionStatusDto(type = "busy")))
        }
        val repository = SessionRepositoryImpl(client, nowMs = { testScheduler.currentTime }, dispatcher = StandardTestDispatcher(testScheduler))

        repository.refresh()

        assertTrue(repository.state.value.snapshot.statuses.containsKey("s1"))
    }

    private fun session(id: String): Session = Session(
        id = id,
        projectID = "project-$id",
        directory = "/workspace",
        title = id,
        version = "1",
        createdAt = 1L,
        updatedAt = 1L,
    )
}
