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
import dev.blazelight.p4oc.data.remote.dto.ExecuteCommandRequest
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI

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
        private const val KEY_DRAFT_TEXT = "chat_draft_text"
        private const val KEY_ATTACHED_FILES = "chat_attached_files"
        private const val COMMAND_CATALOG_REFRESH_DEBOUNCE_MS = 150L

        // SavedState shares Android's Binder transaction budget with the rest of the Activity.
        private const val MAX_PERSISTED_DRAFT_CHARS = 64 * 1024
        private const val MAX_PERSISTED_ATTACHMENTS_JSON_CHARS = 64 * 1024
        private const val UNAVAILABLE_ATTACHMENTS_ERROR =
            "Remove unavailable attachments before sending."

        /**
         * Built-in OpenCode commands that aren't returned by the /command API endpoint.
         * Localized descriptions are resolved at Compose display boundaries.
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

    /** Reconciles REST-backed chat state after Android resumes from the background. */
    fun refreshAfterForeground() {
        loadSession()
        loadMessages()
    }

    fun updateInput(text: String) {
        persistInputText(text)
        _uiState.update { it.copy(inputText = text) }
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

    private var lastResponseCompletedToken = 0L

    private fun applyRepositorySessionState(state: dev.blazelight.p4oc.data.session.SessionUiState) {
        dialogManager.setPermissionsByCallId(state.pendingPermissionsByCallId)
        dialogManager.setPendingQuestion(state.pendingQuestion)

        val isBusy = state.status is SessionStatus.Busy || state.status is SessionStatus.Retry
        val errorMessage = state.error?.takeUnless { it.isAborted() }?.toHumanMessage()
        _uiState.update {
            it.copy(
                session = state.session ?: it.session,
                isBusy = isBusy,
                isSending = if (state.status != null) false else it.isSending,
                todos = state.todos,
                error = errorMessage ?: it.error,
            )
        }

        if (state.responseCompletedToken > lastResponseCompletedToken) {
            lastResponseCompletedToken = state.responseCompletedToken
            if (state.error?.isAborted() != true) {
                _hasUnreadResponse.value = !_isActiveTab.value
                handleResponseCompleted()
            }
        }
    }

    private fun handleResponseCompleted() {
        val settings = notificationSettings.value
        hapticFeedback.vibrate(settings.vibrationPattern)
    }

    // --- Message sending ---

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        val attachedFiles = filePickerManager.attachedFiles.value
        if (text.isEmpty() && attachedFiles.isEmpty()) return
        if (attachedFiles.isEmpty() && text.startsWith("/")) {
            sendSlashCommand(text)
            return
        }

        _uiState.update { it.copy(isSending = true) }
        viewModelScope.launch {
            val validatedFiles = filePickerManager.validateAttachedFiles()
            if (validatedFiles.any { !it.available }) {
                _uiState.update { it.copy(error = UNAVAILABLE_ATTACHMENTS_ERROR, isSending = false) }
                return@launch
            }

            sendValidatedMessage(text, validatedFiles)
        }
    }

    private suspend fun sendValidatedMessage(text: String, attachedFiles: List<SelectedFile>) {
        val selectedAgent = modelAgentManager.selectedAgent.value
        val selectedModel = modelAgentManager.selectedModel.value
        val selectedVariant = modelAgentManager.currentReasoningEffort()
        updateInput("")
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
                _uiState.update { it.copy(isSending = false, isBusy = true) }
                AppLog.d(TAG, "sendMessage: Async call succeeded, waiting for SSE events")
            }
            is ApiResult.Error -> {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        error = "Could not send the message. Check the connection and try again."
                    )
                }
                updateInput(text)
                filePickerManager.restoreAttachedFiles(attachedFiles)
            }
        }
    }

    private fun sendSlashCommand(text: String) {
        val commandText = text.removePrefix("/")
        val commandName = commandText.substringBefore(" ").trim()
        if (commandName.isEmpty()) return
        val arguments = commandText.substringAfter(" ", "").trim()
        updateInput("")
        executeCommand(commandName, arguments)
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
                    val allCommands = (BUILTIN_COMMANDS + apiCommands).distinctBy { it.name }
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
                            commands = (BUILTIN_COMMANDS + apiCommands).distinctBy(Command::name),
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

    fun executeCommand(commandName: String, arguments: String) {
        when (commandName.trim().lowercase()) {
            "undo" -> undoSessionCommand()
            "redo" -> redoSessionCommand()
            else -> executeServerCommand(commandName, arguments)
        }
    }

    private fun executeServerCommand(commandName: String, arguments: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            val request = ExecuteCommandRequest(
                command = commandName,
                arguments = arguments
            )
            val result = safeApiCall { workspaceClient.executeCommand(sessionId, request) }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSending = false, isBusy = true) }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(isSending = false, error = "Could not execute the command. Try again.")
                    }
                }
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

    private fun undoSessionCommand() {
        val targetMessageId = previousUserMessageBoundary()
        if (targetMessageId == null) {
            _uiState.update { it.copy(error = "Nothing to undo") }
            return
        }
        revertSessionTo(targetMessageId, "undo")
    }

    private fun redoSessionCommand() {
        val targetMessageId = nextUserMessageBoundary()
        if (targetMessageId == null) {
            _uiState.update { it.copy(error = "Nothing to redo") }
            return
        }
        revertSessionTo(targetMessageId, "redo")
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

    private fun revertSessionTo(messageId: String, action: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            val request = RevertSessionRequest(messageID = messageId)
            val result = safeApiCall { workspaceClient.revertSession(sessionId, request) }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSending = false) }
                    loadSession()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isSending = false, error = "Could not $action. Try again.") }
                }
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
        viewModelScope.launch {
            when (val result = sessionRepository.abortSession(SessionId(sessionId)).await().toApiResult()) {
                is ApiResult.Success -> {
                    sessionRepository.clearStreamingFlags(SessionId(sessionId))
                    _uiState.update { it.copy(isBusy = false, isSending = false) }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(error = "Could not stop the run. Try again.")
                }
            }
        }
    }

    override fun onCleared() {
        sessionLease.close()
        super.onCleared()
    }

    private fun dev.blazelight.p4oc.domain.model.MessageError.toHumanMessage(): String = when {
        name == "ProviderAuthError" -> "Provider authentication required"
        isRetryable -> "The request failed temporarily. Try again."
        else -> "The run failed. Try again."
    }

    private fun <T> Result<T>.toApiResult(): ApiResult<T> = fold(
        onSuccess = { ApiResult.Success(it) },
        onFailure = { ApiResult.Error(message = it.message ?: "Unknown error", throwable = it) }
    )
}

/**
 * Core UI state — only session lifecycle, sending state, commands, and todos.
 * Model/agent, file picker, and dialog state are exposed via sub-manager StateFlows.
 */
data class ChatUiState(
    val session: Session? = null,
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isLoadingOlderMessages: Boolean = false,
    val hasOlderMessages: Boolean = false,
    val historyLimit: Int = 0,
    val loadingSteps: Set<String> = emptySet(),
    val isSending: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null,
    val commands: List<Command> = emptyList(),
    val isLoadingCommands: Boolean = false,
    val hasLoadedWorkspaceCommands: Boolean = false,
    val commandLoadError: String? = null,
    val todos: List<Todo> = emptyList(),
    val isLoadingTodos: Boolean = false
)
