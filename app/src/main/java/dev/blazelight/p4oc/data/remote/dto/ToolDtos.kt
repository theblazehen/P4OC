package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ============================================================================
// Experimental Tools Types
// ============================================================================

@Serializable
data class ToolListDto(
    val tools: List<ToolDto> = emptyList()
)

@Serializable
data class ToolDto(
    val id: String,
    val description: String? = null,
    val parameters: JsonObject? = null
)
