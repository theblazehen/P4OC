package com.pocketcode.core.database.dao

import androidx.room.*
import com.pocketcode.core.database.entity.ServerConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerConfigDao {
    @Query("SELECT * FROM server_configs ORDER BY lastConnected DESC")
    fun getAllServerConfigs(): Flow<List<ServerConfigEntity>>

    @Query("SELECT * FROM server_configs WHERE id = :id")
    suspend fun getServerConfigById(id: String): ServerConfigEntity?

    @Query("SELECT * FROM server_configs WHERE isLocal = 1 LIMIT 1")
    suspend fun getLocalServerConfig(): ServerConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServerConfig(config: ServerConfigEntity)

    @Update
    suspend fun updateServerConfig(config: ServerConfigEntity)

    @Delete
    suspend fun deleteServerConfig(config: ServerConfigEntity)

    @Query("DELETE FROM server_configs WHERE id = :id")
    suspend fun deleteServerConfigById(id: String)
}
