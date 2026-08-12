package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.BuildConfig
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import dev.blazelight.p4oc.data.remote.mapper.EventMapper
import dev.blazelight.p4oc.domain.server.ServerGeneration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
    private val settingsDataStore: SettingsDataStore,
) {
    companion object {
        private const val TAG = "ConnectionManager"
        private const val MAX_REQUESTS_PER_HOST = 20
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectionLifecycleLock = Any()
    private var sseForwardingJob: Job? = null
    private var sseEscalationJob: Job? = null
    private var generationCounter: Long = 0L
    private val sharedConnectionPool = ConnectionPool(
        maxIdleConnections = 10,
        keepAliveDuration = 5,
        timeUnit = TimeUnit.MINUTES
    )
    private val sharedDispatcher = Dispatcher().apply {
        // SSE and terminal WebSockets are long-lived; leave headroom for REST calls.
        maxRequestsPerHost = MAX_REQUESTS_PER_HOST
    }

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

    val currentBaseUrl: String?
        get() = _connection.value?.config?.url

    val currentGeneration: ServerGeneration?
        get() = _connection.value?.generation

    fun requireApi(): OpenCodeApi {
        return _connection.value?.api
            ?: throw IllegalStateException("Not connected to any server")
    }

    fun getApi(): OpenCodeApi? = _connection.value?.api

    fun getEventSource(): OpenCodeEventSource? = _connection.value?.eventSource

    @Suppress("ReturnCount")
    suspend fun connect(config: ServerConfig, password: String? = null): Result<List<ProjectDto>> {
        AppLog.d(TAG, "Connecting")

        if (config.username != null && password != null &&
            !ServerUrl.allowsCleartextCredentials(config.url)
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "Credentials cannot be sent over HTTP outside a private local network",
                ),
            )
        }

        disconnect()
        _connectionState.value = ConnectionState.Connecting

        val configsToTry = connectionCandidates(config)
        var primaryError: Throwable? = null
        configsToTry.forEachIndexed { index, candidate ->
            if (index > 0) {
                AppLog.d(TAG, "Retrying connection with fallback endpoint")
            }

            val result = connectSingle(candidate, password)
            if (result.isSuccess) return result
            if (primaryError == null) primaryError = result.exceptionOrNull()
        }

        return Result.failure(primaryError ?: Exception("Connection failed"))
    }

    private suspend fun connectSingle(config: ServerConfig, password: String? = null): Result<List<ProjectDto>> {
        return try {
            val baseClient = buildBaseOkHttpClient(config, password)
            val okHttpClient = buildOkHttpClient(baseClient)
            val retrofit = buildRetrofit(config.url, okHttpClient)
            val api = retrofit.create(OpenCodeApi::class.java)

            val probeResult = runCatching {
                withTimeout(8_000) {
                    api.listProjects(directory = null, workspace = null)
                }
            }

            if (probeResult.isFailure) {
                currentCoroutineContext().ensureActive()
                val error = probeResult.exceptionOrNull()
                AppLog.e(TAG, "Project probe failed")
                _connectionState.value = ConnectionState.Error("Connection failed")
                return Result.failure(error ?: Exception("Connection failed"))
            }

            AppLog.d(TAG, "Project probe passed, starting SSE")

            val sseClient = buildSseOkHttpClient(baseClient)
            val eventSource = OpenCodeEventSource(
                okHttpClient = sseClient,
                json = json,
                baseUrl = config.url,
                eventMapper = eventMapper,
            )

            // Build and store the auth-aware WebSocket client (shares pool with base)
            _authOkHttpClient.value = buildWebSocketOkHttpClient(baseClient)

            val generation = ServerGeneration(++generationCounter)
            val connection = Connection(config, generation, api, eventSource)
            synchronized(connectionLifecycleLock) {
                _connection.value = connection
            }

            // Forward SSE connection state instead of setting Connected optimistically.
            // The state will move from Connecting → Connected when SSE onOpen fires.
            sseForwardingJob?.cancel()
            sseForwardingJob = scope.launch {
                eventSource.connectionState.collect { sseState ->
                    // Only forward if this event source is still the active one
                    if (_connection.value?.eventSource === eventSource) {
                        AppLog.d(TAG, "SSE connection state updated")
                        _connectionState.value = sseState
                        handleSseStateForReconnectOwner(connection, sseState)
                    }
                }
            }

            eventSource.connect()

            AppLog.d(TAG, "Connected successfully")
            Result.success(probeResult.getOrNull().orEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(TAG, "Connection failed")
            _connection.value = null
            _connectionState.value = ConnectionState.Error("Connection failed")
            _authOkHttpClient.value = null
            Result.failure(e)
        }
    }

    internal fun connectionCandidates(config: ServerConfig): List<ServerConfig> {
        val primaryUrl = ServerUrl.normalizeConnectUrl(config.url) ?: config.url
        val primaryConfig = if (primaryUrl.trimEnd('/') == config.url.trimEnd('/')) {
            config
        } else {
            config.copy(url = primaryUrl)
        }
        val parsed = primaryConfig.url.toHttpUrlOrNull() ?: return listOf(primaryConfig)
        if (hasExplicitPort(config.url)) return listOf(primaryConfig)

        val opencodeUrl = parsed.newBuilder().port(ServerUrl.DEFAULT_PORT).build().toString().trimEnd('/')
        val opencodeConfig = primaryConfig.copy(url = opencodeUrl)
        if (opencodeUrl == primaryConfig.url.trimEnd('/')) return listOf(primaryConfig)

        return listOf(opencodeConfig, primaryConfig)
    }

    internal fun hasExplicitPort(url: String): Boolean {
        val authority = url
            .substringAfter("://", missingDelimiterValue = url)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')

        if (authority.startsWith("[")) {
            return authority.substringAfter("]", missingDelimiterValue = "").startsWith(":")
        }

        return authority.contains(':')
    }

    /**
     * Lightweight SSE-only reconnect — reuses existing OkHttpClient (with auth baked in).
     * Sets state to Connecting, calls eventSource.reconnect(), and lets the existing
     * SSE state forwarding job handle the transition back to Connected or Error.
     *
     * Use this for background-resume recovery instead of full connect() which requires password.
     */
    fun reconnectSse(@Suppress("UNUSED_PARAMETER") reason: String = "unknown"): Boolean {
        val connection = _connection.value
        if (connection == null) {
            AppLog.w(TAG, "SSE reconnect requested with no connection; ignoring")
            return false
        }
        AppLog.d(TAG, "Restarting SSE connection")
        _connectionState.value = ConnectionState.Connecting
        connection.eventSource.reconnect()
        return true
    }

    fun onAppForegrounded() {
        val connection = _connection.value ?: return
        AppLog.d(TAG, "Restarting SSE connection after foreground resume")
        connection.eventSource.resetConsecutiveErrors()
        reconnectSse(reason = "app_foreground")
    }

    fun disconnect() = synchronized(connectionLifecycleLock) {
        disconnectLocked()
    }

    /** Disconnects only if [generation] still owns this manager's live connection. */
    fun disconnect(generation: ServerGeneration): Boolean = synchronized(connectionLifecycleLock) {
        if (_connection.value?.generation != generation) return@synchronized false
        disconnectLocked()
        true
    }

    private fun disconnectLocked() {
        AppLog.d(TAG, "Disconnecting")
        sseForwardingJob?.cancel()
        sseForwardingJob = null
        sseEscalationJob?.cancel()
        sseEscalationJob = null
        _connection.value?.disconnect()
        sharedConnectionPool.evictAll()
        _connection.value = null
        _authOkHttpClient.value = null
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun handleSseStateForReconnectOwner(connection: Connection, state: ConnectionState) {
        when (state) {
            ConnectionState.Connected, ConnectionState.Connecting -> {
                sseEscalationJob?.cancel()
                sseEscalationJob = null
            }
            is ConnectionState.Error -> scheduleSseEscalation(connection)
            ConnectionState.Disconnected -> {
                sseEscalationJob?.cancel()
                sseEscalationJob = null
            }
        }
    }

    private fun scheduleSseEscalation(connection: Connection) {
        sseEscalationJob?.cancel()
        sseEscalationJob = scope.launch {
            val settings = settingsDataStore.connectionSettings.first()
            if (!settings.autoReconnect) {
                AppLog.w(TAG, "SSE error with autoReconnect=false; escalating to Disconnected")
                if (_connection.value === connection && _connectionState.value is ConnectionState.Error) {
                    connection.eventSource.disconnect()
                }
                return@launch
            }

            delay(settings.reconnectTimeoutSeconds * 1000L)
            if (_connection.value === connection && _connectionState.value is ConnectionState.Error) {
                AppLog.w(
                    TAG,
                    "SSE remained in Error after ${settings.reconnectTimeoutSeconds}s; escalating to Disconnected",
                )
                connection.eventSource.disconnect()
            }
        }
    }

    /**
     * Build a shared base OkHttpClient with auth and common settings.
     * Derived clients share its connection pool and dispatcher via newBuilder().
     */
    internal fun buildBaseOkHttpClient(config: ServerConfig, password: String?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectionPool(sharedConnectionPool)
            .dispatcher(sharedDispatcher)

        if (config.username != null && password != null) {
            val configuredOrigin = requireNotNull(ServerUrl.normalizeConnectUrl(config.url)?.toHttpUrlOrNull())
            builder.addInterceptor(createAuthInterceptor(config.username, password, configuredOrigin))
        }

        if (config.allowInsecure) {
            AppLog.w(TAG, "TLS verification DISABLED (allowInsecure=true)")
            builder.applyInsecureTls()
        }

        return builder.build()
    }

    private fun buildOkHttpClient(base: OkHttpClient): OkHttpClient =
        base.newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(createDiagnosticLoggingInterceptor())
            .build()

    private fun buildSseOkHttpClient(base: OkHttpClient): OkHttpClient =
        base.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .addInterceptor(createDiagnosticLoggingInterceptor())
            .build()

    /**
     * Debug diagnostics intentionally stop at headers. Provider auth and OAuth endpoints carry
     * credentials in JSON bodies, and future endpoints may do the same; a headers-only policy is
     * therefore safer than trying to recognize secrets or maintain a sensitive-path allowlist.
     */
    internal fun createDiagnosticLoggingInterceptor(
        debugLoggingEnabled: Boolean = BuildConfig.DEBUG,
        logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT,
    ): HttpLoggingInterceptor = HttpLoggingInterceptor(logger).apply {
        level = if (debugLoggingEnabled) {
            HttpLoggingInterceptor.Level.HEADERS
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        redactHeader("Authorization")
    }

    /**
     * Build an OkHttpClient configured for WebSocket use (long-lived, with ping).
     * This is exposed to PtyWebSocketClient via [authOkHttpClient].
     */
    private fun buildWebSocketOkHttpClient(base: OkHttpClient): OkHttpClient =
        base.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS) // No timeout for WebSocket
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

    internal fun createAuthInterceptor(username: String, password: String, configuredOrigin: HttpUrl): Interceptor {
        return Interceptor { chain ->
            val requestUrl = chain.request().url
            if (!requestUrl.hasSameOrigin(configuredOrigin)) {
                return@Interceptor chain.proceed(chain.request().newBuilder().removeHeader("Authorization").build())
            }
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

internal fun HttpUrl.hasSameOrigin(other: HttpUrl): Boolean =
    scheme.httpEquivalent() == other.scheme.httpEquivalent() && host == other.host && port == other.port

private fun String.httpEquivalent(): String = when (this) {
    "ws" -> "http"
    "wss" -> "https"
    else -> this
}
