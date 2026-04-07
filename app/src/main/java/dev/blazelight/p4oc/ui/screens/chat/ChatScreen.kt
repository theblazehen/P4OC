package dev.blazelight.p4oc.ui.screens.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.SessionConnectionState
import dev.blazelight.p4oc.ui.components.chat.AbortSummaryCard
import dev.blazelight.p4oc.ui.components.chat.ChatInputBar
import dev.blazelight.p4oc.ui.components.chat.FilePickerDialog
import dev.blazelight.p4oc.ui.components.chat.JumpToBottomButton
import dev.blazelight.p4oc.ui.components.chat.ModelAgentSelectorBar
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
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
                onAbort = viewModel::abortSession,
                isBusy = uiState.isBusy,
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
                        .background(LocalOpenCodeTheme.current.backgroundElement)
                ) {
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
                            if (text.startsWith("/") && uiState.commands.isEmpty()) {
                                viewModel.loadCommands()
                            }
                        },
                        onSend = viewModel::sendMessage,
                        isLoading = uiState.isSending,
                        enabled = connectionState is ConnectionState.Connected,
                        isBusy = uiState.isBusy,
                        hasQueuedMessage = uiState.queuedMessage != null,
                        onQueueMessage = viewModel::queueMessage,
                        onCancelQueue = viewModel::cancelQueuedMessage,
                        queuedMessagePreview = uiState.queuedMessage?.text,
                        attachedFiles = attachedFiles,
                        onAttachClick = {
                            viewModel.filePickerManager.loadPickerFiles()
                            showFilePicker = true
                        },
                        onRemoveAttachment = viewModel.filePickerManager::detachFile,
                        commands = uiState.commands,
                        onCommandSelected = { },
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                        .background(theme.warning.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "↺", color = theme.warning, fontFamily = FontFamily.Monospace)
                        Text(
                            text = stringResource(R.string.revert_active_banner),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = theme.warning
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(theme.warning.copy(alpha = 0.15f))
                            .clickable(role = Role.Button) { viewModel.unrevertSession() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.unrevert_all),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = theme.warning
                        )
                    }
                }
            }

            val hasContent = messages.isNotEmpty() || uiState.isBusy
            
            if (!hasContent && !uiState.isLoading) {
                EmptyChatView(modifier = Modifier.align(Alignment.Center))
            } else {
                val messageBlocks by remember {
                    derivedStateOf { groupMessagesIntoBlocks(messages) }
                }
                
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("message_list"),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
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

                    // Abort summary card (shows after user hits Stop)
                    uiState.abortSummary?.let { summary ->
                        item(key = "abort_summary_${summary.abortedAt}") {
                            AbortSummaryCard(
                                summary = summary,
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
                        },
                        contentType = { block ->
                            when (block) {
                                is MessageBlock.UserBlock -> "user"
                                is MessageBlock.AssistantBlock -> "assistant"
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
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = viewModel::clearError, shape = RoundedCornerShape(4.dp)) {
                            Text(
                                stringResource(R.string.dismiss),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                ) {
                    Text(error, fontFamily = FontFamily.Monospace)
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
            selectedFiles = attachedFiles,
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
    onAbort: () -> Unit,
    isBusy: Boolean,
    branchName: String? = null,
    todoCount: Int = 0,
    onTodos: () -> Unit = {}
) {
    val theme = LocalOpenCodeTheme.current
    var showOverflow by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = theme.backgroundElement,
        tonalElevation = 0.dp
    ) {
        Column {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
            // ── Row 1: back + title + connection + abort ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(Sizing.iconButtonMd)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        modifier = Modifier.size(Sizing.iconLg),
                        tint = theme.text
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                    color = theme.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Connection dot
                ConnectionDot(state = connectionState)
                Spacer(Modifier.width(6.dp))
                // Abort pill — only when busy
                if (isBusy) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(theme.error.copy(alpha = 0.14f))
                            .border(1.dp, theme.error.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                            .clickable(role = Role.Button) { onAbort() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("chat_abort_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("■", color = theme.error, fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall)
                        Text("Stop", color = theme.error, fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            // ── Row 2: branch + quick actions + todos + overflow ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Branch chip
                branchName?.let { branch ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(theme.success.copy(alpha = 0.10f))
                            .border(1.dp, theme.success.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("⎇", color = theme.success, fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = branch,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = theme.success,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = Sizing.panelWidthSm)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                // Quick action pills
                @Composable
                fun ActionChip(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(color.copy(alpha = 0.08f))
                            .border(1.dp, color.copy(alpha = 0.20f), RoundedCornerShape(4.dp))
                            .clickable(role = Role.Button, onClick = onClick)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(label, color = color, fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
                ActionChip("±", theme.warning, onViewChanges)
                ActionChip(">_", theme.accent, onTerminal)
                ActionChip("▤", theme.secondary, onFiles)
                // Todos badge
                if (todoCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(theme.accent.copy(alpha = 0.12f))
                            .border(1.dp, theme.accent.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                            .clickable(role = Role.Button) { onTodos() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("☐ $todoCount", color = theme.accent, fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
                // Overflow (commands only now)
                Box {
                    Box(
                        modifier = Modifier
                            .size(Sizing.iconButtonMd)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(role = Role.Button) { showOverflow = true }
                            .testTag("chat_overflow_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("≡", color = theme.textMuted, fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.titleMedium)
                    }
                    DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                        TuiDropdownMenuItem(
                            text = "/ ${stringResource(R.string.cd_commands)}",
                            onClick = { showOverflow = false; onCommands() }
                        )
                    }
                }
            }
            // Bottom border line
            Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border.copy(alpha = 0.5f)))
        }
    }
}

/**
 * Compact connection dot for the title subtitle row — just a colored text glyph.
 * No 40dp bounding box, no dropdown. Tap the main ConnectionIndicator for details.
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
private fun ConnectionIndicator(state: ConnectionState) {
    val theme = LocalOpenCodeTheme.current
    var showDetail by remember { mutableStateOf(false) }

    val (color, description) = when (state) {
        ConnectionState.Connected -> theme.success to stringResource(R.string.connection_status_connected)
        ConnectionState.Connecting -> theme.warning to stringResource(R.string.connection_status_connecting)
        ConnectionState.Disconnected -> theme.textMuted to stringResource(R.string.connection_status_disconnected)
        is ConnectionState.Error -> theme.error to sanitizeErrorMessage(state.message)
    }

    Box(
        modifier = Modifier.size(Sizing.iconButtonMd),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Circle,
            contentDescription = description,
            tint = color,
            modifier = Modifier
                .size(Sizing.iconXxs)
                .clickable(role = Role.Button) { showDetail = !showDetail }
        )
        DropdownMenu(
            expanded = showDetail,
            onDismissRequest = { showDetail = false }
        ) {
            Text(
                text = description,
                modifier = Modifier.padding(Spacing.md),
                style = MaterialTheme.typography.bodySmall,
                color = theme.text
            )
        }
    }
}

@Composable
private fun sanitizeErrorMessage(raw: String?): String = when {
    raw == null -> stringResource(R.string.connection_error_generic)
    raw.contains("stream closed", ignoreCase = true) -> stringResource(R.string.connection_error_stream_closed)
    raw.contains("timeout", ignoreCase = true) -> stringResource(R.string.connection_error_timeout)
    raw.contains("refused", ignoreCase = true) -> stringResource(R.string.connection_error_refused)
    raw.contains("reset", ignoreCase = true) -> stringResource(R.string.connection_error_reset)
    raw.contains("unreachable", ignoreCase = true) -> stringResource(R.string.connection_error_refused)
    else -> stringResource(R.string.connection_error_generic)
}

@Composable
private fun EmptyChatView(modifier: Modifier = Modifier) {
    val theme = LocalOpenCodeTheme.current
    Column(
        modifier = modifier.padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Box(
            modifier = androidx.compose.ui.Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(theme.backgroundElement),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "◇",
                style = MaterialTheme.typography.headlineMedium,
                color = theme.textMuted
            )
        }
        Text(
            text = stringResource(R.string.chat_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = theme.text
        )
        Text(
            text = stringResource(R.string.chat_empty_description),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = theme.textMuted
        )
        // Tip chips row
        val theme2 = theme
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("/compact", "/clear", "/help").forEach { cmd ->
                Box(
                    modifier = androidx.compose.ui.Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(theme2.backgroundElement)
                        .border(1.dp, theme2.border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = cmd,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = theme2.accent
                    )
                }
            }
        }
    }
}

// MessageBlock, groupMessagesIntoBlocks, and MessageBlockView are now in MessageBlockUtils.kt
