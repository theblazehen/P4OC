package com.pocketcode.data.remote.dto

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class HealthResponse(
    val status: String,
    val version: String? = null
)

@Serializable
data class SessionDto(
    val id: String,
    val slug: String,
    @SerialName("projectID") val projectID: String,
    val directory: String,
    @SerialName("parentID") val parentID: String? = null,
    val title: String,
    val version: String,
    @SerialName("createdAt") val createdAt: Instant,
    @SerialName("updatedAt") val updatedAt: Instant,
    @SerialName("archivedAt") val archivedAt: Instant? = null,
    val summary: SessionSummaryDto? = null,
    @SerialName("shareUrl") val shareUrl: String? = null
)

@Serializable
data class SessionSummaryDto(
    val additions: Int,
    val deletions: Int,
    val files: Int
)

@Serializable
data class SessionStatusDto(
    val status: String,
    val attempt: Int? = null,
    val message: String? = null
)

@Serializable
data class CreateSessionRequest(
    @SerialName("parentID") val parentID: String? = null,
    val title: String? = null
)

@Serializable
data class UpdateSessionRequest(
    val title: String? = null,
    val archived: Boolean? = null
)

@Serializable
data class ForkSessionRequest(
    @SerialName("messageID") val messageID: String
)

@Serializable
data class MessageDto(
    val id: String,
    @SerialName("sessionID") val sessionID: String,
    @SerialName("createdAt") val createdAt: Instant,
    val role: String,
    @SerialName("completedAt") val completedAt: Instant? = null,
    @SerialName("parentID") val parentID: String? = null,
    @SerialName("providerID") val providerID: String? = null,
    @SerialName("modelID") val modelID: String? = null,
    val agent: String? = null,
    val cost: Double? = null,
    val tokens: TokenUsageDto? = null,
    val error: MessageErrorDto? = null,
    val model: ModelRefDto? = null
)

@Serializable
data class ModelRefDto(
    @SerialName("providerID") val providerID: String,
    @SerialName("modelID") val modelID: String
)

@Serializable
data class TokenUsageDto(
    val input: Int = 0,
    val output: Int = 0,
    val reasoning: Int = 0,
    @SerialName("cacheRead") val cacheRead: Int = 0,
    @SerialName("cacheWrite") val cacheWrite: Int = 0
)

@Serializable
data class MessageErrorDto(
    val code: String,
    val message: String
)

@Serializable
data class MessageWithPartsDto(
    val message: MessageDto,
    val parts: List<PartDto>
)

@Serializable
data class PartDto(
    val id: String,
    @SerialName("sessionID") val sessionID: String,
    @SerialName("messageID") val messageID: String,
    val type: String,
    val text: String? = null,
    @SerialName("callID") val callID: String? = null,
    @SerialName("toolName") val toolName: String? = null,
    val state: ToolStateDto? = null,
    val mime: String? = null,
    val filename: String? = null,
    val url: String? = null,
    val hash: String? = null,
    val files: List<String>? = null
)

@Serializable
data class ToolStateDto(
    val status: String,
    val input: JsonObject,
    @SerialName("rawInput") val rawInput: String? = null,
    val title: String? = null,
    val output: String? = null,
    val error: String? = null,
    @SerialName("startedAt") val startedAt: Instant? = null,
    @SerialName("endedAt") val endedAt: Instant? = null,
    val metadata: JsonObject? = null
)

@Serializable
data class SendMessageRequest(
    val model: ModelRefDto? = null,
    val agent: String? = null,
    val parts: List<PartInputDto>
)

@Serializable
data class PartInputDto(
    val type: String,
    val text: String? = null
)

@Serializable
data class PermissionDto(
    val id: String,
    val type: String,
    @SerialName("sessionID") val sessionID: String,
    @SerialName("messageID") val messageID: String,
    val title: String,
    val metadata: JsonObject,
    @SerialName("createdAt") val createdAt: Instant
)

@Serializable
data class PermissionResponseRequest(
    val response: String,
    val remember: Boolean? = null
)

@Serializable
data class FileNodeDto(
    val name: String,
    val path: String,
    @SerialName("isDirectory") val isDirectory: Boolean,
    val size: Long? = null,
    val children: List<FileNodeDto>? = null
)

@Serializable
data class FileContentDto(
    val path: String,
    val content: String,
    @SerialName("mimeType") val mimeType: String? = null,
    val size: Long? = null
)

@Serializable
data class FileStatusDto(
    val path: String,
    val status: String,
    val staged: Boolean = false
)

@Serializable
data class SearchResultDto(
    val file: String,
    val line: Int,
    val column: Int,
    val match: String,
    val context: String
)

@Serializable
data class ConfigDto(
    val providers: Map<String, ProviderConfigDto>? = null,
    val agents: Map<String, AgentConfigDto>? = null
)

@Serializable
data class ProviderConfigDto(
    val id: String,
    val name: String,
    val models: List<ModelConfigDto>? = null
)

@Serializable
data class ModelConfigDto(
    val id: String,
    val name: String,
    val options: JsonObject? = null
)

@Serializable
data class AgentConfigDto(
    val id: String,
    val name: String,
    val description: String? = null
)

@Serializable
data class ProvidersDto(
    val providers: List<ProviderInfoDto>
)

@Serializable
data class ProviderInfoDto(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val models: List<ModelInfoDto>? = null
)

@Serializable
data class ModelInfoDto(
    val id: String,
    val name: String
)

@Serializable
data class AgentDto(
    val id: String,
    val name: String,
    val description: String? = null
)

@Serializable
data class EventDataDto(
    val type: String,
    val properties: JsonObject
)
