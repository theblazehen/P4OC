package dev.blazelight.p4oc.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions",
    indices = [
        Index(value = ["projectID"]),
        Index(value = ["createdAt"]),
        Index(value = ["updatedAt"])
    ]
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val projectID: String,
    val directory: String,
    val parentID: String?,
    val title: String,
    val version: String,
    val createdAt: Long,
    val updatedAt: Long,
    val summaryAdditions: Int?,
    val summaryDeletions: Int?,
    val summaryFiles: Int?,
    val shareUrl: String?
)
