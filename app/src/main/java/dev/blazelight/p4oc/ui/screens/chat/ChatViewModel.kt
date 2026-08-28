package dev.blazelight.p4oc.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.core.datastore.ChatSettings
import dev.blazelight.p4oc.core.datastore.NotificationSettings
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.haptic.HapticFeedback
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.core.mime.FilenameMimeType
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.ServerConnectionRegistry
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.data.media.ChatMediaLoader
import dev.blazelight.p4oc.data.media.WorkspaceChatMediaLoader
import dev.blazelight.p4oc.data.remote.dto.ExecuteCommandRequest
import dev.blazelight.p4oc.data.remote.dto.InitSessionRequest
import dev.blazelight.p4oc.data.remote.dto.PartInputDto
import dev.blazelight.p4oc.data.remote.dto.PermissionResponseRequest
import dev.blazelight.p4oc.data.remote.dto.QuestionReplyRequest
import dev.blazelight.p4oc.data.remote.dto.RevertSessionRequest
import dev.blazelight.p4oc.data.remote.dto.SendMessageRequest
import dev.blazelight.p4oc.data.remote.mapper.CommandMapper
import dev.blazelight.p4oc.data.remote.mapper.SessionMapper
import dev.blazelight.p4oc.data.remote.mapper.TodoMapper
import dev.blazelight.p4oc.data.session.SessionRepositoryImpl
import dev.blazelight.p4oc.data.session.SessionUiState
import dev.blazelight.p4oc.data.session.presence
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.model.*
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.ui.components.chat.SelectedFile
import dev.blazelight.p4oc.ui.navigation.Screen
import dev.blazelight.p4oc.ui.screens.files.upload.UploadCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Slim coordinator — delegates to sub-managers for message state,
 * dialogs, model/agent selection, and file picking. Retains session
 * lifecycle, message sending, command execution, and SSE event routing.
 */
@Suppress("LargeClass", "LongParameterList")
class ChatViewModel constructor(
    private val savedStateHandle: SavedStateHandle,
    private val workspaceClient: WorkspaceClient,
    private val sessionRepository: SessionRepositoryImpl,
    private val uploadCoordinator: UploadCoordinator,
    private val settingsDataStore: SettingsDataStore,
    private val hapticFeedback: HapticFeedback,
    private val modelSelectionCoordinator: ModelSelectionCoordinator = ModelSelectionCoordinator(),
    private val serverConnectionRegistry: ServerConnectionRegistry? = null,
) : ViewModel() {
    private val sessionId: String = savedStateHandle.get<String>(Screen.Chat.ARG_SESSION_ID)
        ?: throw IllegalArgumentException("sessionId is required for ChatViewModel")
    private val sessionLease = sessionRepository.acquireSession(SessionId(sessionId))

    // JSON serializer for SavedStateHandle persistence
    private val json = Json { ignoreUnknownKeys = true }

    // --- Sub-managers ---
    val dialogManager = DialogQueueManager(savedStateHandle, json, viewModelScope)
    val modelAgentManager = ModelAgentManager(
        workspaceClient,
        settingsDataStore,
        viewModelScope,
        sessionId,
        modelSelectionCoordinator,
        serverConnectionRegistry,
    )
    val filePickerManager = FilePickerManager(workspaceClient, viewModelScope, uploadCoordinator, settingsDataStore)
    val mediaLoader: ChatMediaLoader = WorkspaceChatMediaLoader(workspaceClient, serverConnectionRegistry)

    // --- Core state ---
    private val _uiState = MutableStateFlow(ChatUiState(inputText = restoredInputText()))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private val _sessionMissing = MutableSharedFlow<Unit>(replay = 1)
    val sessionMissing: SharedFlow<Unit> = _sessionMissing.asSharedFlow()

    /** Convenience alias — ChatScreen reads this directly. */
    val messages: StateFlow<List<MessageWithParts>> = sessionRepository.messages(SessionId(sessionId))
    private val repositorySessionState: StateFlow<dev.blazelight.p4oc.data.session.SessionUiState> =
        sessionRepository.sessionUiState(SessionId(sessionId))

    val connectionState: StateFlow<ConnectionState> = workspaceClient.connectionState

    private val _branchName = MutableStateFlow<String?>(null)
    val branchName: StateFlow<String?> = _branchName.asStateFlow()

    // Track whether this tab has unread responses (LLM finished but user hasn't viewed)
    private val _hasUnreadResponse = MutableStateFlow(false)
    val hasUnreadResponse: StateFlow<Boolean> = _hasUnreadResponse.asStateFlow()
    private val _isActiveTab = MutableStateFlow(false)

    // Repository emissions may be collected immediately from init, so every field read by
    // applyRepositorySessionState must be initialized before any collector can launch.
    private var lastResponseCompletedToken = 0L
    private var hasResponseTokenBaseline = false
    private var responseReconciliationJob: Job? = null
    private var initOperationGeneration = 0L
    private var currentInitOperationGeneration: Long? = null
    private var currentInitCommandDispatchOwner: Long? = null
    private var initRequestJob: Job? = null
    private var initTerminalTokenBaseline: Long? = null
    private val commandDispatchGeneration = AtomicLong(NO_COMMAND_DISPATCH_OWNER)
    private val commandDispatchOwner = AtomicLong(NO_COMMAND_DISPATCH_OWNER)
    private var composerSubmissionGeneration = 0L
    private var pendingComposerSubmission: PendingComposerSubmission? = null

    private data class ComposerSubmission(
        val generation: Long,
        val draft: String,
    )

    private data class PendingComposerSubmission(
        val submission: ComposerSubmission,
        val clearAcknowledged: Boolean = false,
        val failed: Boolean = false,
    )

    // True from the moment a send clears the previous run's UI error until the run is confirmed
    // active (Busy/Retry) or reaches a genuine terminal boundary. While set, repository emissions
    // that still carry the previous run's error (todos, permissions, session updates arriving
    // before the synthetic Busy clears it) must not flicker that stale error back into the UI.
    private var suppressStaleRunErrors = false

    init {
        serverConnectionRegistry?.let(::observeCommandCatalogEvents)
    }

    @OptIn(FlowPreview::class)
    private fun observeCommandCatalogEvents(registry: ServerConnectionRegistry) {
        viewModelScope.launch {
            registry.events(workspaceClient.workspace.server)
                .filter { scopedEvent ->
                    val event = scopedEvent.event
                    val refreshesCommands = event is OpenCodeEvent.ModelsRefreshed ||
                        event is OpenCodeEvent.CatalogUpdated ||
                        event is OpenCodeEvent.McpToolsChanged
                    scopedEvent.generation == workspaceClient.generation &&
                        scopedEvent.workspaceKey == workspaceClient.workspace.key &&
                        refreshesCommands
                }
                .debounce(COMMAND_CATALOG_REFRESH_DEBOUNCE_MS)
                .collect { refreshCommandsInBackground() }
        }
    }

    /**
     * UI presence for tab indicators. Awaiting input is reserved for real
     * permission/question prompts; unread responses are a separate state.
     */
    val sessionConnectionState: StateFlow<SessionPresence> = combine(
        repositorySessionState,
        dialogManager.pendingQuestion,
        dialogManager.pendingPermissionsByCallId,
        _hasUnreadResponse,
        messages
    ) { repositoryState: SessionUiState,
        pendingQuestion: QuestionRequest?,
        pendingPermissionsByCallId: Map<String, Permission>,
        hasUnread: Boolean,
        msgs: List<MessageWithParts> ->
        val hasRunningTools = msgs.any { msg ->
            msg.parts.any { part -> part is Part.Tool && part.state is ToolState.Running }
        }
        val hasStreamingText = msgs.any { msg ->
            msg.parts.any { part -> part is Part.Text && part.isStreaming }
        }

        repositoryState.copy(
            pendingQuestion = pendingQuestion,
            pendingPermissionsByCallId = pendingPermissionsByCallId,
        ).presence(
            hasUnread = hasUnread,
            hasStreamingText = hasStreamingText,
            hasRunningTools = hasRunningTools,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionPresence.IDLE)

    val visualSettings = settingsDataStore.visualSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, dev.blazelight.p4oc.core.datastore.VisualSettings())

    val chatSettings = settingsDataStore.chatSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChatSettings())

    private val notificationSettings: StateFlow<NotificationSettings> =
        settingsDataStore.notificationSettings
            .stateIn(viewModelScope, SharingStarted.Eagerly, NotificationSettings())

    private fun beginLoadStep(step: String) {
        _uiState.update { it.copy(loadingSteps = it.loadingSteps + step) }
    }

    private fun endLoadStep(step: String) {
        _uiState.update { it.copy(loadingSteps = it.loadingSteps - step) }
    }

    private companion object {
        const val TAG = "ChatViewModel"
        private const val INITIAL_HISTORY_LIMIT = 100
        private const val HISTORY_PAGE_SIZE = 100
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val KEY_DRAFT_TEXT = "chat_draft_text"
        private const val KEY_ATTACHED_FILES = "chat_attached_files"
        private const val COMMAND_CATALOG_REFRESH_DEBOUNCE_MS = 150L
        private val RESPONSE_RECONCILIATION_DELAYS_MS = listOf(2_000L, 5_000L, 10_000L, 20_000L)
        private const val RUN_STALLED_NOTICE = "No completion update was received. " +
            "The run may still be active; stop it before retrying."

        // SavedState shares Android's Binder transaction budget with the rest of the Activity.
        private const val MAX_PERSISTED_DRAFT_CHARS = 64 * 1024
        private const val MAX_PERSISTED_ATTACHMENTS_JSON_CHARS = 64 * 1024
        private const val UNAVAILABLE_ATTACHMENTS_ERROR =
            "Remove unavailable attachments before sending."
        private const val COMMAND_REFUSED_WHILE_RUNNING_ERROR =
            "Wait for the current run to finish or stop it first."
        private const val NO_COMMAND_DISPATCH_OWNER = 0L

        /**
         * Local command fallbacks used when the server catalog omits them or cannot be loaded.
         * Server-provided metadata takes precedence by command name, while localized fallback
         * descriptions are resolved at Compose display boundaries.
         */
        private val BUILTIN_COMMANDS = listOf(
            Command(name = "compact", source = CommandSource.BuiltIn),
            Command(name = "clear", source = CommandSource.BuiltIn),
            Command(name = "new", source = CommandSource.BuiltIn),
            Command(name = "undo", source = CommandSource.BuiltIn),
            Command(name = "redo", source = CommandSource.BuiltIn),
            Command(name = "share", source = CommandSource.BuiltIn),
            Command(name = "init", source = CommandSource.BuiltIn),
            Command(name = "help", source = CommandSource.BuiltIn),
            Command(name = "connect", source = CommandSource.BuiltIn),
            Command(name = "bug", source = CommandSource.BuiltIn),
        )
    }

    private fun restoredInputText(): String = savedStateHandle.get<String>(KEY_DRAFT_TEXT).orEmpty()

    private fun restoredAttachedFiles(): List<SelectedFile> {
        val jsonString = savedStateHandle.get<String>(KEY_ATTACHED_FILES) ?: return emptyList()
        return try {
            json.decodeFromString<List<SelectedFile>>(jsonString)
        } catch (e: SerializationException) {
            AppLog.e(TAG, "Failed to restore attached files")
            savedStateHandle.remove<String>(KEY_ATTACHED_FILES)
            emptyList()
        } catch (e: IllegalArgumentException) {
            AppLog.e(TAG, "Failed to restore attached files")
            savedStateHandle.remove<String>(KEY_ATTACHED_FILES)
            emptyList()
        }
    }

    private fun persistInputText(text: String) {
        if (text.isEmpty() || text.length > MAX_PERSISTED_DRAFT_CHARS) {
            savedStateHandle.remove<String>(KEY_DRAFT_TEXT)
        } else {
            savedStateHandle[KEY_DRAFT_TEXT] = text
        }
    }

    private fun persistAttachedFiles(files: List<SelectedFile>) {
        if (files.isEmpty()) {
            savedStateHandle.remove<String>(KEY_ATTACHED_FILES)
        } else {
            val encoded = json.encodeToString(files)
            if (encoded.length <= MAX_PERSISTED_ATTACHMENTS_JSON_CHARS) {
                savedStateHandle[KEY_ATTACHED_FILES] = encoded
            } else {
                savedStateHandle.remove<String>(KEY_ATTACHED_FILES)
            }
        }
    }

    private fun observeComposerAttachments() {
        viewModelScope.launch {
            filePickerManager.attachedFiles.collect(::persistAttachedFiles)
        }
    }

    init {
        val restoredFiles = restoredAttachedFiles()
        if (restoredFiles.isNotEmpty()) filePickerManager.restoreAttachedFiles(restoredFiles)
        if (restoredFiles.isNotEmpty()) validateRestoredAttachments()
        observeComposerAttachments()
        loadSession()
        loadMessages()
        modelAgentManager.loadAgents()
        modelAgentManager.loadModels()
        observeEvents()
        loadVcsInfo()
    }

    private fun validateRestoredAttachments() {
        viewModelScope.launch {
            filePickerManager.validateAttachedFiles()
        }
    }

    // --- Public API (delegating) ---

    fun markAsRead() {
        _isActiveTab.value = true
        _hasUnreadResponse.value = false
    }

    fun markInactive() {
        _isActiveTab.value = false
    }

    fun updateInput(text: String) {
        val pendingSubmission = pendingComposerSubmission
        if (pendingSubmission?.failed == true && text.isEmpty()) {
            pendingComposerSubmission = pendingSubmission.copy(clearAcknowledged = true)
            failComposerSubmission(pendingSubmission.submission)
            return
        }
        val effectiveText = when {
            pendingSubmission == null -> text
            text.isNotEmpty() -> {
                pendingComposerSubmission = null
                text
            }
            else -> {
                pendingComposerSubmission = pendingSubmission.copy(clearAcknowledged = true)
                text
            }
        }
        persistInputText(effectiveText)
        _uiState.update { it.copy(inputText = effectiveText) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // --- Session lifecycle ---

    private fun loadSession() {
        viewModelScope.launch {
            beginLoadStep("Loading session metadata")
            val result = safeApiCall { workspaceClient.getSession(sessionId) }
            endLoadStep("Loading session metadata")
            when (result) {
                is ApiResult.Success -> {
                    modelAgentManager.applyServerSessionModel(result.data.model)
                    val session = SessionMapper.mapToDomain(result.data)
                    _uiState.update { it.copy(session = session) }
                    // Reload VCS now that we have the canonical session directory
                    loadVcsInfo()
                }
                is ApiResult.Error -> {
                    if (result.code == 404) {
                        _sessionMissing.emit(Unit)
                    } else {
                        _uiState.update { it.copy(error = "Failed to load session") }
                    }
                }
            }
        }
    }

    private fun loadMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            beginLoadStep("Loading session messages")
            AppLog.d(TAG, "loadMessages() called")

            val result = safeApiCall {
                sessionRepository.loadMessages(SessionId(sessionId), limit = INITIAL_HISTORY_LIMIT)
            }
            endLoadStep("Loading session messages")

            when (result) {
                is ApiResult.Success -> {
                    AppLog.d(TAG, "Loaded ${messages.value.size} messages")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            historyLimit = INITIAL_HISTORY_LIMIT,
                            hasOlderMessages = result.data >= INITIAL_HISTORY_LIMIT,
                        )
                    }
                }
                is ApiResult.Error -> {
                    AppLog.e(TAG, "Failed to load messages")
                    if (result.code == 404) {
                        _sessionMissing.emit(Unit)
                    } else {
                        _uiState.update {
                            it.copy(isLoading = false, error = "Failed to load messages")
                        }
                    }
                }
            }
        }
    }

    fun loadOlderMessages() {
        val current = _uiState.value
        if (current.isLoading || current.isLoadingOlderMessages || !current.hasOlderMessages) return

        val nextLimit = current.historyLimit + HISTORY_PAGE_SIZE
        _uiState.update { it.copy(isLoadingOlderMessages = true) }
        viewModelScope.launch {
            when (
                val result = safeApiCall {
                    sessionRepository.loadMessages(SessionId(sessionId), limit = nextLimit)
                }
            ) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isLoadingOlderMessages = false,
                        historyLimit = nextLimit,
                        hasOlderMessages = result.data >= nextLimit,
                    )
                }
                is ApiResult.Error -> {
                    AppLog.e(TAG, "Failed to load older messages")
                    _uiState.update {
                        it.copy(
                            isLoadingOlderMessages = false,
                            error = "Failed to load older messages",
                        )
                    }
                }
            }
        }
    }

    private fun loadVcsInfo() {
        viewModelScope.launch {
            beginLoadStep("Loading workspace status")
            when (val result = safeApiCall { workspaceClient.getVcsInfo() }) {
                is ApiResult.Success -> _branchName.value = result.data.branch
                is ApiResult.Error -> AppLog.w(TAG, "Failed to load VCS info")
            }
            endLoadStep("Loading workspace status")
        }
    }

    // --- Repository-owned session event state ---

    private fun observeEvents() {
        viewModelScope.launch {
            repositorySessionState.collect { state -> applyRepositorySessionState(state) }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun applyRepositorySessionState(state: dev.blazelight.p4oc.data.session.SessionUiState) {
        dialogManager.setPermissionsByCallId(state.pendingPermissionsByCallId)
        dialogManager.setPendingQuestion(state.pendingQuestion)

        val isBusy = state.status is SessionStatus.Busy || state.status is SessionStatus.Retry
        val isTerminalTransition = hasResponseTokenBaseline &&
            state.responseCompletedToken > lastResponseCompletedToken
        val terminalBelongsToCurrentInit = isTerminalTransition &&
            terminalBelongsToCurrentInit(state.responseCompletedToken)
        val isUnrelatedTerminalDuringInit = isTerminalTransition &&
            currentInitOperationGeneration != null &&
            !terminalBelongsToCurrentInit
        if (!hasResponseTokenBaseline) {
            // The first collected repository state is the subscription snapshot, not a fresh
            // completion: adopt its token as the baseline so a token accumulated before this
            // ViewModel attached (e.g. a run completed in another tab holding the same session)
            // can never fire a spurious completion haptic/unread badge or act as a false
            // terminal boundary for notices and the bounded poll.
            hasResponseTokenBaseline = true
            lastResponseCompletedToken = state.responseCompletedToken
        }
        if (isBusy || (isTerminalTransition && !isUnrelatedTerminalDuringInit)) {
            // The run is confirmed active (its Busy transition already cleared the previous run's
            // repository error) or has genuinely completed (a fresh terminal error is real, not
            // stale). Either way, stale-error suppression for the in-flight send ends now, before
            // the error below is computed.
            suppressStaleRunErrors = false
        }
        val errorMessage = state.error?.takeUnless { it.isAborted() }?.toHumanMessage()
        val retryNotice = (state.status as? SessionStatus.Retry)?.toHumanMessage()
        _uiState.update {
            val ownsCommandEndpoint = commandDispatchOwner.get() != NO_COMMAND_DISPATCH_OWNER
            it.copy(
                session = state.session ?: it.session,
                isBusy = if (isUnrelatedTerminalDuringInit) it.isBusy else isBusy,
                isSending = when {
                    ownsCommandEndpoint -> true
                    state.status != null && !isUnrelatedTerminalDuringInit -> false
                    else -> it.isSending
                },
                todos = state.todos,
                error = if (
                    ownsCommandEndpoint || suppressStaleRunErrors || isUnrelatedTerminalDuringInit
                ) {
                    it.error
                } else {
                    errorMessage ?: it.error
                },
                runNotice = if (isUnrelatedTerminalDuringInit) {
                    it.runNotice
                } else {
                    resolveRunNotice(it.runNotice, retryNotice, isTerminalTransition, isBusy)
                },
            )
        }

        if (isTerminalTransition) {
            if (!isUnrelatedTerminalDuringInit) {
                responseReconciliationJob?.cancel()
                responseReconciliationJob = null
            }
            if (terminalBelongsToCurrentInit) invalidateInitOperation()
            lastResponseCompletedToken = state.responseCompletedToken
            if (!isUnrelatedTerminalDuringInit && state.error?.isAborted() != true) {
                _hasUnreadResponse.value = !_isActiveTab.value
                handleResponseCompleted()
            }
        }
    }

    private fun terminalBelongsToCurrentInit(responseCompletedToken: Long): Boolean {
        val baseline = initTerminalTokenBaseline ?: return false
        return initRequestJob == null && responseCompletedToken > baseline
    }

    private fun handleResponseCompleted() {
        val settings = notificationSettings.value
        hapticFeedback.vibrate(settings.vibrationPattern)
    }

    private fun resolveRunNotice(
        current: String?,
        retryNotice: String?,
        isTerminalTransition: Boolean,
        isBusy: Boolean,
    ): String? = when {
        // A terminal boundary (completion, stop, or terminal error) retires any notice.
        isTerminalTransition -> null
        retryNotice != null -> retryNotice
        // The bounded poll's stalled-run warning stays visible across unrelated repository
        // emissions (todos, permissions, session updates) while the run remains busy; only
        // send/stop/terminal boundaries clear it. A non-busy emission also retires it: the
        // warning ("the run may still be active") is meaningless once the run is not running.
        isBusy && current == RUN_STALLED_NOTICE -> current
        else -> null
    }

    // --- Message sending ---

    @Suppress("ReturnCount")
    fun sendMessage(): Boolean {
        val input = _uiState.value.inputText
        val text = input.trim()
        val attachedFiles = filePickerManager.attachedFiles.value
        if (text.isEmpty() && attachedFiles.isEmpty()) return false
        if (attachedFiles.isEmpty() && text.startsWith("/")) {
            return sendSlashCommand(text, input)
        }
        if (attachedFiles.any { !it.available }) {
            _uiState.update { it.copy(error = UNAVAILABLE_ATTACHMENTS_ERROR) }
            return false
        }

        // A replacement send must supersede any in-flight reconciliation from a prior send so the
        // old poll can never reconcile the wrong assistant/status against the new run.
        invalidateInitOperation()
        responseReconciliationJob?.cancel()
        responseReconciliationJob = null
        val submission = beginComposerSubmission(input)
        suppressStaleRunErrors = true
        _uiState.update { it.copy(isSending = true, error = null, runNotice = null) }
        viewModelScope.launch {
            val validatedFiles = filePickerManager.validateAttachedFiles()
            if (validatedFiles.any { !it.available }) {
                suppressStaleRunErrors = false
                _uiState.update { it.copy(error = UNAVAILABLE_ATTACHMENTS_ERROR, isSending = false) }
                failComposerSubmission(submission)
                return@launch
            }

            sendValidatedMessage(text, validatedFiles, submission)
        }
        return true
    }

    private suspend fun sendValidatedMessage(
        text: String,
        attachedFiles: List<SelectedFile>,
        submission: ComposerSubmission?,
    ) {
        val knownMessageIds = messages.value.mapTo(mutableSetOf()) { it.message.id }
        val selectedAgent = modelAgentManager.selectedAgent.value
        val selectedModel = modelAgentManager.selectedModel.value
        val selectedVariant = modelAgentManager.currentReasoningEffort()
        filePickerManager.clearAttachedFiles()

        val parts = buildPartInputs(text, attachedFiles)
        val request = SendMessageRequest(
            parts = parts,
            agent = selectedAgent,
            model = selectedModel,
            variant = selectedVariant
        )

        val result = sessionRepository.sendMessageAsync(SessionId(sessionId), request).await().toApiResult()
        when (result) {
            is ApiResult.Success -> {
                completeComposerSubmission(submission)
                modelAgentManager.markComposerSelectionSent(selectedModel, selectedVariant)
                sessionRepository.acceptEvent(
                    OpenCodeEvent.SessionStatusChanged(sessionId, SessionStatus.Busy)
                )
                _uiState.update { it.copy(isSending = false, isBusy = true, runNotice = null) }
                startResponseReconciliation(knownMessageIds)
                AppLog.d(TAG, "sendMessage: Async call succeeded, waiting for SSE events")
            }
            is ApiResult.Error -> {
                suppressStaleRunErrors = false
                _uiState.update {
                    it.copy(
                        isSending = false,
                        error = "Could not send the message. Check the connection and try again."
                    )
                }
                failComposerSubmission(submission)
                filePickerManager.restoreAttachedFiles(attachedFiles)
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun startResponseReconciliation(
        knownMessageIds: Set<String>,
        initOwnerGeneration: Long? = null,
    ) {
        if (!ownsResponseReconciliation(initOwnerGeneration)) return
        responseReconciliationJob?.cancel()
        if (!ownsResponseReconciliation(initOwnerGeneration)) return
        responseReconciliationJob = viewModelScope.launch {
            var lastNewAssistant: MessageWithParts? = null
            var observedRetry = false

            RESPONSE_RECONCILIATION_DELAYS_MS.forEach { delayMs ->
                delay(delayMs)
                if (!ownsResponseReconciliation(initOwnerGeneration)) return@launch
                if (!_uiState.value.isBusy) return@launch

                val status = runCatching {
                    workspaceClient.getSessionStatuses(workspaceClient.workspace.directory)[sessionId]
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                }.getOrNull()?.let(SessionMapper::mapStatusToDomain)
                if (!ownsResponseReconciliation(initOwnerGeneration)) return@launch
                observedRetry = observedRetry || status is SessionStatus.Retry

                // Reconcile canonical messages BEFORE publishing any status. A terminal Idle
                // published first bumps responseCompletedToken, whose collector cancels this very
                // job while the recovery fetch is still in flight — the run then ends with the
                // completed assistant reachable only via REST and no user-facing explanation.
                // The repository's canonical active-lease, revision-safe recovery primitive is
                // reused rather than a second message buffer or an unsafe overwrite path.
                runCatching {
                    sessionRepository.reconcileMessages(SessionId(sessionId))
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                }
                if (!ownsResponseReconciliation(initOwnerGeneration)) return@launch
                lastNewAssistant = messages.value.asReversed().firstOrNull { messageWithParts ->
                    messageWithParts.message.id !in knownMessageIds &&
                        messageWithParts.message is Message.Assistant
                } ?: lastNewAssistant

                completedAssistantEvent(lastNewAssistant, status)?.let { event ->
                    if (!ownsResponseReconciliation(initOwnerGeneration)) return@launch
                    sessionRepository.acceptEvent(event)
                    return@launch
                }

                // No assistant completed yet: only non-terminal statuses may be published. A REST
                // Idle with no new assistant must not terminate the run (or cancel this poll);
                // keep polling until an assistant appears or the bounded window is exhausted.
                if (status is SessionStatus.Busy || status is SessionStatus.Retry) {
                    if (!ownsResponseReconciliation(initOwnerGeneration)) return@launch
                    sessionRepository.acceptEvent(OpenCodeEvent.SessionStatusChanged(sessionId, status))
                }
            }

            if (!ownsResponseReconciliation(initOwnerGeneration)) return@launch
            if (!_uiState.value.isBusy) return@launch
            if (!observedRetry) {
                _uiState.update { it.copy(runNotice = RUN_STALLED_NOTICE) }
            }
        }
    }

    private fun ownsResponseReconciliation(initOwnerGeneration: Long?): Boolean =
        initOwnerGeneration == null || isCurrentInitOperation(initOwnerGeneration)

    private fun completedAssistantEvent(
        messageWithParts: MessageWithParts?,
        status: SessionStatus?,
    ): OpenCodeEvent? {
        val assistant = messageWithParts?.message as? Message.Assistant ?: return null
        return when {
            // An authoritative Busy/Retry just fetched from REST means nothing terminal was
            // missed: multi-step runs emit one assistant message per step, so a completed
            // intermediate assistant mid-run must never synthesize a terminal Idle/Error (false
            // completion haptic, unread badge, poll cancellation). The caller republishes the
            // non-terminal status and keeps polling.
            status is SessionStatus.Busy || status is SessionStatus.Retry -> null
            assistant.error != null -> OpenCodeEvent.SessionError(sessionId, assistant.error)
            assistant.completedAt == null && status !is SessionStatus.Idle -> null
            messageWithParts.parts.isEmpty() -> OpenCodeEvent.SessionError(
                sessionId,
                MessageError(
                    name = "EmptyResponseError",
                    message = "The model completed without returning content",
                ),
            )
            else -> OpenCodeEvent.SessionStatusChanged(sessionId, SessionStatus.Idle)
        }
    }

    private fun sendSlashCommand(text: String, submittedDraft: String): Boolean {
        val commandText = text.removePrefix("/")
        val commandName = commandText.substringBefore(" ").trim()
        if (commandName.isEmpty()) return false
        val arguments = commandText.substringAfter(" ", "").trim()
        return executeCommand(commandName, arguments, submittedDraft)
    }

    private fun beginComposerSubmission(draft: String?): ComposerSubmission? {
        pendingComposerSubmission = null
        if (draft == null) return null
        val submission = ComposerSubmission(
            generation = ++composerSubmissionGeneration,
            draft = draft,
        )
        pendingComposerSubmission = PendingComposerSubmission(submission)
        return submission
    }

    private fun completeComposerSubmission(submission: ComposerSubmission?) {
        if (pendingComposerSubmission?.submission?.generation == submission?.generation) {
            pendingComposerSubmission = null
        }
    }

    private fun failComposerSubmission(submission: ComposerSubmission?) {
        val pendingSubmission = pendingComposerSubmission
            ?.takeIf { it.submission.generation == submission?.generation }
            ?: return
        if (pendingSubmission.clearAcknowledged || _uiState.value.inputText.isEmpty()) {
            pendingComposerSubmission = null
            val draft = pendingSubmission.submission.draft
            persistInputText(draft)
            _uiState.update {
                it.copy(
                    inputText = draft,
                    inputSyncGeneration = it.inputSyncGeneration + 1,
                )
            }
        } else {
            pendingComposerSubmission = pendingSubmission.copy(failed = true)
        }
    }

    private fun discardPendingComposerSubmission() {
        pendingComposerSubmission = null
    }

    private fun buildPartInputs(text: String, files: List<SelectedFile>): List<PartInputDto> {
        val parts = mutableListOf<PartInputDto>()
        if (text.isNotEmpty()) {
            parts.add(PartInputDto(type = "text", text = text))
        }
        files.forEach { file ->
            parts.add(
                PartInputDto(
                    type = "file",
                    filename = file.name,
                    mime = file.mimeType ?: FilenameMimeType.resolveOrOctetStream(file.name),
                    url = file.toOpenCodeFileUrl()
                )
            )
        }
        return parts
    }

    private fun SelectedFile.toOpenCodeFileUrl(): String {
        val workspaceDirectory = workspaceClient.workspace.directory
            ?: throw IllegalStateException("Cannot attach workspace file without a workspace directory")
        val absolutePath = File(workspaceDirectory, path).normalize().path
        return URI("file", null, absolutePath, null).toASCIIString()
    }

    // --- Permission / question responses ---

    fun respondToPermission(permissionId: String, response: String) {
        viewModelScope.launch {
            val request = PermissionResponseRequest(reply = response)
            when (val result = safeApiCall { workspaceClient.respondToPermission(sessionId, permissionId, request) }) {
                is ApiResult.Success -> {
                    dialogManager.clearPermission(permissionId)
                    sessionRepository.clearPermission(SessionId(sessionId), permissionId)
                }
                is ApiResult.Error -> _uiState.update {
                    if (result.message.contains("bad request", ignoreCase = true)) {
                        dialogManager.clearPermission(permissionId)
                        sessionRepository.clearPermission(SessionId(sessionId), permissionId)
                        it
                    } else {
                        it.copy(error = "Could not respond to the permission request. Try again.")
                    }
                }
            }
        }
    }

    fun respondToQuestion(requestId: String, answers: List<List<String>>) {
        viewModelScope.launch {
            val request = QuestionReplyRequest(answers = answers)
            when (val result = safeApiCall { workspaceClient.respondToQuestion(sessionId, requestId, request) }) {
                is ApiResult.Success -> sessionRepository.clearQuestion(SessionId(sessionId), requestId)
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        error = "Could not answer the question. Try again."
                    )
                }
            }
        }
    }

    fun dismissQuestion(requestId: String) {
        viewModelScope.launch {
            // Reject the question server-side so the agent's pending request is
            // resolved (otherwise it stays pending forever and the session never
            // goes idle). The local modal is cleared optimistically; the matching
            // question.rejected SSE event (handled in SessionRepositoryImpl) will
            // also reconcile any other attached client.
            when (val result = safeApiCall { workspaceClient.rejectQuestion(sessionId, requestId) }) {
                is ApiResult.Success -> sessionRepository.clearQuestion(SessionId(sessionId), requestId)
                is ApiResult.Error -> {
                    // A NotFound here means it was already resolved elsewhere — clear
                    // locally anyway so the user is not stuck on a dead modal.
                    sessionRepository.clearQuestion(SessionId(sessionId), requestId)
                    AppLog.w(TAG, "Question rejection failed; clearing resolved prompt locally")
                }
            }
        }
    }

    // --- Commands & Todos ---

    fun loadCommands() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingCommands = true,
                    commandLoadError = null,
                    commands = it.commands.ifEmpty { BUILTIN_COMMANDS }
                )
            }
            beginLoadStep("Loading slash commands")
            val result = safeApiCall { workspaceClient.listCommands() }
            endLoadStep("Loading slash commands")
            when (result) {
                is ApiResult.Success -> {
                    AppLog.d(TAG, "loadCommands: Got ${result.data.size} commands from API")
                    val apiCommands = result.data.map { CommandMapper.mapToDomain(it) }
                    val allCommands = mergeCommands(apiCommands, BUILTIN_COMMANDS)
                    _uiState.update {
                        it.copy(
                            commands = allCommands,
                            isLoadingCommands = false,
                            hasLoadedWorkspaceCommands = true,
                            commandLoadError = null
                        )
                    }
                }
                is ApiResult.Error -> {
                    AppLog.e(TAG, "loadCommands failed")
                    _uiState.update {
                        it.copy(
                            commands = it.commands.ifEmpty { BUILTIN_COMMANDS },
                            isLoadingCommands = false,
                            hasLoadedWorkspaceCommands = false,
                            commandLoadError = "Could not load workspace commands. Try again."
                        )
                    }
                }
            }
        }
    }

    private fun refreshCommandsInBackground() {
        viewModelScope.launch {
            when (val result = safeApiCall { workspaceClient.listCommands() }) {
                is ApiResult.Success -> {
                    val apiCommands = result.data.map(CommandMapper::mapToDomain)
                    _uiState.update {
                        it.copy(
                            commands = mergeCommands(apiCommands, BUILTIN_COMMANDS),
                            hasLoadedWorkspaceCommands = true,
                        )
                    }
                }
                is ApiResult.Error -> AppLog.d(TAG, "Background command refresh failed")
            }
        }
    }

    fun refreshCommandsIfNeeded(force: Boolean = false) {
        val state = _uiState.value
        if (state.isLoadingCommands) return
        if (force || !state.hasLoadedWorkspaceCommands) {
            loadCommands()
        }
    }

    fun executeCommand(
        commandName: String,
        arguments: String,
        submittedDraft: String? = null,
    ): Boolean {
        val dispatchOwner = tryClaimCommandDispatch() ?: return false

        return when (commandName.trim().lowercase()) {
            "undo" -> undoSessionCommand(dispatchOwner, submittedDraft)
            "redo" -> redoSessionCommand(dispatchOwner, submittedDraft)
            "init" -> initializeSession(dispatchOwner, submittedDraft)
            else -> {
                val submission = beginComposerSubmission(submittedDraft)
                executeServerCommand(commandName, arguments, dispatchOwner, submission)
                true
            }
        }
    }

    @Suppress("ComplexCondition", "ReturnCount")
    private fun tryClaimCommandDispatch(): Long? {
        val dispatchOwner = commandDispatchGeneration.incrementAndGet()
        while (true) {
            val state = _uiState.value
            if (
                commandDispatchOwner.get() != NO_COMMAND_DISPATCH_OWNER ||
                state.isSending ||
                state.isBusy ||
                currentInitOperationGeneration != null
            ) {
                _uiState.update { it.copy(error = COMMAND_REFUSED_WHILE_RUNNING_ERROR) }
                return null
            }
            if (_uiState.compareAndSet(state, state.copy(isSending = true, error = null))) {
                if (commandDispatchOwner.compareAndSet(NO_COMMAND_DISPATCH_OWNER, dispatchOwner)) {
                    // A repository emission can race the UI CAS above. Once the durable owner is
                    // installed, project its sending state again so the endpoint is never in
                    // flight while the UI appears dispatchable. Preserve a refusal another caller
                    // may already have published during that race.
                    _uiState.update {
                        it.copy(
                            isSending = true,
                            error = it.error?.takeIf { error ->
                                error == COMMAND_REFUSED_WHILE_RUNNING_ERROR
                            },
                        )
                    }
                    return dispatchOwner
                }

                // Another caller won ownership after an unrelated repository emission reopened
                // the UI-state CAS window. Its owner keeps isSending asserted; this caller only
                // contributes the visible refusal.
                _uiState.update { it.copy(error = COMMAND_REFUSED_WHILE_RUNNING_ERROR) }
                return null
            }
        }
    }

    private inline fun finishCommandDispatch(
        dispatchOwner: Long,
        updateUi: (ChatUiState) -> ChatUiState,
    ): Boolean {
        if (!commandDispatchOwner.compareAndSet(dispatchOwner, NO_COMMAND_DISPATCH_OWNER)) {
            return false
        }
        _uiState.update(updateUi)
        return true
    }

    private fun releaseCommandDispatch(dispatchOwner: Long) {
        finishCommandDispatch(dispatchOwner) { it.copy(isSending = false) }
    }

    private fun releaseActiveCommandDispatch(clearSending: Boolean) {
        while (true) {
            val dispatchOwner = commandDispatchOwner.get()
            if (dispatchOwner == NO_COMMAND_DISPATCH_OWNER) return
            if (commandDispatchOwner.compareAndSet(dispatchOwner, NO_COMMAND_DISPATCH_OWNER)) {
                if (currentInitCommandDispatchOwner == dispatchOwner) {
                    currentInitCommandDispatchOwner = null
                }
                if (clearSending) {
                    _uiState.update { it.copy(isSending = false) }
                }
                return
            }
        }
    }

    private fun initializeSession(dispatchOwner: Long, submittedDraft: String?): Boolean {
        val selectedModel = modelAgentManager.selectedModel.value
        if (selectedModel == null) {
            suppressStaleRunErrors = false
            finishCommandDispatch(dispatchOwner) {
                it.copy(
                    isSending = false,
                    error = "Select a model before initializing the session.",
                )
            }
            return false
        }

        val submission = beginComposerSubmission(submittedDraft)
        val operationGeneration = ++initOperationGeneration
        currentInitOperationGeneration = operationGeneration
        currentInitCommandDispatchOwner = dispatchOwner
        initTerminalTokenBaseline = null
        responseReconciliationJob?.cancel()
        responseReconciliationJob = null
        suppressStaleRunErrors = true
        val knownMessageIds = messages.value.mapTo(mutableSetOf()) { it.message.id }
        val request = InitSessionRequest(
            messageID = "msg_${UUID.randomUUID()}",
            providerID = selectedModel.providerID,
            modelID = selectedModel.modelID,
        )

        _uiState.update { it.copy(isSending = true, error = null, runNotice = null) }
        val requestJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            var endpointCompleted = false
            try {
                val result = safeApiCall {
                    sessionRepository.initSession(SessionId(sessionId), request)
                }
                when (result) {
                    is ApiResult.Success -> handleInitCompletion(
                        operationGeneration,
                        dispatchOwner,
                        result.data,
                        knownMessageIds,
                        submission,
                    )
                    is ApiResult.Error -> publishInitFailure(operationGeneration, dispatchOwner, submission)
                }
                endpointCompleted = true
            } finally {
                if (!endpointCompleted) {
                    cancelInitCommandDispatch(operationGeneration, dispatchOwner, submission)
                }
            }
        }
        initRequestJob = requestJob
        requestJob.invokeOnCompletion { cause ->
            if (cause != null) cancelInitCommandDispatch(operationGeneration, dispatchOwner, submission)
        }
        requestJob.start()
        return true
    }

    @Suppress("ReturnCount")
    private fun handleInitCompletion(
        operationGeneration: Long,
        dispatchOwner: Long,
        succeeded: Boolean,
        knownMessageIds: Set<String>,
        submission: ComposerSubmission?,
    ) {
        if (!isCurrentInitOperation(operationGeneration)) return
        if (!succeeded) {
            publishInitFailure(operationGeneration, dispatchOwner, submission)
            return
        }

        if (!isCurrentInitOperation(operationGeneration)) return
        sessionRepository.acceptEvent(
            OpenCodeEvent.SessionStatusChanged(sessionId, SessionStatus.Busy)
        )
        if (!isCurrentInitOperation(operationGeneration)) return
        initTerminalTokenBaseline = repositorySessionState.value.responseCompletedToken
        if (!isCurrentInitOperation(operationGeneration)) return
        if (
            !finishCommandDispatch(dispatchOwner) {
                it.copy(isSending = false, isBusy = true, runNotice = null)
            }
        ) {
            return
        }
        completeComposerSubmission(submission)
        currentInitCommandDispatchOwner = null
        if (!isCurrentInitOperation(operationGeneration)) return
        startResponseReconciliation(knownMessageIds, operationGeneration)
        if (isCurrentInitOperation(operationGeneration)) {
            initRequestJob = null
        }
    }

    @Suppress("ReturnCount")
    private fun publishInitFailure(
        operationGeneration: Long,
        dispatchOwner: Long,
        submission: ComposerSubmission?,
    ) {
        if (!isCurrentInitOperation(operationGeneration)) return
        suppressStaleRunErrors = false
        if (!isCurrentInitOperation(operationGeneration)) return
        if (
            !finishCommandDispatch(dispatchOwner) {
                it.copy(
                    isSending = false,
                    error = "Could not initialize the session. Try again.",
                )
            }
        ) {
            return
        }
        failComposerSubmission(submission)
        currentInitCommandDispatchOwner = null
        completeInitOperation(operationGeneration)
    }

    private fun cancelInitCommandDispatch(
        operationGeneration: Long,
        dispatchOwner: Long,
        submission: ComposerSubmission?,
    ) {
        // Invalidation owns release for a superseded generation, including whether an abort keeps
        // the sending presentation asserted while its own endpoint is pending.
        if (!isCurrentInitOperation(operationGeneration)) return
        suppressStaleRunErrors = false
        releaseCommandDispatch(dispatchOwner)
        completeComposerSubmission(submission)
        currentInitCommandDispatchOwner = null
        completeInitOperation(operationGeneration)
    }

    private fun isCurrentInitOperation(generation: Long): Boolean =
        currentInitOperationGeneration == generation

    private fun completeInitOperation(generation: Long) {
        if (!isCurrentInitOperation(generation)) return
        currentInitOperationGeneration = null
        currentInitCommandDispatchOwner = null
        initRequestJob = null
        initTerminalTokenBaseline = null
    }

    private fun invalidateInitOperation(clearSending: Boolean = true) {
        val dispatchOwner = currentInitCommandDispatchOwner
        discardPendingComposerSubmission()
        initOperationGeneration += 1
        currentInitOperationGeneration = null
        currentInitCommandDispatchOwner = null
        initRequestJob?.cancel()
        initRequestJob = null
        initTerminalTokenBaseline = null
        if (dispatchOwner != null) {
            if (clearSending) {
                releaseCommandDispatch(dispatchOwner)
            } else {
                releaseActiveCommandDispatch(clearSending = false)
            }
        }
    }

    private fun executeServerCommand(
        commandName: String,
        arguments: String,
        dispatchOwner: Long,
        submission: ComposerSubmission?,
    ) {
        viewModelScope.launch {
            try {
                val request = ExecuteCommandRequest(
                    command = commandName,
                    arguments = arguments
                )
                val result = safeApiCall { workspaceClient.executeCommand(sessionId, request) }
                when (result) {
                    is ApiResult.Success -> {
                        val finished = finishCommandDispatch(dispatchOwner) {
                            it.copy(isSending = false, isBusy = true)
                        }
                        if (finished) completeComposerSubmission(submission)
                    }
                    is ApiResult.Error -> {
                        val finished = finishCommandDispatch(dispatchOwner) {
                            it.copy(isSending = false, error = "Could not execute the command. Try again.")
                        }
                        if (finished) failComposerSubmission(submission)
                    }
                }
            } finally {
                releaseCommandDispatch(dispatchOwner)
            }
        }
    }

    fun loadTodos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTodos = true) }
            beginLoadStep("Loading todos")
            val result = safeApiCall { workspaceClient.getSessionTodos(sessionId) }
            endLoadStep("Loading todos")
            when (result) {
                is ApiResult.Success -> {
                    val todos = result.data.map { TodoMapper.mapToDomain(it) }
                    _uiState.update { it.copy(todos = todos, isLoadingTodos = false) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoadingTodos = false) }
                }
            }
        }
    }

    // --- Revert / Unrevert ---

    private fun undoSessionCommand(dispatchOwner: Long, submittedDraft: String?): Boolean {
        val targetMessageId = previousUserMessageBoundary()
        if (targetMessageId == null) {
            finishCommandDispatch(dispatchOwner) {
                it.copy(isSending = false, error = "Nothing to undo")
            }
            return false
        }
        val submission = beginComposerSubmission(submittedDraft)
        revertSessionTo(targetMessageId, "undo", dispatchOwner, submission)
        return true
    }

    private fun redoSessionCommand(dispatchOwner: Long, submittedDraft: String?): Boolean {
        val targetMessageId = nextUserMessageBoundary()
        if (targetMessageId == null) {
            finishCommandDispatch(dispatchOwner) {
                it.copy(isSending = false, error = "Nothing to redo")
            }
            return false
        }
        val submission = beginComposerSubmission(submittedDraft)
        revertSessionTo(targetMessageId, "redo", dispatchOwner, submission)
        return true
    }

    private fun previousUserMessageBoundary(): String? {
        val userMessages = orderedUserMessages()
        val activeRevertIndex = activeRevertIndex(userMessages) ?: userMessages.size
        return userMessages.getOrNull(activeRevertIndex - 1)?.id
    }

    private fun nextUserMessageBoundary(): String? {
        val userMessages = orderedUserMessages()
        val activeRevertIndex = activeRevertIndex(userMessages) ?: return null
        return userMessages.getOrNull(activeRevertIndex + 1)?.id
    }

    private fun orderedUserMessages(): List<Message.User> = messages.value
        .mapNotNull { it.message as? Message.User }
        .sortedBy { it.createdAt }

    private fun activeRevertIndex(userMessages: List<Message.User>): Int? {
        val activeRevertMessageId = _uiState.value.session?.revert?.messageID ?: return null
        return userMessages.indexOfFirst { it.id == activeRevertMessageId }.takeIf { it >= 0 }
    }

    private fun revertSessionTo(
        messageId: String,
        action: String,
        dispatchOwner: Long,
        submission: ComposerSubmission?,
    ) {
        viewModelScope.launch {
            try {
                val request = RevertSessionRequest(messageID = messageId)
                val result = safeApiCall { workspaceClient.revertSession(sessionId, request) }
                when (result) {
                    is ApiResult.Success -> {
                        if (finishCommandDispatch(dispatchOwner) { it.copy(isSending = false) }) {
                            completeComposerSubmission(submission)
                            loadSession()
                        }
                    }
                    is ApiResult.Error -> {
                        val finished = finishCommandDispatch(dispatchOwner) {
                            it.copy(isSending = false, error = "Could not $action. Try again.")
                        }
                        if (finished) failComposerSubmission(submission)
                    }
                }
            } finally {
                releaseCommandDispatch(dispatchOwner)
            }
        }
    }

    fun revertMessage(messageId: String) {
        viewModelScope.launch {
            val request = RevertSessionRequest(messageID = messageId)
            val result = safeApiCall { workspaceClient.revertSession(sessionId, request) }
            when (result) {
                is ApiResult.Success -> {
                    loadSession() // Refresh to get updated revert state
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(error = "Could not revert the session. Try again.") }
                }
            }
        }
    }

    fun unrevertSession() {
        viewModelScope.launch {
            val result = safeApiCall { workspaceClient.unrevertSession(sessionId) }
            when (result) {
                is ApiResult.Success -> {
                    loadSession() // Refresh to clear revert state
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(error = "Could not restore the session. Try again.") }
                }
            }
        }
    }

    // --- Abort ---

    fun abortSession() {
        // Abort supersedes any command endpoint. Retire its dispatch owner immediately so a
        // non-cooperative response cannot publish stale success/error state, but leave the
        // sending presentation asserted until the abort endpoint itself resolves.
        invalidateInitOperation(clearSending = false)
        releaseActiveCommandDispatch(clearSending = false)
        responseReconciliationJob?.cancel()
        responseReconciliationJob = null
        viewModelScope.launch {
            when (val result = sessionRepository.abortSession(SessionId(sessionId)).await().toApiResult()) {
                is ApiResult.Success -> {
                    suppressStaleRunErrors = false
                    sessionRepository.clearStreamingFlags(SessionId(sessionId))
                    _uiState.update { it.copy(isBusy = false, isSending = false, runNotice = null) }
                }
                is ApiResult.Error -> {
                    suppressStaleRunErrors = false
                    _uiState.update {
                        it.copy(isSending = false, error = "Could not stop the run. Try again.")
                    }
                }
            }
        }
    }

    override fun onCleared() {
        invalidateInitOperation()
        releaseActiveCommandDispatch(clearSending = true)
        responseReconciliationJob?.cancel()
        responseReconciliationJob = null
        sessionLease.close()
        super.onCleared()
    }

    private fun dev.blazelight.p4oc.domain.model.MessageError.toHumanMessage(): String = when {
        name == "ProviderAuthError" -> "Provider authentication required"
        isUsageLimit() -> "Model usage limit reached. Try again later or choose another model."
        name == "EmptyResponseError" ->
            "The model returned no response. The provider may be unavailable or rate-limited."
        isRetryable -> "The request failed temporarily. Try again."
        else -> "The run failed. Try again."
    }

    private fun dev.blazelight.p4oc.domain.model.MessageError.isUsageLimit(): Boolean =
        statusCode == HTTP_TOO_MANY_REQUESTS ||
            message.containsUsageLimitMarker() ||
            responseBody.containsUsageLimitMarker()

    private fun SessionStatus.Retry.toHumanMessage(): String =
        if (message.containsUsageLimitMarker()) {
            "Model usage limit reached. OpenCode is retrying${attemptLabel()}."
        } else {
            "The model request failed temporarily. OpenCode is retrying${attemptLabel()}."
        }

    private fun SessionStatus.Retry.attemptLabel(): String =
        if (attempt > 0) " (attempt $attempt)" else ""

    private fun String?.containsUsageLimitMarker(): Boolean {
        val normalized = this?.lowercase().orEmpty()
        return "rate limit" in normalized || "rate-limit" in normalized ||
            "too many requests" in normalized || "status 429" in normalized ||
            "free usage exceeded" in normalized || "free limit" in normalized
    }

    private fun <T> Result<T>.toApiResult(): ApiResult<T> = fold(
        onSuccess = { ApiResult.Success(it) },
        onFailure = { ApiResult.Error(message = it.message ?: "Unknown error", throwable = it) }
    )
}

private fun mergeCommands(
    serverCommands: List<Command>,
    localFallbacks: List<Command>,
): List<Command> {
    val serverCommandNames = serverCommands.mapTo(HashSet(serverCommands.size), Command::name)
    return buildList(serverCommands.size + localFallbacks.size) {
        addAll(serverCommands)
        localFallbacks.filterTo(this) { it.name !in serverCommandNames }
    }
}

/**
 * Core UI state — only session lifecycle, sending state, commands, and todos.
 * Model/agent, file picker, and dialog state are exposed via sub-manager StateFlows.
 */
data class ChatUiState(
    val session: Session? = null,
    val inputText: String = "",
    val inputSyncGeneration: Long = 0,
    val isLoading: Boolean = false,
    val isLoadingOlderMessages: Boolean = false,
    val hasOlderMessages: Boolean = false,
    val historyLimit: Int = 0,
    val loadingSteps: Set<String> = emptySet(),
    val isSending: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null,
    val runNotice: String? = null,
    val commands: List<Command> = emptyList(),
    val isLoadingCommands: Boolean = false,
    val hasLoadedWorkspaceCommands: Boolean = false,
    val commandLoadError: String? = null,
    val todos: List<Todo> = emptyList(),
    val isLoadingTodos: Boolean = false
)
