package dev.blazelight.p4oc.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.theme.SemanticColors

data class ContextUsage(
    val usedTokens: Int,
    val maxTokens: Int,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cachedTokens: Int = 0,
    val estimatedCost: Double? = null
)

@Composable
fun ContextUsageIndicator(
    usage: ContextUsage,
    modifier: Modifier = Modifier,
    compact: Boolean = true
) {
    val percentage = (usage.usedTokens.toFloat() / usage.maxTokens.toFloat()).coerceIn(0f, 1f)
    val animatedPercentage by animateFloatAsState(
        targetValue = percentage,
        animationSpec = tween(500),
        label = "context_percentage"
    )
    
    val color = when {
        percentage > 0.9f -> MaterialTheme.colorScheme.error
        percentage > 0.75f -> SemanticColors.Usage.high
        percentage > 0.5f -> SemanticColors.Usage.medium
        else -> MaterialTheme.colorScheme.primary
    }
    
    if (compact) {
        CompactContextIndicator(
            percentage = animatedPercentage,
            color = color,
            usedTokens = usage.usedTokens,
            maxTokens = usage.maxTokens,
            modifier = modifier
        )
    } else {
        ExpandedContextIndicator(
            usage = usage,
            percentage = animatedPercentage,
            color = color,
            modifier = modifier
        )
    }
}

@Composable
private fun CompactContextIndicator(
    percentage: Float,
    color: Color,
    usedTokens: Int,
    maxTokens: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                val strokeWidth = 3.dp.toPx()
                
                drawArc(
                    color = color.copy(alpha = 0.2f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * percentage,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        
        Text(
            text = "${(percentage * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun ExpandedContextIndicator(
    usage: ContextUsage,
    percentage: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Memory,
                        contentDescription = stringResource(R.string.cd_context_memory),
                        tint = color
                    )
                    Text(
                        text = stringResource(R.string.context_usage),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                
                Text(
                    text = "${formatTokenCount(usage.usedTokens)} / ${formatTokenCount(usage.maxTokens)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = color
                )
            }
            
            LinearProgressIndicator(
                progress = { percentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = color,
                trackColor = color.copy(alpha = 0.2f),
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TokenStat(
                    label = "Input",
                    value = usage.inputTokens,
                    icon = Icons.AutoMirrored.Filled.Input,
                    color = MaterialTheme.colorScheme.primary
                )
                TokenStat(
                    label = "Output",
                    value = usage.outputTokens,
                    icon = Icons.Default.Output,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (usage.cachedTokens > 0) {
                    TokenStat(
                        label = "Cached",
                        value = usage.cachedTokens,
                        icon = Icons.Default.Cached,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            
            usage.estimatedCost?.let { cost ->
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.estimated_cost),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$${String.format(java.util.Locale.US, "%.4f", cost)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun TokenStat(
    label: String,
    value: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = stringResource(R.string.cd_token_stat),
            modifier = Modifier.size(16.dp),
            tint = color
        )
        Text(
            text = formatTokenCount(value),
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
fun ContextUsageBar(
    usage: ContextUsage,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier
            .animateContentSize()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        if (expanded) {
            ExpandedContextIndicator(
                usage = usage,
                percentage = usage.usedTokens.toFloat() / usage.maxTokens.toFloat(),
                color = getContextColor(usage),
                modifier = Modifier.clickable { expanded = false }
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ContextUsageIndicator(
                    usage = usage,
                    compact = true
                )
                
                LinearProgressIndicator(
                    progress = { usage.usedTokens.toFloat() / usage.maxTokens.toFloat() },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp),
                    color = getContextColor(usage),
                    trackColor = getContextColor(usage).copy(alpha = 0.2f),
                )
                
                Text(
                    text = formatTokenCount(usage.usedTokens),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = stringResource(R.string.cd_show_details),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun getContextColor(usage: ContextUsage): Color {
    val percentage = usage.usedTokens.toFloat() / usage.maxTokens.toFloat()
    return when {
        percentage > 0.9f -> MaterialTheme.colorScheme.error
        percentage > 0.75f -> SemanticColors.Usage.high
        percentage > 0.5f -> SemanticColors.Usage.medium
        else -> MaterialTheme.colorScheme.primary
    }
}

private fun formatTokenCount(tokens: Int): String = when {
    tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
    tokens >= 1_000 -> "${tokens / 1_000}K"
    else -> tokens.toString()
}
