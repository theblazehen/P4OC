package com.pocketcode.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcode.core.network.ConnectionManager
import com.pocketcode.data.remote.dto.ModelDto
import com.pocketcode.data.remote.dto.ProviderDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderConfigUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val providers: List<ProviderDto> = emptyList(),
    val connectedProviderIds: List<String> = emptyList(),
    val currentModel: String? = null,
    val selectedProviderId: String? = null
)

@HiltViewModel
class ProviderConfigViewModel @Inject constructor(
    private val connectionManager: ConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProviderConfigUiState())
    val uiState: StateFlow<ProviderConfigUiState> = _uiState.asStateFlow()

    init {
        loadProviders()
    }

    fun loadProviders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val api = connectionManager.getApi() ?: run {
                _uiState.update { it.copy(isLoading = false, error = "Not connected") }
                return@launch
            }
            try {
                val providersResponse = api.getProviders()
                val config = api.getConfig()
                
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        providers = providersResponse.all,
                        connectedProviderIds = providersResponse.connected,
                        currentModel = config.model,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        error = e.message ?: "Failed to load providers"
                    ) 
                }
            }
        }
    }

    fun selectProvider(providerId: String) {
        _uiState.update { it.copy(selectedProviderId = providerId) }
    }

    fun setModel(providerId: String, modelId: String) {
        viewModelScope.launch {
            val api = connectionManager.getApi() ?: run {
                _uiState.update { it.copy(error = "Not connected") }
                return@launch
            }
            try {
                val currentConfig = api.getConfig()
                val newModel = "$providerId/$modelId"
                val updatedConfig = currentConfig.copy(model = newModel)
                api.updateConfig(updatedConfig)
                _uiState.update { it.copy(currentModel = newModel) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to set model") }
            }
        }
    }
}
