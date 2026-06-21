package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.BuildConfig
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.core.security.OidcAuthManager
import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import dev.blazelight.p4oc.data.remote.mapper.EventMapper
import dev.blazelight.p4oc.domain.server.ScopedEvent
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.server.WorkspaceKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class ConnectionManager constructor(
    private val json: Json,
    private val eventMapper: EventMapper,
    private val settingsDataStore: SettingsDataStore,
    private val oidcAuthManager: OidcAuthManager,
) {
    companion object {
        private const val TAG = "ConnectionManager"
        private const val MAX_REQUESTS_PER_HOST = 20
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // Off-main scope for blocking teardown (socket close / pool eviction) triggered from the UI thread.
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sseForwardingJob: Job? = null
    private var sseEscalationJob: Job? = null
    private var generationCounter: Long = 0L
    // Set on app background; the next foreground forces an SSE reconnect because the socket is
    // likely dead after a process freeze (lock/unlock) even when the state still reads Connected.
    @Volatile
    private var wasBackgrounded = false
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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val scopedEvents: Flow<ScopedEvent>
        get() = connection.flatMapLatest { conn ->
            if (conn == null) {
                emptyFlow()
            } else {
                val serverRef = ServerRef.fromEndpoint(conn.config.url)
                conn.eventSource.directoryEvents.map { directoryEvent ->
                    ScopedEvent(
                        serverRef = serverRef,
                        generation = conn.generation,
                        workspaceKey = directoryEvent.directory?.takeIf {
                            it.isNotBlank()
                        }?.let(WorkspaceKey::Directory) ?: WorkspaceKey.Global,
                        event = directoryEvent.event,
                    )
                }
            }
        }

    suspend fun connect(config: ServerConfig, password: String? = null): Result<List<ProjectDto>> {
        return withContext(Dispatchers.IO) {
            AppLog.d(TAG, "Connecting to ${config.url}")

            // Tear down any prior connection synchronously on this IO context. Closing sockets is
            // blocking network I/O (NetworkOnMainThreadException if run on the main thread) and must
            // finish before we rebuild on the shared pool — an async evict could race the new socket.
            val previous = releaseConnectionState()
            closeConnectionSockets(previous)
            _connectionState.value = ConnectionState.Connecting

            val configsToTry = connectionCandidates(config)
            var lastError: Throwable? = null
            configsToTry.forEachIndexed { index, candidate ->
                if (index > 0) {
                    AppLog.d(TAG, "Retrying connection with fallback URL ${candidate.url}")
                }

                val result = connectSingle(candidate, password)
                if (result.isSuccess) return@withContext result
                lastError = result.exceptionOrNull()
            }

            Result.failure(lastError ?: Exception("Connection failed"))
        }
    }

    private suspend fun connectSingle(config: ServerConfig, password: String? = null): Result<List<ProjectDto>> {
        return try {
            val baseClient = buildBaseOkHttpClient(config, password)
            val okHttpClient = buildOkHttpClient(baseClient)
            val retrofit = buildRetrofit(config.url, okHttpClient)
            val api = retrofit.create(OpenCodeApi::class.java)

            val probeResult = runCatching {
                withTimeout(8_000) {
                    api.listProjects()
                }
            }

            if (probeResult.isFailure) {
                val error = probeResult.exceptionOrNull()
                AppLog.e(TAG, "Project probe failed", error)
                _connectionState.value = ConnectionState.Error(error?.message ?: "Connection failed")
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
                        handleSseStateForReconnectOwner(connection, sseState)
                    }
                }
            }

            eventSource.connect()

            AppLog.d(TAG, "Connected successfully to ${config.url}")
            Result.success(probeResult.getOrNull().orEmpty())
        } catch (e: Exception) {
            AppLog.e(TAG, "Connection failed", e)
            _connection.value = null
            _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            _authOkHttpClient.value = null
            Result.failure(e)
        }
    }

    private fun connectionCandidates(config: ServerConfig): List<ServerConfig> {
        val parsed = config.url.toHttpUrlOrNull() ?: return listOf(config)
        if (parsed.port != ServerUrl.DEFAULT_PORT) return listOf(config)

        val fallbackPort = when (parsed.scheme) {
            "http" -> 80
            "https" -> 443
            else -> return listOf(config)
        }

        val fallbackUrl = parsed.newBuilder().port(fallbackPort).build().toString().trimEnd('/')
        if (fallbackUrl == config.url.trimEnd('/')) return listOf(config)

        return listOf(config, config.copy(url = fallbackUrl))
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

    fun onAppForegrounded() {
        val connection = _connection.value ?: return
        val backgrounded = wasBackgrounded
        wasBackgrounded = false
        val state = _connectionState.value
        // A connect/reconnect is already in flight — let it finish.
        if (state is ConnectionState.Connecting) return
        // After a real background→foreground gap the SSE socket may be HALF-OPEN: the device froze
        // the process, the socket died without a FIN, no onError/onClosed fired, so the state still
        // reads Connected. Don't trust it — force a fresh SSE reconnect. Skip only when Connected
        // AND there was no background gap (initial composition / in-app navigation re-entry).
        if (state is ConnectionState.Connected && !backgrounded) return

        AppLog.d(TAG, "Foreground resume (state=$state, backgrounded=$backgrounded): forcing SSE reconnect")
        connection.eventSource.resetConsecutiveErrors()
        reconnectSse(reason = "app_foreground")
    }

    /**
     * Mark that the app went to background. The next [onAppForegrounded] then forces an SSE
     * reconnect even if the state still reads Connected, because a frozen process leaves the SSE
     * socket half-open (dead but never erroring under readTimeout=0).
     */
    fun onAppBackgrounded() {
        wasBackgrounded = true
        AppLog.d(TAG, "App backgrounded; next foreground will force an SSE reconnect")
    }

    fun disconnect() {
        AppLog.d(TAG, "Disconnecting")
        val previous = releaseConnectionState()
        _connectionState.value = ConnectionState.Disconnected
        // Socket teardown is blocking network I/O; never run it on the caller's (often main) thread.
        cleanupScope.launch { closeConnectionSockets(previous) }
    }

    /**
     * Drop in-memory connection references and cancel the SSE jobs. Pure state mutation — safe on
     * any thread. Returns the previous [Connection] so its sockets can be closed off the main thread.
     */
    private fun releaseConnectionState(): Connection? {
        sseForwardingJob?.cancel()
        sseForwardingJob = null
        sseEscalationJob?.cancel()
        sseEscalationJob = null
        val previous = _connection.value
        _connection.value = null
        _authOkHttpClient.value = null
        return previous
    }

    /** Close the previous connection's sockets and evict the shared pool. Blocking I/O — keep off the main thread. */
    private fun closeConnectionSockets(previous: Connection?) {
        previous?.disconnect()
        sharedConnectionPool.evictAll()
    }

    private fun handleSseStateForReconnectOwner(connection: Connection, state: ConnectionState) {
        when (state) {
            ConnectionState.Connected, ConnectionState.Connecting -> {
                sseEscalationJob?.cancel()
                sseEscalationJob = null
            }
            is ConnectionState.Error -> scheduleSseEscalation(connection, state)
            ConnectionState.Disconnected -> {
                sseEscalationJob?.cancel()
                sseEscalationJob = null
            }
        }
    }

    private fun scheduleSseEscalation(connection: Connection, state: ConnectionState.Error) {
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
                AppLog.w(TAG, "SSE remained in Error after ${settings.reconnectTimeoutSeconds}s; escalating to Disconnected: ${state.message}")
                connection.eventSource.disconnect()
            }
        }
    }

    /**
     * Build a shared base OkHttpClient with auth and common settings.
     * Derived clients share its connection pool and dispatcher via newBuilder().
     */
    private fun buildBaseOkHttpClient(config: ServerConfig, password: String?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectionPool(sharedConnectionPool)
            .dispatcher(sharedDispatcher)

        when (config.authMode) {
            AuthMode.OIDC -> builder.addInterceptor(createBearerInterceptor(config.url))
            AuthMode.BASIC ->
                if (config.username != null && password != null) {
                    builder.addInterceptor(createAuthInterceptor(config.username, password))
                }
        }

        if (config.allowInsecure) {
            AppLog.w(TAG, "TLS verification DISABLED for ${config.url} (allowInsecure=true)")
            builder.applyInsecureTls()
        }

        return builder.build()
    }

    private fun buildOkHttpClient(base: OkHttpClient): OkHttpClient =
        base.newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
                    redactHeader("Authorization")
                }
            )
            .build()

    private fun buildSseOkHttpClient(base: OkHttpClient): OkHttpClient =
        base.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
                    redactHeader("Authorization")
                }
            )
            .build()

    /**
     * Build an OkHttpClient configured for WebSocket use (long-lived, with ping).
     * This is exposed to PtyWebSocketClient via [authOkHttpClient].
     */
    private fun buildWebSocketOkHttpClient(base: OkHttpClient): OkHttpClient =
        base.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS) // No timeout for WebSocket
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

    /**
     * OIDC interceptor. Fetches a fresh (auto-refreshed) access credential from [OidcAuthManager]
     * per request, so REST, SSE and WebSocket clients (all derived from the base via newBuilder)
     * stay authenticated across expiry — reconnects transparently pick up a refreshed credential.
     */
    private fun createBearerInterceptor(serverUrl: String): Interceptor {
        return Interceptor { chain ->
            val accessToken = oidcAuthManager.freshAccessTokenBlocking(serverUrl)
            val request = if (accessToken != null) {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $accessToken")
                    .build()
            } else {
                chain.request()
            }
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
