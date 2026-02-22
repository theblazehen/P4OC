package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.Serializable

// ============================================================================
// Common Types
// ============================================================================

@Serializable
data class HealthResponse(
    val healthy: Boolean,
    val version: String? = null
)

@Serializable
data class LogRequest(
    val service: String,
    val level: String,
    val message: String,
    val extra: kotlinx.serialization.json.JsonObject? = null
)
