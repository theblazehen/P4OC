package dev.blazelight.p4oc.ui.screens.chat

import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.data.remote.dto.AgentDto
import dev.blazelight.p4oc.data.remote.dto.ModelDto
import dev.blazelight.p4oc.data.remote.dto.ModelInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Manages model/agent loading, selection, favorites, and recents.
 */
class ModelAgentManager(
    private val connectionManager: ConnectionManager,
    private val settingsDataStore: SettingsDataStore,
    private val scope: CoroutineScope
) {
    private val _availableAgents = MutableStateFlow<List<AgentDto>>(emptyList())
    val availableAgents: StateFlow<List<AgentDto>> = _availableAgents.asStateFlow()

    private val _selectedAgent = MutableStateFlow<String?>(null)
    val selectedAgent: StateFlow<String?> = _selectedAgent.asStateFlow()

    private val _availableModels = MutableStateFlow<List<Pair<String, ModelDto>>>(emptyList())
    val availableModels: StateFlow<List<Pair<String, ModelDto>>> = _availableModels.asStateFlow()

    private val _selectedModel = MutableStateFlow<ModelInput?>(null)
    val selectedModel: StateFlow<ModelInput?> = _selectedModel.asStateFlow()

    val favoriteModels: StateFlow<Set<ModelInput>> = settingsDataStore.favoriteModels
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val recentModels: StateFlow<List<ModelInput>> = settingsDataStore.recentModels
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun loadAgents() {
        scope.launch {
            val api = connectionManager.getApi() ?: run {
                AppLog.d(TAG, "loadAgents: No API available")
                return@launch
            }
            val result = safeApiCall { api.getAgents() }
            when (result) {
                is ApiResult.Success -> {
                    AppLog.d(TAG, "loadAgents: Got ${result.data.size} agents")
                    val primaryAgents = result.data.filter {
                        it.mode == "primary" && it.hidden != true
                    }
                    AppLog.d(TAG, "loadAgents: ${primaryAgents.size} primary agents: ${primaryAgents.map { it.name }}")
                    _availableAgents.value = primaryAgents
                    _selectedAgent.value = primaryAgents.find { it.name == "build" }?.name
                        ?: primaryAgents.firstOrNull()?.name
                }
                is ApiResult.Error -> {
                    AppLog.e(TAG, "loadAgents failed: ${result.message}")
                }
            }
        }
    }

    fun selectAgent(agentName: String) {
        _selectedAgent.value = agentName
    }

    fun loadModels() {
        scope.launch {
            val api = connectionManager.getApi() ?: return@launch
            val result = safeApiCall { api.getProviders() }
            when (result) {
                is ApiResult.Success -> {
                    val models = mutableListOf<Pair<String, ModelDto>>()
                    result.data.connected.forEach { providerId ->
                        val provider = result.data.all.find { it.id == providerId }
                        provider?.models?.values?.forEach { model ->
                            models.add(providerId to model)
                        }
                    }
                    val defaultModel = result.data.default.entries.firstOrNull()?.let { (provider, modelId) ->
                        ModelInput(providerID = provider, modelID = modelId)
                    }
                    val lastUsedModel = recentModels.value.firstOrNull()
                    val selectedModel = if (lastUsedModel != null && models.any {
                            it.first == lastUsedModel.providerID && it.second.id == lastUsedModel.modelID
                        }) {
                        lastUsedModel
                    } else {
                        defaultModel
                    }
                    _availableModels.value = models
                    _selectedModel.value = selectedModel
                }
                is ApiResult.Error -> {}
            }
        }
    }

    fun selectModel(model: ModelInput) {
        _selectedModel.value = model
        scope.launch {
            settingsDataStore.addRecentModel(model)
        }
    }

    fun toggleFavoriteModel(model: ModelInput) {
        scope.launch {
            settingsDataStore.toggleFavoriteModel(model)
        }
    }

    private companion object {
        const val TAG = "ModelAgentManager"
    }
}
