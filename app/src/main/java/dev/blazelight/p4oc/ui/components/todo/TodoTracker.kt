package dev.blazelight.p4oc.ui.components.todo

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.domain.model.Todo
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.components.TuiLoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoTrackerSheet(
    todos: List<Todo>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    val completedCount = todos.count { it.status == "completed" }
    val totalCount = todos.size
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f

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
                        text = "[ ${stringResource(R.string.todo_tracker)} ]",
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.text
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (totalCount > 0) {
                            Text(
                                text = "$completedCount/$totalCount",
                                style = MaterialTheme.typography.labelMedium,
                                color = theme.accent
                            )
                        }
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(Sizing.iconButtonSm)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.cd_refresh),
                                tint = theme.textMuted,
                                modifier = Modifier.size(Sizing.iconSm)
                            )
                        }
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
            }
            
            HorizontalDivider(color = theme.border)

            // Progress bar
            if (totalCount > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md)
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = theme.accent,
                        trackColor = theme.border
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = "${(progress * 100).toInt()}% complete",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted
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
                todos.isEmpty() -> {
                    TuiEmptyTodosView()
                }
                else -> {
                    TuiTodoList(todos = todos)
                }
            }
        }
    }
}

@Composable
private fun TuiEmptyTodosView() {
    val theme = LocalOpenCodeTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            text = "✓",
            style = MaterialTheme.typography.displayMedium,
            color = theme.success
        )
        Text(
            text = stringResource(R.string.no_active_todos),
            style = MaterialTheme.typography.titleSmall,
            color = theme.text
        )
        Text(
            text = stringResource(R.string.agent_no_tasks),
            style = MaterialTheme.typography.bodySmall,
            color = theme.textMuted
        )
    }
}

@Composable
private fun TuiTodoList(todos: List<Todo>) {
    val theme = LocalOpenCodeTheme.current
    val groupedTodos = todos.groupBy { it.status }
    val inProgress = groupedTodos["in_progress"] ?: emptyList()
    val pending = groupedTodos["pending"] ?: emptyList()
    val completed = groupedTodos["completed"] ?: emptyList()
    val cancelled = groupedTodos["cancelled"] ?: emptyList()

    LazyColumn(
        modifier = Modifier
            .heightIn(max = 400.dp)
            .padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        if (inProgress.isNotEmpty()) {
            item {
                TuiTodoSectionHeader(title = "▶ in progress", count = inProgress.size, color = theme.accent)
            }
            items(inProgress, key = { it.id }) { todo ->
                TuiTodoItem(todo = todo)
            }
        }

        if (pending.isNotEmpty()) {
            item {
                TuiTodoSectionHeader(title = "○ pending", count = pending.size, color = theme.textMuted)
            }
            items(pending, key = { it.id }) { todo ->
                TuiTodoItem(todo = todo)
            }
        }

        if (completed.isNotEmpty()) {
            item {
                TuiTodoSectionHeader(title = "✓ completed", count = completed.size, color = theme.success)
            }
            items(completed, key = { it.id }) { todo ->
                TuiTodoItem(todo = todo)
            }
        }

        if (cancelled.isNotEmpty()) {
            item {
                TuiTodoSectionHeader(title = "✗ cancelled", count = cancelled.size, color = theme.error)
            }
            items(cancelled, key = { it.id }) { todo ->
                TuiTodoItem(todo = todo)
            }
        }
    }
}

@Composable
private fun TuiTodoSectionHeader(title: String, count: Int, color: Color) {
    val theme = LocalOpenCodeTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
        Text(
            text = "[$count]",
            style = MaterialTheme.typography.labelSmall,
            color = theme.textMuted
        )
    }
}

@Composable
private fun TuiTodoItem(todo: Todo) {
    val theme = LocalOpenCodeTheme.current
    val (statusIcon, statusColor) = getStatusInfo(todo.status)
    val priorityColor = getPriorityColor(todo.priority)
    val isCompleted = todo.status == "completed"
    val isCancelled = todo.status == "cancelled"

    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        color = if (isCompleted || isCancelled)
            theme.backgroundElement.copy(alpha = 0.5f)
        else
            theme.backgroundElement,
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            // Status indicator
            Text(
                text = when (todo.status) {
                    "in_progress" -> "▶"
                    "completed" -> "✓"
                    "cancelled" -> "✗"
                    else -> "○"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todo.content,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (todo.status == "in_progress") FontWeight.Medium else FontWeight.Normal,
                    textDecoration = if (isCompleted || isCancelled) TextDecoration.LineThrough else null,
                    color = if (isCompleted || isCancelled)
                        theme.textMuted
                    else
                        theme.text,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(Spacing.xxs))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "[${todo.priority.uppercase()}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = priorityColor
                    )
                }
            }
        }
    }
}

@Composable
private fun getStatusInfo(status: String): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
    return when (status) {
        "pending" -> Icons.Default.Schedule to SemanticColors.Todo.pending
        "in_progress" -> Icons.Default.PlayCircle to SemanticColors.Todo.inProgress
        "completed" -> Icons.Default.CheckCircle to SemanticColors.Todo.completed
        "cancelled" -> Icons.Default.Cancel to SemanticColors.Todo.cancelled
        else -> Icons.Default.Circle to SemanticColors.Todo.pending
    }
}

@Composable
private fun getPriorityColor(priority: String): Color {
    return SemanticColors.Todo.forPriority(priority)
}
