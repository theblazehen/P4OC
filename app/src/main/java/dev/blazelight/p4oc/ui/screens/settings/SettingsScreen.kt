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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

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
    val uiState by viewModel.uiState.collectAsState()
    var showDisconnectDialog by remember { mutableStateOf(false) }

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
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = "Settings",
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
                headlineContent = { Text("Server") },
                supportingContent = { Text(uiState.serverUrl) },
                leadingContent = {
                    Icon(
                        if (uiState.isLocal) Icons.Default.PhoneAndroid else Icons.Default.Cloud,
                        contentDescription = null
                    )
                }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Provider & Model") },
                supportingContent = { Text("Configure AI providers and models") },
                leadingContent = { Icon(Icons.Default.SmartToy, contentDescription = null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { onProviderConfig() }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Agents") },
                supportingContent = { Text("Configure AI agents") },
                leadingContent = { Icon(Icons.Default.Groups, contentDescription = null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { onAgentsConfig() }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Skills") },
                supportingContent = { Text("Manage MCP servers and skills") },
                leadingContent = { Icon(Icons.Default.Extension, contentDescription = null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { onSkills() }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Visual Settings") },
                supportingContent = { Text("Theme, font size, and display options") },
                leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { onVisualSettings() }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("About") },
                supportingContent = { Text("Pocket Code v0.1.0") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
            )

            HorizontalDivider()

            Spacer(Modifier.weight(1f))

            ListItem(
                headlineContent = {
                    Text("Disconnect", color = MaterialTheme.colorScheme.error)
                },
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.clickable { showDisconnectDialog = true }
            )
        }
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect") },
            text = { Text("Are you sure you want to disconnect from the server?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disconnect()
                        showDisconnectDialog = false
                        onDisconnect()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


