package dev.blazelight.p4oc.ui.screens.sessions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.delay

// Terminal style dimensions
private val CardRadius = 0.dp
private val BadgeRadius = 0.dp
private val DotRadius = 0.dp
private val ButtonRadius = 0.dp
private val TerminalLineHeight = 2.dp

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
    onNavigateBack: (() -> Unit)? = null,
    showTopBar: Boolean = true
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
            if (showTopBar) {
                SessionsTopBar(
                    projectName = projectName,
                    onNavigateBack = onNavigateBack,
                    onProjects = onProjects,
                    onRefresh = viewModel::refresh,
                    onSettings = onSettings
                )
            }
        },
        floatingActionButton = {
            Text(
                text = "+",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = theme.accent,
                modifier = Modifier
                    .clickable { showNewSessionDialog = true }
                    .testTag("fab_new_session")
                    .padding(16.dp)
            )
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
                    // Animated PocketCode Logo Header
                    item(key = "logo_header") {
                        PocketCodeLogoHeader()
                    }

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

    // Terminal TUI style with left accent bar
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showContextMenu = true },
                role = Role.Button
            )
    ) {
        // Left accent bar - color indicates status
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(indicatorColor)
        )

        // Main content area
        Column(
            modifier = Modifier
                .weight(1f)
                .background(cardColor)
        ) {
            Row(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Status prefix icon
                val statusChar = when {
                    isBusy -> "▶"
                    isRetrying -> "⚠"
                    isSubAgent -> "├"
                    else -> if (status != null) "●" else "○"
                }
                Text(
                    text = statusChar,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = indicatorColor.copy(alpha = if (isSubAgent) 0.5f else 1f)
                )

                // Expand toggle (parent sessions with children)
                if (onExpandToggle != null) {
                    Text(
                        text = if (isExpanded) "▼" else "▶",
                        modifier = Modifier.clickable(role = Role.Button) { onExpandToggle() },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted
                    )
                } else if (isSubAgent) {
                    Text(
                        text = "╰",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted
                    )
                }

                // Session title
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isBusy) FontWeight.Bold else FontWeight.Normal,
                    color = if (isBusy) theme.accent else theme.text,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Menu indicator
                Text(
                    text = "≡",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted.copy(alpha = 0.5f),
                    modifier = Modifier.clickable(role = Role.Button) { showContextMenu = true }
                )
            }

            // Bottom info line
            Row(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "[${session.id.take(6)}]",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = theme.textMuted.copy(alpha = 0.7f)
                )

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
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f))
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
    Text(
        text = "▶",
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
        color = color.copy(alpha = alpha)
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
                Text(
                    text = "▶",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.accent
                )
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
                Text(
                    text = "↻",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.error
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

// Animated PocketCode Logo Header - ultra terminal style with typewriter effect
@Composable
private fun PocketCodeLogoHeader(
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    val infiniteTransition = rememberInfiniteTransition(label = "logo_pulse")

    // Funny rotating terminal commands
    val funnyCommands = remember {
        listOf(
            "make coffee",
            "sudo apt-get life",
            "git commit -m \"oops\"",
            "rm -rf /problems",
            "./fix-bugs.sh",
            "npm install happiness",
            "docker run sanity",
            "chmod 777 world",
            "echo \"hello world\"",
            "ping 127.0.0.1",
            "while(alive) { code() }",
            "import antigravity",
            "System.exit(0)",
            ":(){ :|:& };:",
            "tree /dev/brain",
            "cat /etc/motd",
            "man happiness",
            "alias sleep=code",
            "kill -9 procrastination",
            "service life restart"
        )
    }

    // Animation states
    var typedLogo by remember { mutableStateOf("") }
    var displayedCommand by remember { mutableStateOf("") }
    var currentCommandIndex by remember { mutableIntStateOf(0) }
    var cursorVisible by remember { mutableStateOf(true) }
    var animationPhase by remember { mutableIntStateOf(0) } // 0=typing logo, 1=blink logo, 2=typing cmd, 3=show cmd, 4=clearing

    // Main animation loop - single controlled coroutine
    LaunchedEffect(Unit) {
        val logoText = "PocketCode"

        // Phase 0: Type logo
        animationPhase = 0
        logoText.forEachIndexed { i, _ ->
            typedLogo = logoText.substring(0, i + 1)
            delay(60)
        }

        // Phase 1: Blink cursor after logo
        animationPhase = 1
        repeat(3) {
            cursorVisible = !cursorVisible
            delay(400)
        }
        cursorVisible = true
        delay(500)

        // Loop through commands forever
        while (true) {
            val command = funnyCommands[currentCommandIndex]

            // Phase 2: Type command
            animationPhase = 2
            command.forEachIndexed { i, _ ->
                displayedCommand = command.substring(0, i + 1)
                delay(45)
            }

            // Phase 3: Show command with blinking cursor
            animationPhase = 3
            repeat(5) {
                cursorVisible = !cursorVisible
                delay(350)
            }
            cursorVisible = true
            delay(600)

            // Next command - keep text visible while blinking then clear
            repeat(2) {
                cursorVisible = !cursorVisible
                delay(250)
            }

            // Clear and move to next
            currentCommandIndex = (currentCommandIndex + 1) % funnyCommands.size
            displayedCommand = ""  // Clear just before typing next command
            delay(150)
        }
    }

    // Animated values for ambient glow moving around
    val glowPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glow_pos"
    )

    val accentGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "accent"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column {
            // Top connection line
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(2.dp)
                        .background(theme.accent.copy(alpha = accentGlow))
                )
                Text(
                    text = "┬",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.accent.copy(alpha = accentGlow)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(theme.border.copy(alpha = 0.3f))
                )
            }

            // Main logo box with animated moving border glow
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Ambient glow effect moving around the border
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    theme.accent.copy(alpha = 0f),
                                    theme.accent.copy(alpha = 0.15f),
                                    theme.accent.copy(alpha = 0.4f),
                                    theme.accent.copy(alpha = 0.15f),
                                    theme.accent.copy(alpha = 0f)
                                ),
                                center = Offset(
                                    x = if (glowPosition < 0.5f) glowPosition * 2f else (1f - (glowPosition - 0.5f) * 2f),
                                    y = 0.5f
                                )
                            )
                        )
                )

                // Main content row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, theme.border.copy(alpha = 0.4f))
                        .background(theme.backgroundElement.copy(alpha = 0.15f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left side: Logo Symbol + PocketCode with typewriter
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Logo bracket with pulse
                        Text(
                            text = "[",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = theme.accent.copy(alpha = accentGlow)
                        )

                        // P symbol
                        Text(
                            text = "◈",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.headlineSmall,
                            color = theme.accent.copy(alpha = 0.8f)
                        )

                        Text(
                            text = "]",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = theme.accent.copy(alpha = accentGlow)
                        )

                        // Vertical separator
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(theme.border.copy(alpha = 0.4f))
                        )

                        // Typewriter text
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = typedLogo,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = theme.text
                            )
                            // Blinking block cursor after logo
                            if (cursorVisible && animationPhase <= 1) {
                                Text(
                                    text = "█",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = theme.accent.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(start = 1.dp)
                                )
                            }
                        }
                    }

                    // Right side: Terminal prompt + rotating funny command
                    // Fixed width container so prompt stays in place
                    Row(
                        modifier = Modifier.widthIn(min = 140.dp, max = 180.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        // Fixed prompt (always in same position)
                        Text(
                            text = "~$",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.textMuted.copy(alpha = 0.5f),
                            modifier = Modifier.padding(end = 4.dp)
                        )

                        // Command text container - grows left-to-right
                        Text(
                            text = displayedCommand,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.success.copy(alpha = 0.9f),
                            maxLines = 1
                        )

                        // Blinking cursor immediately after command text
                        if (cursorVisible && animationPhase >= 2 && displayedCommand.isNotEmpty()) {
                            Text(
                                text = "█",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.accent.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // Bottom ascii connector
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "├",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.border.copy(alpha = 0.5f)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(theme.border.copy(alpha = 0.3f))
                )
                Text(
                    text = "┤",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.border.copy(alpha = 0.5f)
                )
            }
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
    val isGlobal = icon == Icons.Default.PlayArrow
    val symbol = if (isGlobal) "▶" else "►"
    val color = if (isGlobal) theme.success else theme.accent

    Row(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Animated/cycling bracket effect
        Text(
            text = if (isGlobal) "┌" else "╭",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = color.copy(alpha = 0.6f)
        )

        Column {
            Text(
                text = "$symbol $label",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Text(
                text = if (isGlobal) "~/global" else "~/custom",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = theme.textMuted.copy(alpha = 0.7f)
            )
        }

        Text(
            text = if (isGlobal) "┘" else "╯",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = color.copy(alpha = 0.6f)
        )
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

@Composable
private fun SessionsTopBar(
    projectName: String?,
    onNavigateBack: (() -> Unit)?,
    onProjects: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    val infiniteTransition = rememberInfiniteTransition(label = "topbar_pulse")

    // Ambient glow animation
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.background)
    ) {
        Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))

        // Terminal-style unified header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Top connector line
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(1.dp)
                        .background(theme.border.copy(alpha = 0.3f))
                )
                Text(
                    text = "┌",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.border.copy(alpha = 0.5f)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    theme.border.copy(alpha = 0.3f),
                                    theme.accent.copy(alpha = glowAlpha),
                                    theme.border.copy(alpha = 0.3f)
                                )
                            )
                        )
                )
                Text(
                    text = "┐",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.border.copy(alpha = 0.5f)
                )
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(1.dp)
                        .background(theme.border.copy(alpha = 0.3f))
                )
            }

            // Main content box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, theme.border.copy(alpha = 0.4f))
                    .background(theme.backgroundElement.copy(alpha = 0.1f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side: Navigation and path
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Back button or root indicator
                    if (onNavigateBack != null) {
                        Text(
                            text = "⟨",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.accent,
                            modifier = Modifier.clickable(role = Role.Button) { onNavigateBack() }
                        )
                    } else {
                        Text(
                            text = "~",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.accent.copy(alpha = 0.6f)
                        )
                    }

                    // Path separator
                    Text(
                        text = "/",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.border.copy(alpha = 0.4f)
                    )

                    // Current location
                    val locationText = projectName ?: "sessions"
                    val subText = if (projectName != null) "project" else "all"

                    Column {
                        Text(
                            text = locationText,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = theme.text
                        )
                        Text(
                            text = subText,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.textMuted.copy(alpha = 0.7f)
                        )
                    }
                }

                // Right side: Action buttons in terminal style
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Projects button
                    if (projectName.isNullOrEmpty()) {
                        Text(
                            text = "[P]",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.textMuted,
                            modifier = Modifier.clickable(role = Role.Button) { onProjects() }
                        )
                    }

                    // Separator
                    Text(
                        text = "│",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.border.copy(alpha = 0.3f)
                    )

                    // Refresh
                    Text(
                        text = "[↻]",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted,
                        modifier = Modifier.clickable(role = Role.Button) { onRefresh() }
                    )

                    // Separator
                    Text(
                        text = "│",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.border.copy(alpha = 0.3f)
                    )

                    // Settings with accent
                    Text(
                        text = "[⚙]",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.accent,
                        modifier = Modifier.clickable(role = Role.Button) { onSettings() }
                    )
                }
            }

            // Bottom connector to TabBar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(1.dp)
                        .background(theme.border.copy(alpha = 0.3f))
                )
                Text(
                    text = "├",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.border.copy(alpha = 0.5f)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(theme.border.copy(alpha = 0.3f))
                )
                // Center connector for tabs
                Text(
                    text = "┴",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.accent.copy(alpha = glowAlpha)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(theme.border.copy(alpha = 0.3f))
                )
                Text(
                    text = "┤",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.border.copy(alpha = 0.5f)
                )
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(1.dp)
                        .background(theme.border.copy(alpha = 0.3f))
                )
            }
        }
    }
}

private fun formatDateTime(epochMillis: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.monthNumber}/${local.dayOfMonth}/${local.year} ${local.hour}:${local.minute.toString().padStart(2, '0')}"
}
