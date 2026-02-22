package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ============================================================================
// Provider Types (aligned with SDK Provider type)
// ============================================================================

@Serializable
data class ProviderDto(
    val id: String,
    val name: String,
    val source: String, // "env" | "config" | "custom" | "api"
    val env: List<String> = emptyList(),
    val key: String? = null,
    val options: JsonObject? = null,
    val models: Map<String, ModelDto> = emptyMap()
)

@Serializable
data class ModelDto(
    val id: String,
    @SerialName("providerID") val providerId: String,
    val api: ModelApiDto? = null,
    val name: String,
    val capabilities: ModelCapabilitiesDto? = null,
    val cost: ModelCostDto? = null,
    val limit: ModelLimitDto? = null,
    val status: String? = null, // "alpha" | "beta" | "deprecated" | "active"
    val options: JsonObject? = null,
    val headers: Map<String, String>? = null,
    val contextLength: Int? = null,
    val inputCostPer1k: Double? = null,
    val outputCostPer1k: Double? = null,
    val supportsTools: Boolean? = null,
    val supportsReasoning: Boolean? = null
)

@Serializable
data class ModelApiDto(
    val id: String? = null,
    val url: String? = null,
    val npm: String? = null
)

@Serializable
data class ModelCapabilitiesDto(
    val temperature: Boolean = false,
    val reasoning: Boolean = false,
    val attachment: Boolean = false,
    val toolcall: Boolean = false,
    val input: ModalitiesDto? = null,
    val output: ModalitiesDto? = null
)

@Serializable
data class ModalitiesDto(
    val text: Boolean = true,
    val audio: Boolean = false,
    val image: Boolean = false,
    val video: Boolean = false,
    val pdf: Boolean = false
)

@Serializable
data class ModelCostDto(
    val input: Double = 0.0,
    val output: Double = 0.0,
    val cache: CacheCostDto? = null
)

@Serializable
data class CacheCostDto(
    val read: Double = 0.0,
    val write: Double = 0.0
)

@Serializable
data class ModelLimitDto(
    val context: Int = 0,
    val output: Int = 0
)

@Serializable
data class ProvidersResponseDto(
    val all: List<ProviderDto>,
    val default: Map<String, String> = emptyMap(),
    val connected: List<String> = emptyList()
)

@Serializable
data class ProviderAuthMethodDto(
    val type: String, // "oauth" | "api"
    val label: String
)

@Serializable
data class ProviderAuthAuthorizationDto(
    val url: String,
    val method: String, // "auto" | "code"
    val instructions: String
)
