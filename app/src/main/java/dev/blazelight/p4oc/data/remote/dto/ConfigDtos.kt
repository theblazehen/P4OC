package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ============================================================================
// Config Types (aligned with SDK Config type)
// ============================================================================

@Serializable
data class ConfigDto(
    val theme: String? = null,
    val logLevel: String? = null,
    val model: String? = null,
    @SerialName("small_model") val smallModel: String? = null,
    val username: String? = null,
    val share: String? = null, // "manual" | "auto" | "disabled"
    val autoupdate: JsonElement? = null, // boolean | "notify"
    @SerialName("disabled_providers") val disabledProviders: List<String>? = null,
    @SerialName("enabled_providers") val enabledProviders: List<String>? = null,
    val agent: Map<String, AgentConfigDto>? = null,
    val provider: Map<String, ProviderConfigDto>? = null,
    val mcp: Map<String, McpConfigDto>? = null,
    val tools: Map<String, Boolean>? = null,
    val permission: JsonElement? = null,
    val instructions: List<String>? = null
)

@Serializable
data class AgentConfigDto(
    val model: String? = null,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    val prompt: String? = null,
    val tools: Map<String, Boolean>? = null,
    val disable: Boolean? = null,
    val description: String? = null,
    val mode: String? = null, // "subagent" | "primary" | "all"
    val color: String? = null,
    val maxSteps: Int? = null,
    val permission: JsonElement? = null
)

@Serializable
data class ProviderConfigDto(
    val api: String? = null,
    val name: String? = null,
    val env: List<String>? = null,
    val id: String? = null,
    val npm: String? = null,
    val models: Map<String, ModelConfigDto>? = null,
    val whitelist: List<String>? = null,
    val blacklist: List<String>? = null,
    val options: ProviderOptionsDto? = null
)

@Serializable
data class ProviderOptionsDto(
    val apiKey: String? = null,
    val baseURL: String? = null,
    val enterpriseUrl: String? = null,
    val setCacheKey: Boolean? = null,
    val timeout: Int? = null
)

@Serializable
data class ModelConfigDto(
    val id: String? = null,
    val name: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val attachment: Boolean? = null,
    val reasoning: Boolean? = null,
    val temperature: Boolean? = null,
    @SerialName("tool_call") val toolCall: Boolean? = null,
    val cost: ModelCostConfigDto? = null,
    val limit: ModelLimitDto? = null,
    val experimental: Boolean? = null,
    val status: String? = null,
    val options: JsonObject? = null,
    val headers: Map<String, String>? = null
)

@Serializable
data class ModelCostConfigDto(
    val input: Double = 0.0,
    val output: Double = 0.0,
    @SerialName("cache_read") val cacheRead: Double? = null,
    @SerialName("cache_write") val cacheWrite: Double? = null
)

@Serializable
data class McpConfigDto(
    val type: String, // "local" | "remote"
    // Local MCP
    val command: List<String>? = null,
    val environment: Map<String, String>? = null,
    // Remote MCP
    val url: String? = null,
    val headers: Map<String, String>? = null,
    val oauth: JsonElement? = null, // McpOAuthConfig | false
    // Common
    val enabled: Boolean? = null,
    val timeout: Int? = null
)

@Serializable
data class AddMcpServerRequest(
    val name: String,
    val config: McpConfigDto
)

// ============================================================================
// Config Providers Types
// ============================================================================

@Serializable
data class ConfigProvidersDto(
    val providers: List<ProviderDto> = emptyList(),
    val default: Map<String, String> = emptyMap()
)
