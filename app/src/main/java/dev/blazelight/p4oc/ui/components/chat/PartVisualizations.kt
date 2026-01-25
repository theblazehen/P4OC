package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.domain.model.Part

@Composable
fun RetryPartDisplay(
    attempt: Int,
    errorMessage: String?,
    nextRetryTime: Long?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Retry Attempt $attempt",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                if (nextRetryTime != null) {
                    RetryCountdown(targetTime = nextRetryTime)
                }
            }
            
            errorMessage?.let { message ->
                Surface(
                    shape = RectangleShape,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RetryCountdown(targetTime: Long) {
    var remainingSeconds by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(targetTime) {
        while (true) {
            val remaining = ((targetTime - System.currentTimeMillis()) / 1000).toInt()
            remainingSeconds = maxOf(0, remaining)
            if (remaining <= 0) break
            kotlinx.coroutines.delay(1000)
        }
    }
    
    if (remainingSeconds > 0) {
        Surface(
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.error
        ) {
            Text(
                text = "Retry in ${remainingSeconds}s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onError,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun StepStartDisplay(
    snapshot: String?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "step_progress")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha))
            )
            
            Text(
                text = "Step started",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            snapshot?.let {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Snapshot: ${it.take(8)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun StepFinishDisplay(
    reason: String?,
    cost: Double?,
    inputTokens: Int,
    outputTokens: Int,
    duration: Long?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF4CAF50)
                    )
                    Text(
                        text = "Step completed",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                reason?.let { r ->
                    Surface(
                        shape = RectangleShape,
                        color = getReasonColor(r).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = formatReason(r),
                            style = MaterialTheme.typography.labelSmall,
                            color = getReasonColor(r),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StepStat(
                    label = "Input",
                    value = formatTokens(inputTokens),
                    icon = Icons.Default.Input
                )
                StepStat(
                    label = "Output",
                    value = formatTokens(outputTokens),
                    icon = Icons.Default.Output
                )
                duration?.let { d ->
                    StepStat(
                        label = "Duration",
                        value = formatDuration(d),
                        icon = Icons.Default.Timer
                    )
                }
                cost?.let { c ->
                    StepStat(
                        label = "Cost",
                        value = "$${String.format(java.util.Locale.US, "%.4f", c)}",
                        icon = Icons.Default.AttachMoney
                    )
                }
            }
        }
    }
}

@Composable
private fun StepStat(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SnapshotPartDisplay(
    snapshotId: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = "Snapshot created",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = snapshotId.take(8),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun CompactionPartDisplay(
    isAuto: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Compress,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = if (isAuto) "Auto-compacted" else "Manually compacted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

private fun getReasonColor(reason: String): Color = when (reason.lowercase()) {
    "end_turn", "stop" -> Color(0xFF4CAF50)
    "tool_use", "tool_calls" -> Color(0xFF2196F3)
    "max_tokens" -> Color(0xFFFFA726)
    "error" -> Color(0xFFF44336)
    else -> Color(0xFF78909C)
}

private fun formatReason(reason: String): String = when (reason.lowercase()) {
    "end_turn" -> "End Turn"
    "stop" -> "Stopped"
    "tool_use", "tool_calls" -> "Tool Use"
    "max_tokens" -> "Max Tokens"
    "error" -> "Error"
    else -> reason.replaceFirstChar { it.uppercase() }
}

private fun formatTokens(tokens: Int): String = when {
    tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
    tokens >= 1_000 -> "${tokens / 1_000}K"
    else -> tokens.toString()
}

private fun formatDuration(ms: Long): String = when {
    ms < 1000 -> "${ms}ms"
    ms < 60_000 -> "${ms / 1000}s"
    else -> "${ms / 60_000}m ${(ms % 60_000) / 1000}s"
}
