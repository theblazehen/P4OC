package dev.blazelight.p4oc.ui.components.toolwidgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.ToolState
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.TuiCodeFontSize
import dev.blazelight.p4oc.ui.components.TuiLoadingIndicator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Bash widget expanded view
 * Shows: command + stdout/stderr preview (scrollable, max ~100dp)
 */
@Composable
fun BashWidgetExpanded(
    tool: Part.Tool,
    onClick: (() -> Unit)?,
    onToolApprove: (String) -> Unit,
    onToolDeny: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    val state = tool.state
    val (_, stateColor) = getStateIconColor(state, theme)
    val terminalBg = Color(0xFF0D1117)  // near-black terminal background
    val terminalFg = Color(0xFFE6EDF3)  // terminal text color
    val promptColor = stateColor
    val cardShape = RoundedCornerShape(10.dp)

    val command = extractJsonParam(state.input, "command") ?: "bash"
    val output = when (state) {
        is ToolState.Completed -> state.output.take(3000).trimEnd()
        is ToolState.Running -> state.title?.trimEnd() ?: ""
        is ToolState.Error -> state.error.trimEnd()
        else -> null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(terminalBg)
            .border(1.dp, promptColor.copy(alpha = 0.25f), cardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick, role = Role.Button) else Modifier)
    ) {
        // Tab bar
        Row(
            modifier = Modifier.fillMaxWidth().background(terminalBg)
                .padding(start = 10.dp, end = 10.dp, top = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                    .background(promptColor.copy(alpha = 0.14f))
                    .border(1.dp, promptColor.copy(alpha = 0.22f), RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(">_  bash", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp), color = promptColor, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            if (state is ToolState.Running) TuiLoadingIndicator()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(promptColor.copy(alpha = 0.18f)))
        // Prompt line
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("~", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = Color(0xFF58A6FF), fontWeight = FontWeight.Medium)
            Text("❯", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = promptColor, fontWeight = FontWeight.Bold)
            Text(command, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = terminalFg, modifier = Modifier.weight(1f))
        }

        // Output pane
        if (!output.isNullOrBlank()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = output,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    ),
                    color = if (state is ToolState.Error) theme.error else terminalFg.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                )
            }
        }

        // Pending approval
        if (state is ToolState.Pending) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.secondary.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                PendingApprovalButtons(
                    onApprove = { onToolApprove(tool.callID) },
                    onDeny = { onToolDeny(tool.callID) }
                )
            }
        }
    }
}

/**
 * Read widget expanded view
 * Shows: file path header + syntax-highlighted code preview
 */
@Composable
fun ReadWidgetExpanded(
    tool: Part.Tool,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    val state = tool.state
    val (icon, color) = getStateIconColor(state, theme)
    
    // Extract file path from input
    val filePath = extractJsonParam(state.input, "filePath")
        ?: extractJsonParam(state.input, "path")
        ?: extractJsonParam(state.input, "relative_path")
        ?: "file"
    val fileName = filePath.substringAfterLast("/")
    
    // Extract content from output
    val content = when (state) {
        is ToolState.Completed -> state.output.take(2000)
        is ToolState.Running -> state.title ?: ""
        is ToolState.Error -> state.error
        else -> null
    }
    
    val cardShape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .border(1.dp, color.copy(alpha = 0.2f), cardShape)
            .background(theme.backgroundPanel.copy(alpha = 0.5f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick, role = Role.Button) else Modifier)
    ) {
        // Header
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Text(text = icon, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = color) }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.read_file, fileName),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontSize = TuiCodeFontSize.lg),
                    color = theme.text, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = filePath,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = TuiCodeFontSize.sm),
                    color = theme.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (state is ToolState.Running) TuiLoadingIndicator()
        }
        if (!content.isNullOrBlank()) {
            HorizontalDivider(color = theme.border.copy(alpha = 0.3f))
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp)
                    .background(theme.backgroundElement)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = TuiCodeFontSize.sm, lineHeight = TuiCodeFontSize.xxl),
                    color = if (state is ToolState.Error) theme.error else theme.text,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())
                )
            }
        }
    }
}

/**
 * Edit widget expanded view
 * Shows: file path header + diff summary/preview
 */
@Composable
fun EditWidgetExpanded(
    tool: Part.Tool,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    val state = tool.state
    val (icon, color) = getStateIconColor(state, theme)
    
    // Extract file path from input
    val filePath = extractJsonParam(state.input, "filePath")
        ?: extractJsonParam(state.input, "path")
        ?: extractJsonParam(state.input, "relative_path")
        ?: "file"
    val fileName = filePath.substringAfterLast("/")
    
    // Extract diff info
    val oldString = extractJsonParam(state.input, "oldString")
    val newString = extractJsonParam(state.input, "newString")
    val codeEdit = extractJsonParam(state.input, "code_edit")
    
    val cardShape = RoundedCornerShape(10.dp)
    val previewContent = when {
        codeEdit != null -> codeEdit.take(500)
        oldString != null && newString != null -> "- ${oldString.take(100)}\n+ ${newString.take(100)}"
        else -> null
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .border(1.dp, color.copy(alpha = 0.2f), cardShape)
            .background(theme.backgroundPanel.copy(alpha = 0.5f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick, role = Role.Button) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Text(text = icon, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = color) }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.edit_file, fileName),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontSize = TuiCodeFontSize.lg),
                    color = theme.text, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = filePath,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = TuiCodeFontSize.sm),
                    color = theme.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            getDiffStatsFromTool(tool)?.let { (added, removed) ->
                Text("+$added", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium), color = theme.success)
                Text("-$removed", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium), color = theme.error)
            }
            if (state is ToolState.Running) TuiLoadingIndicator()
        }
        if (previewContent != null) {
            HorizontalDivider(color = theme.border.copy(alpha = 0.3f))
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(max = 100.dp)
                    .background(theme.backgroundElement)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = previewContent,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = TuiCodeFontSize.sm, lineHeight = TuiCodeFontSize.xxl),
                    color = theme.textMuted,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

/**
 * Default widget expanded view (fallback for unknown tools)
 * Shows: tool name + input/output preview
 */
@Composable
fun DefaultWidgetExpanded(
    tool: Part.Tool,
    onClick: (() -> Unit)?,
    onToolApprove: (String) -> Unit,
    onToolDeny: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    val state = tool.state
    val (icon, color) = getStateIconColor(state, theme)
    
    // Extract any output
    val output = when (state) {
        is ToolState.Completed -> state.output.take(1000)
        is ToolState.Running -> state.title ?: ""
        is ToolState.Error -> state.error
        else -> null
    }
    
    val cardShape = RoundedCornerShape(10.dp)
    val inputPreview = state.input.entries.take(2).joinToString("  ") { (k, v) -> "$k=${v.toString().take(28)}" }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .border(1.dp, color.copy(alpha = 0.2f), cardShape)
            .background(theme.backgroundPanel.copy(alpha = 0.5f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick, role = Role.Button) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Text(text = icon, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = color) }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tool.toolName,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontSize = TuiCodeFontSize.lg),
                    color = theme.text
                )
                if (inputPreview.isNotEmpty()) {
                    Text(
                        text = inputPreview,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = TuiCodeFontSize.sm),
                        color = theme.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (state is ToolState.Running) TuiLoadingIndicator()
        }
        if (!output.isNullOrBlank()) {
            HorizontalDivider(color = theme.border.copy(alpha = 0.3f))
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(max = 80.dp)
                    .background(theme.backgroundElement)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = output,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = TuiCodeFontSize.sm, lineHeight = TuiCodeFontSize.xxl),
                    color = if (state is ToolState.Error) theme.error else theme.text,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                )
            }
        }
        if (state is ToolState.Pending) {
            Box(modifier = Modifier.fillMaxWidth().background(theme.secondary.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                PendingApprovalButtons(
                    onApprove = { onToolApprove(tool.callID) },
                    onDeny = { onToolDeny(tool.callID) }
                )
            }
        }
    }
}

/**
 * Task widget expanded view
 * Shows: description + sub-agent type + "Open in Tab" button
 */
@Composable
fun TaskWidgetExpanded(
    tool: Part.Tool,
    onClick: (() -> Unit)?,
    onToolApprove: (String) -> Unit,
    onToolDeny: (String) -> Unit,
    onOpenSubSession: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    val state = tool.state
    val (icon, color) = getStateIconColor(state, theme)
    
    // Extract task info from input
    val description = extractJsonParam(state.input, "description") ?: "Sub-agent task"
    val subagentType = extractJsonParam(state.input, "subagent_type") ?: "general"
    val sessionId = extractSubSessionId(tool, state)
    
    // Extract output/result
    val output = when (state) {
        is ToolState.Completed -> state.output.take(500)
        is ToolState.Running -> state.title ?: "Running..."
        is ToolState.Error -> state.error
        else -> null
    }
    
    val cardShape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .border(1.dp, color.copy(alpha = 0.2f), cardShape)
            .background(theme.backgroundPanel.copy(alpha = 0.5f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick, role = Role.Button) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Text(text = icon, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = color) }
            Text(
                text = "Task",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontSize = TuiCodeFontSize.lg),
                color = theme.text
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(theme.secondary.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = subagentType, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = theme.secondary)
            }
            Spacer(Modifier.weight(1f))
            if (state is ToolState.Running) TuiLoadingIndicator()
        }
        HorizontalDivider(color = theme.border.copy(alpha = 0.3f))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = TuiCodeFontSize.md),
            color = theme.text, maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        if (!output.isNullOrBlank() && state !is ToolState.Running) {
            HorizontalDivider(color = theme.border.copy(alpha = 0.3f))
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(max = 60.dp)
                    .background(theme.backgroundElement)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = output,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = TuiCodeFontSize.sm, lineHeight = TuiCodeFontSize.xxl),
                    color = if (state is ToolState.Error) theme.error else theme.textMuted,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                )
            }
        }
        if (sessionId != null && onOpenSubSession != null) {
            HorizontalDivider(color = theme.border.copy(alpha = 0.3f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                    .background(theme.accent.copy(alpha = 0.08f))
                    .clickable(role = Role.Button) { onOpenSubSession(sessionId) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.cd_open_sub_agent), modifier = Modifier.size(12.dp), tint = theme.accent)
                Text(stringResource(R.string.open_sub_agent), style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = theme.accent)
            }
        }
        if (state is ToolState.Pending) {
            Box(modifier = Modifier.fillMaxWidth().background(theme.secondary.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                PendingApprovalButtons(onApprove = { onToolApprove(tool.callID) }, onDeny = { onToolDeny(tool.callID) })
            }
        }
    }
}

// ============== Shared Components ==============

@Composable
private fun PendingApprovalButtons(
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onDeny,
            modifier = Modifier.weight(1f).height(Sizing.buttonHeightSm),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = Spacing.md)
        ) {
            Text(stringResource(R.string.deny), style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
        }
        Button(
            onClick = onApprove,
            modifier = Modifier.weight(1f).height(Sizing.buttonHeightSm),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = Spacing.md)
        ) {
            Text(stringResource(R.string.allow), style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
        }
    }
}

// ============== Helper Functions ==============

@Composable
private fun getStateIconColor(
    state: ToolState, 
    theme: dev.blazelight.p4oc.ui.theme.opencode.OpenCodeTheme
): Pair<String, androidx.compose.ui.graphics.Color> {
    return when (state) {
        is ToolState.Running -> "◐" to theme.warning
        is ToolState.Pending -> "○" to theme.secondary
        is ToolState.Error -> "✗" to theme.error
        is ToolState.Completed -> "✓" to theme.success
    }
}

private fun extractJsonParam(input: JsonObject, paramName: String): String? {
    return input[paramName]?.jsonPrimitive?.content
}

/**
 * Extract the sub-agent session ID from tool/state metadata.
 * The server places the sub-session ID in metadata, not in the tool input params.
 * Try multiple possible key names for robustness.
 */
private fun extractSubSessionId(tool: Part.Tool, state: ToolState): String? {
    // Check state-level metadata first (most specific - set during Running/Completed/Error)
    val stateMetadata = when (state) {
        is ToolState.Completed -> state.metadata
        is ToolState.Running -> state.metadata
        is ToolState.Error -> state.metadata
        else -> null
    }
    stateMetadata?.let { meta ->
        SESSION_ID_KEYS.forEach { key ->
            meta[key]?.jsonPrimitive?.content?.let { return it }
        }
    }
    // Fallback to part-level metadata
    tool.metadata?.let { meta ->
        SESSION_ID_KEYS.forEach { key ->
            meta[key]?.jsonPrimitive?.content?.let { return it }
        }
    }
    // Last resort: check input (unlikely to have it, but backwards compat)
    return extractJsonParam(state.input, "session_id")
}

private val SESSION_ID_KEYS = listOf("sessionID", "sessionId", "session_id", "subSessionId")

private fun getDiffStatsFromTool(tool: Part.Tool): Pair<Int, Int>? {
    val metadata = when (val state = tool.state) {
        is ToolState.Completed -> state.metadata
        is ToolState.Running -> state.metadata
        is ToolState.Error -> state.metadata
        else -> null
    } ?: return null
    
    val added = metadata["linesAdded"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
    val removed = metadata["linesRemoved"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
    
    return added to removed
}
