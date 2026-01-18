package com.pocketcode.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val id: String,
    val slug: String,
    val projectID: String,
    val directory: String,
    val parentID: String? = null,
    val title: String,
    val version: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null,
    val summary: SessionSummary? = null,
    val shareUrl: String? = null
)

@Serializable
data class SessionSummary(
    val additions: Int,
    val deletions: Int,
    val files: Int
)
