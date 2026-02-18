package dev.blazelight.p4oc.ui.screens.chat

import androidx.compose.runtime.Composable
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.Permission
import dev.blazelight.p4oc.ui.components.chat.ChatMessage
import dev.blazelight.p4oc.ui.components.toolwidgets.ToolWidgetState

/**
 * Sealed class representing a block of messages for display.
 * User messages are their own block. Consecutive assistant messages are merged.
 */
internal sealed class MessageBlock {
    data class UserBlock(val message: MessageWithParts) : MessageBlock()
    data class AssistantBlock(val messages: List<MessageWithParts>) : MessageBlock()
}

/**
 * Group messages into blocks: user messages standalone, consecutive assistant messages merged.
 */
internal fun groupMessagesIntoBlocks(messages: List<MessageWithParts>): List<MessageBlock> {
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
            blocks.add(MessageBlock.AssistantBlock(assistantMessages))
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
    pendingPermissionsByCallId: Map<String, Permission> = emptyMap()
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
                pendingPermissionsByCallId = pendingPermissionsByCallId
            )
        }
        is MessageBlock.AssistantBlock -> {
            val allParts = block.messages.flatMap { it.parts }
            val mergedMessageWithParts = MessageWithParts(
                message = block.messages.first().message,
                parts = allParts
            )
            
            ChatMessage(
                messageWithParts = mergedMessageWithParts,
                onToolApprove = onToolApprove,
                onToolDeny = onToolDeny,
                onToolAlways = onToolAlways,
                onOpenSubSession = onOpenSubSession,
                defaultToolWidgetState = defaultToolWidgetState,
                pendingPermissionsByCallId = pendingPermissionsByCallId
            )
        }
    }
}
