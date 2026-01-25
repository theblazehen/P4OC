package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.ToolState
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme

/**
 * Aggregated tool state for display purposes
 */
private enum class AggregateToolState {
    RUNNING,    // At least one tool is running
    PENDING,    // At least one tool is pending (needs approval)
    ERROR,      // At least one tool errored
    COMPLETED   // All tools completed successfully
}

/**
 * Data class for grouped tool display
 */
private data class ToolGroup(
    val name: String,
    val count: Int,
    val state: AggregateToolState,
    val tools: List<Part.Tool>
)

/**
 * Collapsed tool summary - Claude HUD style one-liner
 * Format: ✓ Read ×3 | ✓ Edit ×2 | ⟳ Bash | ○ Glob
 * All on ONE LINE with pipe separators
 */
@Composable
fun CollapsedToolSummary(
    toolParts: List<Part.Tool>,
    onToolApprove: (String) -> Unit,
    onToolDeny: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    var isExpanded by remember { mutableStateOf(false) }
    
    // Check if any tools are pending - auto-expand if so
    val hasPendingTools = toolParts.any { it.state is ToolState.Pending }
    
    // Group tools by name and determine aggregate state
    val toolGroups = remember(toolParts) {
        toolParts.groupBy { it.toolName }
            .map { (name, tools) ->
                val state = when {
                    tools.any { it.state is ToolState.Running } -> AggregateToolState.RUNNING
                    tools.any { it.state is ToolState.Pending } -> AggregateToolState.PENDING
                    tools.any { it.state is ToolState.Error } -> AggregateToolState.ERROR
                    else -> AggregateToolState.COMPLETED
                }
                ToolGroup(name, tools.size, state, tools)
            }
            .sortedWith(compareBy(
                // Sort: running first, then pending, then error, then completed
                { when (it.state) {
                    AggregateToolState.RUNNING -> 0
                    AggregateToolState.PENDING -> 1
                    AggregateToolState.ERROR -> 2
                    AggregateToolState.COMPLETED -> 3
                }},
                { it.name }
            ))
    }
    
    // Build the one-liner text like claude-hud: "✓ Read ×3 | ✓ Edit ×2 | ⟳ Bash"
    val toolsLineText = remember(toolGroups, theme) {
        buildAnnotatedString {
            toolGroups.forEachIndexed { index, group ->
                if (index > 0) {
                    withStyle(SpanStyle(color = theme.border)) {
                        append(" | ")
                    }
                }
                
                val (icon, color) = when (group.state) {
                    AggregateToolState.RUNNING -> "◐" to theme.warning
                    AggregateToolState.PENDING -> "○" to theme.secondary
                    AggregateToolState.ERROR -> "✗" to theme.error
                    AggregateToolState.COMPLETED -> "✓" to theme.success
                }
                
                withStyle(SpanStyle(color = color)) {
                    append(icon)
                }
                append(" ")
                withStyle(SpanStyle(color = theme.text)) {
                    append(group.name)
                }
                if (group.count > 1) {
                    withStyle(SpanStyle(color = theme.textMuted)) {
                        append(" ×${group.count}")
                    }
                }
            }
        }
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Collapsed summary row - ONE LINE, 150% larger than before
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.backgroundPanel.copy(alpha = 0.5f))  // Subtle background
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = toolsLineText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,  // Was 10.sp, now ~140%
                    lineHeight = 18.sp  // Was 12.sp
                ),
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                maxLines = 1
            )
            
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.size(16.dp),  // Was 12.dp
                tint = theme.border
            )
        }
        
        // Expanded tool list
        AnimatedVisibility(
            visible = isExpanded || hasPendingTools,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                toolParts.forEach { tool ->
                    CompactToolRow(
                        tool = tool,
                        onApprove = { onToolApprove(tool.callID) },
                        onDeny = { onToolDeny(tool.callID) }
                    )
                }
            }
        }
    }
}

/**
 * Compact single tool row for expanded view
 */
@Composable
private fun CompactToolRow(
    tool: Part.Tool,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    val state = tool.state
    val description = getToolDescription(tool.toolName, state.input)
    val diffStats = when (state) {
        is ToolState.Completed -> parseDiffStats(state.metadata)
        is ToolState.Running -> parseDiffStats(state.metadata)
        is ToolState.Error -> parseDiffStats(state.metadata)
        else -> null
    }
    
    val (icon, color) = when (state) {
        is ToolState.Running -> "◐" to theme.warning
        is ToolState.Pending -> "○" to theme.secondary
        is ToolState.Error -> "✗" to theme.error
        is ToolState.Completed -> "✓" to theme.success
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = when (state) {
                    is ToolState.Pending -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    is ToolState.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                },
                shape = RectangleShape
            )
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Text(
                text = icon,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
            
            // Tool name
            Text(
                text = tool.toolName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(min = 60.dp)
            )
            
            // Description (file path, command, etc.)
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            
            // Diff stats for edit tools
            if (diffStats != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "+${diffStats.added}",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.success
                    )
                    Text(
                        text = "-${diffStats.removed}",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.error
                    )
                }
            }
        }
        
        // Pending approval buttons
        if (state is ToolState.Pending) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onDeny,
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    shape = RectangleShape
                ) {
                    Text("Deny", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = onApprove,
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    shape = RectangleShape
                ) {
                    Text("Allow", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
