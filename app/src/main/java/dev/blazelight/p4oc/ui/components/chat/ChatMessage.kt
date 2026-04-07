package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    onToolAlways: (String) -> Unit,
    onOpenSubSession: ((String) -> Unit)? = null,
    defaultToolWidgetState: ToolWidgetState = ToolWidgetState.COMPACT,
    pendingPermissionsByCallId: Map<String, Permission> = emptyMap(),
    onRevert: ((String) -> Unit)? = null,
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
                onToolAlways = onToolAlways,
                onOpenSubSession = onOpenSubSession,
                defaultToolWidgetState = defaultToolWidgetState,
                pendingPermissionsByCallId = pendingPermissionsByCallId,
                onRevert = onRevert
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun UserMessage(messageWithParts: MessageWithParts) {
    val theme = LocalOpenCodeTheme.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    // Filter out synthetic text parts (system prompts, AGENTS.md content, etc.)
    val textParts = messageWithParts.parts
        .filterIsInstance<Part.Text>()
        .filter { !it.synthetic && !it.ignored }
    val text = textParts.joinToString("\n") { it.text }
    
    // Don't render anything if there's no visible text
    if (text.isBlank()) return

    // User message bubble — aligned right, hugs text width, max 80% screen
    val bubbleShape = RoundedCornerShape(topStart = 14.dp, topEnd = 4.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.End
    ) {
        // Spacer pushes bubble right; bubble only takes as much space as text needs
        Spacer(Modifier.weight(0.18f))
        Box(
            modifier = Modifier
                .weight(0.82f, fill = false)
                .clip(bubbleShape)
                .background(theme.primary.copy(alpha = 0.16f))
                .border(1.dp, theme.primary.copy(alpha = 0.28f), bubbleShape)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        clipboardManager.setText(AnnotatedString(text))
                    },
                    onLongClickLabel = "Copy message"
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            StreamingMarkdown(
                text = text,
                modifier = Modifier
            )
        }
    }
}

@Composable
private fun AssistantMessage(
    messageWithParts: MessageWithParts,
    onToolApprove: (String) -> Unit,
    onToolDeny: (String) -> Unit,
    onToolAlways: (String) -> Unit,
    onOpenSubSession: ((String) -> Unit)? = null,
    defaultToolWidgetState: ToolWidgetState = ToolWidgetState.COMPACT,
    pendingPermissionsByCallId: Map<String, Permission> = emptyMap(),
    onRevert: ((String) -> Unit)? = null
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
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
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
                    
                    // Render inline permission prompts for tools with pending permissions
                    group.tools.forEach { tool ->
                        tool.callID?.let { callId ->
                            pendingPermissionsByCallId[callId]?.let { permission ->
                                InlinePermissionPrompt(
                                    permission = permission,
                                    onAllow = { onToolApprove(permission.id) },
                                    onAlways = { onToolAlways(permission.id) },
                                    onReject = { onToolDeny(permission.id) }
                                )
                            }
                        }
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

        // Revert action for messages with file-changing tools
        val hasCompletedTools = messageWithParts.parts.any { it is Part.Tool && it.state is ToolState.Completed }
        if (hasCompletedTools && onRevert != null) {
            val messageId = (messageWithParts.message as? Message.Assistant)?.id
            if (messageId != null) {
                val theme = LocalOpenCodeTheme.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(theme.warning.copy(alpha = 0.1f))
                            .border(1.dp, theme.warning.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                            .clickable(role = Role.Button) { onRevert(messageId) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "↺ ${stringResource(R.string.revert_changes)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = theme.warning
                        )
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
@OptIn(ExperimentalFoundationApi::class)
private fun TextPart(part: Part.Text) {
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        clipboardManager.setText(AnnotatedString(part.text))
                    },
                    onLongClickLabel = "Copy text"
                )
        ) {
            StreamingMarkdown(
                text = part.text,
                isStreaming = part.isStreaming,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (part.isStreaming) {
            TuiLoadingIndicator()
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ReasoningPart(part: Part.Reasoning) {
    val theme = LocalOpenCodeTheme.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
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

    // Collapsed pill — tiny, wraps to content
    val collapsedShape = RoundedCornerShape(20.dp)
    val expandedShape = RoundedCornerShape(10.dp)
    val cardShape = if (expanded) expandedShape else collapsedShape

    Box(
        modifier = Modifier
            .then(
                if (expanded) Modifier.fillMaxWidth()
                else Modifier.wrapContentWidth()
            )
            .clip(cardShape)
            .background(theme.warning.copy(alpha = if (expanded) 0.07f else 0.1f))
            .border(1.dp, theme.warning.copy(alpha = if (expanded) 0.2f else 0.35f), cardShape)
            .clickable(role = Role.Button) { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        if (!expanded) {
            // Pill mode: compact single line
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isThinking) {
                    TuiLoadingIndicator()
                } else {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = theme.warning
                    )
                }
                Text(
                    text = if (isThinking) "Thinking..." else "Reasoning${thinkingDuration?.let { " · $it" } ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = theme.warning
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    modifier = Modifier.size(12.dp),
                    tint = theme.warning.copy(alpha = 0.7f)
                )
            }
        } else {
            // Expanded mode: full reasoning text
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = theme.warning
                    )
                    Text(
                        text = "Reasoning${thinkingDuration?.let { " · $it" } ?: ""}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = theme.warning,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.ExpandLess,
                        contentDescription = "Collapse",
                        modifier = Modifier.size(12.dp),
                        tint = theme.warning.copy(alpha = 0.7f)
                    )
                }
                if (part.text.isNotEmpty()) {
                    HorizontalDivider(color = theme.warning.copy(alpha = 0.2f))
                    Box(
                        modifier = Modifier.combinedClickable(
                            onClick = { expanded = !expanded },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                clipboardManager.setText(AnnotatedString(part.text))
                            },
                            onLongClickLabel = "Copy reasoning",
                            role = Role.Button
                        )
                    ) {
                        TertiaryStreamingMarkdown(
                            text = part.text,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilePart(part: Part.File) {
    val theme = LocalOpenCodeTheme.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(theme.backgroundElement)
            .border(1.dp, theme.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(theme.accent.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AttachFile,
                contentDescription = stringResource(R.string.cd_attach_file),
                modifier = Modifier.size(14.dp),
                tint = theme.accent
            )
        }
        Column {
            Text(
                text = part.filename ?: "File",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = theme.text
            )
            Text(
                text = part.mime,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = theme.textMuted
            )
        }
    }
}

@Composable
private fun CompactPatchPart(part: Part.Patch) {
    val theme = LocalOpenCodeTheme.current
    var expanded by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(theme.backgroundElement)
            .border(1.dp, theme.accent.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .clickable(role = Role.Button) { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(theme.accent.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "±", fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall, color = theme.accent)
                }
                Text(
                    text = "${part.files.size} file(s) modified",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = theme.text,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(14.dp),
                    tint = theme.textMuted
                )
            }
            if (expanded) {
                part.files.forEach { file ->
                    Text(
                        text = "  $file",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = theme.textMuted
                    )
                }
            } else if (part.files.isNotEmpty()) {
                val firstFile = part.files.firstOrNull() ?: return@Column
                Text(
                    text = "  $firstFile" + if (part.files.size > 1) " +${part.files.size - 1} more" else "",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
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
