package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.blazelight.p4oc.domain.model.Command
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing

/**
 * Inline popup that appears above the chat input when user types "/"
 * Shows filtered list of available slash commands
 */
@Composable
fun SlashCommandsPopup(
    commands: List<Command>,
    filter: String,
    onCommandSelected: (Command) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    
    // Filter commands based on what user typed after "/"
    val filteredCommands = remember(commands, filter) {
        val searchTerm = filter.removePrefix("/").lowercase()
        if (searchTerm.isEmpty()) {
            commands.take(8) // Show first 8 when just "/" is typed
        } else {
            commands.filter { cmd ->
                cmd.name.lowercase().contains(searchTerm) ||
                cmd.description?.lowercase()?.contains(searchTerm) == true
            }.take(8)
        }
    }

    if (filteredCommands.isEmpty()) {
        return
    }

    Popup(
        alignment = Alignment.BottomStart,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = modifier
                .widthIn(min = 280.dp, max = 400.dp)
                .heightIn(max = 300.dp)
                .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                .border(1.dp, theme.border, RectangleShape),
            shape = RectangleShape,
            color = theme.background,
            shadowElevation = 8.dp
        ) {
            Column {
                // TUI Header
                Text(
                    text = "[ Commands ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                )
                
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = Spacing.xs)
                ) {
                    items(filteredCommands, key = { it.name }) { command ->
                        SlashCommandItem(
                            command = command,
                            onClick = { onCommandSelected(command) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlashCommandItem(
    command: Command,
    onClick: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.mdLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            text = "/",
            color = theme.accent,
            fontFamily = FontFamily.Monospace
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = command.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = theme.text
                )
                if (command.subtask) {
                    Text(
                        text = "[subtask]",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = theme.info
                    )
                }
            }
            command.description?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = theme.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
