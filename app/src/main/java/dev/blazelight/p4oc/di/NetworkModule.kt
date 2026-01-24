package dev.blazelight.p4oc.di

import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.DirectoryManager
import dev.blazelight.p4oc.data.remote.mapper.EventMapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideConnectionManager(
        json: Json,
        eventMapper: EventMapper,
        directoryManager: DirectoryManager
    ): ConnectionManager = ConnectionManager(json, eventMapper, directoryManager)
}
