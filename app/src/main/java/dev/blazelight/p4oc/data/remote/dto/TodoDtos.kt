package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.Serializable

// ============================================================================
// Todo Types (aligned with SDK Todo type)
// ============================================================================

@Serializable
data class TodoDto(
    val id: String,
    val content: String,
    val status: String, // "pending" | "in_progress" | "completed" | "cancelled"
    val priority: String // "high" | "medium" | "low"
)
