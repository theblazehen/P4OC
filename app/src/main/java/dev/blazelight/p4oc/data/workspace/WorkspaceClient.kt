package dev.blazelight.p4oc.data.workspace

import dev.blazelight.p4oc.core.network.OpenCodeApi
import dev.blazelight.p4oc.data.remote.dto.CreateSessionRequest
import dev.blazelight.p4oc.data.remote.dto.ExecuteCommandRequest
import dev.blazelight.p4oc.data.remote.dto.FileContentDto
import dev.blazelight.p4oc.data.remote.dto.FileDiffDto
import dev.blazelight.p4oc.data.remote.dto.FileNodeDto
import dev.blazelight.p4oc.data.remote.dto.FileStatusDto
import dev.blazelight.p4oc.data.remote.dto.ForkSessionRequest
import dev.blazelight.p4oc.data.remote.dto.InitSessionRequest
import dev.blazelight.p4oc.data.remote.dto.PermissionResponseRequest
import dev.blazelight.p4oc.data.remote.dto.MessageWrapperDto
import dev.blazelight.p4oc.data.remote.dto.QuestionReplyRequest
import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import dev.blazelight.p4oc.data.remote.dto.RevertSessionRequest
import dev.blazelight.p4oc.data.remote.dto.SendMessageRequest
import dev.blazelight.p4oc.data.remote.dto.SessionDto
import dev.blazelight.p4oc.data.remote.dto.SessionStatusDto
import dev.blazelight.p4oc.data.remote.dto.ShellCommandRequest
import dev.blazelight.p4oc.data.remote.dto.CommandDto
import dev.blazelight.p4oc.data.remote.dto.SymbolDto
import dev.blazelight.p4oc.data.remote.dto.TodoDto
import dev.blazelight.p4oc.data.remote.dto.VcsInfoDto
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

    override suspend fun listProjects(): List<ProjectDto> = api.listProjects()

    override suspend fun listSessions(
        directory: String?,
        roots: Boolean?,
        start: Long?,
        search: String?,
        limit: Int?,
    ): List<SessionDto> = api.listSessions(directory, roots, start, search, limit)

    override suspend fun createSession(request: CreateSessionRequest, directory: String?): SessionDto =
        api.createSession(directory = directory, request = request)

    override suspend fun getSession(id: String): SessionDto = api.getSession(id, directory)

    suspend fun getVcsInfo(): VcsInfoDto = api.getVcsInfo(directory)

    override suspend fun deleteSession(id: String, directory: String?): Boolean = api.deleteSession(id, directory)

    override suspend fun updateSession(id: String, request: UpdateSessionRequest, directory: String?): SessionDto =
        api.updateSession(id, request, directory)

    override suspend fun getSessionStatuses(directory: String?): Map<String, SessionStatusDto> = api.getSessionStatuses(directory)

    suspend fun abortSession(id: String): Boolean = api.abortSession(id, directory)

    suspend fun getSessionTodos(id: String): List<TodoDto> = api.getSessionTodos(id, directory)

    suspend fun forkSession(id: String, request: ForkSessionRequest): SessionDto =
        api.forkSession(id, request, directory)

    suspend fun initSession(id: String, request: InitSessionRequest): Boolean =
        api.initSession(id, request, directory)

    override suspend fun shareSession(id: String, directory: String?): SessionDto = api.shareSession(id, directory)

    override suspend fun unshareSession(id: String, directory: String?): SessionDto = api.unshareSession(id, directory)

    override suspend fun summarizeSession(id: String, directory: String?): Boolean = api.summarizeSession(id, directory)

    suspend fun revertSession(id: String, request: RevertSessionRequest): SessionDto =
        api.revertSession(id, request, directory)

    suspend fun unrevertSession(id: String): SessionDto = api.unrevertSession(id, directory)

    suspend fun getSessionDiff(id: String, messageId: String? = null): List<FileDiffDto> =
        api.getSessionDiff(id, messageId, directory)

    suspend fun getMessages(sessionId: String, limit: Int? = null): List<MessageWrapperDto> =
        api.getMessages(sessionId, limit, directory)

    suspend fun sendMessageAsync(sessionId: String, request: SendMessageRequest) {
        api.sendMessageAsync(sessionId, request, directory)
    }

    suspend fun respondToPermission(requestId: String, request: PermissionResponseRequest): Boolean =
        api.respondToPermission(requestId, request, directory)

    suspend fun respondToQuestion(requestId: String, request: QuestionReplyRequest): Boolean =
        api.respondToQuestion(requestId, request, directory)

    suspend fun listCommands(): List<CommandDto> = api.listCommands(directory)

    suspend fun executeCommand(sessionId: String, request: ExecuteCommandRequest): MessageWrapperDto =
        api.executeCommand(sessionId, request, directory)

    suspend fun executeShellCommand(sessionId: String, request: ShellCommandRequest): MessageWrapperDto =
        api.executeShellCommand(sessionId, request, directory)

    suspend fun listFiles(path: String): List<FileNodeDto> = api.listFiles(path)

    suspend fun readFile(path: String): FileContentDto = api.readFile(path)

    suspend fun getFileStatus(): List<FileStatusDto> = api.getFileStatus()

    suspend fun searchSymbols(query: String): List<SymbolDto> = api.searchSymbols(query)
}
