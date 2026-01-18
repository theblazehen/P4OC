package com.pocketcode.di

import android.content.Context
import androidx.room.Room
import com.pocketcode.core.database.PocketCodeDatabase
import com.pocketcode.core.database.dao.MessageDao
import com.pocketcode.core.database.dao.ServerConfigDao
import com.pocketcode.core.database.dao.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): PocketCodeDatabase = Room.databaseBuilder(
        context,
        PocketCodeDatabase::class.java,
        "pocketcode.db"
    )
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    @Singleton
    fun provideSessionDao(database: PocketCodeDatabase): SessionDao =
        database.sessionDao()

    @Provides
    @Singleton
    fun provideMessageDao(database: PocketCodeDatabase): MessageDao =
        database.messageDao()

    @Provides
    @Singleton
    fun provideServerConfigDao(database: PocketCodeDatabase): ServerConfigDao =
        database.serverConfigDao()
}
