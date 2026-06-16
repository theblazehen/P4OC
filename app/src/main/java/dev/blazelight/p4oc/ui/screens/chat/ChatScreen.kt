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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.SessionConnectionState
import dev.blazelight.p4oc.domain.model.SessionPresence
import dev.blazelight.p4oc.ui.components.TuiConfirmDialog
import dev.blazelight.p4oc.ui.components.TuiDropdownMenuItem
import dev.blazelight.p4oc.ui.components.TuiLoadingScreen
import dev.blazelight.p4oc.ui.components.TuiSnackbar
import dev.blazelight.p4oc.ui.components.TuiTopBar
import dev.blazelight.p4oc.ui.components.chat.ChatInputBar
import dev.blazelight.p4oc.ui.components.chat.FilePickerDialog
import dev.blazelight.p4oc.ui.components.chat.JumpToBottomButton
import dev.blazelight.p4oc.ui.components.chat.ModelAgentSelectorBar
import dev.blazelight.p4oc.ui.components.chat.QueuedMessagesStrip
import dev.blazelight.p4oc.ui.components.command.CommandPalette
import dev.blazelight.p4oc.ui.components.question.InlineQuestionCard
import dev.blazelight.p4oc.ui.components.status.SessionStatusDot
import dev.blazelight.p4oc.ui.components.todo.TodoTrackerSheet
import dev.blazelight.p4oc.ui.components.toolwidgets.ToolWidgetState
import dev.blazelight.p4oc.ui.screens.files.upload.ContentResolverUploadSource
import dev.blazelight.p4oc.ui.screens.files.upload.UploadProgressSheet
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

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
    val chatSettings by viewModel.chatSettings.collectAsStateWithLifecycle()

    // Sub-manager state
    val pendingQuestion by viewModel.dialogManager.pendingQuestion.collectAsStateWithLifecycle()
    val pendingPermissionsByCallId by viewModel.dialogManager.pendingPermissionsByCallId.collectAsStateWithLifecycle()
    val availableAgents by viewModel.modelAgentManager.availableAgents.collectAsStateWithLifecycle()
    val selectedAgent by viewModel.modelAgentManager.selectedAgent.collectAsStateWithLifecycle()
    val availableModels by viewModel.modelAgentManager.availableModels.collectAsStateWithLifecycle()
    val selectedModel by viewModel.modelAgentManager.selectedModel.collectAsStateWithLifecycle()
    val selectedReasoningEffort by viewModel.modelAgentManager.selectedReasoningEffort.collectAsStateWithLifecycle()
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
        } else {
            viewModel.markInactive()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.sessionMissing.collect { onNavigateBack() }
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

    // Scroll UX state: follow new tail content only while the user remains pinned to bottom.
    var shouldFollowTail by remember(uiState.session?.id) { mutableStateOf(true) }
    var hasNewContentWhileAway by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Derived state: check if the bottom edge of the last rendered item is visible.
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
            val lastItemIndex = layoutInfo.totalItemsCount - 1
            val lastItemBottom = (lastVisible?.offset ?: 0) + (lastVisible?.size ?: 0)
            layoutInfo.totalItemsCount == 0 ||
                (
                    lastVisible != null &&
                        lastVisible.index >= lastItemIndex &&
                        lastItemBottom <= layoutInfo.viewportEndOffset
                )
        }
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    BackHandler {
        focusManager.clearFocus()
        keyboardController?.hide()
        onNavigateBack()
    }

    // Match the sticky follow-tail model: only update follow state after the user's scroll settles.
    LaunchedEffect(listState, uiState.session?.id) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { isScrolling ->
                if (!isScrolling) {
                    shouldFollowTail = isAtBottom
                    if (isAtBottom) hasNewContentWhileAway = false
                }
            }
    }

    // Auto-scroll when new messages arrive or content changes during streaming
    val messageCount = messages.size
    val tailMessage = messages.lastOrNull()
    val tailContentVersion = tailMessage?.parts?.sumOf { part ->
        when (part) {
            is Part.Text -> part.text.length
            is Part.Reasoning -> part.text.length
            else -> 1
        }
    } ?: 0
    val isBusy = uiState.isBusy
    val pendingQuestionId = pendingQuestion?.id

    // Scroll on new messages, new parts, or streaming text/reasoning growth.
    LaunchedEffect(messageCount, tailContentVersion, isBusy, pendingQuestionId) {
        if (messages.isNotEmpty() || pendingQuestionId != null) {
            if (shouldFollowTail) {
                val lastIndex = (messageCount - 1).coerceAtLeast(0) + if (pendingQuestion != null) 1 else 0
                val layoutInfo = listState.layoutInfo
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull { it.index == lastIndex }
                val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                val distanceFromLastVisible = layoutInfo.totalItemsCount - 1 -
                    (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0)

                if (lastVisibleItem != null && lastVisibleItem.size > viewportHeight) {
                    listState.scrollToItem(lastIndex, Int.MAX_VALUE)
                } else if (distanceFromLastVisible < 3) {
                    listState.animateScrollToItem(lastIndex)
                } else {
                    listState.scrollToItem(lastIndex)
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
                    viewModel.refreshCommandsIfNeeded(force = true)
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
                        selectedReasoningEffort = selectedReasoningEffort,
                        onReasoningEffortSelected = viewModel.modelAgentManager::selectReasoningEffort,
                        favoriteModels = favoriteModels,
                        recentModels = recentModels,
                        onToggleFavorite = viewModel.modelAgentManager::toggleFavoriteModel
                    )
                    ChatInputBar(
                        value = uiState.inputText,
                        onValueChange = { text ->
                            viewModel.updateInput(text)
                            if (text.startsWith("/") && !text.contains(" ")) {
                                viewModel.refreshCommandsIfNeeded()
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
                        isLoadingCommands = uiState.isLoadingCommands,
                        commandLoadError = uiState.commandLoadError,
                        onRetryCommands = { viewModel.refreshCommandsIfNeeded(force = true) },
                        onCommandSelected = { /* Command text is already updated via onValueChange */ },
                        requestFocus = isActiveTab,
                        enterToSend = chatSettings.enterToSend,
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
                ) {
                    // All messages - stable keys ensure only changed items recompose
                    items(
                        items = messageBlocks,
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

                    pendingQuestion?.let { questionRequest ->
                        item(key = "pending_question_${questionRequest.id}") {
                            InlineQuestionCard(
                                questionRequestId = questionRequest.id,
                                questionData = dev.blazelight.p4oc.domain.model.QuestionData(questionRequest.questions),
                                onDismiss = { viewModel.dismissQuestion(questionRequest.id) },
                                onSubmit = { answers ->
                                    viewModel.respondToQuestion(questionRequest.id, answers)
                                },
                                modifier = Modifier.padding(vertical = Spacing.xs)
                            )
                        }
                    }
                }
            }

            val activeLoadSteps = buildList {
                addAll(uiState.loadingSteps)
                if (isPickerLoading) add("Loading files")
            }
            if (uiState.isLoading || activeLoadSteps.isNotEmpty()) {
                TuiLoadingScreen(
                    modifier = Modifier.align(Alignment.Center),
                    text = activeLoadSteps.ifEmpty { listOf("Loading session") }.joinToString("\n")
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
                visible = !shouldFollowTail && uiState.isBusy,
                hasNewContent = hasNewContentWhileAway,
                onClick = {
                    coroutineScope.launch {
                        shouldFollowTail = true
                        hasNewContentWhileAway = false
                        listState.scrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
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
            error = uiState.commandLoadError,
            onRetry = { viewModel.refreshCommandsIfNeeded(force = true) },
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
                        .widthIn(max = Sizing.panelWidthSm) // 80dp — tighter
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
                        onClick = {
                            showOverflow = false
                            onViewChanges()
                        }
                    )
                    TuiDropdownMenuItem(
                        text = "/ ${stringResource(R.string.cd_commands)}",
                        onClick = {
                            showOverflow = false
                            onCommands()
                        }
                    )
                    TuiDropdownMenuItem(
                        text = ">_ ${stringResource(R.string.cd_terminal)}",
                        onClick = {
                            showOverflow = false
                            onTerminal()
                        }
                    )
                    TuiDropdownMenuItem(
                        text = "▤ ${stringResource(R.string.cd_files)}",
                        onClick = {
                            showOverflow = false
                            onFiles()
                        }
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
    val presence = when (state) {
        ConnectionState.Connected -> SessionPresence.IDLE
        ConnectionState.Connecting -> SessionPresence.RETRYING
        ConnectionState.Disconnected -> SessionPresence.BACKGROUND
        is ConnectionState.Error -> SessionPresence.ERROR
    }
    SessionStatusDot(presence = presence, size = Sizing.indicatorDot)
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
