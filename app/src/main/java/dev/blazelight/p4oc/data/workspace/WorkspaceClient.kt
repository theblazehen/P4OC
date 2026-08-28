@file:Suppress("ImportOrdering")

package dev.blazelight.p4oc.data.workspace

import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.OpenCodeApi
import dev.blazelight.p4oc.data.remote.dto.AddMcpServerRequest
import dev.blazelight.p4oc.data.remote.dto.AgentDto
import dev.blazelight.p4oc.data.remote.dto.CommandDto
import dev.blazelight.p4oc.data.remote.dto.ConfigDto
import dev.blazelight.p4oc.data.remote.dto.CreateSessionRequest
import dev.blazelight.p4oc.data.remote.dto.ExecuteCommandRequest
import dev.blazelight.p4oc.data.remote.dto.FileContentDto
import dev.blazelight.p4oc.data.remote.dto.FileNodeDto
import dev.blazelight.p4oc.data.remote.dto.FileStatusDto
import dev.blazelight.p4oc.data.remote.dto.ForkSessionRequest
import dev.blazelight.p4oc.data.remote.dto.InitSessionRequest
import dev.blazelight.p4oc.data.remote.dto.McpStatusDto
import dev.blazelight.p4oc.data.remote.dto.MessageWrapperDto
import dev.blazelight.p4oc.data.remote.dto.OAuthCallbackRequest
import dev.blazelight.p4oc.data.remote.dto.PermissionDto
import dev.blazelight.p4oc.data.remote.dto.PermissionResponseRequest
import dev.blazelight.p4oc.data.remote.dto.PermissionV2RequestDto
import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import dev.blazelight.p4oc.data.remote.dto.ProviderAuthAuthorizationDto
import dev.blazelight.p4oc.data.remote.dto.ProviderAuthAuthorizeRequest
import dev.blazelight.p4oc.data.remote.dto.ProviderAuthMethodDto
import dev.blazelight.p4oc.data.remote.dto.ProvidersResponseDto
import dev.blazelight.p4oc.data.remote.dto.QuestionReplyRequest
import dev.blazelight.p4oc.data.remote.dto.QuestionRequestDto
import dev.blazelight.p4oc.data.remote.dto.RevertSessionRequest
import dev.blazelight.p4oc.data.remote.dto.SendMessageRequest
import dev.blazelight.p4oc.data.remote.dto.SessionDto
import dev.blazelight.p4oc.data.remote.dto.SessionStatusDto
import dev.blazelight.p4oc.data.remote.dto.SnapshotFileDiffDto
import dev.blazelight.p4oc.data.remote.dto.ShellCommandRequest
import dev.blazelight.p4oc.data.remote.dto.SymbolDto
import dev.blazelight.p4oc.data.remote.dto.TodoDto
import dev.blazelight.p4oc.data.remote.dto.UpdateSessionRequest
import dev.blazelight.p4oc.data.remote.dto.VcsInfoDto
import dev.blazelight.p4oc.data.server.ActiveServerApiProvider
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.workspace.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import okio.Buffer

import retrofit2.HttpException
import java.io.IOException

private const val BOUNDED_FILE_READ_CHUNK_BYTES = 8L * 1024
private const val FILE_RESPONSE_TOO_LARGE_MESSAGE = "File response exceeds the allowed size"

class WorkspaceClient(
    override val workspace: Workspace,
    val generation: ServerGeneration,
    private val apiProvider: ActiveServerApiProvider,
    val connectionState: StateFlow<ConnectionState>,
    private val json: Json = Json.Default,
) : SessionWorkspaceClient {
    private val api: OpenCodeApi
        get() = apiProvider.apiFor(workspace.server, generation)
    private val directory: String? = workspace.directory

    override suspend fun listProjects(): List<ProjectDto> = api.listProjects(directory = null, workspace = null)

    /** Probes a directory returned by [listProjects] using that project as API scope. */
    suspend fun listProjectFiles(projectDirectory: String, path: String = "."): List<FileNodeDto> =
        api.listFiles(path, projectDirectory, workspace = null)

    override suspend fun listSessions(
        directory: String?,
        scope: String?,
        roots: Boolean?,
        start: Long?,
        search: String?,
        limit: Int?,
    ): List<SessionDto> = api.listSessions(
        directory = directory,
        workspace = null,
        scope = scope,
        path = null,
        roots = roots,
        start = start,
        search = search,
        limit = limit,
    )

    override suspend fun createSession(request: CreateSessionRequest): SessionDto =
        api.createSession(directory = directory, workspace = null, request = request)

    override suspend fun getSession(id: String): SessionDto = api.getSession(id, directory, workspace = null)

    suspend fun getVcsInfo(): VcsInfoDto = api.getVcsInfo(directory, workspace = null)

    suspend fun getAgents(): List<AgentDto> = api.getAgents(directory, workspace = null)

    suspend fun getProviders(): ProvidersResponseDto = api.getProviders(directory, workspace = null)

    suspend fun getMcpStatus(): Map<String, McpStatusDto> = api.getMcpStatus(directory, workspace = null)

    suspend fun addMcpServer(request: AddMcpServerRequest): Map<String, McpStatusDto> =
        api.addMcpServer(request, directory = directory, workspace = null)

    suspend fun getConfig(): ConfigDto = api.getConfig(directory, workspace = null)

    suspend fun updateConfig(config: ConfigDto): ConfigDto = api.updateConfig(config, directory, workspace = null)

    /** Persists the workspace's default model without replacing unrelated configuration. */
    suspend fun updateCurrentModel(model: String): ConfigDto {
        val currentConfig = api.getConfig(directory, workspace = null)
        return api.updateConfig(currentConfig.copy(model = model), directory, workspace = null)
    }

    suspend fun getProviderAuthMethods(): Map<String, List<ProviderAuthMethodDto>> =
        api.getProviderAuthMethods(directory, workspace = null)

    suspend fun authorizeProvider(
        providerId: String,
        request: ProviderAuthAuthorizeRequest,
    ): ProviderAuthAuthorizationDto =
        api.authorizeProvider(providerId, request, directory, workspace = null)

    suspend fun completeProviderOAuth(providerId: String, request: OAuthCallbackRequest): Boolean =
        api.oauthCallback(providerId, request, directory, workspace = null)

    override suspend fun deleteSession(id: String): Boolean = api.deleteSession(id, directory, workspace = null)

    override suspend fun updateSession(id: String, request: UpdateSessionRequest): SessionDto =
        api.updateSession(id, request, directory, workspace = null)

    override suspend fun getSessionStatuses(directory: String?): Map<String, SessionStatusDto> =
        api.getSessionStatuses(directory, workspace = null)

    override suspend fun abortSession(id: String): Boolean {
        val response = api.abortSession(id, directory, workspace = null)
        if (response.isSuccessful) return true

        throw IOException("Unable to stop run (${response.code()})")
    }

    suspend fun getSessionTodos(id: String): List<TodoDto> = api.getSessionTodos(id, directory, workspace = null)

    override suspend fun forkSession(id: String, request: ForkSessionRequest): SessionDto =
        api.forkSession(id, request, directory, workspace = null)

    override suspend fun initSession(id: String, request: InitSessionRequest): Boolean =
        api.initSession(id, request, directory, workspace = null)

    override suspend fun shareSession(id: String): SessionDto = api.shareSession(id, directory, workspace = null)

    override suspend fun unshareSession(id: String): SessionDto = api.unshareSession(id, directory, workspace = null)

    override suspend fun summarizeSession(id: String): Boolean = api.summarizeSession(id, directory, workspace = null)

    suspend fun revertSession(id: String, request: RevertSessionRequest): SessionDto =
        api.revertSession(id, request, directory, workspace = null)

    suspend fun unrevertSession(id: String): SessionDto = api.unrevertSession(id, directory, workspace = null)

    suspend fun getSessionDiff(id: String, messageId: String? = null): List<SnapshotFileDiffDto> =
        api.getSessionDiff(id, messageId, directory, workspace = null)

    suspend fun getMessages(sessionId: String, limit: Int? = null): List<MessageWrapperDto> =
        api.getMessages(sessionId, limit, before = null, directory = directory, workspace = null)

    override suspend fun sendMessageAsync(sessionId: String, request: SendMessageRequest) {
        api.sendMessageAsync(sessionId, request, directory, workspace = null)
    }

    override suspend fun listSessionPermissionsV2(sessionId: String): List<PermissionV2RequestDto> =
        api.listSessionPermissionsV2(sessionId).data

    override suspend fun listPermissions(): List<PermissionDto> = api.listPermissions(directory, workspace = null)

    suspend fun respondToPermission(
        sessionId: String,
        requestId: String,
        request: PermissionResponseRequest
    ): Boolean {
        val response = api.respondToPermissionV2(sessionId, requestId, request)
        val contentType = response.headers()["Content-Type"].orEmpty()
        if (response.isSuccessful && !contentType.startsWith("text/html")) return true
        if (response.code() != 404 && !contentType.startsWith("text/html")) throw HttpException(response)

        return respondToPermissionLegacy(requestId, request)
    }

    suspend fun respondToPermissionLegacy(requestId: String, request: PermissionResponseRequest): Boolean =
        api.respondToPermission(requestId, request, directory, workspace = null)

    suspend fun respondToQuestion(
        sessionId: String,
        requestId: String,
        request: QuestionReplyRequest,
    ): Boolean {
        val legacy = api.respondToQuestion(requestId, request, directory, workspace = null)
        when (legacy.classifyLegacyQuestionResponse()) {
            LegacyQuestionResponseDisposition.Decode -> return legacy.decodeLegacyBoolean()
            LegacyQuestionResponseDisposition.Fallback -> Unit
        }

        return api.respondToQuestionV2(sessionId, requestId, request).requireUsableV2Response()
    }

    suspend fun rejectQuestion(sessionId: String, requestId: String): Boolean {
        val legacy = api.rejectQuestion(requestId, directory, workspace = null)
        when (legacy.classifyLegacyQuestionResponse()) {
            LegacyQuestionResponseDisposition.Decode -> return legacy.decodeLegacyBoolean()
            LegacyQuestionResponseDisposition.Fallback -> Unit
        }

        return api.rejectQuestionV2(sessionId, requestId).requireUsableV2Response()
    }

    override suspend fun listSessionQuestions(sessionId: String): List<QuestionRequestDto> {
        val legacy = api.listPendingQuestions(directory, workspace = null)
        when (legacy.classifyLegacyQuestionResponse()) {
            LegacyQuestionResponseDisposition.Decode ->
                return legacy.decodeLegacyQuestions().filter { it.sessionID == sessionId }
            LegacyQuestionResponseDisposition.Fallback -> Unit
        }

        val response = api.listSessionQuestionsV2(sessionId)
        if (!response.isUsableV2Response()) throw HttpException(response)
        return response.body()?.data.orEmpty()
    }

    suspend fun listPendingQuestions(): List<QuestionRequestDto> {
        val response = api.listPendingQuestions(directory, workspace = null)
        return when (response.classifyLegacyQuestionResponse()) {
            LegacyQuestionResponseDisposition.Decode -> response.decodeLegacyQuestions()
            LegacyQuestionResponseDisposition.Fallback -> throw HttpException(response)
        }
    }

    private fun retrofit2.Response<*>.isUsableV2Response(): Boolean =
        isSuccessful && !headers()["Content-Type"].orEmpty().startsWith("text/html")

    private enum class LegacyQuestionResponseDisposition { Decode, Fallback }

    private fun retrofit2.Response<ResponseBody>.classifyLegacyQuestionResponse():
        LegacyQuestionResponseDisposition = when {
        code() == 404 -> {
            closeLegacyQuestionBodies()
            LegacyQuestionResponseDisposition.Fallback
        }
        !isSuccessful -> {
            closeLegacyQuestionBodies()
            throw HttpException(this)
        }
        isSuccessfulHtmlQuestionResponse() -> {
            closeLegacyQuestionBodies()
            LegacyQuestionResponseDisposition.Fallback
        }
        else -> LegacyQuestionResponseDisposition.Decode
    }

    private fun retrofit2.Response<ResponseBody>.isSuccessfulHtmlQuestionResponse(): Boolean =
        isSuccessful && (
            headers()["Content-Type"].isHtmlContentType() ||
                body()?.contentType()?.toString().isHtmlContentType()
            )

    private fun String?.isHtmlContentType(): Boolean =
        this?.substringBefore(';')?.trim()?.equals("text/html", ignoreCase = true) == true

    private fun retrofit2.Response<ResponseBody>.closeLegacyQuestionBodies() {
        body()?.close()
        errorBody()?.close()
    }

    private fun retrofit2.Response<ResponseBody>.decodeLegacyBoolean(): Boolean {
        val content = body()?.string() ?: return false
        return json.decodeFromString<Boolean?>(content) == true
    }

    private fun retrofit2.Response<ResponseBody>.decodeLegacyQuestions(): List<QuestionRequestDto> {
        val content = body()?.string() ?: return emptyList()
        return json.decodeFromString<List<QuestionRequestDto>?>(content).orEmpty()
    }

    private fun retrofit2.Response<Unit>.requireUsableV2Response(): Boolean {
        if (!isUsableV2Response()) throw HttpException(this)
        return true
    }

    suspend fun listCommands(): List<CommandDto> = api.listCommands(directory, workspace = null)

    suspend fun executeCommand(sessionId: String, request: ExecuteCommandRequest): MessageWrapperDto =
        api.executeCommand(sessionId, request, directory, workspace = null)

    suspend fun executeShellCommand(sessionId: String, request: ShellCommandRequest): MessageWrapperDto =
        api.executeShellCommand(sessionId, request, directory, workspace = null)

    suspend fun listFiles(path: String): List<FileNodeDto> = api.listFiles(path, directory, workspace = null)

    suspend fun readFile(path: String): FileContentDto = api.readFile(path, directory, workspace = null)

    suspend fun readFileBounded(path: String, maxResponseBytes: Long): FileContentDto {
        require(maxResponseBytes > 0) { "maxResponseBytes must be positive" }
        val response = api.readFileRaw(path, directory, workspace = null)
        try {
            if (!response.isSuccessful) throw HttpException(response)
            val content = response.body()?.let { body ->
                body.readUtf8BoundedCancellable(maxResponseBytes)
            }.orEmpty()
            return json.decodeFromString(content)
        } finally {
            response.body()?.close()
            response.errorBody()?.close()
        }
    }

    private suspend fun ResponseBody.readUtf8BoundedCancellable(maxResponseBytes: Long): String =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { close() }
                continuation.resumeWith(
                    runCatching { readUtf8Bounded(maxResponseBytes) },
                )
            }
        }

    private fun ResponseBody.readUtf8Bounded(maxResponseBytes: Long): String {
        val declaredBytes = contentLength()
        if (declaredBytes > maxResponseBytes) throw IOException(FILE_RESPONSE_TOO_LARGE_MESSAGE)

        val bufferedBytes = Buffer()
        val responseSource = source()
        var streamedBytes = 0L
        while (true) {
            val remainingBytes = maxResponseBytes - streamedBytes
            val readLimit = if (remainingBytes >= BOUNDED_FILE_READ_CHUNK_BYTES) {
                BOUNDED_FILE_READ_CHUNK_BYTES
            } else {
                remainingBytes + 1L
            }
            val readBytes = responseSource.read(bufferedBytes, readLimit)
            if (readBytes == -1L) break
            if (readBytes > remainingBytes) throw IOException(FILE_RESPONSE_TOO_LARGE_MESSAGE)
            streamedBytes += readBytes
        }
        return bufferedBytes.readUtf8()
    }

    suspend fun getFileStatus(): List<FileStatusDto> = api.getFileStatus(directory, workspace = null)

    suspend fun searchFiles(query: String): List<String> = api.searchFiles(
        query = query,
        directory = workspace.directory,
        workspace = null,
        dirs = "false",
        type = "file",
        limit = 200,
    )

    suspend fun searchSymbols(query: String): List<SymbolDto> = api.searchSymbols(query, directory, workspace = null)
}
