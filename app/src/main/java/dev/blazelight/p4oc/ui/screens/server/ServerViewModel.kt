@file:Suppress("ImportOrdering")

package dev.blazelight.p4oc.ui.screens.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.core.datastore.RecentServer
import dev.blazelight.p4oc.core.datastore.SavedServer
import dev.blazelight.p4oc.core.datastore.SavedServerRegistry
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.DiscoveredServer
import dev.blazelight.p4oc.core.network.DiscoverySeed
import dev.blazelight.p4oc.core.network.DiscoveryState
import dev.blazelight.p4oc.core.network.MdnsDiscoveryManager
import dev.blazelight.p4oc.core.network.ServerConnectionRegistry
import dev.blazelight.p4oc.core.network.ServerUrl
import dev.blazelight.p4oc.core.network.toServerConfig
import dev.blazelight.p4oc.core.security.CredentialStore
import dev.blazelight.p4oc.domain.server.ServerIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

enum class ServerConnectionStatus { CONNECTED, CONNECTING, AVAILABLE, DISCONNECTED, ERROR }

data class ServerInventoryEntry(
    val server: SavedServer,
    val discovered: DiscoveredServer?,
    val status: ServerConnectionStatus,
)

data class ServerInventory(
    val saved: List<ServerInventoryEntry>,
    val nearby: List<DiscoveredServer>,
)

internal fun buildServerInventory(
    state: ServerUiState,
    connectionStates: Map<String, ConnectionState> = emptyMap(),
): ServerInventory {
    val discoveredByEndpoint = state.discoveredServers.associateBy {
        ServerUrl.endpointKey(it.url) ?: it.url.trim()
    }
    val saved = state.savedServers.distinctBy(SavedServer::endpointKey).map { server ->
        val discovered = discoveredByEndpoint[server.endpointKey]
        val status = when (connectionStates[server.endpointKey]) {
            ConnectionState.Connected -> ServerConnectionStatus.CONNECTED
            ConnectionState.Connecting -> ServerConnectionStatus.CONNECTING
            is ConnectionState.Error -> ServerConnectionStatus.ERROR
            ConnectionState.Disconnected, null -> when {
                state.connectedEndpointKey == server.endpointKey && state.isConnected ->
                    ServerConnectionStatus.CONNECTED
                state.connectingEndpointKey == server.endpointKey && state.isConnecting ->
                    ServerConnectionStatus.CONNECTING
                state.failedEndpointKey == server.endpointKey -> ServerConnectionStatus.ERROR
                discovered != null -> ServerConnectionStatus.AVAILABLE
                else -> ServerConnectionStatus.DISCONNECTED
            }
        }
        ServerInventoryEntry(server, discovered, status)
    }
    val savedKeys = saved.mapTo(mutableSetOf()) { it.server.endpointKey }
    return ServerInventory(
        saved = saved,
        nearby = state.discoveredServers.filter {
            (ServerUrl.endpointKey(it.url) ?: it.url.trim()) !in savedKeys
        },
    )
}

private const val TAG = "ServerViewModel"

class ServerViewModel constructor(
    private val settingsDataStore: SettingsDataStore,
    private val serverConnectionRegistry: ServerConnectionRegistry,
    private val credentialStore: CredentialStore,
    private val mdnsDiscoveryManager: MdnsDiscoveryManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerUiState())
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()

    private var started = false

    fun start(autoReconnect: Boolean) {
        if (started) return
        started = true
        loadRecentServers()
        loadSavedServers()
        collectDiscoveryFlows()
        if (autoReconnect) {
            viewModelScope.launch {
                if (settingsDataStore.connectionSettings.first().autoReconnect) {
                    tryAutoReconnect()
                }
            }
        }
    }

    private fun loadRecentServers() {
        viewModelScope.launch {
            settingsDataStore.recentServers.collect { servers ->
                _uiState.update { it.copy(recentServers = servers) }
            }
        }
    }

    private fun loadSavedServers() {
        viewModelScope.launch {
            settingsDataStore.savedServers.collect { servers ->
                _uiState.update { it.copy(savedServers = servers) }
            }
        }
    }

    private suspend fun tryAutoReconnect() {
        val (lastConfig, password) = settingsDataStore.getLastConnection() ?: return

        AppLog.d(TAG, "Found last connection")
        val endpointKey = ServerUrl.endpointKey(lastConfig.url)
        _uiState.update {
            it.copy(
                isConnecting = true,
                connectingEndpointKey = endpointKey,
                remoteUrl = lastConfig.url,
                username = lastConfig.username ?: ServerUrl.DEFAULT_USERNAME,
                password = password ?: "",
                allowInsecure = lastConfig.allowInsecure
            )
        }

        val server = SavedServerRegistry.fromConnection(
            url = lastConfig.url,
            name = lastConfig.name,
            username = lastConfig.username,
            allowInsecure = lastConfig.allowInsecure,
        )
        val result = serverConnectionRegistry.connectAndAwait(server, password)

        result.fold(
            onSuccess = { projects ->
                AppLog.d(TAG, "Auto-reconnect successful")
                initializeProjectContext()
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        isConnected = true,
                        connectingEndpointKey = null,
                        connectedEndpointKey = endpointKey,
                        failedEndpointKey = null,
                    )
                }
            },
            onFailure = { error ->
                AppLog.w(TAG, "Auto-reconnect failed")
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        connectingEndpointKey = null,
                        failedEndpointKey = endpointKey,
                        error = "Could not reconnect to the server. Check the address and connection."
                    )
                }
            }
        )
    }

    fun setRemoteUrl(url: String) {
        _uiState.update { it.copy(remoteUrl = url, serverNameCandidate = null, error = null) }
    }

    fun setUsername(username: String) {
        _uiState.update { it.copy(username = username) }
    }

    fun setPassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun setAllowInsecure(allow: Boolean) {
        _uiState.update { it.copy(allowInsecure = allow) }
    }

    fun connectToRemote() {
        val state = _uiState.value
        AppLog.d(TAG, "connectToRemote called")

        val formError = state.connectFormError()
        if (formError != null) {
            AppLog.w(TAG, "Connect form incomplete")
            _uiState.update { it.copy(error = formError) }
            return
        }

        viewModelScope.launch {
            val url = ServerUrl.normalizeConnectUrl(state.remoteUrl)
            if (url == null) {
                AppLog.w(TAG, "Invalid server URL")
                _uiState.update { it.copy(isConnecting = false, error = "Invalid server URL") }
                return@launch
            }
            val password = state.password.takeIf { it.isNotBlank() }
            if (state.username.isNotBlank() && password != null &&
                !ServerUrl.allowsCleartextCredentials(url)
            ) {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        error = "Credentials require HTTPS outside a private local network",
                    )
                }
                return@launch
            }
            val endpointKey = ServerUrl.endpointKey(url)
            _uiState.update {
                it.copy(
                    isConnecting = true,
                    connectingEndpointKey = endpointKey,
                    failedEndpointKey = null,
                    error = null,
                )
            }
            AppLog.d(TAG, "Connecting to normalized URL")
            val identity = ServerIdentity.derive(url, state.serverNameCandidate)

            val candidate = SavedServerRegistry.fromConnection(
                url = url,
                name = identity.displayName,
                username = state.username.takeIf { it.isNotBlank() },
                allowInsecure = state.allowInsecure,
            )
            val config = candidate.toServerConfig()
            val result = serverConnectionRegistry.connectAndAwait(candidate, password)

            result.fold(
                onSuccess = { projects ->
                    AppLog.d(TAG, "Connection successful!")
                    // Clear password from UI state after successful connection for security
                    _uiState.update { it.copy(password = "") }
                    settingsDataStore.saveLastConnection(config, password)
                    settingsDataStore.addRecentServer(
                        url = url,
                        name = identity.displayName,
                        username = state.username.takeIf { it.isNotBlank() },
                        password = password,
                        allowInsecure = state.allowInsecure
                    )
                    settingsDataStore.addSavedServer(
                        url = url,
                        name = identity.displayName,
                        username = state.username.takeIf { it.isNotBlank() },
                        password = password,
                        allowInsecure = state.allowInsecure,
                        lastConnectedAt = System.currentTimeMillis(),
                    )
                    initializeProjectContext()
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            isConnected = true,
                            connectingEndpointKey = null,
                            connectedEndpointKey = endpointKey,
                            failedEndpointKey = null,
                            showTlsOptions = false,
                        )
                    }
                },
                onFailure = { error ->
                    AppLog.e(TAG, "Connection failed")
                    // A TLS trust failure is the only case where the self-signed escape hatch is
                    // useful, so it stays hidden until an attempt actually fails that way.
                    val tlsFailure = error.isTlsTrustFailure()
                    // Clear password from UI state on failure too - user can re-enter
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            connectingEndpointKey = null,
                            failedEndpointKey = endpointKey,
                            password = "",
                            showTlsOptions = it.showTlsOptions || tlsFailure,
                            error = if (tlsFailure) {
                                "The server's TLS certificate is not trusted."
                            } else {
                                "Could not connect. Check the address, credentials, and network."
                            }
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
                serverNameCandidate = server.name,
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

    fun prepareSavedServer(server: SavedServer) {
        val password = credentialStore.getServerPassword(server.id)
            ?: credentialStore.getServerPassword(server.endpoint)
            ?: ""
        _uiState.update {
            it.copy(
                remoteUrl = server.endpoint,
                serverNameCandidate = server.displayName,
                username = server.username ?: ServerUrl.DEFAULT_USERNAME,
                password = password,
                allowInsecure = server.allowInsecure,
                showTlsOptions = server.allowInsecure,
                error = null,
            )
        }
    }

    fun connectToSavedServer(server: SavedServer) {
        prepareSavedServer(server)
        connectToRemote()
    }

    fun saveSavedServer(server: SavedServer) {
        val state = _uiState.value
        val url = ServerUrl.normalizeConnectUrl(state.remoteUrl)
        if (url == null) {
            _uiState.update { it.copy(error = "Invalid server URL") }
            return
        }
        val identity = ServerIdentity.derive(url, state.serverNameCandidate ?: server.displayName)
        viewModelScope.launch {
            val updated = settingsDataStore.addSavedServer(
                url = url,
                name = identity.displayName,
                username = state.username.takeIf(String::isNotBlank),
                password = state.password.takeIf(String::isNotBlank),
                allowInsecure = state.allowInsecure,
                pinned = server.pinned,
                defaultWorkspace = server.defaultWorkspace,
                lastConnectedAt = server.lastConnectedAt,
            )
            if (updated.id != server.id) settingsDataStore.removeSavedServer(server.id)
            _uiState.update { it.copy(remoteUrl = updated.endpoint, password = "", error = null) }
        }
    }

    fun removeSavedServer(server: SavedServer) {
        viewModelScope.launch {
            settingsDataStore.removeSavedServer(server.id)
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
        // Reuse a stored password when we already know this endpoint. A missing password is valid:
        // passwordless OpenCode servers must still be allowed to attempt the connection.
        val savedPassword = credentialStore.getServerPassword(server.url).orEmpty()
        _uiState.update {
            it.copy(
                remoteUrl = server.url,
                serverNameCandidate = server.serviceName,
                username = ServerUrl.DEFAULT_USERNAME,
                password = savedPassword,
                allowInsecure = server.allowInsecure,
                showTlsOptions = server.allowInsecure,
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

/**
 * Why the connect form cannot be submitted yet, or `null` when it is complete. Authentication is
 * optional. The username defaults to OpenCode's conventional value, and is only required when a
 * password is supplied.
 */
internal fun ServerUiState.connectFormError(): String? = when {
    remoteUrl.isBlank() -> "Enter a server URL"
    password.isNotBlank() && username.isBlank() -> "Enter a username (usually opencode)"
    else -> null
}

/** True when [this] (or any cause beneath it) is a certificate/TLS trust failure. */
internal fun Throwable.isTlsTrustFailure(): Boolean {
    var current: Throwable? = this
    val seen = HashSet<Throwable>()
    while (current != null && seen.add(current)) {
        val isTls = current is SSLException ||
            current is CertificateException ||
            current is CertPathValidatorException
        if (isTls) return true
        current = current.cause
    }
    return false
}

data class ServerUiState(
    val remoteUrl: String = "",
    val serverNameCandidate: String? = null,
    val username: String = ServerUrl.DEFAULT_USERNAME,
    val password: String = "",
    val allowInsecure: Boolean = false,
    /**
     * Whether to surface the self-signed-certificate escape hatch. OpenCode is normally served
     * over plain HTTP, so the toggle only appears once a connection actually fails TLS validation
     * (or when editing a server that already has it on).
     */
    val showTlsOptions: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val error: String? = null,
    val connectingEndpointKey: String? = null,
    val connectedEndpointKey: String? = null,
    val failedEndpointKey: String? = null,
    val recentServers: List<RecentServer> = emptyList(),
    val savedServers: List<SavedServer> = emptyList(),
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
