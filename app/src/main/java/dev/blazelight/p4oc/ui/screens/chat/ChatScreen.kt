@file:Suppress("ImportOrdering", "DEPRECATION")

package dev.blazelight.p4oc.ui.screens.chat

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.data.remote.dto.ModelInput
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.Permission
import dev.blazelight.p4oc.domain.model.SessionConnectionState
import dev.blazelight.p4oc.domain.model.SessionPresence
import dev.blazelight.p4oc.ui.components.TuiAlertDialog
import dev.blazelight.p4oc.ui.components.TuiButton
import dev.blazelight.p4oc.ui.components.TuiConfirmDialog
import dev.blazelight.p4oc.ui.components.TuiDropdownMenuItem
import dev.blazelight.p4oc.ui.components.TuiLoadingScreen
import dev.blazelight.p4oc.ui.components.TuiSnackbar
import dev.blazelight.p4oc.ui.components.TuiTopBar
import dev.blazelight.p4oc.ui.components.chat.AttachmentSourceSheet
import dev.blazelight.p4oc.ui.components.chat.ChatInputBar
import dev.blazelight.p4oc.ui.components.chat.ChatJumpNavigationButtons
import dev.blazelight.p4oc.ui.components.chat.FilePickerDialog
import dev.blazelight.p4oc.ui.components.chat.InlinePermissionPrompt
import dev.blazelight.p4oc.ui.components.chat.LocalChatMediaLoader
import dev.blazelight.p4oc.ui.components.chat.ModelAgentSelectorBar
import dev.blazelight.p4oc.ui.components.chat.PhoneAttachmentSheet
import dev.blazelight.p4oc.ui.components.command.CommandPalette
import dev.blazelight.p4oc.ui.components.command.rememberResolvedCommandMetadata
import dev.blazelight.p4oc.ui.components.question.InlineQuestionCard
import dev.blazelight.p4oc.ui.components.form.InlineV2FormCard
import dev.blazelight.p4oc.ui.components.form.InlineV2FormStatusCard
import dev.blazelight.p4oc.ui.components.status.SessionStatusDot
import dev.blazelight.p4oc.ui.components.todo.TodoTrackerSheet
import dev.blazelight.p4oc.ui.components.toolwidgets.ToolWidgetState
import dev.blazelight.p4oc.ui.screens.files.upload.ContentResolverUploadSource
import dev.blazelight.p4oc.ui.screens.files.upload.UploadProgressSheet
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

internal fun pendingPermissionAttentionVersion(pendingPermissionCallIds: Set<String>): String =
    pendingPermissionCallIds.sorted().joinToString(separator = "\u001F")

internal fun hasNewPendingPermission(previous: Set<String>, current: Set<String>): Boolean =
    current.any { it !in previous }

internal fun pendingPermissionBlockIndex(
    blocks: List<MessageBlock>,
    pendingCallIds: Set<String>,
): Int? = blocks.indexOfFirst { block ->
    val messages = when (block) {
        is MessageBlock.UserBlock -> listOf(block.message)
        is MessageBlock.AssistantBlock -> block.messages
    }
    messages.any { message ->
        message.parts.any { part -> part is Part.Tool && part.callID in pendingCallIds }
    }
}.takeIf { it >= 0 }

/** Permissions with no live tool call are session-scoped and must not be attached to an arbitrary message. */
internal fun unmatchedPendingPermissions(
    messages: List<MessageWithParts>,
    pendingPermissionsByKey: Map<String, Permission>,
): List<Permission> {
    val renderedToolCallIds = messages.asSequence()
        .flatMap { it.parts.asSequence() }
        .filterIsInstance<Part.Tool>()
        .map { it.callID }
        .toSet()
    return pendingPermissionsByKey.values
        .filter { permission -> permission.callID.isNullOrBlank() || permission.callID !in renderedToolCallIds }
        .distinctBy(Permission::id)
}

internal fun hasChatContent(
    hasMessages: Boolean,
    isBusy: Boolean,
    hasPendingQuestion: Boolean,
    hasSessionPendingPermissions: Boolean,
): Boolean = hasMessages || isBusy || hasPendingQuestion || hasSessionPendingPermissions

/**
 * Identity of the workspace an external phone picker was launched against. Deliberately
 * excludes the session id: it is null until the session loads, so including it made a
 * process-restored picker result compare against a different key and report a bogus
 * "workspace changed". The per-screen activity-result registration already binds the
 * result to the chat that launched it; only the server/workspace can really drift.
 */
internal fun attachmentOwnerKey(serverEndpointKey: String, workspaceDirectory: String?): String =
    "$serverEndpointKey|${workspaceDirectory.orEmpty()}"

internal enum class ChatLoadingOverlay {
    None,
    Transparent,
    Opaque,
}

/**
 * Presentation seam for the chat loading indicator. An opaque full-bleed overlay is shown only
 * while the session messages themselves are loading over an empty transcript, so cached or
 * populated content stays visible. Incidental in-session steps (slash commands, todos, file
 * picker) render a small transparent centered indicator regardless of content; when there is no
 * loading activity at all no overlay is shown.
 */
internal fun chatLoadingOverlay(
    isMessageLoading: Boolean,
    hasActiveLoadSteps: Boolean,
    hasContent: Boolean,
): ChatLoadingOverlay = when {
    !isMessageLoading && !hasActiveLoadSteps -> ChatLoadingOverlay.None
    isMessageLoading && !hasContent -> ChatLoadingOverlay.Opaque
    else -> ChatLoadingOverlay.Transparent
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod", "FunctionNaming")
fun ChatScreen(
    viewModel: ChatViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenFiles: () -> Unit,
    onViewSessionDiff: ((String) -> Unit)? = null,
    onOpenSubSession: ((String) -> Unit)? = null,
    onProviderAuthRequired: ((String) -> Unit)? = null,
    onSessionLoaded: ((sessionId: String, sessionTitle: String) -> Unit)? = null,
    onConnectionStateChanged: ((SessionConnectionState?) -> Unit)? = null,
    isActiveTab: Boolean = true,
    requestInitialInputFocus: Boolean = false,
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
    val pendingV2Forms by viewModel.pendingV2Forms.collectAsStateWithLifecycle()
    val pendingPermissionsByCallId by viewModel.dialogManager.pendingPermissionsByCallId.collectAsStateWithLifecycle()
    val sessionPendingPermissions = remember(messages, pendingPermissionsByCallId) {
        unmatchedPendingPermissions(messages, pendingPermissionsByCallId)
    }
    val availableAgents by viewModel.modelAgentManager.availableAgents.collectAsStateWithLifecycle()
    val selectedAgent by viewModel.modelAgentManager.selectedAgent.collectAsStateWithLifecycle()
    val availableModels by viewModel.modelAgentManager.availableModels.collectAsStateWithLifecycle()
    val providerNames by viewModel.modelAgentManager.providerNames.collectAsStateWithLifecycle()
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
    val phoneReview by viewModel.filePickerManager.phoneReview.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uploadSource = remember(context) {
        ContentResolverUploadSource(context.applicationContext.contentResolver)
    }
    val attachmentWorkspace = viewModel.filePickerManager.workspace
    val attachmentOwner = attachmentOwnerKey(
        attachmentWorkspace.server.endpointKey,
        attachmentWorkspace.directory,
    )
    var launchedAttachmentOwner by rememberSaveable { mutableStateOf<String?>(null) }
    var attachmentOwnerError by remember { mutableStateOf(false) }
    val acceptPhoneSelection: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) {
            if (launchedAttachmentOwner == attachmentOwner) {
                viewModel.filePickerManager.reviewPhoneFiles(
                    uploadSource,
                    uris.map { it.toString() },
                )
            } else {
                attachmentOwnerError = true
            }
        }
        launchedAttachmentOwner = null
    }
    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        acceptPhoneSelection(uris)
    }
    val photosLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        acceptPhoneSelection(uris)
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isActiveTab) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && isActiveTab) viewModel.refreshPendingForms()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (isActiveTab && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.refreshPendingForms()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.sessionMissing.collect { onNavigateBack() }
    }

    // Convert setting string to ToolWidgetState
    val defaultToolWidgetState = remember(visualSettings.toolWidgetDefaultState) {
        ToolWidgetState.fromString(visualSettings.toolWidgetDefaultState)
    }

    val listState = rememberSaveable(uiState.session?.id, saver = LazyListState.Saver) { LazyListState() }
    var showCommandPalette by remember { mutableStateOf(false) }
    var showTodoTracker by remember { mutableStateOf(false) }
    var showFilePicker by remember { mutableStateOf(false) }
    var showAttachmentSources by remember { mutableStateOf(false) }
    var choosingUploadFolder by remember { mutableStateOf(false) }
    var showUploads by remember { mutableStateOf(true) }
    var showRevertDialog by remember { mutableStateOf<String?>(null) }
    if (attachmentOwnerError) {
        TuiAlertDialog(
            onDismissRequest = { attachmentOwnerError = false },
            title = stringResource(R.string.chat_attachment_workspace_changed_title),
            modifier = Modifier.testTag("chat_attachment_workspace_changed"),
            confirmButton = {
                TuiButton(onClick = { attachmentOwnerError = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        ) {
            Text(stringResource(R.string.chat_attachment_workspace_changed_message))
        }
    }

    val scrollRestorationState = rememberSaveable(
        uiState.session?.id,
        saver = ChatScrollRestorationState.Saver
    ) { ChatScrollRestorationState() }
    val messageBlocks = remember(messages, uiState.isBusy) { groupMessagesIntoBlocks(messages, uiState.isBusy) }
    val olderMessagesItemOffset = if (uiState.hasOlderMessages) 1 else 0
    // A streaming assistant is initially reported with all-zero token totals.
    // Retain the newest meaningful usage until the live reply gains real usage.
    val contextUsage = remember(messages) { latestAssistantContextUsage(messages) }
    val searchMatches = remember(messageBlocks, scrollRestorationState.searchQuery) {
        findChatMatches(messageBlocks, scrollRestorationState.searchQuery)
    }
    val promptHistory = remember(messages) { messages.toPromptHistory() }
    val coroutineScope = rememberCoroutineScope()
    var composerFocused by remember { mutableStateOf(false) }

    // Derived state: true when the list cannot advance toward the tail. Keyed on listState so a
    // session's async null->id load (which recreates listState) cannot leave this observing a
    // discarded, zero-item instance.
    val isAtBottom by remember(listState) {
        derivedStateOf { !listState.canScrollForward }
    }
    val previousUserBlockIndex by remember(listState, messageBlocks, olderMessagesItemOffset) {
        derivedStateOf {
            val firstVisibleBlockIndex =
                (listState.firstVisibleItemIndex - olderMessagesItemOffset).coerceAtMost(messageBlocks.size)
            previousUserMessageBlockIndex(
                blocks = messageBlocks,
                firstVisibleBlockIndex = firstVisibleBlockIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
            )
        }
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    BackHandler {
        if (scrollRestorationState.showSearch) {
            scrollRestorationState.showSearch = false
            scrollRestorationState.searchQuery = ""
        } else {
            focusManager.clearFocus()
            keyboardController?.hide()
            onNavigateBack()
        }
    }

    // A user drag disables tail-following immediately so IME pinning cannot fight the gesture.
    LaunchedEffect(listState, uiState.session?.id) {
        listState.interactionSource.interactions
            .filterIsInstance<DragInteraction.Start>()
            .collect { scrollRestorationState.onUserScrollStarted() }
    }

    // Re-evaluate follow state after any scroll (touch, wheel, keyboard, semantics/TalkBack)
    // settles. The initial emission is ignored so a freshly composed list does not settle from a
    // stale position; the frame wait lets the final layout commit before reading the live bottom.
    LaunchedEffect(listState, uiState.session?.id) {
        snapshotFlow { listState.isScrollInProgress }
            .drop(1)
            .collect { scrolling ->
                if (!scrolling) {
                    withFrameNanos { }
                    scrollRestorationState.onScrollSettled(!listState.canScrollForward)
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
    val hasRenderableFormContent = pendingV2Forms.forms.isNotEmpty() ||
        pendingV2Forms.isLoading || pendingV2Forms.error != null
    val pendingFormVersion = buildString {
        append(pendingV2Forms.isLoading)
        append(pendingV2Forms.error != null)
        pendingV2Forms.forms.forEach { item ->
            append(item.id)
            append(':')
            append(item.form != null)
            append(item.isLoading)
            append(item.error != null)
        }
    }
    val pendingPermissionCallIds = pendingPermissionsByCallId.keys
    val pendingPermissionVersion = pendingPermissionAttentionVersion(pendingPermissionCallIds)
    val hasRenderableTail = !uiState.isLoading &&
        (messages.isNotEmpty() || pendingQuestionId != null || hasRenderableFormContent)
    var previouslyPendingPermissionCallIds by remember(uiState.session?.id) {
        mutableStateOf(emptySet<String>())
    }

    // Scroll on new messages, new parts, or streaming text/reasoning growth.
    LaunchedEffect(messageCount, tailContentVersion, isBusy, pendingQuestionId, pendingFormVersion) {
        if (scrollRestorationState.onTailContentChanged(
                messages.isNotEmpty() || pendingQuestionId != null || hasRenderableFormContent,
            )
        ) {
            listState.scrollChatToBottom()
        }
    }

    // While the composer is focused and the user is already following the tail, keep the latest
    // content visible as the IME opens or resizes. This never forces a scrolled-away viewport back
    // to the tail: it only pins when shouldFollowTail is already true, and scrolling away mid-typing
    // clears that flag so later viewport changes do not snap. The LazyColumn's actual viewport
    // height is observed (not the IME inset), so the effect reacts to real layout changes without
    // recomposing ChatScreen per animation pixel. The initial emission is ignored so merely
    // focusing the composer cannot scroll; only a real viewport-height transition pins the tail.
    LaunchedEffect(listState, composerFocused, scrollRestorationState.shouldFollowTail, hasRenderableTail) {
        if (!scrollRestorationState.shouldPinTailForIme(composerFocused, hasRenderableTail)) {
            return@LaunchedEffect
        }
        snapshotFlow {
            val info = listState.layoutInfo
            info.viewportEndOffset - info.viewportStartOffset
        }
            .drop(1)
            .collect {
                listState.scrollChatToBottom()
            }
    }

    // Permissions can arrive for a tool rendered far above the current viewport without changing
    // the message tail. Treat a newly pending call as explicit attention and reveal the approval UI.
    LaunchedEffect(pendingPermissionVersion) {
        val newPendingCallIds = pendingPermissionCallIds - previouslyPendingPermissionCallIds
        val hasNewPermission = hasNewPendingPermission(
            previous = previouslyPendingPermissionCallIds,
            current = pendingPermissionCallIds,
        )
        previouslyPendingPermissionCallIds = pendingPermissionCallIds.toSet()
        if (hasNewPermission) {
            val blockIndex = pendingPermissionBlockIndex(messageBlocks, newPendingCallIds)
            if (blockIndex != null) {
                // Keep streaming tail updates from immediately pulling the viewport away again.
                scrollRestorationState.shouldFollowTail = false
                listState.scrollToItem(blockIndex + olderMessagesItemOffset)
            } else {
                scrollRestorationState.onJumpToBottom()
                listState.scrollChatToBottom()
            }
        }
    }

    // Keep the active hit in range when matches change, and scroll it into view.
    LaunchedEffect(searchMatches.size) {
        if (scrollRestorationState.currentMatchIndex >= searchMatches.size) {
            scrollRestorationState.currentMatchIndex = 0
        }
    }
    LaunchedEffect(scrollRestorationState.currentMatchIndex, searchMatches) {
        searchMatches.getOrNull(scrollRestorationState.currentMatchIndex)?.let { match ->
            scrollRestorationState.shouldFollowTail = false
            listState.scrollToItem(match.blockIndex)
        }
    }

    // The loading screen hides the list; once the session content is visible, land at the tail.
    LaunchedEffect(uiState.session?.id, uiState.isLoading, messageCount, pendingQuestionId, pendingFormVersion) {
        when (scrollRestorationState.onContentReady(hasRenderableTail)) {
            InitialTailDecision.ScrollToTail -> {
                snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
                listState.scrollChatToBottom()
            }
            InitialTailDecision.KeepRestoredPosition,
            InitialTailDecision.NoContent -> Unit
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                // v2 sessions start without a title; match Home's fallback instead of rendering an empty header.
                title = uiState.session?.title?.ifBlank { "Untitled session" } ?: "Chat",
                connectionState = connectionState,
                onBack = onNavigateBack,
                onTerminal = onOpenTerminal,
                onFiles = onOpenFiles,
                onSearch = {
                    scrollRestorationState.showSearch = true
                    scrollRestorationState.currentMatchIndex = 0
                },
                onCommands = {
                    viewModel.refreshCommandsIfNeeded(force = true)
                    showCommandPalette = true
                },
                onViewChanges = {
                    uiState.session?.id?.let { onViewSessionDiff?.invoke(it) }
                },
                branchName = branchName,
                todoCount = if (viewModel.supportsSessionTodos) {
                    uiState.todos.count { it.status == "in_progress" || it.status == "pending" }
                } else {
                    0
                },
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
                    // Hairline separating the flat composer from the chat above it.
                    HorizontalDivider(
                        color = LocalOpenCodeTheme.current.border,
                        thickness = Sizing.strokeThin
                    )
                    uiState.runNotice?.let { notice -> RunNoticeBanner(notice) }
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
                        onToggleFavorite = viewModel.modelAgentManager::toggleFavoriteModel,
                        usedContextTokens = contextUsage?.tokens,
                        contextUsageModel = contextUsage?.let {
                            ModelInput(providerID = it.providerID, modelID = it.modelID)
                        },
                        providerNames = providerNames,
                    )
                    if (uploadState.isActive || uploadState.failures.isNotEmpty()) {
                        val theme = LocalOpenCodeTheme.current
                        TextButton(
                            onClick = { showUploads = true },
                            modifier = Modifier.testTag("review_pending_uploads"),
                        ) {
                            Text(
                                if (uploadState.isActive) {
                                    stringResource(R.string.chat_upload_active_review)
                                } else {
                                    stringResource(R.string.chat_upload_attention_review)
                                },
                                color = if (uploadState.isActive) theme.accent else theme.warning,
                            )
                        }
                    }
                    CompositionLocalProvider(LocalChatMediaLoader provides viewModel.mediaLoader) {
                        ChatInputBar(
                            value = uiState.inputText,
                            valueSyncGeneration = uiState.inputSyncGeneration,
                            onValueChange = { text ->
                                viewModel.updateInput(text)
                                if (text.startsWith("/") && !text.contains(" ")) {
                                    viewModel.refreshCommandsIfNeeded()
                                }
                            },
                            onSend = { viewModel.sendMessage() },
                            isLoading = uiState.isSending,
                            enabled = connectionState is ConnectionState.Connected,
                            isBusy = uiState.isBusy,
                            onAbort = viewModel::abortSession,
                            attachedFiles = attachedFiles,
                            workspaceDirectory = viewModel.filePickerManager.workspace.directory,
                            submissionBlocked = viewModel.filePickerManager.hasUnresolvedUploads,
                            onAttachClick = {
                                if (uploadState.isActive || uploadState.failures.isNotEmpty()) {
                                    showUploads = true
                                } else {
                                    showAttachmentSources = true
                                }
                            },
                            onRemoveAttachment = viewModel.filePickerManager::detachFile,
                            commands = uiState.commands,
                            isLoadingCommands = uiState.isLoadingCommands,
                            commandLoadError = uiState.commandLoadError,
                            onRetryCommands = { viewModel.refreshCommandsIfNeeded(force = true) },
                            onCommandSelected = { /* Command text is already updated via onValueChange */ },
                            requestFocus = requestInitialInputFocus,
                            isActiveTab = isActiveTab,
                            onComposerFocusChanged = { composerFocused = it },
                            promptHistory = promptHistory,
                            promptHistorySessionId = uiState.session?.id,
                            enterToSend = chatSettings.enterToSend,
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (scrollRestorationState.showSearch) {
                ChatSearchBar(
                    query = scrollRestorationState.searchQuery,
                    onQueryChange = { scrollRestorationState.searchQuery = it },
                    matchCount = searchMatches.size,
                    currentIndex = scrollRestorationState.currentMatchIndex,
                    onPrev = {
                        if (searchMatches.isNotEmpty()) {
                            scrollRestorationState.currentMatchIndex =
                                (scrollRestorationState.currentMatchIndex - 1 + searchMatches.size) % searchMatches.size
                        }
                    },
                    onNext = {
                        if (searchMatches.isNotEmpty()) {
                            scrollRestorationState.currentMatchIndex =
                                (scrollRestorationState.currentMatchIndex + 1) % searchMatches.size
                        }
                    },
                    onClose = {
                        scrollRestorationState.showSearch = false
                        scrollRestorationState.searchQuery = ""
                    },
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
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

                val hasContent = hasChatContent(
                    hasMessages = messages.isNotEmpty(),
                    isBusy = uiState.isBusy,
                    hasPendingQuestion = pendingQuestion != null,
                    hasSessionPendingPermissions = sessionPendingPermissions.isNotEmpty(),
                ) || hasRenderableFormContent

                if (!hasContent && !uiState.isLoading) {
                    EmptyChatView(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag("message_list"),
                        contentPadding = PaddingValues(vertical = Spacing.xxs, horizontal = Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.hairline),
                    ) {
                        if (uiState.hasOlderMessages) {
                            item(key = "load_older_messages") {
                                TextButton(
                                    onClick = viewModel::loadOlderMessages,
                                    enabled = !uiState.isLoadingOlderMessages,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (uiState.isLoadingOlderMessages) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(Sizing.iconSm),
                                            strokeWidth = Spacing.hairline,
                                        )
                                        Spacer(Modifier.width(Spacing.xs))
                                    }
                                    Text(stringResource(R.string.chat_load_older_messages))
                                }
                            }
                        }
                        // All messages - stable keys ensure only changed items recompose
                        itemsIndexed(
                            items = messageBlocks,
                            key = { _, block ->
                                when (block) {
                                    is MessageBlock.UserBlock -> block.message.message.id
                                    is MessageBlock.AssistantBlock -> block.messages.first().message.id
                                }
                            }
                        ) { index, block ->
                            val isCurrentMatch = scrollRestorationState.showSearch &&
                                scrollRestorationState.searchQuery.isNotBlank() &&
                                searchMatches.getOrNull(scrollRestorationState.currentMatchIndex)?.blockIndex == index
                            val highlight = if (isCurrentMatch) {
                                Modifier.background(LocalOpenCodeTheme.current.accent.copy(alpha = 0.08f))
                            } else {
                                Modifier
                            }
                            Box(modifier = highlight) {
                                CompositionLocalProvider(LocalChatMediaLoader provides viewModel.mediaLoader) {
                                    MessageBlockView(
                                        block = block,
                                        onToolApprove = { viewModel.respondToPermission(it, "once") },
                                        onToolDeny = { viewModel.respondToPermission(it, "reject") },
                                        onToolAlways = { viewModel.respondToPermission(it, "always") },
                                        onOpenSubSession = onOpenSubSession,
                                        onProviderAuthRequired = onProviderAuthRequired,
                                        defaultToolWidgetState = defaultToolWidgetState,
                                        pendingPermissionsByCallId = pendingPermissionsByCallId,
                                        onRevert = { messageId -> showRevertDialog = messageId }
                                    )
                                }
                            }
                        }

                        itemsIndexed(
                            items = sessionPendingPermissions,
                            key = { _, permission -> "pending_permission_${permission.id}" },
                        ) { _, permission ->
                            InlinePermissionPrompt(
                                permission = permission,
                                onAllow = { viewModel.respondToPermission(permission.id, "once") },
                                onAlways = { viewModel.respondToPermission(permission.id, "always") },
                                onReject = { viewModel.respondToPermission(permission.id, "reject") },
                                modifier = Modifier.padding(vertical = Spacing.xs),
                            )
                        }

                        pendingQuestion?.let { questionRequest ->
                            item(key = "pending_question_${questionRequest.id}") {
                                InlineQuestionCard(
                                    questionRequestId = questionRequest.id,
                                    questionData = dev.blazelight.p4oc.domain.model.QuestionData(
                                        questionRequest.questions
                                    ),
                                    onDismiss = { viewModel.dismissQuestion(questionRequest.id) },
                                    onSubmit = { answers ->
                                        viewModel.respondToQuestion(questionRequest.id, answers)
                                    },
                                    modifier = Modifier.padding(vertical = Spacing.xs)
                                )
                            }
                        }

                        if (pendingV2Forms.isLoading && pendingV2Forms.forms.isEmpty()) {
                            item(key = "pending_forms_loading") {
                                InlineV2FormStatusCard(
                                    message = stringResource(R.string.v2_form_checking_pending),
                                    isLoading = true,
                                    modifier = Modifier.padding(vertical = Spacing.xs),
                                )
                            }
                        }
                        pendingV2Forms.error?.let { error ->
                            item(key = "pending_forms_error") {
                                InlineV2FormStatusCard(
                                    message = error,
                                    isLoading = false,
                                    onRetry = viewModel::refreshPendingForms,
                                    modifier = Modifier.padding(vertical = Spacing.xs),
                                )
                            }
                        }
                        itemsIndexed(
                            items = pendingV2Forms.forms,
                            key = { _, item -> "pending_v2_form_${item.id}" },
                        ) { _, item ->
                            InlineV2FormCard(
                                form = item.form,
                                isLoading = item.isLoading,
                                isSubmitting = item.isSubmitting,
                                error = item.error,
                                onSubmit = { answer -> viewModel.respondToForm(item.id, answer) },
                                onReject = { viewModel.rejectPendingForm(item.id) },
                                onRetry = { viewModel.retryPendingForm(item.id) },
                                modifier = Modifier.padding(vertical = Spacing.xs),
                            )
                        }
                    }
                }

                val activeLoadSteps = buildList {
                    addAll(uiState.loadingSteps)
                    if (isPickerLoading) add("Loading files")
                }
                val overlayPresentation = chatLoadingOverlay(
                    isMessageLoading = uiState.isLoading,
                    hasActiveLoadSteps = activeLoadSteps.isNotEmpty(),
                    hasContent = hasContent,
                )
                if (overlayPresentation != ChatLoadingOverlay.None) {
                    val loadingDescription = stringResource(R.string.cd_loading)
                    val modifier = when (overlayPresentation) {
                        ChatLoadingOverlay.Opaque ->
                            Modifier
                                .matchParentSize()
                                .background(LocalOpenCodeTheme.current.background)
                        ChatLoadingOverlay.Transparent ->
                            Modifier
                                .align(Alignment.Center)
                        ChatLoadingOverlay.None -> Modifier
                    }
                    TuiLoadingScreen(
                        modifier = modifier
                            .testTag("chat_loading_overlay")
                            .semantics { contentDescription = loadingDescription },
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

                ChatJumpNavigationButtons(
                    showPrevious = previousUserBlockIndex != null,
                    showBottom = !isAtBottom,
                    hasNewContent = scrollRestorationState.hasNewContentWhileAway,
                    onPrevious = {
                        previousUserBlockIndex?.let { blockIndex ->
                            coroutineScope.launch {
                                scrollRestorationState.onJumpToPreviousUser()
                                listState.scrollToItem(blockIndex + olderMessagesItemOffset)
                            }
                        }
                    },
                    onBottom = {
                        coroutineScope.launch {
                            scrollRestorationState.onJumpToBottom()
                            listState.scrollChatToBottom()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = Spacing.xl, bottom = Spacing.md)
                )
            }
        }
    }

    if (showCommandPalette) {
        val resolvedCommands = rememberResolvedCommandMetadata(uiState.commands)
        CommandPalette(
            commands = resolvedCommands,
            isLoading = uiState.isLoadingCommands,
            error = uiState.commandLoadError,
            executionError = uiState.error,
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

    if (showAttachmentSources) {
        AttachmentSourceSheet(
            onPhotos = {
                launchedAttachmentOwner = attachmentOwner
                showAttachmentSources = false
                photosLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPhoneFiles = {
                launchedAttachmentOwner = attachmentOwner
                showAttachmentSources = false
                uploadLauncher.launch(arrayOf("*/*"))
            },
            onWorkspace = {
                showAttachmentSources = false
                viewModel.filePickerManager.loadPickerFiles()
                showFilePicker = true
            },
            onDismiss = { showAttachmentSources = false },
        )
    }
    phoneReview
        ?.takeUnless { choosingUploadFolder }
        ?.let { review ->
            val workspace = viewModel.filePickerManager.workspace
            PhoneAttachmentSheet(
                review = review,
                destination =
                    "${workspace.server.displayName}\n" +
                        "${workspace.directory.orEmpty().trimEnd('/')}/${pickerCurrentPath.trim('/')}",
                destinationError =
                    if (workspace.directory == null) {
                        stringResource(R.string.attach_review_workspace_required)
                    } else {
                        pickerError
                    },
                loadingDestination = isPickerLoading,
                onChangeFolder = {
                    choosingUploadFolder = true
                    showFilePicker = true
                },
                onRemove = viewModel.filePickerManager::removePhoneFile,
                onUpload = {
                    showUploads = true
                    viewModel.filePickerManager.confirmPhoneUpload(uploadSource)
                },
                onDismiss = viewModel.filePickerManager::cancelPhoneReview,
            )
        }

    if (showFilePicker) {
        val workspaceDirectory =
            attachmentWorkspace.directory
                ?: stringResource(R.string.picker_server_workspace)
        FilePickerDialog(
            files =
                if (choosingUploadFolder) pickerFiles.filter { it.isDirectory } else pickerFiles,
            currentPath = pickerCurrentPath,
            workspaceLabel = "${attachmentWorkspace.server.displayName}\n$workspaceDirectory",
            isLoading = isPickerLoading,
            error = pickerError,
            selectedFiles = attachedFiles,
            choosingDirectory = choosingUploadFolder,
            onNavigateTo = { path -> viewModel.filePickerManager.loadPickerFiles(path.ifBlank { "." }) },
            onNavigateUp = {
                val parent = pickerCurrentPath.substringBeforeLast("/", "")
                viewModel.filePickerManager.loadPickerFiles(parent.ifBlank { "." })
            },
            onFileSelected = { viewModel.filePickerManager.attachFile(it) },
            onFileDeselected = { viewModel.filePickerManager.detachFile(it) },
            onConfirm = {
                showFilePicker = false
                choosingUploadFolder = false
            },
            onDismiss = {
                showFilePicker = false
                choosingUploadFolder = false
            },
        )
    }

    if (showUploads && (!uploadState.isEmpty || uploadState.isActive)) {
        UploadProgressSheet(
            state = uploadState,
            onCancel = { viewModel.filePickerManager.cancelUploads() },
            // Dismissal must clear the terminal queue, not just hide the sheet: the gate above
            // re-opens on the retained state, and a remembered visibility flag is lost on the
            // next composition (tab re-entry, rotation, restore), so the summary would return.
            onDismiss = {
                viewModel.filePickerManager.dismissUploadResult()
                showUploads = false
            },
            onRetryFailed = { viewModel.filePickerManager.retryFailedUploads() },
            onRemoveFailed = {
                viewModel.filePickerManager.dismissUploadResult()
                showUploads = false
            },
            contextLabel = stringResource(R.string.upload_sheet_draft_context),
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

internal data class AssistantContextUsage(
    val tokens: Long,
    val providerID: String,
    val modelID: String,
)

internal fun latestAssistantContextUsage(messages: List<MessageWithParts>): AssistantContextUsage? =
    messages.asReversed().firstNotNullOfOrNull { messageWithParts ->
        val assistant = messageWithParts.message as? Message.Assistant
            ?: return@firstNotNullOfOrNull null
        val tokens = assistant.tokens.run {
            input + output + reasoning + cacheRead + cacheWrite
        }
        if (tokens <= 0L) {
            null
        } else {
            AssistantContextUsage(
                tokens = tokens,
                providerID = assistant.providerID,
                modelID = assistant.modelID,
            )
        }
    }

@Composable
@Suppress("FunctionNaming")
private fun RunNoticeBanner(message: String) {
    val theme = LocalOpenCodeTheme.current
    Surface(
        color = theme.warning.copy(alpha = 0.15f),
        shape = RectangleShape,
    ) {
        Text(
            text = "! $message",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            style = MaterialTheme.typography.bodySmall,
            color = theme.warning,
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
    onSearch: () -> Unit,
    onCommands: () -> Unit,
    onViewChanges: () -> Unit,
    branchName: String? = null,
    todoCount: Int = 0,
    onTodos: () -> Unit = {}
) {
    val theme = LocalOpenCodeTheme.current
    var showOverflow by remember { mutableStateOf(false) }

    TuiTopBar(
        onNavigateBack = onBack,
        titleContent = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ConnectionDot(state = connectionState)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                // Branch sits below the title so it gets full width instead of
                // being squeezed/truncated in the actions row.
                branchName?.let { branch ->
                    Text(
                        text = "${stringResource(R.string.vcs_branch_prefix)} $branch",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = theme.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        title = title,
        actions = {
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
                    modifier = Modifier.size(Sizing.iconButtonLg).testTag("chat_overflow_button")
                ) {
                    Text(
                        text = "≡",
                        color = theme.text,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                DropdownMenu(
                    expanded = showOverflow,
                    onDismissRequest = { showOverflow = false }
                ) {
                    TuiDropdownMenuItem(
                        text = "⌕ ${stringResource(R.string.chat_search_action)}",
                        onClick = {
                            showOverflow = false
                            onSearch()
                        }
                    )
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

private suspend fun LazyListState.scrollChatToBottom() {
    val target = layoutInfo.totalItemsCount - 1
    if (target >= 0) scrollToItem(target, Int.MAX_VALUE)
}

internal fun List<MessageWithParts>.toPromptHistory(): List<String> {
    val prompts = mapNotNull { messageWithParts ->
        if (messageWithParts.message !is Message.User) return@mapNotNull null
        messageWithParts.parts
            .filterIsInstance<Part.Text>()
            .filter { !it.synthetic && !it.ignored }
            .joinToString(separator = "\n") { it.text }
            .takeIf(String::isNotBlank)
    }
    return prompts.asReversed().distinct().asReversed()
}

// MessageBlock, groupMessagesIntoBlocks, and MessageBlockView are now in MessageBlockUtils.kt
