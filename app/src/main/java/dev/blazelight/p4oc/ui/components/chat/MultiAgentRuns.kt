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
    if (agents.isEmpty()) return
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Groups,
                        contentDescription = stringResource(R.string.agent_runs),
                        modifier = Modifier.size(Sizing.iconMd),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.agent_runs),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                val runningCount = agents.count { it.status == AgentRunStatus.RUNNING }
                if (runningCount > 0) {
                    Surface(
                        shape = RectangleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.agent_running_count, runningCount),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            agents.forEach { agent ->
                AgentRunRow(agent = agent)
            }
        }
    }
}

@Composable
private fun AgentRunRow(agent: AgentRun) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AgentStatusIndicator(status = agent.status)
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = agent.agentName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            agent.taskDescription?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        
        Text(
            text = formatDuration(agent.startTime, agent.endTime),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AgentStatusIndicator(status: AgentRunStatus) {
    val color = when (status) {
        AgentRunStatus.PENDING -> MaterialTheme.colorScheme.outline
        AgentRunStatus.RUNNING -> MaterialTheme.colorScheme.primary
        AgentRunStatus.COMPLETED -> SemanticColors.Status.success
        AgentRunStatus.FAILED -> MaterialTheme.colorScheme.error
    }
    
    val icon = when (status) {
        AgentRunStatus.PENDING -> Icons.Default.Schedule
        AgentRunStatus.RUNNING -> Icons.Default.PlayArrow
        AgentRunStatus.COMPLETED -> Icons.Default.CheckCircle
        AgentRunStatus.FAILED -> Icons.Default.Error
    }
    
    if (status == AgentRunStatus.RUNNING) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
        
        Box(
            modifier = Modifier
                .size(Sizing.iconLg)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha * 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = stringResource(R.string.cd_agent_status),
                modifier = Modifier.size(Sizing.iconXs),
                tint = color
            )
        }
    } else {
        Icon(
            icon,
            contentDescription = stringResource(R.string.cd_agent_status),
            modifier = Modifier.size(Sizing.iconMd),
            tint = color
        )
    }
}

@Composable
fun AgentSwitchIndicator(
    fromAgent: String?,
    toAgent: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (fromAgent != null) {
                AgentBadge(name = fromAgent, isActive = false)
                
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.cd_decorative),
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(Sizing.iconXs),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            } else {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.cd_agent_running),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(Sizing.iconXs),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            
            AgentBadge(name = toAgent, isActive = true)
        }
    }
}

@Composable
private fun AgentBadge(
    name: String,
    isActive: Boolean
) {
    Surface(
        shape = RectangleShape,
        color = if (isActive) 
            MaterialTheme.colorScheme.primary 
        else 
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = stringResource(R.string.cd_agent_icon),
                modifier = Modifier.size(14.dp),
                tint = if (isActive) 
                    MaterialTheme.colorScheme.onPrimary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = if (isActive) 
                    MaterialTheme.colorScheme.onPrimary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
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
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AgentStatusIndicator(status = status)
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.subtask),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    AgentBadge(name = agentName, isActive = status == AgentRunStatus.RUNNING)
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
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
