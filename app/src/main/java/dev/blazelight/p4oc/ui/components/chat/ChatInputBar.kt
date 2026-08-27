package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.domain.model.Command
import dev.blazelight.p4oc.ui.components.TuiLoadingIndicator
import dev.blazelight.p4oc.ui.components.command.rememberResolvedCommandMetadata
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.TuiCodeFontSize
import android.view.KeyEvent as AndroidKeyEvent

data class ModelOption(
    val key: String,
    val displayName: String
)

internal class PromptHistoryNavigator(initialDraft: String = "") {
    private var historyIndex: Int? = null
    private var draft: String = initialDraft

    fun older(history: List<String>, currentText: String): String? {
        if (history.isEmpty()) {
            reset(currentText)
            return null
        }

        val currentIndex = reconcileSelection(history, currentText)
        val nextIndex = currentIndex?.let { (it - 1).coerceAtLeast(0) } ?: history.lastIndex
        historyIndex = nextIndex
        return history[nextIndex]
    }

    fun newer(history: List<String>, currentText: String): String? {
        val nextText = if (history.isEmpty()) {
            reset(currentText)
            null
        } else {
            val currentIndex = reconcileSelection(history, currentText)
            when {
                currentIndex == null -> null
                currentIndex < history.lastIndex -> {
                    val nextIndex = currentIndex + 1
                    historyIndex = nextIndex
                    history[nextIndex]
                }
                else -> {
                    historyIndex = null
                    draft
                }
            }
        }
        return nextText
    }

    fun onTextChanged(text: String, history: List<String>) {
        reconcileSelection(history, text)
    }

    fun reset(text: String) {
        historyIndex = null
        draft = text
    }

    private fun reconcileSelection(history: List<String>, currentText: String): Int? {
        val currentIndex = historyIndex?.takeIf { it in history.indices }
        if (currentIndex == null || history[currentIndex] != currentText) {
            reset(currentText)
            return null
        }
        return currentIndex
    }
}

private fun nextCommandIndex(
    currentIndex: Int,
    delta: Int,
    commandCount: Int
): Int = (currentIndex + delta + commandCount) % commandCount

@Composable
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod", "FunctionNaming")
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Boolean,
    isLoading: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
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
    isActiveTab: Boolean = true,
    onComposerFocusChanged: (Boolean) -> Unit = {},
    promptHistory: List<String> = emptyList(),
    promptHistorySessionId: String? = null,
    enterToSend: Boolean = false,
    valueSyncGeneration: Long = 0,
) {
    val theme = LocalOpenCodeTheme.current
    val focusRequester = remember { FocusRequester() }
    val textState = rememberTextFieldState(initialText = value)
    var isAttached by remember { mutableStateOf(false) }
    val initialFocusState = rememberSaveable(saver = InitialInputFocusState.Saver) {
        InitialInputFocusState()
    }
    val historyNavigator = remember(promptHistorySessionId) { PromptHistoryNavigator(value) }
    val currentHistoryNavigator by rememberUpdatedState(historyNavigator)
    val currentPromptHistory by rememberUpdatedState(promptHistory)
    val currentText = textState.text.toString()

    // External value changes (e.g. a programmatic set from the parent) → field. The generation
    // also allows the parent to re-apply an unchanged value after the local field was cleared.
    LaunchedEffect(value, valueSyncGeneration) {
        if (value != textState.text.toString()) {
            historyNavigator.reset(value)
            textState.setTextAndPlaceCursorAtEnd(value)
        }
    }
    // Field edits → hoisted state. TextFieldState manages the IME composing
    // region correctly, so clearText() (on send) actually clears even on IMEs
    // like Samsung's that re-commit composing text with the old value API.
    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }.collect { text ->
            currentHistoryNavigator.onTextChanged(text, currentPromptHistory)
            onValueChange(text)
        }
    }

    // Request focus once the field is attached and the tab is active. The request stays eligible
    // while the tab is inactive and fires when it becomes active; onGloballyPositioned establishes
    // the FocusRequester attachment invariant, so a request issued here cannot be deferred to a
    // later reconnect solely because the composer was disabled. Consumption happens only in
    // onFocusChanged after focus is actually observed.
    LaunchedEffect(requestFocus, isActiveTab, isAttached, initialFocusState.isConsumed) {
        if (initialFocusState.shouldAttempt(requestFocus, isActiveTab) && isAttached) {
            focusRequester.requestFocus()
        }
    }

    // Determine button state
    val hasContent = currentText.isNotBlank() || attachedFiles.isNotEmpty()
    val canSubmit = hasContent && enabled && !isLoading
    val loadingDescription = stringResource(R.string.cd_loading)
    val sendDescription = stringResource(R.string.chat_action_send)
    val disconnectedDescription = stringResource(R.string.chat_disabled_disconnected)
    val emptyDescription = stringResource(R.string.chat_disabled_empty)
    val attachDescription = stringResource(R.string.chat_action_attach)
    val stopDescription = stringResource(R.string.chat_action_stop)
    val inputPlaceholder = stringResource(R.string.chat_input_placeholder)
    val sendContentDescription = when {
        isLoading -> loadingDescription
        canSubmit -> sendDescription
        !enabled -> disconnectedDescription
        else -> emptyDescription
    }
    val resolvedCommands = rememberResolvedCommandMetadata(commands)

    // Show slash commands popup when input starts with "/"
    val showSlashCommands = currentText.startsWith("/") && !currentText.contains(" ")
    val filteredCommands = remember(resolvedCommands, currentText) {
        val searchTerm = currentText.removePrefix("/").lowercase()
        val matches = if (searchTerm.isEmpty()) {
            resolvedCommands
        } else {
            resolvedCommands.filter { cmd ->
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
        textState.setTextAndPlaceCursorAtEnd("/${command.name} ")
        onCommandSelected(command)
        return true
    }

    fun clearInput() {
        // TextFieldState.clearText() resets the editing buffer including the IME
        // composing region, so the field stays cleared after send.
        historyNavigator.reset("")
        textState.clearText()
    }

    fun navigatePromptHistory(older: Boolean): Boolean {
        val liveText = textState.text.toString()
        val replacement = if (older) {
            historyNavigator.older(promptHistory, liveText)
        } else {
            historyNavigator.newer(promptHistory, liveText)
        } ?: return false
        textState.setTextAndPlaceCursorAtEnd(replacement)
        return true
    }

    fun submitFromEnter(): Boolean = when {
        showSlashCommands -> selectActiveCommand()
        canSubmit -> {
            if (onSend()) clearInput()
            // A refused submission is still a handled Enter/IME action. Letting it propagate
            // would insert a newline and mutate the exact draft the caller just refused.
            true
        }
        else -> false
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            color = theme.background,
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
                            val chipColor = if (file.available) theme.accent else theme.warning
                            val chipLabelColor = if (file.available) theme.text else theme.warning
                            val removeDescription = stringResource(
                                R.string.chat_action_remove_attachment,
                                file.name,
                            )
                            Box(modifier = Modifier.height(48.dp)) {
                                Surface(
                                    shape = RectangleShape,
                                    color = chipColor.copy(alpha = 0.1f),
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .height(Sizing.buttonHeightSm)
                                        .border(Sizing.strokeMd, chipColor, RectangleShape)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = Spacing.mdLg),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                    ) {
                                        Text(
                                            file.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = chipLabelColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = Sizing.panelWidthMd)
                                        )
                                        if (!file.available) {
                                            Text(
                                                text = stringResource(R.string.attachment_unavailable),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = theme.warning,
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(Sizing.iconButtonMd))
                                    }
                                }
                                IconButton(
                                    onClick = { onRemoveAttachment(file.path) },
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .size(48.dp)
                                        .semantics { contentDescription = removeDescription }
                                ) {
                                    Text(
                                        text = "×",
                                        color = theme.textMuted,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    // The controls rest on the same visual height. The field may grow
                    // to four lines while the button visuals remain square.
                    val controlSize = Sizing.iconButtonLg

                    if (enabled) {
                        ComposerSquareButton(
                            glyph = "+",
                            glyphColor = theme.textMuted,
                            fillColor = Color.Transparent,
                            borderColor = theme.border,
                            size = controlSize,
                            onClick = onAttachClick,
                            contentDescription = attachDescription,
                            testTag = "chat_attach_button",
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = controlSize)
                            .border(
                                width = Sizing.strokeMd,
                                color = theme.border,
                                shape = RectangleShape
                            )
                            .background(
                                theme.backgroundPanel,
                                RectangleShape
                            )
                            .padding(horizontal = Spacing.md),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (currentText.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "▷ ",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = TuiCodeFontSize.xxl
                                    ),
                                    fontFamily = FontFamily.Monospace,
                                    color = theme.primary
                                )
                                Text(
                                    inputPlaceholder,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = TuiCodeFontSize.xxl
                                    ),
                                    fontFamily = FontFamily.Monospace,
                                    color = theme.textMuted
                                )
                            }
                        }
                        BasicTextField(
                            state = textState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onGloballyPositioned { isAttached = true }
                                .onFocusChanged { focusState ->
                                    onComposerFocusChanged(focusState.isFocused)
                                    // Consume the initial request only when focus is actually
                                    // observed and the request is still eligible. A manual focus
                                    // satisfying a pending initial request consumes it too.
                                    val shouldConsumeInitialFocus = focusState.isFocused &&
                                        initialFocusState.shouldAttempt(requestFocus, isActiveTab)
                                    if (shouldConsumeInitialFocus) {
                                        initialFocusState.markConsumed()
                                    }
                                }
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
                                                enterToSend -> submitFromEnter()
                                                else -> false
                                            }
                                        }
                                        else -> when (event.key.nativeKeyCode) {
                                            AndroidKeyEvent.KEYCODE_VOLUME_UP -> navigatePromptHistory(older = true)
                                            AndroidKeyEvent.KEYCODE_VOLUME_DOWN -> navigatePromptHistory(older = false)
                                            else -> false
                                        }
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
                            onKeyboardAction = { performDefault ->
                                if (enterToSend) submitFromEnter() else performDefault()
                            },
                            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4)
                        )
                    }

                    if (isBusy) {
                        ComposerSquareButton(
                            glyph = "■",
                            glyphColor = theme.error,
                            fillColor = Color.Transparent,
                            borderColor = theme.border,
                            size = controlSize,
                            onClick = onAbort,
                            contentDescription = stopDescription,
                            testTag = "chat_abort_button",
                        )
                    }

                    // Keep the filled design when actionable; disabled states remain
                    // visibly muted and expose no click action.
                    ComposerSquareButton(
                        glyph = "↑",
                        glyphColor = if (canSubmit) theme.background else theme.textMuted,
                        fillColor = if (canSubmit) theme.primary else theme.backgroundPanel,
                        borderColor = if (canSubmit) theme.primary else theme.border,
                        size = controlSize,
                        enabled = canSubmit,
                        onClick = {
                            if (onSend()) clearInput()
                            focusRequester.requestFocus()
                        },
                        contentDescription = sendContentDescription,
                        testTag = "send_button",
                        loading = isLoading,
                    )
                }
            }
        }

        // Overlay above the input bar so it doesn't push agent/model controls.
        if (showSlashCommands) {
            SlashCommandsPopup(
                state = SlashCommandsPopupState(
                    commands = resolvedCommands,
                    filter = currentText,
                    isLoading = isLoadingCommands,
                    error = commandLoadError,
                    activeCommandName = filteredCommands.getOrNull(activeCommandIndex)?.name
                ),
                callbacks = SlashCommandsPopupCallbacks(
                    onRetry = onRetryCommands,
                    onCommandSelected = { command ->
                        // Replace the current text with the command
                        textState.setTextAndPlaceCursorAtEnd("/${command.name} ")
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

/**
 * Flat, perfectly-square composer control (attach / send / abort).
 *
 * The outer box owns the minimum touch target and semantics. The bordered
 * inner box stays at [size], preserving the compact TUI visual.
 */
@Composable
@Suppress("LongParameterList", "FunctionNaming")
private fun ComposerSquareButton(
    glyph: String,
    glyphColor: Color,
    fillColor: Color,
    borderColor: Color,
    size: Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
    contentDescription: String,
    testTag: String,
    loading: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(Sizing.minTouchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(fillColor, RectangleShape)
                .border(Sizing.strokeMd, borderColor, RectangleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                TuiLoadingIndicator()
            } else {
                Text(
                    text = glyph,
                    color = glyphColor,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
