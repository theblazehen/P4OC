package dev.blazelight.p4oc.core.database.dao

import androidx.room.*
import dev.blazelight.p4oc.core.database.entity.MessageEntity
import dev.blazelight.p4oc.core.database.entity.PartEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionID = :sessionId ORDER BY createdAt ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE sessionID = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("SELECT * FROM parts WHERE messageID = :messageId ORDER BY insertedAt ASC, id ASC")
    fun getPartsForMessage(messageId: String): Flow<List<PartEntity>>

    @Query("SELECT * FROM parts WHERE sessionID = :sessionId ORDER BY insertedAt ASC, id ASC")
    fun getPartsForSession(sessionId: String): Flow<List<PartEntity>>

    @Query("SELECT * FROM parts WHERE id = :id")
    suspend fun getPartById(id: String): PartEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPart(part: PartEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParts(parts: List<PartEntity>)

    @Update
    suspend fun updatePart(part: PartEntity)

    @Delete
    suspend fun deletePart(part: PartEntity)

    @Query("DELETE FROM parts WHERE messageID = :messageId")
    suspend fun deletePartsForMessage(messageId: String)

    @Transaction
    suspend fun insertMessageWithParts(message: MessageEntity, parts: List<PartEntity>) {
        insertMessage(message)
        insertParts(parts)
    }
}
