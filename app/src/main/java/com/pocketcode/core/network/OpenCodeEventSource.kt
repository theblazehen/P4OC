package com.pocketcode.core.network

import android.util.Log
import com.pocketcode.core.datastore.SettingsDataStore
import com.pocketcode.data.remote.dto.EventDataDto
import com.pocketcode.data.remote.mapper.EventMapper
import com.pocketcode.domain.model.OpenCodeEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenCodeEventSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val settingsDataStore: SettingsDataStore,
    private val eventMapper: EventMapper
) {
    companion object {
        private const val TAG = "OpenCodeEventSource"
        private const val RECONNECT_DELAY_MS = 3000L
    }

    private var eventSource: EventSource? = null

    private val _events = MutableSharedFlow<OpenCodeEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<OpenCodeEvent> = _events.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect() {
        if (_connectionState.value is ConnectionState.Connected) return

        scope.launch {
            val baseUrl = settingsDataStore.serverUrl.first()

            _connectionState.value = ConnectionState.Connecting

            val request = Request.Builder()
                .url("$baseUrl/event")
                .header("Accept", "text/event-stream")
                .build()

            eventSource = EventSources.createFactory(okHttpClient)
                .newEventSource(request, object : EventSourceListener() {

                    override fun onOpen(eventSource: EventSource, response: Response) {
                        Log.d(TAG, "SSE connected")
                        _connectionState.value = ConnectionState.Connected
                        _events.tryEmit(OpenCodeEvent.Connected)
                    }

                    override fun onEvent(
                        eventSource: EventSource,
                        id: String?,
                        type: String?,
                        data: String
                    ) {
                        parseAndEmitEvent(data)
                    }

                    override fun onClosed(eventSource: EventSource) {
                        Log.d(TAG, "SSE closed")
                        _connectionState.value = ConnectionState.Disconnected
                        _events.tryEmit(OpenCodeEvent.Disconnected(null))
                    }

                    override fun onFailure(
                        eventSource: EventSource,
                        t: Throwable?,
                        response: Response?
                    ) {
                        Log.e(TAG, "SSE error", t)
                        _connectionState.value = ConnectionState.Error(t?.message)
                        _events.tryEmit(OpenCodeEvent.Error(t ?: Exception("SSE error")))
                        scheduleReconnect()
                    }
                })
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        eventSource?.cancel()
        eventSource = null
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
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            connect()
        }
    }
}
