package dev.blazelight.p4oc.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.Permission
import dev.blazelight.p4oc.ui.components.chat.ChatMessage
import dev.blazelight.p4oc.ui.components.toolwidgets.ToolWidgetState

// ── Flat item model ───────────────────────────────────────────────────────────
// Each element of this list becomes ONE LazyColumn item.
// This allows the LazyColumn to virtualize individual parts (thoughts, text
// blocks, tool calls) independently — critical when a session has many thoughts.
@Stable
sealed class FlatChatItem {
    // Separator / accent bar start for an assistant turn
    @Stable data class AssistantBarStart(val messageId: String) : FlatChatItem()
    // Separator / accent bar end for an assistant turn (revert row or spacing)
    @Stable data class AssistantBarEnd(
        val messageId: String,
        val showRevert: Boolean
    ) : FlatChatItem()
    @Stable data class UserPart(val messageWithParts: MessageWithParts) : FlatChatItem()
    @Stable data class TextPart(val part: Part.Text, val msgId: String) : FlatChatItem()
    @Stable data class ReasoningPart(val part: Part.Reasoning, val msgId: String) : FlatChatItem()
    @Stable data class ToolBatch(val tools: List<Part.Tool>, val msgId: String, val batchIndex: Int) : FlatChatItem()
    @Stable data class FilePart(val part: Part.File, val msgId: String) : FlatChatItem()
    @Stable data class PatchPart(val part: Part.Patch, val msgId: String) : FlatChatItem()
}

/**
 * Builds flat items already reversed for LazyColumn(reverseLayout = true).
 * Avoids a per-recomposition asReversed() O(N) alloc on the main thread hot path.
 * During streaming (50 tokens/sec) this eliminates ~50 full list copies per second.
 */
fun buildFlatItems(blocks: List<MessageBlock>): List<FlatChatItem> {
    val result = mutableListOf<FlatChatItem>()
    for (block in blocks) {
        when (block) {
            is MessageBlock.UserBlock -> result.add(FlatChatItem.UserPart(block.message))
            is MessageBlock.AssistantBlock -> {
                if (block.messages.isEmpty()) continue
                // Use lastId (newest message) as the block anchor key.
                // firstId changes when loadMore prepends older messages that merge
                // into this block — LazyColumn then sees a new key for the same bar
                // item and plays a reorder animation, briefly showing both old and new.
                // lastId is always the most-recent message and never changes on prepend.
                val lastId = block.messages.last().message.id
                result.add(FlatChatItem.AssistantBarStart(lastId))
                var toolBatch = mutableListOf<Part.Tool>()
                var batchIndex = 0
                for (msg in block.messages) {
                    val msgId = msg.message.id
                    for (part in msg.parts) {
                        when (part) {
                            is Part.Tool -> toolBatch.add(part)
                            is Part.StepStart, is Part.StepFinish, is Part.Snapshot,
                            is Part.Agent, is Part.Retry, is Part.Compaction,
                            is Part.Subtask -> Unit
                            else -> {
                                if (toolBatch.isNotEmpty()) {
                                    result.add(FlatChatItem.ToolBatch(toolBatch.toList(), msgId, batchIndex++))
                                    toolBatch = mutableListOf()
                                }
                                when (part) {
                                    is Part.Text      -> result.add(FlatChatItem.TextPart(part, msgId))
                                    is Part.Reasoning -> result.add(FlatChatItem.ReasoningPart(part, msgId))
                                    is Part.File      -> result.add(FlatChatItem.FilePart(part, msgId))
                                    is Part.Patch     -> result.add(FlatChatItem.PatchPart(part, msgId))
                                    is Part.Tool, is Part.StepStart, is Part.StepFinish,
                                    is Part.Snapshot, is Part.Agent, is Part.Retry,
                                    is Part.Compaction, is Part.Subtask -> Unit
                                }
                            }
                        }
                    }
                }
                if (toolBatch.isNotEmpty()) {
                    result.add(FlatChatItem.ToolBatch(toolBatch.toList(), lastId, batchIndex))
                }
                val hasRevert = block.messages.any { msg ->
                    msg.parts.any { it is Part.Tool }
                }
                result.add(FlatChatItem.AssistantBarEnd(lastId, hasRevert))
            }
        }
    }
    result.reverse()
    return result
}

/**
 * Incremental patch: replaces only the FlatChatItems whose messageId appears in [changedIds].
 * All other items are kept as-is (same object references — LazyColumn skips them).
 *
 * Algorithm:
 *  1. Scan [prev] to find the contiguous range owned by each changedId.
 *  2. For each changed message, rebuild its FlatChatItems from [allMessages] + [allBlocks].
 *  3. Splice the new items back at the same position.
 *
 * Falls back to full rebuild if structural changes (new messages, pagination) are detected.
 * The list is returned already reversed (bottom-first) to match LazyColumn(reverseLayout=true).
 */
fun patchFlatItems(
    prev: List<FlatChatItem>,
    allMessages: List<MessageWithParts>,
    changedIds: Set<String>
): List<FlatChatItem>? {
    if (changedIds.isEmpty()) return null
    // If any changed ID is NEW (not yet in prev), fall back to full rebuild
    val prevMsgIds = buildPrevMsgIdSet(prev)
    if (changedIds.any { it !in prevMsgIds }) return null

    // Rebuild only the blocks containing changed IDs
    val blocks = groupMessagesIntoBlocks(allMessages)
    // Build a map: msgId -> the FlatChatItems it produces (in forward order)
    val changedItemMap = buildChangedItemMap(blocks, changedIds)

    // Walk prev (which is already reversed) and splice replacements in
    val result = ArrayList<FlatChatItem>(prev.size)
    var i = 0
    while (i < prev.size) {
        val item = prev[i]
        val ownerId = flatItemOwnerId(item)
        if (ownerId != null && ownerId in changedIds) {
            // Skip all consecutive items owned by this message (they'll be replaced)
            while (i < prev.size && flatItemOwnerId(prev[i]) == ownerId) i++
            // Insert the replacement items (already in reversed order from changedItemMap)
            changedItemMap[ownerId]?.let { result.addAll(it) }
        } else {
            result.add(item)
            i++
        }
    }
    return result
}

private fun buildPrevMsgIdSet(items: List<FlatChatItem>): Set<String> =
    items.mapNotNullTo(HashSet()) { flatItemOwnerId(it) }

private fun flatItemOwnerId(item: FlatChatItem): String? = when (item) {
    is FlatChatItem.UserPart          -> item.messageWithParts.message.id
    is FlatChatItem.AssistantBarStart -> item.messageId
    is FlatChatItem.AssistantBarEnd   -> item.messageId
    is FlatChatItem.TextPart          -> item.msgId
    is FlatChatItem.ReasoningPart     -> item.msgId
    is FlatChatItem.ToolBatch         -> item.msgId
    is FlatChatItem.FilePart          -> item.msgId
    is FlatChatItem.PatchPart         -> item.msgId
}

private fun buildChangedItemMap(
    blocks: List<MessageBlock>,
    changedIds: Set<String>
): Map<String, List<FlatChatItem>> {
    val result = mutableMapOf<String, MutableList<FlatChatItem>>()
    for (block in blocks) {
        when (block) {
            is MessageBlock.UserBlock -> {
                val id = block.message.message.id
                if (id in changedIds) {
                    result.getOrPut(id) { mutableListOf() }.add(FlatChatItem.UserPart(block.message))
                }
            }
            is MessageBlock.AssistantBlock -> {
                val hasChanged = block.messages.any { it.message.id in changedIds }
                if (!hasChanged) continue
                val lastId = block.messages.last().message.id
                val items = mutableListOf<FlatChatItem>()
                items.add(FlatChatItem.AssistantBarStart(lastId))
                var toolBatch = mutableListOf<Part.Tool>()
                var batchIndex = 0
                for (msg in block.messages) {
                    val msgId = msg.message.id
                    for (part in msg.parts) {
                        when (part) {
                            is Part.Tool -> toolBatch.add(part)
                            is Part.StepStart, is Part.StepFinish, is Part.Snapshot,
                            is Part.Agent, is Part.Retry, is Part.Compaction,
                            is Part.Subtask -> Unit
                            else -> {
                                if (toolBatch.isNotEmpty()) {
                                    items.add(FlatChatItem.ToolBatch(toolBatch.toList(), msgId, batchIndex++))
                                    toolBatch = mutableListOf()
                                }
                                when (part) {
                                    is Part.Text      -> items.add(FlatChatItem.TextPart(part, msgId))
                                    is Part.Reasoning -> items.add(FlatChatItem.ReasoningPart(part, msgId))
                                    is Part.File      -> items.add(FlatChatItem.FilePart(part, msgId))
                                    is Part.Patch     -> items.add(FlatChatItem.PatchPart(part, msgId))
                                    is Part.Tool, is Part.StepStart, is Part.StepFinish,
                                    is Part.Snapshot, is Part.Agent, is Part.Retry,
                                    is Part.Compaction, is Part.Subtask -> Unit
                                }
                            }
                        }
                    }
                }
                if (toolBatch.isNotEmpty()) {
                    items.add(FlatChatItem.ToolBatch(toolBatch.toList(), lastId, batchIndex))
                }
                val hasRevert = block.messages.any { msg -> msg.parts.any { it is Part.Tool } }
                items.add(FlatChatItem.AssistantBarEnd(lastId, hasRevert))
                // Reverse so items are in bottom-first order (matching buildFlatItems output)
                items.reverse()
                // Map ALL msgIds in this block to the same reversed items
                // Create a copy for each message to avoid key duplication in LazyColumn
                block.messages.forEach { msg ->
                    if (msg.message.id in changedIds) {
                        result[msg.message.id] = items.toMutableList()
                    }
                }
            }
        }
    }
    return result
}

// ── Flat item renderer ────────────────────────────────────────────────────────
// Each FlatChatItem maps to one composable that is fully independent.
// The accent bar is replicated per-item via drawBehind so the LazyColumn
// never needs to measure a giant Column that spans all parts.

@Composable
internal fun FlatChatItemView(
    item: FlatChatItem,
    onToolApprove: (String) -> Unit,
    onToolDeny: (String) -> Unit,
    onToolAlways: (String) -> Unit,
    onOpenSubSession: ((String) -> Unit)? = null,
    defaultToolWidgetState: ToolWidgetState = ToolWidgetState.COMPACT,
    pendingPermissionsByCallId: Map<String, Permission> = emptyMap(),
    onRevert: ((String) -> Unit)? = null
) {
    when (item) {
        is FlatChatItem.UserPart -> {
            dev.blazelight.p4oc.ui.components.chat.ChatMessage(
                messageWithParts = item.messageWithParts,
                onToolApprove = onToolApprove,
                onToolDeny = onToolDeny,
                onToolAlways = onToolAlways,
                onOpenSubSession = onOpenSubSession,
                defaultToolWidgetState = defaultToolWidgetState,
                pendingPermissionsByCallId = pendingPermissionsByCallId,
                onRevert = onRevert
            )
        }
        // AssistantBarStart: zero-height spacer that opens the visual turn grouping
        is FlatChatItem.AssistantBarStart -> {
            Spacer(modifier = Modifier.height(4.dp))
        }
        // AssistantBarEnd: bottom spacing + optional revert button
        is FlatChatItem.AssistantBarEnd -> {
            if (item.showRevert && onRevert != null) {
                val theme = dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme.current
                androidx.compose.material3.Text(
                    text = "↺ ${androidx.compose.ui.res.stringResource(dev.blazelight.p4oc.R.string.revert_changes)}",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = theme.warning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onRevert(item.messageId) }
                        .padding(start = 10.dp, top = 2.dp, bottom = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        is FlatChatItem.TextPart -> {
            AssistantPartRow {
                dev.blazelight.p4oc.ui.components.chat.FlatTextPart(item.part)
            }
        }
        is FlatChatItem.ReasoningPart -> {
            AssistantPartRow {
                dev.blazelight.p4oc.ui.components.chat.FlatReasoningPart(item.part)
            }
        }
        is FlatChatItem.ToolBatch -> {
            AssistantPartRow {
                dev.blazelight.p4oc.ui.components.toolwidgets.ToolGroupWidget(
                    tools = item.tools,
                    defaultState = defaultToolWidgetState,
                    onToolApprove = onToolApprove,
                    onToolDeny = onToolDeny,
                    onOpenSubSession = onOpenSubSession
                )
                item.tools.forEach { tool ->
                    pendingPermissionsByCallId[tool.callID]?.let { perm ->
                        dev.blazelight.p4oc.ui.components.chat.FlatInlinePermission(
                            perm, onToolApprove, onToolAlways, onToolDeny
                        )
                    }
                }
            }
        }
        is FlatChatItem.FilePart -> {
            AssistantPartRow {
                dev.blazelight.p4oc.ui.components.chat.FlatFilePart(item.part)
            }
        }
        is FlatChatItem.PatchPart -> {
            AssistantPartRow {
                dev.blazelight.p4oc.ui.components.chat.FlatPatchPart(item.part)
            }
        }
    }
}

// Draws the 3dp left accent bar for assistant content rows without
// wrapping everything in a Column — zero IntrinsicSize overhead.
@Composable
private fun AssistantPartRow(content: @Composable () -> Unit) {
    val theme = dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme.current
    val barColor = remember(theme.accent) { theme.accent.copy(alpha = 0.85f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color = barColor,
                    topLeft = Offset.Zero,
                    size = Size(3.dp.toPx(), size.height)
                )
            }
            .padding(start = 10.dp)
    ) {
        content()
    }
}

/**
 * Sealed class representing a block of messages for display.
 * @Stable tells Compose that equality-based skipping is safe for these classes.
 */
@Stable
sealed class MessageBlock {
    @Stable data class UserBlock(val message: MessageWithParts) : MessageBlock()
    @Stable data class AssistantBlock(val messages: List<MessageWithParts>) : MessageBlock()
}

/**
 * Group messages into blocks: user messages standalone, consecutive assistant messages merged.
 */
private const val TAG = "MessageBlockUtils"

fun groupMessagesIntoBlocks(messages: List<MessageWithParts>): List<MessageBlock> {
    // Early return for empty list
    if (messages.isEmpty()) return emptyList()

    val blocks = mutableListOf<MessageBlock>()
    var i = 0

    while (i < messages.size) {
        val current = messages[i]

        if (current.message is Message.User) {
            blocks.add(MessageBlock.UserBlock(current))
            i++
        } else {
            // Collect consecutive assistant messages
            val assistantMessages = mutableListOf<MessageWithParts>()
            while (i < messages.size && messages[i].message is Message.Assistant) {
                assistantMessages.add(messages[i])
                i++
            }
            // GUARD: Only add if we have messages
            if (assistantMessages.isNotEmpty()) {
                blocks.add(MessageBlock.AssistantBlock(assistantMessages))
            }
        }
    }

    return blocks
}

@Composable
internal fun MessageBlockView(
    block: MessageBlock,
    onToolApprove: (String) -> Unit,
    onToolDeny: (String) -> Unit,
    onToolAlways: (String) -> Unit,
    onOpenSubSession: ((String) -> Unit)? = null,
    defaultToolWidgetState: ToolWidgetState = ToolWidgetState.COMPACT,
    pendingPermissionsByCallId: Map<String, Permission> = emptyMap(),
    onRevert: ((String) -> Unit)? = null
) {
    when (block) {
        is MessageBlock.UserBlock -> {
            ChatMessage(
                messageWithParts = block.message,
                onToolApprove = onToolApprove,
                onToolDeny = onToolDeny,
                onToolAlways = onToolAlways,
                onOpenSubSession = onOpenSubSession,
                defaultToolWidgetState = defaultToolWidgetState,
                pendingPermissionsByCallId = pendingPermissionsByCallId,
                onRevert = onRevert
            )
        }
        is MessageBlock.AssistantBlock -> {
            // GUARD: Protect against empty assistant blocks
            if (block.messages.isEmpty()) {
                AppLog.e(TAG, "AssistantBlock with empty messages - skipping render")
                return
            }
            // Cache key: firstMessageId + total part count across all assistant messages.
            // This is stable across SSE flushes that produce no new parts (e.g. heartbeats),
            // but correctly invalidates when text/reasoning/tool parts are actually added.
            // Avoids the O(n) list-equality check that remember(block.messages) would trigger
            // on every flush because block.messages is always a new List reference.
            val firstId    = block.messages.first().message.id
            val totalParts = block.messages.sumOf { it.parts.size }
            val mergedMessageWithParts = remember(firstId, totalParts) {
                MessageWithParts(
                    message = block.messages.first().message,
                    parts = block.messages.flatMap { it.parts }
                )
            }

            ChatMessage(
                messageWithParts = mergedMessageWithParts,
                onToolApprove = onToolApprove,
                onToolDeny = onToolDeny,
                onToolAlways = onToolAlways,
                onOpenSubSession = onOpenSubSession,
                defaultToolWidgetState = defaultToolWidgetState,
                pendingPermissionsByCallId = pendingPermissionsByCallId,
                onRevert = onRevert
            )
        }
    }
}
