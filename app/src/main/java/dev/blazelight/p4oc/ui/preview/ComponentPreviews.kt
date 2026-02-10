package dev.blazelight.p4oc.ui.preview

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.PocketCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.components.TuiLoadingScreen

/**
 * Preview parameter providers for common data types
 */
class SessionPreviewProvider : PreviewParameterProvider<Session> {
    override val values: Sequence<Session> = sequenceOf(
        Session(
            id = "session-1",
            title = "Fix authentication bug",
            projectID = "project-1",
            directory = "/home/user/project",
            version = "1.0.0",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        ),
        Session(
            id = "session-2",
            title = "Add dark mode support",
            projectID = "project-2",
            directory = "/home/user/project2",
            version = "1.0.0",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    )
}

/**
 * Preview for empty state
 */
@Preview(name = "Empty State", showBackground = true)
@Composable
private fun EmptyStatePreview() {
    PocketCodeTheme {
        val theme = LocalOpenCodeTheme.current
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xl)
            ) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(Sizing.iconHero),
                    tint = theme.textMuted
                )
                Text(
                    text = "No sessions yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.textMuted
                )
                Text(
                    text = "Start a new conversation to begin",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textMuted
                )
            }
        }
    }
}

/**
 * Preview for session card
 */
@Preview(name = "Session Card", showBackground = true)
@Composable
private fun SessionCardPreview(
    @PreviewParameter(SessionPreviewProvider::class) session: Session
) {
    PocketCodeTheme {
        val theme = LocalOpenCodeTheme.current
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            Column(
                modifier = Modifier.padding(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = session.directory,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMuted
                )
            }
        }
    }
}

/**
 * Preview for loading state
 */
@Preview(name = "Loading State", showBackground = true)
@Composable
private fun LoadingStatePreview() {
    PocketCodeTheme {
        TuiLoadingScreen()
    }
}

/**
 * Preview for error state
 */
@Preview(name = "Error State", showBackground = true)
@Composable
private fun ErrorStatePreview() {
    PocketCodeTheme {
        val theme = LocalOpenCodeTheme.current
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xl)
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(Sizing.iconHero),
                    tint = theme.error
                )
                Text(
                    text = "Something went wrong",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.error
                )
                Button(onClick = {}) {
                    Text("Retry")
                }
            }
        }
    }
}

/**
 * Preview for settings item
 */
@Preview(name = "Settings Item", showBackground = true)
@Composable
private fun SettingsItemPreview() {
    PocketCodeTheme {
        val theme = LocalOpenCodeTheme.current
        Surface {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = null,
                    tint = theme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Visual Settings",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Customize theme and appearance",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textMuted
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = theme.textMuted
                )
            }
        }
    }
}

/**
 * Preview for file item
 */
@Preview(name = "File Item", showBackground = true)
@Composable
private fun FileItemPreview() {
    PocketCodeTheme {
        val theme = LocalOpenCodeTheme.current
        Surface {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Code,
                    contentDescription = null,
                    tint = theme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MainActivity.kt",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "2.4 KB • Modified",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textMuted
                    )
                }
            }
        }
    }
}

/**
 * Preview for chat input bar
 */
@Preview(name = "Chat Input Bar", showBackground = true)
@Composable
private fun ChatInputBarPreview() {
    PocketCodeTheme {
        Surface(tonalElevation = 3.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                IconButton(onClick = {}) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach")
                }
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    maxLines = 5
                )
                FilledIconButton(onClick = {}) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

/**
 * Preview for git status badges
 */
@Preview(name = "Git Status Badges", showBackground = true)
@Composable
private fun GitStatusBadgesPreview() {
    PocketCodeTheme {
        val theme = LocalOpenCodeTheme.current
        Row(
            modifier = Modifier.padding(Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            listOf(
                "A" to theme.primary,
                "M" to theme.accent,
                "D" to theme.error
            ).forEach { (label, color) ->
                Surface(
                    color = color.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                    )
                }
            }
        }
    }
}

/**
 * Preview for connection status indicator
 */
@Preview(name = "Connection Status", showBackground = true)
@Composable
private fun ConnectionStatusPreview() {
    PocketCodeTheme {
        val theme = LocalOpenCodeTheme.current
        Column(
            modifier = Modifier.padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Connected
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = theme.primary,
                    modifier = Modifier.size(Sizing.iconSm)
                )
                Text("Connected", style = MaterialTheme.typography.bodySmall)
            }
            // Disconnected
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = theme.error,
                    modifier = Modifier.size(Sizing.iconSm)
                )
                Text("Disconnected", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Preview for todo item
 */
@Preview(name = "Todo Item", showBackground = true)
@Composable
private fun TodoItemPreview() {
    PocketCodeTheme {
        val theme = LocalOpenCodeTheme.current
        Surface {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = false, onCheckedChange = {})
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Implement file picker",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "In Progress",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.primary
                    )
                }
            }
        }
    }
}

/**
 * Preview for project chip
 */
@Preview(name = "Project Chip", showBackground = true)
@Composable
private fun ProjectChipPreview() {
    PocketCodeTheme {
        val theme = LocalOpenCodeTheme.current
        Row(
            modifier = Modifier.padding(Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Surface(
                color = theme.backgroundPanel,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "opencode_android",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                )
            }
            Surface(
                color = theme.backgroundPanel,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "pocket-code",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                )
            }
        }
    }
}
