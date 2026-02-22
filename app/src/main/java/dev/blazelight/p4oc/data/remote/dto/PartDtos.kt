package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ============================================================================
// Part Types (aligned with SDK Part union type)
// ============================================================================

@Serializable
data class PartDto(
    val id: String,
    @SerialName("sessionID") val sessionID: String,
    @SerialName("messageID") val messageID: String,
    val type: String, // "text" | "reasoning" | "tool" | "file" | "patch" | "step-start" | "step-finish" | "snapshot" | "agent" | "retry" | "compaction" | "subtask"
    val time: PartTimeDto? = null,
    // TextPart
    val text: String? = null,
    val synthetic: Boolean? = null,
    val ignored: Boolean? = null,
    // ToolPart
    @SerialName("callID") val callID: String? = null,
    @SerialName("tool") val toolName: String? = null,
    val state: ToolStateDto? = null,
    // FilePart
    val mime: String? = null,
    val filename: String? = null,
    val url: String? = null,
    val source: JsonObject? = null,
    // PatchPart
    val hash: String? = null,
    val files: List<String>? = null,
    // StepStartPart, StepFinishPart, SnapshotPart
    val snapshot: String? = null,
    // StepFinishPart
    val reason: String? = null,
    val cost: Double? = null,
    val tokens: TokenUsageDto? = null,
    // AgentPart
    val name: String? = null,
    // RetryPart
    val attempt: Int? = null,
    val error: MessageErrorDto? = null,
    // CompactionPart
    val auto: Boolean? = null,
    // SubtaskPart
    val prompt: String? = null,
    val description: String? = null,
    val agent: String? = null,
    // Common metadata
    val metadata: JsonObject? = null
)

@Serializable
data class PartTimeDto(
    val start: Long? = null,
    val end: Long? = null,
    val compacted: Long? = null
)

@Serializable
data class ToolStateDto(
    val status: String, // "pending" | "running" | "completed" | "error"
    val input: JsonObject? = null,
    val raw: String? = null,
    val title: String? = null,
    val output: String? = null,
    val error: String? = null,
    val time: PartTimeDto? = null,
    val metadata: JsonObject? = null,
    val attachments: List<PartDto>? = null
)

@Serializable
data class ModelInput(
    val providerID: String,
    val modelID: String
)

@Serializable
data class SendMessageRequest(
    @SerialName("messageID") val messageID: String? = null,
    val model: ModelInput? = null,
    val agent: String? = null,
    val noReply: Boolean? = null,
    val system: String? = null,
    val tools: JsonObject? = null,
    val parts: List<PartInputDto>
)

@Serializable
data class PartInputDto(
    val id: String? = null,
    val type: String, // "text" | "file" | "agent" | "subtask"
    // TextPartInput
    val text: String? = null,
    val synthetic: Boolean? = null,
    val ignored: Boolean? = null,
    val time: PartTimeDto? = null,
    val metadata: JsonObject? = null,
    // FilePartInput
    val mime: String? = null,
    val filename: String? = null,
    val url: String? = null,
    val source: JsonObject? = null,
    // AgentPartInput
    val name: String? = null,
    // SubtaskPartInput
    val prompt: String? = null,
    val description: String? = null,
    val agent: String? = null
)

@Serializable
data class SetActiveModelRequest(
    val model: ModelInput
)
