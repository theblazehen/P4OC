package com.pocketcode.ui.components.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pocketcode.domain.model.*
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun ChatMessage(
    messageWithParts: MessageWithParts,
    onToolApprove: (String) -> Unit,
    onToolDeny: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val message = messageWithParts.message
    val isUser = message is Message.User

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            UserMessage(messageWithParts)
        } else {
            AssistantMessage(
                messageWithParts = messageWithParts,
                onToolApprove = onToolApprove,
                onToolDeny = onToolDeny
            )
        }
    }
}

@Composable
private fun UserMessage(messageWithParts: MessageWithParts) {
    val textParts = messageWithParts.parts.filterIsInstance<Part.Text>()
    val text = textParts.joinToString("\n") { it.text }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
        modifier = Modifier.widthIn(max = 320.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun AssistantMessage(
    messageWithParts: MessageWithParts,
    onToolApprove: (String) -> Unit,
    onToolDeny: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = 360.dp)
    ) {
        messageWithParts.parts.forEach { part ->
            when (part) {
                is Part.Text -> TextPart(part)
                is Part.Reasoning -> ReasoningPart(part)
                is Part.Tool -> EnhancedToolPart(part, onToolApprove, onToolDeny)
                is Part.File -> FilePart(part)
                is Part.Patch -> PatchPart(part)
                // New part types - render as minimal UI or skip
                is Part.StepStart -> {} // No UI for step markers
                is Part.StepFinish -> {} // No UI for step markers
                is Part.Snapshot -> {} // No UI for snapshots
                is Part.Agent -> {} // Could show agent switch indicator
                is Part.Retry -> {} // Could show retry indicator
                is Part.Compaction -> {} // No UI for compaction
                is Part.Subtask -> {} // Could show subtask info
            }
        }

        val assistantMsg = messageWithParts.message as? Message.Assistant
        assistantMsg?.let { msg ->
            if (msg.tokens.input > 0 || msg.tokens.output > 0) {
                TokenUsageInfo(msg.tokens, msg.cost)
            }
        }
    }
}

@Composable
private fun TextPart(part: Part.Text) {
    val syntaxHighlightBg = MaterialTheme.colorScheme.surfaceContainerHighest
    
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MarkdownText(
                markdown = part.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                syntaxHighlightColor = syntaxHighlightBg,
                linkColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (part.isStreaming) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.dp
                )
            }
        }
    }
}

@Composable
private fun ReasoningPart(part: Part.Reasoning) {
    var expanded by remember { mutableStateOf(false) }
    
    val thinkingDuration = part.time?.let { time ->
        val durationMs = (time.end ?: System.currentTimeMillis()) - time.start
        when {
            durationMs < 1000 -> "${durationMs}ms"
            durationMs < 60000 -> "${durationMs / 1000}s"
            else -> "${durationMs / 60000}m ${(durationMs % 60000) / 1000}s"
        }
    }
    
    val isThinking = part.time?.end == null

    Surface(
        onClick = { expanded = !expanded },
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isThinking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                } else {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
                
                Text(
                    text = if (isThinking) "Thinking..." else "Reasoning",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                
                thinkingDuration?.let { duration ->
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
                
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            
            if (expanded && part.text.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f)
                )
                Text(
                    text = part.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontFamily = FontFamily.Default,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.4f
                )
            }
        }
    }
}



@Composable
private fun FilePart(part: Part.File) {
    Card {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AttachFile, contentDescription = null)
            Column {
                Text(
                    text = part.filename ?: "File",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = part.mime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PatchPart(part: Part.Patch) {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Description, contentDescription = null)
                Text(
                    text = "Patch: ${part.files.size} file(s)",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            part.files.take(5).forEach { file ->
                Text(
                    text = file,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp)
                )
            }
            if (part.files.size > 5) {
                Text(
                    text = "... and ${part.files.size - 5} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 28.dp)
                )
            }
        }
    }
}

@Composable
private fun TokenUsageInfo(tokens: TokenUsage, cost: Double) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Text(
            text = "${tokens.input}/${tokens.output} tokens",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        if (cost > 0) {
            Text(
                text = "$${String.format(java.util.Locale.US, "%.4f", cost)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
