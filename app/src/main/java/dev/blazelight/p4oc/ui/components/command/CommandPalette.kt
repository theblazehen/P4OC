package dev.blazelight.p4oc.ui.components.command

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.domain.model.Command

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPalette(
    commands: List<Command>,
    isLoading: Boolean,
    onCommandSelected: (Command, String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCommand by remember { mutableStateOf<Command?>(null) }
    var commandArgs by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val filteredCommands = remember(commands, searchQuery) {
        if (searchQuery.isBlank()) {
            commands
        } else {
            commands.filter { cmd ->
                cmd.name.contains(searchQuery, ignoreCase = true) ||
                    cmd.description?.contains(searchQuery, ignoreCase = true) == true
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            if (selectedCommand == null) {
                CommandSearchView(
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    filteredCommands = filteredCommands,
                    isLoading = isLoading,
                    onCommandClick = { selectedCommand = it },
                    focusRequester = focusRequester
                )
            } else {
                selectedCommand?.let { command ->
                    CommandArgumentsView(
                        command = command,
                        arguments = commandArgs,
                        onArgumentsChange = { commandArgs = it },
                        onBack = { selectedCommand = null; commandArgs = "" },
                        onExecute = {
                            onCommandSelected(command, commandArgs)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandSearchView(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    filteredCommands: List<Command>,
    isLoading: Boolean,
    onCommandClick: (Command) -> Unit,
    focusRequester: FocusRequester
) {
    Text(
        text = stringResource(R.string.command_palette),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 16.dp)
    )

    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.search_commands)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search)) },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                }
            }
        },
        singleLine = true,
        shape = RectangleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
    )

    Spacer(modifier = Modifier.height(16.dp))

    when {
        isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        filteredCommands.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = stringResource(R.string.no_matching_commands),
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isEmpty()) stringResource(R.string.no_commands_available) else stringResource(R.string.no_matching_commands),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredCommands, key = { it.name }) { command ->
                    CommandItem(
                        command = command,
                        onClick = { onCommandClick(command) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandItem(
    command: Command,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Terminal,
                contentDescription = stringResource(R.string.cd_command_icon),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "/${command.name}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (command.subtask) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RectangleShape
                        ) {
                            Text(
                                text = stringResource(R.string.subtask),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                command.description?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }

                if (command.agent != null || command.model != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        command.agent?.let { agent ->
                            CommandBadge(
                                icon = Icons.Default.SmartToy,
                                text = agent,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        command.model?.let { model ->
                            CommandBadge(
                                icon = Icons.Default.Memory,
                                text = model.split("/").lastOrNull() ?: model,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.cd_select),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CommandBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = stringResource(R.string.cd_decorative),
            modifier = Modifier.size(12.dp),
            tint = color
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun CommandArgumentsView(
    command: Command,
    arguments: String,
    onArgumentsChange: (String) -> Unit,
    onBack: () -> Unit,
    onExecute: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Text(
            text = "/${command.name}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary
        )
    }

    command.description?.let { desc ->
        Text(
            text = desc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }

    OutlinedTextField(
        value = arguments,
        onValueChange = onArgumentsChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.arguments_optional)) },
        placeholder = { Text(stringResource(R.string.enter_command_arguments)) },
        singleLine = false,
        minLines = 2,
        maxLines = 4,
        shape = RectangleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onExecute() })
    )

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = onExecute,
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.cd_run_command))
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.execute_command))
    }
}
