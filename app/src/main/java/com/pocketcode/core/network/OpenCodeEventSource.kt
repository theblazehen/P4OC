package com.pocketcode.core.network

import android.util.Log
import com.pocketcode.data.remote.dto.EventDataDto
import com.pocketcode.data.remote.mapper.EventMapper
import com.pocketcode.domain.model.OpenCodeEvent
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
    private val eventMapper: EventMapper
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

    fun connect() {
        Log.d(TAG, "connect() called, state=${_connectionState.value}, jobActive=${sseJob?.isActive}")
        if (_connectionState.value is ConnectionState.Connected) return
        if (sseJob?.isActive == true) return

        sseJob = scope.launch {
            _connectionState.value = ConnectionState.Connecting

            val request = Request.Builder()
                .url("$baseUrl/event")
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .build()

            try {
                val response = okHttpClient.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    throw IOException("Unexpected response: ${response.code}")
                }

                Log.d(TAG, "SSE connected")
                _connectionState.value = ConnectionState.Connected
                _events.tryEmit(OpenCodeEvent.Connected)

                response.body?.source()?.let { source ->
                    readSseStream(source)
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
                    }
                    line.isEmpty() && eventData.isNotEmpty() -> {
                        parseAndEmitEvent(eventData.toString())
                        eventData = StringBuilder()
                    }
                    line.startsWith(":") -> {
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
        sseJob?.cancel()
        sseJob = null
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun parseAndEmitEvent(data: String) {
        try {
            val eventData = json.decodeFromString<EventDataDto>(data)
            val event = eventMapper.mapToEvent(eventData)
            event?.let { _events.tryEmit(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse event: $data", e)
        }
    }

    private fun scheduleReconnect() {
        scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (_connectionState.value !is ConnectionState.Connected) {
                connect()
            }
        }
    }
}
