package dev.blazelight.p4oc.ui.screens.sessions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.components.TuiConfirmDialog
import dev.blazelight.p4oc.ui.components.TuiAlertDialog
import dev.blazelight.p4oc.ui.components.TuiInputDialog
import dev.blazelight.p4oc.ui.components.TuiButton
import dev.blazelight.p4oc.ui.components.TuiTextButton
import dev.blazelight.p4oc.ui.components.TuiDropdownMenu
import dev.blazelight.p4oc.ui.components.TuiDropdownMenuItem
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
import dev.blazelight.p4oc.ui.components.TuiTopBar
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.components.TuiCard
import dev.blazelight.p4oc.ui.components.TuiSnackbar
import android.content.Intent
import androidx.compose.ui.platform.LocalContext

// Corner radius tokens matching ServerScreen
private val CardRadius = 8.dp
private val BadgeRadius = 4.dp
private val DotRadius = 2.dp
private val ButtonRadius = 6.dp

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
    onViewChanges: (sessionId: String) -> Unit = {},
    onNavigateBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showNewSessionDialog by remember { mutableStateOf(false) }
    var showNewSessionCustomDir by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Session?>(null) }
    var showRenameDialog by remember { mutableStateOf<Session?>(null) }
    val context = LocalContext.current
    
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

    LaunchedEffect(uiState.shareUrl) {
        uiState.shareUrl?.let { shareUrl ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareUrl)
            }
            context.startActivity(Intent.createChooser(intent, null))
            viewModel.clearShareUrl()
        }
    }

    val theme = LocalOpenCodeTheme.current

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TuiTopBar(
                title = projectName ?: stringResource(R.string.sessions_title_default),
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = onProjects,
                        modifier = Modifier.size(Sizing.iconButtonMd)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.cd_projects), modifier = Modifier.size(Sizing.iconAction), tint = theme.textMuted)
                    }
                    IconButton(
                        onClick = viewModel::refresh,
                        modifier = Modifier.size(Sizing.iconButtonMd)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_refresh), modifier = Modifier.size(Sizing.iconAction), tint = theme.textMuted)
                    }
                    IconButton(
                        onClick = onSettings,
                        modifier = Modifier.size(Sizing.iconButtonMd).testTag("sessions_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings), modifier = Modifier.size(Sizing.iconAction), tint = theme.textMuted)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewSessionDialog = true },
                shape = RoundedCornerShape(ButtonRadius),
                containerColor = theme.accent,
                contentColor = theme.background,
                modifier = Modifier.testTag("fab_new_session")
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.sessions_new),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading && displayedSessions.isEmpty()) {
                TuiLoadingScreen(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val expandedSessions = remember { mutableStateMapOf<String, Boolean>() }

                val sessionTree = remember(displayedSessions) {
                    buildSessionTree(displayedSessions)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("sessions_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Pinned quick actions (only on unfiltered list)
                    if (filterProjectId == null) {
                        item(key = "quick_actions_row") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                QuickActionCard(
                                    icon = Icons.Default.PlayArrow,
                                    label = stringResource(R.string.sessions_quick_global),
                                    onClick = { viewModel.createSession(title = null, directory = null) },
                                    modifier = Modifier.weight(1f).testTag("quick_action_global")
                                )
                                QuickActionCard(
                                    icon = Icons.Default.CreateNewFolder,
                                    label = stringResource(R.string.sessions_quick_custom),
                                    onClick = {
                                        showNewSessionCustomDir = true
                                        showNewSessionDialog = true
                                    },
                                    modifier = Modifier.weight(1f).testTag("quick_action_custom")
                                )
                            }
                        }
                    }

                    if (displayedSessions.isEmpty() && filterProjectId == null) {
                        item(key = "empty_hint") {
                            EmptySessionsHint(stringResource(R.string.sessions_empty_hint))
                        }
                    } else if (displayedSessions.isEmpty()) {
                        item(key = "empty_hint") {
                            EmptySessionsHint(stringResource(R.string.sessions_empty_title))
                        }
                    } else {
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
                                onRenameSession = { showRenameDialog = it },
                                onShareSession = { session ->
                                    if (session.shareUrl != null) {
                                        viewModel.unshareSession(session.id, session.directory)
                                    } else {
                                        viewModel.shareSession(session.id, session.directory)
                                    }
                                },
                                onViewChanges = { session ->
                                    onViewChanges(session.id)
                                },
                                onSummarizeSession = { session ->
                                    viewModel.summarizeSession(session.id, session.directory)
                                },
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
                TuiSnackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = viewModel::clearError, shape = RoundedCornerShape(4.dp)) {
                            Text(
                                stringResource(R.string.sessions_dismiss),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                ) {
                    Text(error, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }

    if (showNewSessionDialog) {
        NewSessionDialog(
            projects = uiState.projects,
            defaultProjectId = filterProjectId,
            initialUseCustomDirectory = showNewSessionCustomDir,
            onDismiss = {
                showNewSessionDialog = false
                showNewSessionCustomDir = false
            },
            onCreate = { title, directory ->
                viewModel.createSession(title, directory)
                showNewSessionDialog = false
                showNewSessionCustomDir = false
            }
        )
    }

    showDeleteDialog?.let { session ->
        TuiConfirmDialog(
            onDismissRequest = { showDeleteDialog = null },
            onConfirm = { viewModel.deleteSession(session.id, session.directory) },
            title = stringResource(R.string.sessions_delete_title),
            message = stringResource(R.string.sessions_delete_confirm, session.title),
            confirmText = stringResource(R.string.sessions_delete),
            dismissText = stringResource(R.string.button_cancel),
            isDestructive = true
        )
    }

    showRenameDialog?.let { session ->
        TuiInputDialog(
            onDismissRequest = { showRenameDialog = null },
            onConfirm = { newTitle ->
                viewModel.renameSession(session.id, newTitle, session.directory)
                showRenameDialog = null
            },
            title = stringResource(R.string.sessions_rename_title),
            initialValue = session.title,
            label = stringResource(R.string.sessions_title_optional),
            confirmText = stringResource(R.string.sessions_rename),
            dismissText = stringResource(R.string.button_cancel)
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
    onRenameSession: (Session) -> Unit,
    onShareSession: (Session) -> Unit,
    onViewChanges: (Session) -> Unit,
    onSummarizeSession: (Session) -> Unit,
    onProjectClick: (String) -> Unit,
    onToggleExpand: (String) -> Unit
) {
    val swp = node.sessionWithProject
    val session = swp.session
    val isExpanded = expandedSessions[session.id] ?: false
    val hasChildren = node.children.isNotEmpty()
    val indentPadding: Dp = Sizing.treeIndent * depth
    
    Column(modifier = Modifier.padding(start = indentPadding)) {
        SessionCard(
            session = session,
            projectId = swp.projectId,
            projectName = swp.projectName,
            showProjectChip = showProjectChip,
            status = sessionStatuses[session.id],
            isShared = session.shareUrl != null,
            onClick = { onSessionClick(session) },
            onDelete = { onDeleteSession(session) },
            onRename = { onRenameSession(session) },
            onShare = { onShareSession(session) },
            onViewChanges = { onViewChanges(session) },
            onSummarize = { onSummarizeSession(session) },
            onProjectClick = onProjectClick,
            childCount = node.totalDescendants,
            isExpanded = isExpanded,
            onExpandToggle = if (hasChildren) { { onToggleExpand(session.id) } } else null,
            isSubAgent = depth > 0
        )
        
        AnimatedVisibility(
            visible = isExpanded && hasChildren,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(top = 8.dp, start = 12.dp),
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
                        onRenameSession = onRenameSession,
                        onShareSession = onShareSession,
                        onViewChanges = onViewChanges,
                        onSummarizeSession = onSummarizeSession,
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
    isShared: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onViewChanges: () -> Unit,
    onSummarize: () -> Unit,
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

    val cardColor = when {
        isBusy    -> theme.accent.copy(alpha = 0.08f)
        isRetrying -> theme.error.copy(alpha = 0.08f)
        isSubAgent -> theme.backgroundElement.copy(alpha = 0.6f)
        else      -> theme.backgroundElement
    }
    val indicatorColor = when {
        isBusy    -> theme.accent
        isRetrying -> theme.error
        else      -> theme.success
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showContextMenu = true },
                role = Role.Button
            ),
        shape = RoundedCornerShape(CardRadius),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isBusy) 2.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status indicator dot / busy spinner
            if (isBusy) {
                BusyDot(color = theme.accent)
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(indicatorColor.copy(alpha = if (isSubAgent) 0.5f else 1f))
                )
            }

            // Expand toggle (parent sessions with children)
            if (onExpandToggle != null) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(theme.backgroundElement)
                        .clickable(role = Role.Button) { onExpandToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) stringResource(R.string.cd_collapse) else stringResource(R.string.cd_expand),
                        modifier = Modifier.size(14.dp),
                        tint = theme.textMuted
                    )
                }
            } else if (isSubAgent) {
                Text(
                    text = "╰",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMuted
                )
            }

            // Main content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = theme.text,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SessionStatusIndicator(status = status)
                    Text(
                        text = formatDateTime(session.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = theme.textMuted
                    )
                    if (childCount > 0) {
                        MetaBadge(
                            text = "$childCount sub",
                            color = theme.info
                        )
                    }
                    session.summary?.let { summary ->
                        if (summary.additions > 0) {
                            Text(
                                text = "+${summary.additions}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = theme.success
                            )
                        }
                        if (summary.deletions > 0) {
                            Text(
                                text = "-${summary.deletions}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = theme.error
                            )
                        }
                    }
                    if (session.shareUrl != null) {
                        MetaBadge(text = stringResource(R.string.sessions_shared_badge), color = theme.info)
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

            // More icon hint
            Icon(
                Icons.Default.MoreVert,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(role = Role.Button) { showContextMenu = true },
                tint = theme.textMuted.copy(alpha = 0.5f)
            )
        }
    }

    // Long-press context menu
    TuiDropdownMenu(
        expanded = showContextMenu,
        onDismissRequest = { showContextMenu = false }
    ) {
        TuiDropdownMenuItem(
            text = stringResource(R.string.sessions_rename),
            onClick = { showContextMenu = false; onRename() },
            leadingIcon = Icons.Default.Edit
        )
        TuiDropdownMenuItem(
            text = stringResource(R.string.sessions_view_changes),
            onClick = { showContextMenu = false; onViewChanges() },
            leadingIcon = Icons.Default.Description
        )
        TuiDropdownMenuItem(
            text = stringResource(R.string.sessions_summarize),
            onClick = { showContextMenu = false; onSummarize() },
            leadingIcon = Icons.Default.Summarize
        )
        if (isShared) {
            TuiDropdownMenuItem(
                text = stringResource(R.string.sessions_unshare),
                onClick = { showContextMenu = false; onShare() },
                leadingIcon = Icons.Default.LinkOff
            )
        } else {
            TuiDropdownMenuItem(
                text = stringResource(R.string.sessions_share),
                onClick = { showContextMenu = false; onShare() },
                leadingIcon = Icons.Default.Share
            )
        }
        HorizontalDivider(color = theme.borderSubtle)
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sessions_delete), color = theme.error, fontFamily = FontFamily.Monospace) },
            onClick = { showContextMenu = false; onDelete() },
            leadingIcon = {
                Icon(Icons.Default.Delete, contentDescription = null, tint = theme.error)
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
    val bgColor = ProjectColors.colorForProject(projectId)
    val textColor = ProjectColors.textColorForProject(projectId)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(BadgeRadius))
            .background(bgColor)
            .clickable(role = Role.Button, onClick = onClick)
            .widthIn(max = Sizing.chipMaxWidth)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = projectName,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MetaBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(BadgeRadius))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(BadgeRadius))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BusyDot(color: androidx.compose.ui.graphics.Color) {
    val transition = rememberInfiniteTransition(label = "busyDot")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(3.dp))
            .alpha(alpha)
            .background(color)
    )
}

@Composable
private fun SessionStatusIndicator(status: SessionStatus?) {
    val theme = LocalOpenCodeTheme.current
    when (status) {
        is SessionStatus.Busy -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TuiLoadingIndicator()
                Text(
                    text = stringResource(R.string.session_working),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = theme.accent
                )
            }
        }
        is SessionStatus.Retry -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = theme.error
                )
            }
        }
        is SessionStatus.Idle -> {
            // idle — dot handled by SessionCard indicator
        }
        null -> {}
    }
}

@Composable
private fun EmptySessionsHint(text: String) {
    val theme = LocalOpenCodeTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.backgroundElement),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    tint = theme.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = theme.textMuted
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(CardRadius))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(CardRadius),
        colors = CardDefaults.cardColors(containerColor = theme.backgroundElement),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = theme.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = theme.text
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewSessionDialog(
    projects: List<ProjectInfo>,
    defaultProjectId: String? = null,
    initialUseCustomDirectory: Boolean = false,
    onDismiss: () -> Unit,
    onCreate: (String?, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    // Default to null (Global) unless a specific project is requested
    var selectedProject by remember(defaultProjectId, projects) { 
        mutableStateOf(if (defaultProjectId != null && !initialUseCustomDirectory) projects.find { it.id == defaultProjectId } else null) 
    }
    var expanded by remember { mutableStateOf(false) }
    var useCustomDirectory by remember { mutableStateOf(initialUseCustomDirectory) }
    var customDirectory by remember { mutableStateOf("") }

    val globalText = stringResource(R.string.sessions_global)
    val customText = stringResource(R.string.sessions_custom_directory)
    
    // Resolve the effective directory for session creation
    val effectiveDirectory = when {
        useCustomDirectory -> customDirectory.takeIf { it.isNotBlank() }
        else -> selectedProject?.worktree
    }
    
    TuiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.sessions_new),
        confirmButton = {
            TuiButton(
                onClick = { onCreate(title.takeIf { it.isNotBlank() }, effectiveDirectory) }
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
                value = when {
                    useCustomDirectory -> customText
                    selectedProject != null -> selectedProject!!.name
                    else -> globalText
                },
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
                        useCustomDirectory = false
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
                            useCustomDirectory = false
                            expanded = false
                        }
                    )
                }
                
                // Custom directory option
                DropdownMenuItem(
                    text = { 
                        Column {
                            Text(stringResource(R.string.sessions_custom_directory), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.sessions_custom_directory_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.textMuted
                            )
                        }
                    },
                    onClick = {
                        useCustomDirectory = true
                        selectedProject = null
                        expanded = false
                    }
                )
            }
        }
        
        // Show custom directory text field when selected
        if (useCustomDirectory) {
            OutlinedTextField(
                value = customDirectory,
                onValueChange = { customDirectory = it },
                label = { Text(stringResource(R.string.sessions_directory_path)) },
                placeholder = { Text(stringResource(R.string.sessions_directory_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
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
