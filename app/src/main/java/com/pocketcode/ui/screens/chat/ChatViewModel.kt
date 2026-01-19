package com.pocketcode.ui.screens.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcode.core.network.ApiResult
import com.pocketcode.core.network.ConnectionState
import com.pocketcode.core.network.OpenCodeApi
import com.pocketcode.core.network.OpenCodeEventSource
import com.pocketcode.core.network.safeApiCall
import com.pocketcode.data.remote.dto.ExecuteCommandRequest
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
import com.pocketcode.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val eventSource: OpenCodeEventSource,
    private val sessionMapper: SessionMapper,
    private val messageMapper: MessageMapper,
    private val partMapper: PartMapper,
    private val commandMapper: CommandMapper,
    private val todoMapper: TodoMapper
) : ViewModel() {

    private val sessionId: String = savedStateHandle.get<String>(Screen.Chat.ARG_SESSION_ID)!!

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageWithParts>>(emptyList())
    val messages: StateFlow<List<MessageWithParts>> = _messages.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = eventSource.connectionState

    init {
        loadSession()
        loadMessages()
        observeEvents()
        eventSource.connect()
    }

    private fun loadSession() {
        viewModelScope.launch {
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

            val result = safeApiCall { api.getMessages(sessionId) }

            when (result) {
                is ApiResult.Success -> {
                    Log.d("ChatViewModel", "API returned ${result.data.size} message wrappers")
                    val messageList = result.data.map { dto ->
                        Log.d("ChatViewModel", "Mapping message: ${dto.info.id}, role=${dto.info.role}, parts=${dto.parts.size}")
                        messageMapper.mapWrapperToDomain(dto, partMapper)
                    }
                    Log.d("ChatViewModel", "Mapped to ${messageList.size} MessageWithParts")
                    _messages.value = messageList
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
            eventSource.events.collect { event ->
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
                    _uiState.update { it.copy(pendingPermission = event.permission) }
                }
            }
            is OpenCodeEvent.QuestionAsked -> {
                if (event.request.sessionID == sessionId) {
                    _uiState.update { it.copy(pendingQuestion = event.request) }
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

    private fun updateMessage(message: Message) {
        _messages.update { currentMessages ->
            val index = currentMessages.indexOfFirst { it.message.id == message.id }
            if (index >= 0) {
                currentMessages.toMutableList().apply {
                    this[index] = this[index].copy(message = message)
                }
            } else {
                currentMessages + MessageWithParts(message, emptyList())
            }
        }
    }

    private fun updatePart(part: Part, delta: String?) {
        _messages.update { currentMessages ->
            currentMessages.map { mwp ->
                if (mwp.message.id == part.messageID) {
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
                    mwp.copy(parts = updatedParts)
                } else {
                    mwp
                }
            }
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        _uiState.update { it.copy(inputText = "", isSending = true) }

        viewModelScope.launch {
            val request = SendMessageRequest(
                parts = listOf(PartInputDto(type = "text", text = text))
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
                            error = "Failed to send: ${result.message}"
                        ) 
                    }
                }
            }
        }
    }

    fun respondToPermission(permissionId: String, response: String) {
        viewModelScope.launch {
            val request = PermissionResponseRequest(response = response)
            safeApiCall { api.respondToPermission(sessionId, permissionId, request) }
            _uiState.update { it.copy(pendingPermission = null) }
        }
    }

    fun respondToQuestion(questionId: String, answers: List<List<String>>) {
        viewModelScope.launch {
            val request = QuestionReplyRequest(answers = answers)
            safeApiCall { api.respondToQuestion(sessionId, questionId, request) }
            _uiState.update { it.copy(pendingQuestion = null) }
        }
    }

    fun dismissQuestion() {
        _uiState.update { it.copy(pendingQuestion = null) }
    }

    fun abortSession() {
        viewModelScope.launch {
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

    override fun onCleared() {
        super.onCleared()
        eventSource.disconnect()
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
    val isLoadingTodos: Boolean = false
)
