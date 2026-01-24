package dev.blazelight.p4oc.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "parts",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageID"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("messageID"), Index("sessionID")]
)
data class PartEntity(
    @PrimaryKey val id: String,
    val sessionID: String,
    val messageID: String,
    val type: String,
    val text: String?,
    val callID: String?,
    val toolName: String?,
    val toolStateStatus: String?,
    val toolStateInput: String?,
    val toolStateRawInput: String?,
    val toolStateTitle: String?,
    val toolStateOutput: String?,
    val toolStateError: String?,
    val toolStateStartedAt: Long?,
    val toolStateEndedAt: Long?,
    val toolStateMetadata: String?,
    val mime: String?,
    val filename: String?,
    val url: String?,
    val hash: String?,
    val files: List<String>?
)
