package dev.blazelight.p4oc.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionID"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionID")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionID: String,
    val createdAt: Long,
    val role: String,
    val completedAt: Long?,
    val parentID: String?,
    val providerID: String?,
    val modelID: String?,
    val agent: String?,
    val cost: Double?,
    val tokensInput: Int?,
    val tokensOutput: Int?,
    val tokensReasoning: Int?,
    val tokensCacheRead: Int?,
    val tokensCacheWrite: Int?,
    val errorCode: String?,
    val errorMessage: String?
)
