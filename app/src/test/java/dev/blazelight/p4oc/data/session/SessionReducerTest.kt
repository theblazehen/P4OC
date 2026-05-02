package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.domain.session.WorkspaceSession
import dev.blazelight.p4oc.domain.workspace.Workspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class SessionReducerTest {
    private val workspace = Workspace(
        server = ServerRef.fromEndpointKey("http://test.local"),
        directory = "/repo",
    )
    private val reducer = SessionReducer(workspace)

    @Test
    fun `session created upserts session`() {
        val result = reducer.reduce(Snapshot(), OpenCodeEvent.SessionCreated(session("new", title = "New")))

        assertEquals("New", result.sessions.getValue("new").session.title)
        assertEquals(workspace, result.sessions.getValue("new").workspace)
    }

    @Test
    fun `session updated replaces existing session`() {
        val initial = Snapshot(mapOf("same" to workspaceSession("same", title = "Old")))

        val result = reducer.reduce(initial, OpenCodeEvent.SessionUpdated(session("same", title = "Updated")))

        assertEquals("Updated", result.sessions.getValue("same").session.title)
    }

    @Test
    fun `session deleted removes session`() {
        val initial = Snapshot(mapOf("gone" to workspaceSession("gone")))

        val result = reducer.reduce(initial, OpenCodeEvent.SessionDeleted(session("gone")))

        assertFalse(result.sessions.containsKey("gone"))
    }

    @Test
    fun `irrelevant event leaves snapshot unchanged`() {
        val initial = Snapshot(mapOf("keep" to workspaceSession("keep")))

        val result = reducer.reduce(initial, OpenCodeEvent.Connected)

        assertSame(initial, result)
    }

    private fun workspaceSession(id: String, title: String = id): WorkspaceSession = WorkspaceSession(
        id = SessionId(id),
        workspace = workspace,
        session = session(id, title),
    )

    private fun session(id: String, title: String = id): Session = Session(
        id = id,
        projectID = "project-$id",
        directory = "/repo",
        title = title,
        version = "1",
        createdAt = 1L,
        updatedAt = 1L,
    )
}
