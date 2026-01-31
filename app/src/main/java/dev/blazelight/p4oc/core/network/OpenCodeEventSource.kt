package dev.blazelight.p4oc.core.network

import android.util.Log
import dev.blazelight.p4oc.data.remote.dto.EventDataDto
import dev.blazelight.p4oc.data.remote.dto.GlobalEventDto
import dev.blazelight.p4oc.data.remote.mapper.EventMapper
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource
import java.io.IOException

class OpenCodeEventSource(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val baseUrl: String,
    private val eventMapper: EventMapper,
    private val directoryProvider: () -> String?
) {
    companion object {
        private const val TAG = "OpenCodeEventSource"
        private const val RECONNECT_DELAY_MS = 3000L
    }

    private val _events = MutableSharedFlow<OpenCodeEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<OpenCodeEvent> = _events.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var sseJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Flag to prevent auto-reconnect after intentional disconnect
    @Volatile
    private var shouldReconnect = true

    fun connect() {
        Log.d(TAG, "connect() called, state=${_connectionState.value}, jobActive=${sseJob?.isActive}")
        if (_connectionState.value is ConnectionState.Connected) return
        if (sseJob?.isActive == true) return
        
        // Re-enable reconnection when explicitly connecting
        shouldReconnect = true

        sseJob = scope.launch {
            _connectionState.value = ConnectionState.Connecting

            // Use /global/event endpoint which receives ALL events via GlobalBus
            // Events are filtered client-side by sessionId in ChatViewModel
            val eventUrl = "$baseUrl/global/event"
            Log.d(TAG, "Connecting to SSE: $eventUrl")
            
            val request = Request.Builder()
                .url(eventUrl)
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .build()

            try {
                val response = okHttpClient.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    response.close()  // Close response body on error
                    throw IOException("Unexpected response: ${response.code}")
                }

                Log.d(TAG, "SSE connected")
                _connectionState.value = ConnectionState.Connected
                _events.tryEmit(OpenCodeEvent.Connected)

                try {
                    response.body?.source()?.let { source ->
                        readSseStream(source)
                    }
                } finally {
                    response.close()  // Always close response when done
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "SSE error", e)
                _connectionState.value = ConnectionState.Error(e.message)
                _events.tryEmit(OpenCodeEvent.Error(e))
                scheduleReconnect()
            }
        }
    }

    private suspend fun readSseStream(source: BufferedSource) {
        var eventData = StringBuilder()
        
        try {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                
                when {
                    line.startsWith("data:") -> {
                        val data = line.removePrefix("data:").trim()
                        eventData.append(data)
                        Log.v(TAG, "SSE data line received, length=${data.length}")
                    }
                    line.isEmpty() && eventData.isNotEmpty() -> {
                        Log.d(TAG, "SSE event complete, parsing ${eventData.length} chars")
                        parseAndEmitEvent(eventData.toString())
                        eventData = StringBuilder()
                    }
                    line.startsWith(":") -> {
                        // Comment/keepalive
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error reading SSE stream", e)
        } finally {
            Log.d(TAG, "SSE stream ended")
            _connectionState.value = ConnectionState.Disconnected
            _events.tryEmit(OpenCodeEvent.Disconnected(null))
            scheduleReconnect()
        }
    }

    fun disconnect() {
        Log.d(TAG, "disconnect() called")
        // Disable auto-reconnect when intentionally disconnecting
        shouldReconnect = false
        sseJob?.cancel()
        sseJob = null
        _connectionState.value = ConnectionState.Disconnected
    }

    fun reconnect() {
        Log.d(TAG, "reconnect() called - reconnecting with current directory")
        disconnect()
        connect()
    }

    fun shutdown() {
        disconnect()
        scope.cancel()
    }

    private fun parseAndEmitEvent(data: String) {
        try {
            val globalEvent = json.decodeFromString<GlobalEventDto>(data)
            val event = eventMapper.mapToEvent(globalEvent.payload)
            if (event != null) {
                val emitted = _events.tryEmit(event)
                Log.d(TAG, "Event emitted: ${event::class.simpleName}, success=$emitted")
            }
        } catch (e: Exception) {
            try {
                val eventData = json.decodeFromString<EventDataDto>(data)
                val event = eventMapper.mapToEvent(eventData)
                if (event != null) {
                    val emitted = _events.tryEmit(event)
                    Log.d(TAG, "Event emitted (fallback): ${event::class.simpleName}, success=$emitted")
                }
            } catch (e2: Exception) {
                // Only log if not a known ignorable event type
                if (!data.contains("server.heartbeat") && !data.contains("server.connected")) {
                    Log.e(TAG, "Failed to parse event: $data", e2)
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) {
            Log.d(TAG, "scheduleReconnect() skipped - shouldReconnect=false")
            return
        }
        scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (_connectionState.value !is ConnectionState.Connected && shouldReconnect) {
                connect()
            }
        }
    }
}
