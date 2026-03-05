package dev.blazelight.p4oc.ui.screens.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.components.TuiTopBar
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val connectionSettings by viewModel.connectionSettings.collectAsStateWithLifecycle()
    val theme = LocalOpenCodeTheme.current

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TuiTopBar(
                title = stringResource(R.string.settings_connection),
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
            SectionHeader(title = stringResource(R.string.connection_section_general))

            ConnectionSwitch(
                title = stringResource(R.string.settings_auto_reconnect),
                subtitle = stringResource(R.string.settings_auto_reconnect_desc),
                icon = Icons.Default.Sync,
                checked = connectionSettings.autoReconnect,
                onCheckedChange = { viewModel.toggleAutoReconnect() },
                enabled = true,
                testTag = "settings_auto_reconnect"
            )

            if (connectionSettings.autoReconnect) {
                ConnectionSlider(
                    title = stringResource(R.string.settings_reconnect_timeout),
                    icon = Icons.Default.Timer,
                    value = connectionSettings.reconnectTimeoutSeconds,
                    valueRange = 15..120,
                    steps = 20,
                    valueLabel = "${connectionSettings.reconnectTimeoutSeconds}s",
                    onValueChange = { viewModel.updateReconnectTimeout(it) },
                    testTag = "settings_reconnect_timeout_slider"
                )
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
private fun ConnectionSwitch(
    title: String,
    subtitle: String,
    icon: ImageVector,
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
            .clickable(role = Role.Button, enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = Spacing.lg, vertical = Spacing.mdLg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(Sizing.iconMd),
            tint = theme.textMuted
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

@Composable
private fun ConnectionSlider(
    title: String,
    icon: ImageVector,
    value: Int,
    valueRange: IntRange,
    steps: Int,
    valueLabel: String,
    onValueChange: (Int) -> Unit,
    testTag: String
) {
    val theme = LocalOpenCodeTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(Sizing.iconMd),
            tint = theme.textMuted
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.text
                )
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.accent
                )
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = theme.accent,
                    activeTrackColor = theme.accent,
                    inactiveTrackColor = theme.borderSubtle,
                    activeTickColor = theme.accent,
                    inactiveTickColor = theme.textMuted
                ),
                modifier = Modifier.testTag(testTag)
            )
        }
    }
    HorizontalDivider(
        thickness = Sizing.dividerThickness,
        color = theme.borderSubtle
    )
}
