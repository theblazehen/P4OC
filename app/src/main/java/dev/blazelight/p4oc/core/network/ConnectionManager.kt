package dev.blazelight.p4oc.core.network

import android.util.Log
import dev.blazelight.p4oc.data.remote.mapper.EventMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionManager @Inject constructor(
    private val json: Json,
    private val eventMapper: EventMapper,
    private val directoryManager: DirectoryManager
) {
    companion object {
        private const val TAG = "ConnectionManager"
    }

    private val _connection = MutableStateFlow<Connection?>(null)
    val connection: StateFlow<Connection?> = _connection.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    val isConnected: Boolean
        get() = _connection.value != null && _connectionState.value is ConnectionState.Connected

    fun requireApi(): OpenCodeApi {
        return _connection.value?.api
            ?: throw IllegalStateException("Not connected to any server")
    }

    fun getApi(): OpenCodeApi? = _connection.value?.api

    fun getEventSource(): OpenCodeEventSource? = _connection.value?.eventSource

    suspend fun connect(config: ServerConfig, password: String? = null): Result<Unit> {
        Log.d(TAG, "Connecting to ${config.url}")
        
        disconnect()
        _connectionState.value = ConnectionState.Connecting

        return try {
            val okHttpClient = buildOkHttpClient(config, password)
            val retrofit = buildRetrofit(config.url, okHttpClient)
            val api = retrofit.create(OpenCodeApi::class.java)

            val healthResult = runCatching { api.health() }
            
            if (healthResult.isFailure) {
                val error = healthResult.exceptionOrNull()
                Log.e(TAG, "Health check failed", error)
                _connectionState.value = ConnectionState.Error(error?.message ?: "Connection failed")
                return Result.failure(error ?: Exception("Connection failed"))
            }

            Log.d(TAG, "Health check passed, starting SSE")

            val sseClient = buildSseOkHttpClient(config, password)
            val eventSource = OpenCodeEventSource(
                okHttpClient = sseClient,
                json = json,
                baseUrl = config.url,
                eventMapper = eventMapper,
                directoryProvider = { directoryManager.getDirectory() }
            )

            val connection = Connection(config, api, eventSource)
            _connection.value = connection
            _connectionState.value = ConnectionState.Connected

            directoryManager.setOnDirectoryChangedListener {
                eventSource.reconnect()
            }

            eventSource.connect()

            Log.d(TAG, "Connected successfully to ${config.url}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting")
        _connection.value?.disconnect()
        _connection.value = null
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun buildOkHttpClient(config: ServerConfig, password: String?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })

        if (config.username != null && password != null) {
            builder.addInterceptor(createAuthInterceptor(config.username, password))
        }

        return builder.build()
    }

    private fun buildSseOkHttpClient(config: ServerConfig, password: String?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })

        if (config.username != null && password != null) {
            builder.addInterceptor(createAuthInterceptor(config.username, password))
        }

        return builder.build()
    }

    private fun createAuthInterceptor(username: String, password: String): Interceptor {
        return Interceptor { chain ->
            val credentials = Credentials.basic(username, password)
            val request = chain.request().newBuilder()
                .header("Authorization", credentials)
                .build()
            chain.proceed(request)
        }
    }

    private fun buildRetrofit(baseUrl: String, client: OkHttpClient): Retrofit {
        val normalizedUrl = baseUrl.trimEnd('/') + "/"
        val contentType = "application/json".toMediaType()

        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
}
