package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ============================================================================
// Permission Types
// ============================================================================

@Serializable
data class PermissionDto(
    val id: String,
    val permission: String,
    val patterns: List<String>,
    @SerialName("sessionID") val sessionID: String,
    val metadata: JsonObject,
    val always: List<String>,
    val tool: PermissionToolDto? = null
)

@Serializable
data class PermissionToolDto(
    @SerialName("messageID") val messageID: String,
    @SerialName("callID") val callID: String
)

@Serializable
data class PermissionV2RequestListResponseDto(
    val data: List<PermissionV2RequestDto>
)

@Serializable
data class PermissionV2RequestDto(
    val id: String,
    @SerialName("sessionID") val sessionID: String,
    val action: String,
    val resources: List<String>,
    val save: List<String> = emptyList(),
    val metadata: JsonObject? = null,
    val source: PermissionV2SourceDto? = null
)

@Serializable
data class PermissionV2SourceDto(
    val type: String,
    @SerialName("messageID") val messageID: String,
    @SerialName("callID") val callID: String
)

@Serializable
data class PermissionResponseRequest(
    val reply: String,
    val message: String? = null
)
