package com.pocketcode.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcode.core.datastore.SettingsDataStore
import com.pocketcode.core.network.OpenCodeEventSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val eventSource: OpenCodeEventSource
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

    fun disconnect() {
        eventSource.disconnect()
        viewModelScope.launch {
            settingsDataStore.setServerConfig(
                url = SettingsDataStore.DEFAULT_LOCAL_URL,
                name = "Local (Termux)",
                isLocal = true
            )
        }
    }
}

data class SettingsUiState(
    val serverUrl: String = "",
    val isLocal: Boolean = true,
    val themeMode: String = "system"
)
