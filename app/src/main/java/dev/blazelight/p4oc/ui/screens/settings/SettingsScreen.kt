package dev.blazelight.p4oc.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
    var showLogsDialog by remember { mutableStateOf(false) }
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
            // Connection Section
            SettingsSectionHeader(
                title = "Connection",
                icon = Icons.Default.Cloud
            )
            
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

            Spacer(modifier = Modifier.height(24.dp))

            // App Settings Section
            SettingsSectionHeader(
                title = "App Settings",
                icon = Icons.Default.Settings
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

            Spacer(modifier = Modifier.height(24.dp))

            // About Section
            SettingsSectionHeader(
                title = "About",
                icon = Icons.Default.Info
            )

            SettingsItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_about),
                subtitle = stringResource(R.string.settings_version_format, BuildConfig.VERSION_NAME),
                onClick = { showAboutDialog = true },
                showChevron = true,
                testTag = "settings_about_item"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Diagnostics Section
            SettingsSectionHeader(
                title = "Diagnostics",
                icon = Icons.Default.BugReport
            )

            SettingsItem(
                icon = Icons.Default.Description,
                title = "View Logs",
                subtitle = "View and copy recent app logs",
                onClick = { showLogsDialog = true },
                showChevron = true,
                testTag = "settings_logs_item"
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

    if (showLogsDialog) {
        LogsDialog(
            onDismiss = { showLogsDialog = false }
        )
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
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        color = theme.backgroundElement,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (onClick != null && enabled) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null && enabled) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Modern icon container
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = title,
                    modifier = Modifier.size(20.dp),
                    tint = iconColor
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textMuted.copy(alpha = contentAlpha),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            if (showChevron) {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "Navigate",
                    modifier = Modifier.size(20.dp),
                    tint = theme.textMuted.copy(alpha = contentAlpha * 0.7f)
                )
            }
        }
    }
}



@Composable
private fun SettingsSectionHeader(
    title: String,
    icon: ImageVector
) {
    val theme = LocalOpenCodeTheme.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = theme.accent
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            ),
            color = theme.textMuted,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun LogsDialog(
    onDismiss: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var logs by remember { mutableStateOf("Loading logs...") }
    var isLoading by remember { mutableStateOf(true) }
    var copySuccess by remember { mutableStateOf(false) }

    // Load logs when dialog opens
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                // Get app package name for filtering
                val packageName = context.packageName
                // Read last 200 lines of logcat filtered by app tags
                val process = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-d", "-t", "200", "--pid=${android.os.Process.myPid()}")
                )
                val logsText = process.inputStream.bufferedReader().use { it.readText() }
                logs = if (logsText.isBlank()) {
                    "No recent logs found.\n\nNote: Logs are only available in debug builds or with specific permissions."
                } else {
                    logsText
                }
            } catch (e: Exception) {
                logs = "Error reading logs: ${e.message}\n\nNote: Reading logs requires READ_LOGS permission on some Android versions."
            } finally {
                isLoading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = theme.accent
            )
        },
        title = {
            Text(
                text = "Recent Logs",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp)
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = theme.accent
                        )
                    }
                } else {
                    // Log display area
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        color = theme.backgroundElement,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = logs,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = theme.textMuted,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Copy button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (copySuccess) {
                            Text(
                                text = "Copied to clipboard!",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = theme.success
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(logs))
                                copySuccess = true
                                // Reset copy success message after 2 seconds
                                scope.launch {
                                    delay(2000)
                                    copySuccess = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.accent
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Copy All",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Close",
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        containerColor = theme.background,
        shape = RoundedCornerShape(16.dp)
    )
}
