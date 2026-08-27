package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ============================================================================
// Session Types
// ============================================================================

@Serializable
data class TimeDto(
    val created: Long,
    val updated: Long? = null,
    val compacting: Long? = null
)

@Serializable
data class SessionDto(
    val id: String,
    val slug: String? = null,
    @SerialName("projectID") val projectID: String,
    @SerialName("workspaceID") val workspaceID: String? = null,
    val directory: String,
    val path: String? = null,
    @SerialName("parentID") val parentID: String? = null,
    val title: String,
    val version: String,
    val time: TimeDto,
    val summary: SessionSummaryDto? = null,
    val cost: Double? = null,
    val tokens: TokenUsageDto? = null,
    val share: SessionShareDto? = null,
    val agent: String? = null,
    val model: SessionModelDto? = null,
    val metadata: JsonObject? = null,
    val permission: JsonElement? = null,
    val revert: SessionRevertDto? = null
)

@Serializable
data class SessionModelDto(
    val id: String,
    @SerialName("providerID") val providerID: String,
    val variant: String? = null,
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

/** Current response shape for `GET /session/{sessionID}/diff`. */
@Serializable
data class SnapshotFileDiffDto(
    val file: String? = null,
    val patch: String? = null,
    val additions: Double,
    val deletions: Double,
    val status: String? = null,
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
    @SerialName("messageID") val messageID: String? = null
)

@Serializable
data class RevertSessionRequest(
    @SerialName("messageID") val messageID: String,
    @SerialName("partID") val partID: String? = null
)

@Serializable
data class InitSessionRequest(
    @SerialName("messageID") val messageID: String,
    @SerialName("providerID") val providerID: String,
    @SerialName("modelID") val modelID: String
)

// SummarizeSessionRequest removed — the /summarize endpoint body is optional
// and the server uses its own default provider/model when omitted.
