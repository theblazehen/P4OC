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
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.components.TuiConfirmDialog
import dev.blazelight.p4oc.ui.components.TuiAlertDialog
import dev.blazelight.p4oc.ui.components.TuiButton
import dev.blazelight.p4oc.ui.components.TuiTextButton
import dev.blazelight.p4oc.ui.components.TuiLoadingScreen
import dev.blazelight.p4oc.ui.components.TuiLoadingIndicator
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.model.SessionStatus
import dev.blazelight.p4oc.ui.theme.ProjectColors
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing

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
    viewModel: SessionListViewModel = koinViewModel(),
    filterProjectId: String? = null,
    onSessionClick: (sessionId: String, directory: String?) -> Unit,
    onNewSession: (sessionId: String, directory: String?) -> Unit,
    onSettings: () -> Unit,
    onProjects: () -> Unit = {},
    onProjectClick: (projectId: String) -> Unit = {},
    onNavigateBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

    val theme = LocalOpenCodeTheme.current

    Scaffold(
        containerColor = theme.background,
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = theme.backgroundElement,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onNavigateBack != null) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.size(Sizing.iconButtonMd)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                                modifier = Modifier.size(Sizing.iconLg)
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
                        modifier = Modifier.size(Sizing.iconButtonMd)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.cd_projects), modifier = Modifier.size(Sizing.iconAction))
                    }
                    IconButton(
                        onClick = viewModel::refresh,
                        modifier = Modifier.size(Sizing.iconButtonMd)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_refresh), modifier = Modifier.size(Sizing.iconAction))
                    }
                    IconButton(
                        onClick = onSettings,
                        modifier = Modifier.size(Sizing.iconButtonMd)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings), modifier = Modifier.size(Sizing.iconAction))
                    }
                }
            }
        },
        floatingActionButton = {
            SmallFloatingActionButton(
                onClick = { showNewSessionDialog = true },
                containerColor = theme.backgroundElement,
                contentColor = theme.text
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_new_session), modifier = Modifier.size(Sizing.iconAction))
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
                    TuiLoadingScreen(
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
                        contentPadding = PaddingValues(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
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
                        .padding(Spacing.md),
                    action = {
                        TextButton(onClick = viewModel::clearError) {
                            Text(stringResource(R.string.sessions_dismiss))
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
        TuiConfirmDialog(
            onDismissRequest = { showDeleteDialog = null },
            onConfirm = { viewModel.deleteSession(session.id) },
            title = stringResource(R.string.sessions_delete_title),
            message = stringResource(R.string.sessions_delete_confirm, session.title),
            confirmText = stringResource(R.string.sessions_delete),
            dismissText = stringResource(R.string.button_cancel),
            isDestructive = true
        )
    }
}

private fun buildSessionTree(sessions: List<SessionWithProject>): List<SessionNode> {
    val childrenByParent = sessions
        .mapNotNull { swp -> swp.session.parentID?.let { parentId -> parentId to swp } }
        .groupBy({ it.first }, { it.second })
    
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
                modifier = Modifier.padding(top = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
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
    val theme = LocalOpenCodeTheme.current
    val isBusy = status is SessionStatus.Busy
    val isRetrying = status is SessionStatus.Retry
    var showContextMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showContextMenu = true }
            ),
        color = when {
            isBusy -> theme.accent.copy(alpha = 0.1f)
            isRetrying -> theme.error.copy(alpha = 0.1f)
            isSubAgent -> theme.backgroundElement.copy(alpha = 0.7f)
            else -> theme.backgroundElement
        },
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection indicator
            Text(
                text = if (isBusy) "▶" else if (isRetrying) "!" else ">",
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    isBusy -> theme.accent
                    isRetrying -> theme.error
                    else -> theme.accent.copy(alpha = 0.3f)
                }
            )
            
            Spacer(Modifier.width(Spacing.sm))
            
            // Expand/collapse or subagent indicator
            if (onExpandToggle != null) {
                IconButton(
                    onClick = onExpandToggle,
                    modifier = Modifier.size(Sizing.chipHeight)
                ) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(Sizing.iconSm),
                        tint = theme.textMuted
                    )
                }
            } else if (isSubAgent) {
                Text(
                    text = "└",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textMuted
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
            }
            
            // Main content column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                // Title
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.text,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                // Metadata row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (childCount > 0) {
                        Text(
                            text = "[$childCount sub]",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.info
                        )
                    }
                    SessionStatusIndicator(status = status)
                    Text(
                        text = formatDateTime(session.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted
                    )
                    
                    session.summary?.let { summary ->
                        if (summary.additions > 0) {
                            Text(
                                text = "+${summary.additions}",
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.success
                            )
                        }
                        if (summary.deletions > 0) {
                            Text(
                                text = "-${summary.deletions}",
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.error
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
            text = { Text(stringResource(R.string.sessions_delete), color = theme.error) },
            onClick = {
                showContextMenu = false
                onDelete()
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Delete, 
                    contentDescription = stringResource(R.string.sessions_delete),
                    tint = theme.error
                )
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
            .padding(start = Spacing.md)
            .widthIn(max = 150.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md)
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
    val theme = LocalOpenCodeTheme.current
    when (status) {
        is SessionStatus.Busy -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TuiLoadingIndicator()
                Text(
                    text = stringResource(R.string.session_working),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.accent
                )
            }
        }
        is SessionStatus.Retry -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.retry),
                    modifier = Modifier.size(Sizing.iconXs),
                    tint = theme.error
                )
                Text(
                    text = stringResource(R.string.session_retry_format, status.attempt),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.error
                )
            }
        }
        is SessionStatus.Idle -> {
            Text(
                text = "●",
                style = MaterialTheme.typography.labelSmall,
                color = theme.success
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
    val theme = LocalOpenCodeTheme.current
    Column(
        modifier = modifier.padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            text = "◇",
            style = MaterialTheme.typography.displayMedium,
            color = theme.textMuted
        )
        Text(
            text = stringResource(R.string.sessions_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = theme.text
        )
        Text(
            text = stringResource(R.string.sessions_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textMuted
        )
        TuiButton(onClick = onCreateSession) {
            Text("+ ${stringResource(R.string.sessions_new)}")
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

    val globalText = stringResource(R.string.sessions_global)
    
    TuiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.sessions_new),
        confirmButton = {
            TuiButton(
                onClick = { onCreate(title.takeIf { it.isNotBlank() }, selectedProject?.worktree) }
            ) {
                Text(stringResource(R.string.sessions_create))
            }
        },
        dismissButton = {
            TuiTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedProject?.name ?: globalText,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.sessions_project)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                val theme = LocalOpenCodeTheme.current
                // Global option first
                DropdownMenuItem(
                    text = { 
                        Column {
                            Text(stringResource(R.string.sessions_global), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.sessions_no_project_context),
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.textMuted
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
                                    color = theme.textMuted
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
            label = { Text(stringResource(R.string.sessions_title_optional)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatDateTime(epochMillis: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.monthNumber}/${local.dayOfMonth}/${local.year} ${local.hour}:${local.minute.toString().padStart(2, '0')}"
}
