package dev.blazelight.p4oc.ui.components.command

import androidx.compose.foundation.BorderStroke
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
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.components.TuiLoadingIndicator
import dev.blazelight.p4oc.ui.components.TuiButton
import dev.blazelight.p4oc.ui.components.TuiTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPalette(
    commands: List<Command>,
    isLoading: Boolean,
    onCommandSelected: (Command, String) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
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
        containerColor = theme.background,
        shape = RectangleShape,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xl)
        ) {
            // TUI Header
            Surface(
                color = theme.backgroundElement,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "[ ${stringResource(R.string.command_palette)} ]",
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.text
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(Sizing.iconButtonSm)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = theme.textMuted,
                            modifier = Modifier.size(Sizing.iconSm)
                        )
                    }
                }
            }
            
            HorizontalDivider(color = theme.border)

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
    val theme = LocalOpenCodeTheme.current
    
    // Search field with / prefix (vim-style)
    Surface(
        color = theme.backgroundElement,
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "/",
                style = MaterialTheme.typography.bodyLarge,
                color = theme.accent,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = Spacing.md)
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { 
                    Text(
                        stringResource(R.string.search_commands),
                        color = theme.textMuted
                    ) 
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(R.string.clear),
                                tint = theme.textMuted
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RectangleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.accent,
                    unfocusedBorderColor = theme.border,
                    cursorColor = theme.accent,
                    focusedTextColor = theme.text,
                    unfocusedTextColor = theme.text
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
        }
    }

    when {
        isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                TuiLoadingIndicator()
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
                    Text(
                        text = "∅",
                        style = MaterialTheme.typography.displayMedium,
                        color = theme.textMuted
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Text(
                        text = if (searchQuery.isEmpty()) 
                            "-- ${stringResource(R.string.no_commands_available)} --" 
                        else 
                            "-- ${stringResource(R.string.no_matching_commands)} --",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textMuted
                    )
                }
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                items(filteredCommands, key = { it.name }) { command ->
                    TuiCommandItem(
                        command = command,
                        onClick = { onCommandClick(command) }
                    )
                }
            }
            
            // Footer with command count
            Surface(
                color = theme.backgroundElement,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${filteredCommands.size} commands",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                )
            }
        }
    }
}

@Composable
private fun TuiCommandItem(
    command: Command,
    onClick: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection indicator
            Text(
                text = ">",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.accent.copy(alpha = 0.5f)
            )
            
            // Command icon
            Icon(
                Icons.Default.Terminal,
                contentDescription = stringResource(R.string.cd_command_icon),
                tint = theme.accent,
                modifier = Modifier.size(Sizing.iconSm)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "/${command.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = theme.accent
                    )
                    if (command.subtask) {
                        Text(
                            text = "[${stringResource(R.string.subtask)}]",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.warning
                        )
                    }
                }

                command.description?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textMuted,
                        maxLines = 2
                    )
                }

                if (command.agent != null || command.model != null) {
                    Spacer(modifier = Modifier.height(Spacing.xxs))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        command.agent?.let { agent ->
                            Text(
                                text = "@$agent",
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.info
                            )
                        }
                        command.model?.let { model ->
                            Text(
                                text = "[${model.split("/").lastOrNull() ?: model}]",
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.textMuted
                            )
                        }
                    }
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.cd_select),
                tint = theme.textMuted,
                modifier = Modifier.size(Sizing.iconSm)
            )
        }
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
    val theme = LocalOpenCodeTheme.current
    
    Column(
        modifier = Modifier.padding(Spacing.md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = Spacing.md)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(Sizing.iconButtonSm)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = theme.textMuted,
                    modifier = Modifier.size(Sizing.iconSm)
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = "/${command.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = theme.accent
            )
        }

        command.description?.let { desc ->
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textMuted,
                modifier = Modifier.padding(bottom = Spacing.md)
            )
        }

        TuiTextField(
            value = arguments,
            onValueChange = onArgumentsChange,
            label = stringResource(R.string.arguments_optional),
            placeholder = stringResource(R.string.enter_command_arguments),
            singleLine = false,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        TuiButton(
            onClick = onExecute,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("▶ ${stringResource(R.string.execute_command)}")
        }
    }
}
