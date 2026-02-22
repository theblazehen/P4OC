package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.Serializable

// ============================================================================
// LSP, Formatter, MCP Status Types
// ============================================================================

@Serializable
data class LspStatusDto(
    val id: String,
    val name: String,
    val root: String,
    val status: String // "connected" | "error"
)

@Serializable
data class FormatterStatusDto(
    val name: String,
    val extensions: List<String>,
    val enabled: Boolean
)

@Serializable
data class McpStatusDto(
    val status: String, // "connected" | "disabled" | "failed" | "needs_auth" | "needs_client_registration"
    val error: String? = null
)
