package dev.blazelight.p4oc.ui.components.todo

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.domain.model.Todo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoTrackerSheet(
    todos: List<Todo>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    val completedCount = todos.count { it.status == "completed" }
    val totalCount = todos.size
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Todo Tracker",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (totalCount > 0) {
                ProgressCard(
                    completed = completedCount,
                    total = totalCount,
                    progress = progress
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

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
                todos.isEmpty() -> {
                    EmptyTodosCard()
                }
                else -> {
                    TodoList(todos = todos)
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(
    completed: Int,
    total: Int,
    progress: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Progress",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "$completed / $total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${(progress * 100).toInt()}% complete",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun EmptyTodosCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFF4CAF50)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No active todos",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "The agent hasn't created any tasks yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TodoList(todos: List<Todo>) {
    val groupedTodos = todos.groupBy { it.status }
    val inProgress = groupedTodos["in_progress"] ?: emptyList()
    val pending = groupedTodos["pending"] ?: emptyList()
    val completed = groupedTodos["completed"] ?: emptyList()
    val cancelled = groupedTodos["cancelled"] ?: emptyList()

    LazyColumn(
        modifier = Modifier.heightIn(max = 400.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (inProgress.isNotEmpty()) {
            item {
                TodoSectionHeader(title = "In Progress", count = inProgress.size)
            }
            items(inProgress, key = { it.id }) { todo ->
                TodoItem(todo = todo)
            }
        }

        if (pending.isNotEmpty()) {
            item {
                TodoSectionHeader(title = "Pending", count = pending.size)
            }
            items(pending, key = { it.id }) { todo ->
                TodoItem(todo = todo)
            }
        }

        if (completed.isNotEmpty()) {
            item {
                TodoSectionHeader(title = "Completed", count = completed.size)
            }
            items(completed, key = { it.id }) { todo ->
                TodoItem(todo = todo)
            }
        }

        if (cancelled.isNotEmpty()) {
            item {
                TodoSectionHeader(title = "Cancelled", count = cancelled.size)
            }
            items(cancelled, key = { it.id }) { todo ->
                TodoItem(todo = todo)
            }
        }
    }
}

@Composable
private fun TodoSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun TodoItem(todo: Todo) {
    val (statusIcon, statusColor) = getStatusInfo(todo.status)
    val priorityColor = getPriorityColor(todo.priority)
    val isCompleted = todo.status == "completed"
    val isCancelled = todo.status == "cancelled"

    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted || isCancelled)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                statusIcon,
                contentDescription = todo.status,
                tint = statusColor,
                modifier = Modifier.size(20.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todo.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (todo.status == "in_progress") FontWeight.Medium else FontWeight.Normal,
                    textDecoration = if (isCompleted || isCancelled) TextDecoration.LineThrough else null,
                    color = if (isCompleted || isCancelled)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PriorityBadge(priority = todo.priority, color = priorityColor)
                    StatusBadge(status = todo.status, color = statusColor)
                }
            }
        }
    }
}

@Composable
private fun PriorityBadge(priority: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = priority.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun StatusBadge(status: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = status.replace("_", " "),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun getStatusInfo(status: String): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
    return when (status) {
        "pending" -> Icons.Default.Schedule to Color(0xFF9E9E9E)
        "in_progress" -> Icons.Default.PlayCircle to Color(0xFF2196F3)
        "completed" -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
        "cancelled" -> Icons.Default.Cancel to Color(0xFFF44336)
        else -> Icons.Default.Circle to Color(0xFF9E9E9E)
    }
}

private fun getPriorityColor(priority: String): Color {
    return when (priority.lowercase()) {
        "high" -> Color(0xFFF44336)
        "medium" -> Color(0xFFFF9800)
        "low" -> Color(0xFF4CAF50)
        else -> Color(0xFF9E9E9E)
    }
}

@Composable
fun TodoTrackerFab(
    todos: List<Todo>,
    onClick: () -> Unit
) {
    val inProgressCount = todos.count { it.status == "in_progress" }
    val pendingCount = todos.count { it.status == "pending" }
    val activeCount = inProgressCount + pendingCount

    if (activeCount > 0) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            BadgedBox(
                badge = {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Text(activeCount.toString())
                    }
                }
            ) {
                Icon(
                    Icons.Default.Checklist,
                    contentDescription = "Todos",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
