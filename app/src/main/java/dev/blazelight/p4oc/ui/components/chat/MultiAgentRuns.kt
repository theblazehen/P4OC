package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing

data class AgentRun(
    val agentName: String,
    val status: AgentRunStatus,
    val startTime: Long,
    val endTime: Long? = null,
    val taskDescription: String? = null
)

enum class AgentRunStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

@Composable
fun MultiAgentRunsIndicator(
    agents: List<AgentRun>,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    if (agents.isEmpty()) return
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = theme.backgroundElement,
        shape = RectangleShape
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "◈",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.accent
                    )
                    Text(
                        text = stringResource(R.string.agent_runs),
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.text
                    )
                }
                
                val runningCount = agents.count { it.status == AgentRunStatus.RUNNING }
                if (runningCount > 0) {
                    Text(
                        text = "[$runningCount running]",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.accent
                    )
                }
            }
            
            agents.forEach { agent ->
                TuiAgentRunRow(agent = agent)
            }
        }
    }
}

@Composable
private fun TuiAgentRunRow(agent: AgentRun) {
    val theme = LocalOpenCodeTheme.current
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator
        Text(
            text = when (agent.status) {
                AgentRunStatus.PENDING -> "○"
                AgentRunStatus.RUNNING -> "▶"
                AgentRunStatus.COMPLETED -> "✓"
                AgentRunStatus.FAILED -> "✗"
            },
            style = MaterialTheme.typography.bodySmall,
            color = when (agent.status) {
                AgentRunStatus.PENDING -> theme.textMuted
                AgentRunStatus.RUNNING -> theme.accent
                AgentRunStatus.COMPLETED -> theme.success
                AgentRunStatus.FAILED -> theme.error
            }
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = agent.agentName,
                style = MaterialTheme.typography.bodySmall,
                color = theme.text
            )
            agent.taskDescription?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted,
                    maxLines = 1
                )
            }
        }
        
        Text(
            text = formatDuration(agent.startTime, agent.endTime),
            style = MaterialTheme.typography.labelSmall,
            color = theme.textMuted
        )
    }
}

@Composable
fun AgentSwitchIndicator(
    fromAgent: String?,
    toAgent: String,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = theme.info.copy(alpha = 0.1f),
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (fromAgent != null) {
                Text(
                    text = "@$fromAgent",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted
                )
                
                Text(
                    text = " → ",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted
                )
            } else {
                Text(
                    text = "▶ ",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.accent
                )
            }
            
            Text(
                text = "@$toAgent",
                style = MaterialTheme.typography.labelSmall,
                color = theme.accent,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SubtaskIndicator(
    agentName: String,
    description: String,
    status: AgentRunStatus,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = theme.warning.copy(alpha = 0.1f),
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Text(
                text = when (status) {
                    AgentRunStatus.PENDING -> "○"
                    AgentRunStatus.RUNNING -> "▶"
                    AgentRunStatus.COMPLETED -> "✓"
                    AgentRunStatus.FAILED -> "✗"
                },
                style = MaterialTheme.typography.bodySmall,
                color = when (status) {
                    AgentRunStatus.PENDING -> theme.textMuted
                    AgentRunStatus.RUNNING -> theme.accent
                    AgentRunStatus.COMPLETED -> theme.success
                    AgentRunStatus.FAILED -> theme.error
                }
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "[${stringResource(R.string.subtask)}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.warning
                    )
                    Text(
                        text = "@$agentName",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (status == AgentRunStatus.RUNNING) theme.accent else theme.textMuted
                    )
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.text
                )
            }
        }
    }
}

private fun formatDuration(startTime: Long, endTime: Long?): String {
    val end = endTime ?: System.currentTimeMillis()
    val durationMs = end - startTime
    
    return when {
        durationMs < 1000 -> "${durationMs}ms"
        durationMs < 60_000 -> "${durationMs / 1000}s"
        durationMs < 3600_000 -> "${durationMs / 60_000}m ${(durationMs % 60_000) / 1000}s"
        else -> "${durationMs / 3600_000}h ${(durationMs % 3600_000) / 60_000}m"
    }
}
