package dev.blazelight.p4oc.ui.screens.settings

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.datastore.NotificationSettings
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
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
    private val settingsDataStore: SettingsDataStore
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

    fun toggleEnabled() {
        val new = _settings.value.copy(enabled = !_settings.value.enabled)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    viewModel: NotificationSettingsViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val theme = LocalOpenCodeTheme.current

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
            // General section
            SectionHeader(title = stringResource(R.string.notification_general))

            NotificationSwitch(
                title = stringResource(R.string.notification_enable),
                subtitle = stringResource(R.string.notification_enable_desc),
                icon = Icons.Default.Notifications,
                checked = settings.enabled,
                onCheckedChange = { viewModel.toggleEnabled() },
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(testTag)
        )
    }
    HorizontalDivider(
        thickness = Sizing.dividerThickness,
        color = theme.borderSubtle
    )
}
