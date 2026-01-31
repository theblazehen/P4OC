package dev.blazelight.p4oc.ui.screens.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.datastore.RecentServer
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(
    viewModel: ServerViewModel = hiltViewModel(),
    onNavigateToSessions: () -> Unit,
    onNavigateToProjects: () -> Unit,
    onSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.navigationDestination) {
        when (uiState.navigationDestination) {
            is NavigationDestination.Sessions -> {
                viewModel.clearNavigationDestination()
                onNavigateToSessions()
            }
            is NavigationDestination.Projects -> {
                viewModel.clearNavigationDestination()
                onNavigateToProjects()
            }
            null -> { /* waiting for connection */ }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.server_connect_title)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConnectionModeSelector(
                selectedMode = uiState.connectionMode,
                onModeSelected = viewModel::setConnectionMode
            )

            if (uiState.recentServers.isNotEmpty()) {
                RecentServersSection(
                    servers = uiState.recentServers,
                    isConnecting = uiState.isConnecting,
                    onServerClick = viewModel::connectToRecentServer,
                    onRemoveServer = viewModel::removeRecentServer
                )
            }

            when (uiState.connectionMode) {
                ConnectionMode.LOCAL -> LocalServerSection(
                    termuxStatus = uiState.termuxStatus,
                    isConnecting = uiState.isConnecting,
                    onStartServer = viewModel::startLocalServer,
                    onInstallOpenCode = viewModel::installOpenCode,
                    onOpenTermux = viewModel::openTermux,
                    onConnect = viewModel::connectToLocal
                )
                ConnectionMode.REMOTE -> RemoteServerSection(
                    url = uiState.remoteUrl,
                    username = uiState.username,
                    password = uiState.password,
                    isConnecting = uiState.isConnecting,
                    onUrlChange = viewModel::setRemoteUrl,
                    onUsernameChange = viewModel::setUsername,
                    onPasswordChange = viewModel::setPassword,
                    onConnect = viewModel::connectToRemote
                )
            }

            uiState.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = stringResource(R.string.cd_error_icon),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionModeSelector(
    selectedMode: ConnectionMode,
    onModeSelected: (ConnectionMode) -> Unit
) {
    Card {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = stringResource(R.string.server_connection_mode),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConnectionMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { onModeSelected(mode) },
                        label = { Text(mode.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (mode == ConnectionMode.LOCAL)
                                    Icons.Default.PhoneAndroid else Icons.Default.Cloud,
                                contentDescription = if (mode == ConnectionMode.LOCAL)
                                    stringResource(R.string.cd_local_mode) else stringResource(R.string.cd_remote_mode),
                                modifier = Modifier.size(Sizing.iconSm)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalServerSection(
    termuxStatus: TermuxStatusUi,
    isConnecting: Boolean,
    onStartServer: () -> Unit,
    onInstallOpenCode: () -> Unit,
    onOpenTermux: () -> Unit,
    onConnect: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.server_local_title),
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = stringResource(R.string.server_local_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Setup progress indicator
            SetupProgressIndicator(status = termuxStatus)
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            when (termuxStatus) {
                TermuxStatusUi.NotInstalled -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.termux_step1_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.termux_step1_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { /* Open F-Droid link - handled by system */ },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.cd_open_fdroid), modifier = Modifier.size(Sizing.iconSm))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.termux_get_from_fdroid))
                        }
                    }
                }
                TermuxStatusUi.SetupRequired -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.termux_step2_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.termux_step2_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onOpenTermux,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Build, contentDescription = stringResource(R.string.cd_setup))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.termux_run_setup))
                        }
                        Text(
                            text = stringResource(R.string.termux_step2_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                TermuxStatusUi.OpenCodeNotInstalled -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.termux_step3_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.termux_step3_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onInstallOpenCode,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.cd_install))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.termux_install_opencode))
                        }
                    }
                }
                TermuxStatusUi.Ready -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.termux_ready_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.termux_ready_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onStartServer,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.cd_start_server))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.button_start_server))
                            }
                            Button(
                                onClick = onConnect,
                                enabled = !isConnecting,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isConnecting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Link, contentDescription = stringResource(R.string.cd_connect))
                                }
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.button_connect))
                            }
                        }
                    }
                }
                TermuxStatusUi.ServerRunning -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.cd_server_running),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(Sizing.iconMd)
                            )
                            Text(
                                text = stringResource(R.string.termux_server_running),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Button(
                            onClick = onConnect,
                            enabled = !isConnecting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.button_connecting))
                            } else {
                                Icon(Icons.Default.Link, contentDescription = stringResource(R.string.cd_connect))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.button_connect_now))
                            }
                        }
                    }
                }
                TermuxStatusUi.Unknown, TermuxStatusUi.Checking -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(Sizing.iconLg))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.termux_checking),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupProgressIndicator(status: TermuxStatusUi) {
    val steps = listOf(
        stringResource(R.string.termux_progress_install),
        stringResource(R.string.termux_progress_access),
        stringResource(R.string.termux_progress_opencode),
        stringResource(R.string.termux_progress_ready)
    )
    val currentStep = when (status) {
        TermuxStatusUi.NotInstalled -> 0
        TermuxStatusUi.SetupRequired -> 1
        TermuxStatusUi.OpenCodeNotInstalled -> 2
        TermuxStatusUi.Ready, TermuxStatusUi.ServerRunning -> 3
        else -> -1
    }
    
    if (currentStep < 0) return
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, step ->
            val isCompleted = index < currentStep
            val isCurrent = index == currentStep
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(28.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = when {
                            isCompleted -> MaterialTheme.colorScheme.primary
                            isCurrent -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(28.dp)
                        ) {
                            if (isCompleted) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = stringResource(R.string.cd_completed),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
                Text(
                    text = step,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isCompleted || isCurrent -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.outline
                    },
                    maxLines = 1
                )
            }
            
            if (index < steps.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier
                        .weight(0.5f)
                        .padding(horizontal = 4.dp),
                    color = if (index < currentStep) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

@Composable
private fun TermuxStatusCard(status: TermuxStatusUi) {
    val (icon, text, color) = when (status) {
        TermuxStatusUi.Unknown, TermuxStatusUi.Checking ->
            Triple(Icons.Default.HourglassEmpty, "Checking...", MaterialTheme.colorScheme.outline)
        TermuxStatusUi.NotInstalled ->
            Triple(Icons.Default.Error, "Termux not installed", MaterialTheme.colorScheme.error)
        TermuxStatusUi.SetupRequired ->
            Triple(Icons.Default.Warning, "Setup required", MaterialTheme.colorScheme.tertiary)
        TermuxStatusUi.OpenCodeNotInstalled ->
            Triple(Icons.Default.Warning, "OpenCode not installed", MaterialTheme.colorScheme.tertiary)
        TermuxStatusUi.Ready ->
            Triple(Icons.Default.CheckCircle, "Ready", MaterialTheme.colorScheme.primary)
        TermuxStatusUi.ServerRunning ->
            Triple(Icons.Default.CheckCircle, "Server running", MaterialTheme.colorScheme.primary)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = stringResource(R.string.cd_connection_status), tint = color, modifier = Modifier.size(Sizing.iconMd))
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RemoteServerSection(
    url: String,
    username: String,
    password: String,
    isConnecting: Boolean,
    onUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Card {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.server_remote_title),
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = stringResource(R.string.server_remote_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text(stringResource(R.string.field_server_url)) },
                placeholder = { Text(stringResource(R.string.field_server_url_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = stringResource(R.string.cd_server_url)) }
            )

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text(stringResource(R.string.field_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = stringResource(R.string.cd_username)) }
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.field_password)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) 
                    VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.cd_password)) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff 
                            else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) 
                                stringResource(R.string.field_hide_password) 
                            else 
                                stringResource(R.string.field_show_password)
                        )
                    }
                }
            )

            Button(
                onClick = onConnect,
                enabled = url.isNotBlank() && !isConnecting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.button_connecting))
                } else {
                    Icon(Icons.Default.Link, contentDescription = stringResource(R.string.cd_connect))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.button_connect))
                }
            }
        }
    }
}

enum class ConnectionMode(val label: String) {
    LOCAL("Local"),
    REMOTE("Remote")
}

enum class TermuxStatusUi {
    Unknown,
    Checking,
    NotInstalled,
    SetupRequired,
    OpenCodeNotInstalled,
    Ready,
    ServerRunning
}

@Composable
private fun RecentServersSection(
    servers: List<RecentServer>,
    isConnecting: Boolean,
    onServerClick: (RecentServer) -> Unit,
    onRemoveServer: (RecentServer) -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.server_recent_servers),
                style = MaterialTheme.typography.titleMedium
            )
            
            servers.forEach { server ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isConnecting) { onServerClick(server) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = stringResource(R.string.cd_recent_server),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Sizing.iconMd)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = server.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = server.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { onRemoveServer(server) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_remove),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
