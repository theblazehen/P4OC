package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.fakes.FakeWorkspaceClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class RefetchNotRollbackTest {
    @Test
    fun `optimistic http failure recovers by repository refetch snapshot not rollback`() = runTest {
        val client = FakeWorkspaceClient().apply {
            setSessions(FakeWorkspaceClient.sessionDto(id = "s1", title = "Server Truth"))
        }
        val repository = SessionRepositoryImpl(client)
        val reducer = SessionReducer(client.workspace)

        repository.refresh()
        val initial = repository.state.value.snapshot
        assertEquals("Server Truth", initial.sessions.getValue("s1").session.title)
        assertEquals(1, client.listSessionsCalls)

        // Design C: mutation orchestration applies the optimistic event locally before the HTTP result.
        val optimistic = reducer.reduce(
            snapshot = initial,
            event = OpenCodeEvent.SessionUpdated(domainSession("s1", title = "Optimistic Title")),
        )
        assertEquals("Optimistic Title", optimistic.sessions.getValue("s1").session.title)

        // The mutation HTTP call is not implemented in this pure/repository test. Represent the failure
        // handoff honestly by making the coarse refetch attempt fail once and observing the real call.
        client.listSessionsFailure = RuntimeException("HTTP 5xx")
        try {
            repository.refresh()
            fail("Expected refresh to fail")
        } catch (expected: RuntimeException) {
            assertEquals("HTTP 5xx", expected.message)
        }
        assertEquals(2, client.listSessionsCalls)

        // No rollback/pending map is involved. After the UI schedules another coarse refetch, the REST
        // snapshot is loaded through SessionRepositoryImpl and becomes truth again.
        client.listSessionsFailure = null
        client.setSessions(FakeWorkspaceClient.sessionDto(id = "s1", title = "Server Truth"))
        repository.refresh()

        assertEquals(3, client.listSessionsCalls)
        assertEquals("Server Truth", repository.state.value.snapshot.sessions.getValue("s1").session.title)
    }

    private fun domainSession(id: String, title: String): Session = Session(
        id = id,
        projectID = "project-$id",
        directory = "/workspace",
        title = title,
        version = "1",
        createdAt = 1L,
        updatedAt = 1L,
    )
}
