package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ============================================================================
// Command Types (aligned with SDK Command type)
// ============================================================================

@Serializable
data class CommandDto(
    val name: String,
    val description: String? = null,
    val agent: String? = null,
    val model: String? = null,
    val template: JsonElement? = null,  // Can be String or Object (MCP commands use {})
    val subtask: Boolean? = null,
    val mcp: Boolean? = null
)

@Serializable
data class ExecuteCommandRequest(
    @SerialName("messageID") val messageID: String? = null,
    val agent: String? = null,
    val model: String? = null,
    val command: String,
    val arguments: String
)

@Serializable
data class ShellCommandRequest(
    val agent: String,
    val model: String? = null,
    val command: String
)
