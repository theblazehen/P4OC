package dev.blazelight.p4oc.ui.screens.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.blazelight.p4oc.core.datastore.RecentServer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(
    viewModel: ServerViewModel = hiltViewModel(),
    onNavigateToProjects: () -> Unit,
    onNavigateToSessions: (projectId: String?) -> Unit,
    onSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.navigationDestination) {
        when (val destination = uiState.navigationDestination) {
            is NavigationDestination.Projects -> {
                viewModel.clearNavigationDestination()
                onNavigateToProjects()
            }
            is NavigationDestination.Sessions -> {
                viewModel.clearNavigationDestination()
                onNavigateToSessions(destination.projectId)
            }
            is NavigationDestination.GlobalSessions -> {
                viewModel.clearNavigationDestination()
                onNavigateToSessions(null)
            }
            null -> { /* waiting for connection */ }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect to Server") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
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
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Connection Mode",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
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
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
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
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Local Server (Termux)",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Run OpenCode directly on your device using Termux.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TermuxStatusCard(status = termuxStatus)

            when (termuxStatus) {
                TermuxStatusUi.NotInstalled -> {
                    Text(
                        text = "Termux is required. Install it from F-Droid.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TermuxStatusUi.SetupRequired -> {
                    Button(
                        onClick = onOpenTermux,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Setup Termux")
                    }
                }
                TermuxStatusUi.OpenCodeNotInstalled -> {
                    Button(
                        onClick = onInstallOpenCode,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Install OpenCode")
                    }
                }
                TermuxStatusUi.Ready -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onStartServer,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Start")
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
                                Icon(Icons.Default.Link, contentDescription = null)
                            }
                            Spacer(Modifier.width(4.dp))
                            Text("Connect")
                        }
                    }
                }
                TermuxStatusUi.ServerRunning -> {
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
                            Text("Connecting...")
                        } else {
                            Icon(Icons.Default.Link, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Connect to Running Server")
                        }
                    }
                }
                TermuxStatusUi.Unknown, TermuxStatusUi.Checking -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
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
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
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
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Remote Server",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Connect to an OpenCode server running on your network.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text("Server URL") },
                placeholder = { Text("http://192.168.1.100:4096") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) }
            )

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) 
                    VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff 
                            else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
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
                    Text("Connecting...")
                } else {
                    Icon(Icons.Default.Link, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Connect")
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
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Recent Servers",
                style = MaterialTheme.typography.titleMedium
            )
            
            servers.forEach { server ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isConnecting) { onServerClick(server) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
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
                            contentDescription = "Remove",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
