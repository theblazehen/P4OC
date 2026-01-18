package com.pocketcode.domain.model

import kotlinx.serialization.Serializable

// ============================================================================
// Project
// ============================================================================

@Serializable
data class Project(
    val id: String,
    val worktree: String,
    val vcsDir: String? = null,
    val vcs: String? = null,
    val createdAt: Long,
    val initializedAt: Long? = null
)

// ============================================================================
// VCS
// ============================================================================

@Serializable
data class VcsInfo(
    val branch: String
)

// ============================================================================
// Path
// ============================================================================

@Serializable
data class PathInfo(
    val state: String,
    val config: String,
    val worktree: String,
    val directory: String
)

// ============================================================================
// Session
// ============================================================================

@Serializable
data class Session(
    val id: String,
    val projectID: String,
    val directory: String,
    val parentID: String? = null,
    val title: String,
    val version: String,
    val createdAt: Long,
    val updatedAt: Long,
    val compactingAt: Long? = null,
    val summary: SessionSummary? = null,
    val shareUrl: String? = null,
    val revert: SessionRevert? = null
)

@Serializable
data class SessionSummary(
    val additions: Int,
    val deletions: Int,
    val files: Int,
    val diffs: List<FileDiff>? = null
)

@Serializable
data class FileDiff(
    val file: String,
    val before: String,
    val after: String,
    val additions: Int,
    val deletions: Int
)

@Serializable
data class SessionRevert(
    val messageID: String,
    val partID: String? = null,
    val snapshot: String? = null,
    val diff: String? = null
)
