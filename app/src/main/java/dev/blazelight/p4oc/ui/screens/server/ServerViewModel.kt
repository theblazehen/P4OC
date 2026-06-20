package dev.blazelight.p4oc.ui.screens.server

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.BuildConfig
import dev.blazelight.p4oc.core.datastore.RecentServer
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.core.network.AuthMode
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.DiscoveredServer
import dev.blazelight.p4oc.core.network.DiscoverySeed
import dev.blazelight.p4oc.core.network.DiscoveryState
import dev.blazelight.p4oc.core.network.MdnsDiscoveryManager
import dev.blazelight.p4oc.core.network.ServerConfig
import dev.blazelight.p4oc.core.network.ServerUrl
import dev.blazelight.p4oc.core.security.CredentialStore
import dev.blazelight.p4oc.core.security.OidcAuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "ServerViewModel"

class ServerViewModel constructor(
    private val settingsDataStore: SettingsDataStore,
    private val connectionManager: ConnectionManager,
    private val credentialStore: CredentialStore,
    private val mdnsDiscoveryManager: MdnsDiscoveryManager,
    private val oidcAuthManager: OidcAuthManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerUiState())
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()

    init {
        loadServerDraft()
        loadRecentServers()
        collectDiscoveryFlows()
        tryAutoReconnect()
    }

    private fun loadRecentServers() {
        viewModelScope.launch {
            settingsDataStore.recentServers.collect { servers ->
                _uiState.update { it.copy(recentServers = servers) }
            }
        }
    }

    private fun loadServerDraft() {
        viewModelScope.launch {
            val draft = settingsDataStore.getServerDraft() ?: return@launch
            // Restore only into a pristine form — never clobber what the user is already typing.
            _uiState.update { current ->
                if (current.remoteUrl.isNotBlank()) {
                    current
                } else {
                    current.copy(
                        remoteUrl = draft.url,
                        authMode = draft.authMode,
                        oidcIssuer = draft.oidcIssuer,
                        oidcClientId = draft.oidcClientId,
                        allowInsecure = draft.allowInsecure,
                    )
                }
            }
        }
    }

    /** Persist the current form as a draft so a crash/process death never loses an in-progress config. */
    private fun persistDraft() {
        val state = _uiState.value
        viewModelScope.launch {
            settingsDataStore.saveServerDraft(
                url = state.remoteUrl,
                authMode = state.authMode,
                oidcIssuer = state.oidcIssuer,
                oidcClientId = state.oidcClientId,
                allowInsecure = state.allowInsecure,
            )
        }
    }

    private fun tryAutoReconnect() {
        viewModelScope.launch {
            val (lastConfig, password) = settingsDataStore.getLastConnection() ?: return@launch

            AppLog.d(TAG, "Found last connection: ${lastConfig.url}")
            _uiState.update {
                it.copy(
                    isConnecting = true,
                    remoteUrl = lastConfig.url,
                    username = lastConfig.username ?: ServerUrl.DEFAULT_USERNAME,
                    password = password ?: "",
                    allowInsecure = lastConfig.allowInsecure
                )
            }

            val result = connectionManager.connect(lastConfig, password)

            result.fold(
                onSuccess = { projects ->
                    AppLog.d(TAG, "Auto-reconnect successful")
                    initializeProjectContext()
                    _uiState.update { it.copy(isConnecting = false, isConnected = true) }
                },
                onFailure = { error ->
                    AppLog.w(TAG, "Auto-reconnect failed: ${error.message}")
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            error = "Could not reconnect: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    fun setRemoteUrl(url: String) {
        _uiState.update { it.copy(remoteUrl = url, error = null) }
        persistDraft()
    }

    fun setUsername(username: String) {
        _uiState.update { it.copy(username = username) }
    }

    fun setPassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun setAllowInsecure(allow: Boolean) {
        _uiState.update { it.copy(allowInsecure = allow) }
        persistDraft()
    }

    fun setAuthMode(mode: AuthMode) {
        _uiState.update { it.copy(authMode = mode, error = null) }
        persistDraft()
    }

    fun setOidcIssuer(issuer: String) {
        _uiState.update { it.copy(oidcIssuer = issuer) }
        persistDraft()
    }

    fun setOidcClientId(clientId: String) {
        _uiState.update { it.copy(oidcClientId = clientId) }
        persistDraft()
    }

    /** Build the AppAuth login intent for the current OIDC settings; the UI launches it. */
    suspend fun buildOidcLoginIntent(): Intent? {
        val state = _uiState.value
        if (state.oidcIssuer.isBlank() || state.oidcClientId.isBlank()) {
            _uiState.update { it.copy(error = "Enter OIDC issuer and client id first") }
            return null
        }
        return runCatching {
            oidcAuthManager.buildLoginIntent(
                issuer = state.oidcIssuer.trim(),
                clientId = state.oidcClientId.trim(),
                redirectUri = OidcAuthManager.redirectUri(BuildConfig.APPLICATION_ID),
            )
        }.onFailure { e ->
            AppLog.e(TAG, "OIDC discovery failed", e)
            _uiState.update { it.copy(error = "OIDC discovery failed: ${e.message}") }
        }.getOrNull()
    }

    /** Handle the AppAuth redirect result: exchange code for tokens, mark logged in. */
    fun completeOidcLogin(data: Intent) {
        viewModelScope.launch {
            val serverUrl = ServerUrl.normalizeConnectUrl(_uiState.value.remoteUrl)
            if (serverUrl == null) {
                _uiState.update { it.copy(error = "Enter a valid server URL before logging in") }
                return@launch
            }
            oidcAuthManager.completeLogin(serverUrl, data).fold(
                onSuccess = { _uiState.update { it.copy(oidcLoggedIn = true, error = null) } },
                onFailure = { e ->
                    AppLog.e(TAG, "OIDC login failed", e)
                    _uiState.update { it.copy(oidcLoggedIn = false, error = "Login failed: ${e.message}") }
                },
            )
        }
    }

    fun connectToRemote() {
        val state = _uiState.value
        AppLog.d(TAG, "connectToRemote called, url='${state.remoteUrl}'")

        if (state.remoteUrl.isBlank()) {
            AppLog.w(TAG, "URL is blank, showing error")
            _uiState.update { it.copy(error = "Please enter a server URL") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, error = null) }

            val url = ServerUrl.normalizeConnectUrl(state.remoteUrl)
            if (url == null) {
                AppLog.w(TAG, "Invalid server URL: '${state.remoteUrl}'")
                _uiState.update { it.copy(isConnecting = false, error = "Invalid server URL") }
                return@launch
            }
            AppLog.d(TAG, "Connecting to normalized URL: $url")

            val config = ServerConfig(
                url = url,
                name = "Remote Server",
                isLocal = false,
                username = state.username.takeIf { it.isNotBlank() },
                allowInsecure = state.allowInsecure,
                authMode = state.authMode,
                oidcIssuer = state.oidcIssuer.takeIf { it.isNotBlank() },
                oidcClientId = state.oidcClientId.takeIf { it.isNotBlank() }
            )
            val password = if (state.authMode == AuthMode.BASIC) {
                state.password.takeIf { it.isNotBlank() }
            } else {
                null
            }

            val result = connectionManager.connect(config, password)

            result.fold(
                onSuccess = { projects ->
                    AppLog.d(TAG, "Connection successful!")
                    // Clear password from UI state after successful connection for security
                    _uiState.update { it.copy(password = "") }
                    settingsDataStore.saveLastConnection(config, password)
                    settingsDataStore.addRecentServer(
                        url = url,
                        name = "Remote Server",
                        username = state.username.takeIf { it.isNotBlank() },
                        password = password,
                        allowInsecure = state.allowInsecure
                    )
                    initializeProjectContext()
                    _uiState.update { it.copy(isConnecting = false, isConnected = true) }
                },
                onFailure = { error ->
                    AppLog.e(TAG, "Connection failed: ${error.message}", error)
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

    fun connectToRecentServer(server: RecentServer) {
        // Load the password from CredentialStore (not from the RecentServer object)
        val savedPassword = credentialStore.getServerPassword(server.url)
        val normalizedUrl = ServerUrl.normalizeConnectUrl(server.url) ?: server.url
        _uiState.update {
            it.copy(
                remoteUrl = normalizedUrl,
                username = server.username ?: ServerUrl.DEFAULT_USERNAME,
                password = savedPassword ?: "",
                allowInsecure = server.allowInsecure
            )
        }
        connectToRemote()
    }

    fun removeRecentServer(server: RecentServer) {
        viewModelScope.launch {
            settingsDataStore.removeRecentServer(server.url)
        }
    }

    private fun collectDiscoveryFlows() {
        viewModelScope.launch {
            mdnsDiscoveryManager.discoveredServers.collect { servers ->
                _uiState.update { it.copy(discoveredServers = servers) }
            }
        }
        viewModelScope.launch {
            mdnsDiscoveryManager.discoveryState.collect { state ->
                _uiState.update { it.copy(discoveryState = state) }
            }
        }
    }

    fun startDiscovery() {
        val state = _uiState.value
        val seeds = buildList {
            if (state.remoteUrl.isNotBlank()) {
                add(DiscoverySeed(state.remoteUrl, state.allowInsecure))
            }
            state.recentServers.forEach { recent ->
                add(DiscoverySeed(recent.url, recent.allowInsecure))
            }
        }
        mdnsDiscoveryManager.startDiscovery(seeds)
    }

    fun stopDiscovery() {
        mdnsDiscoveryManager.stopDiscovery()
    }

    fun connectToDiscoveredServer(server: DiscoveredServer) {
        _uiState.update {
            it.copy(
                remoteUrl = server.url,
                username = ServerUrl.DEFAULT_USERNAME,
                password = "",
                allowInsecure = server.allowInsecure
            )
        }
        connectToRemote()
    }

    /**
     * Initialize project context after successful connection.
     * Now always navigates to the unified Sessions screen.
     */
    private suspend fun initializeProjectContext() {
        AppLog.d(TAG, "Navigating to unified Sessions screen")
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
    val allowInsecure: Boolean = false,
    val authMode: AuthMode = AuthMode.BASIC,
    val oidcIssuer: String = "",
    val oidcClientId: String = "",
    val oidcLoggedIn: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val error: String? = null,
    val recentServers: List<RecentServer> = emptyList(),
    val discoveredServers: List<DiscoveredServer> = emptyList(),
    val discoveryState: DiscoveryState = DiscoveryState.IDLE,
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
