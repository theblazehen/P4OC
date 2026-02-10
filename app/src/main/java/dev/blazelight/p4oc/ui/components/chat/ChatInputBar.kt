package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
    enabled: Boolean,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    hasQueuedMessage: Boolean = false,
    onQueueMessage: () -> Unit = {},
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
    val canSend = hasContent && enabled && !isLoading && !isBusy
    val canQueue = hasContent && isBusy && !hasQueuedMessage
    
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
                                    .height(32.dp)
                                    .border(1.dp, theme.border, RectangleShape)
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
                                        modifier = Modifier.widthIn(max = 120.dp)
                                    )
                                    Text(
                                        text = "×",
                                        color = theme.textMuted,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.clickable { onRemoveAttachment(file.path) }
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
                    IconButton(
                        onClick = onAttachClick,
                        enabled = enabled,
                        modifier = Modifier.size(Sizing.iconButtonMd)
                    ) {
                        Text(
                            text = "+",
                            color = if (enabled) theme.accent else theme.textMuted,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .border(
                                width = 1.dp,
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
                                .focusRequester(focusRequester),
                            enabled = true,  // Always enabled to keep keyboard open
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = TuiCodeFontSize.xxl,
                                color = theme.text
                            ),
                            cursorBrush = SolidColor(theme.accent),
                            maxLines = 4
                        )
                    }

                    // Show queue button when busy, send button otherwise
                    if (isBusy && !isLoading) {
                        IconButton(
                            onClick = {
                                onQueueMessage()
                                focusRequester.requestFocus()
                            },
                            enabled = canQueue,
                            modifier = Modifier.size(Sizing.iconButtonMd)
                        ) {
                            Text(
                                text = "⊕",
                                color = if (hasQueuedMessage) theme.accent else theme.textMuted,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                onSend()
                                // Re-request focus after sending to keep keyboard open
                                focusRequester.requestFocus()
                            },
                            enabled = canSend,
                            modifier = Modifier.size(Sizing.iconButtonMd)
                        ) {
                            if (isLoading) {
                                TuiLoadingIndicator()
                            } else {
                                Text(
                                    text = "→",
                                    color = if (canSend) theme.accent else theme.textMuted,
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
}
