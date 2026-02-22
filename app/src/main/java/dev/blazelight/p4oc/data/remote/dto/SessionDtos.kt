package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    @SerialName("messageID") val messageID: String? = null
)

@Serializable
data class RevertSessionRequest(
    @SerialName("messageID") val messageID: String,
    @SerialName("partID") val partID: String? = null
)

@Serializable
data class SummarizeSessionRequest(
    @SerialName("providerID") val providerID: String,
    @SerialName("modelID") val modelID: String
)

@Serializable
data class InitSessionRequest(
    @SerialName("messageID") val messageID: String,
    @SerialName("providerID") val providerID: String,
    @SerialName("modelID") val modelID: String
)
