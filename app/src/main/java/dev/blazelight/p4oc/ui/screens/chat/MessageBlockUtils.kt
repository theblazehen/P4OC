package dev.blazelight.p4oc.ui.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.Permission
import dev.blazelight.p4oc.ui.components.chat.ChatMessage
import dev.blazelight.p4oc.ui.components.toolwidgets.ToolWidgetState

/**
 * Sealed class representing a block of messages for display.
 * User messages are their own block. Consecutive assistant messages are merged.
 */
sealed class MessageBlock {
    data class UserBlock(val message: MessageWithParts) : MessageBlock()
    data class AssistantBlock(val messages: List<MessageWithParts>) : MessageBlock()
}

/**
 * Group messages into blocks: user messages standalone, consecutive assistant messages merged.
 */
private const val TAG = "MessageBlockUtils"

fun groupMessagesIntoBlocks(messages: List<MessageWithParts>): List<MessageBlock> {
    AppLog.d(TAG, "groupMessagesIntoBlocks: input size=${messages.size}")
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

    AppLog.d(TAG, "groupMessagesIntoBlocks: output size=${blocks.size}")
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
            val mergedMessageWithParts = remember(block) {
                AppLog.d(TAG, "AssistantBlock merging ${block.messages.size} messages")
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
