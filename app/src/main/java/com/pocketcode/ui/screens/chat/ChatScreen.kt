package com.pocketcode.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pocketcode.core.network.ConnectionState
import com.pocketcode.domain.model.MessageWithParts
import com.pocketcode.domain.model.Permission
import com.pocketcode.ui.components.chat.ChatInputBar
import com.pocketcode.ui.components.chat.ChatMessage
import com.pocketcode.ui.components.chat.PermissionDialog
import com.pocketcode.ui.components.command.CommandPalette
import com.pocketcode.ui.components.question.QuestionDialog
import com.pocketcode.ui.components.todo.TodoTrackerFab
import com.pocketcode.ui.components.todo.TodoTrackerSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenGit: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    val listState = rememberLazyListState()
    var showCommandPalette by remember { mutableStateOf(false) }
    var showTodoTracker by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                title = uiState.session?.title ?: "Chat",
                connectionState = connectionState,
                onBack = onNavigateBack,
                onTerminal = onOpenTerminal,
                onFiles = onOpenFiles,
                onGit = onOpenGit,
                onCommands = {
                    viewModel.loadCommands()
                    showCommandPalette = true
                },
                onAbort = viewModel::abortSession,
                isBusy = uiState.isBusy
            )
        },
        bottomBar = {
            ChatInputBar(
                value = uiState.inputText,
                onValueChange = viewModel::updateInput,
                onSend = viewModel::sendMessage,
                isLoading = uiState.isSending,
                enabled = connectionState is ConnectionState.Connected && !uiState.isSending
            )
        },
        floatingActionButton = {
            TodoTrackerFab(
                todos = uiState.todos,
                onClick = {
                    viewModel.loadTodos()
                    showTodoTracker = true
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (messages.isEmpty() && !uiState.isLoading) {
                EmptyChatView(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = messages,
                        key = { it.message.id }
                    ) { messageWithParts ->
                        ChatMessage(
                            messageWithParts = messageWithParts,
                            onToolApprove = { viewModel.respondToPermission(it, "allow") },
                            onToolDeny = { viewModel.respondToPermission(it, "deny") }
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (connectionState !is ConnectionState.Connected) {
                ConnectionBanner(
                    state = connectionState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            uiState.pendingPermission?.let { permission ->
                PermissionDialog(
                    permission = permission,
                    onAllow = { viewModel.respondToPermission(permission.id, "allow") },
                    onDeny = { viewModel.respondToPermission(permission.id, "deny") },
                    onAlways = { viewModel.respondToPermission(permission.id, "always") }
                )
            }

            uiState.pendingQuestion?.let { questionRequest ->
                QuestionDialog(
                    questionData = com.pocketcode.domain.model.QuestionData(questionRequest.questions),
                    onDismiss = viewModel::dismissQuestion,
                    onSubmit = { answers ->
                        viewModel.respondToQuestion(questionRequest.id, answers)
                    }
                )
            }

            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
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

    if (showCommandPalette) {
        CommandPalette(
            commands = uiState.commands,
            isLoading = uiState.isLoadingCommands,
            onCommandSelected = { command, args ->
                viewModel.executeCommand(command.name, args)
            },
            onDismiss = { showCommandPalette = false }
        )
    }

    if (showTodoTracker) {
        TodoTrackerSheet(
            todos = uiState.todos,
            isLoading = uiState.isLoadingTodos,
            onDismiss = { showTodoTracker = false },
            onRefresh = { viewModel.loadTodos() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    title: String,
    connectionState: ConnectionState,
    onBack: () -> Unit,
    onTerminal: () -> Unit,
    onFiles: () -> Unit,
    onGit: () -> Unit,
    onCommands: () -> Unit,
    onAbort: () -> Unit,
    isBusy: Boolean
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title)
                ConnectionIndicator(state = connectionState)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            if (isBusy) {
                IconButton(onClick = onAbort) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            IconButton(onClick = onCommands) {
                Icon(Icons.Default.Code, contentDescription = "Commands")
            }
            IconButton(onClick = onTerminal) {
                Icon(Icons.Default.Terminal, contentDescription = "Terminal")
            }
            IconButton(onClick = onFiles) {
                Icon(Icons.Default.Folder, contentDescription = "Files")
            }
            IconButton(onClick = onGit) {
                Icon(Icons.Default.AccountTree, contentDescription = "Git")
            }
        }
    )
}

@Composable
private fun ConnectionIndicator(state: ConnectionState) {
    val (color, description) = when (state) {
        ConnectionState.Connected -> MaterialTheme.colorScheme.primary to "Connected"
        ConnectionState.Connecting -> MaterialTheme.colorScheme.tertiary to "Connecting"
        ConnectionState.Disconnected -> MaterialTheme.colorScheme.outline to "Disconnected"
        is ConnectionState.Error -> MaterialTheme.colorScheme.error to "Error"
    }

    Icon(
        Icons.Default.Circle,
        contentDescription = description,
        tint = color,
        modifier = Modifier.size(8.dp)
    )
}

@Composable
private fun ConnectionBanner(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (state) {
        ConnectionState.Connecting -> "Connecting..." to MaterialTheme.colorScheme.tertiaryContainer
        ConnectionState.Disconnected -> "Disconnected" to MaterialTheme.colorScheme.errorContainer
        is ConnectionState.Error -> "Connection error: ${state.message}" to MaterialTheme.colorScheme.errorContainer
        ConnectionState.Connected -> return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = color
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun EmptyChatView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.Chat,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Text(
            text = "Start a conversation",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Type a message below to begin",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
