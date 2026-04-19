package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================================
// Project Types (aligned with SDK Project type)
// ============================================================================

@Serializable
data class ProjectDto(
    val id: String,
    val worktree: String,
    @SerialName("vcsDir") val vcsDir: String? = null,
    val vcs: String? = null, // "git" or null
    val time: ProjectTimeDto
)

@Serializable
data class ProjectTimeDto(
    val created: Long,
    val initialized: Long? = null
)

// ============================================================================
// VCS Types (aligned with SDK VcsInfo type)
// ============================================================================

@Serializable
data class VcsInfoDto(
    val branch: String? = null
)

// ============================================================================
// Path Types (aligned with SDK Path type)
// ============================================================================

@Serializable
data class PathInfoDto(
    val state: String,
    val config: String,
    val worktree: String,
    val directory: String
)
