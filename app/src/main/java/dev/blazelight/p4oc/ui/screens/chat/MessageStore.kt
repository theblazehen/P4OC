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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns message state and all mutation logic.
 * Pure state container — no network dependencies.
 */
class MessageStore(
    private val sessionId: String,
    private val scope: CoroutineScope
) {
    private val _messagesMap: SnapshotStateMap<String, MessageWithParts> = mutableStateMapOf()

    // Version counter to trigger flow emission when map values change
    // SnapshotStateMap only detects key add/remove, not value updates
    private val _messagesVersion = mutableStateOf(0L)

    private val messagesMutex = Mutex()

    /**
     * Messages flow — emits when any message in the map changes.
     *
     * We use a version counter inside snapshotFlow to detect value changes.
     * The snapshotFlow will emit whenever _messagesVersion changes.
     */
    val messages: StateFlow<List<MessageWithParts>> = snapshotFlow {
        // Read version to establish dependency — triggers emission on value changes
        _messagesVersion.value
        _messagesMap.values.sortedBy { it.message.createdAt }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * Load initial messages from API response.
     */
    fun loadInitial(messages: List<MessageWithParts>) {
        messages.forEach { msg ->
            _messagesMap[msg.message.id] = msg
        }
    }

    fun upsertMessage(message: Message) {
        scope.launch {
            messagesMutex.withLock {
                val existing = _messagesMap[message.id]
                _messagesMap[message.id] = if (existing != null) {
                    existing.copy(message = message)
                } else {
                    MessageWithParts(message, emptyList())
                }
                _messagesVersion.value++
                AppLog.d(TAG, "upsertMessage: ${message.id}, exists=${existing != null}")
            }
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
                val updatedParts = if (partIndex >= 0) {
                    existing.parts.toMutableList().apply {
                        this[partIndex] = applyDelta(this[partIndex], part, delta)
                    }
                } else {
                    existing.parts + part
                }

                _messagesMap[messageId] = existing.copy(parts = updatedParts)
                _messagesVersion.value++
                AppLog.d(TAG, "upsertPart: partId=${part.id}, messageId=$messageId, delta=${delta?.length ?: 0} chars, partCount=${updatedParts.size}")
            }
        }
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

    private companion object {
        const val TAG = "MessageStore"
    }
}
