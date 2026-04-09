package dev.blazelight.p4oc.ui.screens.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import dev.blazelight.p4oc.domain.model.Agent
import dev.blazelight.p4oc.domain.model.Model
import dev.blazelight.p4oc.core.datastore.VisualSettings
import dev.blazelight.p4oc.ui.screens.chat.ChatUiState
import kotlinx.coroutines.flow.combine
import dev.blazelight.p4oc.ui.components.chat.AbortSummaryCard
import dev.blazelight.p4oc.ui.components.chat.ChatInputBar
import dev.blazelight.p4oc.ui.components.chat.FilePickerDialog
import dev.blazelight.p4oc.ui.components.chat.JumpToBottomButton
import dev.blazelight.p4oc.ui.components.chat.ModelAgentSelectorBar
import dev.blazelight.p4oc.ui.components.command.CommandPalette
import dev.blazelight.p4oc.ui.components.question.InlineQuestionCard
import dev.blazelight.p4oc.ui.components.todo.TodoTrackerSheet
import dev.blazelight.p4oc.ui.components.toolwidgets.ToolWidgetState
import dev.blazelight.p4oc.ui.components.TuiDropdownMenu
import dev.blazelight.p4oc.ui.components.TuiDropdownMenuItem
import dev.blazelight.p4oc.ui.components.TuiTopBar
import dev.blazelight.p4oc.ui.components.TuiConfirmDialog
import dev.blazelight.p4oc.ui.components.TuiLoadingScreen
import dev.blazelight.p4oc.ui.components.TuiSnackbar
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme

// Data classes for optimized state management (currently not used but kept for future optimization)
data class CombinedChatState(
    val uiState: ChatUiState,
    val connectionState: ConnectionState,
    val sessionConnectionState: SessionConnectionState,
    val branchName: String?,
    val visualSettings: VisualSettings
)

data class ModelAgentState(
    val availableAgents: List<Agent>,
    val selectedAgent: Agent?,
    val availableModels: List<Model>,
    val selectedModel: Model?,
    val favoriteModels: Set<Model>
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    // Optimized state collection - reduce recompositions by grouping related states
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val branchName by viewModel.branchName.collectAsStateWithLifecycle()
    val sessionConnectionState by viewModel.sessionConnectionState.collectAsStateWithLifecycle()
    val visualSettings by viewModel.visualSettings.collectAsStateWithLifecycle()

    // === INSTANT PAINT: Show UI immediately with skeleton ===
    val instantPaint by viewModel.instantPaintState.collectAsStateWithLifecycle()
    val estimatedCount by viewModel.estimatedMessageCount.collectAsStateWithLifecycle()

    // Dialog states - collected separately to avoid unnecessary recompositions
    val pendingQuestion by viewModel.dialogManager.pendingQuestion.collectAsStateWithLifecycle()
    val pendingPermissionsByCallId by viewModel.dialogManager.pendingPermissionsByCallId.collectAsStateWithLifecycle()
    
    // Model/Agent states - collect only what's needed
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
    val coroutineScope = rememberCoroutineScope()

    // ScrollableDefaults.flingBehavior() already uses splineBasedDecay internally in Compose 1.8+
    // — same physics as RecyclerView. No custom implementation needed.
    val smoothFling = ScrollableDefaults.flingBehavior()

    // Single snapshotFlow observer — replaces multiple LaunchedEffect(state) that caused re-execution races
    val isAtBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 120
        }
    }
    var userScrolledAway by remember { mutableStateOf(false) }
    var hasNewContentWhileAway by remember { mutableStateOf(false) }

    // One unified scroll observer — zero LaunchedEffect overhead during fling
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to isAtBottom }
            .collect { (scrolling, atBottom) ->
                viewModel.messageStore.setFlushDelayWhileScrolling(scrolling)
                if (scrolling && !atBottom) userScrolledAway = true
                if (atBottom && !scrolling && userScrolledAway) {
                    userScrolledAway = false
                    hasNewContentWhileAway = false
                }
            }
    }

    // OPTIMIZED: Instant scroll to bottom with minimal delay
    val messageCount = messages.size
    var hasScrolledToBottom by remember { mutableStateOf(false) }

    // Reset when session changes
    LaunchedEffect(uiState.session?.id) {
        hasScrolledToBottom = false
    }

    // Fast scroll on initial load - minimal delay
    LaunchedEffect(messageCount, hasScrolledToBottom) {
        if (messageCount > 0 && !hasScrolledToBottom) {
            kotlinx.coroutines.delay(30) // Minimal wait for LazyColumn
            listState.scrollToItem(0)
            hasScrolledToBottom = true
        }
    }

    // Handle new messages without re-triggering initial scroll
    LaunchedEffect(messageCount, hasScrolledToBottom) {
        if (messageCount > 0 && hasScrolledToBottom && !userScrolledAway) {
            listState.scrollToItem(0)
        } else if (messageCount > 0 && userScrolledAway) {
            hasNewContentWhileAway = true
        }
    }

    // Loading state
    val showSkeleton = uiState.isLoading || uiState.session == null
    var showCommandPalette by remember { mutableStateOf(false) }
    var showTodoTracker by remember { mutableStateOf(false) }
    var showFilePicker by remember { mutableStateOf(false) }
    var showRevertDialog by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    BackHandler {
        focusManager.clearFocus()
        keyboardController?.hide()
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            // INSTANT PAINT: Use instant title while real session loads
            val displayTitle = when {
                instantPaint.hasRealSession -> uiState.session?.title
                instantPaint.sessionTitle.isNotBlank() -> instantPaint.sessionTitle
                else -> "Chat"
            } ?: "Chat"
            ChatTopBar(
                modifier = Modifier,
                title = displayTitle,
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
                    ChatInputBar(
                        value = uiState.inputText,
                        connectionState = when(connectionState) {
                            is ConnectionState.Connected -> dev.blazelight.p4oc.ui.components.chat.InputConnectionState.CONNECTED
                            is ConnectionState.Connecting -> dev.blazelight.p4oc.ui.components.chat.InputConnectionState.CONNECTING
                            else -> dev.blazelight.p4oc.ui.components.chat.InputConnectionState.DISCONNECTED
                        },
                        modelSelector = {
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
                        },
                        agentSelector = { },
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
            // Use pre-computed blocks from ViewModel (computed on Default dispatcher)
            val messageBlocks by viewModel.messageBlocks.collectAsStateWithLifecycle()
            // OPTIMIZED: Reverse in background thread to prevent UI blocking
            val reversedBlocks by produceState(
                initialValue = emptyList<MessageBlock>(),
                key1 = messageBlocks
            ) {
                value = withContext(Dispatchers.Default) {
                    messageBlocks.asReversed()
                }
            }

            // Stable lambdas — same reference across recompositions, prevents MessageBlockView recompose
            val onToolApprove = remember(viewModel) { { id: String -> viewModel.respondToPermission(id, "once") } }
            val onToolDeny = remember(viewModel) { { id: String -> viewModel.respondToPermission(id, "reject") } }
            val onToolAlways = remember(viewModel) { { id: String -> viewModel.respondToPermission(id, "always") } }
            val onRevert = remember { { id: String -> showRevertDialog = id } }

            if (!hasContent && !uiState.isLoading) {
                EmptyChatView(modifier = Modifier.align(Alignment.Center))
            } else {
                // OPTIMIZED: Show LazyColumn directly without AnimatedVisibility to prevent scroll jank
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("message_list"),
                    reverseLayout = true,
                    flingBehavior = smoothFling,
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    // Inline question card
                    pendingQuestion?.let { questionRequest ->
                        item(key = "q_${questionRequest.id}") {
                            InlineQuestionCard(
                                questionData = dev.blazelight.p4oc.domain.model.QuestionData(questionRequest.questions),
                                onDismiss = viewModel::dismissQuestion,
                                onSubmit = { viewModel.respondToQuestion(questionRequest.id, it) },
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    // Abort summary card
                    uiState.abortSummary?.let { summary ->
                        item(key = "abort_${summary.abortedAt}") {
                            AbortSummaryCard(summary = summary, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }

                    // Messages — stable keys, contentType for recycling, no per-item animations
                    items(
                        count = reversedBlocks.size,
                        key = { index ->
                            when (val block = reversedBlocks[index]) {
                                is MessageBlock.UserBlock -> "u_${block.message.message.id}"
                                // GUARD: Protect against empty assistant block - use fallback key
                                is MessageBlock.AssistantBlock -> {
                                    val firstMessage = block.messages.firstOrNull()
                                    if (firstMessage != null) {
                                        "a_${firstMessage.message.id}"
                                    } else {
                                        "a_empty_${index}" // Fallback for empty blocks
                                    }
                                }
                            }
                        },
                        contentType = { index ->
                            when (reversedBlocks[index]) {
                                is MessageBlock.UserBlock -> 0
                                is MessageBlock.AssistantBlock -> 1
                            }
                        }
                    ) { index ->
                        val block = reversedBlocks[index]
                        // GUARD: Skip empty assistant blocks
                        if (block is MessageBlock.AssistantBlock && block.messages.isEmpty()) {
                            return@items
                        }
                        MessageBlockView(
                            block = block,
                            onToolApprove = onToolApprove,
                            onToolDeny = onToolDeny,
                            onToolAlways = onToolAlways,
                            onOpenSubSession = onOpenSubSession,
                            defaultToolWidgetState = defaultToolWidgetState,
                            pendingPermissionsByCallId = pendingPermissionsByCallId,
                            onRevert = onRevert
                        )
                    }

                    // Load older messages control (appears at the top due to reverseLayout)
                    item(key = "load_more_messages") {
                        val theme = LocalOpenCodeTheme.current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xs)
                                .clip(RoundedCornerShape(4.dp))
                                .background(theme.backgroundElement)
                                .border(1.dp, theme.borderSubtle, RoundedCornerShape(4.dp))
                                .clickable(role = Role.Button) { viewModel.loadOlderMessages() }
                                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Load older messages",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = theme.accent
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

    // === INSTANT PAINT SKELETON ===
    // Show immediately while real data hydrates in background
    if (instantPaint.isVisible && !instantPaint.hasRealMessages) {
        InstantPaintSkeleton(
            messageCount = instantPaint.estimatedMessageCount,
            hasRealSession = instantPaint.hasRealSession
        )
    }
}

/**
 * INSTANT PAINT Skeleton - shows immediately with estimated content.
 * OPTIMIZED: Static placeholder without shimmer to prevent UI freeze.
 */
@Composable
private fun InstantPaintSkeleton(
    messageCount: Int,
    hasRealSession: Boolean
) {
    val theme = LocalOpenCodeTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // OPTIMIZED: Limit to max 5 skeleton items to prevent performance issues
        repeat(messageCount.coerceIn(2, 5)) { index ->
            val isUser = index % 2 == 0
            // Fixed height to avoid complex calculations during rendering
            val height = 56.dp

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (isUser) 0.75f else 0.85f)
                        .height(height)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isUser) theme.primary.copy(alpha = 0.08f)
                            else theme.backgroundElement.copy(alpha = 0.6f)
                        )
                        .border(
                            1.dp,
                            if (isUser) theme.primary.copy(alpha = 0.15f)
                            else theme.border.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        )
                )
            }
        }
    }
}

/**
 * LITE loading skeleton for session loading state.
 * Simple placeholder rows without heavy animations.
 */
@Composable
private fun LoadingSkeleton() {
    val theme = LocalOpenCodeTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Simulate 6 message placeholders
        repeat(6) { index ->
            val isUser = index % 2 == 0
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (isUser) 0.7f else 0.8f)
                        .height(48.dp + (index * 8).dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isUser) theme.primary.copy(alpha = 0.08f)
                            else theme.backgroundElement.copy(alpha = 0.6f)
                        )
                        .border(
                            1.dp,
                            if (isUser) theme.primary.copy(alpha = 0.15f)
                            else theme.border.copy(alpha = 0.3f),
                            RoundedCornerShape(12.dp)
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    modifier: Modifier = Modifier,
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
    // Apply modifier to TuiTopBar wrapper
    val theme = LocalOpenCodeTheme.current
    var showOverflow by remember { mutableStateOf(false) }

    TuiTopBar(
        modifier = modifier,
        title = title,
        onNavigateBack = onBack,
        actions = {
            // Animated connection indicator
            ConnectionDot(state = connectionState)

            // Branch chip
            branchName?.let { branch ->
                Spacer(Modifier.width(4.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(theme.success.copy(alpha = 0.10f))
                        .border(1.dp, theme.success.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
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

            Spacer(Modifier.width(4.dp))

            // Todos badge
            if (todoCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(theme.accent.copy(alpha = 0.12f))
                        .border(1.dp, theme.accent.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button) { onTodos() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("☐ $todoCount", color = theme.accent, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(4.dp))
            }

            // Abort pill — only when busy
            if (isBusy) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(theme.error.copy(alpha = 0.14f))
                        .border(1.dp, theme.error.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button) { onAbort() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("chat_abort_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("■", color = theme.error, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall)
                    Text("Stop", color = theme.error, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(4.dp))
            }

            // Overflow menu — full submenu restored
            Box {
                Box(
                    modifier = Modifier
                        .size(Sizing.iconButtonMd)
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.accent.copy(alpha = 0.08f))
                        .clickable(role = Role.Button) { showOverflow = true }
                        .testTag("chat_overflow_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("≡", color = theme.accent, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleMedium)
                }
                TuiDropdownMenu(
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
        TuiDropdownMenu(
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
