package dev.blazelight.p4oc.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.datastore.VoiceSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VoiceSettingsViewModel(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val voiceSettings: StateFlow<VoiceSettings> = settingsDataStore.voiceSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VoiceSettings()
        )

    fun updateSettings(settings: VoiceSettings) {
        viewModelScope.launch {
            settingsDataStore.updateVoiceSettings(settings)
        }
    }
}
