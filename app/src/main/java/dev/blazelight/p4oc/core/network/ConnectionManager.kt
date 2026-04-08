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
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import android.content.Context
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import java.io.File


class ConnectionManager constructor(
    private val context: Context,
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
            val baseClient = buildBaseOkHttpClient(config, password)
            val okHttpClient = buildOkHttpClient(baseClient)
            val retrofit = buildRetrofit(config.url, okHttpClient)
            val api = retrofit.create(OpenCodeApi::class.java)

            // Parallel health check with timeout for faster connection validation
            val healthResult = runCatching {
                kotlinx.coroutines.withTimeout(8000) { api.health() }
            }

            if (healthResult.isFailure) {
                val error = healthResult.exceptionOrNull()
                AppLog.e(TAG, "Health check failed", error)
                _connectionState.value = ConnectionState.Error(error?.message ?: "Connection failed")
                return Result.failure(error ?: Exception("Connection failed"))
            }

            AppLog.d(TAG, "Health check passed, starting SSE")

            // Pre-warm connection pool for upcoming requests
            scope.launch(Dispatchers.IO) {
                try {
                    api.health() // Second call reuses warmed connection
                } catch (_: Exception) { }
            }

            val sseClient = buildSseOkHttpClient(baseClient)
            val eventSource = OpenCodeEventSource(
                okHttpClient = sseClient,
                json = json,
                baseUrl = config.url,
                eventMapper = eventMapper,
                directoryProvider = { directoryManager.getDirectory() }
            )

            // Build and store the auth-aware WebSocket client (shares pool with base)
            _authOkHttpClient.value = buildWebSocketOkHttpClient(baseClient)

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

    // Shared connection pool for all clients - aggressive settings for low latency
    private val sharedConnectionPool = ConnectionPool(
        maxIdleConnections = 10,        // More idle connections ready
        keepAliveDuration = 5,          // 5 minutes keep-alive
        timeUnit = TimeUnit.MINUTES
    )

    /**
     * Build a shared base OkHttpClient with auth and common settings.
     * Optimized for minimal latency with aggressive connection pooling.
     */
    private fun buildBaseOkHttpClient(config: ServerConfig, password: String?): OkHttpClient {
        val cacheDir = File(context.cacheDir, "http_cache")
        val cache = Cache(cacheDir, 20L * 1024L * 1024L)

        val builder = OkHttpClient.Builder()
            // Reduced timeouts for faster failure detection and retry
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // Aggressive connection pooling
            .connectionPool(sharedConnectionPool)
            // HTTP/2 for multiplexing and header compression
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .cache(cache)
            // Retry on connection failure
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val original = chain.request()
                // Only add Accept header if not already provided
                if (original.header("Accept") == null) {
                    val newReq = original.newBuilder()
                        .header("Accept", "application/json")
                        .build()
                    chain.proceed(newReq)
                } else {
                    chain.proceed(original)
                }
            }

        if (config.username != null && password != null) {
            builder.addInterceptor(createAuthInterceptor(config.username, password))
        }

        return builder.build()
    }

    private fun buildOkHttpClient(base: OkHttpClient): OkHttpClient =
        base.newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            // Minimal logging - only in debug and only headers (no body)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
                redactHeader("Authorization")
            })
            .build()

    private fun buildSseOkHttpClient(base: OkHttpClient): OkHttpClient =
        base.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
                redactHeader("Authorization")
            })
            .build()

    /**
     * Build an OkHttpClient configured for WebSocket use (long-lived, with ping).
     * This is exposed to PtyWebSocketClient via [authOkHttpClient].
     */
    private fun buildWebSocketOkHttpClient(base: OkHttpClient): OkHttpClient =
        base.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)  // No timeout for WebSocket
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

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
