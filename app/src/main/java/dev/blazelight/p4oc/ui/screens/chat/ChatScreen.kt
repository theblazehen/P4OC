package dev.blazelight.p4oc.ui.screens.chat

import dev.blazelight.p4oc.core.log.AppLog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

    // isLoading se obtiene de uiState

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

    // SINGLE LaunchedEffect for all scroll logic - prevents multiple triggers and lag
    // Key: session ID - only runs once per session load
    LaunchedEffect(uiState.session?.id) {
        if (messages.isNotEmpty()) {
            // IMMEDIATE scroll without delay - critical for instant paint
            listState.scrollToItem(0)
        }
    }
    
    // New messages auto-scroll (only if user hasn't scrolled away)
    val messageCount = messages.size
    LaunchedEffect(messageCount) {
        if (messageCount > 0 && !userScrolledAway) {
            listState.scrollToItem(0)
        } else if (messageCount > 0 && userScrolledAway) {
            hasNewContentWhileAway = true
        }
    }

    // Loading state - skeleton removed to prevent background placeholders
    // val showSkeleton = uiState.isLoading || uiState.session == null
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
            // Session title from loaded session
            val displayTitle = uiState.session?.title ?: "Chat"
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
                            AppLog.w("ChatScreen", "=== INPUT CHANGE ===")
                            AppLog.w("ChatScreen", "onValueChange: text='${text.take(20)}...', length=${text.length}")
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
                        onCancelQueue = { /* TODO: Implementar cancelacion de mensaje encolado */ },
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
            // ORIGINAL: Simple message blocks computation
            val messageBlocks = remember(messages) {
                groupMessagesIntoBlocks(messages)
            }

            // Stable lambdas — same reference across recompositions, prevents MessageBlockView recompose
            val onToolApprove = remember(viewModel) { { id: String -> viewModel.respondToPermission(id, "once") } }
            val onToolDeny = remember(viewModel) { { id: String -> viewModel.respondToPermission(id, "reject") } }
            val onToolAlways = remember(viewModel) { { id: String -> viewModel.respondToPermission(id, "always") } }
            val onRevert = remember { { id: String -> showRevertDialog = id } }

            if (!hasContent && !uiState.isLoading) {
                EmptyChatView(modifier = Modifier.align(Alignment.Center))
            } else {
                // OPTIMIZED: LazyColumn with enhanced performance and smooth animations
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp) // Match topbar padding
                        .testTag("message_list"),
                    reverseLayout = true,
                    flingBehavior = smoothFling,
                    contentPadding = PaddingValues(vertical = 2.dp),
                    // Performance optimizations
                    userScrollEnabled = true,
                ) {
                    // Inline question card - optimized without animation to prevent lag
                    pendingQuestion?.let { questionRequest ->
                        item(
                            key = "q_${questionRequest.id}",
                            contentType = "question"
                        ) {
                            InlineQuestionCard(
                                questionData = dev.blazelight.p4oc.domain.model.QuestionData(questionRequest.questions),
                                onDismiss = viewModel::dismissQuestion,
                                onSubmit = { viewModel.respondToQuestion(questionRequest.id, it) },
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    // Abort summary card - optimized without animation to prevent lag
                    uiState.abortSummary?.let { summary ->
                        item(
                            key = "abort_${summary.abortedAt}",
                            contentType = "abort"
                        ) {
                            AbortSummaryCard(summary = summary, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }

                    // All messages - stable keys + contentType for optimal recycling
                    items(
                        items = messageBlocks.asReversed(),
                        key = { block ->
                            when (block) {
                                is MessageBlock.UserBlock -> "u_${block.message.message.id}"
                                is MessageBlock.AssistantBlock -> {
                                    val firstMsg = block.messages.first()
                                    "a_${firstMsg.message.id}_${block.messages.size}"
                                }
                            }
                        },
                        contentType = { block ->
                            when (block) {
                                is MessageBlock.UserBlock -> "user_simple"
                                is MessageBlock.AssistantBlock -> {
                                    val hasTools = block.messages.any { msg ->
                                        msg.parts.any { part ->
                                            part is Part.Tool
                                        }
                                    }
                                    val hasLongText = block.messages.any { msg ->
                                        msg.parts.filterIsInstance<dev.blazelight.p4oc.domain.model.Part.Text>()
                                            .any { it.text.length > 2000 }
                                    }
                                    when {
                                        hasTools -> "assistant_tools"
                                        hasLongText -> "assistant_heavy"
                                        else -> "assistant_simple"
                                    }
                                }
                            }
                        }
                    ) { block ->
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
                    // Show if there are more messages available to load
                    // Calculate outside items block to avoid @Composable issues
                    val hasMoreMessages = viewModel.hasMoreMessages()
                    val totalMessageCount = viewModel.getTotalMessageCount()
                    val visibleMessageCount = messages.size
                    
                    if (hasMoreMessages && visibleMessageCount < totalMessageCount) {
                        item(
                            key = "load_more_messages",
                            contentType = "load_more"
                        ) {
                            val theme = LocalOpenCodeTheme.current
                            var isLoadingMore by remember { mutableStateOf(false) }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .clickable(role = Role.Button, onClick = {
                                        if (!isLoadingMore) {
                                            isLoadingMore = true
                                            viewModel.loadOlderMessages()
                                            isLoadingMore = false
                                        }
                                    })
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isLoadingMore) "..." else "↺ more (${visibleMessageCount}/${totalMessageCount})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = theme.textMuted
                                )
                            }
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

    // === SKELETON REMOVED ===
    // Removed skeleton to prevent background placeholders during loading
    // Messages will appear directly without placeholder backgrounds
}

// INSTANT PAINT Skeleton removed - no longer needed
// Messages appear directly without placeholder backgrounds

// LoadingSkeleton removed - no longer needed
// Messages appear directly without placeholder backgrounds

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
    // Unified Chat TopBar - coherent with MainTabScreen style
    val theme = LocalOpenCodeTheme.current
    var showOverflow by remember { mutableStateOf(false) }
    
    // Animated glow effect
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.background)
            .padding(horizontal = 12.dp, vertical = 0.dp) // No vertical padding for connector contact
    ) {
        // Top connector - direct connection from MainTabScreen
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(1.dp)
                    .background(theme.border.copy(alpha = 0.3f))
            )
            Text(
                text = "├", // Changed from ┌ to ├ for direct connection
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = theme.accent.copy(alpha = 0.7f) // Matches MainTabScreen connector
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                theme.border.copy(alpha = 0.3f),
                                theme.accent.copy(alpha = 0.4f),
                                theme.border.copy(alpha = 0.3f)
                            )
                        )
                    )
            )
            Text(
                text = "┤",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = theme.border.copy(alpha = 0.5f)
            )
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(1.dp)
                    .background(theme.border.copy(alpha = 0.3f))
            )
        }

        // Segmented terminal layout - 3 connected sections
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp) // More compact height
                .background(theme.background),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Section 1: Back button segment - ASCII style
            Row(
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "[",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = theme.textMuted.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "←",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = theme.accent.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onBack)
                        .padding(horizontal = 4.dp, vertical = 0.dp)
                )
                Text(
                    text = "]",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = theme.textMuted.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold
                )
            }

            // ASCII connector 1
            Text(
                text = "┼",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = theme.accent.copy(alpha = 0.7f)
            )

            // Center section with proper weight distribution
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Section 2: Compact status indicators (truly centered)
                Row(
                    modifier = Modifier
                        .wrapContentWidth()
                        .border(1.dp, theme.border.copy(alpha = 0.4f))
                        .background(theme.backgroundElement.copy(alpha = 0.08f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Compact status display
                    Text(
                        text = if (isBusy) "⚙" else "✦",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isBusy) theme.warning.copy(alpha = 0.9f) else theme.success.copy(alpha = 0.9f)
                    )
                    
                    Text(
                        text = if (isBusy) "run" else "idle",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isBusy) theme.warning.copy(alpha = 0.9f) else theme.success.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )

                    branchName?.let {
                        Text(
                            text = "·",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.border.copy(alpha = 0.5f)
                        )
                        Text(
                            text = it.take(6),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.success.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "·",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.border.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "●",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (connectionState is ConnectionState.Connected) theme.success.copy(alpha = glowAlpha) else theme.warning.copy(alpha = 0.6f)
                    )
                }
            }

            // ASCII connector 2
            Text(
                text = "┼",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = theme.accent.copy(alpha = 0.7f)
            )

            // Section 3: Controls segment
            Row(
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (isBusy) {
                    var clickCount by remember { mutableIntStateOf(0) }
                    val scope = rememberCoroutineScope()
                    
                    Text(
                        text = "■",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.error.copy(alpha = if (clickCount > 0) 1.0f else 0.8f),
                        modifier = Modifier
                            .clickable(role = Role.Button, onClick = {
                                clickCount++
                                if (clickCount == 1) {
                                    // Reset after 2 seconds if no second click
                                    scope.launch {
                                        delay(2000)
                                        if (clickCount == 1) clickCount = 0
                                    }
                                } else if (clickCount >= 2) {
                                    onAbort()
                                    clickCount = 0
                                }
                            })
                            .padding(4.dp)
                    )
                }

                if (todoCount > 0) {
                    Text(
                        text = "[$todoCount]",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.accent.copy(alpha = 0.8f),
                        modifier = Modifier
                            .clickable(role = Role.Button, onClick = onTodos)
                            .padding(2.dp)
                    )
                }

                // ASCII menu button with special brackets
                Text(
                    text = "[",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = theme.textMuted.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "☰",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = theme.accent.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = { showOverflow = true })
                        .padding(horizontal = 4.dp, vertical = 0.dp)
                )
                Text(
                    text = "]",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = theme.textMuted.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Dropdown menu for terminal-style menu button
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
