package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.domain.model.*

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
        modifier = modifier.fillMaxWidth()
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "You",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        StreamingMarkdown(
            text = text,
            modifier = Modifier.fillMaxWidth()
        )
    }
    
    HorizontalDivider(
        modifier = Modifier.padding(top = 2.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

@Composable
private fun AssistantMessage(
    messageWithParts: MessageWithParts,
    onToolApprove: (String) -> Unit,
    onToolDeny: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        val assistantMsg = messageWithParts.message as? Message.Assistant
        val displayName = assistantMsg?.modelID ?: "Assistant"
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary
            )
            
            assistantMsg
            assistantMsg?.let { msg ->
                if (msg.tokens.input > 0 || msg.tokens.output > 0) {
                    Spacer(modifier = Modifier.weight(1f))
                    TokenUsageInfo(msg.tokens, msg.cost)
                }
            }
        }
        
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            messageWithParts.parts.forEach { part ->
                when (part) {
                    is Part.Text -> TextPart(part)
                    is Part.Reasoning -> ReasoningPart(part)
                    is Part.Tool -> EnhancedToolPart(part, onToolApprove, onToolDeny)
                    is Part.File -> FilePart(part)
                    is Part.Patch -> EnhancedPatchPart(part)
                    is Part.StepStart -> {}
                    is Part.StepFinish -> {}
                    is Part.Snapshot -> {}
                    is Part.Agent -> {}
                    is Part.Retry -> {}
                    is Part.Compaction -> {}
                    is Part.Subtask -> {}
                }
            }
        }
    }
    
    HorizontalDivider(
        modifier = Modifier.padding(top = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun TextPart(part: Part.Text) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top
    ) {
        StreamingMarkdown(
            text = part.text,
            isStreaming = part.isStreaming,
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
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp)
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
                StreamingMarkdown(
                    text = part.text,
                    styles = rememberTertiaryMarkdownStyles(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}



@Composable
private fun FilePart(part: Part.File) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AttachFile, 
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
private fun EnhancedPatchPart(part: Part.Patch) {
    var expanded by remember { mutableStateOf(false) }
    
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Surface(
                onClick = { expanded = !expanded },
                color = androidx.compose.ui.graphics.Color.Transparent
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Description, 
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Patch: ${part.files.size} file(s)",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                part.files.forEach { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 28.dp, top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = file,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                part.files.take(3).forEach { file ->
                    Text(
                        text = file,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp)
                    )
                }
                if (part.files.size > 3) {
                    Text(
                        text = "... and ${part.files.size - 3} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PatchPart(part: Part.Patch) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Description, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
