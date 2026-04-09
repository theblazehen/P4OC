package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.sp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.domain.model.*
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.components.toolwidgets.ToolGroupWidget
import dev.blazelight.p4oc.ui.components.toolwidgets.ToolWidgetState
import dev.blazelight.p4oc.ui.components.TuiLoadingIndicator

// ── Cached shapes — file-level singletons, zero allocation during scroll ──────
private val pillShape       = RoundedCornerShape(20.dp)
private val blockShape      = RoundedCornerShape(2.dp)

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
    if (messageWithParts.message is Message.User) {
        UserMessage(messageWithParts, modifier)
    } else {
        AssistantMessage(
            messageWithParts = messageWithParts,
            onToolApprove = onToolApprove,
            onToolDeny = onToolDeny,
            onToolAlways = onToolAlways,
            onOpenSubSession = onOpenSubSession,
            defaultToolWidgetState = defaultToolWidgetState,
            pendingPermissionsByCallId = pendingPermissionsByCallId,
            onRevert = onRevert,
            modifier = modifier
        )
    }
}

// ── USER — terminal command line ───────────────────────────────────────────────
// Looks like typing a command in a shell: full-width row with prompt prefix,
// slight background tint on the whole line, no bubble or right-alignment.
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun UserMessage(messageWithParts: MessageWithParts, modifier: Modifier = Modifier) {
    val theme     = LocalOpenCodeTheme.current
    val clipboard = LocalClipboardManager.current
    val haptic    = LocalHapticFeedback.current

    val textParts = remember(messageWithParts.parts) {
        messageWithParts.parts
            .filterIsInstance<Part.Text>()
            .filter { !it.synthetic && !it.ignored }
    }
    val text = remember(textParts) { textParts.joinToString("\n") { it.text } }
    if (text.isBlank()) return

    // Slightly more visible tint — enough contrast to read as a command line block
    val rowBg  = remember(theme.primary) { theme.primary.copy(alpha = 0.09f) }
    val arrowColor = theme.primary   // uses live theme color, no remember needed (Color is stable)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(rowBg)
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    clipboard.setText(AnnotatedString(text))
                },
                onLongClickLabel = "Copy"
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // Prompt arrow in theme color — matches image (green/cyan/etc per active theme)
        Text(
            text = "→ ",
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = arrowColor,
        )
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = theme.text,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── ASSISTANT — terminal output with left accent bar ──────────────────────────
// Mimics shell output: no bubble, left vertical bar as visual separator from prompt.
@Composable
private fun AssistantMessage(
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
    val theme = LocalOpenCodeTheme.current
    val partGroups = remember(messageWithParts.parts) {
        buildList {
            var toolBatch = mutableListOf<Part.Tool>()
            for (part in messageWithParts.parts) {
                when (part) {
                    is Part.Tool -> toolBatch.add(part)
                    is Part.StepStart, is Part.StepFinish, is Part.Snapshot,
                    is Part.Agent, is Part.Retry, is Part.Compaction, is Part.Subtask -> Unit
                    else -> {
                        if (toolBatch.isNotEmpty()) {
                            add(PartGroupItem.Tools(toolBatch.toList()))
                            toolBatch = mutableListOf()
                        }
                        add(PartGroupItem.Other(part))
                    }
                }
            }
            if (toolBatch.isNotEmpty()) add(PartGroupItem.Tools(toolBatch.toList()))
        }
    }

    val barColor = remember(theme.accent) { theme.accent.copy(alpha = 0.85f) }

    // OPTIMIZED: Using drawBehind for accent bar to avoid IntrinsicSize.Min crash
    // Bar is now thinner (3dp) and closer to edge with less start padding
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp, start = 8.dp)  // Reduced from 16dp to 8dp
            .drawBehind {
                // Draw accent bar - 3dp wide (thinner), positioned at very left
                drawRect(
                    color = barColor,
                    topLeft = Offset.Zero,
                    size = Size(3.dp.toPx(), size.height)
                )
            }
    ) {
        // Content column - less indent since bar is thinner and closer
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 12.dp),  // 10dp = 3dp bar + 7dp gap
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            partGroups.forEach { group ->
                when (group) {
                    is PartGroupItem.Tools -> {
                        ToolGroupWidget(
                            tools = group.tools,
                            defaultState = defaultToolWidgetState,
                            onToolApprove = onToolApprove,
                            onToolDeny = onToolDeny,
                            onOpenSubSession = onOpenSubSession
                        )
                        group.tools.forEach { tool ->
                            tool.callID?.let { callId ->
                                pendingPermissionsByCallId[callId]?.let { perm ->
                                    InlinePermissionPrompt(
                                        permission = perm,
                                        onAllow  = { onToolApprove(perm.id) },
                                        onAlways = { onToolAlways(perm.id) },
                                        onReject = { onToolDeny(perm.id) }
                                    )
                                }
                            }
                        }
                    }
                    is PartGroupItem.Other -> when (val part = group.part) {
                        is Part.Text      -> TextPart(part)
                        is Part.Reasoning -> ReasoningPart(part)
                        is Part.File      -> FilePart(part)
                        is Part.Patch     -> CompactPatchPart(part)
                        else              -> Unit
                    }
                }
            }

            val hasCompletedTools = remember(messageWithParts.parts) {
                messageWithParts.parts.any { it is Part.Tool && it.state is ToolState.Completed }
            }
            if (hasCompletedTools && onRevert != null) {
                val msgId = (messageWithParts.message as? Message.Assistant)?.id
                if (msgId != null) {
                    Text(
                        text = "↺ ${stringResource(R.string.revert_changes)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = theme.warning,
                        modifier = Modifier
                            .clickable(role = Role.Button) { onRevert(msgId) }
                            .padding(vertical = 2.dp)
                    )
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
            // Keep indicator small and low-cost; could be a thin bar shimmer in future
            TuiLoadingIndicator()
        }
    }
}

// ── REASONING — terminal comment block ────────────────────────────────────────
// Collapsed: "# thinking... [3s] ▸"  — looks like a shell comment
// Expanded: indented block with left dim bar
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ReasoningPart(part: Part.Reasoning) {
    val theme     = LocalOpenCodeTheme.current
    val clipboard = LocalClipboardManager.current
    val haptic    = LocalHapticFeedback.current
    var expanded  by remember { mutableStateOf(false) }

    val isThinking = part.time?.end == null
    val duration = remember(part.time) {
        part.time?.let { t ->
            val ms = (t.end ?: t.start) - t.start
            when {
                ms < 1_000  -> " [${ms}ms]"
                ms < 60_000 -> " [${ms / 1_000}s]"
                else        -> " [${ms / 60_000}m${(ms % 60_000) / 1_000}s]"
            }
        } ?: ""
    }
    // warning is orange/amber in most themes — adapts to active theme
    val reasoningColor = remember(theme.warning) { theme.warning.copy(alpha = 0.80f) }
    val reasoningBarColor = remember(theme.warning) { theme.warning.copy(alpha = 0.50f) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header line — always a single monospace comment line
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isThinking) TuiLoadingIndicator()
            Text(
                text = buildString {
                    // ⟳ U+27F3 — clockwise open circle arrow, monospace-safe
                    append("\u27F3 ")
                    append(if (isThinking) "thinking..." else "reasoning$duration")
                    append(if (expanded) "  \u25BE" else "  \u25B8")
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = reasoningColor,
            )
        }

        // Expanded content — indented block, Box overlay avoids IntrinsicSize double-pass
        if (expanded && part.text.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .width(2.dp)
                        .matchParentSize()
                        .background(reasoningBarColor)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp)
                        .combinedClickable(
                            onClick = { expanded = !expanded },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                clipboard.setText(AnnotatedString(part.text))
                            },
                            onLongClickLabel = "Copy reasoning",
                            role = Role.Button
                        )
                ) {
                    TertiaryStreamingMarkdown(text = part.text, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// ── FILE — terminal ls-style line ─────────────────────────────────────────────
@Composable
private fun FilePart(part: Part.File) {
    val theme = LocalOpenCodeTheme.current
    Text(
        text = "  \uD83D\uDCC4 ${part.filename ?: "file"}  ${part.mime}",
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = theme.textMuted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    )
}

// ── PATCH — terminal diff summary ─────────────────────────────────────────────
// "± 3 files modified ▸" — tap to expand file list inline
@Composable
private fun CompactPatchPart(part: Part.Patch) {
    val theme    = LocalOpenCodeTheme.current
    var expanded by remember { mutableStateOf(false) }
    val mutedColor = remember(theme.accent) { theme.accent.copy(alpha = 0.7f) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "± ${part.files.size} file${if (part.files.size != 1) "s" else ""} modified${if (expanded) "  ▾" else "  ▸"}",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = mutedColor,
            modifier = Modifier
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(vertical = 2.dp)
        )
        if (expanded) {
            part.files.forEach { file ->
                Text(
                    text = "  ~ $file",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = theme.textMuted,
                    modifier = Modifier.padding(vertical = 1.dp)
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
