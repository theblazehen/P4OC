package dev.blazelight.p4oc.ui.screens.chat

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.DirectoryManager
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.data.remote.dto.AgentDto
import dev.blazelight.p4oc.data.remote.dto.ExecuteCommandRequest
import dev.blazelight.p4oc.data.remote.dto.ModelDto
import dev.blazelight.p4oc.data.remote.dto.ModelInput
import dev.blazelight.p4oc.data.remote.dto.PartInputDto
import dev.blazelight.p4oc.data.remote.dto.PermissionResponseRequest
import dev.blazelight.p4oc.data.remote.dto.QuestionReplyRequest
import dev.blazelight.p4oc.data.remote.dto.SendMessageRequest
import dev.blazelight.p4oc.data.remote.mapper.CommandMapper
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.data.remote.mapper.TodoMapper
import dev.blazelight.p4oc.data.remote.mapper.PartMapper
import dev.blazelight.p4oc.data.remote.mapper.SessionMapper
import dev.blazelight.p4oc.domain.model.*
import dev.blazelight.p4oc.domain.model.SessionConnectionState as TabConnectionState
import dev.blazelight.p4oc.ui.components.chat.SelectedFile
import dev.blazelight.p4oc.ui.navigation.Screen
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


class ChatViewModel constructor(
    private val savedStateHandle: SavedStateHandle,
    private val connectionManager: ConnectionManager,
    private val directoryManager: DirectoryManager,
    private val sessionMapper: SessionMapper,
    private val messageMapper: MessageMapper,
    private val partMapper: PartMapper,
    private val commandMapper: CommandMapper,
    private val todoMapper: TodoMapper,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val sessionId: String = savedStateHandle.get<String>(Screen.Chat.ARG_SESSION_ID)
        ?: throw IllegalArgumentException("sessionId is required for ChatViewModel")
    private val sessionDirectory: String? = savedStateHandle.get<String>(Screen.Chat.ARG_DIRECTORY)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _messagesMap: SnapshotStateMap<String, MessageWithParts> = mutableStateMapOf()
    
    // Version counter to trigger flow emission when map values change
    // SnapshotStateMap only detects key add/remove, not value updates
    private val _messagesVersion = mutableStateOf(0L)
    
    /**
     * Messages flow - emits when any message in the map changes.
     * 
     * We use a version counter inside snapshotFlow to detect value changes.
     * The snapshotFlow will emit whenever _messagesVersion changes.
     */
    val messages: StateFlow<List<MessageWithParts>> = snapshotFlow {
        // Read version to establish dependency - triggers emission on value changes
        _messagesVersion.value
        _messagesMap.values.sortedBy { it.message.createdAt }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val pendingPermissions = ConcurrentLinkedQueue<Permission>()
    private val pendingQuestions = ConcurrentLinkedQueue<QuestionRequest>()
    
    // Mutex to protect message map updates from race conditions
    private val messagesMutex = Mutex()
    
    // JSON serializer for SavedStateHandle persistence
    private val json = Json { ignoreUnknownKeys = true }
    
    // SavedStateHandle keys for question/permission persistence
    private companion object {
        const val TAG = "ChatViewModel"
        const val KEY_PENDING_QUESTION = "pending_question"
        const val KEY_PENDING_QUESTIONS_QUEUE = "pending_questions_queue"
        const val KEY_PENDING_PERMISSION = "pending_permission"
        const val KEY_PENDING_PERMISSIONS_QUEUE = "pending_permissions_queue"
        
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

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState

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
        // Check if any tools are currently running or any text is streaming
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
    
    /**
     * Mark the session as read - clears the unread indicator.
     * Call this when the tab becomes active.
     */
    fun markAsRead() {
        _hasUnreadResponse.value = false
    }

    val favoriteModels: StateFlow<Set<ModelInput>> = settingsDataStore.favoriteModels
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    
    val recentModels: StateFlow<List<ModelInput>> = settingsDataStore.recentModels
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val visualSettings = settingsDataStore.visualSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, dev.blazelight.p4oc.core.datastore.VisualSettings())

    init {
        restorePendingDialogState()
        loadSession()
        loadMessages()
        loadAgents()
        loadModels()
        observeEvents()
    }

    /**
     * Restore pending question/permission state from SavedStateHandle after process death
     */
    private fun restorePendingDialogState() {
        // Restore pending question
        savedStateHandle.get<String>(KEY_PENDING_QUESTION)?.let { jsonString ->
            try {
                val question = json.decodeFromString<QuestionRequest>(jsonString)
                _uiState.update { it.copy(pendingQuestion = question) }
                Log.d(TAG, "Restored pending question: ${question.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore pending question", e)
                savedStateHandle.remove<String>(KEY_PENDING_QUESTION)
            }
        }
        
        // Restore pending questions queue
        savedStateHandle.get<String>(KEY_PENDING_QUESTIONS_QUEUE)?.let { jsonString ->
            try {
                val questions = json.decodeFromString<List<QuestionRequest>>(jsonString)
                pendingQuestions.addAll(questions)
                Log.d(TAG, "Restored ${questions.size} queued questions")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore pending questions queue", e)
                savedStateHandle.remove<String>(KEY_PENDING_QUESTIONS_QUEUE)
            }
        }
        
        // Restore pending permission
        savedStateHandle.get<String>(KEY_PENDING_PERMISSION)?.let { jsonString ->
            try {
                val permission = json.decodeFromString<Permission>(jsonString)
                _uiState.update { it.copy(pendingPermission = permission) }
                Log.d(TAG, "Restored pending permission: ${permission.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore pending permission", e)
                savedStateHandle.remove<String>(KEY_PENDING_PERMISSION)
            }
        }
        
        // Restore pending permissions queue
        savedStateHandle.get<String>(KEY_PENDING_PERMISSIONS_QUEUE)?.let { jsonString ->
            try {
                val permissions = json.decodeFromString<List<Permission>>(jsonString)
                pendingPermissions.addAll(permissions)
                Log.d(TAG, "Restored ${permissions.size} queued permissions")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore pending permissions queue", e)
                savedStateHandle.remove<String>(KEY_PENDING_PERMISSIONS_QUEUE)
            }
        }
    }

    private fun getDirectory(): String? = sessionDirectory ?: _uiState.value.session?.directory ?: directoryManager.getDirectory()

    private fun loadSession() {
        viewModelScope.launch {
            val api = connectionManager.getApi() ?: run {
                _uiState.update { it.copy(error = "Not connected") }
                return@launch
            }
            val result = safeApiCall { api.getSession(sessionId, sessionDirectory ?: directoryManager.getDirectory()) }
            when (result) {
                is ApiResult.Success -> {
                    val session = sessionMapper.mapToDomain(result.data)
                    _uiState.update { it.copy(session = session) }
                    if (sessionDirectory == null && session.directory.isNotBlank()) {
                        directoryManager.setDirectory(session.directory)
                    }
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
            Log.d(TAG, "loadMessages() called for session: $sessionId")

            val api = connectionManager.getApi() ?: run {
                _uiState.update { it.copy(isLoading = false, error = "Not connected") }
                return@launch
            }
            
            val directory = getDirectory()
            val result = safeApiCall { api.getMessages(sessionId, limit = null, directory = directory) }

            when (result) {
                is ApiResult.Success -> {
                    Log.d(TAG, "Loaded ${result.data.size} messages")
                    result.data.forEach { dto ->
                        val msg = messageMapper.mapWrapperToDomain(dto, partMapper)
                        _messagesMap[msg.message.id] = msg
                    }
                    _uiState.update { it.copy(isLoading = false) }
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to load messages: ${result.message}", result.throwable)
                    _uiState.update { 
                        it.copy(isLoading = false, error = "Failed to load messages") 
                    }
                }
            }
        }
    }

    private fun observeEvents() {
        viewModelScope.launch {
            Log.d(TAG, "observeEvents: Starting to collect SSE events")
            connectionManager.getEventSource()?.events?.collect { event ->
                Log.d(TAG, "observeEvents: Received ${event::class.simpleName}")
                handleEvent(event)
            }
        }
    }

    private fun handleEvent(event: OpenCodeEvent) {
        when (event) {
            is OpenCodeEvent.MessageUpdated -> {
                if (event.message.sessionID == sessionId) {
                    upsertMessage(event.message)
                }
            }
            is OpenCodeEvent.MessagePartUpdated -> {
                if (event.part.sessionID == sessionId) {
                    upsertPart(event.part, event.delta)
                }
            }
            is OpenCodeEvent.PermissionRequested -> {
                if (event.permission.sessionID == sessionId) {
                    pendingPermissions.offer(event.permission)
                    showNextPermission()
                }
            }
            is OpenCodeEvent.QuestionAsked -> {
                if (event.request.sessionID == sessionId) {
                    pendingQuestions.offer(event.request)
                    showNextQuestion()
                }
            }
            is OpenCodeEvent.SessionStatusChanged -> {
                if (event.sessionID == sessionId) {
                    val wasBusy = _uiState.value.isBusy
                    val isBusy = event.status is SessionStatus.Busy || event.status is SessionStatus.Retry
                    _uiState.update { it.copy(isBusy = isBusy, isSending = if (!isBusy) false else it.isSending) }
                    
                    // Clear streaming flags when session becomes idle
                    if (wasBusy && !isBusy) {
                        viewModelScope.launch {
                            messagesMutex.withLock {
                                clearStreamingFlags()
                            }
                        }
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
                    Log.e(TAG, "Session error: ${event.error?.message}")
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
                    Log.d(TAG, "Session became idle")
                    viewModelScope.launch {
                        messagesMutex.withLock {
                            clearStreamingFlags()
                        }
                    }
                    _uiState.update { it.copy(isBusy = false, isSending = false) }
                    sendQueuedMessageIfAny()
                }
            }
            is OpenCodeEvent.TodoUpdated -> {
                if (event.sessionID == sessionId) {
                    _uiState.update { it.copy(todos = event.todos) }
                }
            }
            else -> {}
        }
    }

    private fun showNextPermission() {
        if (_uiState.value.pendingPermission == null) {
            pendingPermissions.poll()?.let { permission ->
                _uiState.update { it.copy(pendingPermission = permission) }
                // Persist to SavedStateHandle for process death survival
                savedStateHandle[KEY_PENDING_PERMISSION] = json.encodeToString(permission)
            }
        }
        // Persist remaining queue
        persistPermissionsQueue()
    }

    private fun showNextQuestion() {
        if (_uiState.value.pendingQuestion == null) {
            pendingQuestions.poll()?.let { question ->
                _uiState.update { it.copy(pendingQuestion = question) }
                // Persist to SavedStateHandle for process death survival
                savedStateHandle[KEY_PENDING_QUESTION] = json.encodeToString(question)
            }
        }
        // Persist remaining queue
        persistQuestionsQueue()
    }
    
    private fun persistQuestionsQueue() {
        val queueList = pendingQuestions.toList()
        if (queueList.isNotEmpty()) {
            savedStateHandle[KEY_PENDING_QUESTIONS_QUEUE] = json.encodeToString(queueList)
        } else {
            savedStateHandle.remove<String>(KEY_PENDING_QUESTIONS_QUEUE)
        }
    }
    
    private fun persistPermissionsQueue() {
        val queueList = pendingPermissions.toList()
        if (queueList.isNotEmpty()) {
            savedStateHandle[KEY_PENDING_PERMISSIONS_QUEUE] = json.encodeToString(queueList)
        } else {
            savedStateHandle.remove<String>(KEY_PENDING_PERMISSIONS_QUEUE)
        }
    }

    private fun upsertMessage(message: Message) {
        viewModelScope.launch {
            messagesMutex.withLock {
                val existing = _messagesMap[message.id]
                _messagesMap[message.id] = if (existing != null) {
                    existing.copy(message = message)
                } else {
                    MessageWithParts(message, emptyList())
                }
                _messagesVersion.value++
                Log.d(TAG, "upsertMessage: ${message.id}, exists=${existing != null}")
            }
        }
    }

    /**
     * Handle part updates - simple approach.
     * 
     * All parts go to _messagesMap. SnapshotStateMap + stable LazyColumn keys
     * ensure only the changed message item recomposes.
     */
    private fun upsertPart(part: Part, delta: String?) {
        viewModelScope.launch {
            messagesMutex.withLock {
                val messageId = part.messageID
                
                val existing = _messagesMap[messageId] ?: run {
                    val placeholder = createPlaceholderMessage(messageId)
                    _messagesMap[messageId] = placeholder
                    Log.d(TAG, "upsertPart: Created placeholder for message $messageId")
                    placeholder
                }
                
                val partIndex = existing.parts.indexOfFirst { it.id == part.id }
                val updatedParts = if (partIndex >= 0) {
                    existing.parts.toMutableList().apply {
                        this[partIndex] = applyDelta(this[partIndex], part, delta)
                    }
                } else {
                    existing.parts + part
                }
                
                _messagesMap[messageId] = existing.copy(parts = updatedParts)
                _messagesVersion.value++
                Log.d(TAG, "upsertPart: partId=${part.id}, messageId=$messageId, delta=${delta?.length ?: 0} chars, partCount=${updatedParts.size}")
            }
        }
    }

    private fun applyDelta(existing: Part, incoming: Part, delta: String?): Part {
        return if (delta != null && incoming is Part.Text && existing is Part.Text) {
            incoming.copy(text = existing.text + delta, isStreaming = true)
        } else {
            incoming
        }
    }

    private fun createPlaceholderMessage(messageId: String): MessageWithParts {
        return MessageWithParts(
            message = Message.Assistant(
                id = messageId,
                sessionID = sessionId,
                createdAt = System.currentTimeMillis(),
                parentID = "",
                providerID = "",
                modelID = "",
                mode = "",
                agent = "",
                cost = 0.0,
                tokens = TokenUsage(input = 0, output = 0)
            ),
            parts = emptyList()
        )
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        val attachedFiles = _uiState.value.attachedFiles
        if (text.isEmpty() && attachedFiles.isEmpty()) return

        val selectedAgent = _uiState.value.selectedAgent
        val selectedModel = _uiState.value.selectedModel
        _uiState.update { it.copy(inputText = "", attachedFiles = emptyList(), isSending = true) }

        viewModelScope.launch {
            val api = connectionManager.getApi() ?: run {
                _uiState.update { it.copy(isSending = false, inputText = text, attachedFiles = attachedFiles, error = "Not connected") }
                return@launch
            }
            
            val parts = mutableListOf<PartInputDto>()
            if (text.isNotEmpty()) {
                parts.add(PartInputDto(type = "text", text = text))
            }
            attachedFiles.forEach { file ->
                parts.add(PartInputDto(
                    type = "file",
                    filename = file.name,
                    url = "file://${file.path}"
                ))
            }
            
            val request = SendMessageRequest(
                parts = parts,
                agent = selectedAgent,
                model = selectedModel
            )

            // Use async endpoint - returns immediately, all content streams via SSE
            // This avoids HTTP timeout issues for long-running tool executions
            val result = safeApiCall { api.sendMessageAsync(sessionId, request, getDirectory()) }

            when (result) {
                is ApiResult.Success -> {
                    Log.d(TAG, "sendMessage: Async call succeeded, waiting for SSE events")
                }
                is ApiResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            isSending = false, 
                            inputText = text,
                            attachedFiles = attachedFiles,
                            error = "Failed to send: ${result.message}"
                        ) 
                    }
                }
            }
        }
    }

    fun respondToPermission(permissionId: String, response: String) {
        viewModelScope.launch {
            val api = connectionManager.getApi() ?: return@launch
            val request = PermissionResponseRequest(response = response)
            safeApiCall { api.respondToPermission(sessionId, permissionId, request, getDirectory()) }
            _uiState.update { it.copy(pendingPermission = null) }
            // Clear persisted state
            savedStateHandle.remove<String>(KEY_PENDING_PERMISSION)
            showNextPermission()
        }
    }

    fun respondToQuestion(requestId: String, answers: List<List<String>>) {
        viewModelScope.launch {
            val api = connectionManager.getApi() ?: return@launch
            val request = QuestionReplyRequest(answers = answers)
            safeApiCall { api.respondToQuestion(requestId, request, getDirectory()) }
            _uiState.update { it.copy(pendingQuestion = null) }
            // Clear persisted state
            savedStateHandle.remove<String>(KEY_PENDING_QUESTION)
            showNextQuestion()
        }
    }

    fun dismissQuestion() {
        _uiState.update { it.copy(pendingQuestion = null) }
        // Clear persisted state
        savedStateHandle.remove<String>(KEY_PENDING_QUESTION)
        showNextQuestion()
    }

    /**
     * Queue a message to be sent when the session becomes idle
     */
    fun queueMessage() {
        val text = _uiState.value.inputText.trim()
        val attachedFiles = _uiState.value.attachedFiles
        if (text.isEmpty() && attachedFiles.isEmpty()) return
        
        val selectedAgent = _uiState.value.selectedAgent
        val selectedModel = _uiState.value.selectedModel
        
        val queued = QueuedMessage(
            text = text,
            attachedFiles = attachedFiles,
            agent = selectedAgent,
            model = selectedModel
        )
        
        _uiState.update { 
            it.copy(
                inputText = "",
                attachedFiles = emptyList(),
                queuedMessage = queued
            )
        }
        Log.d(TAG, "queueMessage: Queued message with ${text.length} chars, ${attachedFiles.size} files")
    }
    
    /**
     * Send the queued message if one exists
     */
    private fun sendQueuedMessageIfAny() {
        val queued = _uiState.value.queuedMessage ?: return
        
        Log.d(TAG, "sendQueuedMessageIfAny: Sending queued message")
        
        // Clear the queued message first
        _uiState.update { it.copy(queuedMessage = null, isSending = true) }
        
        viewModelScope.launch {
            val api = connectionManager.getApi() ?: run {
                _uiState.update { 
                    it.copy(
                        isSending = false, 
                        inputText = queued.text,
                        attachedFiles = queued.attachedFiles,
                        error = "Not connected"
                    )
                }
                return@launch
            }
            
            val parts = mutableListOf<PartInputDto>()
            if (queued.text.isNotEmpty()) {
                parts.add(PartInputDto(type = "text", text = queued.text))
            }
            queued.attachedFiles.forEach { file ->
                parts.add(PartInputDto(
                    type = "file",
                    filename = file.name,
                    url = "file://${file.path}"
                ))
            }
            
            val request = SendMessageRequest(
                parts = parts,
                agent = queued.agent,
                model = queued.model
            )
            
            val result = safeApiCall { api.sendMessageAsync(sessionId, request, getDirectory()) }
            
            when (result) {
                is ApiResult.Success -> {
                    Log.d(TAG, "sendQueuedMessageIfAny: Queued message sent successfully")
                }
                is ApiResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            isSending = false, 
                            inputText = queued.text,
                            attachedFiles = queued.attachedFiles,
                            error = "Failed to send queued message: ${result.message}"
                        )
                    }
                }
            }
        }
    }

    fun abortSession() {
        viewModelScope.launch {
            val api = connectionManager.getApi() ?: return@launch
            safeApiCall { api.abortSession(sessionId, getDirectory()) }
            
            // Clear streaming flags on all parts
            messagesMutex.withLock {
                clearStreamingFlags()
            }
            
            _uiState.update { it.copy(isBusy = false, isSending = false) }
        }
    }
    
    /**
     * Clear streaming flags on all text parts in the messages map.
     * Called when session becomes idle or is aborted.
     */
    private fun clearStreamingFlags() {
        var changed = false
        _messagesMap.forEach { (id, msgWithParts) ->
            val updatedParts = msgWithParts.parts.map { part ->
                if (part is Part.Text && part.isStreaming) {
                    part.copy(isStreaming = false)
                } else {
                    part
                }
            }
            if (updatedParts != msgWithParts.parts) {
                _messagesMap[id] = msgWithParts.copy(parts = updatedParts)
                changed = true
            }
        }
        if (changed) {
            _messagesVersion.value++
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun loadCommands() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCommands = true) }
            val api = connectionManager.getApi() ?: run {
                _uiState.update { it.copy(isLoadingCommands = false, error = "Not connected") }
                return@launch
            }
            val result = safeApiCall { api.listCommands(getDirectory()) }
            when (result) {
                is ApiResult.Success -> {
                    Log.d(TAG, "loadCommands: Got ${result.data.size} commands from API")
                    val apiCommands = result.data.map { commandMapper.mapToDomain(it) }
                    // Merge with built-in commands (API doesn't return these)
                    val allCommands = (BUILTIN_COMMANDS + apiCommands).distinctBy { it.name }
                    _uiState.update { it.copy(commands = allCommands, isLoadingCommands = false) }
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "loadCommands failed: ${result.message}", result.throwable)
                    // Still show built-in commands even if API fails
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
            val api = connectionManager.getApi() ?: run {
                _uiState.update { it.copy(isSending = false, error = "Not connected") }
                return@launch
            }
            val request = ExecuteCommandRequest(
                command = commandName,
                arguments = arguments
            )
            val result = safeApiCall { api.executeCommand(sessionId, request, getDirectory()) }
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
            val api = connectionManager.getApi() ?: run {
                _uiState.update { it.copy(isLoadingTodos = false) }
                return@launch
            }
            val result = safeApiCall { api.getSessionTodos(sessionId, getDirectory()) }
            when (result) {
                is ApiResult.Success -> {
                    val todos = result.data.map { todoMapper.mapToDomain(it) }
                    _uiState.update { it.copy(todos = todos, isLoadingTodos = false) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoadingTodos = false) }
                }
            }
        }
    }

    private fun loadAgents() {
        viewModelScope.launch {
            val api = connectionManager.getApi() ?: run {
                Log.d(TAG, "loadAgents: No API available")
                return@launch
            }
            val result = safeApiCall { api.getAgents() }
            when (result) {
                is ApiResult.Success -> {
                    Log.d(TAG, "loadAgents: Got ${result.data.size} agents")
                    val primaryAgents = result.data.filter { 
                        it.mode == "primary" && it.hidden != true 
                    }
                    Log.d(TAG, "loadAgents: ${primaryAgents.size} primary agents: ${primaryAgents.map { it.name }}")
                    _uiState.update { state ->
                        state.copy(
                            availableAgents = primaryAgents,
                            selectedAgent = primaryAgents.find { it.name == "build" }?.name
                                ?: primaryAgents.firstOrNull()?.name
                        )
                    }
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "loadAgents failed: ${result.message}")
                }
            }
        }
    }

    fun selectAgent(agentName: String) {
        _uiState.update { it.copy(selectedAgent = agentName) }
    }

    private fun loadModels() {
        viewModelScope.launch {
            val api = connectionManager.getApi() ?: return@launch
            val result = safeApiCall { api.getProviders() }
            when (result) {
                is ApiResult.Success -> {
                    val models = mutableListOf<Pair<String, ModelDto>>()
                    result.data.connected.forEach { providerId ->
                        val provider = result.data.all.find { it.id == providerId }
                        provider?.models?.values?.forEach { model ->
                            models.add(providerId to model)
                        }
                    }
                    val defaultModel = result.data.default.entries.firstOrNull()?.let { (provider, modelId) ->
                        ModelInput(providerID = provider, modelID = modelId)
                    }
                    val lastUsedModel = recentModels.value.firstOrNull()
                    val selectedModel = if (lastUsedModel != null && models.any { 
                        it.first == lastUsedModel.providerID && it.second.id == lastUsedModel.modelID 
                    }) {
                        lastUsedModel
                    } else {
                        defaultModel
                    }
                    _uiState.update { state ->
                        state.copy(
                            availableModels = models,
                            selectedModel = selectedModel
                        )
                    }
                }
                is ApiResult.Error -> {}
            }
        }
    }

    fun selectModel(model: ModelInput) {
        _uiState.update { it.copy(selectedModel = model) }
        viewModelScope.launch {
            settingsDataStore.addRecentModel(model)
        }
    }

    fun toggleFavoriteModel(model: ModelInput) {
        viewModelScope.launch {
            settingsDataStore.toggleFavoriteModel(model)
        }
    }

    fun loadPickerFiles(path: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPickerLoading = true) }
            val api = connectionManager.getApi() ?: run {
                _uiState.update { it.copy(isPickerLoading = false) }
                return@launch
            }
            val effectivePath = path ?: "."
            val result = safeApiCall { api.listFiles(effectivePath) }
            when (result) {
                is ApiResult.Success -> {
                    val files = result.data.map { dto ->
                        FileNode(
                            name = dto.name,
                            path = dto.path,
                            absolute = dto.absolute,
                            type = dto.type,
                            ignored = dto.ignored
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isPickerLoading = false,
                            pickerFiles = files,
                            pickerCurrentPath = if (effectivePath == ".") "" else effectivePath
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isPickerLoading = false) }
                }
            }
        }
    }

    fun attachFile(file: FileNode) {
        val selected = SelectedFile(path = file.path, name = file.name)
        _uiState.update { state ->
            if (state.attachedFiles.none { it.path == file.path }) {
                state.copy(attachedFiles = state.attachedFiles + selected)
            } else state
        }
    }

    fun detachFile(path: String) {
        _uiState.update { state ->
            state.copy(attachedFiles = state.attachedFiles.filter { it.path != path })
        }
    }

    fun clearAttachedFiles() {
        _uiState.update { it.copy(attachedFiles = emptyList()) }
    }

    override fun onCleared() {
        super.onCleared()
    }
}

data class ChatUiState(
    val session: Session? = null,
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isBusy: Boolean = false,
    val pendingPermission: Permission? = null,
    val pendingQuestion: QuestionRequest? = null,
    val error: String? = null,
    val commands: List<Command> = emptyList(),
    val isLoadingCommands: Boolean = false,
    val todos: List<Todo> = emptyList(),
    val isLoadingTodos: Boolean = false,
    val availableAgents: List<AgentDto> = emptyList(),
    val selectedAgent: String? = null,
    val availableModels: List<Pair<String, ModelDto>> = emptyList(),
    val selectedModel: ModelInput? = null,
    val attachedFiles: List<SelectedFile> = emptyList(),
    val pickerFiles: List<FileNode> = emptyList(),
    val pickerCurrentPath: String = "",
    val isPickerLoading: Boolean = false,
    val queuedMessage: QueuedMessage? = null
)

data class QueuedMessage(
    val text: String,
    val attachedFiles: List<SelectedFile> = emptyList(),
    val agent: String? = null,
    val model: ModelInput? = null
)
