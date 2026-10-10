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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val useV2: Boolean = false,
) {
    data class DirectoryEvent(
        val directory: String?,
        val event: OpenCodeEvent,
    )

    companion object {
        private const val TAG = "OpenCodeEventSource"
        private const val MAX_CONSECUTIVE_ERRORS = 15
        internal const val MAX_EVENT_DATA_CHARS = 4 * 1024 * 1024
    }

    private val eventPumpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eventChannel = Channel<QueuedEvent>(capacity = Channel.UNLIMITED)

    private val _events = MutableSharedFlow<OpenCodeEvent>(replay = 0)
    val events: SharedFlow<OpenCodeEvent> = _events.asSharedFlow()

    private val _directoryEvents = MutableSharedFlow<DirectoryEvent>(replay = 0)
    val directoryEvents: SharedFlow<DirectoryEvent> = _directoryEvents.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Monotonic reconnect counter. Increments on every active-generation onOpen, before
    // Connected is published. It is the authoritative reconnect signal: rapid or conflated
    // Connecting->Connected transitions that a StateFlow<ConnectionState> may coalesce
    // remain individually observable here, and downstream owners can key recovery off it.
    private val _connectionEpoch = MutableStateFlow(0L)
    val connectionEpoch: StateFlow<Long> = _connectionEpoch.asStateFlow()

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
    private val v2EventMapper = V2EventMapper(json, eventMapper)

    init {
        eventPumpScope.launch {
            for (queued in eventChannel) {
                if (queued.generation != null && !isActiveGeneration(queued.generation)) continue
                _events.emit(queued.event)
                if (queued.directory != null || queued.includeDirectoryEvent) {
                    _directoryEvents.emit(DirectoryEvent(directory = queued.directory, event = queued.event))
                }
            }
        }
    }

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
        eventChannel.cancel()
        eventPumpScope.cancel("OpenCodeEventSource shut down")
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
            AppLog.w(TAG, "Error closing BackgroundEventSource: ${e.javaClass.simpleName}")
        }
    }

    private fun createBackgroundEventSource(gen: Long): BackgroundEventSource {
        // V2 /api/event is live-only: events during a disconnect are missed. Each successful
        // onOpen advances the connection epoch so repositories can recover through REST.
        val eventUrl = if (useV2) "$baseUrl/api/event" else "$baseUrl/global/event"
        AppLog.d(TAG, "Creating SSE event source")

        val connectStrategy = ConnectStrategy.http(URI(eventUrl))
            .httpClient(okHttpClient)

        val eventSourceBuilder = EventSource.Builder(connectStrategy)
            .errorStrategy(ErrorStrategy.alwaysContinue())
            .retryDelay(3, TimeUnit.SECONDS)

        val handler = SseEventHandler(gen)

        return BackgroundEventSource.Builder(handler, eventSourceBuilder)
            .threadBaseName("OpenCodeSSE")
            .connectionErrorHandler(
                ConnectionErrorHandler { t -> connectionErrorAction(t, gen) }
            )
            .build()
    }

    // Decision-only — do NOT emit events here to avoid duplicates with onError/onClosed.
    private fun connectionErrorAction(t: Throwable, gen: Long): ConnectionErrorHandler.Action =
        if (isShutdown || !isActiveGeneration(gen)) {
            AppLog.d(TAG, "Connection error after shutdown/stale → SHUTDOWN (${t.javaClass.simpleName})")
            ConnectionErrorHandler.Action.SHUTDOWN
        } else if (consecutiveErrors.get() >= MAX_CONSECUTIVE_ERRORS) {
            AppLog.w(
                TAG,
                "Connection error cap reached (${consecutiveErrors.get()}) → SHUTDOWN (${t.javaClass.simpleName})"
            )
            ConnectionErrorHandler.Action.SHUTDOWN
        } else {
            AppLog.d(TAG, "Connection error; library will retry: ${t.javaClass.simpleName}")
            ConnectionErrorHandler.Action.PROCEED
        }

    /** Returns true if [gen] matches the current active generation and we're not shut down. */
    private fun isActiveGeneration(gen: Long): Boolean =
        !isShutdown && generation == gen

    private fun parseAndEmitEvent(data: String, gen: Long) {
        when {
            data.length > MAX_EVENT_DATA_CHARS -> rejectOversizedEvent(data.length, gen)
            useV2 -> {
                v2EventMapper.map(data)?.let { mapped ->
                    enqueueMappedEvent(mapped.event, mapped.directory, gen)
                    AppLog.d(TAG, "V2 event queued: ${mapped.event::class.simpleName}")
                }
            }
            else -> parseV1Event(data, gen)
        }
    }

    private fun parseV1Event(data: String, gen: Long) {
        try {
            val globalEvent = json.decodeFromString<GlobalEventDto>(data)
            val event = eventMapper.mapToEvent(globalEvent.payload)
            if (event != null) {
                enqueueMappedEvent(event, globalEvent.directory, gen)
                AppLog.d(TAG, "Event queued: ${event::class.simpleName}")
            }
        } catch (e: Exception) {
            try {
                val eventData = json.decodeFromString<EventDataDto>(data)
                val event = eventMapper.mapToEvent(eventData)
                if (event != null) {
                    enqueueMappedEvent(event, directory = null, gen)
                    AppLog.d(TAG, "Event queued (fallback): ${event::class.simpleName}")
                }
            } catch (e2: Exception) {
                if (!data.contains("server.heartbeat") && !data.contains("server.connected")) {
                    AppLog.e(TAG, "Failed to parse event (${data.length} chars): ${e2.javaClass.simpleName}")
                }
            }
        }
    }

    private fun rejectOversizedEvent(length: Int, gen: Long) {
        AppLog.w(TAG, "Rejecting oversized SSE event ($length chars)")
        if (!isActiveGeneration(gen)) return
        val error = IllegalStateException("SSE event exceeded the safe size limit")
        _connectionState.value = ConnectionState.Error(error.message.orEmpty())
        enqueueEvent(OpenCodeEvent.Error(error), gen)
        eventPumpScope.launch {
            if (isActiveGeneration(gen)) reconnect()
        }
    }

    private fun enqueueEvent(event: OpenCodeEvent, gen: Long? = null) {
        val result = eventChannel.trySend(
            QueuedEvent(event = event, directory = null, includeDirectoryEvent = false, generation = gen)
        )
        if (result.isFailure) {
            AppLog.e(TAG, "Failed to queue event: ${event::class.simpleName}")
        }
    }

    private fun enqueueMappedEvent(event: OpenCodeEvent, directory: String?, gen: Long) {
        val result = eventChannel.trySend(
            QueuedEvent(event = event, directory = directory, includeDirectoryEvent = true, generation = gen)
        )
        if (result.isFailure) {
            AppLog.e(TAG, "Failed to queue mapped event: ${event::class.simpleName}")
        }
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
            // Bump the reconnect epoch before publishing Connected so every successful
            // (re)connection is observable even if the state transition is coalesced.
            _connectionEpoch.update { it + 1 }
            _connectionState.value = ConnectionState.Connected
            enqueueEvent(OpenCodeEvent.Connected, gen)
        }

        override fun onMessage(event: String, messageEvent: MessageEvent) {
            if (!isActiveGeneration(gen)) return
            val data = messageEvent.data
            AppLog.v(TAG, "SSE message received: event=$event, length=${data.length}")
            parseAndEmitEvent(data, gen)
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
                AppLog.w(
                    TAG,
                    "SSE stream closed (onClosed), $closedCount consecutive errors – escalating to Disconnected"
                )
                _connectionState.value = ConnectionState.Disconnected
            } else {
                AppLog.d(
                    TAG,
                    "SSE stream closed (onClosed), consecutiveErrors=$closedCount, library may auto-reconnect"
                )
                // Don't set Error state here to avoid UI flicker during auto-retries
            }
        }

        override fun onError(t: Throwable) {
            if (!isActiveGeneration(gen)) return
            errorFiredSinceOpen = true
            val errorCount = consecutiveErrors.incrementAndGet()
            if (errorCount >= MAX_CONSECUTIVE_ERRORS) {
                AppLog.e(
                    TAG,
                    "SSE error: ${t.javaClass.simpleName}, $errorCount consecutive errors; disconnecting",
                )
                _connectionState.value = ConnectionState.Disconnected
            } else {
                AppLog.e(TAG, "SSE error (onError): ${t.javaClass.simpleName}, consecutiveErrors=$errorCount")
                _connectionState.value = ConnectionState.Error("Live updates are temporarily unavailable")
            }
            enqueueEvent(OpenCodeEvent.Error(t), gen)
        }
    }

    private data class QueuedEvent(
        val event: OpenCodeEvent,
        val directory: String?,
        val includeDirectoryEvent: Boolean,
        val generation: Long?,
    )
}
