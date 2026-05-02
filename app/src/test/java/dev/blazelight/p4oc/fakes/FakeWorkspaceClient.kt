package dev.blazelight.p4oc.fakes

import dev.blazelight.p4oc.data.remote.dto.SessionDto
import dev.blazelight.p4oc.data.remote.dto.TimeDto
import dev.blazelight.p4oc.data.workspace.SessionWorkspaceClient
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.workspace.Workspace

class FakeWorkspaceClient(
    override val workspace: Workspace = Workspace(
        server = ServerRef.fromEndpointKey("http://fake.test"),
        directory = "/workspace",
    ),
) : SessionWorkspaceClient {
    var listSessionsCalls: Int = 0
        private set
    var getSessionCalls: Int = 0
        private set

    var listSessionsResult: List<SessionDto> = emptyList()
    var listSessionsFailure: Throwable? = null
    var getSessionResults: MutableMap<String, SessionDto> = mutableMapOf()
    var getSessionFailure: Throwable? = null

    override suspend fun listSessions(): List<SessionDto> {
        listSessionsCalls += 1
        listSessionsFailure?.let { throw it }
        return listSessionsResult
    }

    override suspend fun getSession(id: String): SessionDto {
        getSessionCalls += 1
        getSessionFailure?.let { throw it }
        return getSessionResults[id] ?: error("No fake session configured for id: $id")
    }

    fun setSessions(vararg sessions: SessionDto) {
        listSessionsResult = sessions.toList()
        getSessionResults = sessions.associateBy { it.id }.toMutableMap()
    }

    companion object {
        fun sessionDto(
            id: String,
            title: String = id,
            directory: String = "/workspace",
            updatedAt: Long = 1L,
        ): SessionDto = SessionDto(
            id = id,
            projectID = "project-$id",
            directory = directory,
            title = title,
            version = "1",
            time = TimeDto(created = 1L, updated = updatedAt),
        )
    }
}
