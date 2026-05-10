package dev.blazelight.p4oc.ui.screens.chat

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.SessionConnectionState
import dev.blazelight.p4oc.ui.components.chat.ChatInputBar
import dev.blazelight.p4oc.ui.components.chat.FilePickerDialog
import dev.blazelight.p4oc.ui.components.chat.JumpToBottomButton
import dev.blazelight.p4oc.ui.components.chat.ModelAgentSelectorBar
import dev.blazelight.p4oc.ui.components.chat.QueuedMessagesStrip
import dev.blazelight.p4oc.ui.components.command.CommandPalette
import dev.blazelight.p4oc.ui.components.question.InlineQuestionCard
import dev.blazelight.p4oc.ui.components.todo.TodoTrackerSheet
import dev.blazelight.p4oc.ui.components.toolwidgets.ToolWidgetState
import dev.blazelight.p4oc.ui.components.TuiDropdownMenuItem
import dev.blazelight.p4oc.ui.components.TuiTopBar
import dev.blazelight.p4oc.ui.components.TuiConfirmDialog
import dev.blazelight.p4oc.ui.components.TuiLoadingScreen
import dev.blazelight.p4oc.ui.components.TuiSnackbar
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import dev.blazelight.p4oc.ui.screens.files.upload.ContentResolverUploadSource
import dev.blazelight.p4oc.ui.screens.files.upload.UploadProgressSheet
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
    onViewSessionDiff: ((String) -> Unit)? = null,
    onOpenSubSession: ((String) -> Unit)? = null,
    onSessionLoaded: ((sessionId: String, sessionTitle: String) -> Unit)? = null,
    onConnectionStateChanged: ((SessionConnectionState?) -> Unit)? = null,
    isActiveTab: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val branchName by viewModel.branchName.collectAsStateWithLifecycle()
    val sessionConnectionState by viewModel.sessionConnectionState.collectAsStateWithLifecycle()
    val visualSettings by viewModel.visualSettings.collectAsStateWithLifecycle()

    // Sub-manager state
    val pendingQuestion by viewModel.dialogManager.pendingQuestion.collectAsStateWithLifecycle()
    val pendingPermissionsByCallId by viewModel.dialogManager.pendingPermissionsByCallId.collectAsStateWithLifecycle()
    val availableAgents by viewModel.modelAgentManager.availableAgents.collectAsStateWithLifecycle()
    val selectedAgent by viewModel.modelAgentManager.selectedAgent.collectAsStateWithLifecycle()
    val availableModels by viewModel.modelAgentManager.availableModels.collectAsStateWithLifecycle()
    val selectedModel by viewModel.modelAgentManager.selectedModel.collectAsStateWithLifecycle()
    val favoriteModels by viewModel.modelAgentManager.favoriteModels.collectAsStateWithLifecycle()
    val recentModels by viewModel.modelAgentManager.recentModels.collectAsStateWithLifecycle()
    val attachedFiles by viewModel.filePickerManager.attachedFiles.collectAsStateWithLifecycle()
    val pickerFiles by viewModel.filePickerManager.pickerFiles.collectAsStateWithLifecycle()
    val pickerCurrentPath by viewModel.filePickerManager.pickerCurrentPath.collectAsStateWithLifecycle()
    val isPickerLoading by viewModel.filePickerManager.isPickerLoading.collectAsStateWithLifecycle()
    val pickerError by viewModel.filePickerManager.pickerError.collectAsStateWithLifecycle()
    val uploadState by viewModel.filePickerManager.uploadState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uploadSource = remember(context) {
        ContentResolverUploadSource(context.applicationContext.contentResolver)
    }
    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            viewModel.filePickerManager.uploadAndAttach(uploadSource, uris.map { it.toString() })
        }
    }
    
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
    var showRevertDialog by remember { mutableStateOf<String?>(null) }
    
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
                // Smooth scroll for small distances, instant for large jumps
                if (listState.firstVisibleItemIndex < 3) {
                    listState.animateScrollToItem(0)  // Smooth when close to bottom
                } else {
                    listState.scrollToItem(0)  // Instant for large jumps
                }
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
                onViewChanges = {
                    uiState.session?.id?.let { onViewSessionDiff?.invoke(it) }
                },
                branchName = branchName,
                todoCount = uiState.todos.count { it.status == "in_progress" || it.status == "pending" },
                onTodos = {
                    viewModel.loadTodos()
                    showTodoTracker = true
                }
            )
        },
        bottomBar = {
            // Sub-agent sessions are read-only — hide input bar and model selector
            val isSubAgent = uiState.session?.parentID != null
            if (!isSubAgent) {
                Column(
                    modifier = Modifier
                        .imePadding()
                        .navigationBarsPadding()
                ) {
                    QueuedMessagesStrip(
                        queuedMessages = uiState.queuedMessages,
                        onCancel = viewModel::cancelQueuedMessage
                    )
                    ModelAgentSelectorBar(
                        availableAgents = availableAgents,
                        selectedAgent = selectedAgent,
                        onAgentSelected = viewModel.modelAgentManager::selectAgent,
                        availableModels = availableModels,
                        selectedModel = selectedModel,
                        onModelSelected = viewModel.modelAgentManager::selectModel,
                        favoriteModels = favoriteModels,
                        recentModels = recentModels,
                        onToggleFavorite = viewModel.modelAgentManager::toggleFavoriteModel
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
                        enabled = connectionState is ConnectionState.Connected,
                        isBusy = uiState.isBusy,
                        queuedCount = uiState.queuedMessages.size,
                        onQueueMessage = viewModel::queueMessage,
                        onAbort = viewModel::abortSession,
                        attachedFiles = attachedFiles,
                        onAttachClick = {
                            viewModel.filePickerManager.loadPickerFiles()
                            showFilePicker = true
                        },
                        onRemoveAttachment = viewModel.filePickerManager::detachFile,
                        commands = uiState.commands,
                        onCommandSelected = { /* Command text is already updated via onValueChange */ },
                        requestFocus = isActiveTab
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Revert active banner
            uiState.session?.revert?.let {
                val theme = LocalOpenCodeTheme.current
                Surface(
                    color = theme.warning.copy(alpha = 0.15f),
                    shape = RectangleShape
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\u21BA ${stringResource(R.string.revert_active_banner)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = theme.warning
                        )
                        Text(
                            text = "[${stringResource(R.string.unrevert_all)}]",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                            color = theme.accent,
                            modifier = Modifier.clickable(role = Role.Button) { viewModel.unrevertSession() }
                        )
                    }
                }
            }

            val hasContent = messages.isNotEmpty() || uiState.isBusy
            
            if (!hasContent && !uiState.isLoading) {
                EmptyChatView(modifier = Modifier.align(Alignment.Center))
            } else {
                val messageBlocks = remember(messages) {
                    groupMessagesIntoBlocks(messages)
                }
                
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("message_list"),
                    contentPadding = PaddingValues(vertical = Spacing.xxs, horizontal = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.hairline),
                    reverseLayout = true
                ) {
                    // Inline question card at the bottom (top in reversed layout)
                    pendingQuestion?.let { questionRequest ->
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
                            pendingPermissionsByCallId = pendingPermissionsByCallId,
                            onRevert = { messageId -> showRevertDialog = messageId }
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                TuiLoadingScreen(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            uiState.error?.let { error ->
                TuiSnackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(Spacing.md),
                    action = {
                        TextButton(onClick = viewModel::clearError, shape = RectangleShape) {
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
                        listState.scrollToItem(0)
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
            files = pickerFiles,
            currentPath = pickerCurrentPath,
            isLoading = isPickerLoading,
            error = pickerError,
            selectedFiles = attachedFiles,
            onUploadClick = { uploadLauncher.launch(arrayOf("*/*")) },
            onNavigateTo = { path -> viewModel.filePickerManager.loadPickerFiles(path.ifBlank { "." }) },
            onNavigateUp = {
                val parent = pickerCurrentPath.substringBeforeLast("/", "")
                viewModel.filePickerManager.loadPickerFiles(parent.ifBlank { "." })
            },
            onFileSelected = { viewModel.filePickerManager.attachFile(it) },
            onFileDeselected = { viewModel.filePickerManager.detachFile(it) },
            onConfirm = { showFilePicker = false },
            onDismiss = { showFilePicker = false }
        )
    }

    if (!uploadState.isEmpty) {
        UploadProgressSheet(
            state = uploadState,
            onCancel = { viewModel.filePickerManager.cancelUploads() },
            onDismiss = { viewModel.filePickerManager.dismissUploadResult() },
            onRetryFailed = { viewModel.filePickerManager.retryFailedUploads() },
        )
    }

    showRevertDialog?.let { messageId ->
        TuiConfirmDialog(
            onDismissRequest = { showRevertDialog = null },
            onConfirm = { viewModel.revertMessage(messageId) },
            title = stringResource(R.string.revert_confirm_title),
            message = stringResource(R.string.revert_confirm_message),
            confirmText = stringResource(R.string.revert_changes),
            dismissText = stringResource(R.string.button_cancel),
            isDestructive = true
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
    onViewChanges: () -> Unit,
    branchName: String? = null,
    todoCount: Int = 0,
    onTodos: () -> Unit = {}
) {
    val theme = LocalOpenCodeTheme.current
    var showOverflow by remember { mutableStateOf(false) }

    TuiTopBar(
        title = title,
        onNavigateBack = onBack,
        actions = {
            // Compact status: connection dot + branch (no 40dp boxes)
            ConnectionDot(state = connectionState)
            branchName?.let { branch ->
                Text(
                    text = "${stringResource(R.string.vcs_branch_prefix)} $branch",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = theme.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = Sizing.panelWidthSm)  // 80dp — tighter
                        .padding(start = Spacing.xxs)
                )
            }

            Spacer(Modifier.width(Spacing.xs))
            // Todo count — only when there are todos (TUI glyph, no rounded badge)
            if (todoCount > 0) {
                IconButton(
                    onClick = onTodos,
                    modifier = Modifier.size(Sizing.iconButtonMd)
                ) {
                    Text(
                        text = "☐$todoCount",
                        color = theme.accent,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Single overflow glyph — navigation actions collapse into menu
            Box {
                IconButton(
                    onClick = { showOverflow = true },
                    modifier = Modifier.size(Sizing.iconButtonMd).testTag("chat_overflow_button")
                ) {
                    Text(
                        text = "≡",
                        color = theme.accent,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                DropdownMenu(
                    expanded = showOverflow,
                    onDismissRequest = { showOverflow = false }
                ) {
                    TuiDropdownMenuItem(
                        text = "± ${stringResource(R.string.sessions_view_changes)}",
                        onClick = { showOverflow = false; onViewChanges() }
                    )
                    TuiDropdownMenuItem(
                        text = "/ ${stringResource(R.string.cd_commands)}",
                        onClick = { showOverflow = false; onCommands() }
                    )
                    TuiDropdownMenuItem(
                        text = ">_ ${stringResource(R.string.cd_terminal)}",
                        onClick = { showOverflow = false; onTerminal() }
                    )
                    TuiDropdownMenuItem(
                        text = "▤ ${stringResource(R.string.cd_files)}",
                        onClick = { showOverflow = false; onFiles() }
                    )
                }
            }
        }
    )
}

/**
 * Compact connection dot for the title subtitle row — just a colored text glyph.
 * No bounding box or dropdown.
 */
@Composable
private fun ConnectionDot(state: ConnectionState) {
    val theme = LocalOpenCodeTheme.current
    val color = when (state) {
        ConnectionState.Connected -> theme.success
        ConnectionState.Connecting -> theme.warning
        ConnectionState.Disconnected -> theme.textMuted
        is ConnectionState.Error -> theme.error
    }
    Text(
        text = "●",
        color = color,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.labelSmall
    )
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
