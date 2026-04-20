package dev.blazelight.p4oc.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.BuildConfig
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.components.TuiConfirmDialog
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.components.TuiTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onDisconnect: () -> Unit,
    onProviderConfig: () -> Unit = {},
    onVisualSettings: () -> Unit = {},
    onAgentsConfig: () -> Unit = {},
    onSkills: () -> Unit = {},
    onNotificationSettings: () -> Unit = {},
    onConnectionSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val theme = LocalOpenCodeTheme.current
    val githubUrl = stringResource(R.string.settings_about_github_url)

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TuiTopBar(
                title = stringResource(R.string.settings_title),
                onNavigateBack = onNavigateBack
            )
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
                subtitle = if (isConnected) {
                    stringResource(R.string.settings_provider_model_desc)
                } else {
                    stringResource(R.string.settings_requires_connection)
                },
                onClick = if (isConnected) onProviderConfig else null,
                showChevron = isConnected,
                enabled = isConnected,
                testTag = "settings_provider_item"
            )

            SettingsItem(
                icon = Icons.Default.Groups,
                title = stringResource(R.string.settings_agents),
                subtitle = if (isConnected) {
                    stringResource(R.string.settings_agents_desc)
                } else {
                    stringResource(R.string.settings_requires_connection)
                },
                onClick = if (isConnected) onAgentsConfig else null,
                showChevron = isConnected,
                enabled = isConnected
            )

            SettingsItem(
                icon = Icons.Default.Extension,
                title = stringResource(R.string.settings_skills),
                subtitle = if (isConnected) {
                    stringResource(R.string.settings_skills_desc)
                } else {
                    stringResource(R.string.settings_requires_connection)
                },
                onClick = if (isConnected) onSkills else null,
                showChevron = isConnected,
                enabled = isConnected
            )

            // These don't require connection
            SettingsItem(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.settings_visual),
                subtitle = stringResource(R.string.settings_visual_desc),
                onClick = onVisualSettings,
                showChevron = true,
                testTag = "settings_visual_item"
            )

            SettingsItem(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.settings_notifications),
                subtitle = stringResource(R.string.settings_notifications_desc),
                onClick = onNotificationSettings,
                showChevron = true,
                testTag = "settings_notifications_item"
            )

            SettingsItem(
                icon = Icons.Default.Sync,
                title = stringResource(R.string.settings_connection),
                subtitle = stringResource(R.string.settings_connection_desc),
                onClick = onConnectionSettings,
                showChevron = true,
                testTag = "settings_connection_item"
            )

            SettingsItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_about),
                subtitle = stringResource(R.string.settings_version_format, BuildConfig.VERSION_NAME),
                onClick = { showAboutDialog = true },
                showChevron = true,
                testTag = "settings_about_item"
            )

            Spacer(Modifier.weight(1f))

            // Disconnect button — only show when connected
            if (isConnected) {
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = stringResource(R.string.settings_disconnect),
                    onClick = { showDisconnectDialog = true },
                    tint = theme.error,
                    testTag = "settings_disconnect_button"
                )
            }
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

    if (showAboutDialog) {
        dev.blazelight.p4oc.ui.components.TuiAlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = Icons.Default.Info,
            title = stringResource(R.string.settings_about),
            confirmButton = {
                dev.blazelight.p4oc.ui.components.TuiTextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = stringResource(R.string.settings_version_format, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.text
                )
                Text(
                    text = stringResource(R.string.settings_about_build_info, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMuted
                )
                if (uiState.serverUrl.isNotEmpty()) {
                    Text(
                        text = "${stringResource(R.string.server)}: ${uiState.serverUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textMuted
                    )
                }
                dev.blazelight.p4oc.ui.components.TuiButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                        context.startActivity(intent)
                    }
                ) {
                    Text(stringResource(R.string.settings_about_github))
                }
            }
        }
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
    tint: androidx.compose.ui.graphics.Color? = null,
    enabled: Boolean = true,
    testTag: String? = null
) {
    val theme = LocalOpenCodeTheme.current
    val contentAlpha = if (enabled) 1f else 0.4f
    val iconColor = (tint ?: theme.textMuted).copy(alpha = contentAlpha)
    val titleColor = (tint ?: theme.text).copy(alpha = contentAlpha)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        color = theme.background,
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null && enabled) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else Modifier
                )
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
                        color = theme.textMuted.copy(alpha = contentAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (showChevron) {
                Text(
                    text = "→",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textMuted.copy(alpha = contentAlpha)
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


