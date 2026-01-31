package dev.blazelight.p4oc.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.components.TuiConfirmDialog
import dev.blazelight.p4oc.ui.theme.Sizing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onDisconnect: () -> Unit,
    onProviderConfig: () -> Unit = {},
    onVisualSettings: () -> Unit = {},
    onAgentsConfig: () -> Unit = {},
    onSkills: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDisconnectDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            modifier = Modifier.size(Sizing.iconLg)
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.server)) },
                supportingContent = { Text(uiState.serverUrl) },
                leadingContent = {
                    Icon(
                        if (uiState.isLocal) Icons.Default.PhoneAndroid else Icons.Default.Cloud,
                        contentDescription = stringResource(R.string.cd_connection_status)
                    )
                }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_provider_model)) },
                supportingContent = { Text(stringResource(R.string.settings_provider_model_desc)) },
                leadingContent = { Icon(Icons.Default.SmartToy, contentDescription = stringResource(R.string.settings_provider_model)) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.cd_chevron_right)) },
                modifier = Modifier.clickable { onProviderConfig() }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_agents)) },
                supportingContent = { Text(stringResource(R.string.settings_agents_desc)) },
                leadingContent = { Icon(Icons.Default.Groups, contentDescription = stringResource(R.string.settings_agents)) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.cd_chevron_right)) },
                modifier = Modifier.clickable { onAgentsConfig() }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_skills)) },
                supportingContent = { Text(stringResource(R.string.settings_skills_desc)) },
                leadingContent = { Icon(Icons.Default.Extension, contentDescription = stringResource(R.string.settings_skills)) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.cd_chevron_right)) },
                modifier = Modifier.clickable { onSkills() }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_visual)) },
                supportingContent = { Text(stringResource(R.string.settings_visual_desc)) },
                leadingContent = { Icon(Icons.Default.Palette, contentDescription = stringResource(R.string.settings_visual)) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.cd_chevron_right)) },
                modifier = Modifier.clickable { onVisualSettings() }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about)) },
                supportingContent = { Text(stringResource(R.string.settings_version)) },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = stringResource(R.string.settings_about)) }
            )

            HorizontalDivider()

            Spacer(Modifier.weight(1f))

            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.settings_disconnect), color = MaterialTheme.colorScheme.error)
                },
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = stringResource(R.string.settings_disconnect),
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.clickable { showDisconnectDialog = true }
            )
        }
    }

    if (showDisconnectDialog) {
        TuiConfirmDialog(
            onDismissRequest = { showDisconnectDialog = false },
            onConfirm = {
                scope.launch {
                    viewModel.disconnect()
                    onDisconnect()
                }
            },
            title = stringResource(R.string.settings_disconnect),
            message = stringResource(R.string.settings_disconnect_confirm),
            confirmText = stringResource(R.string.settings_disconnect),
            dismissText = stringResource(R.string.button_cancel),
            isDestructive = true
        )
    }
}


