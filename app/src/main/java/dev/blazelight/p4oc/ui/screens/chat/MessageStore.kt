package dev.blazelight.p4oc.ui.screens.chat

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.TokenUsage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns message state and all mutation logic.
 * Pure state container — no network dependencies.
 */
class MessageStore(
    private val sessionId: String,
    private val scope: CoroutineScope
) {
    private val _messagesMap: SnapshotStateMap<String, MessageWithParts> = mutableStateMapOf()
    private val messageOrder = mutableListOf<String>() // ascending by createdAt

    // Version counter to trigger flow emission when map values change
    // SnapshotStateMap only detects key add/remove, not value updates
    private val _messagesVersion = mutableStateOf(0L)

    private val messagesMutex = Mutex()

    // PAGINATION: Store all loaded messages, display only visible subset
    private val allMessagesMap = mutableMapOf<String, MessageWithParts>()
    private var visibleMessageCount = 0
    private val MESSAGES_PER_PAGE = 25
    private val INITIAL_MESSAGE_COUNT = 25

    // Buffered updates to coalesce rapid SSE text deltas
    private val pendingMutex = Mutex()
    private val pendingUpdates = mutableMapOf<String, MutableMap<String, PendingDelta>>() // messageId -> (partId -> delta)
    private var flushJob: Job? = null
    @Volatile private var flushDelayMs: Long = 16L // 1 frame @60fps
    @Volatile private var reasoningFlushDelayMs: Long = 32L // 2 frames — coalesce reasoning tokens

    /**
     * Messages flow - emits whenever messages or parts change.
     * CRITICAL: No conflate to ensure all updates reach UI.
     */
    val messages: StateFlow<List<MessageWithParts>> = snapshotFlow {
        // Read version to establish dependency — triggers emission on value changes
        _messagesVersion.value
        // Map stable order to current map values
        messageOrder.mapNotNull { id -> _messagesMap[id] }
    }
        .stateIn(
            scope = scope,
            started = SharingStarted.Lazily, // Keep alive for entire ViewModel lifetime
            initialValue = emptyList()
        )

    /**
     * Load initial messages from API response.
     * PAGINATED: Load only last 25 messages initially for instant paint.
     * Remaining messages loaded on demand via loadMore().
     */
    fun loadInitial(messages: List<MessageWithParts>) {
        val sorted = messages.sortedBy { it.message.createdAt }
        AppLog.d(TAG, "loadInitial: Total ${sorted.size} messages, loading last $INITIAL_MESSAGE_COUNT initially")
        
        scope.launch(Dispatchers.Main.immediate) {
            // Store ALL messages in background map
            allMessagesMap.clear()
            sorted.forEach { msg ->
                allMessagesMap[msg.message.id] = msg
            }
            
            // Clear and load only last 25 into visible map
            _messagesMap.clear()
            messageOrder.clear()
            
            // Take last 25 messages (most recent)
            val initialMessages = sorted.takeLast(INITIAL_MESSAGE_COUNT)
            visibleMessageCount = initialMessages.size
            
            initialMessages.forEach { msg ->
                _messagesMap[msg.message.id] = msg
                messageOrder.add(msg.message.id)
            }
            
            _messagesVersion.value++
            AppLog.d(TAG, "loadInitial: Instant paint with $visibleMessageCount messages (${sorted.size - visibleMessageCount} more available)")
        }
    }
    
    /**
     * Load next page of older messages.
     * Returns true if more messages available, false if all loaded.
     */
    fun loadMore(count: Int = MESSAGES_PER_PAGE): Boolean {
        val allMessageIds = allMessagesMap.keys.sortedBy { allMessagesMap[it]?.message?.createdAt }
        val currentVisibleIds = messageOrder.toSet()
        
        // Find oldest visible message index
        val oldestVisibleIndex = allMessageIds.indexOfFirst { it in currentVisibleIds }
        if (oldestVisibleIndex <= 0) {
            AppLog.d(TAG, "loadMore: All messages already visible")
            return false // All loaded or nothing to load
        }
        
        // Load 'count' messages before the oldest visible
        val startIndex = (oldestVisibleIndex - count).coerceAtLeast(0)
        val messagesToLoad = allMessageIds.subList(startIndex, oldestVisibleIndex)
        
        messagesToLoad.forEach { id ->
            allMessagesMap[id]?.let { msg ->
                _messagesMap[id] = msg
                messageOrder.add(0, id) // Add at beginning (oldest)
            }
        }
        
        visibleMessageCount += messagesToLoad.size
        _messagesVersion.value++
        
        val hasMore = startIndex > 0
        AppLog.d(TAG, "loadMore: Loaded ${messagesToLoad.size} older messages, total visible: $visibleMessageCount, hasMore: $hasMore")
        return hasMore
    }
    
    /**
     * Check if there are more messages to load.
     */
    fun hasMoreMessages(): Boolean {
        return allMessagesMap.size > visibleMessageCount
    }
    
    /**
     * Get total message count (including not yet visible).
     */
    fun getTotalMessageCount(): Int = allMessagesMap.size

    fun upsertMessage(message: Message) {
        scope.launch {
            messagesMutex.withLock {
                val existing = _messagesMap[message.id]
                val isNew = existing == null
                val messageType = when (message) {
                    is Message.User -> "USER"
                    is Message.Assistant -> "ASSISTANT"
                }
                _messagesMap[message.id] = if (existing != null) {
                    existing.copy(message = message)
                } else {
                    // New message — insert into ordered list by createdAt
                    insertIntoOrder(message.id, message.createdAt)
                    MessageWithParts(message, emptyList())
                }
                _messagesVersion.value++
                AppLog.d(TAG, "upsertMessage: msgId=${message.id}, type=$messageType, sessionId=${message.sessionID}, isNew=$isNew, totalMessages=${_messagesMap.size}")
            }
        }
    }

    /**
     * Batch upsert for better performance when loading multiple messages.
     * Single version bump for all changes reduces recompositions.
     */
    fun upsertMessages(messages: List<Message>) {
        scope.launch {
            messagesMutex.withLock {
                messages.forEach { message ->
                    val existing = _messagesMap[message.id]
                    _messagesMap[message.id] = if (existing != null) {
                        existing.copy(message = message)
                    } else {
                        insertIntoOrder(message.id, message.createdAt)
                        MessageWithParts(message, emptyList())
                    }
                }
                _messagesVersion.value++
                AppLog.d(TAG, "upsertMessages: batch of ${messages.size}")
            }
        }
    }

    /**
     * Coalesced variant of upsertPart: accumulates rapid updates and applies in a single batch.
     * This reduces recompositions under heavy streaming.
     * 
     * OPTIMIZED: Reasoning Parts flush immediately for real-time visibility.
     */
    fun upsertPartBuffered(part: Part, delta: String?) {
        scope.launch {
            AppLog.d(TAG, "upsertPartBuffered: partId=${part.id}, msgId=${part.messageID}, delta=${delta?.length ?: 0} chars")
            
            // Reasoning parts are buffered too — they stream dozens of tokens/sec.
            // Bypassing the buffer caused a full list recomposition per token = scroll jank.
            // We use a shorter delay (reasoningFlushDelayMs) so they still feel live.
            
            pendingMutex.withLock {
                val byPart = pendingUpdates.getOrPut(part.messageID) { mutableMapOf() }
                val existing = byPart[part.id]
                if (existing == null) {
                    byPart[part.id] = PendingDelta(part, delta)
                } else {
                    // Merge: accumulate deltas for streaming text+reasoning; last-write for others
                    val merged = when {
                        existing.part is Part.Text && part is Part.Text -> {
                            val acc = (existing.delta ?: "") + (delta ?: "")
                            PendingDelta(part.copy(text = part.text, isStreaming = true), acc)
                        }
                        existing.part is Part.Reasoning && part is Part.Reasoning -> {
                            // Accumulate reasoning delta too \u2014 avoids replace-on-every-token
                            val acc = (existing.delta ?: "") + (delta ?: "")
                            PendingDelta(part, acc)
                        }
                        else -> PendingDelta(part, delta)
                    }
                    byPart[part.id] = merged
                }

                if (flushJob?.isActive != true) {
                    val delay = if (part is Part.Reasoning) reasoningFlushDelayMs else flushDelayMs
                    flushJob = scope.launch {
                        delay(delay)
                        flushPendingParts()
                    }
                }
            }
        }
    }

    private suspend fun flushPendingParts() {
        val batch: Map<String, Map<String, PendingDelta>> = pendingMutex.withLock {
            if (pendingUpdates.isEmpty()) {
                AppLog.v(TAG, "flushPendingParts: no pending updates")
                return
            }
            val snapshot = pendingUpdates.mapValues { it.value.toMap() }.toMap()
            val msgCount = snapshot.size
            val partCount = snapshot.values.sumOf { it.size }
            AppLog.d(TAG, "flushPendingParts: flushing $msgCount messages, $partCount parts")
            pendingUpdates.clear()
            snapshot
        }

        var changed = false
        messagesMutex.withLock {
            batch.forEach { (messageId, partsMap) ->
                val existing = _messagesMap[messageId] ?: run {
                    val placeholder = createPlaceholderMessage(messageId)
                    _messagesMap[messageId] = placeholder
                    ensureInOrderList(messageId, placeholder.message.createdAt)
                    placeholder
                }

                var updated = existing
                partsMap.values.forEach { pd ->
                    val partIndex = updated.parts.indexOfFirst { it.id == pd.part.id }
                    val newPart = applyDeltaIfNeeded(updated, pd)
                    val newParts = if (partIndex >= 0) {
                        updated.parts.toMutableList().apply { this[partIndex] = newPart }
                    } else {
                        updated.parts + newPart
                    }
                    updated = updated.copy(parts = newParts)
                    changed = true
                }

                _messagesMap[messageId] = updated
            }
            if (changed) _messagesVersion.value++
        }
    }

    private fun applyDeltaIfNeeded(existing: MessageWithParts, pd: PendingDelta): Part {
        val incoming = pd.part
        val delta = pd.delta
        val current = existing.parts.firstOrNull { it.id == incoming.id }
        return when {
            delta != null && incoming is Part.Text && current is Part.Text ->
                incoming.copy(text = current.text + delta, isStreaming = true)
            delta != null && incoming is Part.Reasoning && current is Part.Reasoning ->
                incoming.copy(text = current.text + delta)
            else -> incoming
        }
    }

    /**
     * Handle part updates — simple approach.
     *
     * All parts go to _messagesMap. SnapshotStateMap + stable LazyColumn keys
     * ensure only the changed message item recomposes.
     */
    fun upsertPart(part: Part, delta: String?) {
        scope.launch {
            messagesMutex.withLock {
                val messageId = part.messageID

                val existing = _messagesMap[messageId] ?: run {
                    val placeholder = createPlaceholderMessage(messageId)
                    _messagesMap[messageId] = placeholder
                    AppLog.d(TAG, "upsertPart: Created placeholder for message $messageId")
                    placeholder
                }

                val partIndex = existing.parts.indexOfFirst { it.id == part.id }
                AppLog.d(TAG, "upsertPart: existing parts=${existing.parts.size}, partIndex=$partIndex, partId=${part.id}")
                val updatedParts = if (partIndex >= 0) {
                    existing.parts.toMutableList().apply {
                        this[partIndex] = applyDelta(this[partIndex], part, delta)
                    }
                } else {
                    existing.parts + part
                }

                // Single put — SnapshotStateMap.put() is atomic, no need for remove+put
                _messagesMap[messageId] = existing.copy(parts = updatedParts)
                _messagesVersion.value++
                AppLog.d(TAG, "upsertPart: DONE - partId=${part.id}, messageId=$messageId, delta=${delta?.length ?: 0} chars, oldCount=${existing.parts.size}, newCount=${updatedParts.size}")
            }
        }
    }

    /**
     * Remove a message entirely from the store.
     * Called when the server sends a message.removed event.
     */
    fun removeMessage(messageId: String) {
        scope.launch {
            messagesMutex.withLock {
                if (_messagesMap.remove(messageId) != null) {
                    messageOrder.remove(messageId)
                    _messagesVersion.value++
                    AppLog.d(TAG, "removeMessage: $messageId")
                }
            }
        }
    }

    /**
     * Remove a specific part from a message.
     * Called when the server sends a message.part.removed event.
     */
    fun removePart(messageId: String, partId: String) {
        scope.launch {
            messagesMutex.withLock {
                val existing = _messagesMap[messageId] ?: return@withLock
                val updatedParts = existing.parts.filter { it.id != partId }
                if (updatedParts.size != existing.parts.size) {
                    _messagesMap[messageId] = existing.copy(parts = updatedParts)
                    _messagesVersion.value++
                    AppLog.d(TAG, "removePart: partId=$partId from messageId=$messageId")
                }
            }
        }
    }

    /**
     * Thread-safe snapshot of current messages for abort summary building.
     * Uses mutex to ensure consistency during concurrent SSE part updates.
     */
    suspend fun snapshotMessages(): List<MessageWithParts> =
        messagesMutex.withLock {
            _messagesMap.values.sortedBy { it.message.createdAt }
        }

    /**
     * Clear streaming flags on all text parts in the messages map.
     * Called when session becomes idle or is aborted.
     */
    suspend fun clearStreamingFlags() {
        messagesMutex.withLock {
            var changed = false
            _messagesMap.forEach { (id, msgWithParts) ->
                val updatedParts = msgWithParts.parts.map { part ->
                    if (part is Part.Text && part.isStreaming) {
                        part.copy(isStreaming = false)
                    } else {
                        part
                    }
                }
                if (updatedParts != msgWithParts.parts) {
                    _messagesMap[id] = msgWithParts.copy(parts = updatedParts)
                    changed = true
                }
            }
            if (changed) {
                _messagesVersion.value++
            }
        }
    }

    private fun applyDelta(existing: Part, incoming: Part, delta: String?): Part {
        return if (delta != null && incoming is Part.Text && existing is Part.Text) {
            incoming.copy(text = existing.text + delta, isStreaming = true)
        } else {
            incoming
        }
    }

    private data class PendingDelta(val part: Part, val delta: String?)

    /**
     * Adjust flush cadence depending on scroll state.
     * Slightly slower during scroll reduces layout thrash while keeping streaming responsive.
     */
    fun setFlushDelayWhileScrolling(isScrolling: Boolean) {
        flushDelayMs = if (isScrolling) 50L else 16L          // 3 frames during scroll vs 1 at rest
        reasoningFlushDelayMs = if (isScrolling) 80L else 32L // 5 frames during scroll vs 2 at rest
    }

    /** Directly control flush delay (used for speed-adaptive tuning). */
    fun setFlushDelayMs(delayMs: Long) {
        flushDelayMs = delayMs.coerceIn(8L, 120L)
    }

    private fun insertIntoOrder(messageId: String, createdAt: Long) {
        // Binary search by createdAt over current order
        var low = 0
        var high = messageOrder.size
        while (low < high) {
            val mid = (low + high) ushr 1
            val midCreated = _messagesMap[messageOrder[mid]]?.message?.createdAt ?: Long.MAX_VALUE
            if (midCreated < createdAt) low = mid + 1 else high = mid
        }
        messageOrder.add(low, messageId)
    }

    private fun ensureInOrderList(messageId: String, createdAt: Long) {
        if (messageOrder.contains(messageId)) return
        insertIntoOrder(messageId, createdAt)
    }

    private fun createPlaceholderMessage(messageId: String): MessageWithParts {
        return MessageWithParts(
            message = Message.Assistant(
                id = messageId,
                sessionID = sessionId,
                createdAt = System.currentTimeMillis(),
                parentID = "",
                providerID = "",
                modelID = "",
                mode = "",
                agent = "",
                cost = 0.0,
                tokens = TokenUsage(input = 0, output = 0)
            ),
            parts = emptyList()
        )
    }

    /**
     * Snapshot of current messages for testing — avoids reflection.
     */
    @androidx.annotation.VisibleForTesting
    internal fun currentMessagesSnapshot(): List<MessageWithParts> =
        _messagesMap.values.sortedBy { it.message.createdAt }

    private companion object {
        const val TAG = "MessageStore"
    }
}
