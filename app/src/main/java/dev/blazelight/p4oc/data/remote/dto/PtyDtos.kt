package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.Serializable

// ============================================================================
// PTY Types
// ============================================================================

@Serializable
data class PtyDto(
    val id: String,
    val title: String,
    val command: String,
    val args: List<String>,
    val cwd: String,
    val status: String,
    val pid: Int? = null  // Server may return null for pid
)

@Serializable
data class CreatePtyRequest(
    val command: String = "/bin/bash",
    val args: List<String> = emptyList(),
    val cwd: String = ".",
    val title: String = "Terminal",
    val env: Map<String, String> = emptyMap()
)

@Serializable
data class UpdatePtyRequest(
    val title: String? = null,
    val size: PtySizeDto? = null
)

@Serializable
data class PtySizeDto(
    val rows: Int,
    val cols: Int
)

@Serializable
data class PtyInputRequest(
    val data: String
)

@Serializable
data class PtyOutputDto(
    val lines: List<String> = emptyList(),
    val totalLines: Int = 0,
    val hasMore: Boolean = false
)
