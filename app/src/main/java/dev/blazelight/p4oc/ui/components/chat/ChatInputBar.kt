package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.domain.model.Command
import dev.blazelight.p4oc.ui.components.TuiLoadingIndicator
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.TuiCodeFontSize

data class ModelOption(
    val key: String,
    val displayName: String
)

private fun nextCommandIndex(
    currentIndex: Int,
    delta: Int,
    commandCount: Int
): Int = (currentIndex + delta + commandCount) % commandCount

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
    isLoadingCommands: Boolean = false,
    commandLoadError: String? = null,
    onRetryCommands: () -> Unit = {},
    onCommandSelected: (Command) -> Unit = {},
    requestFocus: Boolean = false,
    enterToSend: Boolean = false,
) {
    val theme = LocalOpenCodeTheme.current
    val focusRequester = remember { FocusRequester() }
    var inputFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (value != inputFieldValue.text) {
            inputFieldValue = inputFieldValue.copy(
                text = value,
                selection = TextRange(value.length)
            )
        }
    }

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
    val currentText = inputFieldValue.text
    val hasContent = currentText.isNotBlank() || attachedFiles.isNotEmpty()
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
    val showSlashCommands = currentText.startsWith("/") && !currentText.contains(" ")
    val filteredCommands = remember(commands, currentText) {
        val searchTerm = currentText.removePrefix("/").lowercase()
        val matches = if (searchTerm.isEmpty()) {
            commands
        } else {
            commands.filter { cmd ->
                cmd.name.lowercase().contains(searchTerm) ||
                    cmd.description?.lowercase()?.contains(searchTerm) == true
            }
        }
        matches
    }
    var activeCommandIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentText, filteredCommands.size) {
        activeCommandIndex = activeCommandIndex.coerceIn(0, (filteredCommands.size - 1).coerceAtLeast(0))
    }

    fun selectActiveCommand(): Boolean {
        val command = filteredCommands.getOrNull(activeCommandIndex) ?: return false
        inputFieldValue = TextFieldValue("/${command.name} ")
        onValueChange(inputFieldValue.text)
        onCommandSelected(command)
        return true
    }

    fun clearInput() {
        inputFieldValue = TextFieldValue("")
        onValueChange("")
    }

    fun submitFromEnter(): Boolean = when {
        showSlashCommands -> selectActiveCommand()
        canSend -> {
            onSend()
            clearInput()
            true
        }
        canQueue -> {
            onQueueMessage()
            clearInput()
            true
        }
        else -> false
    }

    Box(modifier = modifier.fillMaxWidth()) {
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
                                        modifier = Modifier.clickable(
                                            role = Role.Button
                                        ) { onRemoveAttachment(file.path) }
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
                        if (currentText.isEmpty()) {
                            Text(
                                "> Message...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = theme.textMuted
                            )
                        }
                        BasicTextField(
                            value = inputFieldValue,
                            onValueChange = { nextValue: TextFieldValue ->
                                inputFieldValue = nextValue
                                onValueChange(nextValue.text)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) {
                                        return@onPreviewKeyEvent false
                                    }
                                    when (event.key) {
                                        Key.DirectionDown -> {
                                            if (!showSlashCommands) return@onPreviewKeyEvent false
                                            if (filteredCommands.isNotEmpty()) {
                                                activeCommandIndex = nextCommandIndex(
                                                    activeCommandIndex,
                                                    1,
                                                    filteredCommands.size
                                                )
                                            }
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            if (!showSlashCommands) return@onPreviewKeyEvent false
                                            if (filteredCommands.isNotEmpty()) {
                                                activeCommandIndex =
                                                    nextCommandIndex(
                                                        activeCommandIndex,
                                                        -1,
                                                        filteredCommands.size
                                                    )
                                            }
                                            true
                                        }
                                        Key.Tab -> showSlashCommands && selectActiveCommand()
                                        Key.Enter, Key.NumPadEnter -> {
                                            when {
                                                showSlashCommands -> selectActiveCommand()
                                                // When enter-to-send is on, always consume Enter so the
                                                // TextField can't commit a trailing newline that
                                                // re-populates the field after the message is sent.
                                                enterToSend -> {
                                                    submitFromEnter()
                                                    true
                                                }
                                                else -> false
                                            }
                                        }
                                        else -> false
                                    }
                                }
                                .testTag("chat_input"),
                            enabled = true,
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = TuiCodeFontSize.xxl,
                                color = theme.text
                            ),
                            cursorBrush = SolidColor(theme.accent),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = if (enterToSend) ImeAction.Send else ImeAction.Default,
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (enterToSend) submitFromEnter()
                                },
                            ),
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
                            clearInput()
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

        // Overlay above the input bar so it doesn't push agent/model controls.
        if (showSlashCommands) {
            SlashCommandsPopup(
                state = SlashCommandsPopupState(
                    commands = commands,
                    filter = currentText,
                    isLoading = isLoadingCommands,
                    error = commandLoadError,
                    activeCommandName = filteredCommands.getOrNull(activeCommandIndex)?.name
                ),
                callbacks = SlashCommandsPopupCallbacks(
                    onRetry = onRetryCommands,
                    onCommandSelected = { command ->
                        // Replace the current text with the command
                        inputFieldValue = TextFieldValue("/${command.name} ")
                        onValueChange(inputFieldValue.text)
                        onCommandSelected(command)
                    },
                    onDismiss = { /* Keep popup open while typing */ }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md)
            )
        }
    }
}
