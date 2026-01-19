package com.pocketcode.core.network

import com.pocketcode.data.remote.dto.*
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
    suspend fun listSessions(): List<SessionDto>

    @POST("session")
    suspend fun createSession(@Body request: CreateSessionRequest): SessionDto

    @GET("session/{id}")
    suspend fun getSession(@Path("id") id: String): SessionDto

    @DELETE("session/{id}")
    suspend fun deleteSession(@Path("id") id: String): Boolean

    @PATCH("session/{id}")
    suspend fun updateSession(
        @Path("id") id: String,
        @Body request: UpdateSessionRequest
    ): SessionDto

    @GET("session/status")
    suspend fun getSessionStatuses(): Map<String, SessionStatusDto>

    @POST("session/{id}/abort")
    suspend fun abortSession(@Path("id") id: String): Boolean

    @POST("session/{id}/fork")
    suspend fun forkSession(
        @Path("id") id: String,
        @Body request: ForkSessionRequest
    ): SessionDto

    @GET("session/{id}/children")
    suspend fun getSessionChildren(@Path("id") id: String): List<SessionDto>

    @GET("session/{id}/todo")
    suspend fun getSessionTodos(@Path("id") id: String): List<TodoDto>

    @POST("session/{id}/init")
    suspend fun initSession(
        @Path("id") id: String,
        @Body request: InitSessionRequest
    ): Boolean

    @POST("session/{id}/share")
    suspend fun shareSession(@Path("id") id: String): SessionDto

    @DELETE("session/{id}/share")
    suspend fun unshareSession(@Path("id") id: String): SessionDto

    @GET("session/{id}/diff")
    suspend fun getSessionDiff(
        @Path("id") id: String,
        @Query("messageID") messageID: String? = null
    ): List<FileDiffDto>

    @POST("session/{id}/summarize")
    suspend fun summarizeSession(
        @Path("id") id: String,
        @Body request: SummarizeSessionRequest
    ): Boolean

    @POST("session/{id}/revert")
    suspend fun revertSession(
        @Path("id") id: String,
        @Body request: RevertSessionRequest
    ): Boolean

    @POST("session/{id}/unrevert")
    suspend fun unrevertSession(@Path("id") id: String): Boolean

    @GET("session/{sessionId}/message")
    suspend fun getMessages(
        @Path("sessionId") sessionId: String,
        @Query("limit") limit: Int? = null
    ): List<MessageWrapperDto>

    @GET("session/{sessionId}/message/{messageId}")
    suspend fun getMessage(
        @Path("sessionId") sessionId: String,
        @Path("messageId") messageId: String
    ): MessageWrapperDto

    @POST("session/{sessionId}/message")
    suspend fun sendMessage(
        @Path("sessionId") sessionId: String,
        @Body request: SendMessageRequest
    ): MessageWrapperDto

    @POST("session/{sessionId}/prompt_async")
    suspend fun sendMessageAsync(
        @Path("sessionId") sessionId: String,
        @Body request: SendMessageRequest
    )

    @POST("session/{sessionId}/command")
    suspend fun executeCommand(
        @Path("sessionId") sessionId: String,
        @Body request: ExecuteCommandRequest
    ): MessageWrapperDto

    @POST("session/{sessionId}/shell")
    suspend fun executeShellCommand(
        @Path("sessionId") sessionId: String,
        @Body request: ShellCommandRequest
    ): MessageWrapperDto

    @POST("session/{sessionId}/permissions/{permissionId}")
    suspend fun respondToPermission(
        @Path("sessionId") sessionId: String,
        @Path("permissionId") permissionId: String,
        @Body request: PermissionResponseRequest
    ): Boolean

    @POST("session/{sessionId}/questions/{questionId}")
    suspend fun respondToQuestion(
        @Path("sessionId") sessionId: String,
        @Path("questionId") questionId: String,
        @Body request: QuestionReplyRequest
    ): Boolean

    @GET("command")
    suspend fun listCommands(): List<CommandDto>

    @GET("file")
    suspend fun listFiles(@Query("path") path: String?): List<FileNodeDto>

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
