package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ============================================================================
// Event Types (aligned with SDK Event union type)
// ============================================================================

@Serializable
data class EventDataDto(
    val type: String,
    val properties: JsonObject
)

@Serializable
data class GlobalEventDto(
    val directory: String,
    val payload: EventDataDto
)

// Event-specific property DTOs

@Serializable
data class TodoEventPropertiesDto(
    @SerialName("sessionID") val sessionID: String,
    val todos: List<TodoDto>
)

@Serializable
data class SessionDiffPropertiesDto(
    @SerialName("sessionID") val sessionID: String,
    val diff: List<FileDiffDto>
)

@Serializable
data class SessionErrorPropertiesDto(
    @SerialName("sessionID") val sessionID: String? = null,
    val error: MessageErrorDto? = null
)

@Serializable
data class FileEditedPropertiesDto(
    val file: String
)

@Serializable
data class MessageRemovedPropertiesDto(
    @SerialName("sessionID") val sessionID: String,
    @SerialName("messageID") val messageID: String
)

@Serializable
data class PartRemovedPropertiesDto(
    @SerialName("sessionID") val sessionID: String,
    @SerialName("messageID") val messageID: String,
    @SerialName("partID") val partID: String
)

@Serializable
data class PermissionRepliedPropertiesDto(
    @SerialName("sessionID") val sessionID: String,
    @SerialName("requestID") val requestID: String,
    val reply: String
)

@Serializable
data class FileWatcherPropertiesDto(
    val file: String,
    val event: String // "add" | "change" | "unlink"
)

@Serializable
data class VcsBranchPropertiesDto(
    val branch: String? = null
)

// ============================================================================
// Session Idle Event Types
// ============================================================================

@Serializable
data class SessionIdlePropertiesDto(
    @SerialName("sessionID") val sessionID: String
)

@Serializable
data class CommandExecutedPropertiesDto(
    val name: String,
    @SerialName("sessionID") val sessionID: String,
    val arguments: String,
    @SerialName("messageID") val messageID: String
)
