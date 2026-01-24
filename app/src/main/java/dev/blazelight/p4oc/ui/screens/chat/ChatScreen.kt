package dev.blazelight.p4oc.ui.screens.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.Permission
import dev.blazelight.p4oc.ui.components.chat.ChatInputBar
import dev.blazelight.p4oc.ui.components.chat.ChatMessage
import dev.blazelight.p4oc.ui.components.chat.FilePickerDialog
import dev.blazelight.p4oc.ui.components.chat.JumpToBottomButton
import dev.blazelight.p4oc.ui.components.chat.ModelAgentSelectorBar
import dev.blazelight.p4oc.ui.components.chat.PermissionDialog
import dev.blazelight.p4oc.ui.components.command.CommandPalette
import dev.blazelight.p4oc.ui.components.question.QuestionDialog
import dev.blazelight.p4oc.ui.components.todo.TodoTrackerFab
import dev.blazelight.p4oc.ui.components.todo.TodoTrackerSheet
import kotlinx.coroutines.launch

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
    val favoriteModels by viewModel.favoriteModels.collectAsState()
    val recentModels by viewModel.recentModels.collectAsState()

    val listState = rememberLazyListState()
    var showCommandPalette by remember { mutableStateOf(false) }
    var showTodoTracker by remember { mutableStateOf(false) }
    var showFilePicker by remember { mutableStateOf(false) }
    
    // Scroll UX state
    var userScrolledAway by remember { mutableStateOf(false) }
    var hasNewContentWhileAway by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    // Derived state: check if user is "at bottom" (within 2 items of end)
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount
            totalItems == 0 || lastVisibleItem >= totalItems - 2 || !listState.canScrollForward
        }
    }
    
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    
    BackHandler {
        focusManager.clearFocus()
        keyboardController?.hide()
        onNavigateBack()
    }

    // Detect user scroll gesture - when scrolling and not at bottom, mark as scrolled away
    LaunchedEffect(listState.isScrollInProgress, isAtBottom) {
        if (listState.isScrollInProgress && !isAtBottom) {
            userScrolledAway = true
        }
        // Reset when user manually scrolls back to bottom
        if (isAtBottom && !listState.isScrollInProgress && userScrolledAway) {
            userScrolledAway = false
            hasNewContentWhileAway = false
        }
    }

    // Auto-scroll when new messages arrive or content changes during streaming
    val lastMessageContent = remember(messages) {
        messages.lastOrNull()?.parts?.sumOf { it.hashCode() } ?: 0
    }
    
    LaunchedEffect(messages.size, lastMessageContent) {
        if (messages.isNotEmpty()) {
            if (!userScrolledAway) {
                listState.animateScrollToItem(messages.size - 1)
            } else {
                // Track that new content arrived while user was scrolled away
                hasNewContentWhileAway = true
            }
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
            Column(
                modifier = Modifier
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                ModelAgentSelectorBar(
                    availableAgents = uiState.availableAgents,
                    selectedAgent = uiState.selectedAgent,
                    onAgentSelected = viewModel::selectAgent,
                    availableModels = uiState.availableModels,
                    selectedModel = uiState.selectedModel,
                    onModelSelected = viewModel::selectModel,
                    favoriteModels = favoriteModels,
                    recentModels = recentModels,
                    onToggleFavorite = viewModel::toggleFavoriteModel
                )
                ChatInputBar(
                    value = uiState.inputText,
                    onValueChange = viewModel::updateInput,
                    onSend = viewModel::sendMessage,
                    isLoading = uiState.isSending,
                    enabled = connectionState is ConnectionState.Connected && !uiState.isSending,
                    attachedFiles = uiState.attachedFiles,
                    onAttachClick = {
                        viewModel.loadPickerFiles()
                        showFilePicker = true
                    },
                    onRemoveAttachment = viewModel::detachFile
                )
            }
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
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                    questionData = dev.blazelight.p4oc.domain.model.QuestionData(questionRequest.questions),
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
            
            // Jump to bottom button - shows when scrolled away during streaming
            JumpToBottomButton(
                visible = userScrolledAway && uiState.isBusy,
                hasNewContent = hasNewContentWhileAway,
                onClick = {
                    coroutineScope.launch {
                        userScrolledAway = false
                        hasNewContentWhileAway = false
                        listState.animateScrollToItem(messages.size - 1)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 8.dp)
            )
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

    if (showFilePicker) {
        FilePickerDialog(
            files = uiState.pickerFiles,
            currentPath = uiState.pickerCurrentPath,
            isLoading = uiState.isPickerLoading,
            selectedFiles = uiState.attachedFiles,
            onNavigateTo = { path -> viewModel.loadPickerFiles(path.ifBlank { "." }) },
            onNavigateUp = {
                val parent = uiState.pickerCurrentPath.substringBeforeLast("/", "")
                viewModel.loadPickerFiles(parent.ifBlank { "." })
            },
            onFileSelected = { viewModel.attachFile(it) },
            onFileDeselected = { viewModel.detachFile(it) },
            onConfirm = { showFilePicker = false },
            onDismiss = { showFilePicker = false }
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
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Text(
                text = title,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            
            ConnectionIndicator(state = connectionState)
            
            Spacer(Modifier.width(8.dp))
            
            if (isBusy) {
                IconButton(
                    onClick = onAbort,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            IconButton(
                onClick = onCommands,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Code, contentDescription = "Commands", modifier = Modifier.size(22.dp))
            }
            IconButton(
                onClick = onTerminal,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Terminal, contentDescription = "Terminal", modifier = Modifier.size(22.dp))
            }
            IconButton(
                onClick = onFiles,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Folder, contentDescription = "Files", modifier = Modifier.size(22.dp))
            }
            IconButton(
                onClick = onGit,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.AccountTree, contentDescription = "Git", modifier = Modifier.size(22.dp))
            }
        }
    }
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
        modifier = Modifier.size(10.dp)
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
