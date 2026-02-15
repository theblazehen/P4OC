package dev.blazelight.p4oc.core.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for PTY terminal I/O.
 * Connects to /pty/{id}/connect endpoint for real-time terminal communication.
 */

class PtyWebSocketClient constructor(
    private val connectionManager: ConnectionManager
) : java.io.Closeable {
    companion object {
        private const val TAG = "PtyWebSocketClient"
    }

    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(supervisorJob + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _output = MutableSharedFlow<String>(extraBufferCapacity = 1000)
    val output: SharedFlow<String> = _output.asSharedFlow()

    private var currentWebSocket: WebSocket? = null
    private var currentPtyId: String? = null
    
    // Lock to prevent race conditions in connect/disconnect
    private val connectionLock = Any()
    
    // Dedicated OkHttpClient for WebSocket (long-lived connections)
    private val wsOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // No timeout for WebSocket
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
                Log.d(TAG, "Already connected to $ptyId")
                return
            }

            val connection = connectionManager.connection.value
            if (connection == null) {
                Log.e(TAG, "Cannot connect: No active connection")
                _connectionState.value = ConnectionState.Error("Not connected to server")
                return
            }

            _connectionState.value = ConnectionState.Connecting
            currentPtyId = ptyId

            val baseUrl = connection.config.url
            // Convert http(s):// to ws(s)://
            val wsUrl = baseUrl
                .replace("http://", "ws://")
                .replace("https://", "wss://")
                .trimEnd('/') + "/pty/$ptyId/connect"

            Log.d(TAG, "Connecting to WebSocket: $wsUrl")

            val requestBuilder = Request.Builder().url(wsUrl)
            val config = connection.config
            if (config.username != null && config.password != null) {
                requestBuilder.header("Authorization", Credentials.basic(config.username, config.password))
            }
            val request = requestBuilder.build()

            currentWebSocket = wsOkHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket connected to $ptyId")
                    _connectionState.value = ConnectionState.Connected(ptyId)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.v(TAG, "Received: ${text.take(100)}${if (text.length > 100) "..." else ""}")
                    scope.launch {
                        _output.emit(text)
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closing: $code $reason")
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: $code $reason")
                    synchronized(connectionLock) {
                        _connectionState.value = ConnectionState.Disconnected
                        currentWebSocket = null
                        currentPtyId = null
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket error: ${t.message}", t)
                    synchronized(connectionLock) {
                        _connectionState.value = ConnectionState.Error(t.message ?: "Unknown error")
                        currentWebSocket = null
                        currentPtyId = null
                    }
                }
            })
        }
    }

    fun send(data: String): Boolean {
        val ws = currentWebSocket
        if (ws == null) {
            Log.w(TAG, "Cannot send: WebSocket not connected")
            return false
        }
        Log.v(TAG, "Sending: ${data.take(50)}${if (data.length > 50) "..." else ""}")
        return ws.send(data)
    }

    fun disconnect() {
        synchronized(connectionLock) {
            Log.d(TAG, "Disconnecting from $currentPtyId")
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
