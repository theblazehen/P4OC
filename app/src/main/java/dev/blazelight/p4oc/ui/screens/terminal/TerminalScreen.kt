package dev.blazelight.p4oc.ui.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.termux.view.TerminalView
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.datastore.VisualSettings
import dev.blazelight.p4oc.ui.components.TermuxExtraKeysBar
import dev.blazelight.p4oc.ui.components.TermuxTerminalView
import dev.blazelight.p4oc.ui.components.TuiLoadingIndicator
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.Spacing
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = koinViewModel(),
    onPtyLoaded: ((ptyId: String, ptyTitle: String) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accessibleScreenText by viewModel.accessibleScreenText.collectAsStateWithLifecycle()
    val terminalAccessibilityLabel = stringResource(R.string.terminal_accessibility_label)
    val terminalReconnectingState = stringResource(R.string.terminal_accessibility_reconnecting)
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    val currentTerminalView by rememberUpdatedState(terminalView)
    val settingsDataStore: SettingsDataStore = koinInject()
    val visualSettings by settingsDataStore.visualSettings.collectAsStateWithLifecycle(
        initialValue = VisualSettings(),
    )
    val settingsScope = rememberCoroutineScope()

    // Modifier key state (hoisted for use by both keyboard and extra keys bar)
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }

    // Wrapped input handler that processes CTRL/ALT modifiers
    val wrappedKeyInput: (String) -> Unit = remember(ctrlActive, altActive) {
        {
                input ->
            when {
                // CTRL + lowercase letter (a-z) -> control character (0x01-0x1A)
                ctrlActive && input.length == 1 && input[0] in 'a'..'z' -> {
                    val controlChar = (input[0].code - 'a'.code + 1).toChar().toString()
                    viewModel.sendInput(controlChar)
                    ctrlActive = false
                }
                // CTRL + uppercase letter (A-Z) -> control character (0x01-0x1A)
                ctrlActive && input.length == 1 && input[0] in 'A'..'Z' -> {
                    val controlChar = (input[0].code - 'A'.code + 1).toChar().toString()
                    viewModel.sendInput(controlChar)
                    ctrlActive = false
                }
                // ALT + any single character -> ESC + character
                altActive && input.length == 1 -> {
                    viewModel.sendInput("\u001B$input")
                    altActive = false
                }
                // No modifiers active, or multi-char input (sequences like arrow keys)
                else -> viewModel.sendInput(input)
            }
        }
    }

    // Notify when PTY title is loaded
    LaunchedEffect(uiState.title) {
        uiState.title?.let { title ->
            onPtyLoaded?.invoke(viewModel.ptyId, title)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.terminalInvalidations.collect {
            currentTerminalView?.postInvalidate()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
        ) {
            // Terminal view
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(SemanticColors.Terminal.background)
            ) {
                if (uiState.isConnecting && !uiState.isConnected) {
                    TuiLoadingIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .semantics {
                                contentDescription = terminalAccessibilityLabel
                                stateDescription = terminalReconnectingState
                                liveRegion = LiveRegionMode.Polite
                            }
                    )
                } else if (uiState.isConnected) {
                    TermuxTerminalView(
                        emulator = viewModel.getTerminalEmulator(),
                        accessibleScreenText = accessibleScreenText,
                        onKeyInput = wrappedKeyInput,
                        textSizeSp = visualSettings.terminalFontSize,
                        onTextSizeChange = { size ->
                            settingsScope.launch { settingsDataStore.setTerminalFontSize(size) }
                        },
                        modifier = Modifier.fillMaxSize(),
                        onTerminalViewReady = { view -> terminalView = view },
                        onTerminalSizeChanged = viewModel::onTerminalSizeChanged,
                    )
                } else {
                    terminalDisconnectedState(uiState, viewModel::reconnect)
                }
            }

            // Extra keys bar
            TermuxExtraKeysBar(
                onKeyPress = wrappedKeyInput,
                ctrlActive = ctrlActive,
                altActive = altActive,
                onCtrlToggle = { ctrlActive = !ctrlActive },
                onAltToggle = { altActive = !altActive },
                enabled = uiState.isConnected,
                onPaste = { currentTerminalView?.mTermSession?.onPasteTextFromClipboard() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BoxScope.terminalDisconnectedState(state: TerminalUiState, onRetry: () -> Unit) {
    val theme = LocalOpenCodeTheme.current
    val accessibilityState = when {
        state.isExited -> stringResource(R.string.terminal_exited)
        else -> stringResource(R.string.terminal_disconnected)
    }
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(Spacing.lg)
            .semantics {
                stateDescription = accessibilityState
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = when {
                state.isExited -> stringResource(R.string.terminal_exited)
                state.error != null -> state.error
                else -> stringResource(R.string.terminal_disconnected)
            },
            color = theme.text,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!state.isExited) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}
