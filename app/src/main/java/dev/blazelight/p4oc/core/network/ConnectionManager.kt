package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.BuildConfig
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.data.remote.mapper.EventMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit


class ConnectionManager constructor(
    private val json: Json,
    private val eventMapper: EventMapper,
    private val directoryManager: DirectoryManager
) {
    companion object {
        private const val TAG = "ConnectionManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sseForwardingJob: Job? = null

    private val _connection = MutableStateFlow<Connection?>(null)
    val connection: StateFlow<Connection?> = _connection.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /**
     * The auth-aware OkHttpClient for the current connection.
     * Used by PtyWebSocketClient for WebSocket connections that need the same auth.
     * Null when not connected.
     */
    private val _authOkHttpClient = MutableStateFlow<OkHttpClient?>(null)
    val authOkHttpClient: StateFlow<OkHttpClient?> = _authOkHttpClient.asStateFlow()

    val isConnected: Boolean
        get() = _connection.value != null && _connectionState.value is ConnectionState.Connected

    val hasConnection: Boolean
        get() = _connection.value != null

    fun requireApi(): OpenCodeApi {
        return _connection.value?.api
            ?: throw IllegalStateException("Not connected to any server")
    }

    fun getApi(): OpenCodeApi? = _connection.value?.api

    fun getEventSource(): OpenCodeEventSource? = _connection.value?.eventSource

    suspend fun connect(config: ServerConfig, password: String? = null): Result<Unit> {
        AppLog.d(TAG, "Connecting to ${config.url}")
        
        disconnect()
        _connectionState.value = ConnectionState.Connecting

        return try {
            val okHttpClient = buildOkHttpClient(config, password)
            val retrofit = buildRetrofit(config.url, okHttpClient)
            val api = retrofit.create(OpenCodeApi::class.java)

            val healthResult = runCatching { api.health() }
            
            if (healthResult.isFailure) {
                val error = healthResult.exceptionOrNull()
                AppLog.e(TAG, "Health check failed", error)
                _connectionState.value = ConnectionState.Error(error?.message ?: "Connection failed")
                return Result.failure(error ?: Exception("Connection failed"))
            }

            AppLog.d(TAG, "Health check passed, starting SSE")

            val sseClient = buildSseOkHttpClient(config, password)
            val eventSource = OpenCodeEventSource(
                okHttpClient = sseClient,
                json = json,
                baseUrl = config.url,
                eventMapper = eventMapper,
                directoryProvider = { directoryManager.getDirectory() }
            )

            // Build and store the auth-aware WebSocket client
            _authOkHttpClient.value = buildWebSocketOkHttpClient(config, password)

            val connection = Connection(config, api, eventSource)
            _connection.value = connection

            // Forward SSE connection state instead of setting Connected optimistically.
            // The state will move from Connecting → Connected when SSE onOpen fires.
            sseForwardingJob?.cancel()
            sseForwardingJob = scope.launch {
                eventSource.connectionState.collect { sseState ->
                    // Only forward if this event source is still the active one
                    if (_connection.value?.eventSource === eventSource) {
                        AppLog.d(TAG, "SSE state forwarded: $sseState")
                        _connectionState.value = sseState
                    }
                }
            }

            directoryManager.setOnDirectoryChangedListener {
                eventSource.reconnect()
            }

            eventSource.connect()

            AppLog.d(TAG, "Connected successfully to ${config.url}")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLog.e(TAG, "Connection failed", e)
            _connection.value = null
            _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            _authOkHttpClient.value = null
            Result.failure(e)
        }
    }

    /**
     * Lightweight SSE-only reconnect — reuses existing OkHttpClient (with auth baked in).
     * Sets state to Connecting, calls eventSource.reconnect(), and lets the existing
     * SSE state forwarding job handle the transition back to Connected or Error.
     *
     * Use this for background-resume recovery instead of full connect() which requires password.
     */
    fun reconnectSse(reason: String = "unknown"): Boolean {
        val connection = _connection.value
        if (connection == null) {
            AppLog.w(TAG, "reconnectSse($reason) called with no connection – ignoring")
            return false
        }
        AppLog.d(TAG, "reconnectSse($reason) – lightweight SSE restart")
        _connectionState.value = ConnectionState.Connecting
        connection.eventSource.reconnect()
        return true
    }

    fun disconnect() {
        AppLog.d(TAG, "Disconnecting")
        sseForwardingJob?.cancel()
        sseForwardingJob = null
        _connection.value?.disconnect()
        _connection.value = null
        _authOkHttpClient.value = null
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun buildOkHttpClient(config: ServerConfig, password: String?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
                redactHeader("Authorization")
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
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
                redactHeader("Authorization")
            })

        if (config.username != null && password != null) {
            builder.addInterceptor(createAuthInterceptor(config.username, password))
        }

        return builder.build()
    }

    /**
     * Build an OkHttpClient configured for WebSocket use (long-lived, with auth).
     * This is exposed to PtyWebSocketClient via [authOkHttpClient].
     */
    private fun buildWebSocketOkHttpClient(config: ServerConfig, password: String?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)  // No timeout for WebSocket
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)

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
