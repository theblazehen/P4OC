package com.pocketcode.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(tableName = "server_configs")
data class ServerConfigEntity(
    @PrimaryKey val id: String,
    val url: String,
    val name: String,
    val username: String?,
    val password: String?,
    val isLocal: Boolean,
    val lastConnected: Instant?
)
