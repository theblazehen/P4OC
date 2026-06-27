package dev.blazelight.p4oc.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.datastore.ChatSettings
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.ui.components.TuiSwitch
import dev.blazelight.p4oc.ui.components.TuiTopBar
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChatSettingsScreen(
    viewModel: ChatSettingsViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val theme = LocalOpenCodeTheme.current

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TuiTopBar(
                title = stringResource(R.string.settings_chat),
                onNavigateBack = onNavigateBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                    contentDescription = null,
                    modifier = Modifier.size(Sizing.iconMd),
                    tint = theme.accent,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.chat_settings_enter_to_send),
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.text,
                    )
                    Text(
                        text = stringResource(R.string.chat_settings_enter_to_send_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textMuted,
                    )
                }
                TuiSwitch(
                    checked = settings.enterToSend,
                    onCheckedChange = { viewModel.toggleEnterToSend() },
                )
            }

            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

class ChatSettingsViewModel constructor(
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {
    private val _settings = MutableStateFlow(ChatSettings())
    val settings: StateFlow<ChatSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.chatSettings.collect { saved ->
                _settings.value = saved
            }
        }
    }

    fun toggleEnterToSend() {
        val updated = _settings.value.copy(enterToSend = !_settings.value.enterToSend)
        _settings.value = updated
        viewModelScope.launch { settingsDataStore.updateChatSettings(updated) }
    }
}
