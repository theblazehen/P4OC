package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.domain.model.*
import dev.blazelight.p4oc.ui.components.TuiLoadingIndicator
import dev.blazelight.p4oc.ui.components.toolwidgets.ToolGroupWidget
import dev.blazelight.p4oc.ui.components.toolwidgets.ToolWidgetState
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun MessageWithParts.hasVisibleUserText(): Boolean =
    message is Message.User && parts.any { part ->
        part is Part.Text && !part.synthetic && !part.ignored && part.text.isNotBlank()
    }

@Composable
@Suppress("LongParameterList", "FunctionNaming")
fun ChatMessage(
    messageWithParts: MessageWithParts,
    onToolApprove: (String) -> Unit,
    onToolDeny: (String) -> Unit,
    onToolAlways: (String) -> Unit,
    onOpenSubSession: ((String) -> Unit)? = null,
    onProviderAuthRequired: ((String) -> Unit)? = null,
    defaultToolWidgetState: ToolWidgetState = ToolWidgetState.COMPACT,
    pendingPermissionsByCallId: Map<String, Permission> = emptyMap(),
    onRevert: (() -> Unit)? = null,
    isQueued: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val message = messageWithParts.message
    val isUser = message is Message.User

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        if (isUser) {
            UserMessage(messageWithParts, onRevert = onRevert, isQueued = isQueued)
        } else {
            AssistantMessages(
                messagesWithParts = listOf(messageWithParts),
                onToolApprove = onToolApprove,
                onToolDeny = onToolDeny,
                onToolAlways = onToolAlways,
                onOpenSubSession = onOpenSubSession,
                onProviderAuthRequired = onProviderAuthRequired,
                defaultToolWidgetState = defaultToolWidgetState,
                pendingPermissionsByCallId = pendingPermissionsByCallId,
            )
        }
    }
}

@Composable
@Suppress("LongParameterList", "FunctionNaming")
fun AssistantMessages(
    messagesWithParts: List<MessageWithParts>,
    onToolApprove: (String) -> Unit,
    onToolDeny: (String) -> Unit,
    onToolAlways: (String) -> Unit,
    onOpenSubSession: ((String) -> Unit)? = null,
    onProviderAuthRequired: ((String) -> Unit)? = null,
    defaultToolWidgetState: ToolWidgetState = ToolWidgetState.COMPACT,
    pendingPermissionsByCallId: Map<String, Permission> = emptyMap(),
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.hairline, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.hairline)
    ) {
        messagesWithParts.forEach { messageWithParts ->
            AssistantMessageContent(
                messageWithParts = messageWithParts,
                onToolApprove = onToolApprove,
                onToolDeny = onToolDeny,
                onToolAlways = onToolAlways,
                onOpenSubSession = onOpenSubSession,
                onProviderAuthRequired = onProviderAuthRequired,
                defaultToolWidgetState = defaultToolWidgetState,
                pendingPermissionsByCallId = pendingPermissionsByCallId,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun UserMessage(
    messageWithParts: MessageWithParts,
    onRevert: (() -> Unit)? = null,
    isQueued: Boolean = false,
) {
    val theme = LocalOpenCodeTheme.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var revertActionWidthPx by remember { mutableIntStateOf(0) }
    if (!messageWithParts.hasVisibleUserText()) return

    // Filter out synthetic text parts (system prompts, AGENTS.md content, etc.)
    val textParts = messageWithParts.parts
        .filterIsInstance<Part.Text>()
        .filter { !it.synthetic && !it.ignored }
    val text = textParts.joinToString("\n") { it.text }

    // TUI style: flat panel surface with a "you" label — matches the design's user block.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.backgroundPanel)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        clipboardManager.setText(AnnotatedString(text))
                    },
                    onLongClickLabel = "Copy message"
                )
                .padding(horizontal = Spacing.mdLg, vertical = Spacing.md)
        ) {
            val revertEndInset = if (onRevert != null) {
                with(density) { revertActionWidthPx.toDp() } + Spacing.sm
            } else {
                0.dp
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = revertEndInset)
            ) {
                Text(
                    text = stringResource(R.string.chat_user_label),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = theme.textMuted,
                    modifier = Modifier.padding(bottom = Spacing.xxs)
                )
                StreamingMarkdown(text = text, modifier = Modifier.fillMaxWidth())

                if (isQueued) {
                    Text(
                        text = stringResource(R.string.chat_queued_prefix),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = theme.background,
                        modifier = Modifier
                            .padding(top = Spacing.xs)
                            .background(theme.primary, RectangleShape)
                            .padding(horizontal = Spacing.xs, vertical = Spacing.hairline)
                    )
                }
            }

            onRevert?.let { revert ->
                Text(
                    text = "\u21BA ${stringResource(R.string.revert_changes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .onSizeChanged { revertActionWidthPx = it.width }
                        .clickable(role = Role.Button) { revert() }
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList", "FunctionNaming")
private fun AssistantMessageContent(
    messageWithParts: MessageWithParts,
    onToolApprove: (String) -> Unit,
    onToolDeny: (String) -> Unit,
    onToolAlways: (String) -> Unit,
    onOpenSubSession: ((String) -> Unit)? = null,
    onProviderAuthRequired: ((String) -> Unit)? = null,
    defaultToolWidgetState: ToolWidgetState = ToolWidgetState.COMPACT,
    pendingPermissionsByCallId: Map<String, Permission> = emptyMap(),
) {
    val assistant = messageWithParts.message as? Message.Assistant
    // Build ordered groups: consecutive tools get batched, non-tools rendered individually
    // Invisible parts (StepStart, StepFinish, Snapshot, etc.) don't break tool groups
    val partGroups = buildPartGroups(messageWithParts.parts)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.hairline)
    ) {
        // Per-turn attribution header: `@build · claude-sonnet-4-5` (design 05).
        assistant?.let { assistantMessage ->
            val attribution = assistantAttribution(assistantMessage.agent, assistantMessage.modelID)
            if (partGroups.isNotEmpty() && attribution != null) {
                AssistantAttributionHeader(attribution)
            }
        }

        // Render part groups in order
        partGroups.forEach { group ->
            when (group) {
                is PartGroupItem.Tools -> {
                    // Use the grouped tool summary with progressive disclosure
                    ToolGroupWidget(
                        tools = group.tools,
                        defaultState = defaultToolWidgetState,
                        pendingPermissionIdsByCallId = pendingPermissionsByCallId.mapValues { it.value.id },
                        onToolApprove = onToolApprove,
                        onToolDeny = onToolDeny,
                        onToolAlways = onToolAlways,
                        onOpenSubSession = onOpenSubSession
                    )
                }
                is PartGroupItem.Other -> renderOtherPart(group.part)
            }
        }

        assistant?.error?.let { error ->
            AssistantError(error, onProviderAuthRequired)
        }

        if (assistant != null && (assistant.tokens.hasUsage() || assistant.cost > 0.0)) {
            TokenUsageInfo(tokens = assistant.tokens, cost = assistant.cost)
        }
    }
}

private fun buildPartGroups(parts: List<Part>): List<PartGroupItem> = buildList {
    var currentToolBatch = mutableListOf<Part.Tool>()

    for (part in parts) {
        when (part) {
            is Part.Tool -> currentToolBatch.add(part)
            // Invisible parts - don't break tool groups, just skip
            is Part.StepStart, is Part.StepFinish, is Part.Snapshot,
            is Part.Agent -> {
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

@Composable
private fun renderOtherPart(part: Part) {
    when (part) {
        is Part.Text -> TextPart(part)
        is Part.Reasoning -> ReasoningPart(part)
        is Part.File -> FilePart(part)
        is Part.Patch -> CompactPatchPart(part)
        is Part.Subtask -> activityMarker(stringResource(R.string.chat_delegated_to, part.agent, part.description))
        is Part.Retry -> activityMarker(stringResource(R.string.chat_retry_attempt, part.attempt))
        is Part.Compaction -> activityMarker(stringResource(R.string.chat_context_compacted))
        else -> Unit
    }
}

/**
 * Assistant turn attribution header — `@build · claude-sonnet-4-5` (design 05).
 * `@agent` takes the agent's accent color; the model id trails muted.
 */
internal data class AssistantAttribution(
    val agent: String?,
    val modelID: String?,
) {
    val showSeparator: Boolean get() = agent != null && modelID != null
}

internal fun assistantAttribution(agent: String, modelID: String): AssistantAttribution? {
    val visibleAgent = agent.trim().takeIf(String::isNotEmpty)
    val visibleModelID = modelID.trim().takeIf(String::isNotEmpty)
    return if (visibleAgent == null && visibleModelID == null) {
        null
    } else {
        AssistantAttribution(visibleAgent, visibleModelID)
    }
}

@Composable
@Suppress("FunctionNaming")
private fun AssistantAttributionHeader(attribution: AssistantAttribution) {
    val theme = LocalOpenCodeTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs, bottom = Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        attribution.agent?.let { agent ->
            Text(
                text = "@${agent.lowercase()}",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.SemiBold,
                color = SemanticColors.AgentSelector.forName(agent)
            )
        }
        if (attribution.showSeparator) {
            Text(
                text = "·",
                style = MaterialTheme.typography.labelMedium,
                color = theme.textMuted
            )
        }
        attribution.modelID?.let { modelID ->
            Text(
                text = modelID,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = theme.textMuted,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun activityMarker(label: String) {
    val theme = LocalOpenCodeTheme.current
    Text(
        text = label,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        style = MaterialTheme.typography.labelSmall,
        color = theme.textMuted,
    )
}

@Composable
@Suppress("FunctionNaming")
private fun AssistantError(error: MessageError, onProviderAuthRequired: ((String) -> Unit)? = null) {
    val theme = LocalOpenCodeTheme.current
    val isAuth = error.name == "ProviderAuthError"
    val isAborted = error.name == "MessageAbortedError"
    val message = assistantErrorMessage(error, isAuth, isAborted)
    val accent = if (isAborted) theme.warning else theme.error
    val header = assistantErrorHeader(error, isAuth, isAborted)

    // Left-border error card, matching design 20's provider-auth banner.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(theme.backgroundElement)
    ) {
        Box(
            modifier = Modifier
                .width(Sizing.strokeThick)
                .fillMaxHeight()
                .background(accent)
        )
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = header,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = accent
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = theme.text
            )
            if (isAuth && error.providerID != null && onProviderAuthRequired != null) {
                OutlinedButton(
                    onClick = { onProviderAuthRequired(error.providerID) },
                    shape = RectangleShape,
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.none),
                    border = BorderStroke(Sizing.strokeMd, theme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.primary)
                ) {
                    Text(
                        stringResource(R.string.provider_auth_action),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun assistantErrorMessage(error: MessageError, isAuth: Boolean, isAborted: Boolean): String = when {
    isAborted -> stringResource(R.string.chat_run_aborted)
    isAuth -> stringResource(R.string.chat_provider_auth_required)
    error.isRetryable -> stringResource(R.string.chat_run_retryable_error)
    else -> stringResource(R.string.chat_run_failed)
}

private fun assistantErrorHeader(error: MessageError, isAuth: Boolean, isAborted: Boolean): String {
    val title = when {
        isAuth -> "provider auth error"
        isAborted -> "run aborted"
        error.isRetryable -> "run error · retryable"
        else -> "run failed"
    }
    return buildString {
        append(title)
        error.statusCode?.let {
            append(" · ")
            append(it)
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
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
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ReasoningPart(part: Part.Reasoning) {
    val theme = LocalOpenCodeTheme.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var expanded by rememberSaveable(part.id) { mutableStateOf(false) }

    val isThinking = part.time?.end == null

    Surface(
        onClick = { expanded = !expanded },
        color = theme.warning.copy(alpha = 0.1f),
        shape = RectangleShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.size(Sizing.iconXs),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isThinking) {
                        TuiLoadingIndicator()
                    } else {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = stringResource(R.string.models_reasoning),
                            modifier = Modifier.fillMaxSize(),
                            tint = theme.warning
                        )
                    }
                }

                val detailTitle = reasoningDetailTitle(part)
                Text(
                    text = detailTitle?.let {
                        stringResource(R.string.reasoning_with_title, it)
                    } ?: stringResource(R.string.models_reasoning),
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.warning,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

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

internal fun reasoningDetailTitle(part: Part.Reasoning): String? {
    val metadataTitle = REASONING_TITLE_METADATA_KEYS
        .firstNotNullOfOrNull { key ->
            val value = part.metadata?.get(key) as? JsonPrimitive
            if (value != null && value.isString) value.contentOrNull else null
        }
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    val source = metadataTitle ?: part.text.lineSequence()
        .map(String::trim)
        .firstOrNull { it.isNotBlank() }
        ?.trimStart('#')
        ?.trim()
    return source
        ?.stripReasoningTitleMarkdown()
        ?.replace(REASONING_WHITESPACE_REGEX, " ")
        ?.take(REASONING_TITLE_MAX_CHARS)
        ?.takeIf { it.isNotBlank() && !it.equals("reasoning", ignoreCase = true) }
}

private fun String.stripReasoningTitleMarkdown(): String =
    replace("**", "")
        .replace("__", "")

private const val REASONING_TITLE_MAX_CHARS = 80
private val REASONING_TITLE_METADATA_KEYS = listOf("title", "summary", "subject", "heading")
private val REASONING_WHITESPACE_REGEX = Regex("\\s+")

@Composable
private fun FilePart(part: Part.File) {
    val theme = LocalOpenCodeTheme.current
    Surface(
        color = theme.backgroundElement,
        shape = RectangleShape,
        modifier = Modifier.fillMaxWidth()
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
        shape = RectangleShape,
        modifier = Modifier.fillMaxWidth()
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
                    text = stringResource(R.string.chat_patch_files, part.files.size),
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

private fun TokenUsage.hasUsage(): Boolean =
    (input or output or reasoning or cacheRead or cacheWrite) != 0

private const val MINIMUM_VISIBLE_COST = 0.0001

@Composable
@Suppress("FunctionNaming")
private fun TokenUsageInfo(tokens: TokenUsage, cost: Double, modifier: Modifier = Modifier) {
    val theme = LocalOpenCodeTheme.current
    val total = tokens.input.toLong() +
        tokens.output.toLong() +
        tokens.reasoning.toLong() +
        tokens.cacheRead.toLong() +
        tokens.cacheWrite.toLong()
    Row(
        modifier = modifier.testTag("assistant_token_usage"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = "$total total",
            style = MaterialTheme.typography.labelSmall,
            color = theme.textMuted
        )
        Text(
            text = "${tokens.input}/${tokens.output}",
            style = MaterialTheme.typography.labelSmall,
            color = theme.textMuted
        )
        if (cost > 0) {
            Text(
                text = if (cost < MINIMUM_VISIBLE_COST) {
                    "<\$0.0001"
                } else {
                    "$${String.format(java.util.Locale.US, "%.4f", cost)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = theme.textMuted
            )
        }
    }
}
