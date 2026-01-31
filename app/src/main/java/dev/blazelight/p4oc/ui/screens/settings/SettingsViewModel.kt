package dev.blazelight.p4oc.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.network.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val connectionManager: ConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsDataStore.serverUrl,
                settingsDataStore.isLocalServer,
                settingsDataStore.themeMode
            ) { url, isLocal, theme ->
                SettingsUiState(
                    serverUrl = url,
                    isLocal = isLocal,
                    themeMode = theme
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            settingsDataStore.setThemeMode(mode)
        }
    }

    suspend fun disconnect() {
        connectionManager.disconnect()
        settingsDataStore.clearLastConnection()
    }
}

data class SettingsUiState(
    val serverUrl: String = "",
    val isLocal: Boolean = true,
    val themeMode: String = "system"
)
