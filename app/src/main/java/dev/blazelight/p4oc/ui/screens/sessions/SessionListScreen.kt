package dev.blazelight.p4oc.ui.screens.sessions

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.model.SessionPresence
import dev.blazelight.p4oc.domain.model.SessionStatus
import dev.blazelight.p4oc.ui.components.TuiAlertDialog
import dev.blazelight.p4oc.ui.components.TuiButton
import dev.blazelight.p4oc.ui.components.TuiConfirmDialog
import dev.blazelight.p4oc.ui.components.TuiDropdownMenuItem
import dev.blazelight.p4oc.ui.components.TuiInputDialog
import dev.blazelight.p4oc.ui.components.TuiLoadingIndicator
import dev.blazelight.p4oc.ui.components.TuiSnackbar
import dev.blazelight.p4oc.ui.components.TuiTextButton
import dev.blazelight.p4oc.ui.components.TuiTopBar
import dev.blazelight.p4oc.ui.components.status.SessionStatusRow
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.ProjectColors
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.androidx.compose.koinViewModel

private data class SessionNode(
    val sessionWithProject: SessionWithProject,
    val children: List<SessionNode>
) {
    val totalDescendants: Int
        get() = children.size + children.sumOf { it.totalDescendants }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel = koinViewModel(),
    filterProjectId: String? = null,
    onSessionClick: (sessionId: String, directory: String?) -> Unit,
    onNewSession: (sessionId: String, directory: String?) -> Unit,
    onSettings: () -> Unit,
    onProjects: () -> Unit = {},
    onProjectClick: (directory: String) -> Unit = {},
    onViewChanges: (sessionId: String) -> Unit = {},
    onCreateSessionInWorkspace: (title: String?, directory: String?) -> Unit = { title, directory ->
        viewModel.createSession(title, directory)
    },
    autoCreateSession: Boolean = false,
    autoCreateSessionTitle: String? = null,
    autoCreateSessionDirectory: String? = null,
    onAutoCreateSessionConsumed: () -> Unit = {},
    onNavigateBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showNewSessionDialog by remember { mutableStateOf(false) }
    var showNewSessionCustomDir by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Session?>(null) }
    var showRenameDialog by remember { mutableStateOf<Session?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var sessionSearchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    val filterDirectory = remember(uiState.projects, filterProjectId) {
        filterProjectId?.let { filter ->
            uiState.projects.find { it.id == filter }?.worktree ?: filter
        }
    }

    val displayedSessions = remember(uiState.sessions, filterDirectory) {
        if (filterDirectory != null) {
            uiState.sessions.filter { it.session.directory == filterDirectory }
        } else {
            uiState.sessions
        }
    }

    val filteredProject = remember(uiState.projects, filterDirectory) {
        filterDirectory?.let { directory -> uiState.projects.find { it.worktree == directory } }
    }
    val projectName = filteredProject?.name ?: filterDirectory?.substringAfterLast("/")

    LaunchedEffect(autoCreateSession, autoCreateSessionTitle, autoCreateSessionDirectory) {
        if (autoCreateSession) {
            onAutoCreateSessionConsumed()
            viewModel.createSession(title = autoCreateSessionTitle, directory = autoCreateSessionDirectory)
        }
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
                    // Always show folder icon for project navigation
                    IconButton(
                        onClick = onProjects,
                        modifier = Modifier.size(Sizing.iconButtonMd)
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = stringResource(R.string.cd_projects),
                            modifier = Modifier.size(Sizing.iconAction)
                        )
                    }
                    IconButton(
                        onClick = viewModel::refresh,
                        modifier = Modifier.size(Sizing.iconButtonMd)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh),
                            modifier = Modifier.size(Sizing.iconAction)
                        )
                    }
                    IconButton(
                        onClick = {
                            showSearch = !showSearch
                            if (!showSearch) sessionSearchQuery = ""
                        },
                        modifier = Modifier.size(Sizing.iconButtonMd).testTag("sessions_search_button")
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.cd_search),
                            modifier = Modifier.size(Sizing.iconAction)
                        )
                    }
                    IconButton(
                        onClick = onSettings,
                        modifier = Modifier.size(Sizing.iconButtonMd).testTag("sessions_settings_button")
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.cd_settings),
                            modifier = Modifier.size(Sizing.iconAction)
                        )
                    }
                }
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading && displayedSessions.isEmpty()) {
                val theme = LocalOpenCodeTheme.current
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    TuiLoadingIndicator(
                        text = uiState.loadingText ?: stringResource(R.string.sessions_loading)
                    )
                    uiState.loadingProgress?.let { progress ->
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.width(Sizing.panelWidthLg),
                            color = theme.accent,
                            trackColor = theme.border,
                        )
                    }
                    uiState.loadingCounts?.let { counts ->
                        Text(
                            text = counts,
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.textMuted,
                        )
                    }
                }
            } else {
                val expandedSessions = remember { mutableStateMapOf<String, Boolean>() }

                // During search, flatten to matching sessions (tree roots only would
                // hide matching child sessions whose parent is filtered out).
                val sessionTree = remember(displayedSessions, sessionSearchQuery) {
                    val q = sessionSearchQuery.trim()
                    if (q.isBlank()) {
                        buildSessionTree(displayedSessions)
                    } else {
                        displayedSessions
                            .filter { it.session.title.contains(q, ignoreCase = true) }
                            .map { SessionNode(it, emptyList()) }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("sessions_list"),
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    val searchActive = sessionSearchQuery.isNotBlank()
                    if (showSearch) {
                        stickyHeader(key = "session_search") {
                            SessionSearchField(
                                query = sessionSearchQuery,
                                onQueryChange = { sessionSearchQuery = it },
                                onClose = {
                                    showSearch = false
                                    sessionSearchQuery = ""
                                },
                            )
                        }
                    }
                    // Pinned quick actions (hidden while searching)
                    if (!searchActive && filterProjectId == null) {
                        item(key = "quick_action_global") {
                            QuickActionCard(
                                icon = "\u25C6",
                                title = stringResource(R.string.sessions_quick_global),
                                subtitle = stringResource(R.string.sessions_quick_global_desc),
                                contentDescription = stringResource(R.string.cd_new_session),
                                onClick = {
                                    onCreateSessionInWorkspace(null, null)
                                },
                                modifier = Modifier.testTag("quick_action_global")
                            )
                        }

                        item(key = "quick_action_custom") {
                            QuickActionCard(
                                icon = "\u25C7",
                                title = stringResource(R.string.sessions_quick_custom),
                                subtitle = stringResource(R.string.sessions_quick_custom_desc),
                                contentDescription = stringResource(R.string.cd_new_session),
                                onClick = {
                                    showNewSessionCustomDir = true
                                    showNewSessionDialog = true
                                },
                                modifier = Modifier.testTag("quick_action_custom")
                            )
                        }
                    } else if (!searchActive) {
                        filterDirectory?.let { directory ->
                            item(key = "quick_action_project") {
                                QuickActionCard(
                                    icon = "\u25C6",
                                    title = stringResource(
                                        R.string.sessions_create_in_project,
                                        projectName ?: directory.substringAfterLast("/")
                                    ),
                                    subtitle = directory,
                                    contentDescription = stringResource(R.string.cd_new_session),
                                    onClick = { onCreateSessionInWorkspace(null, directory) },
                                    modifier = Modifier.testTag("project_new_session_button")
                                )
                            }
                        }
                    }

                    if (searchActive && sessionTree.isEmpty()) {
                        item(key = "no_match") {
                            Text(
                                text = stringResource(R.string.sessions_search_no_match),
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.textMuted,
                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.lg)
                            )
                        }
                    } else if (displayedSessions.isEmpty() && filterProjectId == null) {
                        item(key = "empty_hint") {
                            Text(
                                text = stringResource(R.string.sessions_empty_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.textMuted,
                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.lg)
                            )
                        }
                    } else if (displayedSessions.isEmpty()) {
                        item(key = "empty_hint") {
                            Column(
                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.lg),
                                verticalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                Text(
                                    text = stringResource(R.string.sessions_empty_title),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = theme.textMuted,
                                )
                            }
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
                                sessionPresences = uiState.sessionPresences,
                                showProjectChip = filterProjectId == null,
                                onSessionClick = { session -> onSessionClick(session.id, session.directory) },
                                onDeleteSession = { showDeleteDialog = it },
                                onRenameSession = { showRenameDialog = it },
                                onShareSession = { session ->
                                    if (session.shareUrl != null) {
                                        viewModel.unshareSession(session.id)
                                    } else {
                                        viewModel.shareSession(session.id)
                                    }
                                },
                                onViewChanges = { session ->
                                    onViewChanges(session.id)
                                },
                                onSummarizeSession = { session ->
                                    viewModel.summarizeSession(session.id)
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
                        .padding(Spacing.md),
                    action = {
                        TextButton(onClick = viewModel::clearError, shape = RectangleShape) {
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
            defaultProjectId = filteredProject?.id,
            initialUseCustomDirectory = showNewSessionCustomDir,
            onDismiss = {
                showNewSessionDialog = false
                showNewSessionCustomDir = false
            },
            onCreate = { title, directory ->
                onCreateSessionInWorkspace(title, directory)
                showNewSessionDialog = false
                showNewSessionCustomDir = false
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

    showRenameDialog?.let { session ->
        TuiInputDialog(
            onDismissRequest = { showRenameDialog = null },
            onConfirm = { newTitle ->
                viewModel.renameSession(session.id, newTitle)
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

@Composable
private fun SessionSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val theme = LocalOpenCodeTheme.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Surface(color = theme.backgroundPanel, shape = RectangleShape, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.sessions_search_placeholder),
                        color = theme.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = theme.text),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = theme.accent,
                    focusedTextColor = theme.text,
                    unfocusedTextColor = theme.text,
                ),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(Sizing.iconButtonMd)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.button_cancel),
                    modifier = Modifier.size(Sizing.iconAction),
                    tint = theme.textMuted,
                )
            }
        }
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
    sessionPresences: Map<String, SessionPresence>,
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
            presence = sessionPresences[session.id] ?: SessionPresence.IDLE,
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
                        sessionPresences = sessionPresences,
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
    presence: SessionPresence,
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
    var showContextMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showContextMenu = true },
                role = Role.Button
            ),
        color = when {
            presence == SessionPresence.BUSY -> theme.accent.copy(alpha = 0.1f)
            presence == SessionPresence.RETRYING || presence == SessionPresence.ERROR -> theme.error.copy(alpha = 0.1f)
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
            // Expand/collapse or subagent indicator
            if (onExpandToggle != null) {
                IconButton(
                    onClick = onExpandToggle,
                    modifier = Modifier.size(Sizing.chipHeight)
                ) {
                    Icon(
                        if (isExpanded) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                        contentDescription = if (isExpanded) {
                            stringResource(R.string.cd_collapse)
                        } else {
                            stringResource(R.string.cd_expand)
                        },
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
                    SessionStatusIndicator(status = status, presence = presence)
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
                    if (session.shareUrl != null) {
                        Text(
                            text = "◈ ${stringResource(R.string.sessions_shared_badge)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.info
                        )
                    }
                }
            }

            // Directory chip on far right. Real projects and pseudo-project
            // directories use the same navigation model: filter by directory.
            if (showProjectChip && !projectName.isNullOrEmpty()) {
                ProjectChip(
                    projectKey = projectId ?: session.directory,
                    projectName = projectName,
                    onClick = { onProjectClick(session.directory) }
                )
            }
        }
    }

    // Long-press context menu
    DropdownMenu(
        expanded = showContextMenu,
        onDismissRequest = { showContextMenu = false }
    ) {
        TuiDropdownMenuItem(
            text = stringResource(R.string.sessions_rename),
            onClick = {
                showContextMenu = false
                onRename()
            },
            leadingIcon = Icons.Default.Edit
        )
        TuiDropdownMenuItem(
            text = stringResource(R.string.sessions_view_changes),
            onClick = {
                showContextMenu = false
                onViewChanges()
            },
            leadingIcon = Icons.Default.Description
        )
        TuiDropdownMenuItem(
            text = stringResource(R.string.sessions_summarize),
            onClick = {
                showContextMenu = false
                onSummarize()
            },
            leadingIcon = Icons.Default.Summarize
        )
        if (isShared) {
            TuiDropdownMenuItem(
                text = stringResource(R.string.sessions_unshare),
                onClick = {
                    showContextMenu = false
                    onShare()
                },
                leadingIcon = Icons.Default.LinkOff
            )
        } else {
            TuiDropdownMenuItem(
                text = stringResource(R.string.sessions_share),
                onClick = {
                    showContextMenu = false
                    onShare()
                },
                leadingIcon = Icons.Default.Share
            )
        }
        HorizontalDivider(color = theme.borderSubtle)
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
    projectKey: String,
    projectName: String,
    onClick: (() -> Unit)?
) {
    val modifier = Modifier
        .padding(start = Spacing.md)
        .widthIn(max = Sizing.chipMaxWidth)
        .testTag("session_directory_chip_$projectName")
        .semantics { contentDescription = "Open sessions for $projectName" }
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RectangleShape,
        color = ProjectColors.colorForProject(projectKey),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md)
        ) {
            Text(
                text = projectName,
                style = MaterialTheme.typography.labelMedium,
                color = ProjectColors.textColorForProject(projectKey),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SessionStatusIndicator(status: SessionStatus?, presence: SessionPresence) {
    val label = when (status) {
        is SessionStatus.Busy -> stringResource(R.string.session_working)
        is SessionStatus.Retry -> stringResource(R.string.session_retry_format, status.attempt)
        is SessionStatus.Idle, null -> null
    }

    if (label != null) {
        SessionStatusRow(presence = presence, label = label)
    }
}

@Composable
private fun QuickActionCard(
    icon: String,
    title: String,
    subtitle: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription }
            .clickable(role = Role.Button, onClick = onClick),
        color = theme.background,
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .border(Sizing.strokeThin, theme.accent, RectangleShape)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.accent
            )
            Text(
                text = icon,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.accent
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.text
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMuted
                )
            }
            Text(
                text = "\u2192",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textMuted
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
        mutableStateOf(
            if (defaultProjectId != null && !initialUseCustomDirectory) {
                projects.find {
                    it.id == defaultProjectId
                }
            } else {
                null
            }
        )
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
                            Text(
                                stringResource(R.string.sessions_custom_directory),
                                style = MaterialTheme.typography.bodyMedium
                            )
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
    return "${local.monthNumber}/${local.dayOfMonth}/${local.year} ${local.hour}:${local.minute.toString().padStart(
        2,
        '0'
    )}"
}
