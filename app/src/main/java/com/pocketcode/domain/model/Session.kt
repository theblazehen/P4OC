package com.pocketcode.domain.model

import kotlinx.serialization.Serializable

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
    val summary: SessionSummary? = null,
    val shareUrl: String? = null
)

@Serializable
data class SessionSummary(
    val additions: Int,
    val deletions: Int,
    val files: Int
)
