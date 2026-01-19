package com.pocketcode.di

import android.content.Context
import com.pocketcode.core.datastore.SettingsDataStore
import com.pocketcode.core.termux.TermuxBridge
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false  // Don't encode null/default values - server rejects explicit nulls
        explicitNulls = false   // Omit null fields from JSON output
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context
    ): SettingsDataStore = SettingsDataStore(context)

    @Provides
    @Singleton
    fun provideTermuxBridge(
        @ApplicationContext context: Context
    ): TermuxBridge = TermuxBridge(context)
}
