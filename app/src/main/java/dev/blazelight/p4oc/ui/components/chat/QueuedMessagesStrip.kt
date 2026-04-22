package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.screens.chat.QueuedMessage
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing

@Composable
fun QueuedMessagesStrip(
    queuedMessages: List<QueuedMessage>,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (queuedMessages.isEmpty()) return

    val theme = LocalOpenCodeTheme.current
    val cancelDescription = stringResource(R.string.chat_action_queue_cancel)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        queuedMessages.forEach { queuedMessage ->
            Surface(
                shape = RectangleShape,
                color = theme.backgroundElement,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(Sizing.strokeMd, theme.border, RectangleShape)
                    .testTag("queued_message_${queuedMessage.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .background(theme.accent.copy(alpha = 0.16f), RectangleShape)
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                    ) {
                        Text(
                            text = stringResource(R.string.chat_queued_prefix),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = theme.accent
                        )
                    }
                    Text(
                        text = queuedMessage.text.ifBlank { queuedMessage.attachedFiles.joinToString { it.name } },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = theme.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (queuedMessage.attachedFiles.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            queuedMessage.attachedFiles.forEach { file ->
                                Surface(
                                    shape = RectangleShape,
                                    color = theme.background,
                                    modifier = Modifier.border(Sizing.strokeMd, theme.border, RectangleShape)
                                ) {
                                    Text(
                                        text = file.name,
                                        modifier = Modifier
                                            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                                            .widthIn(max = Sizing.panelWidthSm),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = theme.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = "×",
                        modifier = Modifier
                            .semantics {
                                contentDescription = cancelDescription
                            }
                            .clickable(role = Role.Button) { onCancel(queuedMessage.id) }
                            .testTag("queued_message_cancel_${queuedMessage.id}"),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        color = theme.textMuted
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizing.dividerThickness)
                .background(theme.border)
        )
    }
}
