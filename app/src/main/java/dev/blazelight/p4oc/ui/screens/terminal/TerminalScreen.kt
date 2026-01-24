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
import dev.blazelight.p4oc.ui.components.TermuxExtraKeysBar
import dev.blazelight.p4oc.ui.components.TermuxTerminalView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(uiState.selectedPty?.title ?: "Terminal")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::clearTerminal) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear")
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { viewModel.createNewSession() }) {
                        Icon(Icons.Default.Add, contentDescription = "New Terminal")
                    }
                }
            )
        }
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
                    .background(Color.Black)
            ) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color(0xFF00FF00)
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

        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = viewModel::clearError) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(error)
            }
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
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { onDeleteSession(pty.id) },
                        modifier = Modifier.size(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
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
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.Terminal,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color(0xFF00FF00)
        )
        Text(
            text = "No Terminal Sessions",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
        Button(
            onClick = onCreateTerminal,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00FF00),
                contentColor = Color.Black
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Create Terminal")
        }
    }
}
