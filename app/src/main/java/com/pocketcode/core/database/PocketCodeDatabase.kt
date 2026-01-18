package com.pocketcode.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pocketcode.core.database.dao.MessageDao
import com.pocketcode.core.database.dao.ServerConfigDao
import com.pocketcode.core.database.dao.SessionDao
import com.pocketcode.core.database.entity.MessageEntity
import com.pocketcode.core.database.entity.PartEntity
import com.pocketcode.core.database.entity.ServerConfigEntity
import com.pocketcode.core.database.entity.SessionEntity

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        PartEntity::class,
        ServerConfigEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PocketCodeDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun serverConfigDao(): ServerConfigDao
}
