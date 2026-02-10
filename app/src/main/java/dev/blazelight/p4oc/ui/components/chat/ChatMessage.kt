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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.domain.model.*
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.components.toolwidgets.ToolGroupWidget
import dev.blazelight.p4oc.ui.components.toolwidgets.ToolWidgetState
import dev.blazelight.p4oc.ui.components.TuiLoadingIndicator

@Composable
fun ChatMessage(
    messageWithParts: MessageWithParts,
    onToolApprove: (String) -> Unit,
    onToolDeny: (String) -> Unit,
    onOpenSubSession: ((String) -> Unit)? = null,
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
                onOpenSubSession = onOpenSubSession,
                defaultToolWidgetState = defaultToolWidgetState
            )
        }
    }
}

@Composable
private fun UserMessage(messageWithParts: MessageWithParts) {
    val theme = LocalOpenCodeTheme.current
    // Filter out synthetic text parts (system prompts, AGENTS.md content, etc.)
    val textParts = messageWithParts.parts
        .filterIsInstance<Part.Text>()
        .filter { !it.synthetic && !it.ignored }
    val text = textParts.joinToString("\n") { it.text }
    
    // Don't render anything if there's no visible text
    if (text.isBlank()) return

    // TUI style: Distinct background with accent left border for user messages
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
    ) {
        // Accent left border (thicker for user messages)
        Box(
            modifier = Modifier
                .width(Spacing.xs)
                .fillMaxHeight()
                .background(theme.primary)
        )
        
        // Content with distinct background - use primary tint for better contrast
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.primary.copy(alpha = 0.12f))
                .padding(horizontal = Spacing.mdLg, vertical = Spacing.md)
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
    onOpenSubSession: ((String) -> Unit)? = null,
    defaultToolWidgetState: ToolWidgetState = ToolWidgetState.COMPACT
) {
    // Build ordered groups: consecutive tools get batched, non-tools rendered individually
    // Invisible parts (StepStart, StepFinish, Snapshot, etc.) don't break tool groups
    val partGroups = buildList {
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
    
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.dp)  // 1.dp = minimal spacing, no token for this
    ) {
        // Render part groups in order
        partGroups.forEach { group ->
            when (group) {
                is PartGroupItem.Tools -> {
                    // Use the grouped tool summary with progressive disclosure
                    ToolGroupWidget(
                        tools = group.tools,
                        defaultState = defaultToolWidgetState,
                        onToolApprove = onToolApprove,
                        onToolDeny = onToolDeny,
                        onOpenSubSession = onOpenSubSession
                    )
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
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.Top
    ) {
        StreamingMarkdown(
            text = part.text,
            isStreaming = part.isStreaming,
            modifier = Modifier.weight(1f)
        )
        if (part.isStreaming) {
            TuiLoadingIndicator()
        }
    }
}

@Composable
private fun ReasoningPart(part: Part.Reasoning) {
    val theme = LocalOpenCodeTheme.current
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
        color = theme.warning.copy(alpha = 0.1f),
        shape = RectangleShape
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isThinking) {
                    TuiLoadingIndicator()
                } else {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = stringResource(R.string.models_reasoning),
                        modifier = Modifier.size(Sizing.iconXs),
                        tint = theme.warning
                    )
                }
                
                Text(
                    text = if (isThinking) "Thinking..." else "Reasoning",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.warning,
                    modifier = Modifier.weight(1f)
                )
                
                thinkingDuration?.let { duration ->
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted
                    )
                }
                
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(Sizing.iconXs),
                    tint = theme.textMuted
                )
            }
            
            if (expanded && part.text.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.xs),
                    color = theme.border
                )
                TertiaryStreamingMarkdown(
                    text = part.text,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun FilePart(part: Part.File) {
    val theme = LocalOpenCodeTheme.current
    Surface(
        color = theme.backgroundElement,
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AttachFile, 
                contentDescription = stringResource(R.string.cd_attach_file),
                modifier = Modifier.size(Sizing.iconXs),
                tint = theme.textMuted
            )
            Column {
                Text(
                    text = part.filename ?: "File",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.text
                )
                Text(
                    text = part.mime,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted
                )
            }
        }
    }
}

@Composable
private fun CompactPatchPart(part: Part.Patch) {
    val theme = LocalOpenCodeTheme.current
    var expanded by remember { mutableStateOf(false) }
    
    Surface(
        onClick = { expanded = !expanded },
        color = theme.backgroundElement,
        shape = RectangleShape
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Description, 
                    contentDescription = stringResource(R.string.cd_diff_icon),
                    modifier = Modifier.size(Sizing.iconXs),
                    tint = theme.accent
                )
                Text(
                    text = "Patch: ${part.files.size} file(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.text,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(Sizing.iconXs),
                    tint = theme.textMuted
                )
            }
            
            if (expanded) {
                part.files.forEach { file ->
                    Text(
                        text = "  $file",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted
                    )
                }
            } else if (part.files.isNotEmpty()) {
                val firstFile = part.files.firstOrNull() ?: return@Column
                Text(
                    text = "  $firstFile" + if (part.files.size > 1) " ..." else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted
                )
            }
        }
    }
}

@Composable
private fun TokenUsageInfo(tokens: TokenUsage, cost: Double) {
    val theme = LocalOpenCodeTheme.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = "${tokens.input}/${tokens.output}",
            style = MaterialTheme.typography.labelSmall,
            color = theme.textMuted
        )
        if (cost > 0) {
            Text(
                text = "$${String.format(java.util.Locale.US, "%.4f", cost)}",
                style = MaterialTheme.typography.labelSmall,
                color = theme.textMuted
            )
        }
    }
}
