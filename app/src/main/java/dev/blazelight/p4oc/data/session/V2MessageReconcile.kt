package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.Part

/**
 * Reconcile rules for OpenCode v2, where assistant text/reasoning streams only as ephemeral SSE
 * content events while message metadata, tool state, and every other part arrive only through the
 * REST projection, which can lag the stream. REST wins for everything only REST carries; streamed
 * content wins wherever the lagging REST projection would truncate or drop it.
 */
internal object V2MessageReconcile {
    /**
     * Authoritative window replacement: REST repairs deletions and stale state, except that an
     * assistant message REST has not projected yet survives while it is still streaming text.
     */
    fun replace(loaded: List<MessageWithParts>, current: List<MessageWithParts>): List<MessageWithParts> {
        val loadedIds = loaded.mapTo(HashSet()) { it.message.id }
        val stillStreaming = current.filter { it.message.id !in loadedIds && it.isStreamingAssistant() }
        return (mergeLoaded(loaded, current) + stillStreaming).sortedBy { it.message.createdAt }
    }

    /** Non-destructive merge (pagination, repeated races): messages outside the window are kept. */
    fun merge(loaded: List<MessageWithParts>, current: List<MessageWithParts>): List<MessageWithParts> {
        val loadedIds = loaded.mapTo(HashSet()) { it.message.id }
        val outsideWindow = current.filter { it.message.id !in loadedIds }
        return (mergeLoaded(loaded, current) + outsideWindow).sortedBy { it.message.createdAt }
    }

    private fun mergeLoaded(loaded: List<MessageWithParts>, current: List<MessageWithParts>): List<MessageWithParts> {
        val currentById = current.associateBy { it.message.id }
        return loaded.map { message -> mergeMessage(message, currentById[message.message.id]) }
    }

    private fun mergeMessage(loaded: MessageWithParts, current: MessageWithParts?): MessageWithParts {
        if (current == null) return loaded
        val currentPartsById = current.parts.associateBy { it.id }
        val loadedPartIds = loaded.parts.mapTo(HashSet()) { it.id }
        // Content REST has not projected yet was produced by the stream and stays until REST has it.
        val unprojectedContent = if (current.message is Message.Assistant) {
            current.parts.filter { it.id !in loadedPartIds && it.isStreamedContent() }
        } else {
            emptyList()
        }
        val parts = loaded.parts.map { part -> mergePart(part, currentPartsById[part.id]) } + unprojectedContent
        return MessageWithParts(loaded.message, parts)
    }

    private fun mergePart(loaded: Part, current: Part?): Part = when {
        loaded is Part.Text && current is Part.Text &&
            keepsStreamedText(rest = loaded.text, streamed = current.text, streaming = current.isStreaming) ->
            loaded.copy(text = current.text, isStreaming = current.isStreaming)
        loaded is Part.Reasoning && current is Part.Reasoning &&
            keepsStreamedText(rest = loaded.text, streamed = current.text, streaming = false) ->
            loaded.copy(text = current.text)
        else -> loaded
    }

    /**
     * Streamed text stays while its deltas are still arriving (REST text would be re-appended by
     * later deltas) or while REST holds only a strict prefix of it (REST is lagging the stream).
     */
    private fun keepsStreamedText(rest: String, streamed: String, streaming: Boolean): Boolean =
        streaming || (streamed.length > rest.length && streamed.startsWith(rest))

    private fun Part.isStreamedContent(): Boolean = this is Part.Text || this is Part.Reasoning

    private fun MessageWithParts.isStreamingAssistant(): Boolean =
        message is Message.Assistant && parts.any { it is Part.Text && it.isStreaming }
}
