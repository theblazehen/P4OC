package com.pocketcode.di

import com.pocketcode.core.datastore.SettingsDataStore
import com.pocketcode.core.network.OpenCodeApi
import com.pocketcode.core.network.OpenCodeEventSource
import com.pocketcode.data.remote.mapper.EventMapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

    @Provides
    @Singleton
    @Named("baseUrl")
    fun provideBaseUrlInterceptor(
        settingsDataStore: SettingsDataStore
    ): Interceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val baseUrl = settingsDataStore.getCachedServerUrl()
        
        val newUrl = originalRequest.url.newBuilder()
            .scheme(if (baseUrl.startsWith("https")) "https" else "http")
            .host(baseUrl.removePrefix("http://").removePrefix("https://").split(":").first().split("/").first())
            .port(baseUrl.split(":").lastOrNull()?.split("/")?.firstOrNull()?.toIntOrNull() ?: 4096)
            .build()
        
        val newRequest = originalRequest.newBuilder()
            .url(newUrl)
            .build()
        
        chain.proceed(newRequest)
    }

    @Provides
    @Singleton
    @Named("auth")
    fun provideAuthInterceptor(
        settingsDataStore: SettingsDataStore
    ): Interceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val username = settingsDataStore.getCachedUsername()
        val password = settingsDataStore.getCachedPassword()

        val request = if (username != null && password != null) {
            val credentials = okhttp3.Credentials.basic(username, password)
            originalRequest.newBuilder()
                .header("Authorization", credentials)
                .build()
        } else {
            originalRequest
        }

        chain.proceed(request)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        @Named("auth") authInterceptor: Interceptor,
        @Named("baseUrl") baseUrlInterceptor: Interceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(baseUrlInterceptor)
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @Named("sse")
    fun provideSseOkHttpClient(
        @Named("auth") authInterceptor: Interceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        // No body logging for SSE - it blocks forever trying to read the stream
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // No timeout for SSE
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
        settingsDataStore: SettingsDataStore
    ): Retrofit {
        val baseUrl = settingsDataStore.getCachedServerUrl()
        val contentType = "application/json".toMediaType()
        
        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenCodeApi(retrofit: Retrofit): OpenCodeApi =
        retrofit.create(OpenCodeApi::class.java)

    @Provides
    @Singleton
    fun provideOpenCodeEventSource(
        @Named("sse") sseClient: OkHttpClient,
        json: Json,
        settingsDataStore: SettingsDataStore,
        eventMapper: EventMapper
    ): OpenCodeEventSource = OpenCodeEventSource(
        okHttpClient = sseClient,
        json = json,
        settingsDataStore = settingsDataStore,
        eventMapper = eventMapper
    )
}
