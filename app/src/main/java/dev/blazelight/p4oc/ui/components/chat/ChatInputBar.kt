package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    enabled: Boolean,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    queuedCount: Int = 0,
    onQueueMessage: () -> Unit = {},
    onAbort: () -> Unit = {},
    attachedFiles: List<SelectedFile> = emptyList(),
    onAttachClick: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    commands: List<Command> = emptyList(),
    onCommandSelected: (Command) -> Unit = {},
    requestFocus: Boolean = false
) {
    val theme = LocalOpenCodeTheme.current
    val focusRequester = remember { FocusRequester() }
    
    // Request focus when triggered
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Focus request can fail if not attached yet
            }
        }
    }
    
    // Determine button state
    val hasContent = value.isNotBlank() || attachedFiles.isNotEmpty()
    val queueIsFull = queuedCount >= 10
    val canSend = hasContent && enabled && !isLoading && !isBusy
    val canQueue = hasContent && enabled && isBusy && !queueIsFull
    val loadingDescription = stringResource(R.string.cd_loading)
    val sendDescription = stringResource(R.string.chat_action_send)
    val queueDescription = stringResource(R.string.chat_action_queue)
    val queueFullDescription = stringResource(R.string.chat_action_queue_full)
    val disconnectedDescription = stringResource(R.string.chat_disabled_disconnected)
    val emptyDescription = stringResource(R.string.chat_disabled_empty)
    val attachDescription = stringResource(R.string.chat_action_attach)
    val stopDescription = stringResource(R.string.chat_action_stop)
    val sendContentDescription = when {
        isLoading -> loadingDescription
        canSend -> sendDescription
        canQueue -> queueDescription
        queueIsFull -> queueFullDescription
        !enabled -> disconnectedDescription
        else -> emptyDescription
    }

    // Show slash commands popup when input starts with "/"
    val showSlashCommands = value.startsWith("/") && !value.contains(" ") && commands.isNotEmpty()
    
    Box(modifier = modifier.fillMaxWidth()) {
        // Slash commands popup - positioned above the input bar
        if (showSlashCommands) {
            SlashCommandsPopup(
                commands = commands,
                filter = value,
                onCommandSelected = { command ->
                    // Replace the current text with the command
                    onValueChange("/${command.name} ")
                    onCommandSelected(command)
                },
                onDismiss = { /* Keep popup open while typing */ },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(y = (-4).dp)
            )
        }
        
        Surface(
            color = theme.backgroundElement,
            shape = RectangleShape
        ) {
            Column {
                if (attachedFiles.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        attachedFiles.forEach { file ->
                            Surface(
                                shape = RectangleShape,
                                color = theme.accent.copy(alpha = 0.1f),
                                modifier = Modifier
                                    .height(Sizing.buttonHeightSm)
                                    .border(Sizing.strokeMd, theme.border, RectangleShape)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = Spacing.mdLg),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    Text(
                                        file.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = theme.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = Sizing.panelWidthMd)
                                    )
                                    Text(
                                        text = "×",
                                        color = theme.textMuted,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.clickable(role = Role.Button) { onRemoveAttachment(file.path) }
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    if (enabled) {
                        IconButton(
                            onClick = onAttachClick,
                            modifier = Modifier
                                .size(Sizing.iconButtonMd)
                                .semantics { contentDescription = attachDescription }
                                .testTag("chat_attach_button")
                        ) {
                            Text(
                                text = "+",
                                color = theme.accent,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = Sizing.textFieldHeightSm)
                            .border(
                                width = Sizing.strokeMd,
                                color = theme.border,
                                shape = RectangleShape
                            )
                            .background(
                                theme.background,
                                RectangleShape
                            )
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                "> Message...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = theme.textMuted
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
                            maxLines = 4
                        )
                    }

                    if (isBusy) {
                        IconButton(
                            onClick = onAbort,
                            modifier = Modifier
                                .size(Sizing.iconButtonMd)
                                .semantics { contentDescription = stopDescription }
                                .testTag("chat_abort_button")
                        ) {
                            Text(
                                text = "■",
                                color = theme.error,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            when {
                                canSend -> onSend()
                                canQueue -> onQueueMessage()
                                else -> return@IconButton
                            }
                            focusRequester.requestFocus()
                        },
                        enabled = canSend || canQueue,
                        modifier = Modifier
                            .size(Sizing.iconButtonMd)
                            .semantics { contentDescription = sendContentDescription }
                            .testTag("send_button")
                    ) {
                        if (isLoading) {
                            TuiLoadingIndicator()
                        } else {
                            Text(
                                text = "↑",
                                color = if (canSend || canQueue) theme.accent else theme.textMuted,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
