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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.Permission
import dev.blazelight.p4oc.domain.model.SessionConnectionState
import dev.blazelight.p4oc.ui.components.chat.ChatInputBar
import dev.blazelight.p4oc.ui.components.chat.ChatMessage
import dev.blazelight.p4oc.ui.components.chat.FilePickerDialog
import dev.blazelight.p4oc.ui.components.chat.JumpToBottomButton
import dev.blazelight.p4oc.ui.components.chat.ModelAgentSelectorBar
import dev.blazelight.p4oc.ui.components.chat.PermissionDialogEnhanced
import dev.blazelight.p4oc.ui.components.command.CommandPalette
import dev.blazelight.p4oc.ui.components.question.InlineQuestionCard
import dev.blazelight.p4oc.ui.components.todo.TodoTrackerSheet
import dev.blazelight.p4oc.ui.components.toolwidgets.ToolWidgetState
import dev.blazelight.p4oc.ui.components.TuiTopBar
import dev.blazelight.p4oc.ui.components.TuiLoadingScreen
import kotlinx.coroutines.launch
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenSubSession: ((String) -> Unit)? = null,
    onSessionLoaded: ((sessionId: String, sessionTitle: String) -> Unit)? = null,
    onConnectionStateChanged: ((SessionConnectionState?) -> Unit)? = null,
    isActiveTab: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val sessionConnectionState by viewModel.sessionConnectionState.collectAsStateWithLifecycle()
    val favoriteModels by viewModel.favoriteModels.collectAsStateWithLifecycle()
    val recentModels by viewModel.recentModels.collectAsStateWithLifecycle()
    val visualSettings by viewModel.visualSettings.collectAsStateWithLifecycle()
    
    // Notify parent when session is loaded
    LaunchedEffect(uiState.session) {
        uiState.session?.let { session ->
            onSessionLoaded?.invoke(session.id, session.title)
        }
    }
    
    // Propagate connection state changes to parent (for tab indicator)
    LaunchedEffect(sessionConnectionState) {
        onConnectionStateChanged?.invoke(sessionConnectionState)
    }
    
    // Mark as read when tab becomes active
    LaunchedEffect(isActiveTab) {
        if (isActiveTab) {
            viewModel.markAsRead()
        }
    }
    
    // Convert setting string to ToolWidgetState
    val defaultToolWidgetState = remember(visualSettings.toolWidgetDefaultState) {
        ToolWidgetState.fromString(visualSettings.toolWidgetDefaultState)
    }

    val listState = rememberLazyListState()
    var showCommandPalette by remember { mutableStateOf(false) }
    var showTodoTracker by remember { mutableStateOf(false) }
    var showFilePicker by remember { mutableStateOf(false) }
    
    // Scroll UX state
    var userScrolledAway by remember { mutableStateOf(false) }
    var hasNewContentWhileAway by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    // Derived state: check if user is "at bottom" (reversed layout: index 0 is bottom)
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            layoutInfo.totalItemsCount == 0 || 
                (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 100)
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
    val messageCount = messages.size
    val lastMessagePartCount = messages.lastOrNull()?.parts?.size ?: 0
    val isBusy = uiState.isBusy
    
    // Scroll on new messages or when parts are added to the last message
    LaunchedEffect(messageCount, lastMessagePartCount, isBusy) {
        if (messages.isNotEmpty()) {
            if (!userScrolledAway) {
                listState.scrollToItem(0)  // In reversed layout, 0 is bottom
            } else {
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
                onCommands = {
                    viewModel.loadCommands()
                    showCommandPalette = true
                },
                onAbort = viewModel::abortSession,
                isBusy = uiState.isBusy,
                todoCount = uiState.todos.count { it.status == "in_progress" || it.status == "pending" },
                onTodos = {
                    viewModel.loadTodos()
                    showTodoTracker = true
                }
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
                    onValueChange = { text ->
                        viewModel.updateInput(text)
                        // Load commands when user starts typing /
                        if (text.startsWith("/") && uiState.commands.isEmpty()) {
                            viewModel.loadCommands()
                        }
                    },
                    onSend = viewModel::sendMessage,
                    isLoading = uiState.isSending,
                    enabled = connectionState is ConnectionState.Connected,  // Keep input enabled while sending
                    isBusy = uiState.isBusy,
                    hasQueuedMessage = uiState.queuedMessage != null,
                    onQueueMessage = viewModel::queueMessage,
                    attachedFiles = uiState.attachedFiles,
                    onAttachClick = {
                        viewModel.loadPickerFiles()
                        showFilePicker = true
                    },
                    onRemoveAttachment = viewModel::detachFile,
                    commands = uiState.commands,
                    onCommandSelected = { /* Command text is already updated via onValueChange */ },
                    requestFocus = isActiveTab
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Use isBusy as a proxy for "has content or is loading"
            // We don't observe streamingState here to avoid recomposition
            val hasContent = messages.isNotEmpty() || uiState.isBusy
            
            if (!hasContent && !uiState.isLoading) {
                EmptyChatView(modifier = Modifier.align(Alignment.Center))
            } else {
                // Group consecutive messages into blocks for display
                // User messages are their own block, consecutive assistant messages merge into one block
                // This only recomputes when the messages list changes (not during streaming)
                val messageBlocks = remember(messages) {
                    groupMessagesIntoBlocks(messages)
                }
                
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = Spacing.xxs, horizontal = Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                        reverseLayout = true
                    ) {
                        // Inline question card at the bottom (top in reversed layout)
                        uiState.pendingQuestion?.let { questionRequest ->
                            item(key = "pending_question_${questionRequest.id}") {
                                InlineQuestionCard(
                                    questionData = dev.blazelight.p4oc.domain.model.QuestionData(questionRequest.questions),
                                    onDismiss = viewModel::dismissQuestion,
                                    onSubmit = { answers ->
                                        viewModel.respondToQuestion(questionRequest.id, answers)
                                    },
                                    modifier = Modifier.padding(vertical = Spacing.xs)
                                )
                            }
                        }
                        
                        // All messages - stable keys ensure only changed items recompose
                        items(
                            items = messageBlocks.asReversed(),
                            key = { block -> 
                                when (block) {
                                    is MessageBlock.UserBlock -> block.message.message.id
                                    is MessageBlock.AssistantBlock -> block.messages.first().message.id
                                }
                            }
                        ) { block ->
                            MessageBlockView(
                                block = block,
                                onToolApprove = { viewModel.respondToPermission(it, "once") },
                                onToolDeny = { viewModel.respondToPermission(it, "reject") },
                                onToolAlways = { viewModel.respondToPermission(it, "always") },
                                onOpenSubSession = onOpenSubSession,
                                defaultToolWidgetState = defaultToolWidgetState,
                                pendingPermissionsByCallId = uiState.pendingPermissionsByCallId
                            )
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                TuiLoadingScreen(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (connectionState !is ConnectionState.Connected) {
                ConnectionBanner(
                    state = connectionState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            // Inline permission handling via tool widgets - no modal dialog needed
            // uiState.pendingPermission?.let { permission ->
            //     PermissionDialogEnhanced(
            //         permission = permission,
            //         onAllow = { viewModel.respondToPermission(permission.id, "once") },
            //         onDeny = { viewModel.respondToPermission(permission.id, "reject") },
            //         onAlways = { viewModel.respondToPermission(permission.id, "always") }
            //     )
            // }

            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(Spacing.md),
                    action = {
                        TextButton(onClick = viewModel::clearError) {
                            Text(stringResource(R.string.dismiss))
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
                        listState.scrollToItem(0)  // In reversed layout, 0 is bottom
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Spacing.xl, bottom = Spacing.md)
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
    onCommands: () -> Unit,
    onAbort: () -> Unit,
    isBusy: Boolean,
    todoCount: Int = 0,
    onTodos: () -> Unit = {}
) {
    val theme = LocalOpenCodeTheme.current
    TuiTopBar(
        title = title,
        onNavigateBack = onBack,
        actions = {
            ConnectionIndicator(state = connectionState)
            
            Spacer(Modifier.width(Spacing.md))
            
            if (isBusy) {
                IconButton(
                    onClick = onAbort,
                    modifier = Modifier.size(Sizing.iconButtonMd)
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = stringResource(R.string.cd_stop),
                        tint = theme.error,
                        modifier = Modifier.size(Sizing.iconAction)
                    )
                }
            }
            
            // Todo button with badge
            if (todoCount > 0) {
                IconButton(
                    onClick = onTodos,
                    modifier = Modifier.size(Sizing.iconButtonMd)
                ) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = theme.accent
                            ) {
                                Text(todoCount.toString())
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Checklist,
                            contentDescription = stringResource(R.string.cd_todos),
                            modifier = Modifier.size(Sizing.iconAction)
                        )
                    }
                }
            }
            
            IconButton(
                onClick = onCommands,
                modifier = Modifier.size(Sizing.iconButtonMd)
            ) {
                Icon(Icons.Default.Code, contentDescription = stringResource(R.string.cd_commands), modifier = Modifier.size(Sizing.iconAction))
            }
            IconButton(
                onClick = onTerminal,
                modifier = Modifier.size(Sizing.iconButtonMd)
            ) {
                Icon(Icons.Default.Terminal, contentDescription = stringResource(R.string.cd_terminal), modifier = Modifier.size(Sizing.iconAction))
            }
            IconButton(
                onClick = onFiles,
                modifier = Modifier.size(Sizing.iconButtonMd)
            ) {
                Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.cd_files), modifier = Modifier.size(Sizing.iconAction))
            }
        }
    )
}

@Composable
private fun ConnectionIndicator(state: ConnectionState) {
    val theme = LocalOpenCodeTheme.current
    val (color, description) = when (state) {
        ConnectionState.Connected -> theme.success to "Connected"
        ConnectionState.Connecting -> theme.warning to "Connecting"
        ConnectionState.Disconnected -> theme.textMuted to "Disconnected"
        is ConnectionState.Error -> theme.error to "Error"
    }

    Icon(
        Icons.Default.Circle,
        contentDescription = description,
        tint = color,
        modifier = Modifier.size(Sizing.iconXxs)
    )
}

@Composable
private fun ConnectionBanner(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    val (text, color) = when (state) {
        ConnectionState.Connecting -> "Connecting..." to theme.warning.copy(alpha = 0.2f)
        ConnectionState.Disconnected -> "Disconnected" to theme.error.copy(alpha = 0.2f)
        is ConnectionState.Error -> "Connection error: ${state.message}" to theme.error.copy(alpha = 0.2f)
        ConnectionState.Connected -> return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = color
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(Spacing.md),
            style = MaterialTheme.typography.bodySmall,
            color = theme.text
        )
    }
}

@Composable
private fun EmptyChatView(modifier: Modifier = Modifier) {
    val theme = LocalOpenCodeTheme.current
    Column(
        modifier = modifier.padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            text = "◇",
            style = MaterialTheme.typography.displayMedium,
            color = theme.textMuted
        )
        Text(
            text = stringResource(R.string.chat_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = theme.text
        )
        Text(
            text = stringResource(R.string.chat_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textMuted
        )
    }
}

// MessageBlock, groupMessagesIntoBlocks, and MessageBlockView are now in MessageBlockUtils.kt
