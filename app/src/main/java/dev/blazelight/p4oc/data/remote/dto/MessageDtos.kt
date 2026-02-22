package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ============================================================================
// Message Types
// ============================================================================

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
    @SerialName("modelID") val modelID: String? = null,
    @SerialName("providerID") val providerID: String? = null,
    val agent: String? = null,
    val cost: Double? = null,
    val tokens: TokenUsageDto? = null,
    val error: MessageErrorDto? = null,
    val path: MessagePathDto? = null,
    val summary: JsonElement? = null,
    val finish: String? = null,
    val mode: String? = null,
    val system: String? = null,
    val tools: JsonObject? = null
)

@Serializable
data class MessageTimeDto(
    val created: Long,
    val completed: Long? = null
)

@Serializable
data class MessagePathDto(
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

// ============================================================================
// Error Types (aligned with SDK error union types)
// ============================================================================

@Serializable
data class MessageErrorDto(
    val name: String, // "ProviderAuthError" | "UnknownError" | "MessageOutputLengthError" | "MessageAbortedError" | "APIError"
    val data: JsonObject? = null
)

@Serializable
data class ProviderAuthErrorDataDto(
    @SerialName("providerID") val providerID: String,
    val message: String
)

@Serializable
data class ApiErrorDataDto(
    val message: String,
    val statusCode: Int? = null,
    val isRetryable: Boolean = false,
    val responseHeaders: Map<String, String>? = null,
    val responseBody: String? = null
)

@Serializable
data class MessageSummaryDto(
    val title: String? = null,
    val body: String? = null,
    val diffs: List<FileDiffDto> = emptyList()
)
