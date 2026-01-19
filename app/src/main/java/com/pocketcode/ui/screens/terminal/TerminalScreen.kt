package com.pocketcode.ui.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(uiState.outputBuffer) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

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
                    IconButton(onClick = viewModel::clearOutput) {
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
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.ptySessions) { pty ->
                        FilterChip(
                            selected = pty.id == uiState.selectedPtyId,
                            onClick = { viewModel.selectSession(pty.id) },
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
                                    onClick = { viewModel.deleteSession(pty.id) },
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
                HorizontalDivider()
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
            ) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color(0xFF00FF00)
                        )
                    }
                    uiState.ptySessions.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
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
                                onClick = { viewModel.createNewSession() },
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
                    else -> {
                        SelectionContainer {
                            Text(
                                text = uiState.outputBuffer,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .horizontalScroll(rememberScrollState())
                                    .padding(8.dp),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFF00FF00),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            SpecialKeysRow(
                enabled = uiState.selectedPtyId != null,
                onSpecialKey = viewModel::sendSpecialKey
            )

            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "$",
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = uiState.input,
                        onValueChange = viewModel::updateInput,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Enter command...") },
                        singleLine = true,
                        enabled = uiState.selectedPtyId != null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { viewModel.sendInput() }),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                    )
                    IconButton(
                        onClick = viewModel::sendInput,
                        enabled = uiState.input.isNotEmpty() && uiState.selectedPtyId != null
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
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
private fun SpecialKeysRow(
    enabled: Boolean,
    onSpecialKey: (SpecialKey) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SpecialKeyButton("Ctrl+C", enabled) { onSpecialKey(SpecialKey.CTRL_C) }
        SpecialKeyButton("Ctrl+D", enabled) { onSpecialKey(SpecialKey.CTRL_D) }
        SpecialKeyButton("Ctrl+Z", enabled) { onSpecialKey(SpecialKey.CTRL_Z) }
        SpecialKeyButton("Tab", enabled) { onSpecialKey(SpecialKey.TAB) }
        SpecialKeyButton("↑", enabled) { onSpecialKey(SpecialKey.ARROW_UP) }
        SpecialKeyButton("↓", enabled) { onSpecialKey(SpecialKey.ARROW_DOWN) }
        SpecialKeyButton("←", enabled) { onSpecialKey(SpecialKey.ARROW_LEFT) }
        SpecialKeyButton("→", enabled) { onSpecialKey(SpecialKey.ARROW_RIGHT) }
    }
}

@Composable
private fun SpecialKeyButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace
        )
    }
}
