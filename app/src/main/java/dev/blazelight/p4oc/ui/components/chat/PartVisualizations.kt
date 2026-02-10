package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
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
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing

@Composable
fun RetryPartDisplay(
    attempt: Int,
    errorMessage: String?,
    nextRetryTime: Long?,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = theme.error.copy(alpha = 0.1f),
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
                        text = "!",
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.error
                    )
                    Text(
                        text = stringResource(R.string.retry_attempt, attempt),
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.error
                    )
                }
                
                if (nextRetryTime != null) {
                    RetryCountdown(targetTime = nextRetryTime)
                }
            }
            
            errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMuted,
                    modifier = Modifier.padding(start = Spacing.lg)
                )
            }
        }
    }
}

@Composable
private fun RetryCountdown(targetTime: Long) {
    val theme = LocalOpenCodeTheme.current
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
        Text(
            text = "[${stringResource(R.string.retry_in_seconds, remainingSeconds)}]",
            style = MaterialTheme.typography.labelSmall,
            color = theme.error
        )
    }
}

@Composable
fun StepStartDisplay(
    snapshot: String?,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
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
        color = theme.accent.copy(alpha = 0.1f),
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "▶",
                style = MaterialTheme.typography.bodySmall,
                color = theme.accent.copy(alpha = dotAlpha)
            )
            
            Text(
                text = stringResource(R.string.step_started),
                style = MaterialTheme.typography.bodySmall,
                color = theme.text
            )
            
            snapshot?.let {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "[${it.take(8)}]",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted
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
    val theme = LocalOpenCodeTheme.current
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = theme.backgroundElement,
        shape = RectangleShape
    ) {
        Column(
            modifier = Modifier.padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
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
                        text = "✓",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.success
                    )
                    Text(
                        text = stringResource(R.string.step_completed),
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.text
                    )
                }
                
                reason?.let { r ->
                    Text(
                        text = "[${formatReason(r)}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = getReasonColor(r)
                    )
                }
            }
            
            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = "in:${formatTokens(inputTokens)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted
                )
                Text(
                    text = "out:${formatTokens(outputTokens)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted
                )
                duration?.let { d ->
                    Text(
                        text = formatDuration(d),
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted
                    )
                }
                cost?.let { c ->
                    Text(
                        text = "$${String.format(java.util.Locale.US, "%.4f", c)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted
                    )
                }
            }
        }
    }
}

@Composable
fun SnapshotPartDisplay(
    snapshotId: String,
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
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "◆",
                style = MaterialTheme.typography.bodySmall,
                color = theme.info
            )
            Text(
                text = stringResource(R.string.snapshot_created),
                style = MaterialTheme.typography.bodySmall,
                color = theme.text
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "[${snapshotId.take(8)}]",
                style = MaterialTheme.typography.labelSmall,
                color = theme.textMuted
            )
        }
    }
}

@Composable
fun CompactionPartDisplay(
    isAuto: Boolean,
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
            Text(
                text = "⟨⟩",
                style = MaterialTheme.typography.bodySmall,
                color = theme.warning
            )
            Text(
                text = if (isAuto) "Auto-compacted" else "Manually compacted",
                style = MaterialTheme.typography.bodySmall,
                color = theme.text
            )
        }
    }
}

@Composable
private fun getReasonColor(reason: String): Color = SemanticColors.Reason.forReason(reason)

private fun formatReason(reason: String): String = when (reason.lowercase()) {
    "end_turn" -> "end"
    "stop" -> "stop"
    "tool_use", "tool_calls" -> "tools"
    "max_tokens" -> "max"
    "error" -> "err"
    else -> reason.take(6)
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
