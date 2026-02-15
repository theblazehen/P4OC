package dev.blazelight.p4oc.ui.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.ui.components.TermuxExtraKeysBar
import dev.blazelight.p4oc.ui.components.TermuxTerminalView
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.components.TuiLoadingIndicator

@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = koinViewModel(),
    onPtyLoaded: ((ptyId: String, ptyTitle: String) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Modifier key state (hoisted for use by both keyboard and extra keys bar)
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    
    // Wrapped input handler that processes CTRL/ALT modifiers
    val wrappedKeyInput: (String) -> Unit = remember(ctrlActive, altActive) {
        { input ->
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

    // Show error in snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
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
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    TermuxTerminalView(
                        emulator = viewModel.getTerminalEmulator(),
                        onKeyInput = wrappedKeyInput,
                        modifier = Modifier.fillMaxSize(),
                        onTerminalViewReady = { view -> viewModel.setTerminalView(view) }
                    )
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
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Snackbar host overlaid
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
