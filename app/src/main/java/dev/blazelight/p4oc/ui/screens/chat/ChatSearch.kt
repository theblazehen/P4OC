package dev.blazelight.p4oc.ui.screens.chat

import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.Part

/**
 * A search hit inside the open conversation: the LazyColumn block index that
 * contains the query (used to scroll the match into view) plus the id of the
 * first message in that block (stable identity for highlighting).
 */
internal data class ChatSearchMatch(
    val blockIndex: Int,
    val messageId: String,
)

/**
 * The plain text of a message a user could meaningfully search: visible content
 * ([Part.Text]), model reasoning ([Part.Reasoning]), invoked tool names and
 * sub-task prompts. Binary/structural parts (files, patches, snapshots) are skipped.
 */
internal fun MessageWithParts.searchableText(): String = buildString {
    parts.forEach { part ->
        when (part) {
            is Part.Text -> append(part.text).append('\n')
            is Part.Reasoning -> append(part.text).append('\n')
            is Part.Tool -> append(part.toolName).append('\n')
            is Part.Subtask -> append(part.prompt).append('\n').append(part.description).append('\n')
            else -> {}
        }
    }
}

/**
 * Blocks containing [query] (case-insensitive substring), in display order.
 * Returns an empty list for a blank query. Pure function — trivially testable
 * and recomputed via `remember(blocks, query)` on each keystroke.
 */
internal fun findChatMatches(blocks: List<MessageBlock>, query: String): List<ChatSearchMatch> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    val matches = ArrayList<ChatSearchMatch>()
    blocks.forEachIndexed { index, block ->
        val messages = when (block) {
            is MessageBlock.UserBlock -> listOf(block.message)
            is MessageBlock.AssistantBlock -> block.messages
        }
        val firstId = messages.firstOrNull()?.message?.id ?: return@forEachIndexed
        if (messages.any { it.searchableText().contains(needle, ignoreCase = true) }) {
            matches.add(ChatSearchMatch(index, firstId))
        }
    }
    return matches
}
