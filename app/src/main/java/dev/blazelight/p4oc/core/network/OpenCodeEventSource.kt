package dev.blazelight.p4oc.core.network

import com.launchdarkly.eventsource.ConnectStrategy
import com.launchdarkly.eventsource.ErrorStrategy
import com.launchdarkly.eventsource.EventSource
import com.launchdarkly.eventsource.MessageEvent
import com.launchdarkly.eventsource.background.BackgroundEventHandler
import com.launchdarkly.eventsource.background.BackgroundEventSource
import com.launchdarkly.eventsource.background.ConnectionErrorHandler
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.data.remote.dto.EventDataDto
import dev.blazelight.p4oc.data.remote.dto.GlobalEventDto
import dev.blazelight.p4oc.data.remote.mapper.EventMapper
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OpenCodeEventSource(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val baseUrl: String,
    private val eventMapper: EventMapper,
) {
    data class DirectoryEvent(
        val directory: String?,
        val event: OpenCodeEvent,
    )

    companion object {
        private const val TAG = "OpenCodeEventSource"
        private const val MAX_CONSECUTIVE_ERRORS = 15
    }

    private val _events = MutableSharedFlow<OpenCodeEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<OpenCodeEvent> = _events.asSharedFlow()

    private val _directoryEvents = MutableSharedFlow<DirectoryEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val directoryEvents: SharedFlow<DirectoryEvent> = _directoryEvents.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Lifecycle lock — guards backgroundEventSource reference only.
    // close() is called OUTSIDE the lock to avoid blocking other callers (~2s executor shutdown).
    private val lock = Any()
    private var backgroundEventSource: BackgroundEventSource? = null

    // Generation counter for stale-callback protection.
    // Volatile because it is written under lock but read from library threads without lock.
    @Volatile
    private var generation: Long = 0L

    @Volatile
    private var isShutdown = false

    private val consecutiveErrors = AtomicInteger(0)

    fun connect() {
        val besRef: BackgroundEventSource
        synchronized(lock) {
            if (isShutdown) {
                AppLog.w(TAG, "connect() called after shutdown – ignoring")
                return
            }
            if (backgroundEventSource != null) {
                AppLog.d(TAG, "connect() called but already active – no-op")
                return
            }

            AppLog.d(TAG, "connect() – creating BackgroundEventSource")
            _connectionState.value = ConnectionState.Connecting
            consecutiveErrors.set(0)

            val gen = ++generation
            besRef = createBackgroundEventSource(gen)
            backgroundEventSource = besRef
        }
        besRef.start()
    }

    fun disconnect() {
        AppLog.d(TAG, "disconnect() called")
        val toClose: BackgroundEventSource?
        synchronized(lock) {
            toClose = detachLocked()
            _connectionState.value = ConnectionState.Disconnected
        }
        toClose?.closeSafely()
    }

    fun reconnect() {
        AppLog.d(TAG, "reconnect() called")
        val toClose: BackgroundEventSource?
        val besRef: BackgroundEventSource
        synchronized(lock) {
            if (isShutdown) {
                AppLog.w(TAG, "reconnect() called after shutdown – ignoring")
                return
            }
            toClose = detachLocked()
            _connectionState.value = ConnectionState.Connecting
            consecutiveErrors.set(0)

            val gen = ++generation
            besRef = createBackgroundEventSource(gen)
            backgroundEventSource = besRef
        }
        toClose?.closeSafely()
        besRef.start()
    }

    /** Reset error counter — call on foreground resume before reconnect. */
    fun resetConsecutiveErrors() {
        consecutiveErrors.set(0)
    }

    fun shutdown() {
        AppLog.d(TAG, "shutdown() called")
        val toClose: BackgroundEventSource?
        synchronized(lock) {
            isShutdown = true
            toClose = detachLocked()
            _connectionState.value = ConnectionState.Disconnected
        }
        toClose?.closeSafely()
    }

    /**
     * Detach the current BackgroundEventSource and invalidate its generation.
     * Must be called while holding [lock]. The returned BES should be closed OUTSIDE the lock.
     */
    private fun detachLocked(): BackgroundEventSource? {
        val bes = backgroundEventSource
        backgroundEventSource = null
        // Increment generation so any pending callbacks from bes are ignored
        generation++
        return bes
    }

    private fun BackgroundEventSource.closeSafely() {
        AppLog.d(TAG, "Closing BackgroundEventSource")
        try {
            close()
        } catch (e: Exception) {
            AppLog.w(TAG, "Error closing BackgroundEventSource: ${e.message}", e)
        }
    }

    private fun createBackgroundEventSource(gen: Long): BackgroundEventSource {
        val eventUrl = "$baseUrl/global/event"
        AppLog.d(TAG, "SSE target URL: $eventUrl")

        val connectStrategy = ConnectStrategy.http(URI(eventUrl))
            .httpClient(okHttpClient)
            .readTimeout(0, TimeUnit.SECONDS)

        val eventSourceBuilder = EventSource.Builder(connectStrategy)
            .errorStrategy(ErrorStrategy.alwaysContinue())
            .retryDelay(3, TimeUnit.SECONDS)

        val handler = SseEventHandler(gen)

        return BackgroundEventSource.Builder(handler, eventSourceBuilder)
            .threadBaseName("OpenCodeSSE")
            .connectionErrorHandler(ConnectionErrorHandler { t ->
                // Decision-only — do NOT emit events here to avoid duplicates with onError/onClosed.
                if (isShutdown || !isActiveGeneration(gen)) {
                    AppLog.d(TAG, "Connection error after shutdown/stale → SHUTDOWN (${t.message})")
                    ConnectionErrorHandler.Action.SHUTDOWN
                } else {
                    AppLog.d(TAG, "Connection error, library will retry: ${t.message}")
                    ConnectionErrorHandler.Action.PROCEED
                }
            })
            .build()
    }

    /** Returns true if [gen] matches the current active generation and we're not shut down. */
    private fun isActiveGeneration(gen: Long): Boolean =
        !isShutdown && generation == gen

    private fun parseAndEmitEvent(data: String) {
        try {
            val globalEvent = json.decodeFromString<GlobalEventDto>(data)
            val event = eventMapper.mapToEvent(globalEvent.payload)
            if (event != null) {
                val emitted = emitMappedEvent(event, globalEvent.directory)
                AppLog.d(TAG, "Event emitted: ${event::class.simpleName}, success=$emitted")
                if (!emitted) {
                    AppLog.w(TAG, "Dropped event (buffer full): ${event::class.simpleName}")
                }
            }
        } catch (e: Exception) {
            try {
                val eventData = json.decodeFromString<EventDataDto>(data)
                val event = eventMapper.mapToEvent(eventData)
                if (event != null) {
                    val emitted = emitMappedEvent(event, directory = null)
                    AppLog.d(TAG, "Event emitted (fallback): ${event::class.simpleName}, success=$emitted")
                    if (!emitted) {
                        AppLog.w(TAG, "Dropped event (buffer full, fallback): ${event::class.simpleName}")
                    }
                }
            } catch (e2: Exception) {
                if (!data.contains("server.heartbeat") && !data.contains("server.connected")) {
                    AppLog.e(TAG, "Failed to parse event (${data.length} chars): ${data.take(80)}…", e2)
                }
            }
        }
    }

    private fun emitEvent(event: OpenCodeEvent) {
        val emitted = _events.tryEmit(event)
        if (!emitted) {
            AppLog.w(TAG, "Dropped lifecycle event (buffer full): ${event::class.simpleName}")
        }
    }

    private fun emitMappedEvent(event: OpenCodeEvent, directory: String?): Boolean {
        val eventEmitted = _events.tryEmit(event)
        val scopedEmitted = _directoryEvents.tryEmit(DirectoryEvent(directory = directory, event = event))
        if (!scopedEmitted) {
            AppLog.w(TAG, "Dropped directory event (buffer full): ${event::class.simpleName}")
        }
        return eventEmitted && scopedEmitted
    }

    /**
     * BackgroundEventHandler implementation. Each instance is bound to a [gen]
     * so stale callbacks from a closed BackgroundEventSource are safely ignored.
     *
     * Note on library callback sequence for FaultEvents:
     *   1. onError(cause) — called on event thread
     *   2. onClosed()     — called on event thread
     *   3. ConnectionErrorHandler.onConnectionError(cause) — called on stream thread
     * When ConnectionErrorHandler returns PROCEED, the library auto-reconnects and
     * onOpen() fires once the new connection succeeds. We only emit Error from onError()
     * and don't emit Disconnected from onClosed() to avoid UI flicker during retries.
     */
    private inner class SseEventHandler(private val gen: Long) : BackgroundEventHandler {

        // Track whether onError already fired for this failure cycle to avoid double-counting.
        // FaultEvent sequence: onError → onClosed → ConnectionErrorHandler for one failure.
        @Volatile
        private var errorFiredSinceOpen = false

        override fun onOpen() {
            if (!isActiveGeneration(gen)) return
            AppLog.d(TAG, "SSE connected (onOpen)")
            errorFiredSinceOpen = false
            consecutiveErrors.set(0)
            _connectionState.value = ConnectionState.Connected
            emitEvent(OpenCodeEvent.Connected)
        }

        override fun onMessage(event: String, messageEvent: MessageEvent) {
            if (!isActiveGeneration(gen)) return
            val data = messageEvent.data
            AppLog.v(TAG, "SSE message received: event=$event, length=${data.length}")
            parseAndEmitEvent(data)
        }

        override fun onComment(comment: String) {
            // Keepalive — nothing to do
        }

        override fun onClosed() {
            if (!isActiveGeneration(gen)) return
            // If onError already fired for this failure cycle, don't double-count.
            // The library calls onError → onClosed for the same FaultEvent.
            if (errorFiredSinceOpen) {
                AppLog.d(TAG, "SSE stream closed (onClosed) after onError – skipping duplicate count")
                errorFiredSinceOpen = false
                return
            }
            // Clean close without preceding error (e.g., server shutdown)
            val closedCount = consecutiveErrors.incrementAndGet()
            if (closedCount >= MAX_CONSECUTIVE_ERRORS) {
                AppLog.w(TAG, "SSE stream closed (onClosed), $closedCount consecutive errors – escalating to Disconnected")
                _connectionState.value = ConnectionState.Disconnected
            } else {
                AppLog.d(TAG, "SSE stream closed (onClosed), consecutiveErrors=$closedCount, library may auto-reconnect")
                // Don't set Error state here to avoid UI flicker during auto-retries
            }
        }

        override fun onError(t: Throwable) {
            if (!isActiveGeneration(gen)) return
            errorFiredSinceOpen = true
            val errorCount = consecutiveErrors.incrementAndGet()
            if (errorCount >= MAX_CONSECUTIVE_ERRORS) {
                AppLog.e(TAG, "SSE error (onError): ${t.message}, $errorCount consecutive errors – escalating to Disconnected", t)
                _connectionState.value = ConnectionState.Disconnected
            } else {
                AppLog.e(TAG, "SSE error (onError): ${t.message}, consecutiveErrors=$errorCount", t)
                _connectionState.value = ConnectionState.Error(t.message)
            }
            emitEvent(OpenCodeEvent.Error(t))
        }
    }
}
