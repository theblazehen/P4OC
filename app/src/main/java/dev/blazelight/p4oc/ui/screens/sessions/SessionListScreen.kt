package dev.blazelight.p4oc.ui.screens.sessions

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import dev.blazelight.p4oc.ui.components.TuiConfirmDialog
import dev.blazelight.p4oc.ui.components.TuiDropdownMenuItem
import dev.blazelight.p4oc.ui.components.TuiInputDialog
import dev.blazelight.p4oc.ui.components.TuiLoadingIndicator
import dev.blazelight.p4oc.ui.components.TuiSnackbar
import dev.blazelight.p4oc.ui.components.TuiTopBar
import dev.blazelight.p4oc.ui.components.status.SessionStatusRow
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.ProjectColors
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Instant

private data class SessionNode(
    val sessionWithProject: SessionWithProject,
    val children: List<SessionNode>
) {
    val totalDescendants: Int
        get() = children.size + children.sumOf { it.totalDescendants }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod", "FunctionNaming")
fun SessionListScreen(
    viewModel: SessionListViewModel = koinViewModel(),
    filterProjectId: String? = null,
    isSessionForkPending: Boolean = false,
    onSessionClick: (sessionId: String, directory: String?) -> Unit,
    onNewSession: (sessionId: String, directory: String?) -> Unit,
    onSettings: () -> Unit,
    onProjects: () -> Unit = {},
    onProjectClick: (directory: String) -> Unit = {},
    onViewChanges: (sessionId: String) -> Unit = {},
    onForkSessionInWorkspace: (sessionId: String, directory: String?) -> Unit = { sessionId, _ ->
        viewModel.forkSession(sessionId)
    },
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
    var showDeleteDialog by remember { mutableStateOf<Session?>(null) }
    var showRenameDialog by remember { mutableStateOf<Session?>(null) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val filterDirectory = remember(uiState.projects, filterProjectId) {
        filterProjectId?.let { filter ->
            uiState.projects.find { it.id == filter }?.worktree ?: filter
        }
    }

    val baseDisplayedSessions = remember(uiState.sessions, filterDirectory) {
        if (filterDirectory != null) {
            uiState.sessions.filter { it.session.directory == filterDirectory }
        } else {
            uiState.sessions
        }
    }
    val displayedSessions = if (!uiState.isSearchActive) {
        baseDisplayedSessions
    } else if (uiState.serverSearchQuery == null) {
        baseDisplayedSessions.filter { it.session.title.contains(uiState.searchQuery.trim(), ignoreCase = true) }
    } else {
        uiState.displayedSearchResults
    }

    val filteredProject = remember(uiState.projects, filterDirectory) {
        filterDirectory?.let { directory -> uiState.projects.find { it.worktree == directory } }
    }
    val projectName = filteredProject?.name ?: filterDirectory?.substringAfterLast("/")

    LaunchedEffect(filterDirectory) {
        viewModel.updateSearchDirectory(filterDirectory)
    }

    LaunchedEffect(autoCreateSession, autoCreateSessionTitle, autoCreateSessionDirectory) {
        if (autoCreateSession) {
            onAutoCreateSessionConsumed()
            onCreateSessionInWorkspace(autoCreateSessionTitle, autoCreateSessionDirectory)
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
                        onClick = { showSearch = !showSearch },
                        modifier = Modifier.size(Sizing.iconButtonMd).testTag("sessions_search_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isSearchActive) Icons.Default.SearchOff else Icons.Default.Search,
                            contentDescription = stringResource(R.string.cd_search),
                            tint = if (uiState.isSearchActive) theme.accent else LocalContentColor.current,
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
                // During search, flatten to matching sessions (tree roots only would
                // hide matching child sessions whose parent is filtered out).
                val sessionTree = remember(displayedSessions, uiState.searchQuery) {
                    val q = uiState.searchQuery.trim()
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
                    val searchActive = uiState.isSearchActive
                    if (showSearch || uiState.isSearchActive) {
                        stickyHeader(key = "session_search") {
                            SessionSearchField(
                                query = uiState.searchQuery,
                                status = uiState.searchStatus,
                                onQueryChange = { query -> viewModel.updateSearchQuery(query, filterDirectory) },
                                onClose = {
                                    showSearch = false
                                    viewModel.updateSearchQuery("", filterDirectory)
                                    keyboardController?.hide()
                                },
                            )
                        }
                    }

                    if (searchActive && sessionTree.isEmpty()) {
                        item(key = "no_match") {
                            Text(
                                text = if (uiState.isSearching || uiState.serverSearchQuery != uiState.searchQuery.trim()) {
                                    stringResource(R.string.sessions_search_waiting)
                                } else {
                                    stringResource(R.string.sessions_search_no_match)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.textMuted,
                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.lg)
                            )
                        }
                    } else if (displayedSessions.isEmpty()) {
                        item(key = "empty_hint") {
                            Column(
                                modifier = Modifier
                                    .testTag("sessions_empty_state")
                                    .padding(horizontal = Spacing.md, vertical = Spacing.lg),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                Text(
                                    text = stringResource(R.string.sessions_empty_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = theme.text,
                                )
                                Text(
                                    text = stringResource(R.string.sessions_empty_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = theme.textMuted,
                                )
                                filterDirectory?.let { directory ->
                                    Text(
                                        text = directory,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = theme.textMuted,
                                    )
                                }
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
                                expandedSessionIds = uiState.expandedSessionIds,
                                sessionStatuses = uiState.sessionStatuses,
                                sessionPresences = uiState.sessionPresences,
                                forkingSessionIds = uiState.forkingSessionIds,
                                isSessionForkPending = isSessionForkPending ||
                                    uiState.forkingSessionIds.isNotEmpty(),
                                showProjectChip = filterProjectId == null,
                                onSessionClick = { session -> onSessionClick(session.id, session.directory) },
                                onDeleteSession = { showDeleteDialog = it },
                                onRenameSession = { showRenameDialog = it },
                                onForkSession = { session ->
                                    onForkSessionInWorkspace(session.id, session.directory)
                                },
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
                                    viewModel.toggleSessionExpanded(id)
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
    status: SessionSearchStatus?,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val theme = LocalOpenCodeTheme.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
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
                    .focusRequester(focusRequester)
                    .testTag("sessions_search_field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = theme.accent,
                    focusedTextColor = theme.text,
                    unfocusedTextColor = theme.text,
                ),
            )
            status?.let { searchStatus ->
                val visual = searchStatusVisual(searchStatus, theme)
                Icon(
                    imageVector = visual.icon,
                    contentDescription = visual.contentDescription,
                    tint = visual.color,
                    modifier = Modifier.size(Sizing.iconMd),
                )
            }
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

@Composable
private fun searchStatusVisual(
    status: SessionSearchStatus,
    theme: dev.blazelight.p4oc.ui.theme.opencode.OpenCodeTheme,
): SearchStatusVisual = when (status) {
    SessionSearchStatus.Searching -> SearchStatusVisual(
        icon = Icons.Default.Search,
        contentDescription = stringResource(R.string.sessions_search_status_searching),
        color = theme.accent,
    )
    SessionSearchStatus.Refining -> SearchStatusVisual(
        icon = Icons.Default.FilterList,
        contentDescription = stringResource(R.string.sessions_search_status_refining),
        color = theme.warning,
    )
    SessionSearchStatus.Current -> SearchStatusVisual(
        icon = Icons.Default.CheckCircle,
        contentDescription = stringResource(R.string.sessions_search_status_current),
        color = theme.success,
    )
    SessionSearchStatus.Failed -> SearchStatusVisual(
        icon = Icons.Default.ErrorOutline,
        contentDescription = stringResource(R.string.sessions_search_status_failed),
        color = theme.error,
    )
}

private data class SearchStatusVisual(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val contentDescription: String,
    val color: Color,
)

private fun buildSessionTree(sessions: List<SessionWithProject>): List<SessionNode> {
    val sessionIds = sessions.map { it.session.id }.toSet()
    val childrenByParent = sessions
        .mapNotNull { swp -> swp.session.parentID?.let { parentId -> parentId to swp } }
        .groupBy({ it.first }, { it.second })

    val included = mutableSetOf<String>()
    fun buildNode(sessionWithProject: SessionWithProject, ancestors: Set<String>): SessionNode {
        val id = sessionWithProject.session.id
        included += id
        val children = childrenByParent[id]
            .orEmpty()
            .filterNot { it.session.id in ancestors || it.session.id == id }
            .map { buildNode(it, ancestors + id) }
        return SessionNode(sessionWithProject, children)
    }

    val roots = sessions.filter { it.session.parentID == null || it.session.parentID !in sessionIds }
        .map { buildNode(it, emptySet()) }
        .toMutableList()
    sessions.filterNot { it.session.id in included }
        .forEach { roots += buildNode(it, emptySet()) }
    return roots
}

@Composable
@Suppress("FunctionNaming", "LongParameterList", "LongMethod")
private fun SessionTreeNode(
    node: SessionNode,
    depth: Int,
    expandedSessionIds: Set<String>,
    sessionStatuses: Map<String, SessionStatus>,
    sessionPresences: Map<String, SessionPresence>,
    forkingSessionIds: Set<String>,
    isSessionForkPending: Boolean,
    showProjectChip: Boolean,
    onSessionClick: (Session) -> Unit,
    onDeleteSession: (Session) -> Unit,
    onRenameSession: (Session) -> Unit,
    onForkSession: (Session) -> Unit,
    onShareSession: (Session) -> Unit,
    onViewChanges: (Session) -> Unit,
    onSummarizeSession: (Session) -> Unit,
    onProjectClick: (String) -> Unit,
    onToggleExpand: (String) -> Unit
) {
    val swp = node.sessionWithProject
    val session = swp.session
    val isExpanded = session.id in expandedSessionIds
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
            isForking = session.id in forkingSessionIds,
            isSessionForkPending = isSessionForkPending,
            isShared = session.shareUrl != null,
            onClick = { onSessionClick(session) },
            onDelete = { onDeleteSession(session) },
            onRename = { onRenameSession(session) },
            onFork = { onForkSession(session) },
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
                        expandedSessionIds = expandedSessionIds,
                        sessionStatuses = sessionStatuses,
                        sessionPresences = sessionPresences,
                        forkingSessionIds = forkingSessionIds,
                        isSessionForkPending = isSessionForkPending,
                        showProjectChip = showProjectChip,
                        onSessionClick = onSessionClick,
                        onDeleteSession = onDeleteSession,
                        onRenameSession = onRenameSession,
                        onForkSession = onForkSession,
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
@Suppress("FunctionNaming", "LongParameterList", "LongMethod", "CyclomaticComplexMethod")
private fun SessionCard(
    session: Session,
    projectId: String?,
    projectName: String?,
    showProjectChip: Boolean,
    status: SessionStatus?,
    presence: SessionPresence,
    isForking: Boolean,
    isSessionForkPending: Boolean,
    isShared: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onFork: () -> Unit,
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
    val workspaceIdentity = projectName?.takeIf { it.isNotBlank() }
        ?: session.directory.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.sessions_no_project_context)
    val resumeIdentity = stringResource(
        R.string.sessions_resume_identity,
        session.title,
        workspaceIdentity,
        formatDateTime(session.updatedAt),
    )
    val forkingDescription = stringResource(R.string.sessions_forking)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session_resume_${session.id}")
            .semantics { contentDescription = resumeIdentity }
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showContextMenu = true },
                onLongClickLabel = stringResource(R.string.sessions_open_actions),
                role = Role.Button,
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
                            text = "[${stringResource(R.string.sessions_sub_count, childCount)}]",
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

            if (isForking) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(Sizing.iconSm)
                        .semantics {
                            contentDescription = forkingDescription
                        },
                    color = theme.accent,
                    strokeWidth = Sizing.strokeThin,
                )
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
            text = stringResource(R.string.sessions_fork),
            onClick = {
                showContextMenu = false
                onFork()
            },
            leadingIcon = Icons.Default.ContentCopy,
            enabled = !isSessionForkPending && !isForking,
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
    val openDescription = stringResource(R.string.sessions_open_project, projectName)
    val modifier = Modifier
        .padding(start = Spacing.md)
        .widthIn(max = Sizing.chipMaxWidth)
        .testTag("session_directory_chip_$projectName")
        .semantics { contentDescription = openDescription }
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

private fun formatDateTime(epochMillis: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.month.ordinal + 1}/${local.day}/${local.year} ${local.hour}:${local.minute.toString().padStart(
        2,
        '0'
    )}"
}
