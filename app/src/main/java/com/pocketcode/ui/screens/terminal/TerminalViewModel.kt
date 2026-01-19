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
import com.pocketcode.terminal.PtyTerminalClient
import com.pocketcode.terminal.WebSocketTerminalOutput
import com.termux.terminal.TerminalEmulator
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
        private const val DEFAULT_ROWS = 24
        private const val DEFAULT_COLS = 80
        private const val TRANSCRIPT_ROWS = 2000
    }

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private var terminalEmulator: TerminalEmulator? = null
    private var terminalOutput: WebSocketTerminalOutput? = null
    private var terminalClient: PtyTerminalClient? = null

    init {
        loadPtySessions()
        observeEvents()
        observeWebSocketOutput()
        observeWebSocketState()
    }

    fun getTerminalEmulator(): TerminalEmulator? {
        if (terminalEmulator == null) {
            initializeEmulator()
        }
        return terminalEmulator
    }

    private fun initializeEmulator() {
        terminalOutput = WebSocketTerminalOutput(
            webSocket = ptyWebSocket,
            onTitleChanged = { _, newTitle ->
                Log.d(TAG, "Terminal title changed: $newTitle")
            },
            onBell = {
                Log.d(TAG, "Terminal bell")
            }
        )

        terminalClient = PtyTerminalClient(
            onTextChanged = {
                _uiState.update { it.copy(terminalRevision = it.terminalRevision + 1) }
            },
            onTitleChanged = { title ->
                Log.d(TAG, "Session title changed: $title")
            },
            onSessionFinished = {
                Log.d(TAG, "Terminal session finished")
            },
            onBellCallback = {
                Log.d(TAG, "Terminal bell")
            }
        )

        terminalEmulator = TerminalEmulator(
            terminalOutput,
            DEFAULT_COLS,
            DEFAULT_ROWS,
            TRANSCRIPT_ROWS,
            terminalClient
        )
    }

    private fun observeWebSocketOutput() {
        viewModelScope.launch {
            ptyWebSocket.output.collect { data ->
                val bytes = data.toByteArray()
                terminalEmulator?.append(bytes, bytes.size)
                _uiState.update { it.copy(terminalRevision = it.terminalRevision + 1) }
            }
        }
    }

    private fun observeWebSocketState() {
        viewModelScope.launch {
            ptyWebSocket.connectionState.collect { connectionState ->
                when (connectionState) {
                    is PtyWebSocketClient.ConnectionState.Connected -> {
                        Log.d(TAG, "WebSocket connected to ${connectionState.ptyId}")
                        _uiState.update { it.copy(isConnected = true) }
                    }
                    is PtyWebSocketClient.ConnectionState.Error -> {
                        Log.e(TAG, "WebSocket error: ${connectionState.message}")
                        _uiState.update { it.copy(error = "Connection error: ${connectionState.message}", isConnected = false) }
                    }
                    is PtyWebSocketClient.ConnectionState.Disconnected -> {
                        Log.d(TAG, "WebSocket disconnected")
                        _uiState.update { it.copy(isConnected = false) }
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
                        val exitMessage = "\r\n[Process exited with code ${event.exitCode}]\r\n"
                        val bytes = exitMessage.toByteArray()
                        terminalEmulator?.append(bytes, bytes.size)
                        _uiState.update { state ->
                            state.copy(
                                ptySessions = state.ptySessions.map {
                                    if (it.id == event.id) it.copy(status = "exited") else it
                                },
                                terminalRevision = state.terminalRevision + 1
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
                    clearTerminal()
                    _uiState.update {
                        it.copy(
                            isCreating = false,
                            ptySessions = it.ptySessions + pty,
                            selectedPtyId = pty.id
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
        clearTerminal()
        _uiState.update { it.copy(selectedPtyId = ptyId) }
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

    fun sendInput(input: String) {
        if (input.isEmpty()) return
        if (!ptyWebSocket.isConnected()) {
            _uiState.update { it.copy(error = "Not connected to terminal") }
            return
        }
        ptyWebSocket.send(input)
    }

    fun clearTerminal() {
        terminalEmulator?.reset()
        _uiState.update { it.copy(terminalRevision = it.terminalRevision + 1) }
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

data class TerminalUiState(
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val isConnected: Boolean = false,
    val ptySessions: List<Pty> = emptyList(),
    val selectedPtyId: String? = null,
    val error: String? = null,
    val terminalRevision: Int = 0
) {
    val selectedPty: Pty? get() = ptySessions.find { it.id == selectedPtyId }
}
