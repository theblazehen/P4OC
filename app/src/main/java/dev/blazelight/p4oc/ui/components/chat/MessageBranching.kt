package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    if (branchCount <= 1) return
    
    Row(
        modifier = modifier
            .clickable(onClick = onShowBranches)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = onPreviousBranch,
            enabled = currentBranchIndex > 0,
            modifier = Modifier.size(Sizing.iconLg)
        ) {
            Icon(
                Icons.Default.ChevronLeft,
                contentDescription = stringResource(R.string.cd_previous_branch),
                modifier = Modifier.size(Sizing.iconXs)
            )
        }
        
        Text(
            text = "${currentBranchIndex + 1}/$branchCount",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        IconButton(
            onClick = onNextBranch,
            enabled = currentBranchIndex < branchCount - 1,
            modifier = Modifier.size(Sizing.iconLg)
        ) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.cd_next_branch),
                modifier = Modifier.size(Sizing.iconXs)
            )
        }
        
        Icon(
            Icons.Default.AccountTree,
            contentDescription = stringResource(R.string.cd_show_branches),
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun ForkMessageButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(Sizing.iconXl)
    ) {
        Icon(
            Icons.AutoMirrored.Filled.CallSplit,
            contentDescription = stringResource(R.string.cd_fork_conversation),
            modifier = Modifier.size(Sizing.iconSm),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.xl),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.conversation_branches),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(R.string.select_branch_to_continue),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
                
                HorizontalDivider()
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.xl),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(Spacing.lg)) {
                        Text(
                            text = stringResource(R.string.branch_point),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = branchPoint.messagePreview,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(branchPoint.branches, key = { it.id }) { branch ->
                        BranchCard(
                            branch = branch,
                            onSelect = { onSelectBranch(branch) },
                            onDelete = { onDeleteBranch(branch) }
                        )
                    }
                }
                
                HorizontalDivider()
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.xl),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onCreateNewBranch) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.branch_new),
                            modifier = Modifier.size(Sizing.iconSm)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.branch_new))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchCard(
    branch: MessageBranch,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (branch.isActive) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        ),
        border = if (branch.isActive) CardDefaults.outlinedCardBorder() else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    if (branch.isActive) Icons.Default.CheckCircle else Icons.Default.AccountTree,
                    contentDescription = stringResource(R.string.cd_branch_icon),
                    tint = if (branch.isActive) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Column {
                    Text(
                        text = branch.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (branch.isActive) FontWeight.Bold else FontWeight.Normal
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.branch_messages_count, branch.messageCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatTimestamp(branch.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            if (!branch.isActive) {
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(Sizing.iconXl)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_delete_branch),
                        modifier = Modifier.size(Sizing.iconSm),
                        tint = MaterialTheme.colorScheme.error
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = messagePreview,
                style = MaterialTheme.typography.bodySmall,
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
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AccountTree,
                contentDescription = stringResource(R.string.cd_branch_icon),
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = branchName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
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
