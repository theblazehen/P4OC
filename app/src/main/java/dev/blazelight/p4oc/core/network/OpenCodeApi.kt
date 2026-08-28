package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.data.remote.dto.*
import dev.blazelight.p4oc.data.vcs.VcsDiffMode
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface OpenCodeApi {

    @GET("global/health")
    suspend fun health(): HealthResponse

    @GET("project")
    suspend fun listProjects(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): List<ProjectDto>

    @GET("project/current")
    suspend fun getCurrentProject(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): ProjectDto

    @GET("path")
    suspend fun getPath(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): PathInfoDto

    @GET("vcs")
    suspend fun getVcsInfo(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): VcsInfoDto

    @Streaming
    @GET("vcs")
    suspend fun getVcsInfoRaw(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): Response<ResponseBody>

    @Streaming
    @GET("vcs/status")
    suspend fun getVcsStatusRaw(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): Response<ResponseBody>

    @Streaming
    @GET("vcs/diff")
    suspend fun getVcsDiffRaw(
        @Query("mode") mode: VcsDiffMode,
        @Query("context") context: Int,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): Response<ResponseBody>

    @GET("session")
    @Suppress("LongParameterList")
    suspend fun listSessions(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?,
        @Query("scope") scope: String? = null,
        @Query("path") path: String? = null,
        @Query("roots") roots: Boolean? = null,
        @Query("start") start: Long? = null,
        @Query("search") search: String? = null,
        @Query("limit") limit: Int? = null
    ): List<SessionDto>

    @POST("session")
    suspend fun createSession(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?,
        @Body request: CreateSessionRequest
    ): SessionDto

    @GET("session/{id}")
    suspend fun getSession(
        @Path("id") id: String,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): SessionDto

    @DELETE("session/{id}")
    suspend fun deleteSession(
        @Path("id") id: String,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): Boolean

    @PATCH("session/{id}")
    suspend fun updateSession(
        @Path("id") id: String,
        @Body request: UpdateSessionRequest,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): SessionDto

    @GET("session/status")
    suspend fun getSessionStatuses(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): Map<String, SessionStatusDto>

    @POST("session/{id}/abort")
    suspend fun abortSession(
        @Path("id") id: String,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): Response<Unit>

    @POST("session/{id}/fork")
    suspend fun forkSession(
        @Path("id") id: String,
        @Body request: ForkSessionRequest,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): SessionDto

    @GET("session/{id}/children")
    suspend fun getSessionChildren(
        @Path("id") id: String,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): List<SessionDto>

    @GET("session/{id}/todo")
    suspend fun getSessionTodos(
        @Path("id") id: String,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): List<TodoDto>

    @POST("session/{id}/init")
    suspend fun initSession(
        @Path("id") id: String,
        @Body request: InitSessionRequest,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): Boolean

    @POST("session/{id}/share")
    suspend fun shareSession(
        @Path("id") id: String,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): SessionDto

    @DELETE("session/{id}/share")
    suspend fun unshareSession(
        @Path("id") id: String,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): SessionDto

    @GET("session/{id}/diff")
    suspend fun getSessionDiff(
        @Path("id") id: String,
        @Query("messageID") messageID: String? = null,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): List<SnapshotFileDiffDto>

    @POST("session/{id}/summarize")
    suspend fun summarizeSession(
        @Path("id") id: String,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): Boolean

    @POST("session/{id}/revert")
    suspend fun revertSession(
        @Path("id") id: String,
        @Body request: RevertSessionRequest,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): SessionDto

    @POST("session/{id}/unrevert")
    suspend fun unrevertSession(
        @Path("id") id: String,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): SessionDto

    @GET("session/{sessionId}/message")
    suspend fun getMessages(
        @Path("sessionId") sessionId: String,
        @Query("limit") limit: Int?,
        @Query("before") before: String?,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): List<MessageWrapperDto>

    @GET("session/{sessionId}/message/{messageId}")
    suspend fun getMessage(
        @Path("sessionId") sessionId: String,
        @Path("messageId") messageId: String,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): MessageWrapperDto

    /**
     * Send a message asynchronously (fire-and-forget).
     * Returns immediately - all response content streams via SSE events.
     * Use this for long-running operations to avoid HTTP timeout issues.
     */
    @POST("session/{sessionId}/prompt_async")
    suspend fun sendMessageAsync(
        @Path("sessionId") sessionId: String,
        @Body request: SendMessageRequest,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    )

    @POST("session/{sessionId}/command")
    suspend fun executeCommand(
        @Path("sessionId") sessionId: String,
        @Body request: ExecuteCommandRequest,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): MessageWrapperDto

    @POST("session/{sessionId}/shell")
    suspend fun executeShellCommand(
        @Path("sessionId") sessionId: String,
        @Body request: ShellCommandRequest,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): MessageWrapperDto

    @GET("permission")
    suspend fun listPermissions(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): List<PermissionDto>

    @POST("permission/{requestId}/reply")
    suspend fun respondToPermission(
        @Path("requestId") requestId: String,
        @Body request: PermissionResponseRequest,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): Boolean

    @GET("api/session/{sessionId}/permission")
    suspend fun listSessionPermissionsV2(
        @Path("sessionId") sessionId: String
    ): PermissionV2RequestListResponseDto

    @GET("api/session/{sessionId}/question")
    suspend fun listSessionQuestionsV2(
        @Path("sessionId") sessionId: String
    ): Response<QuestionV2RequestListResponseDto>

    @POST("api/session/{sessionId}/question/{requestId}/reply")
    suspend fun respondToQuestionV2(
        @Path("sessionId") sessionId: String,
        @Path("requestId") requestId: String,
        @Body request: QuestionV2Reply
    ): Response<Unit>

    @POST("api/session/{sessionId}/question/{requestId}/reject")
    suspend fun rejectQuestionV2(
        @Path("sessionId") sessionId: String,
        @Path("requestId") requestId: String
    ): Response<Unit>

    @POST("api/session/{sessionId}/permission/{requestId}/reply")
    suspend fun respondToPermissionV2(
        @Path("sessionId") sessionId: String,
        @Path("requestId") requestId: String,
        @Body request: PermissionResponseRequest
    ): Response<Unit>

    @POST("question/{requestId}/reply")
    suspend fun respondToQuestion(
        @Path("requestId") requestId: String,
        @Body request: QuestionReplyRequest,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): Response<ResponseBody>

    @POST("question/{requestId}/reject")
    suspend fun rejectQuestion(
        @Path("requestId") requestId: String,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): Response<ResponseBody>

    @GET("question")
    suspend fun listPendingQuestions(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): Response<ResponseBody>

    @GET("command")
    suspend fun listCommands(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): List<CommandDto>

    @GET("file")
    suspend fun listFiles(
        @Query("path") path: String,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): List<FileNodeDto>

    @GET("file/content")
    suspend fun readFile(
        @Query("path") path: String,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): FileContentDto

    @Streaming
    @GET("file/content")
    suspend fun readFileRaw(
        @Query("path") path: String,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): Response<ResponseBody>

    @GET("file/status")
    suspend fun getFileStatus(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): List<FileStatusDto>

    @GET("find/file")
    @Suppress("LongParameterList")
    suspend fun searchFiles(
        @Query("query") query: String,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?,
        @Query("dirs") dirs: String? = null,
        @Query("type") type: String? = null,
        @Query("limit") limit: Int? = null
    ): List<String>

    @GET("find/symbol")
    suspend fun searchSymbols(
        @Query("query") query: String,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): List<SymbolDto>

    @GET("config")
    suspend fun getConfig(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): ConfigDto

    @PATCH("config")
    suspend fun updateConfig(
        @Body config: ConfigDto,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): ConfigDto

    @GET("provider")
    suspend fun getProviders(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): ProvidersResponseDto

    @GET("provider/auth")
    suspend fun getProviderAuthMethods(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): Map<String, List<ProviderAuthMethodDto>>

    @GET("agent")
    suspend fun getAgents(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): List<AgentDto>

    @GET("lsp")
    suspend fun getLspStatus(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): List<LspStatusDto>

    @GET("formatter")
    suspend fun getFormatterStatus(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): List<FormatterStatusDto>

    @GET("mcp")
    suspend fun getMcpStatus(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): Map<String, McpStatusDto>

    // ============================================================================
    // OAuth & Auth Endpoints (aligned with SDK)
    // ============================================================================

    @POST("provider/{id}/oauth/authorize")
    suspend fun authorizeProvider(
        @Path("id") id: String,
        @Body request: ProviderAuthAuthorizeRequest,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): ProviderAuthAuthorizationDto

    @POST("provider/{id}/oauth/callback")
    suspend fun oauthCallback(
        @Path("id") id: String,
        @Body request: OAuthCallbackRequest,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): Boolean

    @PUT("auth/{id}")
    suspend fun setAuth(
        @Path("id") id: String,
        @Body auth: AuthDto
    ): Boolean

    // ============================================================================
    // Instance Management
    // ============================================================================

    @POST("instance/dispose")
    suspend fun disposeInstance(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): Boolean

    // ============================================================================
    // MCP Management
    // ============================================================================

    @POST("mcp")
    suspend fun addMcpServer(
        @Body request: AddMcpServerRequest,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?,
    ): Map<String, McpStatusDto>

    // ============================================================================
    // Logging
    // ============================================================================

    @POST("log")
    suspend fun log(
        @Body request: LogRequest,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): Boolean

    // ============================================================================
    // PTY (Terminal) Endpoints
    // ============================================================================

    @GET("pty")
    suspend fun listPtySessions(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?,
    ): List<PtyDto>

    @POST("pty")
    suspend fun createPtySession(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?,
        @Body request: CreatePtyRequest,
    ): PtyDto

    @GET("pty/{id}")
    suspend fun getPtySession(
        @Path("id") id: String,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?,
    ): PtyDto

    @DELETE("pty/{id}")
    suspend fun deletePtySession(
        @Path("id") id: String,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?,
    ): Boolean

    @PUT("pty/{id}")
    suspend fun updatePtySession(
        @Path("id") id: String,
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?,
        @Body request: UpdatePtyRequest,
    ): PtyDto

    // ============================================================================
    // Experimental Tools Endpoints
    // ============================================================================

    @GET("experimental/tool/ids")
    suspend fun getToolIds(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): List<String>

    @GET("experimental/tool")
    suspend fun getTools(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?,
        @Query("provider") provider: String,
        @Query("model") model: String
    ): ToolListDto

    // ============================================================================
    // Config Providers Endpoint
    // ============================================================================

    @GET("config/providers")
    suspend fun getConfigProviders(
        @Query("directory") directory: String?,
        @Query("workspace") workspace: String?
    ): ConfigProvidersDto
}
