package dev.blazelight.p4oc.ui.screens.chat

import dev.blazelight.p4oc.core.log.AppLog
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.data.session.SessionRepositoryImpl
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.data.remote.dto.ExecuteCommandRequest
import dev.blazelight.p4oc.data.remote.dto.PartInputDto
import dev.blazelight.p4oc.data.remote.dto.PermissionResponseRequest
import dev.blazelight.p4oc.data.remote.dto.QuestionReplyRequest
import dev.blazelight.p4oc.data.remote.dto.ModelInput
import dev.blazelight.p4oc.data.remote.dto.SendMessageRequest
import dev.blazelight.p4oc.data.remote.mapper.CommandMapper
import dev.blazelight.p4oc.data.remote.mapper.SessionMapper
import dev.blazelight.p4oc.data.remote.mapper.TodoMapper
import dev.blazelight.p4oc.domain.model.*
import dev.blazelight.p4oc.domain.model.SessionConnectionState as TabConnectionState
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.domain.workspace.RelativePath
import dev.blazelight.p4oc.domain.workspace.WorkspacePath
import dev.blazelight.p4oc.domain.workspace.toAttachmentUrl
import dev.blazelight.p4oc.ui.components.chat.AbortSummary
import dev.blazelight.p4oc.ui.components.chat.InterruptedTool
import dev.blazelight.p4oc.ui.components.chat.SelectedFile
import dev.blazelight.p4oc.ui.navigation.Screen
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
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
    private val connectionManager: ConnectionManager,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val sessionId: String = savedStateHandle.get<String>(Screen.Chat.ARG_SESSION_ID)
        ?: throw IllegalArgumentException("sessionId is required for ChatViewModel")

    // Child session IDs (subagent sessions whose parentID == this sessionId)
    private val childSessionIds = mutableSetOf<String>()

    private fun isOwnedSession(eventSessionId: String): Boolean =
        eventSessionId == sessionId || eventSessionId in childSessionIds

    // JSON serializer for SavedStateHandle persistence
    private val json = Json { ignoreUnknownKeys = true }

    // --- Sub-managers ---
    val dialogManager = DialogQueueManager(savedStateHandle, json)
    val modelAgentManager = ModelAgentManager(connectionManager, settingsDataStore, viewModelScope)
    val filePickerManager = FilePickerManager(connectionManager, viewModelScope)

    // --- Core state ---
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** Convenience alias — ChatScreen reads this directly. */
    val messages: StateFlow<List<MessageWithParts>> = sessionRepository.messages(SessionId(sessionId))

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState

    private val _branchName = MutableStateFlow<String?>(null)
    val branchName: StateFlow<String?> = _branchName.asStateFlow()

    // Track whether this tab has unread responses (LLM finished but user hasn't viewed)
    private val _hasUnreadResponse = MutableStateFlow(false)
    val hasUnreadResponse: StateFlow<Boolean> = _hasUnreadResponse.asStateFlow()

    /**
     * Session connection state for tab indicator display.
     * - BUSY: LLM is processing, streaming, or tools are running
     * - AWAITING_INPUT: LLM finished but user hasn't viewed (tab not active)
     * - IDLE: User has viewed the response
     */
    val sessionConnectionState: StateFlow<TabConnectionState> = combine(
        _uiState.map { it.isBusy }.distinctUntilChanged(),
        _hasUnreadResponse,
        messages
    ) { isBusy, hasUnread, msgs ->
        val hasRunningTools = msgs.any { msg ->
            msg.parts.any { part -> part is Part.Tool && part.state is ToolState.Running }
        }
        val hasStreamingText = msgs.any { msg ->
            msg.parts.any { part -> part is Part.Text && part.isStreaming }
        }

        when {
            isBusy || hasRunningTools || hasStreamingText -> TabConnectionState.BUSY
            hasUnread -> TabConnectionState.AWAITING_INPUT
            else -> TabConnectionState.IDLE
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TabConnectionState.IDLE)

    val visualSettings = settingsDataStore.visualSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, dev.blazelight.p4oc.core.datastore.VisualSettings())

    private companion object {
        const val TAG = "ChatViewModel"
        private const val MAX_QUEUED_MESSAGES = 10

        /**
         * Built-in OpenCode commands that aren't returned by the /command API endpoint.
         * These are hardcoded based on OpenCode documentation.
         */
        private val BUILTIN_COMMANDS = listOf(
            Command(name = "compact", description = "Compact the conversation to reduce context size"),
            Command(name = "clear", description = "Clear the conversation history"),
            Command(name = "new", description = "Start a new conversation"),
            Command(name = "undo", description = "Undo the last change"),
            Command(name = "redo", description = "Redo the last undone change"),
            Command(name = "share", description = "Share the current conversation"),
            Command(name = "init", description = "Initialize OpenCode for this project"),
            Command(name = "help", description = "Show help information"),
            Command(name = "connect", description = "Connect to a provider"),
            Command(name = "bug", description = "Report a bug"),
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
        _hasUnreadResponse.value = false
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
            val result = safeApiCall { workspaceClient.getSession(sessionId) }
            when (result) {
                is ApiResult.Success -> {
                    val session = SessionMapper.mapToDomain(result.data)
                    _uiState.update { it.copy(session = session) }
                    // Reload VCS now that we have the canonical session directory
                    loadVcsInfo()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(error = "Failed to load session") }
                }
            }
        }
    }

    private fun loadMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            AppLog.d(TAG, "loadMessages() called for session: $sessionId")

            val result = safeApiCall { sessionRepository.loadMessages(SessionId(sessionId), limit = null) }

            when (result) {
                is ApiResult.Success -> {
                    AppLog.d(TAG, "Loaded ${messages.value.size} messages")
                    _uiState.update { it.copy(isLoading = false) }
                }
                is ApiResult.Error -> {
                    AppLog.e(TAG, "Failed to load messages: ${result.message}", result.throwable)
                    _uiState.update {
                        it.copy(isLoading = false, error = "Failed to load messages")
                    }
                }
            }
        }
    }

    private fun loadVcsInfo() {
        viewModelScope.launch {
            when (val result = safeApiCall { workspaceClient.getVcsInfo() }) {
                is ApiResult.Success -> _branchName.value = result.data.branch
                is ApiResult.Error -> AppLog.w(TAG, "Failed to load VCS info: ${result.message}")
            }
        }
    }

    // --- SSE event routing ---

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeEvents() {
        viewModelScope.launch {
            AppLog.d(TAG, "observeEvents: Starting to collect SSE events")
            // Use flatMapLatest on the connection flow so that if a full reconnect
            // creates a new Connection (with a new EventSource), we automatically
            // switch to the new event stream instead of staying on the stale one.
            connectionManager.connection
                .flatMapLatest { conn ->
                    conn?.eventSource?.events ?: emptyFlow()
                }
                .collect { event ->
                    AppLog.d(TAG, "observeEvents: Received ${event::class.simpleName}")
                    handleEvent(event)
                }
        }
    }

    private fun handleEvent(event: OpenCodeEvent) {
        when (event) {
            is OpenCodeEvent.MessageUpdated -> {
                if (event.message.sessionID == sessionId) {
                    sessionRepository.acceptEvent(event)
                }
            }
            is OpenCodeEvent.MessagePartUpdated -> {
                if (event.part.sessionID == sessionId) {
                    sessionRepository.acceptEvent(event)
                }
            }
            is OpenCodeEvent.MessageRemoved -> {
                if (event.sessionID == sessionId) {
                    sessionRepository.acceptEvent(event)
                }
            }
            is OpenCodeEvent.PartRemoved -> {
                if (event.sessionID == sessionId) {
                    sessionRepository.acceptEvent(event)
                }
            }
            is OpenCodeEvent.PermissionRequested -> {
                if (isOwnedSession(event.permission.sessionID)) {
                    dialogManager.enqueuePermission(event.permission)
                }
            }
            is OpenCodeEvent.QuestionAsked -> {
                if (isOwnedSession(event.request.sessionID)) {
                    dialogManager.enqueueQuestion(event.request)
                }
            }
            is OpenCodeEvent.SessionCreated -> {
                if (event.session.parentID == sessionId) {
                    childSessionIds.add(event.session.id)
                }
            }
            is OpenCodeEvent.SessionStatusChanged -> {
                if (event.sessionID == sessionId) {
                    val wasBusy = _uiState.value.isBusy
                    val isBusy = event.status is SessionStatus.Busy || event.status is SessionStatus.Retry
                    _uiState.update { it.copy(isBusy = isBusy, isSending = false) }

                    // Clear streaming flags when session becomes idle
                    if (wasBusy && !isBusy) {
                        viewModelScope.launch { sessionRepository.clearStreamingFlags(SessionId(sessionId)) }
                        _hasUnreadResponse.value = true
                    }

                    // Send queued message when session becomes idle
                    if (!isBusy) {
                        sendQueuedMessageIfAny()
                    }
                }
            }
            is OpenCodeEvent.SessionUpdated -> {
                if (event.session.id == sessionId) {
                    _uiState.update { it.copy(session = event.session) }
                }
            }
            is OpenCodeEvent.SessionError -> {
                if (event.sessionID == sessionId) {
                    AppLog.e(TAG, "Session error: ${event.error?.message}")
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            isSending = false,
                            error = event.error?.message ?: "An error occurred"
                        )
                    }
                }
            }
            is OpenCodeEvent.SessionIdle -> {
                if (event.sessionID == sessionId) {
                    AppLog.d(TAG, "Session became idle")
                    viewModelScope.launch { sessionRepository.clearStreamingFlags(SessionId(sessionId)) }
                    _uiState.update { it.copy(isBusy = false, isSending = false) }
                    sendQueuedMessageIfAny()
                }
            }
            is OpenCodeEvent.TodoUpdated -> {
                if (event.sessionID == sessionId) {
                    _uiState.update { it.copy(todos = event.todos) }
                }
            }
            is OpenCodeEvent.PermissionReplied -> {
                if (isOwnedSession(event.sessionID)) {
                    dialogManager.clearPermissionByRequestId(event.requestID)
                }
            }
            else -> {}
        }
    }

    // --- Message sending ---

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        val attachedFiles = filePickerManager.attachedFiles.value
        if (text.isEmpty() && attachedFiles.isEmpty()) return

        val selectedAgent = modelAgentManager.selectedAgent.value
        val selectedModel = modelAgentManager.selectedModel.value
        _uiState.update { it.copy(inputText = "", isSending = true, abortSummary = null) }
        filePickerManager.clearAttachedFiles()

        viewModelScope.launch {
            val parts = buildPartInputs(text, attachedFiles)
            val request = SendMessageRequest(
                parts = parts,
                agent = selectedAgent,
                model = selectedModel
            )

            val result = safeApiCall { workspaceClient.sendMessageAsync(sessionId, request) }
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

        _uiState.update {
            it.copy(
                inputText = "",
                queuedMessages = it.queuedMessages + QueuedMessage(
                    text = text,
                    attachedFiles = attachedFiles,
                    agent = selectedAgent,
                    model = selectedModel
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
                model = queued.model
            )

            val result = safeApiCall { workspaceClient.sendMessageAsync(sessionId, request) }
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
            parts.add(PartInputDto(
                type = "file",
                filename = file.name,
                url = WorkspacePath.Relative(RelativePath(file.path)).toAttachmentUrl()
            ))
        }
        return parts
    }

    // --- Permission / question responses ---

    fun respondToPermission(permissionId: String, response: String) {
        viewModelScope.launch {
            val request = PermissionResponseRequest(reply = response)
            safeApiCall { workspaceClient.respondToPermission(permissionId, request) }
            dialogManager.clearPermission(permissionId)
        }
    }

    fun respondToQuestion(requestId: String, answers: List<List<String>>) {
        viewModelScope.launch {
            val request = QuestionReplyRequest(answers = answers)
            safeApiCall { workspaceClient.respondToQuestion(requestId, request) }
            dialogManager.clearQuestion()
        }
    }

    fun dismissQuestion() {
        dialogManager.clearQuestion()
    }

    // --- Commands & Todos ---

    fun loadCommands() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCommands = true) }
            val result = safeApiCall { workspaceClient.listCommands() }
            when (result) {
                is ApiResult.Success -> {
                    AppLog.d(TAG, "loadCommands: Got ${result.data.size} commands from API")
                    val apiCommands = result.data.map { CommandMapper.mapToDomain(it) }
                    val allCommands = (BUILTIN_COMMANDS + apiCommands).distinctBy { it.name }
                    _uiState.update { it.copy(commands = allCommands, isLoadingCommands = false) }
                }
                is ApiResult.Error -> {
                    AppLog.e(TAG, "loadCommands failed: ${result.message}", result.throwable)
                    _uiState.update {
                        it.copy(
                            commands = BUILTIN_COMMANDS,
                            isLoadingCommands = false
                        )
                    }
                }
            }
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
            val result = safeApiCall { workspaceClient.getSessionTodos(sessionId) }
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
                    loadSession()  // Refresh to get updated revert state
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
                    loadSession()  // Refresh to clear revert state
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
            // Snapshot state BEFORE clearing flags
            val summary = buildAbortSummary()

            safeApiCall { workspaceClient.abortSession(sessionId) }
            sessionRepository.clearStreamingFlags(SessionId(sessionId))
            _uiState.update { it.copy(isBusy = false, isSending = false, abortSummary = summary) }
        }
    }

    private suspend fun buildAbortSummary(): AbortSummary {
        val snapshot = messages.value

        val runningTools = snapshot
            .flatMap { it.parts }
            .filterIsInstance<Part.Tool>()
            .filter { it.state is ToolState.Running }
            .map { tool ->
                val running = tool.state as ToolState.Running
                InterruptedTool(
                    toolName = tool.toolName,
                    context = running.title?.take(40)
                )
            }

        val wasStreaming = snapshot
            .flatMap { it.parts }
            .any { it is Part.Text && it.isStreaming }

        val lastAssistant = snapshot
            .map { it.message }
            .filterIsInstance<Message.Assistant>()
            .lastOrNull()

        return AbortSummary(
            interruptedTools = runningTools,
            wasTextStreaming = wasStreaming,
            tokens = lastAssistant?.tokens,
            cost = lastAssistant?.cost
        )
    }
}

/**
 * Core UI state — only session lifecycle, sending state, commands, and todos.
 * Model/agent, file picker, and dialog state are exposed via sub-manager StateFlows.
 */
data class ChatUiState(
    val session: Session? = null,
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null,
    val commands: List<Command> = emptyList(),
    val isLoadingCommands: Boolean = false,
    val todos: List<Todo> = emptyList(),
    val isLoadingTodos: Boolean = false,
    val queuedMessages: List<QueuedMessage> = emptyList(),
    val abortSummary: AbortSummary? = null
)

data class QueuedMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val attachedFiles: List<SelectedFile> = emptyList(),
    val agent: String? = null,
    val model: ModelInput? = null
)
