package dev.blazelight.p4oc.ui.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.components.TermuxExtraKeysBar
import dev.blazelight.p4oc.ui.components.TermuxTerminalView
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(uiState.selectedPty?.title ?: stringResource(R.string.terminal_title))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::clearTerminal) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clear))
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    IconButton(onClick = { viewModel.createNewSession() }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.terminal_create))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.ptySessions.isNotEmpty()) {
                SessionTabRow(
                    sessions = uiState.ptySessions,
                    selectedPtyId = uiState.selectedPtyId,
                    onSelectSession = viewModel::selectSession,
                    onDeleteSession = viewModel::deleteSession
                )
                HorizontalDivider()
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(SemanticColors.Terminal.background)
            ) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = SemanticColors.Terminal.green
                        )
                    }
                    uiState.ptySessions.isEmpty() -> {
                        EmptyTerminalState(
                            onCreateTerminal = { viewModel.createNewSession() },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        TermuxTerminalView(
                            emulator = viewModel.getTerminalEmulator(),
                            onKeyInput = viewModel::sendInput,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            TermuxExtraKeysBar(
                onKeyPress = viewModel::sendInput,
                enabled = uiState.selectedPtyId != null && uiState.isConnected,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SessionTabRow(
    sessions: List<dev.blazelight.p4oc.domain.model.Pty>,
    selectedPtyId: String?,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(sessions) { pty ->
            FilterChip(
                selected = pty.id == selectedPtyId,
                onClick = { onSelectSession(pty.id) },
                label = { Text(pty.title) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = stringResource(R.string.cd_terminal_tab),
                        modifier = Modifier.size(Sizing.iconXs)
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { onDeleteSession(pty.id) },
                        modifier = Modifier.size(Sizing.iconXs)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun EmptyTerminalState(
    onCreateTerminal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.Terminal,
            contentDescription = stringResource(R.string.cd_terminal),
            modifier = Modifier.size(64.dp),
            tint = SemanticColors.Terminal.green
        )
        Text(
            text = stringResource(R.string.terminal_no_sessions),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
        Button(
            onClick = onCreateTerminal,
            colors = ButtonDefaults.buttonColors(
                containerColor = SemanticColors.Terminal.green,
                contentColor = Color.Black
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.terminal_create))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.terminal_create))
        }
    }
}
