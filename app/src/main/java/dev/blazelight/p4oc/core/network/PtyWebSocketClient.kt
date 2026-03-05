package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.core.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for PTY terminal I/O.
 * Connects to /pty/{id}/connect endpoint for real-time terminal communication.
 *
 * Auth is handled by the OkHttpClient provided by ConnectionManager,
 * which has an auth interceptor baked in. This class never sees credentials.
 */

class PtyWebSocketClient constructor(
    private val connectionManager: ConnectionManager
) : java.io.Closeable {
    companion object {
        private const val TAG = "PtyWebSocketClient"
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private val RECONNECT_DELAYS_MS = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L)
    }

    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(supervisorJob + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _output = MutableSharedFlow<String>(extraBufferCapacity = 1000)
    val output: SharedFlow<String> = _output.asSharedFlow()

    private var currentWebSocket: WebSocket? = null
    private var currentPtyId: String? = null
    
    // Track the last PTY ID for reconnection after background disconnect
    private var lastPtyId: String? = null
    private var reconnectAttempts: Int = 0
    private var userDisconnected: Boolean = false
    
    // Lock to prevent race conditions in connect/disconnect
    private val connectionLock = Any()
    
    // Fallback OkHttpClient for unauthenticated connections
    private val fallbackOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val ptyId: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    fun connect(ptyId: String) {
        synchronized(connectionLock) {
            // Disconnect from previous session if any
            if (currentPtyId != null && currentPtyId != ptyId) {
                disconnect()
            }

            if (currentWebSocket != null && currentPtyId == ptyId) {
                AppLog.d(TAG, "Already connected to $ptyId")
                return
            }

            val connection = connectionManager.connection.value
            if (connection == null) {
                AppLog.e(TAG, "Cannot connect: No active connection")
                _connectionState.value = ConnectionState.Error("Not connected to server")
                return
            }

            _connectionState.value = ConnectionState.Connecting
            currentPtyId = ptyId
            lastPtyId = ptyId
            userDisconnected = false

            val baseUrl = connection.config.url
            // Convert http(s):// to ws(s)://
            val wsUrl = baseUrl
                .replace("http://", "ws://")
                .replace("https://", "wss://")
                .trimEnd('/') + "/pty/$ptyId/connect"

            AppLog.d(TAG, "Connecting to WebSocket: $wsUrl")

            val request = Request.Builder().url(wsUrl).build()

            // Use the auth-aware OkHttpClient from ConnectionManager.
            // The auth interceptor automatically adds Authorization headers.
            val wsClient = connectionManager.authOkHttpClient.value ?: fallbackOkHttpClient

            currentWebSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    AppLog.d(TAG, "WebSocket connected to $ptyId")
                    reconnectAttempts = 0
                    _connectionState.value = ConnectionState.Connected(ptyId)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    AppLog.v(TAG, "Received: ${text.take(100)}${if (text.length > 100) "..." else ""}")
                    scope.launch {
                        _output.emit(text)
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    AppLog.d(TAG, "WebSocket closing: $code $reason")
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    AppLog.d(TAG, "WebSocket closed: $code $reason")
                    val ptyIdForReconnect: String?
                    synchronized(connectionLock) {
                        currentWebSocket = null
                        ptyIdForReconnect = currentPtyId
                        currentPtyId = null
                        _connectionState.value = ConnectionState.Disconnected
                    }
                    // Attempt reconnection if not user-initiated
                    if (!userDisconnected && ptyIdForReconnect != null) {
                        scheduleReconnect(ptyIdForReconnect)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    AppLog.e(TAG, "WebSocket error: ${t.message}", t)
                    val ptyIdForReconnect: String?
                    synchronized(connectionLock) {
                        currentWebSocket = null
                        ptyIdForReconnect = currentPtyId
                        currentPtyId = null
                        _connectionState.value = ConnectionState.Error(t.message ?: "Unknown error")
                    }
                    // Attempt reconnection if not user-initiated
                    if (!userDisconnected && ptyIdForReconnect != null) {
                        scheduleReconnect(ptyIdForReconnect)
                    }
                }
            })
        }
    }

    fun send(data: String): Boolean {
        val ws = currentWebSocket
        if (ws == null) {
            AppLog.w(TAG, "Cannot send: WebSocket not connected")
            return false
        }
        AppLog.v(TAG, "Sending: ${data.take(50)}${if (data.length > 50) "..." else ""}")
        return ws.send(data)
    }

    private fun scheduleReconnect(ptyId: String) {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            AppLog.w(TAG, "Max reconnect attempts reached for $ptyId, giving up")
            reconnectAttempts = 0
            return
        }
        val delayMs = RECONNECT_DELAYS_MS[reconnectAttempts.coerceAtMost(RECONNECT_DELAYS_MS.lastIndex)]
        reconnectAttempts++
        AppLog.d(TAG, "Scheduling reconnect attempt $reconnectAttempts for $ptyId in ${delayMs}ms")
        scope.launch {
            delay(delayMs)
            if (!userDisconnected && currentWebSocket == null) {
                AppLog.d(TAG, "Attempting reconnect to $ptyId (attempt $reconnectAttempts)")
                connect(ptyId)
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
            AppLog.d(TAG, "reconnect() called but already connected to $ptyId")
            return
        }
        AppLog.d(TAG, "reconnect() to last PTY: $ptyId")
        userDisconnected = false
        reconnectAttempts = 0
        connect(ptyId)
    }

    fun disconnect() {
        synchronized(connectionLock) {
            AppLog.d(TAG, "Disconnecting from $currentPtyId")
            userDisconnected = true
            reconnectAttempts = 0
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
