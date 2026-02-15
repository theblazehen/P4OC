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
            modifier = Modifier.fillMaxSize()
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
                        onKeyInput = viewModel::sendInput,
                        modifier = Modifier.fillMaxSize(),
                        onTerminalViewReady = { view -> viewModel.setTerminalView(view) }
                    )
                }
            }

            // Extra keys bar
            TermuxExtraKeysBar(
                onKeyPress = viewModel::sendInput,
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
