package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blazelight.p4oc.data.remote.dto.AgentDto
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing

@Suppress("LongMethod", "LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSwitcherSheet(
    agents: List<AgentDto>,
    selectedAgent: String?,
    onAgentSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = theme.background,
        shape = RectangleShape,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // TUI Header
            Surface(
                color = theme.backgroundElement,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "[ agent ]",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.titleSmall,
                            color = theme.text
                        )
                        Text(
                            text = "${agents.size}",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.textMuted
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(Sizing.iconButtonSm)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = theme.textMuted,
                            modifier = Modifier.size(Sizing.iconSm)
                        )
                    }
                }
            }

            HorizontalDivider(color = theme.border, thickness = Sizing.dividerThickness)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                contentPadding = PaddingValues(vertical = Spacing.xs)
            ) {
                itemsIndexed(agents, key = { _, a -> a.name }) { index, agent ->
                    val isSelected = agent.name == selectedAgent
                    AgentRow(
                        agent = agent,
                        isSelected = isSelected,
                        index = index,
                        onSelect = {
                            onAgentSelected(agent.name)
                            onDismiss()
                        }
                    )
                    if (index < agents.lastIndex) {
                        HorizontalDivider(
                            color = theme.border.copy(alpha = 0.3f),
                            thickness = Sizing.dividerThickness,
                            modifier = Modifier.padding(horizontal = Spacing.md)
                        )
                    }
                }
            }

            // Footer legend
            Surface(
                color = theme.backgroundElement,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    listOf(
                        "▶" to "primary",
                        "◎" to "subagent",
                        "◈" to "all"
                    ).forEach { (icon, label) ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = icon,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.textMuted
                            )
                            Text(
                                text = label,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.textMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun AgentRow(
    agent: AgentDto,
    isSelected: Boolean,
    index: Int,
    onSelect: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    var expanded by remember { mutableStateOf(false) }

    val fallbackColor = SemanticColors.AgentSelector.forName(agent.name)
    val agentColor = remember(agent.color, fallbackColor) {
        agent.color?.let { hex ->
            runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
        } ?: fallbackColor
    }

    val bgAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.08f else 0f,
        animationSpec = tween(180),
        label = "agent_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) agentColor.copy(alpha = 0.6f) else theme.border.copy(alpha = 0.2f),
        animationSpec = tween(180),
        label = "agent_border"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(agentColor.copy(alpha = bgAlpha))
            .border(
                width = if (isSelected) Sizing.strokeMd else 0.dp,
                color = if (isSelected) borderColor else Color.Transparent
            )
            .clickable(role = Role.Button) { onSelect() }
            .testTag("agent_row_${agent.name}")
            .semantics { contentDescription = "Agent ${agent.name}" }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode icon
            Text(
                text = when (agent.mode) {
                    "primary" -> "▶"
                    "subagent" -> "◎"
                    "all" -> "◈"
                    else -> "○"
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = agentColor.copy(alpha = 0.8f)
            )

            // Name
            Text(
                text = agent.name.lowercase(),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) agentColor else theme.text,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Selection indicator
            if (isSelected) {
                Text(
                    text = "◄",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = agentColor
                )
            }

            // Mode badge
            Text(
                text = "[${(agent.mode ?: "?").take(3)}]",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = agentColor.copy(alpha = 0.6f)
            )

            // Expand toggle — only if there's something to show
            val hasDetails = !agent.description.isNullOrBlank() || agent.model != null || agent.maxSteps != null
            if (hasDetails) {
                Text(
                    text = if (expanded) "▲" else "▼",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = theme.textMuted,
                    modifier = Modifier
                        .clickable(role = Role.Button) { expanded = !expanded }
                        .padding(Spacing.xxs)
                )
            }
        }

        // Expandable details
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeIn(tween(150)),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(tween(100))
        ) {
            AgentDetails(agent = agent, agentColor = agentColor)
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun AgentDetails(
    agent: AgentDto,
    agentColor: Color
) {
    val theme = LocalOpenCodeTheme.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs, start = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        HorizontalDivider(
            color = agentColor.copy(alpha = 0.2f),
            thickness = Sizing.dividerThickness,
            modifier = Modifier.padding(bottom = Spacing.xxs)
        )

        // Description
        agent.description?.takeIf { it.isNotBlank() }?.let { desc ->
            Text(
                text = desc,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textMuted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Model
        agent.model?.let { modelRef ->
            val modelText = listOfNotNull(modelRef.providerID, modelRef.modelID)
                .joinToString("/")
            if (modelText.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = "model:",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted
                    )
                    Text(
                        text = modelText,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // maxSteps
        agent.maxSteps?.let { steps ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = "max_steps:",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted
                )
                Text(
                    text = "$steps",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.warning
                )
            }
        }

        // Tools enabled (show top 4)
        agent.tools?.filterValues { it }?.keys?.take(4)?.let { enabledTools ->
            if (enabledTools.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "tools:",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted
                    )
                    enabledTools.forEach { tool ->
                        Text(
                            text = tool,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                            color = agentColor.copy(alpha = 0.7f)
                        )
                    }
                    val remainder = (agent.tools?.filterValues { it }?.size ?: 0) - enabledTools.size
                    if (remainder > 0) {
                        Text(
                            text = "+$remainder",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.textMuted
                        )
                    }
                }
            }
        }
    }
}
