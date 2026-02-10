package dev.blazelight.p4oc.ui.screens.server

import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.datastore.RecentServer
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.components.TuiLoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(
    viewModel: ServerViewModel = koinViewModel(),
    onNavigateToSessions: () -> Unit,
    onNavigateToProjects: () -> Unit,
    onSettings: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
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
                title = { 
                    Text(
                        "[ ${stringResource(R.string.server_connect_title)} ]",
                        fontFamily = FontFamily.Monospace,
                        color = theme.text
                    ) 
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Text(
                            text = "⚙",
                            color = theme.textMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.backgroundElement
                )
            )
        },
        containerColor = theme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (uiState.recentServers.isNotEmpty()) {
                RecentServersSection(
                    servers = uiState.recentServers,
                    isConnecting = uiState.isConnecting,
                    onServerClick = viewModel::connectToRecentServer,
                    onRemoveServer = viewModel::removeRecentServer
                )
            }

            RemoteServerSection(
                url = uiState.remoteUrl,
                username = uiState.username,
                password = uiState.password,
                isConnecting = uiState.isConnecting,
                onUrlChange = viewModel::setRemoteUrl,
                onUsernameChange = viewModel::setUsername,
                onPasswordChange = viewModel::setPassword,
                onConnect = viewModel::connectToRemote
            )

            uiState.error?.let { error ->
                Surface(
                    color = theme.error.copy(alpha = 0.1f),
                    shape = RectangleShape,
                    modifier = Modifier.border(1.dp, theme.error.copy(alpha = 0.3f), RectangleShape)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✗",
                            color = theme.error,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = error,
                            color = theme.error,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
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
    val theme = LocalOpenCodeTheme.current
    var passwordVisible by remember { mutableStateOf(false) }

    Surface(
        color = theme.backgroundElement,
        shape = RectangleShape
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = "[ ${stringResource(R.string.server_remote_title)} ]",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                color = theme.text
            )

            Text(
                text = stringResource(R.string.server_remote_description),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = theme.textMuted
            )

            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text(stringResource(R.string.field_server_url), fontFamily = FontFamily.Monospace) },
                placeholder = { Text(stringResource(R.string.field_server_url_placeholder), fontFamily = FontFamily.Monospace) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.accent,
                    unfocusedBorderColor = theme.border
                )
            )

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text(stringResource(R.string.field_username), fontFamily = FontFamily.Monospace) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.accent,
                    unfocusedBorderColor = theme.border
                )
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.field_password), fontFamily = FontFamily.Monospace) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) 
                    VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RectangleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.accent,
                    unfocusedBorderColor = theme.border
                ),
                trailingIcon = {
                    Text(
                        text = if (passwordVisible) "◉" else "○",
                        color = theme.textMuted,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { passwordVisible = !passwordVisible }
                    )
                }
            )

            Button(
                onClick = onConnect,
                enabled = url.isNotBlank() && !isConnecting,
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.accent,
                    contentColor = theme.background
                )
            ) {
                if (isConnecting) {
                    TuiLoadingIndicator()
                    Spacer(Modifier.width(Spacing.md))
                    Text(stringResource(R.string.button_connecting), fontFamily = FontFamily.Monospace)
                } else {
                    Text("→ ${stringResource(R.string.button_connect)}", fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun RecentServersSection(
    servers: List<RecentServer>,
    isConnecting: Boolean,
    onServerClick: (RecentServer) -> Unit,
    onRemoveServer: (RecentServer) -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    
    Surface(
        color = theme.backgroundElement,
        shape = RectangleShape
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = "[ ${stringResource(R.string.server_recent_servers)} ]",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                color = theme.text
            )
            
            servers.forEach { server ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isConnecting) { onServerClick(server) }
                        .padding(vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    Text(
                        text = "◇",
                        color = theme.textMuted,
                        fontFamily = FontFamily.Monospace
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = server.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = theme.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = server.url,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = theme.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "×",
                        color = theme.textMuted,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { onRemoveServer(server) }
                    )
                }
            }
        }
    }
}
