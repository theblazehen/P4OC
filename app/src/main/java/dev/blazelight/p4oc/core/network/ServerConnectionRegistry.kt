@file:Suppress("Indentation", "ImportOrdering")

package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.core.datastore.SavedServer
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.server.ScopedEvent
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.server.WorkspaceKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Owns connection state per saved server so multi-server tabs do not depend on a
 * mutable app-global current server. This registry is the sole authority that creates
 * and owns live [ConnectionManager] instances.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServerConnectionRegistry constructor(
    private val settingsDataStore: SettingsDataStore,
    private val connectionManagerFactory: (ServerConfig, () -> ServerGeneration) -> ConnectionManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val generationSequence = ServerGenerationSequence
    private val states = ConcurrentHashMap<String, MutableStateFlow<ConnectionState>>()
    private val managers = ConcurrentHashMap<String, ConnectionManager>()
    private val connections = ConcurrentHashMap<String, MutableStateFlow<Connection?>>()
    private val generationStates = ConcurrentHashMap<GenerationKey, MutableStateFlow<ConnectionState>>()
    private val generationStateLock = Any()
    private val generationEpochs = ConcurrentHashMap<GenerationKey, MutableStateFlow<Long>>()
    private val generationEpochBridges = ConcurrentHashMap<GenerationKey, Job>()
    private val stateCollectors = ConcurrentHashMap<String, Job>()
    private val connectionCollectors = ConcurrentHashMap<String, Job>()
    private val eventCollectors = ConcurrentHashMap<String, Job>()
    private val connectJobs = ConcurrentHashMap<String, Job>()
    private val serverEvents = ConcurrentHashMap<String, MutableSharedFlow<ScopedEvent>>()
    private val _scopedEvents = MutableSharedFlow<ScopedEvent>()

    private val staleGenerationState = MutableStateFlow<ConnectionState>(STALE_GENERATION_ERROR).asStateFlow()

    /** All live events from every registry-owned server connection. */
    val scopedEvents: SharedFlow<ScopedEvent> = _scopedEvents.asSharedFlow()

    /** Stable live event stream for one server, including each reconnect generation. */
    fun events(serverRef: ServerRef): Flow<ScopedEvent> = eventFlow(serverRef.endpointKey).asSharedFlow()

    fun connectionState(serverRef: ServerRef): StateFlow<ConnectionState> = stateFlow(
        serverRef.endpointKey
    ).asStateFlow()

    /** Connection state for an immutable workspace generation. */
    fun connectionState(
        serverRef: ServerRef,
        generation: ServerGeneration,
    ): StateFlow<ConnectionState> = synchronized(generationStateLock) {
        val manager = managers[serverRef.endpointKey]
        val activeGeneration = manager?.connection?.value?.generation
        if (activeGeneration != generation) return@synchronized staleGenerationState

        generationStates.getOrPut(GenerationKey(serverRef.endpointKey, generation)) {
            MutableStateFlow(manager.connectionState.value)
        }
    }

    internal val generationStateCount: Int
        get() = generationStates.size

    /**
     * Monotonic reconnect epoch for an immutable workspace generation. Returns a stable per-generation
     * flow even when requested before the manager publishes its active connection; the flow bridges
     * to that generation's live [OpenCodeEventSource.connectionEpoch] once it activates. A stale or
     * wrong generation stays pinned at 0 so no reconnect is observed for it.
     */
    fun connectionEpoch(
        serverRef: ServerRef,
        generation: ServerGeneration,
    ): StateFlow<Long> = synchronized(generationStateLock) {
        val key = GenerationKey(serverRef.endpointKey, generation)
        val flow = generationEpochs.getOrPut(key) { MutableStateFlow(0L) }
        val manager = managers[serverRef.endpointKey]
        if (manager?.connection?.value?.generation == generation) {
            ensureEpochBridge(key, manager)
        }
        flow.asStateFlow()
    }

    fun connection(serverRef: ServerRef): StateFlow<Connection?> = connectionFlow(serverRef.endpointKey).asStateFlow()

    fun api(serverRef: ServerRef): OpenCodeApi? = managers[serverRef.endpointKey]?.getApi()

    /**
     * Resolves the REST API owned by one exact server connection generation.
     * A workspace owner must never silently move to a newer connection after reconnect.
     */
    fun api(serverRef: ServerRef, generation: dev.blazelight.p4oc.domain.server.ServerGeneration): OpenCodeApi? {
        val connection = managers[serverRef.endpointKey]?.connection?.value
        return connection.apiForGeneration(generation)
    }

    /** Resolves the connection and auth-aware client from the same registry-owned manager. */
    fun terminalTransport(
        serverRef: ServerRef,
        generation: dev.blazelight.p4oc.domain.server.ServerGeneration,
    ): TerminalTransport? {
        val manager = managerForGeneration(serverRef, generation)
        val connection = manager?.connection?.value
        val authClient = manager?.authOkHttpClient?.value
        val pairIsCurrent = connection != null &&
            authClient != null &&
            manager.connection.value === connection &&
            connection.generation == generation
        return if (pairIsCurrent) TerminalTransport(checkNotNull(connection), checkNotNull(authClient)) else null
    }

    fun generation(serverRef: ServerRef): dev.blazelight.p4oc.domain.server.ServerGeneration? =
        managers[serverRef.endpointKey]?.currentGeneration

    private fun managerForGeneration(
        serverRef: ServerRef,
        generation: dev.blazelight.p4oc.domain.server.ServerGeneration,
    ): ConnectionManager? = managers[serverRef.endpointKey]?.takeIf { it.currentGeneration == generation }

    /** Starts a registry-owned connection attempt without awaiting its probe result. */
    fun connect(server: SavedServer, password: String? = null) {
        scope.launch { connectAndAwait(server, password) }
    }

    /**
     * Connects one explicit server and returns only the result of the attempt that still owns
     * this endpoint. A replacement attempt cancels this suspension, so stale successes cannot
     * be used by a caller to persist or navigate.
     */
    @Suppress("LongMethod", "Indentation")
    suspend fun connectAndAwait(server: SavedServer, password: String? = null): Result<List<ProjectDto>> =
        coroutineScope {
        val serverRef = server.toServerRef()
        val state = stateFlow(server.endpointKey)
        state.value = ConnectionState.Connecting
        val manager = managers.getOrPut(server.endpointKey) {
            connectionManagerFactory(server.toServerConfig(), generationSequence::next)
        }
        stateCollectors.computeIfAbsent(server.endpointKey) {
            scope.launch {
                manager.connectionState.collect {
                    val managerState = manager.connectionState.value
                    if (managerState !is ConnectionState.Disconnected || state.value !is ConnectionState.Connecting) {
                        state.value = managerState
                    }
                    reconcileGenerationState(server.endpointKey, manager)
                }
            }
        }
        val connection = connectionFlow(server.endpointKey)
        connection.value = manager.connection.value
        connectionCollectors.computeIfAbsent(server.endpointKey) {
            scope.launch {
                manager.connection.collect { managerConnection ->
                    connection.value = managerConnection
                    reconcileGenerationState(server.endpointKey, manager)
                }
            }
        }
        val events = eventFlow(server.endpointKey)
        eventCollectors.computeIfAbsent(server.endpointKey) {
            scope.launch {
                manager.connection.flatMapLatest { activeConnection ->
                    if (activeConnection == null) {
                        emptyFlow()
                    } else {
                        activeConnection.eventSource.directoryEvents.map { directoryEvent ->
                            ScopedEvent(
                                serverRef = serverRef,
                                generation = activeConnection.generation,
                                workspaceKey = directoryEvent.directory?.takeIf(String::isNotBlank)
                                    ?.let(WorkspaceKey::Directory) ?: WorkspaceKey.Global,
                                event = directoryEvent.event,
                            )
                        }
                    }
                }.collect { event ->
                    events.emit(event)
                    _scopedEvents.emit(event)
                    if (event.event is OpenCodeEvent.GlobalDisposed) {
                        invalidateGeneration(event.serverRef, event.generation)
                    }
                }
            }
        }
        connectJobs.remove(server.endpointKey)?.cancel()
        val outcome = CompletableDeferred<Result<List<ProjectDto>>>()
        val connectJob = launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            val resolvedPassword = password ?: settingsDataStore.getSavedServerPassword(server)
            val result = manager.connect(server.toServerConfig(), resolvedPassword)
            coroutineContext.ensureActive()
            val stillOwned = connectJobs[server.endpointKey] === coroutineContext[Job] &&
                managers[server.endpointKey] === manager
            if (!stillOwned) throw CancellationException("Connection attempt was replaced")
            if (result.isSuccess) {
                val connectedKey = manager.connection.value?.config?.url?.let(ServerUrl::endpointKey)
                if (connectedKey != server.endpointKey) {
                    throw CancellationException("Connection attempt no longer owns the requested server")
                }
            } else {
                state.value = ConnectionState.Error("Connection failed")
            }
            outcome.complete(result)
        }
        connectJobs[server.endpointKey] = connectJob
        connectJob.invokeOnCompletion { cause ->
            connectJobs.remove(server.endpointKey, connectJob)
            if (cause != null) outcome.cancel(cause as? CancellationException)
        }
        connectJob.start()
        try {
            outcome.await()
        } finally {
            if (connectJobs.remove(server.endpointKey, connectJob)) connectJob.cancel()
        }
    }

    suspend fun connect(serverId: String, password: String? = null) {
        val server = settingsDataStore.findSavedServer(serverId) ?: return
        connect(server, password ?: settingsDataStore.getSavedServerPassword(server))
    }

    fun disconnect(serverRef: ServerRef) {
        connectJobs.remove(serverRef.endpointKey)?.cancel()
        stateCollectors.remove(serverRef.endpointKey).let { collector ->
            if (collector != null) collector.cancel()
        }
        connectionCollectors.remove(serverRef.endpointKey).let { collector ->
            if (collector != null) collector.cancel()
        }
        eventCollectors.remove(serverRef.endpointKey)?.cancel()
        connectionFlow(serverRef.endpointKey).value = null
        stateFlow(serverRef.endpointKey).value = ConnectionState.Disconnected
        staleAndEvictGenerationStates(serverRef.endpointKey)
        managers.remove(serverRef.endpointKey).let { manager ->
            if (manager != null) manager.disconnect()
        }
    }

    /**
     * Applies a server-global disposal only to the connection generation that emitted it.
     * A delayed event from an old SSE stream must not tear down its replacement.
     */
    @Suppress("ReturnCount")
    internal fun invalidateGeneration(serverRef: ServerRef, generation: ServerGeneration): Boolean {
        val endpointKey = serverRef.endpointKey
        val manager = managers[endpointKey] ?: return false
        if (!manager.disconnect(generation)) return false
        if (!managers.remove(endpointKey, manager)) return false

        connectJobs.remove(endpointKey)?.cancel()
        stateCollectors.remove(endpointKey)?.cancel()
        connectionCollectors.remove(endpointKey)?.cancel()
        eventCollectors.remove(endpointKey)?.cancel()
        connectionFlow(endpointKey).value = null
        stateFlow(endpointKey).value = ConnectionState.Disconnected
        staleAndEvictGenerationStates(endpointKey)
        return true
    }

    suspend fun reconnectAll(openTabServers: Set<ServerRef>) {
        val savedByKey = settingsDataStore.getSavedServers().associateBy { it.endpointKey }
        for (serverRef in openTabServers) {
            val saved = savedByKey[serverRef.endpointKey] ?: continue
            connect(saved, settingsDataStore.getSavedServerPassword(saved))
        }
    }

    /** Gives every currently owned connection one opportunity to recover after foregrounding. */
    fun onAppForegrounded() {
        managers.values.toList().forEach(ConnectionManager::onAppForegrounded)
    }

    private fun stateFlow(endpointKey: String): MutableStateFlow<ConnectionState> =
        states.getOrPut(endpointKey) { MutableStateFlow(ConnectionState.Disconnected) }

    private fun connectionFlow(endpointKey: String): MutableStateFlow<Connection?> =
        connections.getOrPut(endpointKey) { MutableStateFlow(null) }

    private fun eventFlow(endpointKey: String): MutableSharedFlow<ScopedEvent> =
        serverEvents.getOrPut(endpointKey) { MutableSharedFlow() }

    private fun reconcileGenerationState(endpointKey: String, manager: ConnectionManager) {
        synchronized(generationStateLock) {
            val activeGeneration = manager.connection.value?.generation
            val iterator = generationStates.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key.endpointKey == endpointKey && entry.key.generation != activeGeneration) {
                    entry.value.value = STALE_GENERATION_ERROR
                    iterator.remove()
                }
            }
            if (activeGeneration != null) {
                generationStates[GenerationKey(endpointKey, activeGeneration)]?.value = manager.connectionState.value
            }
            reconcileGenerationEpochs(endpointKey, manager, activeGeneration)
        }
    }

    /**
     * Mirrors the live event-source epoch of the exact active generation into any stable per-generation
     * flow returned earlier by [connectionEpoch]. When no generation is active yet, existing bridges
     * are cancelled and their flows reset to 0 while the requested entries are preserved (so a
     * pre-connect subscription is not orphaned). When a generation is active, bridges for generations
     * that are no longer active are cancelled and their flows reset/evicted to the stale 0 state.
     */
    private fun reconcileGenerationEpochs(
        endpointKey: String,
        manager: ConnectionManager,
        activeGeneration: ServerGeneration?,
    ) {
        val endpointEntries = generationEpochs.entries.filter { it.key.endpointKey == endpointKey }
        if (activeGeneration == null) {
            // No active generation yet: cancel any existing endpoint bridges and reset their flows
            // to 0 so a leaked collector cannot push stale updates, but KEEP the requested entries
            // so a pre-connect connectionEpoch() call is not orphaned.
            endpointEntries.forEach { (key, flow) ->
                flow.value = 0L
                generationEpochBridges.remove(key)?.cancel()
            }
        } else {
            // Active generation present: evict entries for generations that are no longer active.
            endpointEntries
                .filter { it.key.generation != activeGeneration }
                .forEach { (key, flow) ->
                    flow.value = 0L
                    // Reset to 0 so a previously active generation stops reflecting its source.
                    generationEpochBridges.remove(key)?.cancel()
                    // Evict the stale entry only if it still maps to this flow; a caller-held flow
                    // (via asStateFlow) stays valid at 0 because it wraps the same MutableStateFlow.
                    generationEpochs.remove(key, flow)
                }
        }
        // Bridge only the exact active generation once a generation is present. When activeGeneration
        // is null this filter matches nothing, so pre-publication entries survive unbridged.
        generationEpochs.keys
            .filter { it.endpointKey == endpointKey && it.generation == activeGeneration }
            .forEach { ensureEpochBridge(it, manager) }
    }

    internal val generationEpochCount: Int
        get() = generationEpochs.size

    internal val generationEpochBridgeCount: Int
        get() = generationEpochBridges.size

    /**
     * Starts the bridge from the exact active generation's live source epoch, if not already running.
     * MUST be called while holding [generationStateLock]; both callers ([connectionEpoch] and
     * [reconcileGenerationState]) synchronize on it, and the bridge-identity check relies on that
     * serialization with resets/evictions.
     */
    private fun ensureEpochBridge(key: GenerationKey, manager: ConnectionManager) {
        if (generationEpochBridges.containsKey(key)) return
        val sourceEpoch = manager.connection.value?.eventSource?.connectionEpoch
        val flow = generationEpochs[key]
        if (sourceEpoch == null || flow == null) return
        // Seed synchronously so an active lookup reflects the current source epoch immediately,
        // before the async bridge's first collect.
        flow.value = sourceEpoch.value
        // Start lazily so the job is registered in the map before its first collect runs; otherwise
        // an emission between the sync seed and the map assignment could be lost by the identity check.
        val bridgeJob = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            sourceEpoch.collect { value ->
                synchronized(generationStateLock) {
                    // Only write if this bridge is still the current bridge for the key; otherwise a
                    // stale in-flight emission after reset/eviction could repopulate a nonzero epoch.
                    if (generationEpochBridges[key] === coroutineContext[Job]) {
                        flow.value = value
                    }
                }
            }
        }
        generationEpochBridges[key] = bridgeJob
        bridgeJob.start()
    }

    private fun staleAndEvictGenerationStates(endpointKey: String) {
        synchronized(generationStateLock) {
            val iterator = generationStates.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key.endpointKey == endpointKey) {
                    entry.value.value = STALE_GENERATION_ERROR
                    iterator.remove()
                }
            }
            val epochIterator = generationEpochs.entries.iterator()
            while (epochIterator.hasNext()) {
                val entry = epochIterator.next()
                if (entry.key.endpointKey == endpointKey) {
                    // Reset so a caller-held flow cannot retain a nonzero active epoch after
                    // explicit disconnect/invalidate.
                    entry.value.value = 0L
                    generationEpochBridges.remove(entry.key)?.cancel()
                    epochIterator.remove()
                }
            }
        }
    }

    private data class GenerationKey(val endpointKey: String, val generation: ServerGeneration)

    private companion object {
        val STALE_GENERATION_ERROR = ConnectionState.Error("Server connection generation is no longer available")
    }
}

data class TerminalTransport(
    val connection: Connection,
    val authClient: OkHttpClient,
)

fun SavedServer.toServerRef(): ServerRef = ServerRef.fromEndpointKey(endpointKey, displayName)

fun SavedServer.toServerConfig(): ServerConfig = ServerConfig(
    url = endpoint,
    name = displayName,
    isLocal = endpoint.toHttpUrlOrNull()?.host in setOf("localhost", "127.0.0.1", "::1"),
    username = username,
    allowInsecure = allowInsecure,
)
