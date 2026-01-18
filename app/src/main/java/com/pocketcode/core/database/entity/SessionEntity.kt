package com.pocketcode.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val slug: String,
    val projectID: String,
    val directory: String,
    val parentID: String?,
    val title: String?,
    val version: String,
    val createdAt: Long,
    val updatedAt: Long,
    val summaryAdditions: Int?,
    val summaryDeletions: Int?,
    val summaryFiles: Int?,
    val shareUrl: String?
)
