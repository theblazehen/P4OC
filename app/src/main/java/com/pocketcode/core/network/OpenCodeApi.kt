package com.pocketcode.core.network

import com.pocketcode.data.remote.dto.*
import retrofit2.http.*

interface OpenCodeApi {

    @GET("global/health")
    suspend fun health(): HealthResponse

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

    @GET("session/{sessionId}/message")
    suspend fun getMessages(
        @Path("sessionId") sessionId: String,
        @Query("limit") limit: Int? = null
    ): List<MessageWithPartsDto>

    @POST("session/{sessionId}/message")
    suspend fun sendMessage(
        @Path("sessionId") sessionId: String,
        @Body request: SendMessageRequest
    ): MessageWithPartsDto

    @POST("session/{sessionId}/prompt_async")
    suspend fun sendMessageAsync(
        @Path("sessionId") sessionId: String,
        @Body request: SendMessageRequest
    )

    @POST("session/{sessionId}/permissions/{permissionId}")
    suspend fun respondToPermission(
        @Path("sessionId") sessionId: String,
        @Path("permissionId") permissionId: String,
        @Body request: PermissionResponseRequest
    ): Boolean

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

    @GET("config")
    suspend fun getConfig(): ConfigDto

    @GET("config/providers")
    suspend fun getProviders(): ProvidersDto

    @GET("agent")
    suspend fun getAgents(): List<AgentDto>
}
