package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.data.remote.dto.QuestionRequestDto
import dev.blazelight.p4oc.data.remote.dto.SessionDto
import dev.blazelight.p4oc.data.remote.dto.SessionRevertDto
import dev.blazelight.p4oc.data.remote.mapper.SessionMapper
import dev.blazelight.p4oc.data.workspace.SessionWorkspaceClient
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.fakes.FakeWorkspaceClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryTargetedRefreshTest {
    @Test
    fun `external rename and revert staging and clearing update the already observed session`() = runTest {
        val client = client()
        val repository = repository(client)
        repository.refresh()
        val ui = repository.sessionUiState(SessionId("s1"))
        val initial = SessionMapper.mapToDomain(client.getSessionResults.getValue("s1"))
        repository.acceptEvent(OpenCodeEvent.SessionUpdated(initial))
        val renamed = client.getSessionResults.getValue("s1").copy(
            title = "Renamed externally",
            revert = SessionRevertDto(messageID = "message-undo-boundary"),
        )
        client.getSessionResults["s1"] = renamed

        repository.acceptEvent(OpenCodeEvent.SessionRefreshRequested("s1"))
        advanceUntilIdle()

        assertEquals(renamed.title, ui.value.session?.title)
        assertEquals("message-undo-boundary", ui.value.session?.revert?.messageID)
        assertEquals(renamed.title, repository.state.value.snapshot.sessions.getValue("s1").session.title)
        client.getSessionResults["s1"] = renamed.copy(revert = null)
        repository.acceptEvent(OpenCodeEvent.SessionRefreshRequested("s1"))
        advanceUntilIdle()
        assertNull(ui.value.session?.revert)
        assertEquals(1, client.listProjectsCalls)
        repository.close()
    }

    @Test
    fun `a burst coalesces into one session fetch and no full hydrate`() = runTest {
        val client = client()
        val repository = repository(client)
        repeat(100) { repository.acceptEvent(OpenCodeEvent.SessionRefreshRequested("s1")) }

        advanceUntilIdle()

        assertEquals(1, client.getSessionCalls)
        assertEquals(0, client.listProjectsCalls)
        assertEquals(0, client.listSessionsCalls)
        assertEquals(0, client.getSessionStatusesCalls)
        assertEquals("Initial", repository.state.value.snapshot.sessions.getValue("s1").session.title)
        repository.close()
    }

    @Test
    fun `events during an in flight fetch rerun once without cancelling the fetch`() = runTest {
        val base = client()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        var completed = 0
        val client = object : SessionWorkspaceClient by base {
            override suspend fun getSession(id: String): SessionDto {
                calls++
                val fetched = base.getSessionResults.getValue(id)
                if (calls == 1) release.await()
                completed++
                return fetched
            }
        }
        val repository = repository(client)
        val ui = repository.sessionUiState(SessionId("s1"))
        repository.acceptEvent(OpenCodeEvent.SessionRefreshRequested("s1"))
        advanceUntilIdle()
        base.getSessionResults["s1"] = base.getSessionResults.getValue("s1").copy(title = "Latest")
        repeat(100) { repository.acceptEvent(OpenCodeEvent.SessionRefreshRequested("s1")) }
        advanceUntilIdle()
        assertEquals(1, calls)

        release.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, calls)
        assertEquals(2, completed)
        assertEquals("Latest", ui.value.session?.title)
        assertEquals(0, base.listProjectsCalls)
        repository.close()
    }

    @Test
    fun `refreshes for separate sessions do not cancel each other`() = runTest {
        val client = client().apply {
            getSessionResults["s2"] = FakeWorkspaceClient.sessionDto("s2", title = "Other")
        }
        val repository = repository(client)
        repository.acceptEvent(OpenCodeEvent.SessionRefreshRequested("s1"))
        repository.acceptEvent(OpenCodeEvent.SessionRefreshRequested("s2"))
        advanceUntilIdle()

        assertEquals(2, client.getSessionCalls)
        assertEquals(setOf("s1", "s2"), repository.state.value.snapshot.sessions.keys)
        repository.close()
    }

    @Test
    fun `shutdown recovery survives a following session refresh and reconciles pending questions`() = runTest {
        val base = client().apply { statusBlocker = CompletableDeferred() }
        var questionFetches = 0
        val client = object : SessionWorkspaceClient by base {
            override suspend fun listSessionQuestions(sessionId: String): List<QuestionRequestDto> {
                questionFetches++
                return emptyList()
            }
        }
        val repository = repository(client)
        repository.sessionUiState(SessionId("s1"))
        repository.acceptEvent(OpenCodeEvent.LocationShutdown(base.workspace.directory))
        runCurrent()
        repository.acceptEvent(OpenCodeEvent.SessionRefreshRequested("s1"))
        advanceUntilIdle()
        assertEquals(0, questionFetches)

        base.statusBlocker?.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, questionFetches)
        assertEquals(1, base.listProjectsCalls)
        assertEquals(1, base.getSessionCalls)
        assertTrue(repository.state.value is RepoState.Live)
        repository.close()
    }

    @Test
    fun `not found refresh removes the session from the catalog`() = runTest {
        val client = client()
        val repository = repository(client)
        repository.refresh()
        client.getSessionFailure = HttpException(Response.error<SessionDto>(404, "gone".toResponseBody()))

        repository.acceptEvent(OpenCodeEvent.SessionRefreshRequested("s1"))
        advanceUntilIdle()

        assertFalse(repository.state.value.snapshot.sessions.containsKey("s1"))
        assertEquals(1, client.getSessionCalls)
        assertEquals(1, client.listProjectsCalls)
        repository.close()
    }

    @Test
    fun `server failures preserve existing session metadata`() = runTest {
        val client = client()
        val repository = repository(client)
        repository.refresh()
        client.getSessionFailure = HttpException(Response.error<SessionDto>(503, "unavailable".toResponseBody()))
        repository.acceptEvent(OpenCodeEvent.SessionRefreshRequested("s1"))
        advanceUntilIdle()

        assertEquals("Initial", repository.state.value.snapshot.sessions.getValue("s1").session.title)
        repository.close()
    }

    @Test
    fun `deleted session cannot be resurrected by a late non cancellable refresh response`() = runTest {
        val base = client()
        val release = CompletableDeferred<Unit>()
        val client = object : SessionWorkspaceClient by base {
            override suspend fun getSession(id: String): SessionDto = withContext(NonCancellable) {
                release.await()
                base.getSessionResults.getValue(id)
            }
        }
        val repository = repository(client)
        repository.refresh()
        repository.acceptEvent(OpenCodeEvent.SessionRefreshRequested("s1"))
        advanceUntilIdle()
        repository.acceptEvent(OpenCodeEvent.SessionRefreshRequested("s1", removed = true))
        release.complete(Unit)
        advanceUntilIdle()

        assertFalse(repository.state.value.snapshot.sessions.containsKey("s1"))
        repository.close()
    }

    @Test
    fun `deleted transient session cancels its pending refresh without fetching`() = runTest {
        val client = client()
        val repository = repository(client)
        repository.refresh()
        repository.acceptEvent(OpenCodeEvent.SessionRefreshRequested("s1"))
        repository.acceptEvent(OpenCodeEvent.SessionRefreshRequested("s1", removed = true))
        advanceUntilIdle()

        assertFalse(repository.state.value.snapshot.sessions.containsKey("s1"))
        assertEquals(0, client.getSessionCalls)
        repository.close()
    }

    @Test
    fun `targeted metadata survives a concurrent full hydrate with stale list results`() = runTest {
        val client = client().apply { statusBlocker = CompletableDeferred() }
        val repository = repository(client)
        val ui = repository.sessionUiState(SessionId("s1"))
        repository.prewarm(emptyList())
        runCurrent()
        client.getSessionResults["s1"] = client.getSessionResults.getValue("s1").copy(title = "Latest")
        repository.acceptEvent(OpenCodeEvent.SessionRefreshRequested("s1"))
        advanceUntilIdle()
        assertEquals("Latest", ui.value.session?.title)
        assertEquals("Latest", repository.state.value.snapshot.sessions.getValue("s1").session.title)

        client.statusBlocker?.complete(Unit)
        advanceUntilIdle()

        assertEquals("Latest", repository.state.value.snapshot.sessions.getValue("s1").session.title)
        assertEquals("Latest", ui.value.session?.title)
        repository.close()
    }

    @Test
    fun `legacy session updated still publishes metadata immediately without REST reads`() = runTest {
        val client = client()
        val repository = repository(client)
        repository.refresh()
        val ui = repository.sessionUiState(SessionId("s1"))
        val updated = SessionMapper.mapToDomain(client.getSessionResults.getValue("s1").copy(title = "Legacy"))

        repository.acceptEvent(OpenCodeEvent.SessionUpdated(updated))

        assertEquals(updated, ui.value.session)
        assertEquals(updated, repository.state.value.snapshot.sessions.getValue("s1").session)
        assertEquals(0, client.getSessionCalls)
        repository.close()
    }

    private fun client(): FakeWorkspaceClient = FakeWorkspaceClient().apply {
        val initial = FakeWorkspaceClient.sessionDto("s1", title = "Initial")
        listSessionsResult = listOf(initial)
        getSessionResults[initial.id] = initial
    }

    private fun TestScope.repository(client: SessionWorkspaceClient): SessionRepositoryImpl = SessionRepositoryImpl(
        client,
        nowMs = { testScheduler.currentTime },
        dispatcher = StandardTestDispatcher(testScheduler),
    )
}
