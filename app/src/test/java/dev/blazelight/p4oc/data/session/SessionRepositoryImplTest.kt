package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.data.remote.dto.SessionStatusDto
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageError
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.model.SessionStatus
import dev.blazelight.p4oc.domain.model.TokenUsage
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.fakes.FakeWorkspaceClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
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
        val repository =
            SessionRepositoryImpl(
                client,
                nowMs = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler)
            )

        val first = repository.prewarm(client.projects)
        val second = repository.prewarm(client.projects)
        advanceUntilIdle()

        assertSame(first, second)
        assertEquals(listOf(null, "/repo/p1"), client.listSessionsDirectories)
        assertEquals(listOf(null, "project"), client.listSessionsScopes)

        client.statusBlocker?.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `awaitOrFetch joins in-flight Deferred from prewarm`() = runTest {
        val client = FakeWorkspaceClient().apply {
            projects = listOf(FakeWorkspaceClient.projectDto("p1", "/repo/p1"), FakeWorkspaceClient.projectDto("p2", "/repo/p2"))
            statusBlocker = CompletableDeferred()
        }
        val repository =
            SessionRepositoryImpl(
                client,
                nowMs = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler)
            )

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
        val repository =
            SessionRepositoryImpl(
                client,
                nowMs = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler)
            )

        val result = repository.awaitOrFetch()

        assertTrue(result.isSuccess)
        assertEquals(1, client.listProjectsCalls)
        assertEquals(listOf(null, "/repo/p1"), client.listSessionsDirectories)
        assertEquals(listOf(null, "project"), client.listSessionsScopes)
    }

    @Test
    fun `peek returns non-null within 30s and null after`() = runTest {
        val client = FakeWorkspaceClient().apply {
            projects = listOf(FakeWorkspaceClient.projectDto("p1", "/repo/p1"))
        }
        val repository =
            SessionRepositoryImpl(
                client,
                nowMs = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler)
            )

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
        val repository =
            SessionRepositoryImpl(
                client,
                nowMs = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler)
            )

        val deferred = repository.prewarm(client.projects)
        advanceUntilIdle()
        repository.invalidate()

        assertTrue(deferred.isCancelled)
        assertNull(repository.peek())
    }

    @Test
    fun `empty projects seeds global session and status requests`() = runTest {
        val client = FakeWorkspaceClient()
        val repository =
            SessionRepositoryImpl(
                client,
                nowMs = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler)
            )

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
        val repository =
            SessionRepositoryImpl(
                client,
                nowMs = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler)
            )

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
        val repository =
            SessionRepositoryImpl(
                client,
                nowMs = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler)
            )

        repository.refresh()

        val sessions = repository.state.value.snapshot.sessions
        assertEquals(1, sessions.size)
        assertEquals("/repo/p1", sessions.getValue("same").session.directory)
    }

    @Test
    fun `hydrate filters OFISH sessions from visible state`() = runTest {
        val client = FakeWorkspaceClient().apply {
            projects = emptyList()
            setSessions(
                FakeWorkspaceClient.sessionDto(id = "normal", title = "Normal"),
                FakeWorkspaceClient.sessionDto(id = "ofish", title = "__ofish_probe_1_x"),
            )
        }
        val repository =
            SessionRepositoryImpl(
                client,
                nowMs = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler)
            )

        repository.refresh()

        val sessions = repository.state.value.snapshot.sessions
        assertTrue(sessions.containsKey("normal"))
        assertFalse(sessions.containsKey("ofish"))
    }

    @Test
    fun `SSE event during hydrate appears in final state`() = runTest {
        val client = FakeWorkspaceClient().apply {
            projects = listOf(FakeWorkspaceClient.projectDto("p1", "/repo/p1"))
            statusBlocker = CompletableDeferred()
        }
        val repository =
            SessionRepositoryImpl(
                client,
                nowMs = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler)
            )

        val prewarm = repository.prewarm(client.projects)
        advanceUntilIdle()
        repository.acceptEvent(OpenCodeEvent.SessionCreated(session("streamed")))
        client.statusBlocker?.complete(Unit)
        prewarm.await()

        assertTrue(repository.state.value.snapshot.sessions.containsKey("streamed"))
    }

    @Test
    fun `connected event triggers hydrate and incorporates missed server state`() = runTest {
        val client = FakeWorkspaceClient().apply {
            projects = emptyList()
            setSessions(FakeWorkspaceClient.sessionDto(id = "before", title = "Before"))
        }
        val repository =
            SessionRepositoryImpl(
                client,
                nowMs = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler)
            )
        repository.refresh()
        val listProjectsCallsBeforeReconnect = client.listProjectsCalls
        client.setSessions(
            FakeWorkspaceClient.sessionDto(id = "before", title = "Before"),
            FakeWorkspaceClient.sessionDto(id = "missed", title = "Missed"),
        )

        repository.acceptEvent(OpenCodeEvent.Connected)
        advanceUntilIdle()

        assertEquals(listProjectsCallsBeforeReconnect + 1, client.listProjectsCalls)
        assertTrue(repository.state.value.snapshot.sessions.containsKey("missed"))
    }

    @Test
    fun `session event during reconnect hydrate replays over hydrated snapshot`() = runTest {
        val client = FakeWorkspaceClient().apply {
            projects = emptyList()
            statusBlocker = CompletableDeferred()
            setSessions(FakeWorkspaceClient.sessionDto(id = "missed", title = "Missed"))
        }
        val repository =
            SessionRepositoryImpl(
                client,
                nowMs = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler)
            )

        repository.acceptEvent(OpenCodeEvent.Connected)
        advanceUntilIdle()
        assertTrue(repository.state.value is RepoState.Hydrating)

        repository.acceptEvent(OpenCodeEvent.SessionCreated(session("streamed")))
        client.statusBlocker?.complete(Unit)
        advanceUntilIdle()

        val sessions = repository.state.value.snapshot.sessions
        assertTrue(sessions.containsKey("missed"))
        assertTrue(sessions.containsKey("streamed"))
    }

    @Test
    fun `delete http failure refetches server truth instead of rollback map`() = runTest {
        val client = FakeWorkspaceClient().apply {
            projects = emptyList()
            setSessions(FakeWorkspaceClient.sessionDto(id = "s1", title = "Server Truth"))
            deleteSessionFailure = RuntimeException("HTTP 5xx")
        }
        val repository =
            SessionRepositoryImpl(
                client,
                nowMs = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler)
            )
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
        val repository =
            SessionRepositoryImpl(
                client,
                nowMs = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler)
            )

        repository.refresh()

        assertTrue(repository.state.value.snapshot.statuses.containsKey("s1"))
    }

    @Test
    fun `send message continues after UI waiter is cancelled`() = runTest {
        val client = FakeWorkspaceClient().apply {
            sendMessageBlocker = CompletableDeferred()
        }
        val repository =
            SessionRepositoryImpl(
                client,
                nowMs = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler)
            )
        val deferred = repository.sendMessageAsync(
            SessionId("s1"),
            dev.blazelight.p4oc.data.remote.dto.SendMessageRequest(parts = emptyList()),
        )

        val uiWaiter = launch { deferred.await() }
        advanceUntilIdle()
        uiWaiter.cancelAndJoin()
        client.sendMessageBlocker?.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, client.sendMessageAsyncCalls)
        assertTrue(deferred.isCompleted)
        assertTrue(deferred.await().isSuccess)
    }

    @Test
    fun `abort session continues after UI waiter is cancelled`() = runTest {
        val client = FakeWorkspaceClient().apply {
            abortSessionBlocker = CompletableDeferred()
        }
        val repository =
            SessionRepositoryImpl(
                client,
                nowMs = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler)
            )
        val deferred = repository.abortSession(SessionId("s1"))

        val uiWaiter = launch { deferred.await() }
        advanceUntilIdle()
        uiWaiter.cancelAndJoin()
        client.abortSessionBlocker?.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, client.abortSessionCalls)
        assertTrue(deferred.isCompleted)
        assertTrue(deferred.await().isSuccess)
    }

    @Test
    fun `idle status clears streaming flags without ChatViewModel collector`() = runTest {
        val repository =
            SessionRepositoryImpl(FakeWorkspaceClient(), nowMs = {
                testScheduler.currentTime
            }, dispatcher = StandardTestDispatcher(testScheduler))
        repository.acceptEvent(OpenCodeEvent.MessageUpdated(assistantMessage("m1", "s1")))
        repository.acceptEvent(OpenCodeEvent.MessagePartUpdated(textPart("p1", "m1", "s1", "Hello"), delta = null))
        repository.acceptEvent(
            OpenCodeEvent.MessagePartUpdated(textPart("p1", "m1", "s1", "ignored"), delta = " world")
        )

        repository.acceptEvent(OpenCodeEvent.SessionStatusChanged("s1", SessionStatus.Idle))

        val text = repository.messages(SessionId("s1")).value.single().parts.single() as Part.Text
        assertFalse(text.isStreaming)
    }

    @Test
    fun `session error clears streaming flags without ChatViewModel collector`() = runTest {
        val repository =
            SessionRepositoryImpl(FakeWorkspaceClient(), nowMs = {
                testScheduler.currentTime
            }, dispatcher = StandardTestDispatcher(testScheduler))
        repository.acceptEvent(OpenCodeEvent.MessageUpdated(assistantMessage("m1", "s1")))
        repository.acceptEvent(OpenCodeEvent.MessagePartUpdated(textPart("p1", "m1", "s1", "Hello"), delta = null))
        repository.acceptEvent(
            OpenCodeEvent.MessagePartUpdated(textPart("p1", "m1", "s1", "ignored"), delta = " world")
        )

        repository.acceptEvent(OpenCodeEvent.SessionError("s1", MessageError(name = "Error", message = "boom")))

        val text = repository.messages(SessionId("s1")).value.single().parts.single() as Part.Text
        assertFalse(text.isStreaming)
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

    private fun assistantMessage(id: String, sessionId: String): Message.Assistant = Message.Assistant(
        id = id,
        sessionID = sessionId,
        createdAt = 1L,
        parentID = "",
        providerID = "provider",
        modelID = "model",
        mode = "chat",
        agent = "assistant",
        cost = 0.0,
        tokens = TokenUsage(input = 0, output = 0),
    )

    private fun textPart(id: String, messageId: String, sessionId: String, text: String): Part.Text = Part.Text(
        id = id,
        sessionID = sessionId,
        messageID = messageId,
        text = text,
    )
}
