package dev.blazelight.p4oc.data.files.ofish

import dev.blazelight.p4oc.data.remote.dto.CreateSessionRequest
import dev.blazelight.p4oc.data.remote.dto.MessageWrapperDto
import dev.blazelight.p4oc.data.remote.dto.PermissionResponseRequest
import dev.blazelight.p4oc.data.remote.dto.SessionDto
import dev.blazelight.p4oc.data.remote.dto.ShellCommandRequest
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.workspace.Workspace

internal interface OfishWorkspaceClient {
    val workspace: Workspace

    suspend fun createSession(title: String): SessionDto

    suspend fun deleteSession(id: String): Boolean

    suspend fun executeShellCommand(sessionId: String, request: ShellCommandRequest): MessageWrapperDto

    suspend fun listSessionsCurrentWorkspace(limit: Int? = null): List<SessionDto>

    suspend fun respondToPermission(id: String, request: PermissionResponseRequest): Boolean
}

internal class WorkspaceClientOfishAdapter(
    private val workspaceClient: WorkspaceClient,
) : OfishWorkspaceClient {
    override val workspace: Workspace = workspaceClient.workspace

    override suspend fun createSession(title: String): SessionDto =
        workspaceClient.createSession(CreateSessionRequest(title = title))

    override suspend fun deleteSession(id: String): Boolean = workspaceClient.deleteSession(id)

    override suspend fun executeShellCommand(sessionId: String, request: ShellCommandRequest): MessageWrapperDto =
        workspaceClient.executeShellCommand(sessionId, request)

    override suspend fun listSessionsCurrentWorkspace(limit: Int?): List<SessionDto> =
        workspaceClient.listSessions(
            directory = workspace.directory,
            roots = true,
            limit = limit,
        )

    override suspend fun respondToPermission(id: String, request: PermissionResponseRequest): Boolean =
        workspaceClient.respondToPermissionLegacy(id, request)
}
