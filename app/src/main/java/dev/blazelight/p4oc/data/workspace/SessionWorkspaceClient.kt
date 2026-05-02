package dev.blazelight.p4oc.data.workspace

import dev.blazelight.p4oc.data.remote.dto.CreateSessionRequest
import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import dev.blazelight.p4oc.data.remote.dto.SessionDto
import dev.blazelight.p4oc.data.remote.dto.SessionStatusDto
import dev.blazelight.p4oc.data.remote.dto.UpdateSessionRequest
import dev.blazelight.p4oc.domain.workspace.Workspace

interface SessionWorkspaceClient {
    val workspace: Workspace

    suspend fun listProjects(): List<ProjectDto>

    suspend fun listSessions(
        directory: String? = workspace.directory,
        roots: Boolean? = null,
        start: Long? = null,
        search: String? = null,
        limit: Int? = null,
    ): List<SessionDto>

    suspend fun getSession(id: String): SessionDto

    suspend fun getSessionStatuses(directory: String? = workspace.directory): Map<String, SessionStatusDto>

    suspend fun createSession(request: CreateSessionRequest, directory: String? = workspace.directory): SessionDto

    suspend fun deleteSession(id: String, directory: String? = workspace.directory): Boolean

    suspend fun updateSession(id: String, request: UpdateSessionRequest, directory: String? = workspace.directory): SessionDto

    suspend fun shareSession(id: String, directory: String? = workspace.directory): SessionDto

    suspend fun unshareSession(id: String, directory: String? = workspace.directory): SessionDto

    suspend fun summarizeSession(id: String, directory: String? = workspace.directory): Boolean
}
