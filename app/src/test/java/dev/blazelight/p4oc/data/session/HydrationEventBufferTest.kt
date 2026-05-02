package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.domain.session.WorkspaceSession
import dev.blazelight.p4oc.domain.workspace.Workspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HydrationEventBufferTest {
    private val workspace = Workspace(
        server = ServerRef.fromEndpointKey("http://test.local"),
        directory = "/repo",
    )
    private val reducer = SessionReducer(workspace)

    @Test
    fun `events during hydrate are buffered and replayed after hydrated snapshot`() {
        val buffer = HydrationEventBuffer()
        val hydrating = buffer.buffer(OpenCodeEvent.SessionCreated(session("streamed")))
        val hydratedSnapshot = Snapshot(mapOf("rest" to workspaceSession(workspace, session("rest"))))

        val liveSnapshot = buffer.replayOver(hydratedSnapshot, reducer)

        assertEquals(1, hydrating.bufferedEvents)
        assertEquals(setOf("rest", "streamed"), liveSnapshot.sessions.keys)
    }

    @Test
    fun `buffer drops oldest event when capacity is exceeded`() {
        val buffer = HydrationEventBuffer(capacity = 2)

        buffer.buffer(OpenCodeEvent.SessionCreated(session("oldest")))
        buffer.buffer(OpenCodeEvent.SessionCreated(session("middle")))
        buffer.buffer(OpenCodeEvent.SessionCreated(session("newest")))
        val liveSnapshot = buffer.replayOver(Snapshot(), reducer)

        assertEquals(setOf("middle", "newest"), liveSnapshot.sessions.keys)
        assertFalse(liveSnapshot.sessions.containsKey("oldest"))
    }

    private fun workspaceSession(workspace: Workspace, session: Session): WorkspaceSession = WorkspaceSession(
        id = SessionId(session.id),
        workspace = workspace,
        session = session,
    )

    private fun session(id: String): Session = Session(
        id = id,
        projectID = "project-$id",
        directory = "/repo",
        title = id,
        version = "1",
        createdAt = 1L,
        updatedAt = 1L,
    )
}
