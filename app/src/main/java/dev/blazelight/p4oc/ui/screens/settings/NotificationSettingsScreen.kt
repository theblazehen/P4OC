package dev.blazelight.p4oc.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.datastore.NotificationSettings
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.datastore.VibrationPattern
import dev.blazelight.p4oc.core.haptic.HapticFeedback
import dev.blazelight.p4oc.ui.components.TuiAlertDialog
import dev.blazelight.p4oc.ui.components.TuiButton
import dev.blazelight.p4oc.ui.components.TuiSwitch
import dev.blazelight.p4oc.ui.components.TuiTextButton
import dev.blazelight.p4oc.ui.components.TuiTopBar
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class NotificationSettingsViewModel constructor(
    private val settingsDataStore: SettingsDataStore,
    private val hapticFeedback: HapticFeedback,
) : ViewModel() {

    private val _settings = MutableStateFlow(NotificationSettings())
    val settings: StateFlow<NotificationSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.notificationSettings.collect { saved ->
                _settings.value = saved
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        val new = _settings.value.copy(enabled = enabled)
        _settings.value = new
        viewModelScope.launch { settingsDataStore.updateNotificationSettings(new) }
    }

    fun togglePermissionRequests() {
        val new = _settings.value.copy(permissionRequests = !_settings.value.permissionRequests)
        _settings.value = new
        viewModelScope.launch { settingsDataStore.updateNotificationSettings(new) }
    }

    fun toggleQuestions() {
        val new = _settings.value.copy(questions = !_settings.value.questions)
        _settings.value = new
        viewModelScope.launch { settingsDataStore.updateNotificationSettings(new) }
    }

    fun toggleNotifyOnCompletion() {
        val new = _settings.value.copy(notifyOnCompletion = !_settings.value.notifyOnCompletion)
        _settings.value = new
        viewModelScope.launch { settingsDataStore.updateNotificationSettings(new) }
    }

    fun setVibrationPattern(pattern: VibrationPattern) {
        val new = _settings.value.copy(vibrationPattern = pattern)
        _settings.value = new
        viewModelScope.launch { settingsDataStore.updateNotificationSettings(new) }
    }

    fun previewVibration(pattern: VibrationPattern) {
        hapticFeedback.preview(pattern)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    viewModel: NotificationSettingsViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val theme = LocalOpenCodeTheme.current
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    var showPermissionDeniedDialog by remember { mutableStateOf(false) }
    var showVibrationPatternDialog by remember { mutableStateOf(false) }

    // Re-check permission when screen resumes (e.g., returning from system settings)
    LifecycleResumeEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            viewModel.setEnabled(true)
        } else {
            viewModel.setEnabled(false)
            showPermissionDeniedDialog = true
        }
    }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TuiTopBar(
                title = stringResource(R.string.settings_notifications),
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
            // Permission warning banner (when denied on API 33+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission) {
                PermissionWarningBanner(
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            }

            // General section
            SectionHeader(title = stringResource(R.string.notification_general))

            NotificationSwitch(
                title = stringResource(R.string.notification_enable),
                subtitle = stringResource(R.string.notification_enable_desc),
                icon = Icons.Default.Notifications,
                checked = settings.enabled,
                onCheckedChange = { shouldEnable ->
                    if (!shouldEnable) {
                        viewModel.setEnabled(false)
                        return@NotificationSwitch
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setEnabled(true)
                    }
                },
                enabled = true,
                testTag = "notification_enable_switch"
            )

            // Types section
            SectionHeader(title = stringResource(R.string.notification_types))

            NotificationSwitch(
                title = stringResource(R.string.notification_permission_requests),
                subtitle = stringResource(R.string.notification_permission_requests_desc),
                icon = Icons.Default.Security,
                checked = settings.permissionRequests,
                onCheckedChange = { viewModel.togglePermissionRequests() },
                enabled = settings.enabled,
                testTag = "notification_permissions_switch"
            )

            NotificationSwitch(
                title = stringResource(R.string.notification_questions),
                subtitle = stringResource(R.string.notification_questions_desc),
                icon = Icons.Default.QuestionAnswer,
                checked = settings.questions,
                onCheckedChange = { viewModel.toggleQuestions() },
                enabled = settings.enabled,
                testTag = "notification_questions_switch"
            )

            NotificationSwitch(
                title = stringResource(R.string.notification_completion),
                subtitle = stringResource(R.string.notification_completion_desc),
                icon = Icons.Default.DoneAll,
                checked = settings.notifyOnCompletion,
                onCheckedChange = { viewModel.toggleNotifyOnCompletion() },
                enabled = settings.enabled,
                testTag = "notify_on_completion_switch"
            )

            VibrationPatternRow(
                pattern = settings.vibrationPattern,
                onClick = { showVibrationPatternDialog = true }
            )
        }
    }

    if (showPermissionDeniedDialog) {
        TuiAlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            title = stringResource(R.string.notification_permission_required),
            icon = Icons.Default.NotificationsOff,
            confirmButton = {
                TuiButton(
                    onClick = {
                        showPermissionDeniedDialog = false
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text(stringResource(R.string.notification_open_settings))
                }
            },
            dismissButton = {
                TuiTextButton(onClick = { showPermissionDeniedDialog = false }) {
                    Text(stringResource(R.string.button_cancel))
                }
            }
        ) {
            Text(
                text = stringResource(R.string.notification_permission_denied_message),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textMuted
            )
        }
    }

    if (showVibrationPatternDialog) {
        VibrationPatternDialog(
            currentPattern = settings.vibrationPattern,
            onPreview = viewModel::previewVibration,
            onConfirm = { pattern ->
                viewModel.setVibrationPattern(pattern)
                showVibrationPatternDialog = false
            },
            onDismiss = { showVibrationPatternDialog = false }
        )
    }
}

@Composable
private fun PermissionWarningBanner(
    onRequestPermission: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        color = theme.warning.copy(alpha = 0.1f),
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = theme.warning,
                modifier = Modifier.size(Sizing.iconMd)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.notification_permission_not_granted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.text
                )
                Text(
                    text = stringResource(R.string.notification_permission_tap_to_grant),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMuted
                )
            }
            TuiButton(onClick = onRequestPermission) {
                Text(stringResource(R.string.notification_grant))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val theme = LocalOpenCodeTheme.current
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = theme.accent,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
    )
}

@Composable
private fun VibrationPatternRow(
    pattern: VibrationPattern,
    onClick: () -> Unit,
) {
    val theme = LocalOpenCodeTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("vibration_pattern_row")
            .padding(horizontal = Spacing.lg, vertical = Spacing.mdLg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Vibration,
                contentDescription = null,
                tint = theme.textMuted
            )
            Column {
                Text(
                    text = stringResource(R.string.notification_vibration_pattern),
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.text
                )
                Text(
                    text = stringResource(pattern.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMuted
                )
            }
        }
        Text(
            text = "→",
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textMuted
        )
    }
    HorizontalDivider(
        thickness = Sizing.dividerThickness,
        color = theme.borderSubtle
    )
}

@Composable
private fun VibrationPatternDialog(
    currentPattern: VibrationPattern,
    onPreview: (VibrationPattern) -> Unit,
    onConfirm: (VibrationPattern) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedPattern by remember(currentPattern) { mutableStateOf(currentPattern) }

    TuiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.notification_vibration_pattern),
        icon = Icons.Default.Vibration,
        confirmButton = {
            TuiButton(onClick = { onConfirm(selectedPattern) }) {
                Text(stringResource(R.string.button_done))
            }
        },
        dismissButton = {
            TuiTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        },
        modifier = Modifier.testTag("vibration_pattern_dialog"),
    ) {
        VibrationPattern.entries.forEach { pattern ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) {
                        selectedPattern = pattern
                        onPreview(pattern)
                    }
                    .testTag("vibration_pattern_${pattern.storageValue}")
                    .padding(vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedPattern == pattern,
                    onClick = {
                        selectedPattern = pattern
                        onPreview(pattern)
                    }
                )
                Text(
                    text = stringResource(pattern.labelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalOpenCodeTheme.current.text
                )
            }
        }
    }
}

private fun VibrationPattern.labelRes(): Int = when (this) {
    VibrationPattern.None -> R.string.vibration_pattern_none
    VibrationPattern.Tick -> R.string.vibration_pattern_tick
    VibrationPattern.Click -> R.string.vibration_pattern_click
    VibrationPattern.HeavyClick -> R.string.vibration_pattern_heavy_click
    VibrationPattern.DoubleClick -> R.string.vibration_pattern_double_click
    VibrationPattern.LongPulse -> R.string.vibration_pattern_long_pulse
    VibrationPattern.DoubleLongPulse -> R.string.vibration_pattern_double_long_pulse
}

@Composable
private fun NotificationSwitch(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    testTag: String
) {
    val theme = LocalOpenCodeTheme.current
    val contentAlpha = if (enabled) 1f else 0.5f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(contentAlpha)
            .padding(horizontal = Spacing.lg, vertical = Spacing.mdLg),
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
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.text
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMuted
                )
            }
        }
        TuiSwitch(
            checked = checked,
            onCheckedChange = { onCheckedChange(!checked) },
            enabled = enabled,
            modifier = Modifier.testTag(testTag)
        )
    }
    HorizontalDivider(
        thickness = Sizing.dividerThickness,
        color = theme.borderSubtle
    )
}
