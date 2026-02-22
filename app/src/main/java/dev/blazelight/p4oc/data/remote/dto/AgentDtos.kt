package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ============================================================================
// Agent Types (aligned with SDK Agent type)
// ============================================================================

@Serializable
data class AgentDto(
    val name: String,
    val description: String? = null,
    val mode: String? = null, // "subagent" | "primary" | "all"
    val builtIn: Boolean = false,
    @SerialName("native") val isNative: Boolean = false,
    val hidden: Boolean? = null,
    val topP: Double? = null,
    val temperature: Double? = null,
    val color: String? = null,
    val permission: JsonElement? = null, // Array of PermissionRuleDto from server
    val model: ModelRefDto? = null,
    val prompt: String? = null,
    val tools: Map<String, Boolean>? = null,
    val options: JsonObject? = null,
    val maxSteps: Int? = null,
    val systemPrompt: String? = null,
    val isEnabled: Boolean? = null,
    val isBuiltIn: Boolean? = null
)

@Serializable
data class PermissionRuleDto(
    val permission: String,
    val pattern: String,
    val action: String
)
