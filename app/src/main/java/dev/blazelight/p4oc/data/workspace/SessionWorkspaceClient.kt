package dev.blazelight.p4oc.data.workspace

import dev.blazelight.p4oc.data.remote.dto.SessionDto
import dev.blazelight.p4oc.domain.workspace.Workspace

interface SessionWorkspaceClient {
    val workspace: Workspace

    suspend fun listSessions(): List<SessionDto>

    suspend fun getSession(id: String): SessionDto
}
