package dev.blazelight.p4oc.ui.components.toolwidgets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.ToolState
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.TuiCodeFontSize
import dev.blazelight.p4oc.ui.components.TuiLoadingIndicator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Main wrapper component for tool call widgets.
 * Handles state cycling between Oneline, Compact, and Expanded views.
 * 
 * HITL tools (like question) always display expanded and don't cycle.
 */
@Composable
fun ToolCallWidget(
    tool: Part.Tool,
    defaultState: ToolWidgetState,
    onToolApprove: (String) -> Unit,
    onToolDeny: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    
    // HITL tools (pending state) always show expanded
    val isHitl = tool.state is ToolState.Pending
    val effectiveDefault = if (isHitl) ToolWidgetState.EXPANDED else defaultState
    
    var currentState by remember(tool.callID) { mutableStateOf(effectiveDefault) }
    
    // Update state if tool becomes pending (HITL)
    LaunchedEffect(isHitl) {
        if (isHitl) {
            currentState = ToolWidgetState.EXPANDED
        }
    }
    
    val canCycle = !isHitl // HITL tools don't cycle
    
    AnimatedContent(
        targetState = currentState,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "tool_widget_state"
    ) { state ->
        when (state) {
            ToolWidgetState.ONELINE -> ToolCallOneline(
                tool = tool,
                onClick = if (canCycle) {{ currentState = currentState.next() }} else null,
                modifier = Modifier.fillMaxWidth()
            )
            ToolWidgetState.COMPACT -> ToolCallCompact(
                tool = tool,
                onClick = if (canCycle) {{ currentState = currentState.next() }} else null,
                modifier = Modifier.fillMaxWidth()
            )
            ToolWidgetState.EXPANDED -> ToolCallExpanded(
                tool = tool,
                onClick = if (canCycle) {{ currentState = currentState.next() }} else null,
                onToolApprove = onToolApprove,
                onToolDeny = onToolDeny,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Oneline view: flat terminal log line
 * Format: ✓ bash  |  ◐ Read Theme.kt
 */
@Composable
fun ToolCallOneline(
    tool: Part.Tool,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    val (icon, color) = getToolStateIcon(tool.state, theme)
    val description = getToolCompactDescription(tool)

    Row(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick, role = Role.Button) else Modifier)
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$icon ",
            fontFamily = FontFamily.Monospace,
            fontSize = TuiCodeFontSize.lg,
            color = color
        )
        Text(
            text = description,
            fontFamily = FontFamily.Monospace,
            fontSize = TuiCodeFontSize.lg,
            color = theme.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (tool.state is ToolState.Running) {
            TuiLoadingIndicator()
        }
    }
}

/**
 * Compact view: terminal log line with left color bar
 * Matches AssistantMessage visual language
 */
@Composable
fun ToolCallCompact(
    tool: Part.Tool,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    val (icon, color) = getToolStateIcon(tool.state, theme)
    val description = getToolCompactDescription(tool)

    Box(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick, role = Role.Button) else Modifier)
            .padding(vertical = 2.dp)
    ) {
        // Left state color bar
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .width(2.dp)
                .matchParentSize()
                .background(color.copy(alpha = 0.55f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                fontFamily = FontFamily.Monospace,
                fontSize = TuiCodeFontSize.lg,
                color = color
            )
            Text(
                text = description,
                fontFamily = FontFamily.Monospace,
                fontSize = TuiCodeFontSize.xl,
                color = theme.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (tool.state is ToolState.Running) {
                TuiLoadingIndicator()
            }
            getDiffStats(tool)?.let { (added, removed) ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("+$added", fontFamily = FontFamily.Monospace,
                        fontSize = TuiCodeFontSize.lg, color = theme.success)
                    Text("-$removed", fontFamily = FontFamily.Monospace,
                        fontSize = TuiCodeFontSize.lg, color = theme.error)
                }
            }
        }
    }
}

/**
 * Expanded view: full details with tool-specific UI
 * Delegates to specialized widgets based on tool type
 */
@Composable
fun ToolCallExpanded(
    tool: Part.Tool,
    onClick: (() -> Unit)?,
    onToolApprove: (String) -> Unit,
    onToolDeny: (String) -> Unit,
    onOpenSubSession: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    when (tool.toolName.lowercase()) {
        "bash", "execute", "shell" -> BashWidgetExpanded(
            tool = tool,
            onClick = onClick,
            onToolApprove = onToolApprove,
            onToolDeny = onToolDeny,
            modifier = modifier
        )
        "read", "read_file", "serena_read_file" -> ReadWidgetExpanded(
            tool = tool,
            onClick = onClick,
            modifier = modifier
        )
        "edit", "write", "morph_edit_file", "serena_replace_content", "serena_create_text_file" -> EditWidgetExpanded(
            tool = tool,
            onClick = onClick,
            modifier = modifier
        )
        "task" -> TaskWidgetExpanded(
            tool = tool,
            onClick = onClick,
            onToolApprove = onToolApprove,
            onToolDeny = onToolDeny,
            onOpenSubSession = onOpenSubSession,
            modifier = modifier
        )
        else -> DefaultWidgetExpanded(
            tool = tool,
            onClick = onClick,
            onToolApprove = onToolApprove,
            onToolDeny = onToolDeny,
            modifier = modifier
        )
    }
}

// ============== Helper Functions ==============

@Composable
private fun getToolStateIcon(state: ToolState, theme: dev.blazelight.p4oc.ui.theme.opencode.OpenCodeTheme): Pair<String, androidx.compose.ui.graphics.Color> {
    return when (state) {
        is ToolState.Running -> "◐" to theme.warning
        is ToolState.Pending -> "○" to theme.secondary
        is ToolState.Error -> "✗" to theme.error
        is ToolState.Completed -> "✓" to theme.success
    }
}

/**
 * Get compact description for a tool based on its type and input
 */
private fun getToolCompactDescription(tool: Part.Tool): String {
    val input = tool.state.input
    val name = tool.toolName.lowercase()
    
    return when {
        name in listOf("bash", "execute", "shell") -> {
            extractParam(input, "command")?.take(60) ?: tool.toolName
        }
        name in listOf("read", "read_file", "serena_read_file") -> {
            val path = extractParam(input, "filePath") 
                ?: extractParam(input, "path")
                ?: extractParam(input, "relative_path")
            val fileName = path?.substringAfterLast("/") ?: "file"
            val lines = extractParam(input, "limit")?.toIntOrNull()
            if (lines != null) "Read $fileName ($lines lines)" else "Read $fileName"
        }
        name in listOf("edit", "write", "morph_edit_file", "serena_replace_content", "serena_create_text_file") -> {
            val path = extractParam(input, "filePath") 
                ?: extractParam(input, "path")
                ?: extractParam(input, "relative_path")
            val fileName = path?.substringAfterLast("/") ?: "file"
            "Modified $fileName"
        }
        name in listOf("glob", "find", "serena_find_file") -> {
            val pattern = extractParam(input, "pattern") ?: extractParam(input, "file_mask")
            pattern?.let { "Glob $it" } ?: tool.toolName
        }
        name in listOf("grep", "search", "serena_search_for_pattern") -> {
            val pattern = extractParam(input, "pattern") ?: extractParam(input, "substring_pattern")
            pattern?.take(40)?.let { "Search: $it" } ?: tool.toolName
        }
        else -> tool.toolName
    }
}

/**
 * Extract a parameter value from JsonObject
 */
private fun extractParam(input: JsonObject, paramName: String): String? {
    return try {
        input[paramName]?.jsonPrimitive?.content
    } catch (e: Exception) {
        null
    }
}

/**
 * Get diff stats (added, removed lines) for edit tools
 */
private fun getDiffStats(tool: Part.Tool): Pair<Int, Int>? {
    val metadata = when (val state = tool.state) {
        is ToolState.Completed -> state.metadata
        is ToolState.Running -> state.metadata
        is ToolState.Error -> state.metadata
        else -> null
    } ?: return null
    
    return try {
        val added = metadata["linesAdded"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val removed = metadata["linesRemoved"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        added to removed
    } catch (e: Exception) {
        null
    }
}
