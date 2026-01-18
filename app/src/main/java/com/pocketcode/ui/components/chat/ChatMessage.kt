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
                is Part.Tool -> ToolPart(part, onToolApprove, onToolDeny)
                is Part.File -> FilePart(part)
                is Part.Patch -> PatchPart(part)
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

    Surface(
        onClick = { expanded = !expanded },
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "Reasoning",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (expanded) {
                Text(
                    text = part.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ToolPart(
    part: Part.Tool,
    onApprove: (String) -> Unit,
    onDeny: (String) -> Unit
) {
    val state = part.state
    val (containerColor, icon, statusText) = when (state) {
        is ToolState.Pending -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            Icons.Default.HourglassEmpty,
            "Pending approval"
        )
        is ToolState.Running -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            Icons.Default.PlayArrow,
            "Running..."
        )
        is ToolState.Completed -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            Icons.Default.CheckCircle,
            state.title
        )
        is ToolState.Error -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            Icons.Default.Error,
            "Error: ${state.error}"
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = part.toolName,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state is ToolState.Running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            if (state is ToolState.Pending) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onDeny(part.callID) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Deny")
                    }
                    Button(
                        onClick = { onApprove(part.callID) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Allow")
                    }
                }
            }

            if (state is ToolState.Completed && state.output.isNotBlank()) {
                var showOutput by remember { mutableStateOf(false) }
                TextButton(
                    onClick = { showOutput = !showOutput },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(if (showOutput) "Hide output" else "Show output")
                }
                if (showOutput) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.output.take(500) + if (state.output.length > 500) "..." else "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
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
                text = "$${String.format("%.4f", cost)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
