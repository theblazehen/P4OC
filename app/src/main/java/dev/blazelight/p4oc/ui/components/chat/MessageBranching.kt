package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.components.TuiConfirmDialog
import dev.blazelight.p4oc.ui.components.TuiAlertDialog
import dev.blazelight.p4oc.ui.components.TuiButton
import dev.blazelight.p4oc.ui.components.TuiOutlinedButton
import dev.blazelight.p4oc.ui.components.TuiTextField
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing

data class MessageBranch(
    val id: String,
    val parentMessageId: String,
    val createdAt: Long,
    val title: String,
    val messageCount: Int,
    val isActive: Boolean = false
)

data class BranchPoint(
    val messageId: String,
    val messagePreview: String,
    val branches: List<MessageBranch>,
    val timestamp: Long
)

@Composable
fun MessageBranchIndicator(
    branchCount: Int,
    currentBranchIndex: Int,
    onPreviousBranch: () -> Unit,
    onNextBranch: () -> Unit,
    onShowBranches: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    if (branchCount <= 1) return
    
    Row(
        modifier = modifier
            .clickable(onClick = onShowBranches)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        IconButton(
            onClick = onPreviousBranch,
            enabled = currentBranchIndex > 0,
            modifier = Modifier.size(Sizing.iconLg)
        ) {
            Icon(
                Icons.Default.ChevronLeft,
                contentDescription = stringResource(R.string.cd_previous_branch),
                modifier = Modifier.size(Sizing.iconXs),
                tint = if (currentBranchIndex > 0) theme.text else theme.textMuted
            )
        }
        
        Text(
            text = "${currentBranchIndex + 1}/$branchCount",
            style = MaterialTheme.typography.labelSmall,
            color = theme.textMuted
        )
        
        IconButton(
            onClick = onNextBranch,
            enabled = currentBranchIndex < branchCount - 1,
            modifier = Modifier.size(Sizing.iconLg)
        ) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.cd_next_branch),
                modifier = Modifier.size(Sizing.iconXs),
                tint = if (currentBranchIndex < branchCount - 1) theme.text else theme.textMuted
            )
        }
        
        Icon(
            Icons.Default.AccountTree,
            contentDescription = stringResource(R.string.cd_show_branches),
            modifier = Modifier.size(Sizing.iconXs),
            tint = theme.accent
        )
    }
}

@Composable
fun ForkMessageButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    IconButton(
        onClick = onClick,
        modifier = modifier.size(Sizing.iconXl)
    ) {
        Icon(
            Icons.AutoMirrored.Filled.CallSplit,
            contentDescription = stringResource(R.string.cd_fork_conversation),
            modifier = Modifier.size(Sizing.iconSm),
            tint = theme.textMuted
        )
    }
}

@Composable
fun BranchSelectorDialog(
    branchPoint: BranchPoint,
    onSelectBranch: (MessageBranch) -> Unit,
    onCreateNewBranch: () -> Unit,
    onDeleteBranch: (MessageBranch) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f),
            shape = RectangleShape,
            color = theme.background,
            border = BorderStroke(1.dp, theme.border)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
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
                            text = "[ ${stringResource(R.string.conversation_branches)} ]",
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
                
                // Branch point preview
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    color = theme.backgroundElement,
                    shape = RectangleShape,
                    border = BorderStroke(1.dp, theme.borderSubtle)
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            text = "◆ branch point",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.accent
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = branchPoint.messagePreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.textMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // Branch list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    items(branchPoint.branches, key = { it.id }) { branch ->
                        TuiBranchCard(
                            branch = branch,
                            onSelect = { onSelectBranch(branch) },
                            onDelete = { onDeleteBranch(branch) }
                        )
                    }
                }
                
                HorizontalDivider(color = theme.border)
                
                // Footer with new branch button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    horizontalArrangement = Arrangement.End
                ) {
                    TuiButton(onClick = onCreateNewBranch) {
                        Text("+ ${stringResource(R.string.branch_new)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun TuiBranchCard(
    branch: MessageBranch,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        color = if (branch.isActive) theme.accent.copy(alpha = 0.1f) else Color.Transparent,
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
                text = if (branch.isActive) ">" else " ",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.accent
            )
            
            // Branch icon
            Text(
                text = if (branch.isActive) "●" else "○",
                style = MaterialTheme.typography.bodySmall,
                color = if (branch.isActive) theme.success else theme.textMuted
            )
            
            // Branch info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = branch.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (branch.isActive) theme.text else theme.text.copy(alpha = 0.9f),
                    fontWeight = if (branch.isActive) FontWeight.Bold else FontWeight.Normal
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "${branch.messageCount} msgs",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted
                    )
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted
                    )
                    Text(
                        text = formatTimestamp(branch.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted
                    )
                }
            }
            
            // Delete button (only for non-active branches)
            if (!branch.isActive) {
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(Sizing.iconButtonSm)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_delete_branch),
                        modifier = Modifier.size(Sizing.iconSm),
                        tint = theme.error
                    )
                }
            }
        }
    }
    
    if (showDeleteConfirm) {
        TuiConfirmDialog(
            onDismissRequest = { showDeleteConfirm = false },
            onConfirm = { onDelete() },
            title = stringResource(R.string.branch_delete_title),
            message = stringResource(R.string.branch_delete_confirm, branch.title),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.button_cancel),
            isDestructive = true,
            icon = Icons.Default.Warning
        )
    }
}

@Composable
fun CreateBranchDialog(
    messagePreview: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    var branchName by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    TuiAlertDialog(
        onDismissRequest = onDismiss,
        icon = Icons.AutoMirrored.Filled.CallSplit,
        title = stringResource(R.string.branch_create_title),
        confirmButton = {
            TuiButton(
                onClick = { onConfirm(branchName.ifBlank { context.getString(R.string.new_branch_default) }) },
                enabled = true
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TuiOutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    ) {
        Text(
            text = stringResource(R.string.fork_conversation_hint),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = theme.textMuted
        )
        
        Surface(
            color = theme.backgroundElement,
            shape = RectangleShape
        ) {
            Text(
                text = messagePreview,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = theme.text,
                modifier = Modifier.padding(Spacing.lg),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        TuiTextField(
            value = branchName,
            onValueChange = { branchName = it },
            label = stringResource(R.string.branch_name_label),
            placeholder = stringResource(R.string.branch_placeholder),
            singleLine = true
        )
    }
}

@Composable
fun BranchBadge(
    branchName: String,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    
    Surface(
        modifier = modifier,
        shape = RectangleShape,
        color = theme.info.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "◈",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = theme.info
            )
            Text(
                text = branchName,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = theme.info
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> {
            val instant = Instant.fromEpochMilliseconds(timestamp)
            val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            "${localDateTime.month.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }} ${localDateTime.dayOfMonth}"
        }
    }
}
