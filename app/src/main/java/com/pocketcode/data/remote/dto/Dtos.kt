package com.pocketcode.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class HealthResponse(
    val healthy: Boolean,
    val version: String? = null
)

@Serializable
data class TimeDto(
    val created: Long,
    val updated: Long? = null,
    val compacting: Long? = null
)

@Serializable
data class SessionDto(
    val id: String,
    @SerialName("projectID") val projectID: String,
    val directory: String,
    @SerialName("parentID") val parentID: String? = null,
    val title: String,
    val version: String,
    val time: TimeDto,
    val summary: SessionSummaryDto? = null,
    val share: SessionShareDto? = null,
    val revert: SessionRevertDto? = null
)

@Serializable
data class SessionSummaryDto(
    val additions: Int,
    val deletions: Int,
    val files: Int,
    val diffs: List<FileDiffDto>? = null
)

@Serializable
data class FileDiffDto(
    val file: String,
    val before: String,
    val after: String,
    val additions: Int,
    val deletions: Int
)

@Serializable
data class SessionShareDto(
    val url: String
)

@Serializable
data class SessionRevertDto(
    @SerialName("messageID") val messageID: String,
    @SerialName("partID") val partID: String? = null,
    val snapshot: String? = null,
    val diff: String? = null
)

@Serializable
data class SessionStatusDto(
    val type: String,
    val attempt: Int? = null,
    val message: String? = null,
    val next: Long? = null
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
data class MessageWrapperDto(
    val info: MessageInfoDto,
    val parts: List<PartDto>
)

@Serializable
data class MessageInfoDto(
    val id: String,
    @SerialName("sessionID") val sessionID: String,
    val time: MessageTimeDto,
    val role: String,
    @SerialName("parentID") val parentID: String? = null,
    val model: ModelRefDto? = null,
    val agent: String? = null,
    val cost: Double? = null,
    val tokens: TokenUsageDto? = null,
    val error: MessageErrorDto? = null,
    val path: PathDto? = null,
    val summary: Boolean? = null,
    val finish: String? = null,
    val mode: String? = null
)

@Serializable
data class MessageTimeDto(
    val created: Long,
    val completed: Long? = null
)

@Serializable
data class PathDto(
    val cwd: String? = null,
    val root: String? = null
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
    val cache: TokenCacheDto? = null
)

@Serializable
data class TokenCacheDto(
    val read: Int = 0,
    val write: Int = 0
)

@Serializable
data class MessageErrorDto(
    val name: String,
    val data: JsonObject? = null
)

@Serializable
data class PartDto(
    val id: String,
    @SerialName("sessionID") val sessionID: String,
    @SerialName("messageID") val messageID: String,
    val type: String,
    val time: PartTimeDto? = null,
    val text: String? = null,
    val synthetic: Boolean? = null,
    val ignored: Boolean? = null,
    @SerialName("callID") val callID: String? = null,
    @SerialName("tool") val toolName: String? = null,
    val state: ToolStateDto? = null,
    val mime: String? = null,
    val filename: String? = null,
    val url: String? = null,
    val hash: String? = null,
    val files: List<String>? = null,
    val metadata: JsonObject? = null
)

@Serializable
data class PartTimeDto(
    val start: Long? = null,
    val end: Long? = null
)

@Serializable
data class ToolStateDto(
    val status: String,
    val input: JsonObject? = null,
    val raw: String? = null,
    val title: String? = null,
    val output: String? = null,
    val error: String? = null,
    val time: PartTimeDto? = null,
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
    val metadata: JsonObject? = null,
    val time: TimeDto? = null
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
    val absolute: String,
    val type: String,
    val ignored: Boolean = false
)

@Serializable
data class FileContentDto(
    val type: String,
    val content: String,
    val diff: String? = null,
    val encoding: String? = null,
    val mimeType: String? = null
)

@Serializable
data class FileStatusDto(
    val path: String,
    val status: String,
    val added: Int = 0,
    val removed: Int = 0
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

@Serializable
data class TodoDto(
    val id: String,
    val content: String,
    val status: String,
    val priority: String
)
