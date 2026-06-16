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
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.data.remote.dto.ExecuteCommandRequest
import dev.blazelight.p4oc.data.remote.dto.ModelInput
import dev.blazelight.p4oc.data.remote.dto.PartInputDto
import dev.blazelight.p4oc.data.remote.dto.PermissionResponseRequest
import dev.blazelight.p4oc.data.remote.dto.QuestionReplyRequest
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.util.UUID

/**
 * Slim coordinator — delegates to sub-managers for message state,
 * dialogs, model/agent selection, and file picking. Retains session
 * lifecycle, message sending, command execution, and SSE event routing.
 */
class ChatViewModel constructor(
    private val savedStateHandle: SavedStateHandle,
    private val workspaceClient: WorkspaceClient,
    private val sessionRepository: SessionRepositoryImpl,
    private val uploadCoordinator: UploadCoordinator,
    private val connectionManager: ConnectionManager,
    private val settingsDataStore: SettingsDataStore,
    private val hapticFeedback: HapticFeedback,
) : ViewModel() {

    private val sessionId: String = savedStateHandle.get<String>(Screen.Chat.ARG_SESSION_ID)
        ?: throw IllegalArgumentException("sessionId is required for ChatViewModel")

    // JSON serializer for SavedStateHandle persistence
    private val json = Json { ignoreUnknownKeys = true }

    // --- Sub-managers ---
    val dialogManager = DialogQueueManager(savedStateHandle, json, viewModelScope)
    val modelAgentManager = ModelAgentManager(connectionManager, settingsDataStore, viewModelScope, sessionId)
    val filePickerManager = FilePickerManager(workspaceClient, viewModelScope, uploadCoordinator)

    // --- Core state ---
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private val _sessionMissing = MutableSharedFlow<Unit>(replay = 1)
    val sessionMissing: SharedFlow<Unit> = _sessionMissing.asSharedFlow()

    /** Convenience alias — ChatScreen reads this directly. */
    val messages: StateFlow<List<MessageWithParts>> = sessionRepository.messages(SessionId(sessionId))
    private val repositorySessionState: StateFlow<dev.blazelight.p4oc.data.session.SessionUiState> =
        sessionRepository.sessionUiState(SessionId(sessionId))

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState

    private val _branchName = MutableStateFlow<String?>(null)
    val branchName: StateFlow<String?> = _branchName.asStateFlow()

    // Track whether this tab has unread responses (LLM finished but user hasn't viewed)
    private val _hasUnreadResponse = MutableStateFlow(false)
    val hasUnreadResponse: StateFlow<Boolean> = _hasUnreadResponse.asStateFlow()
    private val _isActiveTab = MutableStateFlow(false)

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
        private const val MAX_QUEUED_MESSAGES = 10

        /**
         * Built-in OpenCode commands that aren't returned by the /command API endpoint.
         * These are hardcoded based on OpenCode documentation.
         */
        private val BUILTIN_COMMANDS = listOf(
            Command(
                name = "compact",
                description = "Compact the conversation to reduce context size",
                source = CommandSource.BuiltIn
            ),
            Command(name = "clear", description = "Clear the conversation history", source = CommandSource.BuiltIn),
            Command(name = "new", description = "Start a new conversation", source = CommandSource.BuiltIn),
            Command(name = "undo", description = "Undo the last change", source = CommandSource.BuiltIn),
            Command(name = "redo", description = "Redo the last undone change", source = CommandSource.BuiltIn),
            Command(name = "share", description = "Share the current conversation", source = CommandSource.BuiltIn),
            Command(
                name = "init",
                description = "Initialize OpenCode for this project",
                source = CommandSource.BuiltIn
            ),
            Command(name = "help", description = "Show help information", source = CommandSource.BuiltIn),
            Command(name = "connect", description = "Connect to a provider", source = CommandSource.BuiltIn),
            Command(name = "bug", description = "Report a bug", source = CommandSource.BuiltIn),
        )
    }

    init {
        loadSession()
        loadMessages()
        modelAgentManager.loadAgents()
        modelAgentManager.loadModels()
        observeEvents()
        loadVcsInfo()
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
            AppLog.d(TAG, "loadMessages() called for session: $sessionId")

            val result = safeApiCall { sessionRepository.loadMessages(SessionId(sessionId), limit = null) }
            endLoadStep("Loading session messages")

            when (result) {
                is ApiResult.Success -> {
                    AppLog.d(TAG, "Loaded ${messages.value.size} messages")
                    _uiState.update { it.copy(isLoading = false) }
                }
                is ApiResult.Error -> {
                    AppLog.e(TAG, "Failed to load messages: ${result.message}", result.throwable)
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

    private fun loadVcsInfo() {
        viewModelScope.launch {
            beginLoadStep("Loading workspace status")
            when (val result = safeApiCall { workspaceClient.getVcsInfo() }) {
                is ApiResult.Success -> _branchName.value = result.data.branch
                is ApiResult.Error -> AppLog.w(TAG, "Failed to load VCS info: ${result.message}")
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
            if (!isBusy) sendQueuedMessageIfAny()
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

        val selectedAgent = modelAgentManager.selectedAgent.value
        val selectedModel = modelAgentManager.selectedModel.value
        val selectedVariant = modelAgentManager.currentReasoningEffort()
        _uiState.update { it.copy(inputText = "", isSending = true) }
        filePickerManager.clearAttachedFiles()

        viewModelScope.launch {
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
                    AppLog.d(TAG, "sendMessage: Async call succeeded, waiting for SSE events")
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            inputText = text,
                            error = "Failed to send: ${result.message}"
                        )
                    }
                    filePickerManager.restoreAttachedFiles(attachedFiles)
                }
            }
        }
    }

    private fun sendSlashCommand(text: String) {
        val commandText = text.removePrefix("/")
        val commandName = commandText.substringBefore(" ").trim()
        if (commandName.isEmpty()) return
        val arguments = commandText.substringAfter(" ", "").trim()
        _uiState.update { it.copy(inputText = "") }
        executeCommand(commandName, arguments)
    }

    fun queueMessage() {
        val text = _uiState.value.inputText.trim()
        val attachedFiles = filePickerManager.attachedFiles.value
        if (text.isEmpty() && attachedFiles.isEmpty()) return
        if (_uiState.value.queuedMessages.size >= MAX_QUEUED_MESSAGES) {
            AppLog.w(TAG, "queueMessage: Queue full, ignoring new queued message")
            return
        }

        val selectedAgent = modelAgentManager.selectedAgent.value
        val selectedModel = modelAgentManager.selectedModel.value
        val selectedVariant = modelAgentManager.currentReasoningEffort()

        _uiState.update {
            it.copy(
                inputText = "",
                queuedMessages = it.queuedMessages + QueuedMessage(
                    text = text,
                    attachedFiles = attachedFiles,
                    agent = selectedAgent,
                    model = selectedModel,
                    variant = selectedVariant
                )
            )
        }
        filePickerManager.clearAttachedFiles()
        AppLog.d(TAG, "queueMessage: Queued message with ${text.length} chars, ${attachedFiles.size} files")
    }

    fun cancelQueuedMessage(messageId: String) {
        _uiState.update { state ->
            state.copy(queuedMessages = state.queuedMessages.filterNot { it.id == messageId })
        }
    }

    private fun sendQueuedMessageIfAny() {
        val queued = _uiState.value.queuedMessages.firstOrNull() ?: return

        AppLog.d(TAG, "sendQueuedMessageIfAny: Sending queued message")
        _uiState.update { state ->
            state.copy(
                queuedMessages = state.queuedMessages.drop(1),
                isSending = true
            )
        }

        viewModelScope.launch {
            val parts = buildPartInputs(queued.text, queued.attachedFiles)
            val request = SendMessageRequest(
                parts = parts,
                agent = queued.agent,
                model = queued.model,
                variant = queued.variant
            )

            val result = sessionRepository.sendMessageAsync(SessionId(sessionId), request).await().toApiResult()
            when (result) {
                is ApiResult.Success -> {
                    AppLog.d(TAG, "sendQueuedMessageIfAny: Queued message sent successfully")
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            error = "Failed to send queued message: ${result.message}"
                        )
                    }
                    _uiState.update { state -> state.copy(queuedMessages = listOf(queued) + state.queuedMessages) }
                    filePickerManager.restoreAttachedFiles(queued.attachedFiles)
                }
            }
        }
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
            when (val result = safeApiCall { workspaceClient.respondToPermission(permissionId, request) }) {
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
                        it.copy(error = "Failed to respond to permission: ${result.message}")
                    }
                }
            }
        }
    }

    fun respondToQuestion(requestId: String, answers: List<List<String>>) {
        viewModelScope.launch {
            val request = QuestionReplyRequest(answers = answers)
            when (val result = safeApiCall { workspaceClient.respondToQuestion(requestId, request) }) {
                is ApiResult.Success -> sessionRepository.clearQuestion(SessionId(sessionId))
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        error = "Failed to answer question: ${result.message}"
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
            when (val result = safeApiCall { workspaceClient.rejectQuestion(requestId) }) {
                is ApiResult.Success -> sessionRepository.clearQuestion(SessionId(sessionId))
                is ApiResult.Error -> {
                    // A NotFound here means it was already resolved elsewhere — clear
                    // locally anyway so the user is not stuck on a dead modal.
                    sessionRepository.clearQuestion(SessionId(sessionId))
                    AppLog.w(TAG, "rejectQuestion failed (clearing locally): ${result.message}")
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
                    AppLog.e(TAG, "loadCommands failed: ${result.message}", result.throwable)
                    _uiState.update {
                        it.copy(
                            commands = it.commands.ifEmpty { BUILTIN_COMMANDS },
                            isLoadingCommands = false,
                            hasLoadedWorkspaceCommands = false,
                            commandLoadError = result.message.ifBlank { "Unable to load workspace commands" }
                        )
                    }
                }
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
                        it.copy(isSending = false, error = "Failed to execute command: ${result.message}")
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

    fun revertMessage(messageId: String) {
        viewModelScope.launch {
            val request = dev.blazelight.p4oc.data.remote.dto.RevertSessionRequest(messageID = messageId)
            val result = safeApiCall { workspaceClient.revertSession(sessionId, request) }
            when (result) {
                is ApiResult.Success -> {
                    loadSession() // Refresh to get updated revert state
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(error = "Failed to revert: ${result.message}") }
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
                    _uiState.update { it.copy(error = "Failed to unrevert: ${result.message}") }
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
                    it.copy(error = "Failed to stop run: ${result.message.toHumanAbortError()}")
                }
            }
        }
    }

    private fun String.toHumanAbortError(): String {
        val trimmed = trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return "Unable to stop run"
        return trimmed.ifBlank { "Unable to stop run" }
    }

    private fun dev.blazelight.p4oc.domain.model.MessageError.toHumanMessage(): String =
        message?.toHumanAbortError() ?: "An error occurred"

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
    val loadingSteps: Set<String> = emptySet(),
    val isSending: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null,
    val commands: List<Command> = emptyList(),
    val isLoadingCommands: Boolean = false,
    val hasLoadedWorkspaceCommands: Boolean = false,
    val commandLoadError: String? = null,
    val todos: List<Todo> = emptyList(),
    val isLoadingTodos: Boolean = false,
    val queuedMessages: List<QueuedMessage> = emptyList()
)

data class QueuedMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val attachedFiles: List<SelectedFile> = emptyList(),
    val agent: String? = null,
    val model: ModelInput? = null,
    val variant: String? = null
)
