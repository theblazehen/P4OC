package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.data.remote.dto.*
import retrofit2.http.*

interface OpenCodeApi {

    @GET("global/health")
    suspend fun health(): HealthResponse

    @GET("project")
    suspend fun listProjects(): List<ProjectDto>

    @GET("project/current")
    suspend fun getCurrentProject(): ProjectDto

    @GET("path")
    suspend fun getPath(): PathInfoDto

    @GET("vcs")
    suspend fun getVcsInfo(): VcsInfoDto

    @GET("session")
    suspend fun listSessions(
        @Query("directory") directory: String? = null,
        @Query("roots") roots: Boolean? = null,
        @Query("start") start: Long? = null,
        @Query("search") search: String? = null,
        @Query("limit") limit: Int? = null
    ): List<SessionDto>

    @POST("session")
    suspend fun createSession(
        @Query("directory") directory: String? = null,
        @Body request: CreateSessionRequest
    ): SessionDto

    @GET("session/{id}")
    suspend fun getSession(
        @Path("id") id: String,
        @Query("directory") directory: String? = null
    ): SessionDto

    @DELETE("session/{id}")
    suspend fun deleteSession(
        @Path("id") id: String,
        @Query("directory") directory: String? = null
    ): Boolean

    @PATCH("session/{id}")
    suspend fun updateSession(
        @Path("id") id: String,
        @Body request: UpdateSessionRequest,
        @Query("directory") directory: String? = null
    ): SessionDto

    @GET("session/status")
    suspend fun getSessionStatuses(
        @Query("directory") directory: String? = null
    ): Map<String, SessionStatusDto>

    @POST("session/{id}/abort")
    suspend fun abortSession(
        @Path("id") id: String,
        @Query("directory") directory: String? = null
    ): Boolean

    @POST("session/{id}/fork")
    suspend fun forkSession(
        @Path("id") id: String,
        @Body request: ForkSessionRequest,
        @Query("directory") directory: String? = null
    ): SessionDto

    @GET("session/{id}/children")
    suspend fun getSessionChildren(
        @Path("id") id: String,
        @Query("directory") directory: String? = null
    ): List<SessionDto>

    @GET("session/{id}/todo")
    suspend fun getSessionTodos(
        @Path("id") id: String,
        @Query("directory") directory: String? = null
    ): List<TodoDto>

    @POST("session/{id}/init")
    suspend fun initSession(
        @Path("id") id: String,
        @Body request: InitSessionRequest,
        @Query("directory") directory: String? = null
    ): Boolean

    @POST("session/{id}/share")
    suspend fun shareSession(
        @Path("id") id: String,
        @Query("directory") directory: String? = null
    ): SessionDto

    @DELETE("session/{id}/share")
    suspend fun unshareSession(
        @Path("id") id: String,
        @Query("directory") directory: String? = null
    ): SessionDto

    @GET("session/{id}/diff")
    suspend fun getSessionDiff(
        @Path("id") id: String,
        @Query("messageID") messageID: String? = null,
        @Query("directory") directory: String? = null
    ): List<FileDiffDto>

    @POST("session/{id}/summarize")
    suspend fun summarizeSession(
        @Path("id") id: String,
        @Body request: SummarizeSessionRequest,
        @Query("directory") directory: String? = null
    ): Boolean

    @POST("session/{id}/revert")
    suspend fun revertSession(
        @Path("id") id: String,
        @Body request: RevertSessionRequest,
        @Query("directory") directory: String? = null
    ): Boolean

    @POST("session/{id}/unrevert")
    suspend fun unrevertSession(
        @Path("id") id: String,
        @Query("directory") directory: String? = null
    ): Boolean

    @GET("session/{sessionId}/message")
    suspend fun getMessages(
        @Path("sessionId") sessionId: String,
        @Query("limit") limit: Int? = null,
        @Query("directory") directory: String? = null
    ): List<MessageWrapperDto>

    @GET("session/{sessionId}/message/{messageId}")
    suspend fun getMessage(
        @Path("sessionId") sessionId: String,
        @Path("messageId") messageId: String,
        @Query("directory") directory: String? = null
    ): MessageWrapperDto

    @POST("session/{sessionId}/message")
    suspend fun sendMessage(
        @Path("sessionId") sessionId: String,
        @Body request: SendMessageRequest,
        @Query("directory") directory: String? = null
    ): MessageWrapperDto

    @POST("session/{sessionId}/message")
    suspend fun sendMessageStreaming(
        @Path("sessionId") sessionId: String,
        @Body request: SendMessageRequest,
        @Query("directory") directory: String? = null
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
        @Query("directory") directory: String? = null
    )

    @POST("session/{sessionId}/command")
    suspend fun executeCommand(
        @Path("sessionId") sessionId: String,
        @Body request: ExecuteCommandRequest,
        @Query("directory") directory: String? = null
    ): MessageWrapperDto

    @POST("session/{sessionId}/shell")
    suspend fun executeShellCommand(
        @Path("sessionId") sessionId: String,
        @Body request: ShellCommandRequest,
        @Query("directory") directory: String? = null
    ): MessageWrapperDto

    @POST("permission/{requestId}/reply")
    suspend fun respondToPermission(
        @Path("requestId") requestId: String,
        @Body request: PermissionResponseRequest,
        @Query("directory") directory: String? = null
    ): Boolean

    @POST("question/{requestId}/reply")
    suspend fun respondToQuestion(
        @Path("requestId") requestId: String,
        @Body request: QuestionReplyRequest,
        @Query("directory") directory: String?
    ): Boolean

    @GET("command")
    suspend fun listCommands(
        @Query("directory") directory: String? = null
    ): List<CommandDto>

    @GET("file")
    suspend fun listFiles(@Query("path") path: String = "."): List<FileNodeDto>

    @GET("file/content")
    suspend fun readFile(@Query("path") path: String): FileContentDto

    @GET("file/status")
    suspend fun getFileStatus(): List<FileStatusDto>

    @GET("find")
    suspend fun searchText(@Query("pattern") pattern: String): List<SearchResultDto>

    @GET("find/file")
    suspend fun searchFiles(
        @Query("query") query: String,
        @Query("type") type: String? = null,
        @Query("limit") limit: Int? = null
    ): List<String>

    @GET("find/symbol")
    suspend fun searchSymbols(@Query("query") query: String): List<SymbolDto>

    @GET("config")
    suspend fun getConfig(): ConfigDto

    @PATCH("config")
    suspend fun updateConfig(@Body config: ConfigDto): ConfigDto

    @GET("provider")
    suspend fun getProviders(): ProvidersResponseDto

    @GET("provider/auth")
    suspend fun getProviderAuthMethods(): Map<String, List<ProviderAuthMethodDto>>

    @GET("agent")
    suspend fun getAgents(): List<AgentDto>
    
    @POST("model/active")
    suspend fun setActiveModel(@Body request: SetActiveModelRequest): Boolean

    @GET("lsp")
    suspend fun getLspStatus(): List<LspStatusDto>

    @GET("formatter")
    suspend fun getFormatterStatus(): List<FormatterStatusDto>

    @GET("mcp")
    suspend fun getMcpStatus(): Map<String, McpStatusDto>

    // ============================================================================
    // OAuth & Auth Endpoints (aligned with SDK)
    // ============================================================================

    @POST("provider/{id}/oauth/authorize")
    suspend fun authorizeProvider(@Path("id") id: String): ProviderAuthAuthorizationDto

    @POST("provider/{id}/oauth/callback")
    suspend fun oauthCallback(
        @Path("id") id: String,
        @Body request: OAuthCallbackRequest
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
    suspend fun disposeInstance(): Boolean

    // ============================================================================
    // MCP Management
    // ============================================================================

    @POST("mcp")
    suspend fun addMcpServer(@Body request: AddMcpServerRequest): McpStatusDto

    // ============================================================================
    // Logging
    // ============================================================================

    @POST("log")
    suspend fun log(@Body request: LogRequest): Boolean

    // ============================================================================
    // PTY (Terminal) Endpoints
    // ============================================================================

    @GET("pty")
    suspend fun listPtySessions(): List<PtyDto>

    @POST("pty")
    suspend fun createPtySession(@Body request: CreatePtyRequest): PtyDto

    @GET("pty/{id}")
    suspend fun getPtySession(@Path("id") id: String): PtyDto

    @DELETE("pty/{id}")
    suspend fun deletePtySession(@Path("id") id: String): Boolean

    @PATCH("pty/{id}")
    suspend fun updatePtySession(
        @Path("id") id: String,
        @Body request: UpdatePtyRequest
    ): PtyDto

    // ============================================================================
    // Experimental Tools Endpoints
    // ============================================================================

    @GET("experimental/tool/ids")
    suspend fun getToolIds(): List<String>

    @GET("experimental/tool")
    suspend fun getTools(
        @Query("provider") provider: String,
        @Query("model") model: String
    ): ToolListDto

    // ============================================================================
    // Config Providers Endpoint
    // ============================================================================

    @GET("config/providers")
    suspend fun getConfigProviders(): ConfigProvidersDto
}
