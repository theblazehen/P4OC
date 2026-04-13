package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.domain.model.Command
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.TuiCodeFontSize
import dev.blazelight.p4oc.ui.components.TuiLoadingIndicator

data class ModelOption(
    val key: String,
    val displayName: String
)

@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    modelSelector: @Composable () -> Unit = {},
    agentSelector: @Composable () -> Unit = {},
    enabled: Boolean,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    hasQueuedMessage: Boolean = false,
    onQueueMessage: () -> Unit = {},
    onCancelQueue: (() -> Unit)? = null,
    queuedMessagePreview: String? = null,
    attachedFiles: List<SelectedFile> = emptyList(),
    onAttachClick: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    commands: List<Command> = emptyList(),
    onCommandSelected: (Command) -> Unit = {},
    requestFocus: Boolean = false
) {
    val theme = LocalOpenCodeTheme.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val hasContent = value.isNotBlank() || attachedFiles.isNotEmpty()
    val canSend = hasContent && enabled && !isLoading && !isBusy
    val canQueue = hasContent && isBusy && !hasQueuedMessage
    val showSlashCommands = value.startsWith("/") && !value.contains(" ") && commands.isNotEmpty()

    // Contextual placeholder - modern and short
    val placeholder = when {
        !enabled         -> "Offline"
        isBusy && hasQueuedMessage -> "Queued ⊕"
        isBusy           -> "Queue next..."
        isLoading        -> "Sending..."
        else             -> "Type..."
    }

    Box(modifier = modifier.fillMaxWidth()) {
        if (showSlashCommands) {
            SlashCommandsPopup(
                commands = commands,
                filter = value,
                onCommandSelected = { command ->
                    onValueChange("/${command.name} ")
                    onCommandSelected(command)
                },
                onDismiss = {},
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(y = (-4).dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.background)
        ) {
            // Rounded corners top frame - erudite style ⌜ ⌝
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⌜",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.accent.copy(alpha = 0.9f)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    theme.accent.copy(alpha = 0.6f),
                                    theme.border.copy(alpha = 0.1f),
                                    theme.accent.copy(alpha = 0.6f)
                                )
                            )
                        )
                )
                Text(
                    text = "⌝",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.accent.copy(alpha = 0.9f)
                )
            }

            // ── Queued message chip ─────────────────────────────────────────
            AnimatedVisibility(
                visible = hasQueuedMessage && queuedMessagePreview != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.background)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pulsing indicator
                    QueuePulseDot(color = theme.accent)
                    Text(
                        text = "Queued:",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.accent
                    )
                    Text(
                        text = queuedMessagePreview ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = theme.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // File attachments - compact row
            if (attachedFiles.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    attachedFiles.forEach { file ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(theme.backgroundElement)
                                .border(1.dp, theme.border, RoundedCornerShape(2.dp))
                                .height(Sizing.buttonHeightSm)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "▒",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.accent
                            )
                            Text(
                                file.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = theme.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = Sizing.panelWidthMd)
                            )
                            Box(
                                modifier = Modifier
                                    .clickable(role = Role.Button) { onRemoveAttachment(file.path) }
                                    .padding(2.dp)
                            ) {
                                Text(
                                    text = "×",
                                    color = theme.textMuted,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            // ── Main input row (unified with selectors) ─────────────────────
            Row(
                modifier = Modifier
                    .padding(horizontal = Spacing.sm, vertical = Spacing.hairline)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                // Attach button
                Box(
                    modifier = Modifier
                        .size(Sizing.touchTargetSm)
                        .clip(RoundedCornerShape(Sizing.radiusNone))
                        .background(if (enabled) theme.accent else theme.backgroundElement)
                        .then(
                            if (enabled && !isLoading) Modifier.clickable(role = Role.Button) { onAttachClick() }
                            else Modifier
                        )
                        .testTag("attach_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = theme.background,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Input field
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(Sizing.touchTargetSm)
                        .clip(RoundedCornerShape(Sizing.radiusNone))
                        .background(theme.backgroundElement)
                        .border(Sizing.strokeThin, theme.accent.copy(alpha = 0.5f), RoundedCornerShape(Sizing.radiusNone))
                        .padding(horizontal = Spacing.sm, vertical = Spacing.none),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ASCII prompt - terminal style
                    Text(
                        text = ">",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = theme.accent,
                        modifier = Modifier.padding(end = Spacing.sm)
                    )

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = theme.textMuted.copy(alpha = 0.5f)
                            )
                        }
                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .testTag("chat_input"),
                            enabled = true,
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = TuiCodeFontSize.xxl,
                                color = theme.text
                            ),
                            cursorBrush = SolidColor(theme.accent),
                            maxLines = 5
                        )
                    }
                }

                // Send/Queue/Cancel button
                val isCancelState = isBusy && onCancelQueue != null
                val iconColor = when {
                    canSend || canQueue -> theme.background
                    isCancelState -> theme.error
                    else -> theme.textMuted.copy(alpha = 0.4f)
                }
                Box(
                    modifier = Modifier
                        .size(Sizing.touchTargetSm)
                        .clip(RoundedCornerShape(Sizing.radiusNone))
                        .background(
                            when {
                                canSend -> theme.accent
                                canQueue -> theme.warning
                                isCancelState -> theme.error.copy(alpha = 0.15f)
                                else -> theme.backgroundElement
                            }
                        )
                        .then(
                            if (isCancelState || (!hasContent && value.isNotEmpty())) {
                                Modifier.clickable(role = Role.Button) {
                                    if (isBusy && onCancelQueue != null) {
                                        onCancelQueue()
                                    } else {
                                        onValueChange("")
                                    }
                                    try { focusRequester.requestFocus() } catch (_: Exception) {}
                                }.testTag("cancel_button")
                            } else if (canSend) {
                                Modifier.clickable(role = Role.Button) {
                                    onSend()
                                    try { focusRequester.requestFocus() } catch (_: Exception) {}
                                }.testTag("send_button")
                            } else if (canQueue) {
                                Modifier.clickable(role = Role.Button) {
                                    onQueueMessage()
                                    try { focusRequester.requestFocus() } catch (_: Exception) {}
                                }.testTag("chat_queue_button")
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Optimized button rendering with state-based display
                    when {
                        isLoading -> TuiLoadingIndicator()
                        isCancelState || (!hasContent && value.isNotEmpty()) -> Text(
                            text = "✕",
                            color = theme.error.copy(alpha = 0.8f),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        canQueue -> Text(
                            text = "⊕",
                            color = iconColor,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        else -> Text(
                            text = "↑",
                            color = iconColor,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bottom row: selectors + corner marks unified
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.none),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "⌞",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.border.copy(alpha = 0.15f)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                agentSelector()
                Spacer(modifier = Modifier.width(Spacing.sm))
                modelSelector()
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "⌟",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.border.copy(alpha = 0.15f)
                )
            }
        }
    }
}

@Composable
private fun QueuePulseDot(color: androidx.compose.ui.graphics.Color) {
    val transition = rememberInfiniteTransition(label = "queuePulse")
    val alpha by transition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .alpha(alpha)
            .background(color)
    )
}

