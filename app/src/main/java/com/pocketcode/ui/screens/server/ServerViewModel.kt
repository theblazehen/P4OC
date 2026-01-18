package com.pocketcode.ui.screens.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcode.core.datastore.SettingsDataStore
import com.pocketcode.core.network.ApiResult
import com.pocketcode.core.network.OpenCodeApi
import com.pocketcode.core.network.safeApiCall
import com.pocketcode.core.termux.TermuxBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServerViewModel @Inject constructor(
    private val termuxBridge: TermuxBridge,
    private val settingsDataStore: SettingsDataStore,
    private val api: OpenCodeApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerUiState())
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()

    init {
        checkTermuxStatus()
    }

    private fun checkTermuxStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(termuxStatus = TermuxStatusUi.Checking) }
            val status = termuxBridge.checkStatus()
            _uiState.update { 
                it.copy(termuxStatus = mapTermuxStatus(status)) 
            }
        }
    }

    private fun mapTermuxStatus(status: TermuxBridge.TermuxStatus): TermuxStatusUi = when (status) {
        TermuxBridge.TermuxStatus.Unknown -> TermuxStatusUi.Unknown
        TermuxBridge.TermuxStatus.NotInstalled -> TermuxStatusUi.NotInstalled
        TermuxBridge.TermuxStatus.Installed -> TermuxStatusUi.SetupRequired
        TermuxBridge.TermuxStatus.SetupRequired -> TermuxStatusUi.SetupRequired
        TermuxBridge.TermuxStatus.OpenCodeNotInstalled -> TermuxStatusUi.OpenCodeNotInstalled
        TermuxBridge.TermuxStatus.Ready -> TermuxStatusUi.Ready
        is TermuxBridge.TermuxStatus.ServerRunning -> TermuxStatusUi.ServerRunning
    }

    fun setConnectionMode(mode: ConnectionMode) {
        _uiState.update { it.copy(connectionMode = mode, error = null) }
    }

    fun setRemoteUrl(url: String) {
        _uiState.update { it.copy(remoteUrl = url, error = null) }
    }

    fun setUsername(username: String) {
        _uiState.update { it.copy(username = username) }
    }

    fun setPassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun startLocalServer() {
        termuxBridge.startOpenCodeServer()
        _uiState.update { it.copy(termuxStatus = TermuxStatusUi.ServerRunning) }
    }

    fun installOpenCode() {
        termuxBridge.installOpenCode()
    }

    fun openTermux() {
        termuxBridge.openTermuxSetup()
    }

    fun connectToLocal() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, error = null) }
            
            settingsDataStore.setServerConfig(
                url = "http://localhost:4096",
                name = "Local (Termux)",
                isLocal = true
            )

            val result = safeApiCall { api.health() }
            
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isConnecting = false, isConnected = true) }
                }
                is ApiResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            isConnecting = false, 
                            error = "Failed to connect: ${result.message}"
                        ) 
                    }
                }
            }
        }
    }

    fun connectToRemote() {
        val state = _uiState.value
        if (state.remoteUrl.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a server URL") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, error = null) }

            val url = normalizeServerUrl(state.remoteUrl)

            settingsDataStore.setServerConfig(
                url = url,
                name = "Remote Server",
                isLocal = false,
                username = state.username.takeIf { it.isNotBlank() },
                password = state.password.takeIf { it.isNotBlank() }
            )

            val result = safeApiCall { api.health() }

            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isConnecting = false, isConnected = true) }
                }
                is ApiResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            isConnecting = false, 
                            error = "Failed to connect: ${result.message}"
                        ) 
                    }
                }
            }
        }
    }

    private fun normalizeServerUrl(input: String): String {
        var url = input.trim()
        
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        
        val hasPort = url.substringAfter("://").contains(":")
        if (!hasPort) {
            val schemeEnd = url.indexOf("://") + 3
            val pathStart = url.indexOf("/", schemeEnd)
            url = if (pathStart == -1) {
                "$url:4096"
            } else {
                "${url.substring(0, pathStart)}:4096${url.substring(pathStart)}"
            }
        }
        
        return url
    }
}

data class ServerUiState(
    val connectionMode: ConnectionMode = ConnectionMode.LOCAL,
    val termuxStatus: TermuxStatusUi = TermuxStatusUi.Unknown,
    val remoteUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val error: String? = null
)
