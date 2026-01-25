package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.domain.model.*
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.components.toolwidgets.ToolCallWidget
import dev.blazelight.p4oc.ui.components.toolwidgets.ToolWidgetState

@Composable
fun ChatMessage(
    messageWithParts: MessageWithParts,
    onToolApprove: (String) -> Unit,
    onToolDeny: (String) -> Unit,
    defaultToolWidgetState: ToolWidgetState = ToolWidgetState.COMPACT,
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
                onToolDeny = onToolDeny,
                defaultToolWidgetState = defaultToolWidgetState
            )
        }
    }
}

@Composable
private fun UserMessage(messageWithParts: MessageWithParts) {
    val theme = LocalOpenCodeTheme.current
    val textParts = messageWithParts.parts.filterIsInstance<Part.Text>()
    val text = textParts.joinToString("\n") { it.text }

    // TUI style: Surface2 background + Mauve left border, no header
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp)
    ) {
        // Mauve left border
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(theme.secondary)
        )
        
        // Content with Surface2 background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.backgroundPanel.copy(alpha = 0.3f))
                .padding(horizontal = 6.dp, vertical = 2.dp)  // Reduced vertical padding
        ) {
            StreamingMarkdown(
                text = text,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AssistantMessage(
    messageWithParts: MessageWithParts,
    onToolApprove: (String) -> Unit,
    onToolDeny: (String) -> Unit,
    defaultToolWidgetState: ToolWidgetState = ToolWidgetState.COMPACT
) {
    // Build ordered groups: consecutive tools get batched, non-tools rendered individually
    // Invisible parts (StepStart, StepFinish, Snapshot, etc.) don't break tool groups
    val partGroups = remember(messageWithParts.parts) {
        buildList {
            var currentToolBatch = mutableListOf<Part.Tool>()
            
            for (part in messageWithParts.parts) {
                when (part) {
                    is Part.Tool -> currentToolBatch.add(part)
                    // Invisible parts - don't break tool groups, just skip
                    is Part.StepStart, is Part.StepFinish, is Part.Snapshot,
                    is Part.Agent, is Part.Retry, is Part.Compaction, is Part.Subtask -> {
                        // Skip - truly invisible
                    }
                    // Visible parts - flush tools before rendering
                    else -> {
                        if (currentToolBatch.isNotEmpty()) {
                            add(PartGroupItem.Tools(currentToolBatch.toList()))
                            currentToolBatch = mutableListOf()
                        }
                        add(PartGroupItem.Other(part))
                    }
                }
            }
            // Flush any trailing tools
            if (currentToolBatch.isNotEmpty()) {
                add(PartGroupItem.Tools(currentToolBatch.toList()))
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)  // Reduced from 2.dp
    ) {
        // Render part groups in order
        partGroups.forEach { group ->
            when (group) {
                is PartGroupItem.Tools -> {
                    // Render each tool as a separate widget (for parallel tool calls)
                    group.tools.forEach { tool ->
                        ToolCallWidget(
                            tool = tool,
                            defaultState = defaultToolWidgetState,
                            onToolApprove = onToolApprove,
                            onToolDeny = onToolDeny
                        )
                    }
                }
                is PartGroupItem.Other -> {
                    when (val part = group.part) {
                        is Part.Text -> TextPart(part)
                        is Part.Reasoning -> ReasoningPart(part)
                        is Part.File -> FilePart(part)
                        is Part.Patch -> CompactPatchPart(part)
                        else -> {} // Already handled invisible parts above
                    }
                }
            }
        }
    }
}

/**
 * Sealed class for grouping parts: either a batch of consecutive tools or a single other part
 */
private sealed class PartGroupItem {
    data class Tools(val tools: List<Part.Tool>) : PartGroupItem()
    data class Other(val part: Part) : PartGroupItem()
}

@Composable
private fun TextPart(part: Part.Text) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Key on content length to force remeasurement when content grows
        key(part.text.length) {
            StreamingMarkdown(
                text = part.text,
                isStreaming = part.isStreaming,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        if (part.isStreaming) {
            CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
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
        shape = RectangleShape
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isThinking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.dp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                } else {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
                
                Text(
                    text = if (isThinking) "Thinking..." else "Reasoning",
                    style = MaterialTheme.typography.labelSmall,
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
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            
            if (expanded && part.text.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
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
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AttachFile, 
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                Text(
                    text = part.filename ?: "File",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = part.mime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CompactPatchPart(part: Part.Patch) {
    var expanded by remember { mutableStateOf(false) }
    
    Surface(
        onClick = { expanded = !expanded },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RectangleShape
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Description, 
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Patch: ${part.files.size} file(s)",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (expanded) {
                part.files.forEach { file ->
                    Text(
                        text = "  $file",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (part.files.isNotEmpty()) {
                Text(
                    text = "  ${part.files.first()}" + if (part.files.size > 1) " ..." else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TokenUsageInfo(tokens: TokenUsage, cost: Double) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "${tokens.input}/${tokens.output}",
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
