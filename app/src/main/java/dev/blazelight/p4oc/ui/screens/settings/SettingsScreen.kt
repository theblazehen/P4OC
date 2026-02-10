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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.components.TuiConfirmDialog
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
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
    val theme = LocalOpenCodeTheme.current

    Scaffold(
        containerColor = theme.background,
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = theme.backgroundElement,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(Sizing.iconButtonMd)
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
            // Server info (non-clickable)
            SettingsItem(
                icon = if (uiState.isLocal) Icons.Default.PhoneAndroid else Icons.Default.Cloud,
                title = stringResource(R.string.server),
                subtitle = uiState.serverUrl
            )

            SettingsItem(
                icon = Icons.Default.SmartToy,
                title = stringResource(R.string.settings_provider_model),
                subtitle = stringResource(R.string.settings_provider_model_desc),
                onClick = onProviderConfig,
                showChevron = true
            )

            SettingsItem(
                icon = Icons.Default.Groups,
                title = stringResource(R.string.settings_agents),
                subtitle = stringResource(R.string.settings_agents_desc),
                onClick = onAgentsConfig,
                showChevron = true
            )

            SettingsItem(
                icon = Icons.Default.Extension,
                title = stringResource(R.string.settings_skills),
                subtitle = stringResource(R.string.settings_skills_desc),
                onClick = onSkills,
                showChevron = true
            )

            SettingsItem(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.settings_visual),
                subtitle = stringResource(R.string.settings_visual_desc),
                onClick = onVisualSettings,
                showChevron = true
            )

            SettingsItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_about),
                subtitle = stringResource(R.string.settings_version)
            )

            Spacer(Modifier.weight(1f))

            // Disconnect button
            SettingsItem(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = stringResource(R.string.settings_disconnect),
                onClick = { showDisconnectDialog = true },
                tint = theme.error
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

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
    tint: androidx.compose.ui.graphics.Color? = null
) {
    val theme = LocalOpenCodeTheme.current
    val iconColor = tint ?: theme.textMuted
    val titleColor = tint ?: theme.text

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = theme.background,
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = Spacing.lg, vertical = Spacing.mdLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Icon(
                icon,
                contentDescription = title,
                modifier = Modifier.size(Sizing.iconMd),
                tint = iconColor
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (showChevron) {
                Text(
                    text = "→",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textMuted
                )
            }
        }
    }
    // Thin separator
    HorizontalDivider(
        thickness = Sizing.dividerThickness,
        color = theme.borderSubtle
    )
}


