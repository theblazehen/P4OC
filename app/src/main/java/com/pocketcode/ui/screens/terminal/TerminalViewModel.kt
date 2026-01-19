package com.pocketcode.ui.screens.terminal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcode.core.network.ApiResult
import com.pocketcode.core.network.OpenCodeApi
import com.pocketcode.core.network.OpenCodeEventSource
import com.pocketcode.core.network.PtyWebSocketClient
import com.pocketcode.core.network.safeApiCall
import com.pocketcode.data.remote.dto.CreatePtyRequest
import com.pocketcode.domain.model.OpenCodeEvent
import com.pocketcode.domain.model.Pty
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val api: OpenCodeApi,
    private val eventSource: OpenCodeEventSource,
    private val ptyWebSocket: PtyWebSocketClient
) : ViewModel() {

    companion object {
        private const val TAG = "TerminalViewModel"
    }

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    init {
        loadPtySessions()
        observeEvents()
        observeWebSocketOutput()
        observeWebSocketState()
    }

    private fun observeWebSocketOutput() {
        viewModelScope.launch {
            ptyWebSocket.output.collect { data ->
                _uiState.update { state ->
                    val newOutput = state.outputBuffer + data
                    state.copy(outputBuffer = newOutput)
                }
            }
        }
    }

    private fun observeWebSocketState() {
        viewModelScope.launch {
            ptyWebSocket.connectionState.collect { connectionState ->
                when (connectionState) {
                    is PtyWebSocketClient.ConnectionState.Connected -> {
                        Log.d(TAG, "WebSocket connected to ${connectionState.ptyId}")
                    }
                    is PtyWebSocketClient.ConnectionState.Error -> {
                        Log.e(TAG, "WebSocket error: ${connectionState.message}")
                        _uiState.update { it.copy(error = "Connection error: ${connectionState.message}") }
                    }
                    is PtyWebSocketClient.ConnectionState.Disconnected -> {
                        Log.d(TAG, "WebSocket disconnected")
                    }
                    is PtyWebSocketClient.ConnectionState.Connecting -> {
                        Log.d(TAG, "WebSocket connecting...")
                    }
                }
            }
        }
    }

    private fun observeEvents() {
        viewModelScope.launch {
            eventSource.events.collect { event ->
                when (event) {
                    is OpenCodeEvent.PtyCreated -> {
                        _uiState.update { state ->
                            state.copy(
                                ptySessions = state.ptySessions + event.pty,
                                selectedPtyId = state.selectedPtyId ?: event.pty.id
                            )
                        }
                    }
                    is OpenCodeEvent.PtyUpdated -> {
                        _uiState.update { state ->
                            state.copy(
                                ptySessions = state.ptySessions.map {
                                    if (it.id == event.pty.id) event.pty else it
                                }
                            )
                        }
                    }
                    is OpenCodeEvent.PtyExited -> {
                        _uiState.update { state ->
                            state.copy(
                                ptySessions = state.ptySessions.map {
                                    if (it.id == event.id) it.copy(status = "exited") else it
                                },
                                outputBuffer = state.outputBuffer + "\r\n[Process exited with code ${event.exitCode}]\r\n"
                            )
                        }
                    }
                    is OpenCodeEvent.PtyDeleted -> {
                        if (_uiState.value.selectedPtyId == event.id) {
                            ptyWebSocket.disconnect()
                        }
                        _uiState.update { state ->
                            val newSessions = state.ptySessions.filter { it.id != event.id }
                            state.copy(
                                ptySessions = newSessions,
                                selectedPtyId = if (state.selectedPtyId == event.id) {
                                    newSessions.firstOrNull()?.id
                                } else state.selectedPtyId
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun loadPtySessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val result = safeApiCall { api.listPtySessions() }

            when (result) {
                is ApiResult.Success -> {
                    val sessions = result.data.map { dto ->
                        Pty(
                            id = dto.id,
                            title = dto.title,
                            command = dto.command,
                            args = dto.args,
                            cwd = dto.cwd,
                            status = dto.status,
                            pid = dto.pid
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            ptySessions = sessions,
                            selectedPtyId = sessions.firstOrNull()?.id
                        )
                    }
                    sessions.firstOrNull()?.let { connectToSession(it.id) }
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to load PTY sessions: ${result.message}")
                    _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
            }
        }
    }

    fun createNewSession(title: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true) }

            val request = CreatePtyRequest(title = title ?: "Terminal")

            val result = safeApiCall { api.createPtySession(request) }

            when (result) {
                is ApiResult.Success -> {
                    val pty = Pty(
                        id = result.data.id,
                        title = result.data.title,
                        command = result.data.command,
                        args = result.data.args,
                        cwd = result.data.cwd,
                        status = result.data.status,
                        pid = result.data.pid
                    )
                    _uiState.update {
                        it.copy(
                            isCreating = false,
                            ptySessions = it.ptySessions + pty,
                            selectedPtyId = pty.id,
                            outputBuffer = ""
                        )
                    }
                    connectToSession(pty.id)
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to create PTY session: ${result.message}")
                    _uiState.update {
                        it.copy(isCreating = false, error = result.message)
                    }
                }
            }
        }
    }

    fun selectSession(ptyId: String) {
        _uiState.update { it.copy(selectedPtyId = ptyId, outputBuffer = "") }
        connectToSession(ptyId)
    }

    private fun connectToSession(ptyId: String) {
        ptyWebSocket.connect(ptyId)
    }

    fun deleteSession(ptyId: String) {
        viewModelScope.launch {
            if (_uiState.value.selectedPtyId == ptyId) {
                ptyWebSocket.disconnect()
            }
            val result = safeApiCall { api.deletePtySession(ptyId) }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        val newSessions = state.ptySessions.filter { it.id != ptyId }
                        state.copy(
                            ptySessions = newSessions,
                            selectedPtyId = if (state.selectedPtyId == ptyId) {
                                newSessions.firstOrNull()?.id
                            } else state.selectedPtyId
                        )
                    }
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to delete PTY: ${result.message}")
                }
            }
        }
    }

    fun updateInput(input: String) {
        _uiState.update { it.copy(input = input) }
    }

    fun sendInput() {
        val input = _uiState.value.input
        if (input.isEmpty()) return
        if (!ptyWebSocket.isConnected()) {
            _uiState.update { it.copy(error = "Not connected to terminal") }
            return
        }

        _uiState.update { it.copy(input = "") }
        ptyWebSocket.send(input + "\n")
    }

    fun sendSpecialKey(key: SpecialKey) {
        if (!ptyWebSocket.isConnected()) return

        val data = when (key) {
            SpecialKey.CTRL_C -> "\u0003"
            SpecialKey.CTRL_D -> "\u0004"
            SpecialKey.CTRL_Z -> "\u001a"
            SpecialKey.TAB -> "\t"
            SpecialKey.ARROW_UP -> "\u001b[A"
            SpecialKey.ARROW_DOWN -> "\u001b[B"
            SpecialKey.ARROW_LEFT -> "\u001b[D"
            SpecialKey.ARROW_RIGHT -> "\u001b[C"
        }
        ptyWebSocket.send(data)
    }

    fun clearOutput() {
        _uiState.update { it.copy(outputBuffer = "") }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refresh() {
        loadPtySessions()
    }

    override fun onCleared() {
        super.onCleared()
        ptyWebSocket.disconnect()
    }
}

enum class SpecialKey {
    CTRL_C, CTRL_D, CTRL_Z, TAB, ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT
}

data class TerminalUiState(
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val ptySessions: List<Pty> = emptyList(),
    val selectedPtyId: String? = null,
    val input: String = "",
    val outputBuffer: String = "OpenCode Terminal\r\nCreate or select a terminal session to begin.\r\n",
    val error: String? = null
) {
    val selectedPty: Pty? get() = ptySessions.find { it.id == selectedPtyId }
}
