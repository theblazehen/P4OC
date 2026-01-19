package com.pocketcode.ui.screens.settings

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
    onModelControls: () -> Unit = {},
    onAgentsConfig: () -> Unit = {},
    onSkills: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDisconnectDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
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
                headlineContent = { Text("Model Controls") },
                supportingContent = { Text("Manage models and favorites") },
                leadingContent = { Icon(Icons.Default.Tune, contentDescription = null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { onModelControls() }
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
                supportingContent = { Text("Font size, spacing, and display") },
                leadingContent = { Icon(Icons.Default.TextFields, contentDescription = null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { onVisualSettings() }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = { Text(uiState.themeMode.replaceFirstChar { it.uppercase() }) },
                leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                trailingContent = {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.ExpandMore, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            listOf("system", "light", "dark").forEach { theme ->
                                DropdownMenuItem(
                                    text = { Text(theme.replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        viewModel.setThemeMode(theme)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
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


