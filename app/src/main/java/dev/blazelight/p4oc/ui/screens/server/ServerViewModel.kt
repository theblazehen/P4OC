package dev.blazelight.p4oc.ui.screens.server

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.core.datastore.RecentServer
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.DirectoryManager
import dev.blazelight.p4oc.core.network.ServerConfig
import dev.blazelight.p4oc.core.network.safeApiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "ServerViewModel"


class ServerViewModel constructor(
    private val settingsDataStore: SettingsDataStore,
    private val connectionManager: ConnectionManager,
    private val directoryManager: DirectoryManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerUiState())
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()

    init {
        loadRecentServers()
        tryAutoReconnect()
    }

    private fun loadRecentServers() {
        viewModelScope.launch {
            settingsDataStore.recentServers.collect { servers ->
                _uiState.update { it.copy(recentServers = servers) }
            }
        }
    }

    private fun tryAutoReconnect() {
        viewModelScope.launch {
            val lastConnection = settingsDataStore.getLastConnection() ?: return@launch
            
            Log.d(TAG, "Found last connection: ${lastConnection.url}")
            _uiState.update { 
                it.copy(
                    isConnecting = true,
                    remoteUrl = lastConnection.url
                )
            }
            
            val result = connectionManager.connect(lastConnection, lastConnection.password)
            
            result.fold(
                onSuccess = {
                    Log.d(TAG, "Auto-reconnect successful")
                    initializeProjectContext()
                    _uiState.update { it.copy(isConnecting = false, isConnected = true) }
                },
                onFailure = { error ->
                    Log.w(TAG, "Auto-reconnect failed: ${error.message}")
                    _uiState.update { it.copy(isConnecting = false) }
                }
            )
        }
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

    fun connectToRemote() {
        val state = _uiState.value
        Log.d(TAG, "connectToRemote called, url='${state.remoteUrl}'")
        
        if (state.remoteUrl.isBlank()) {
            Log.w(TAG, "URL is blank, showing error")
            _uiState.update { it.copy(error = "Please enter a server URL") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, error = null) }

            val url = normalizeServerUrl(state.remoteUrl)
            Log.d(TAG, "Connecting to normalized URL: $url")
            
            val config = ServerConfig(
                url = url,
                name = "Remote Server",
                isLocal = false,
                username = state.username.takeIf { it.isNotBlank() },
                password = state.password.takeIf { it.isNotBlank() }
            )
            val password = state.password.takeIf { it.isNotBlank() }

            val result = connectionManager.connect(config, password)

            result.fold(
                onSuccess = {
                    Log.d(TAG, "Connection successful!")
                    // Clear password from UI state after successful connection for security
                    _uiState.update { it.copy(password = "") }
                    settingsDataStore.saveLastConnection(config)
                    settingsDataStore.addRecentServer(url, "Remote Server", state.username.takeIf { it.isNotBlank() }, state.password.takeIf { it.isNotBlank() })
                    initializeProjectContext()
                    _uiState.update { it.copy(isConnecting = false, isConnected = true) }
                },
                onFailure = { error ->
                    Log.e(TAG, "Connection failed: ${error.message}", error)
                    // Clear password from UI state on failure too - user can re-enter
                    _uiState.update { 
                        it.copy(
                            isConnecting = false, 
                            password = "",
                            error = "Failed to connect: ${error.message}"
                        ) 
                    }
                }
            )
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

    fun connectToRecentServer(server: RecentServer) {
        _uiState.update { 
            it.copy(
                remoteUrl = server.url,
                username = server.username ?: "opencode",
                password = server.password ?: ""
            )
        }
        connectToRemote()
    }

    fun removeRecentServer(server: RecentServer) {
        viewModelScope.launch {
            settingsDataStore.removeRecentServer(server.url)
        }
    }

    /**
     * Initialize project context after successful connection.
     * Now always navigates to the unified Sessions screen.
     */
    private suspend fun initializeProjectContext() {
        // Clear any persisted directory - we now show all sessions unified
        directoryManager.setDirectory(null)
        
        Log.d(TAG, "Navigating to unified Sessions screen")
        _uiState.update { it.copy(navigationDestination = NavigationDestination.Sessions) }
    }

    fun clearNavigationDestination() {
        _uiState.update { it.copy(navigationDestination = null) }
    }
}

data class ServerUiState(
    val remoteUrl: String = "",
    val username: String = "opencode",
    val password: String = "",
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val error: String? = null,
    val recentServers: List<RecentServer> = emptyList(),
    // Navigation destination after connection
    val navigationDestination: NavigationDestination? = null
)

/**
 * Where to navigate after successful connection.
 */
sealed class NavigationDestination {
    /** Navigate to unified sessions view */
    data object Sessions : NavigationDestination()
    
    /** Navigate to project selector */
    data object Projects : NavigationDestination()
}
