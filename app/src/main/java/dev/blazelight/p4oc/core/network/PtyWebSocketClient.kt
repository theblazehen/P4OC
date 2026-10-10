package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.core.log.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * WebSocket client for PTY terminal I/O.
 * Connects to the server-version-specific PTY endpoint for real-time terminal communication.
 *
 * Auth is handled by the OkHttpClient resolved from the exact registry-owned server generation,
 * which has an auth interceptor baked in. This class never sees credentials.
 */

class PtyWebSocketClient constructor(
    private val serverConnectionRegistry: ServerConnectionRegistry,
    private val serverRef: dev.blazelight.p4oc.domain.server.ServerRef,
    private val serverGeneration: dev.blazelight.p4oc.domain.server.ServerGeneration,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : java.io.Closeable {
    companion object {
        private const val TAG = "PtyWebSocketClient"
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private val RECONNECT_DELAYS_MS = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L)
    }

    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(supervisorJob + dispatcher)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _output = MutableSharedFlow<String>(extraBufferCapacity = 1000)
    val output: SharedFlow<String> = _output.asSharedFlow()

    private var currentWebSocket: WebSocket? = null
    private var currentPtyId: String? = null
    private var pendingConnectJob: Job? = null

    // Track the last PTY ID for reconnection after background disconnect
    private var lastPtyId: String? = null
    private var lastDirectory: String? = null
    private var lastWorkspace: String? = null
    private val reconnectAttempts = AtomicInteger(0)

    @Volatile
    private var userDisconnected: Boolean = false

    // Generation counter to detect stale WebSocket callbacks.
    // Incremented on each connect(); callbacks check their captured generation
    // against the current value to avoid corrupting a newer connection.
    @Volatile
    private var generation: Long = 0L

    // Lock to prevent race conditions in connect/disconnect
    private val connectionLock = Any()

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val ptyId: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    fun connect(ptyId: String, directory: String?, workspace: String?) {
        synchronized(connectionLock) {
            if (currentPtyId != null && currentPtyId != ptyId) {
                disconnect()
            }
            when {
                currentWebSocket != null && currentPtyId == ptyId ->
                    AppLog.d(TAG, "Already connected to PTY")
                currentPtyId == ptyId && _connectionState.value is ConnectionState.Connecting ->
                    AppLog.d(TAG, "PTY connection is already in progress")
                else -> {
                    val transport = serverConnectionRegistry.terminalTransport(serverRef, serverGeneration)
                    if (transport == null) {
                        AppLog.e(TAG, "Cannot connect: server connection generation is unavailable")
                        _connectionState.value = ConnectionState.Error("Server connection is no longer available")
                    } else {
                        _connectionState.value = ConnectionState.Connecting
                        currentPtyId = ptyId
                        lastPtyId = ptyId
                        lastDirectory = directory
                        lastWorkspace = workspace
                        userDisconnected = false
                        val gen = ++generation

                        if (transport.connection.api is V2WorkspaceOpenCodeApi) {
                            val job = scope.launch(start = CoroutineStart.LAZY) {
                                prepareV2Connection(ptyId, directory, workspace, gen, transport)
                            }
                            pendingConnectJob = job
                            job.start()
                        } else {
                            val location = PtyLocation(ptyId, directory, workspace)
                            openWebSocket(
                                PtyWebSocketConnection(
                                    location = location,
                                    generation = gen,
                                    transport = transport,
                                    wsUrl = buildPtyWebSocketUrl(
                                        transport.connection.config.url,
                                        ptyId,
                                        directory,
                                        workspace,
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun prepareV2Connection(
        ptyId: String,
        directory: String?,
        workspace: String?,
        gen: Long,
        transport: TerminalTransport,
    ) {
        try {
            val ticket = requestV2PtyConnectTicket(
                transport.authClient,
                transport.connection.config.url,
                ptyId,
                directory,
                workspace,
            )
            currentCoroutineContext().ensureActive()

            synchronized(connectionLock) {
                if (generation != gen || userDisconnected || currentPtyId != ptyId) return

                val activeTransport = serverConnectionRegistry.terminalTransport(serverRef, serverGeneration)
                if (
                    activeTransport == null ||
                    activeTransport.connection !== transport.connection ||
                    activeTransport.authClient !== transport.authClient
                ) {
                    currentPtyId = null
                    pendingConnectJob = null
                    _connectionState.value = ConnectionState.Error("Server connection is no longer available")
                    return
                }

                pendingConnectJob = null
                val wsUrl = buildV2PtyWebSocketUrl(
                    transport.connection.config.url,
                    ptyId,
                    directory,
                    workspace,
                    ticket,
                )
                openWebSocket(
                    PtyWebSocketConnection(
                        location = PtyLocation(ptyId, directory, workspace),
                        generation = gen,
                        transport = transport,
                        wsUrl = wsUrl,
                    ),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            handleV2ConnectionFailure(ptyId, directory, workspace, gen, error)
        } catch (error: IllegalArgumentException) {
            handleV2ConnectionFailure(ptyId, directory, workspace, gen, error)
        }
    }

    private fun handleV2ConnectionFailure(
        ptyId: String,
        directory: String?,
        workspace: String?,
        gen: Long,
        error: Exception,
    ) {
        val shouldReconnect = synchronized(connectionLock) {
            if (generation != gen || userDisconnected) {
                false
            } else {
                currentWebSocket = null
                currentPtyId = null
                pendingConnectJob = null
                _connectionState.value = ConnectionState.Error(
                    error.message?.takeIf(String::isNotBlank) ?: "Terminal connection failed",
                )
                true
            }
        }
        if (shouldReconnect) scheduleReconnect(ptyId, directory, workspace, gen)
    }

    private fun requestV2PtyConnectTicket(
        authClient: OkHttpClient,
        baseUrl: String,
        ptyId: String,
        directory: String?,
        workspace: String?,
    ): String {
        val request = buildV2PtyConnectTokenRequest(baseUrl, ptyId, directory, workspace)
        return authClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("PTY connect-token request failed (HTTP ${response.code})")
            }
            val body = response.body?.string()
                ?: throw IOException("PTY connect-token response was empty")
            parseV2PtyConnectTicket(body)
        }
    }

    private fun openWebSocket(connection: PtyWebSocketConnection) {
        val ptyId = connection.location.ptyId
        val directory = connection.location.directory
        val workspace = connection.location.workspace
        val gen = connection.generation
        AppLog.d(TAG, "Connecting PTY WebSocket (gen=$gen)")
        val request = Request.Builder().url(connection.wsUrl).build()
        currentWebSocket = connection.transport.authClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    synchronized(connectionLock) {
                        if (generation != gen) {
                            AppLog.d(TAG, "Stale onOpen (gen=$gen, current=$generation), ignoring")
                            webSocket.close(1000, "Stale connection")
                            return
                        }
                        AppLog.d(TAG, "PTY WebSocket connected")
                        reconnectAttempts.set(0)
                        _connectionState.value = ConnectionState.Connected(ptyId)
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (generation != gen) return
                    AppLog.v(TAG, "Received terminal output (${text.length} chars)")
                    // This is a bounded handoff to the terminal collector, not upstream
                    // backpressure to OkHttp. If rendering falls behind far enough to
                    // fill the buffer, drop the frame rather than spawning unbounded work.
                    if (!_output.tryEmit(text)) {
                        AppLog.w(TAG, "Dropped PTY output frame because terminal output buffer is full")
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    AppLog.d(TAG, "PTY WebSocket closing (code=$code)")
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    AppLog.d(TAG, "PTY WebSocket closed (code=$code, gen=$gen)")
                    val ptyIdForReconnect: String?
                    synchronized(connectionLock) {
                        if (generation != gen) {
                            AppLog.d(TAG, "Stale onClosed (gen=$gen, current=$generation), ignoring")
                            return
                        }
                        currentWebSocket = null
                        ptyIdForReconnect = currentPtyId
                        currentPtyId = null
                        _connectionState.value = ConnectionState.Disconnected
                    }
                    if (!userDisconnected && ptyIdForReconnect != null) {
                        scheduleReconnect(ptyIdForReconnect, directory, workspace, gen)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    AppLog.e(TAG, "PTY WebSocket failure (${t::class.simpleName}, gen=$gen)")
                    val ptyIdForReconnect: String?
                    synchronized(connectionLock) {
                        if (generation != gen) {
                            AppLog.d(TAG, "Stale onFailure (gen=$gen, current=$generation), ignoring")
                            return
                        }
                        currentWebSocket = null
                        ptyIdForReconnect = currentPtyId
                        currentPtyId = null
                        _connectionState.value = ConnectionState.Error("Terminal connection failed")
                    }
                    if (!userDisconnected && ptyIdForReconnect != null) {
                        scheduleReconnect(ptyIdForReconnect, directory, workspace, gen)
                    }
                }
            },
        )
    }

    fun send(data: String): Boolean {
        val ws = currentWebSocket
        if (ws == null) {
            AppLog.w(TAG, "Cannot send: WebSocket not connected")
            return false
        }
        AppLog.v(TAG, "Sending terminal input (${data.length} chars)")
        return ws.send(data)
    }

    private fun scheduleReconnect(ptyId: String, directory: String?, workspace: String?, gen: Long) {
        val attempts = reconnectAttempts.get()
        if (attempts >= MAX_RECONNECT_ATTEMPTS) {
            AppLog.w(TAG, "Max PTY reconnect attempts reached; giving up")
            reconnectAttempts.set(0)
            return
        }
        val delayMs = RECONNECT_DELAYS_MS[attempts.coerceAtMost(RECONNECT_DELAYS_MS.lastIndex)]
        reconnectAttempts.incrementAndGet()
        AppLog.d(TAG, "Scheduling PTY reconnect attempt ${attempts + 1} in ${delayMs}ms")
        scope.launch {
            delay(delayMs)
            synchronized(connectionLock) {
                // A different terminal (or a new connection to this one) supersedes this retry.
                // V2 ticket acquisition leaves currentWebSocket null while it is in flight.
                if (userDisconnected || generation != gen) return@synchronized
                if (currentPtyId == null && currentWebSocket == null) {
                    AppLog.d(TAG, "Attempting PTY reconnect (attempt ${reconnectAttempts.get()})")
                    connect(ptyId, directory, workspace)
                }
            }
        }
    }

    /**
     * Reconnect to the last known PTY session.
     * Called on foreground resume to recover terminal sessions lost during background.
     */
    fun reconnect() {
        val ptyId = lastPtyId
        if (ptyId == null) {
            AppLog.d(TAG, "reconnect() called but no lastPtyId")
            return
        }
        if (isConnected() && currentPtyId == ptyId) {
            AppLog.d(TAG, "reconnect() called but PTY is already connected")
            return
        }
        AppLog.d(TAG, "reconnect() to last PTY")
        userDisconnected = false
        reconnectAttempts.set(0)
        connect(ptyId, lastDirectory, lastWorkspace)
    }

    fun disconnect() {
        synchronized(connectionLock) {
            AppLog.d(TAG, "Disconnecting from $currentPtyId")
            userDisconnected = true
            reconnectAttempts.set(0)
            generation++ // Invalidate any pending callbacks
            pendingConnectJob?.cancel()
            pendingConnectJob = null
            currentWebSocket?.close(1000, "User disconnected")
            currentWebSocket = null
            currentPtyId = null
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    fun isConnected(): Boolean = currentWebSocket != null &&
        _connectionState.value is ConnectionState.Connected

    fun getCurrentPtyId(): String? = currentPtyId

    /**
     * Cleanup resources. Called when the singleton is being destroyed.
     */
    override fun close() {
        disconnect()
        supervisorJob.cancel()
    }
}

private data class PtyLocation(
    val ptyId: String,
    val directory: String?,
    val workspace: String?,
)

private data class PtyWebSocketConnection(
    val location: PtyLocation,
    val generation: Long,
    val transport: TerminalTransport,
    val wsUrl: String,
)

private data class V2PtyUrlLocation(
    val baseUrl: String,
    val ptyId: String,
    val directory: String?,
    val workspace: String?,
)

internal fun buildPtyWebSocketUrl(
    baseUrl: String,
    ptyId: String,
    directory: String?,
    workspace: String?,
): String {
    val httpUrl = baseUrl.trimEnd('/').toHttpUrl()
    val webSocketScheme = when (httpUrl.scheme) {
        "http" -> "ws"
        "https" -> "wss"
        else -> error("Unsupported server URL scheme: ${httpUrl.scheme}")
    }
    val encodedHttpUrl = httpUrl.newBuilder()
        .addPathSegment("pty")
        .addPathSegment(ptyId)
        .addPathSegment("connect")
        .apply {
            directory?.let { addQueryParameter("directory", it) }
            workspace?.let { addQueryParameter("workspace", it) }
        }
        .build()
    // HttpUrl deliberately models only HTTP(S); convert the already safely encoded URL afterward.
    return encodedHttpUrl.toString().replaceFirst("${httpUrl.scheme}://", "$webSocketScheme://")
}

internal fun buildV2PtyConnectTokenRequest(
    baseUrl: String,
    ptyId: String,
    directory: String?,
    workspace: String?,
): Request = Request.Builder()
    .url(
        buildV2PtyHttpUrl(
            V2PtyUrlLocation(baseUrl, ptyId, directory, workspace),
            endpoint = "connect-token",
        ),
    )
    .header("x-opencode-ticket", "1")
    .post(ByteArray(0).toRequestBody(null))
    .build()

internal fun buildV2PtyWebSocketUrl(
    baseUrl: String,
    ptyId: String,
    directory: String?,
    workspace: String?,
    ticket: String,
): String {
    val location = V2PtyUrlLocation(baseUrl, ptyId, directory, workspace)
    val httpUrl = buildV2PtyHttpUrl(location, endpoint = "connect", ticket = ticket)
    val webSocketScheme = when (httpUrl.scheme) {
        "http" -> "ws"
        "https" -> "wss"
        else -> error("Unsupported server URL scheme: ${httpUrl.scheme}")
    }
    return httpUrl.toString().replaceFirst("${httpUrl.scheme}://", "$webSocketScheme://")
}

private fun buildV2PtyHttpUrl(
    location: V2PtyUrlLocation,
    endpoint: String,
    ticket: String? = null,
) = location.baseUrl.trimEnd('/').toHttpUrl().newBuilder()
    .addPathSegment("api")
    .addPathSegment("pty")
    .addPathSegment(location.ptyId)
    .addPathSegment(endpoint)
    .apply {
        location.directory?.let { addQueryParameter("location[directory]", it) }
        location.workspace?.let { addQueryParameter("location[workspace]", it) }
        ticket?.let { addQueryParameter("ticket", it) }
    }
    .build()

internal fun parseV2PtyConnectTicket(responseBody: String): String {
    val ticket = try {
        Json.parseToJsonElement(responseBody)
            .jsonObject["data"]
            ?.jsonObject["ticket"]
            ?.jsonPrimitive
            ?.takeIf { it.isString }
            ?.content
    } catch (_: Exception) {
        null
    } ?: throw IOException("OpenCode returned an invalid PTY connect ticket")

    return ticket.takeIf(String::isNotBlank)
        ?: throw IOException("OpenCode returned an empty PTY connect ticket")
}
