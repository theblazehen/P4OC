package com.pocketcode.ui.screens.chat

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcode.core.network.ApiResult
import com.pocketcode.core.network.ConnectionManager
import com.pocketcode.core.network.ConnectionState
import com.pocketcode.core.network.safeApiCall
import com.pocketcode.core.datastore.SettingsDataStore
import com.pocketcode.data.remote.dto.AgentDto
import com.pocketcode.data.remote.dto.ExecuteCommandRequest
import com.pocketcode.data.remote.dto.ModelDto
import com.pocketcode.data.remote.dto.ModelInput
import com.pocketcode.data.remote.dto.PartInputDto
import com.pocketcode.data.remote.dto.PermissionResponseRequest
import com.pocketcode.data.remote.dto.QuestionReplyRequest
import com.pocketcode.data.remote.dto.SendMessageRequest
import com.pocketcode.data.remote.mapper.CommandMapper
import com.pocketcode.data.remote.mapper.MessageMapper
import com.pocketcode.data.remote.mapper.TodoMapper
import com.pocketcode.data.remote.mapper.PartMapper
import com.pocketcode.data.remote.mapper.SessionMapper
import com.pocketcode.domain.model.*
import com.pocketcode.ui.components.chat.SelectedFile
import com.pocketcode.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val connectionManager: ConnectionManager,
    private val sessionMapper: SessionMapper,
    private val messageMapper: MessageMapper,
    private val partMapper: PartMapper,
    private val commandMapper: CommandMapper,
    private val todoMapper: TodoMapper,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val sessionId: String = savedStateHandle.get<String>(Screen.Chat.ARG_SESSION_ID)!!

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _messages: SnapshotStateList<MessageWithParts> = mutableStateListOf()
    val messages: SnapshotStateList<MessageWithParts> = _messages

    private val pendingPermissions = ConcurrentLinkedQueue<Permission>()
    private val pendingQuestions = ConcurrentLinkedQueue<QuestionRequest>()

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState

    val favoriteModels: StateFlow<Set<ModelInput>> = settingsDataStore.favoriteModels
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    
    val recentModels: StateFlow<List<ModelInput>> = settingsDataStore.recentModels
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        loadSession()
        loadMessages()
        loadAgents()
        loadModels()
        observeEvents()
    }

    private fun loadSession() {
        viewModelScope.launch {
            val api = connectionManager.getApi() ?: run {
                _uiState.update { it.copy(error = "Not connected") }
                return@launch
            }
            val result = safeApiCall { api.getSession(sessionId) }
            when (result) {
                is ApiResult.Success -> {
                    val session = sessionMapper.mapToDomain(result.data)
                    _uiState.update { it.copy(session = session) }
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
            Log.d("ChatViewModel", "loadMessages() called for session: $sessionId")

            val api = connectionManager.getApi() ?: run {
                _uiState.update { it.copy(isLoading = false, error = "Not connected") }
                return@launch
            }
            val result = safeApiCall { api.getMessages(sessionId) }

            when (result) {
                is ApiResult.Success -> {
                    Log.d("ChatViewModel", "API returned ${result.data.size} message wrappers")
                    val messageList = result.data.map { dto ->
                        Log.d("ChatViewModel", "Mapping message: ${dto.info.id}, role=${dto.info.role}, parts=${dto.parts.size}")
                        messageMapper.mapWrapperToDomain(dto, partMapper)
                    }
                    Log.d("ChatViewModel", "Mapped to ${messageList.size} MessageWithParts")
                    _messages.clear()
                    _messages.addAll(messageList)
                    _uiState.update { it.copy(isLoading = false) }
                }
                is ApiResult.Error -> {
                    Log.e("ChatViewModel", "Failed to load messages: ${result.message}", result.throwable)
                    _uiState.update { 
                        it.copy(isLoading = false, error = "Failed to load messages") 
                    }
                }
            }
        }
    }

    private fun observeEvents() {
        viewModelScope.launch {
            connectionManager.getEventSource()?.events?.collect { event ->
                handleEvent(event)
            }
        }
    }

    private fun handleEvent(event: OpenCodeEvent) {
        when (event) {
            is OpenCodeEvent.MessageUpdated -> {
                if (event.message.sessionID == sessionId) {
                    updateMessage(event.message)
                }
            }
            is OpenCodeEvent.MessagePartUpdated -> {
                if (event.part.sessionID == sessionId) {
                    updatePart(event.part, event.delta)
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
                    val isBusy = event.status is SessionStatus.Busy || event.status is SessionStatus.Retry
                    _uiState.update { it.copy(isBusy = isBusy) }
                }
            }
            is OpenCodeEvent.SessionUpdated -> {
                if (event.session.id == sessionId) {
                    _uiState.update { it.copy(session = event.session) }
                }
            }
            else -> {}
        }
    }

    private fun showNextPermission() {
        if (_uiState.value.pendingPermission == null) {
            pendingPermissions.poll()?.let { permission ->
                _uiState.update { it.copy(pendingPermission = permission) }
            }
        }
    }

    private fun showNextQuestion() {
        if (_uiState.value.pendingQuestion == null) {
            pendingQuestions.poll()?.let { question ->
                _uiState.update { it.copy(pendingQuestion = question) }
            }
        }
    }

    private fun updateMessage(message: Message) {
        val index = _messages.indexOfFirst { it.message.id == message.id }
        if (index >= 0) {
            _messages[index] = _messages[index].copy(message = message)
        } else {
            _messages.add(MessageWithParts(message, emptyList()))
        }
    }

    private fun updatePart(part: Part, delta: String?) {
        val msgIndex = _messages.indexOfFirst { it.message.id == part.messageID }
        if (msgIndex < 0) return

        val mwp = _messages[msgIndex]
        val partIndex = mwp.parts.indexOfFirst { it.id == part.id }
        
        val updatedParts = if (partIndex >= 0) {
            mwp.parts.toMutableList().apply {
                this[partIndex] = if (delta != null && part is Part.Text) {
                    val existingText = (this[partIndex] as? Part.Text)?.text ?: ""
                    part.copy(text = existingText + delta, isStreaming = true)
                } else {
                    part
                }
            }
        } else {
            mwp.parts + part
        }
        _messages[msgIndex] = mwp.copy(parts = updatedParts)
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

            val result = safeApiCall { api.sendMessageAsync(sessionId, request) }

            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSending = false, isBusy = true) }
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
            safeApiCall { api.respondToPermission(sessionId, permissionId, request) }
            _uiState.update { it.copy(pendingPermission = null) }
            showNextPermission()
        }
    }

    fun respondToQuestion(questionId: String, answers: List<List<String>>) {
        viewModelScope.launch {
            val api = connectionManager.getApi() ?: return@launch
            val request = QuestionReplyRequest(answers = answers)
            safeApiCall { api.respondToQuestion(sessionId, questionId, request) }
            _uiState.update { it.copy(pendingQuestion = null) }
            showNextQuestion()
        }
    }

    fun dismissQuestion() {
        _uiState.update { it.copy(pendingQuestion = null) }
        showNextQuestion()
    }

    fun abortSession() {
        viewModelScope.launch {
            val api = connectionManager.getApi() ?: return@launch
            safeApiCall { api.abortSession(sessionId) }
            _uiState.update { it.copy(isBusy = false) }
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
            val result = safeApiCall { api.listCommands() }
            when (result) {
                is ApiResult.Success -> {
                    val commands = result.data.map { commandMapper.mapToDomain(it) }
                    _uiState.update { it.copy(commands = commands, isLoadingCommands = false) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoadingCommands = false, error = "Failed to load commands") }
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
            val result = safeApiCall { api.executeCommand(sessionId, request) }
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
            val result = safeApiCall { api.getSessionTodos(sessionId) }
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
            val api = connectionManager.getApi() ?: return@launch
            val result = safeApiCall { api.getAgents() }
            when (result) {
                is ApiResult.Success -> {
                    val primaryAgents = result.data.filter { 
                        it.mode == "primary" && it.hidden != true 
                    }
                    _uiState.update { state ->
                        state.copy(
                            availableAgents = primaryAgents,
                            selectedAgent = primaryAgents.find { it.name == "build" }?.name
                                ?: primaryAgents.firstOrNull()?.name
                        )
                    }
                }
                is ApiResult.Error -> {}
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
                    _uiState.update { state ->
                        state.copy(
                            availableModels = models,
                            selectedModel = defaultModel
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

    fun loadPickerFiles(path: String = ".") {
        viewModelScope.launch {
            _uiState.update { it.copy(isPickerLoading = true) }
            val api = connectionManager.getApi() ?: run {
                _uiState.update { it.copy(isPickerLoading = false) }
                return@launch
            }
            val result = safeApiCall { api.listFiles(path) }
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
                            pickerCurrentPath = if (path == ".") "" else path
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
    val isPickerLoading: Boolean = false
)
