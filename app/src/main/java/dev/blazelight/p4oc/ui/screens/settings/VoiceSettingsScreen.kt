package dev.blazelight.p4oc.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.datastore.VoiceSettings
import dev.blazelight.p4oc.ui.components.TuiSwitch
import dev.blazelight.p4oc.ui.components.TuiTopBar
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun VoiceSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: VoiceSettingsViewModel = koinViewModel()
) {
    val settings by viewModel.voiceSettings.collectAsStateWithLifecycle()
    val theme = LocalOpenCodeTheme.current

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TuiTopBar(
                title = stringResource(R.string.voice_settings_title),
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            SettingSwitchItem(
                title = stringResource(R.string.voice_settings_enable),
                subtitle = stringResource(R.string.voice_settings_enable_desc),
                checked = settings.enabled,
                onCheckedChange = { viewModel.updateSettings(settings.copy(enabled = it)) },
                icon = Icons.Default.Mic
            )

            if (settings.enabled) {
                SettingSwitchItem(
                    title = stringResource(R.string.voice_settings_read_streaming),
                    subtitle = stringResource(R.string.voice_settings_read_streaming_desc),
                    checked = settings.readWhileStreaming,
                    onCheckedChange = { viewModel.updateSettings(settings.copy(readWhileStreaming = it)) },
                    icon = Icons.Default.RecordVoiceOver
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val theme = LocalOpenCodeTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Icon(
            icon,
            contentDescription = null,
            tint = theme.textMuted
        )
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMuted
                )
            }
        }
        TuiSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
