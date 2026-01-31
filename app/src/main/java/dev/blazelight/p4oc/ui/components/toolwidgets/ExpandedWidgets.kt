package dev.blazelight.p4oc.ui.components.toolwidgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.ToolState
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
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
    val (icon, color) = getStateIconColor(state, theme)
    
    // Extract command from input
    val command = extractJsonParam(state.input, "command") ?: "bash"
    
    // Extract output from result
    val output = when (state) {
        is ToolState.Completed -> state.output.take(2000)
        is ToolState.Running -> state.title ?: ""
        is ToolState.Error -> state.error
        else -> null
    }
    
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(theme.backgroundPanel.copy(alpha = 0.5f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Header: icon + command
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
            Text(
                text = command,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                ),
                color = theme.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (state is ToolState.Running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = theme.warning
                )
            }
        }
        
        // Output preview (scrollable, max height ~100dp)
        if (!output.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 100.dp)
                    .background(theme.backgroundElement)
                    .padding(6.dp)
            ) {
                Text(
                    text = output,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    ),
                    color = if (state is ToolState.Error) theme.error else theme.textMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                )
            }
        }
        
        // Pending approval buttons
        if (state is ToolState.Pending) {
            PendingApprovalButtons(
                onApprove = { onToolApprove(tool.callID) },
                onDeny = { onToolDeny(tool.callID) }
            )
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
    
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(theme.backgroundPanel.copy(alpha = 0.5f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Header: icon + file path
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
            Text(
                text = stringResource(R.string.read_file, fileName),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                ),
                color = theme.text
            )
            Spacer(Modifier.weight(1f))
            if (state is ToolState.Running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = theme.warning
                )
            }
        }
        
        // Full path (muted)
        Text(
            text = filePath,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            ),
            color = theme.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        // Content preview (scrollable, max height ~100dp)
        if (!content.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 100.dp)
                    .background(theme.backgroundElement)
                    .padding(6.dp)
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    ),
                    color = if (state is ToolState.Error) theme.error else theme.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
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
    
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(theme.backgroundPanel.copy(alpha = 0.5f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Header: icon + file name + diff stats
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
            Text(
                text = stringResource(R.string.edit_file, fileName),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                ),
                color = theme.text
            )
            Spacer(Modifier.weight(1f))
            
            // Show diff stats if available
            getDiffStatsFromTool(tool)?.let { (added, removed) ->
                Text("+$added", style = MaterialTheme.typography.labelSmall, color = theme.success)
                Text("-$removed", style = MaterialTheme.typography.labelSmall, color = theme.error)
            }
            
            if (state is ToolState.Running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = theme.warning
                )
            }
        }
        
        // Full path (muted)
        Text(
            text = filePath,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            ),
            color = theme.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        // Show edit preview (old → new or code_edit)
        val previewContent = when {
            codeEdit != null -> codeEdit.take(500)
            oldString != null && newString != null -> 
                "- ${oldString.take(100)}\n+ ${newString.take(100)}"
            else -> null
        }
        
        if (previewContent != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 80.dp)
                    .background(theme.backgroundElement)
                    .padding(6.dp)
            ) {
                Text(
                    text = previewContent,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    ),
                    color = theme.textMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
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
    
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(theme.backgroundPanel.copy(alpha = 0.5f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Header
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
            Text(
                text = tool.toolName,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                ),
                color = theme.text
            )
            Spacer(Modifier.weight(1f))
            if (state is ToolState.Running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = theme.warning
                )
            }
        }
        
        // Input preview (show first few params)
        val inputPreview = state.input.entries.take(2).joinToString(", ") { (k, v) ->
            "$k: ${v.toString().take(30)}"
        }
        if (inputPreview.isNotEmpty()) {
            Text(
                text = inputPreview,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                ),
                color = theme.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Output preview
        if (!output.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 80.dp)
                    .background(theme.backgroundElement)
                    .padding(6.dp)
            ) {
                Text(
                    text = output,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    ),
                    color = if (state is ToolState.Error) theme.error else theme.text,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        }
        
        // Pending approval buttons
        if (state is ToolState.Pending) {
            PendingApprovalButtons(
                onApprove = { onToolApprove(tool.callID) },
                onDeny = { onToolDeny(tool.callID) }
            )
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
            modifier = Modifier
                .weight(1f)
                .height(32.dp),
            shape = RectangleShape,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(stringResource(R.string.deny), style = MaterialTheme.typography.labelSmall)
        }
        Button(
            onClick = onApprove,
            modifier = Modifier
                .weight(1f)
                .height(32.dp),
            shape = RectangleShape,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(stringResource(R.string.allow), style = MaterialTheme.typography.labelSmall)
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
