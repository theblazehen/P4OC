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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.Permission
import dev.blazelight.p4oc.ui.components.chat.ChatInputBar
import dev.blazelight.p4oc.ui.components.chat.ChatMessage
import dev.blazelight.p4oc.ui.components.chat.FilePickerDialog
import dev.blazelight.p4oc.ui.components.chat.JumpToBottomButton
import dev.blazelight.p4oc.ui.components.chat.ModelAgentSelectorBar
import dev.blazelight.p4oc.ui.components.chat.PermissionDialogEnhanced
import dev.blazelight.p4oc.ui.components.command.CommandPalette
import dev.blazelight.p4oc.ui.components.question.InlineQuestionCard
import dev.blazelight.p4oc.ui.components.todo.TodoTrackerFab
import dev.blazelight.p4oc.ui.components.todo.TodoTrackerSheet
import dev.blazelight.p4oc.ui.components.toolwidgets.ToolWidgetState
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val favoriteModels by viewModel.favoriteModels.collectAsStateWithLifecycle()
    val recentModels by viewModel.recentModels.collectAsStateWithLifecycle()
    val visualSettings by viewModel.visualSettings.collectAsStateWithLifecycle()
    
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
    val lastMessageContent = remember(messages) {
        messages.firstOrNull()?.parts?.sumOf { it.hashCode() } ?: 0
    }
    
    LaunchedEffect(messages.size, lastMessageContent) {
        if (messages.isNotEmpty()) {
            if (!userScrolledAway) {
                listState.scrollToItem(0)  // In reversed layout, 0 is bottom
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
                // Group consecutive messages into blocks for display
                // User messages are their own block, consecutive assistant messages merge into one block
                val messageBlocks = remember(messages) {
                    groupMessagesIntoBlocks(messages)
                }
                
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 2.dp, horizontal = 4.dp),
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
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                        
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
                                onToolApprove = { viewModel.respondToPermission(it, "allow") },
                                onToolDeny = { viewModel.respondToPermission(it, "deny") },
                                defaultToolWidgetState = defaultToolWidgetState
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
                PermissionDialogEnhanced(
                    permission = permission,
                    onAllow = { viewModel.respondToPermission(permission.id, "allow") },
                    onDeny = { viewModel.respondToPermission(permission.id, "deny") },
                    onAlways = { viewModel.respondToPermission(permission.id, "always") }
                )
            }

            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp),
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
                    contentDescription = stringResource(R.string.cd_back),
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
                        contentDescription = stringResource(R.string.cd_stop),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            IconButton(
                onClick = onCommands,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Code, contentDescription = stringResource(R.string.cd_commands), modifier = Modifier.size(22.dp))
            }
            IconButton(
                onClick = onTerminal,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Terminal, contentDescription = stringResource(R.string.cd_terminal), modifier = Modifier.size(22.dp))
            }
            IconButton(
                onClick = onFiles,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.cd_files), modifier = Modifier.size(22.dp))
            }
            IconButton(
                onClick = onGit,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.AccountTree, contentDescription = stringResource(R.string.cd_git), modifier = Modifier.size(22.dp))
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
        modifier = modifier.padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.ChatBubbleOutline,
            contentDescription = stringResource(R.string.chat_empty_title),
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Text(
            text = stringResource(R.string.chat_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.chat_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Sealed class representing a block of messages for display.
 * User messages are their own block. Consecutive assistant messages are merged.
 */
private sealed class MessageBlock {
    data class UserBlock(val message: MessageWithParts) : MessageBlock()
    data class AssistantBlock(val messages: List<MessageWithParts>) : MessageBlock()
}

/**
 * Group messages into blocks: user messages standalone, consecutive assistant messages merged.
 */
private fun groupMessagesIntoBlocks(messages: List<MessageWithParts>): List<MessageBlock> {
    if (messages.isEmpty()) return emptyList()
    
    val blocks = mutableListOf<MessageBlock>()
    var i = 0
    
    while (i < messages.size) {
        val current = messages[i]
        
        if (current.message is Message.User) {
            blocks.add(MessageBlock.UserBlock(current))
            i++
        } else {
            // Collect consecutive assistant messages
            val assistantMessages = mutableListOf<MessageWithParts>()
            while (i < messages.size && messages[i].message is Message.Assistant) {
                assistantMessages.add(messages[i])
                i++
            }
            blocks.add(MessageBlock.AssistantBlock(assistantMessages))
        }
    }
    
    return blocks
}

/**
 * Render a message block (either user or merged assistant messages)
 */
@Composable
private fun MessageBlockView(
    block: MessageBlock,
    onToolApprove: (String) -> Unit,
    onToolDeny: (String) -> Unit,
    defaultToolWidgetState: ToolWidgetState = ToolWidgetState.COMPACT
) {
    when (block) {
        is MessageBlock.UserBlock -> {
            ChatMessage(
                messageWithParts = block.message,
                onToolApprove = onToolApprove,
                onToolDeny = onToolDeny,
                defaultToolWidgetState = defaultToolWidgetState
            )
        }
        is MessageBlock.AssistantBlock -> {
            // Merge all parts from all messages, preserving order
            val allParts = block.messages.flatMap { it.parts }
            
            // Create a synthetic merged message for display
            // Use the first message as the base
            val mergedMessageWithParts = MessageWithParts(
                message = block.messages.first().message,
                parts = allParts
            )
            
            ChatMessage(
                messageWithParts = mergedMessageWithParts,
                onToolApprove = onToolApprove,
                onToolDeny = onToolDeny,
                defaultToolWidgetState = defaultToolWidgetState
            )
        }
    }
}
