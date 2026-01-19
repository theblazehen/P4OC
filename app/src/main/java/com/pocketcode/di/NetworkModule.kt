package com.pocketcode.di

import com.pocketcode.core.network.ConnectionManager
import com.pocketcode.data.remote.mapper.EventMapper
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
        eventMapper: EventMapper
    ): ConnectionManager = ConnectionManager(json, eventMapper)
}
