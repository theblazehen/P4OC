package dev.blazelight.p4oc.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.core.network.ServerConnectionRegistry
import dev.blazelight.p4oc.data.remote.dto.ModelInput
import dev.blazelight.p4oc.data.remote.dto.OAuthCallbackRequest
import dev.blazelight.p4oc.data.remote.dto.ProviderAuthAuthorizationDto
import dev.blazelight.p4oc.data.remote.dto.ProviderAuthAuthorizeRequest
import dev.blazelight.p4oc.data.remote.dto.ProviderAuthMethodDto
import dev.blazelight.p4oc.data.remote.dto.ProviderDto
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.server.affectsCatalogIn
import dev.blazelight.p4oc.ui.screens.chat.ModelSelectionCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
internal fun ServerConnectionRegistry.observeWorkspaceCatalogEvents(
    workspaceClient: WorkspaceClient,
    scope: CoroutineScope,
    mcpOnly: Boolean = false,
    includeMcp: Boolean = false,
    refresh: () -> Unit,
) {
    scope.launch {
        events(workspaceClient.workspace.server)
            .filter { scoped ->
                scoped.affectsCatalogIn(workspaceClient.workspace, workspaceClient.generation) &&
                    if (mcpOnly) {
                        scoped.event is OpenCodeEvent.McpToolsChanged
                    } else {
                        scoped.event is OpenCodeEvent.ModelsRefreshed ||
                            scoped.event is OpenCodeEvent.CatalogUpdated ||
                            (includeMcp && scoped.event is OpenCodeEvent.McpToolsChanged)
                    }
            }
            .debounce(CATALOG_REFRESH_DEBOUNCE_MS)
            .collect { refresh() }
    }
}

private const val CATALOG_REFRESH_DEBOUNCE_MS = 150L

data class ProviderConfigUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val providers: List<ProviderDto> = emptyList(),
    val connectedProviderIds: List<String> = emptyList(),
    val currentModel: String? = null,
    val canSetDefaultModel: Boolean = true,
    val selectedProviderId: String? = null,
    val authMethods: Map<String, List<ProviderAuthMethodDto>> = emptyMap(),
    val pendingAuthorization: PendingProviderAuthorization? = null,
    val isAuthenticating: Boolean = false
)

data class PendingProviderAuthorization(
    val providerId: String,
    val methodIndex: Int,
    val authorization: ProviderAuthAuthorizationDto
)

class ProviderConfigViewModel constructor(
    private val workspaceClient: WorkspaceClient,
    private val modelSelectionCoordinator: ModelSelectionCoordinator = ModelSelectionCoordinator(),
    serverConnectionRegistry: dev.blazelight.p4oc.core.network.ServerConnectionRegistry? = null,
) : ViewModel() {

    private companion object { const val TAG = "ProviderConfigViewModel" }

    private val _uiState = MutableStateFlow(
        ProviderConfigUiState(canSetDefaultModel = workspaceClient.supportsGlobalModelConfig)
    )
    val uiState: StateFlow<ProviderConfigUiState> = _uiState.asStateFlow()

    init {
        loadProviders()
        serverConnectionRegistry?.observeWorkspaceCatalogEvents(workspaceClient, viewModelScope) {
            loadProviders(background = true)
        }
    }

    fun loadProviders() = loadProviders(background = false)

    private fun loadProviders(background: Boolean) {
        viewModelScope.launch {
            if (!background) _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val providersResponse = workspaceClient.getProviders()
                val config = workspaceClient.getConfig()
                val authMethods = if (workspaceClient.supportsGlobalModelConfig) {
                    workspaceClient.getProviderAuthMethods()
                } else {
                    emptyMap()
                }

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        providers = providersResponse.all,
                        connectedProviderIds = providersResponse.connected,
                        currentModel = config.model,
                        authMethods = authMethods,
                        error = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to load providers")
                if (!background) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Could not load providers. Check the connection and try again."
                        )
                    }
                }
            }
        }
    }

    fun selectProvider(providerId: String) {
        _uiState.update { it.copy(selectedProviderId = providerId) }
    }

    fun startOAuth(providerId: String, methodIndex: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthenticating = true, error = null) }
            try {
                val authorization = workspaceClient.authorizeProvider(
                    providerId,
                    ProviderAuthAuthorizeRequest(methodIndex)
                )
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        pendingAuthorization = PendingProviderAuthorization(
                            providerId,
                            methodIndex,
                            authorization
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to start provider authentication")
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        error = "Could not start provider authentication. Try again."
                    )
                }
            }
        }
    }

    fun completeOAuth(code: String? = null) {
        val pending = _uiState.value.pendingAuthorization ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthenticating = true, error = null) }
            try {
                val completed = workspaceClient.completeProviderOAuth(
                    pending.providerId,
                    OAuthCallbackRequest(
                        method = pending.methodIndex,
                        code = code?.trim()?.takeIf(String::isNotEmpty)
                    )
                )
                check(completed) { "OAuth callback rejected" }
                _uiState.update { it.copy(pendingAuthorization = null, isAuthenticating = false) }
                loadProviders()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to complete provider authentication")
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        error = "Provider authentication was not completed. Try again."
                    )
                }
            }
        }
    }

    fun dismissAuthorization() {
        _uiState.update { it.copy(pendingAuthorization = null) }
    }

    fun setModel(providerId: String, modelId: String) {
        viewModelScope.launch {
            try {
                val currentConfig = workspaceClient.getConfig()
                val newModel = "$providerId/$modelId"
                val updatedConfig = currentConfig.copy(model = newModel)
                val savedConfig = workspaceClient.updateConfig(updatedConfig)
                val savedModel = savedConfig.model ?: newModel
                _uiState.update { it.copy(currentModel = savedModel, error = null) }
                parseModelInput(savedModel)?.let { model ->
                    modelSelectionCoordinator.publishActiveModel(
                        workspaceClient.workspace,
                        workspaceClient.generation,
                        model,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to set provider model")
                _uiState.update { it.copy(error = "Could not set the model. Try again.") }
            }
        }
    }

    private fun parseModelInput(value: String): ModelInput? {
        val separator = value.indexOf('/')
        if (separator <= 0 || separator == value.lastIndex) return null
        return ModelInput(
            providerID = value.substring(0, separator),
            modelID = value.substring(separator + 1)
        )
    }
}
