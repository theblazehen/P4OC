package dev.blazelight.p4oc.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.blazelight.p4oc.core.database.dao.MessageDao
import dev.blazelight.p4oc.core.database.dao.ServerConfigDao
import dev.blazelight.p4oc.core.database.dao.SessionDao
import dev.blazelight.p4oc.core.database.entity.MessageEntity
import dev.blazelight.p4oc.core.database.entity.PartEntity
import dev.blazelight.p4oc.core.database.entity.ServerConfigEntity
import dev.blazelight.p4oc.core.database.entity.SessionEntity

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
