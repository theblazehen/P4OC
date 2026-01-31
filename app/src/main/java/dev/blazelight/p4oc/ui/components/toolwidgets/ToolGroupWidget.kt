package dev.blazelight.p4oc.ui.components.toolwidgets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.ToolState
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing

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
 * Tool group widget with progressive disclosure.
 * Shows HUD-style summary that can expand to show individual widgets.
 * 
 * Oneline: ✓ Read ×3 | ✓ Edit ×2 | ⟳ Bash
 * Compact: HUD + compact tool rows
 * Expanded: HUD + full tool widgets
 */
@Composable
fun ToolGroupWidget(
    tools: List<Part.Tool>,
    defaultState: ToolWidgetState,
    onToolApprove: (String) -> Unit,
    onToolDeny: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    
    // HITL tools (pending state) always show expanded
    val hasPendingTools = tools.any { it.state is ToolState.Pending }
    val effectiveDefault = if (hasPendingTools) ToolWidgetState.EXPANDED else defaultState
    
    var currentState by remember(tools.firstOrNull()?.callID) { mutableStateOf(effectiveDefault) }
    
    // Update state if tools become pending (HITL)
    LaunchedEffect(hasPendingTools) {
        if (hasPendingTools && currentState == ToolWidgetState.ONELINE) {
            currentState = ToolWidgetState.COMPACT
        }
    }
    
    // Group tools by name and determine aggregate state
    val toolGroups = remember(tools) {
        tools.groupBy { it.toolName }
            .map { (name, toolList) ->
                val state = when {
                    toolList.any { it.state is ToolState.Running } -> AggregateToolState.RUNNING
                    toolList.any { it.state is ToolState.Pending } -> AggregateToolState.PENDING
                    toolList.any { it.state is ToolState.Error } -> AggregateToolState.ERROR
                    else -> AggregateToolState.COMPLETED
                }
                ToolGroup(name, toolList.size, state, toolList)
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
    
    // Build the one-liner HUD text: "✓ Read ×3 | ✓ Edit ×2 | ⟳ Bash"
    val hudText = remember(toolGroups, theme) {
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
        // HUD summary row - always visible, click to cycle state
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.backgroundPanel.copy(alpha = 0.5f))
                .clickable { currentState = currentState.next() }
                .padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = hudText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 18.sp
                ),
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                maxLines = 1
            )
            
            // Show expand/collapse icon only in Compact/Expanded states
            if (currentState != ToolWidgetState.ONELINE) {
                Icon(
                    imageVector = Icons.Default.ExpandLess,
                    contentDescription = stringResource(R.string.cd_expanded),
                    modifier = Modifier.size(Sizing.iconXs),
                    tint = theme.border
                )
            }
        }
        
        // Expanded details - show individual widgets based on state
        AnimatedVisibility(
            visible = currentState != ToolWidgetState.ONELINE,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                tools.forEach { tool ->
                    when (currentState) {
                        ToolWidgetState.COMPACT -> {
                            // Show compact row (like old CompactToolRow)
                            ToolCallCompact(
                                tool = tool,
                                onClick = null, // Don't allow individual cycling in group
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            // Show approval buttons if pending
                            if (tool.state is ToolState.Pending) {
                                PendingApprovalButtonsInline(
                                    onApprove = { onToolApprove(tool.callID) },
                                    onDeny = { onToolDeny(tool.callID) }
                                )
                            }
                        }
                        ToolWidgetState.EXPANDED -> {
                            // Show full expanded widget
                            ToolCallExpanded(
                                tool = tool,
                                onClick = null, // Don't allow individual cycling in group
                                onToolApprove = onToolApprove,
                                onToolDeny = onToolDeny,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        else -> {} // Oneline handled above
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingApprovalButtonsInline(
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onDeny,
            modifier = Modifier
                .weight(1f)
                .height(28.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            shape = RectangleShape
        ) {
            Text(stringResource(R.string.deny), style = MaterialTheme.typography.labelSmall)
        }
        Button(
            onClick = onApprove,
            modifier = Modifier
                .weight(1f)
                .height(28.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            shape = RectangleShape
        ) {
            Text(stringResource(R.string.allow), style = MaterialTheme.typography.labelSmall)
        }
    }
}
