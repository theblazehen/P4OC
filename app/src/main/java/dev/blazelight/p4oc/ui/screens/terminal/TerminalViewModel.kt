package dev.blazelight.p4oc.ui.screens.terminal

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.PtyWebSocketClient
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.terminal.PtyTerminalClient
import dev.blazelight.p4oc.terminal.WebSocketTerminalOutput
import dev.blazelight.p4oc.ui.navigation.Screen
import com.termux.terminal.TerminalEmulator
import com.termux.view.TerminalView
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


/**
 * ViewModel for a single PTY terminal session.
 * Each terminal tab gets its own instance with its own ptyId and websocket connection.
 */
class TerminalViewModel constructor(
    savedStateHandle: SavedStateHandle,
    private val context: Context,
    private val connectionManager: ConnectionManager,
    private val ptyWebSocket: PtyWebSocketClient
) : ViewModel() {

    companion object {
        private const val TAG = "TerminalViewModel"
        private const val DEFAULT_ROWS = 24
        private const val DEFAULT_COLS = 80
        private const val TRANSCRIPT_ROWS = 2000
    }

    val ptyId: String = savedStateHandle.get<String>(Screen.Terminal.ARG_PTY_ID)
        ?: throw IllegalArgumentException("ptyId is required for TerminalViewModel")

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private var emulator: TerminalEmulator? = null
    private var terminalOutput: WebSocketTerminalOutput? = null
    private var terminalClient: PtyTerminalClient? = null
    private var terminalViewRef: WeakReference<TerminalView>? = null

    fun setTerminalView(view: TerminalView) {
        terminalViewRef = WeakReference(view)
        // Calculate and apply proper terminal size based on view dimensions
        view.post {
            resizeTerminalToFit(view)
        }
    }
    
    private fun resizeTerminalToFit(view: TerminalView) {
        val width = view.width
        val height = view.height
        
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "View not measured yet, skipping resize")
            return
        }
        
        val renderer = view.mRenderer
        if (renderer == null) {
            Log.w(TAG, "Renderer not initialized, skipping resize")
            return
        }
        
        // Get actual character dimensions from TerminalRenderer (public API)
        val charWidth = renderer.getFontWidth()
        val charLineSpacing = renderer.getFontLineSpacing()
        
        if (charWidth <= 0 || charLineSpacing <= 0) {
            Log.w(TAG, "Invalid character dimensions, skipping resize")
            return
        }
        
        // Calculate cols/rows exactly as Termux does
        val cols = maxOf(4, (width / charWidth).toInt())
        val rows = maxOf(4, height / charLineSpacing)
        
        Log.d(TAG, "Resizing terminal: ${cols}x${rows} (view: ${width}x${height}px, char: ${charWidth}x${charLineSpacing})")
        
        // Resize the local emulator
        emulator?.resize(cols, rows)
        view.onScreenUpdated()
        
        // Notify server of new size
        viewModelScope.launch {
            val api = connectionManager.getApi() ?: return@launch
            val result = safeApiCall { 
                api.updatePtySession(
                    ptyId, 
                    dev.blazelight.p4oc.data.remote.dto.UpdatePtyRequest(
                        size = dev.blazelight.p4oc.data.remote.dto.PtySizeDto(rows, cols)
                    )
                )
            }
            when (result) {
                is ApiResult.Success -> Log.d(TAG, "PTY size updated to ${cols}x${rows}")
                is ApiResult.Error -> Log.e(TAG, "Failed to update PTY size: ${result.message}")
            }
        }
    }

    init {
        initEmulator()
        fetchPtyDetails()
        connectToSession()
        observeEvents()
        observeWebSocketOutput()
        observeWebSocketState()
    }
    
    private fun fetchPtyDetails() {
        viewModelScope.launch {
            val api = connectionManager.getApi() ?: return@launch
            val result = safeApiCall { api.listPtySessions() }
            when (result) {
                is ApiResult.Success -> {
                    val pty = result.data.find { it.id == ptyId }
                    pty?.let {
                        _uiState.update { state -> state.copy(title = it.title) }
                    }
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to fetch PTY details: ${result.message}")
                }
            }
        }
    }

    fun getTerminalEmulator(): TerminalEmulator? = emulator

    private fun initEmulator() {
        terminalClient = PtyTerminalClient(
            context = context,
            onTextChanged = { /* View invalidated directly via postInvalidate */ },
            onTitleChanged = { title ->
                Log.d(TAG, "Session title changed: $title")
            },
            onSessionFinished = {
                Log.d(TAG, "Terminal session finished")
            },
            onBellCallback = {
                Log.d(TAG, "Terminal bell")
            },
            onPasteRequest = { text ->
                sendInput(text)
            }
        )

        terminalOutput = WebSocketTerminalOutput(
            webSocket = ptyWebSocket,
            onTitleChanged = { _, newTitle ->
                Log.d(TAG, "Terminal title changed: $newTitle")
            },
            onBell = {
                Log.d(TAG, "Terminal bell")
            }
        )

        emulator = TerminalEmulator(
            terminalOutput,
            DEFAULT_COLS,
            DEFAULT_ROWS,
            TRANSCRIPT_ROWS,
            terminalClient
        )
    }

    private fun connectToSession() {
        ptyWebSocket.connect(ptyId)
        _uiState.update { it.copy(isConnecting = true) }
    }

    private fun observeWebSocketOutput() {
        viewModelScope.launch {
            ptyWebSocket.output.collect { data ->
                val em = emulator ?: return@collect
                val bytes = data.toByteArray()
                em.append(bytes, bytes.size)
                // Invalidate the TerminalView directly on the UI thread
                // instead of triggering Compose recomposition for every data chunk
                terminalViewRef?.get()?.postInvalidate()
            }
        }
    }

    private fun observeWebSocketState() {
        viewModelScope.launch {
            ptyWebSocket.connectionState.collect { connectionState ->
                when (connectionState) {
                    is PtyWebSocketClient.ConnectionState.Connected -> {
                        Log.d(TAG, "WebSocket connected to ${connectionState.ptyId}")
                        _uiState.update { it.copy(isConnected = true, isConnecting = false) }
                    }
                    is PtyWebSocketClient.ConnectionState.Error -> {
                        Log.e(TAG, "WebSocket error: ${connectionState.message}")
                        _uiState.update { it.copy(
                            error = "Connection error: ${connectionState.message}",
                            isConnected = false,
                            isConnecting = false
                        ) }
                    }
                    is PtyWebSocketClient.ConnectionState.Disconnected -> {
                        Log.d(TAG, "WebSocket disconnected")
                        _uiState.update { it.copy(isConnected = false, isConnecting = false) }
                    }
                    is PtyWebSocketClient.ConnectionState.Connecting -> {
                        Log.d(TAG, "WebSocket connecting...")
                        _uiState.update { it.copy(isConnecting = true) }
                    }
                }
            }
        }
    }

    private fun observeEvents() {
        viewModelScope.launch {
            connectionManager.getEventSource()?.events?.collect { event ->
                when (event) {
                    is OpenCodeEvent.PtyUpdated -> {
                        if (event.pty.id == ptyId) {
                            _uiState.update { it.copy(title = event.pty.title) }
                        }
                    }
                    is OpenCodeEvent.PtyExited -> {
                        if (event.id == ptyId) {
                            val exitMessage = "\r\n[Process exited with code ${event.exitCode}]\r\n"
                            val bytes = exitMessage.toByteArray()
                            emulator?.append(bytes, bytes.size)
                            terminalViewRef?.get()?.postInvalidate()
                            _uiState.update { it.copy(isExited = true) }
                        }
                    }
                    else -> {}
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
        emulator?.reset()
        terminalViewRef?.get()?.postInvalidate()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        ptyWebSocket.disconnect()
        emulator = null
        terminalClient = null
        terminalOutput = null
        terminalViewRef = null
    }
}

data class TerminalUiState(
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val isExited: Boolean = false,
    val title: String? = null,
    val error: String? = null
)
