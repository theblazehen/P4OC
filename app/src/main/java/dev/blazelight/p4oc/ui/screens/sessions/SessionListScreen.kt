package dev.blazelight.p4oc.ui.screens.sessions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.model.SessionStatus
import dev.blazelight.p4oc.ui.theme.ProjectColors
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private data class SessionNode(
    val sessionWithProject: SessionWithProject,
    val children: List<SessionNode>
) {
    val totalDescendants: Int
        get() = children.size + children.sumOf { it.totalDescendants }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel = hiltViewModel(),
    filterProjectId: String? = null,
    onSessionClick: (sessionId: String, directory: String?) -> Unit,
    onNewSession: (sessionId: String, directory: String?) -> Unit,
    onSettings: () -> Unit,
    onProjects: () -> Unit = {},
    onProjectClick: (projectId: String) -> Unit = {},
    onNavigateBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    var showNewSessionDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Session?>(null) }
    
    val displayedSessions = remember(uiState.sessions, filterProjectId) {
        if (filterProjectId != null) {
            uiState.sessions.filter { it.projectId == filterProjectId }
        } else {
            uiState.sessions
        }
    }
    
    val projectName = remember(uiState.projects, filterProjectId) {
        if (filterProjectId != null) {
            uiState.projects.find { it.id == filterProjectId }?.name
        } else null
    }

    LaunchedEffect(uiState.newSessionId, uiState.newSessionDirectory) {
        uiState.newSessionId?.let { sessionId ->
            onNewSession(sessionId, uiState.newSessionDirectory)
            viewModel.clearNewSession()
        }
    }

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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onNavigateBack != null) {
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
                    }
                    
                    Text(
                        text = projectName ?: "Sessions",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Always show folder icon for project navigation
                    IconButton(
                        onClick = onProjects,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = "Projects", modifier = Modifier.size(22.dp))
                    }
                    IconButton(
                        onClick = viewModel::refresh,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(22.dp))
                    }
                    IconButton(
                        onClick = onSettings,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(22.dp))
                    }
                }
            }
        },
        floatingActionButton = {
            SmallFloatingActionButton(
                onClick = { showNewSessionDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Session", modifier = Modifier.size(22.dp))
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading && displayedSessions.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                displayedSessions.isEmpty() -> {
                    EmptySessionsView(
                        modifier = Modifier.align(Alignment.Center),
                        onCreateSession = { showNewSessionDialog = true }
                    )
                }
                else -> {
                    val expandedSessions = remember { mutableStateMapOf<String, Boolean>() }
                    
                    val sessionTree = remember(displayedSessions) {
                        buildSessionTree(displayedSessions)
                    }
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = sessionTree,
                            key = { it.sessionWithProject.session.id }
                        ) { node ->
                            SessionTreeNode(
                                node = node,
                                depth = 0,
                                expandedSessions = expandedSessions,
                                sessionStatuses = uiState.sessionStatuses,
                                showProjectChip = filterProjectId == null,
                                onSessionClick = { session -> onSessionClick(session.id, session.directory) },
                                onDeleteSession = { showDeleteDialog = it },
                                onProjectClick = onProjectClick,
                                onToggleExpand = { id ->
                                    expandedSessions[id] = !(expandedSessions[id] ?: false)
                                }
                            )
                        }
                    }
                }
            }

            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = viewModel::clearError) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }

    if (showNewSessionDialog) {
        NewSessionDialog(
            projects = uiState.projects,
            defaultProjectId = filterProjectId,
            onDismiss = { showNewSessionDialog = false },
            onCreate = { title, directory ->
                viewModel.createSession(title, directory)
                showNewSessionDialog = false
            }
        )
    }

    showDeleteDialog?.let { session ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Session") },
            text = { Text("Are you sure you want to delete \"${session.title}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(session.id)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun buildSessionTree(sessions: List<SessionWithProject>): List<SessionNode> {
    val childrenByParent = sessions
        .filter { it.session.parentID != null }
        .groupBy { it.session.parentID!! }
    
    fun buildNode(sessionWithProject: SessionWithProject): SessionNode {
        val children = childrenByParent[sessionWithProject.session.id]?.map { buildNode(it) } ?: emptyList()
        return SessionNode(sessionWithProject, children)
    }
    
    return sessions
        .filter { it.session.parentID == null }
        .map { buildNode(it) }
}

@Composable
private fun SessionTreeNode(
    node: SessionNode,
    depth: Int,
    expandedSessions: MutableMap<String, Boolean>,
    sessionStatuses: Map<String, SessionStatus>,
    showProjectChip: Boolean,
    onSessionClick: (Session) -> Unit,
    onDeleteSession: (Session) -> Unit,
    onProjectClick: (String) -> Unit,
    onToggleExpand: (String) -> Unit
) {
    val swp = node.sessionWithProject
    val session = swp.session
    val isExpanded = expandedSessions[session.id] ?: false
    val hasChildren = node.children.isNotEmpty()
    val indentPadding: Dp = (depth * 24).dp
    
    Column(modifier = Modifier.padding(start = indentPadding)) {
        SessionCard(
            session = session,
            projectId = swp.projectId,
            projectName = swp.projectName,
            showProjectChip = showProjectChip,
            status = sessionStatuses[session.id],
            onClick = { onSessionClick(session) },
            onDelete = { onDeleteSession(session) },
            onProjectClick = onProjectClick,
            childCount = node.totalDescendants,
            isExpanded = isExpanded,
            onExpandToggle = if (hasChildren) { { onToggleExpand(session.id) } } else null,
            isSubAgent = depth > 0
        )
        
        AnimatedVisibility(
            visible = isExpanded && hasChildren,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                node.children.forEach { child ->
                    SessionTreeNode(
                        node = child,
                        depth = depth + 1,
                        expandedSessions = expandedSessions,
                        sessionStatuses = sessionStatuses,
                        showProjectChip = showProjectChip,
                        onSessionClick = onSessionClick,
                        onDeleteSession = onDeleteSession,
                        onProjectClick = onProjectClick,
                        onToggleExpand = onToggleExpand
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionCard(
    session: Session,
    projectId: String?,
    projectName: String?,
    showProjectChip: Boolean,
    status: SessionStatus?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onProjectClick: (String) -> Unit,
    childCount: Int = 0,
    isExpanded: Boolean = false,
    onExpandToggle: (() -> Unit)? = null,
    isSubAgent: Boolean = false
) {
    val isBusy = status is SessionStatus.Busy
    val isRetrying = status is SessionStatus.Retry
    var showContextMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showContextMenu = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isBusy -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                isRetrying -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                isSubAgent -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expand/collapse or subagent indicator
            if (onExpandToggle != null) {
                IconButton(
                    onClick = onExpandToggle,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else if (isSubAgent) {
                Icon(
                    Icons.Default.SubdirectoryArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            
            // Main content column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Title
                Text(
                    text = session.title ?: "Untitled",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                // Metadata row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (childCount > 0) {
                        Text(
                            text = "$childCount sub",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    SessionStatusIndicator(status = status)
                    Text(
                        text = formatDateTime(session.updatedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    session.summary?.let { summary ->
                        if (summary.additions > 0) {
                            Text(
                                text = "+${summary.additions}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (summary.deletions > 0) {
                            Text(
                                text = "-${summary.deletions}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            
            // Project chip on far right
            if (showProjectChip && projectId != null && !projectName.isNullOrEmpty()) {
                ProjectChip(
                    projectId = projectId,
                    projectName = projectName,
                    onClick = { onProjectClick(projectId) }
                )
            }
        }
    }
    
    // Long-press context menu
    DropdownMenu(
        expanded = showContextMenu,
        onDismissRequest = { showContextMenu = false }
    ) {
        DropdownMenuItem(
            text = { Text("Delete") },
            onClick = {
                showContextMenu = false
                onDelete()
            },
            leadingIcon = {
                Icon(Icons.Default.Delete, contentDescription = null)
            }
        )
    }
}

@Composable
private fun ProjectChip(
    projectId: String,
    projectName: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RectangleShape,
        color = ProjectColors.colorForProject(projectId),
        modifier = Modifier
            .padding(start = 8.dp)
            .widthIn(max = 150.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text(
                text = projectName,
                style = MaterialTheme.typography.labelMedium,
                color = ProjectColors.textColorForProject(projectId),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SessionStatusIndicator(status: SessionStatus?) {
    when (status) {
        is SessionStatus.Busy -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Working",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        is SessionStatus.Retry -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Retry #${status.attempt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        is SessionStatus.Idle -> {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Ready",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
        null -> {}
    }
}

@Composable
private fun EmptySessionsView(
    modifier: Modifier = Modifier,
    onCreateSession: () -> Unit
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.Chat,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Text(
            text = "No sessions yet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Start a new conversation with your AI assistant",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onCreateSession) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("New Session")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewSessionDialog(
    projects: List<ProjectInfo>,
    defaultProjectId: String? = null,
    onDismiss: () -> Unit,
    onCreate: (String?, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    // Default to null (Global) unless a specific project is requested
    var selectedProject by remember(defaultProjectId, projects) { 
        mutableStateOf(if (defaultProjectId != null) projects.find { it.id == defaultProjectId } else null) 
    }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedProject?.name ?: "Global",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Project") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        // Global option first
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text("Global", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "No project context",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                selectedProject = null
                                expanded = false
                            }
                        )
                        
                        // Project options
                        projects.forEach { project ->
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text(project.name, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            project.worktree,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedProject = project
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title.takeIf { it.isNotBlank() }, selectedProject?.worktree) }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatDateTime(epochMillis: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.monthNumber}/${local.dayOfMonth}/${local.year} ${local.hour}:${local.minute.toString().padStart(2, '0')}"
}
