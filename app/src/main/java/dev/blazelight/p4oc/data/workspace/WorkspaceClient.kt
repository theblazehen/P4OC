package dev.blazelight.p4oc.data.workspace

import dev.blazelight.p4oc.core.network.OpenCodeApi
import dev.blazelight.p4oc.data.remote.dto.CreateSessionRequest
import dev.blazelight.p4oc.data.remote.dto.ExecuteCommandRequest
import dev.blazelight.p4oc.data.remote.dto.ForkSessionRequest
import dev.blazelight.p4oc.data.remote.dto.InitSessionRequest
import dev.blazelight.p4oc.data.remote.dto.MessageWrapperDto
import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import dev.blazelight.p4oc.data.remote.dto.RevertSessionRequest
import dev.blazelight.p4oc.data.remote.dto.SendMessageRequest
import dev.blazelight.p4oc.data.remote.dto.SessionDto
import dev.blazelight.p4oc.data.remote.dto.SessionStatusDto
import dev.blazelight.p4oc.data.remote.dto.ShellCommandRequest
import dev.blazelight.p4oc.data.remote.dto.UpdateSessionRequest
import dev.blazelight.p4oc.data.server.ActiveServerApiProvider
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.workspace.Workspace

class WorkspaceClient(
    override val workspace: Workspace,
    val generation: ServerGeneration,
    apiProvider: ActiveServerApiProvider,
) : SessionWorkspaceClient {
    private val api: OpenCodeApi = apiProvider.apiFor(workspace.server, generation)
    private val directory: String? = workspace.directory

    suspend fun listProjects(): List<ProjectDto> = api.listProjects()

    override suspend fun listSessions(): List<SessionDto> = listSessions(
        roots = null,
        start = null,
        search = null,
        limit = null,
    )

    suspend fun listSessions(
        roots: Boolean? = null,
        start: Long? = null,
        search: String? = null,
        limit: Int? = null,
    ): List<SessionDto> = api.listSessions(directory, roots, start, search, limit)

    suspend fun createSession(request: CreateSessionRequest): SessionDto =
        api.createSession(directory = directory, request = request)

    override suspend fun getSession(id: String): SessionDto = api.getSession(id, directory)

    suspend fun deleteSession(id: String): Boolean = api.deleteSession(id, directory)

    suspend fun updateSession(id: String, request: UpdateSessionRequest): SessionDto =
        api.updateSession(id, request, directory)

    suspend fun getSessionStatuses(): Map<String, SessionStatusDto> = api.getSessionStatuses(directory)

    suspend fun abortSession(id: String): Boolean = api.abortSession(id, directory)

    suspend fun forkSession(id: String, request: ForkSessionRequest): SessionDto =
        api.forkSession(id, request, directory)

    suspend fun initSession(id: String, request: InitSessionRequest): Boolean =
        api.initSession(id, request, directory)

    suspend fun shareSession(id: String): SessionDto = api.shareSession(id, directory)

    suspend fun unshareSession(id: String): SessionDto = api.unshareSession(id, directory)

    suspend fun revertSession(id: String, request: RevertSessionRequest): SessionDto =
        api.revertSession(id, request, directory)

    suspend fun unrevertSession(id: String): SessionDto = api.unrevertSession(id, directory)

    suspend fun getMessages(sessionId: String, limit: Int? = null): List<MessageWrapperDto> =
        api.getMessages(sessionId, limit, directory)

    suspend fun sendMessageAsync(sessionId: String, request: SendMessageRequest) {
        api.sendMessageAsync(sessionId, request, directory)
    }

    suspend fun executeCommand(sessionId: String, request: ExecuteCommandRequest): MessageWrapperDto =
        api.executeCommand(sessionId, request, directory)

    suspend fun executeShellCommand(sessionId: String, request: ShellCommandRequest): MessageWrapperDto =
        api.executeShellCommand(sessionId, request, directory)
}
