package dev.blazelight.p4oc.ui.tabs

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.datastore.VisualSettings
import dev.blazelight.p4oc.core.network.ServerConnectionRegistry
import dev.blazelight.p4oc.domain.model.SessionConnectionState
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.server.WorkspaceKey
import dev.blazelight.p4oc.ui.navigation.Screen
import dev.blazelight.p4oc.ui.screens.chat.ChatScreen
import dev.blazelight.p4oc.ui.screens.diff.DiffViewerScreen
import dev.blazelight.p4oc.ui.screens.diff.SessionDiffScreen
import dev.blazelight.p4oc.ui.screens.files.FileExplorerScreen
import dev.blazelight.p4oc.ui.screens.files.FileViewerScreen
import dev.blazelight.p4oc.ui.screens.files.FilesViewModel
import dev.blazelight.p4oc.ui.screens.home.HomeActions
import dev.blazelight.p4oc.ui.screens.home.HomeSummaryBuilder
import dev.blazelight.p4oc.ui.screens.home.HomeSummaryInput
import dev.blazelight.p4oc.ui.screens.home.homeScreen
import dev.blazelight.p4oc.ui.screens.projects.ProjectsScreen
import dev.blazelight.p4oc.ui.screens.sessions.SessionListScreen
import dev.blazelight.p4oc.ui.screens.sessions.SessionListViewModel
import dev.blazelight.p4oc.ui.screens.settings.*
import dev.blazelight.p4oc.ui.screens.terminal.TerminalScreen
import dev.blazelight.p4oc.ui.workspace.WorkspaceRepositoryOwner
import dev.blazelight.p4oc.ui.workspace.WorkspaceViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

private const val ANIMATION_DURATION = 300
private const val WORKSPACE_ROUTE_ARG_TAB_ID = "tabId"
private const val WORKSPACE_ROUTE_ARG_REVISION = "workspaceRevision"
private const val WORKSPACE_GRAPH_ROUTE = "workspace/{$WORKSPACE_ROUTE_ARG_TAB_ID}/{$WORKSPACE_ROUTE_ARG_REVISION}"

private data class PendingSessionCreate(
    val title: String?,
    val directory: String?,
)

internal data class PendingSessionFork(
    val sourceSessionId: String,
    val directory: String?,
)

internal data class PendingSessionForkEnqueueDecision(
    val pending: PendingSessionFork,
    val accepted: Boolean,
)

internal fun decidePendingSessionForkEnqueue(
    current: PendingSessionFork?,
    requested: PendingSessionFork,
): PendingSessionForkEnqueueDecision = if (current == null) {
    PendingSessionForkEnqueueDecision(pending = requested, accepted = true)
} else {
    PendingSessionForkEnqueueDecision(pending = current, accepted = false)
}

internal data class PendingSessionForkStep(
    val remaining: PendingSessionFork?,
    val sourceSessionIdToFork: String?,
)

internal fun pendingSessionForkStep(
    pending: PendingSessionFork?,
    currentDirectory: String?,
): PendingSessionForkStep = when {
    pending == null -> PendingSessionForkStep(remaining = null, sourceSessionIdToFork = null)
    pending.directory != currentDirectory -> PendingSessionForkStep(
        remaining = pending,
        sourceSessionIdToFork = null,
    )
    else -> PendingSessionForkStep(
        remaining = null,
        sourceSessionIdToFork = pending.sourceSessionId,
    )
}

internal fun workspaceGraphRoute(tabId: String, revision: Int): String = "workspace/$tabId/$revision"

internal fun filesViewModelKey(
    tabId: String,
    workspaceKey: WorkspaceKey,
    generation: ServerGeneration,
    keySuffix: String,
): String = "$tabId:$workspaceKey:${generation.value}:$keySuffix"

/**
 * Per-tab navigation host.
 * Each tab has its own NavHost with independent navigation stack.
 * The [startRoute] determines the initial screen — defaults to Sessions list,
 * but can be a filled route like "terminal/abc123" or "files" for dedicated tabs.
 */
@Composable
fun TabNavHost(
    navController: NavHostController,
    tabManager: TabManager,
    tabId: String,
    onDisconnect: () -> Unit,
    onNewFilesTab: () -> Unit = {},
    onNewTerminalTab: () -> Unit = {},
    onCloseTab: () -> Unit = {},
    isActiveTab: Boolean = true,
    startRoute: String = Screen.Sessions.route,
    workspaceOwner: WorkspaceRepositoryOwner,
    serverRef: ServerRef,
    onConnectionStateChanged: ((SessionConnectionState?) -> Unit)? = null,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    // Read visual settings for sub-agent tab behavior
    val settingsDataStore: SettingsDataStore = koinInject()
    val visualSettings by settingsDataStore.visualSettings.collectAsStateWithLifecycle(
        initialValue = VisualSettings(),
    )
    val savedServers by settingsDataStore.savedServers.collectAsStateWithLifecycle(
        initialValue = emptyList(),
    )
    val serverConnectionRegistry: ServerConnectionRegistry = koinInject()
    val homeConnectionStates = savedServers.associate { savedServer ->
        val savedServerRef = ServerRef.fromEndpointKey(savedServer.endpointKey, savedServer.displayName)
        val state by serverConnectionRegistry.connectionState(savedServerRef).collectAsStateWithLifecycle()
        savedServer.endpointKey to state
    }
    val openSubAgentInNewTab = visualSettings.openSubAgentInNewTab
    val tabs by tabManager.tabs.collectAsStateWithLifecycle()
    val tab = tabs.firstOrNull { it.id == tabId }
    val workspaceRevision = tab?.workspaceRevision ?: 0

    val workspaceRoute = remember(tabId, workspaceRevision) { workspaceGraphRoute(tabId, workspaceRevision) }
    var pendingRoute by remember(tabId) { mutableStateOf<String?>(null) }
    var pendingSessionCreate by remember(tabId) { mutableStateOf<PendingSessionCreate?>(null) }
    var pendingSessionFork by remember(tabId) { mutableStateOf<PendingSessionFork?>(null) }

    LaunchedEffect(workspaceRevision, pendingRoute, pendingSessionCreate) {
        pendingRoute?.let { route ->
            pendingRoute = null
            navController.navigate(route)
        }
    }

    // Double-back-to-close when at the root of any tab.
    // Dedicated tabs (terminal, files, chat): closes the tab.
    // Sessions tab: exits the app.
    var backPressedAt by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
    val forkAlreadyPendingMessage = stringResource(R.string.sessions_fork_already_pending)
    val isDedicatedTab = startRoute != Screen.Sessions.route

    BackHandler(enabled = navController.previousBackStackEntry == null) {
        val now = System.currentTimeMillis()
        if (now - backPressedAt < 2000L) {
            if (isDedicatedTab) {
                onCloseTab()
            } else {
                (context as? android.app.Activity)?.finish()
            }
        } else {
            backPressedAt = now
            val message = if (isDedicatedTab) "Press back again to close tab" else "Press back again to exit"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    NavHost(
        navController = navController,
        startDestination = workspaceRoute,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(ANIMATION_DURATION)
            ) + fadeIn(animationSpec = tween(ANIMATION_DURATION))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 3 },
                animationSpec = tween(ANIMATION_DURATION)
            ) + fadeOut(animationSpec = tween(ANIMATION_DURATION))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 3 },
                animationSpec = tween(ANIMATION_DURATION)
            ) + fadeIn(animationSpec = tween(ANIMATION_DURATION))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(ANIMATION_DURATION)
            ) + fadeOut(animationSpec = tween(ANIMATION_DURATION))
        }
    ) {
        navigation(
            startDestination = startRoute,
            route = WORKSPACE_GRAPH_ROUTE,
            arguments = listOf(
                navArgument(WORKSPACE_ROUTE_ARG_TAB_ID) { type = NavType.StringType },
                navArgument(WORKSPACE_ROUTE_ARG_REVISION) { type = NavType.IntType },
            )
        ) {
            composable(Screen.Home.route) {
                val homeCoroutineScope = rememberCoroutineScope()
                homeScreen(
                    summary = HomeSummaryBuilder.build(
                        HomeSummaryInput(
                            savedServers = savedServers,
                            connectionStates = homeConnectionStates,
                            tabs = tabs,
                        ),
                    ),
                    actions = HomeActions(
                        onBrowseSessions = { navController.navigate(Screen.Sessions.route) },
                        onOpenFiles = { onNewFilesTab() },
                        onOpenTerminal = { onNewTerminalTab() },
                        onChooseTarget = onNewFilesTab,
                        onRefresh = {
                            homeCoroutineScope.launch { workspaceOwner.sessionRepository.refresh() }
                        },
                        onSettings = { navController.navigate(Screen.Settings.route) },
                    ),
                )
            }

            // Sessions list (start destination for new tabs)
            composable(Screen.Sessions.route) { backStackEntry ->
                val workspaceViewModel = TouchWorkspaceViewModel(
                    backStackEntry,
                    navController,
                    workspaceRoute,
                    workspaceOwner,
                    backStackEntry.destination.route
                )
                val sessionListViewModel = sessionListViewModelForRoute(
                    owner = backStackEntry,
                    workspaceViewModel = workspaceViewModel,
                    keySuffix = "sessions",
                )
                LaunchedEffect(workspaceViewModel, sessionListViewModel, pendingSessionFork) {
                    val step = pendingSessionForkStep(
                        pending = pendingSessionFork,
                        currentDirectory = workspaceViewModel.workspace.directory,
                    )
                    val sourceSessionId = step.sourceSessionIdToFork ?: return@LaunchedEffect
                    pendingSessionFork = step.remaining
                    sessionListViewModel.forkSession(sourceSessionId)
                }
                SessionListScreen(
                    viewModel = sessionListViewModel,
                    isSessionForkPending = pendingSessionFork != null,
                    onSessionClick = { sessionId, directory ->
                        val selection = directory.toNavigationWorkspaceSelection()
                            ?: return@SessionListScreen
                        val existingTab = tabManager.findSessionTab(
                            serverRef = serverRef,
                            workspaceKey = selection.workspaceKey,
                            sessionId = sessionId,
                        )
                        if (existingTab != null && existingTab.id != tabId) {
                            tabManager.focusTab(existingTab.id)
                        } else {
                            val chatRoute = Screen.Chat.createRoute(sessionId)
                            if (selection.directory != workspaceOwner.workspace.directory) {
                                pendingRoute = chatRoute
                                tabManager.updateTabWorkspace(tabId, selection.workspaceKey)
                            } else {
                                navController.navigate(chatRoute)
                            }
                        }
                    },
                    onNewSession = { sessionId, directory ->
                        val selection = directory.toNavigationWorkspaceSelection()
                            ?: return@SessionListScreen
                        val chatRoute = Screen.Chat.createRoute(sessionId, focusInput = true)
                        if (selection.directory != workspaceOwner.workspace.directory) {
                            pendingRoute = chatRoute
                            tabManager.updateTabWorkspace(tabId, selection.workspaceKey)
                        } else {
                            navController.navigate(chatRoute)
                        }
                    },
                    onSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onProjects = {
                        navController.navigate(Screen.Projects.route)
                    },
                    onProjectClick = { projectId ->
                        navController.navigate(Screen.SessionsFiltered.createRoute(projectId))
                    },
                    onViewChanges = { sessionId ->
                        navController.navigate(Screen.SessionDiff.createRoute(sessionId))
                    },
                    onForkSessionInWorkspace = { sourceSessionId, directory ->
                        val selection = directory.toNavigationWorkspaceSelection()
                            ?: return@SessionListScreen
                        val decision = decidePendingSessionForkEnqueue(
                            current = pendingSessionFork,
                            requested = PendingSessionFork(sourceSessionId, selection.directory),
                        )
                        pendingSessionFork = decision.pending
                        if (!decision.accepted) {
                            Toast.makeText(
                                context,
                                forkAlreadyPendingMessage,
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@SessionListScreen
                        }
                        if (selection.directory != workspaceOwner.workspace.directory) {
                            tabManager.updateTabWorkspace(tabId, selection.workspaceKey)
                        }
                    },
                    onCreateSessionInWorkspace = { title, directory ->
                        val selection = directory.toNavigationWorkspaceSelection()
                            ?: return@SessionListScreen
                        pendingSessionCreate = PendingSessionCreate(title, selection.directory)
                        if (selection.directory != workspaceOwner.workspace.directory) {
                            tabManager.updateTabWorkspace(tabId, selection.workspaceKey)
                        }
                    },
                    autoCreateSession = pendingSessionCreate != null &&
                        pendingSessionCreate?.directory == workspaceOwner.workspace.directory,
                    autoCreateSessionTitle = pendingSessionCreate?.title,
                    autoCreateSessionDirectory = pendingSessionCreate?.directory,
                    onAutoCreateSessionConsumed = { pendingSessionCreate = null }
                )
            }

            // Filtered sessions by project
            composable(
                route = Screen.SessionsFiltered.route,
                arguments = listOf(
                    navArgument(Screen.SessionsFiltered.ARG_PROJECT_ID) {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val workspaceViewModel = TouchWorkspaceViewModel(
                    backStackEntry,
                    navController,
                    workspaceRoute,
                    workspaceOwner,
                    backStackEntry.destination.route
                )
                val projectId = backStackEntry.arguments?.getString(Screen.SessionsFiltered.ARG_PROJECT_ID) ?: ""
                val sessionListViewModel = sessionListViewModelForRoute(
                    owner = backStackEntry,
                    workspaceViewModel = workspaceViewModel,
                    keySuffix = "sessions-filtered-$projectId",
                )
                LaunchedEffect(workspaceViewModel, sessionListViewModel, pendingSessionFork) {
                    val step = pendingSessionForkStep(
                        pending = pendingSessionFork,
                        currentDirectory = workspaceViewModel.workspace.directory,
                    )
                    val sourceSessionId = step.sourceSessionIdToFork ?: return@LaunchedEffect
                    pendingSessionFork = step.remaining
                    sessionListViewModel.forkSession(sourceSessionId)
                }
                SessionListScreen(
                    viewModel = sessionListViewModel,
                    filterProjectId = projectId,
                    isSessionForkPending = pendingSessionFork != null,
                    onSessionClick = { sessionId, directory ->
                        val selection = directory.toNavigationWorkspaceSelection()
                            ?: return@SessionListScreen
                        val existingTab = tabManager.findSessionTab(
                            serverRef = serverRef,
                            workspaceKey = selection.workspaceKey,
                            sessionId = sessionId,
                        )
                        if (existingTab != null && existingTab.id != tabId) {
                            tabManager.focusTab(existingTab.id)
                        } else {
                            val chatRoute = Screen.Chat.createRoute(sessionId)
                            if (selection.directory != workspaceOwner.workspace.directory) {
                                pendingRoute = chatRoute
                                tabManager.updateTabWorkspace(tabId, selection.workspaceKey)
                            } else {
                                navController.navigate(chatRoute)
                            }
                        }
                    },
                    onNewSession = { sessionId, directory ->
                        val selection = directory.toNavigationWorkspaceSelection()
                            ?: return@SessionListScreen
                        val chatRoute = Screen.Chat.createRoute(sessionId, focusInput = true)
                        if (selection.directory != workspaceOwner.workspace.directory) {
                            pendingRoute = chatRoute
                            tabManager.updateTabWorkspace(tabId, selection.workspaceKey)
                        } else {
                            navController.navigate(chatRoute)
                        }
                    },
                    onSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onProjects = {
                        navController.navigate(Screen.Projects.route)
                    },
                    onProjectClick = { pid ->
                        navController.navigate(Screen.SessionsFiltered.createRoute(pid))
                    },
                    onViewChanges = { sessionId ->
                        navController.navigate(Screen.SessionDiff.createRoute(sessionId))
                    },
                    onForkSessionInWorkspace = { sourceSessionId, directory ->
                        val selection = directory.toNavigationWorkspaceSelection()
                            ?: return@SessionListScreen
                        val decision = decidePendingSessionForkEnqueue(
                            current = pendingSessionFork,
                            requested = PendingSessionFork(sourceSessionId, selection.directory),
                        )
                        pendingSessionFork = decision.pending
                        if (!decision.accepted) {
                            Toast.makeText(
                                context,
                                forkAlreadyPendingMessage,
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@SessionListScreen
                        }
                        if (selection.directory != workspaceOwner.workspace.directory) {
                            pendingRoute = Screen.SessionsFiltered.createRoute(projectId)
                            tabManager.updateTabWorkspace(tabId, selection.workspaceKey)
                        }
                    },
                    onCreateSessionInWorkspace = { title, directory ->
                        val selection = directory.toNavigationWorkspaceSelection()
                            ?: return@SessionListScreen
                        pendingSessionCreate = PendingSessionCreate(title, selection.directory)
                        if (selection.directory != workspaceOwner.workspace.directory) {
                            pendingRoute = Screen.SessionsFiltered.createRoute(projectId)
                            tabManager.updateTabWorkspace(tabId, selection.workspaceKey)
                        }
                    },
                    autoCreateSession = pendingSessionCreate != null &&
                        pendingSessionCreate?.directory == workspaceOwner.workspace.directory,
                    autoCreateSessionTitle = pendingSessionCreate?.title,
                    autoCreateSessionDirectory = pendingSessionCreate?.directory,
                    onAutoCreateSessionConsumed = { pendingSessionCreate = null },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            // Chat screen
            composable(
                route = Screen.Chat.route,
                arguments = listOf(
                    navArgument(Screen.Chat.ARG_SESSION_ID) { type = NavType.StringType },
                    navArgument(Screen.Chat.ARG_FOCUS_INPUT) {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                )
            ) { backStackEntry ->
                val workspaceViewModel = TouchWorkspaceViewModel(
                    backStackEntry,
                    navController,
                    workspaceRoute,
                    workspaceOwner,
                    backStackEntry.destination.route
                )
                ChatScreen(
                    viewModel = koinViewModel(
                        parameters = {
                            parametersOf(workspaceOwner)
                        },
                    ),
                    onNavigateBack = {
                        // Clear session binding when leaving chat
                        tabManager.clearTabSession(tabId)
                        onConnectionStateChanged?.invoke(null)
                        if (!navController.popBackStack()) {
                            onCloseTab()
                        }
                    },
                    onOpenTerminal = onNewTerminalTab,
                    onOpenFiles = onNewFilesTab,
                    onViewSessionDiff = { sessionId ->
                        navController.navigate(Screen.SessionDiff.createRoute(sessionId))
                    },
                    onOpenSubSession = { subSessionId ->
                        // Check if sub-session is already open in another tab
                        val existingTab = tabManager.findSessionTab(
                            serverRef = serverRef,
                            workspaceKey = workspaceOwner.workspace.key,
                            sessionId = subSessionId,
                        )
                        if (existingTab != null && existingTab.id != tabId) {
                            // Focus the existing tab
                            tabManager.focusTab(existingTab.id)
                        } else if (openSubAgentInNewTab) {
                            // Open in a new tab (default), inheriting the source tab's workspace
                            tabManager.createTab(
                                startRoute = Screen.Chat.createRoute(subSessionId),
                                workspaceKey = workspaceOwner.workspace.key,
                                serverRef = serverRef,
                                focus = true,
                            )
                        } else {
                            // Reuse this tab when sub-agent tab splitting is disabled.
                            navController.navigate(Screen.Chat.createRoute(subSessionId))
                        }
                    },
                    onProviderAuthRequired = {
                        navController.navigate(Screen.ProviderConfig.route)
                    },
                    onSessionLoaded = { sessionId, sessionTitle ->
                        // Update tab's session binding
                        tabManager.updateTabSession(tabId, sessionId, sessionTitle)
                    },
                    onConnectionStateChanged = onConnectionStateChanged,
                    isActiveTab = isActiveTab,
                    requestInitialInputFocus = backStackEntry.arguments
                        ?.getBoolean(Screen.Chat.ARG_FOCUS_INPUT)
                        ?: false,
                )
            }

            // Projects screen
            composable(Screen.Projects.route) { backStackEntry ->
                TouchWorkspaceViewModel(
                    backStackEntry,
                    navController,
                    workspaceRoute,
                    workspaceOwner,
                    backStackEntry.destination.route
                )
                ProjectsScreen(
                    workspaceClient = workspaceOwner.workspaceClient,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onProjectClick = { projectId, worktree ->
                        val filteredRoute = Screen.SessionsFiltered.createRoute(projectId)
                        if (worktree != workspaceOwner.workspace.directory) {
                            pendingRoute = filteredRoute
                            tabManager.updateTabWorkspace(tabId, WorkspaceKey.Directory(worktree))
                        } else {
                            navController.navigate(filteredRoute)
                        }
                    }
                )
            }

            // Terminal screen (per-PTY, each tab has its own)
            composable(
                route = Screen.Terminal.route,
                arguments = listOf(
                    navArgument(Screen.Terminal.ARG_PTY_ID) { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val routePtyId = requireNotNull(backStackEntry.arguments?.getString(Screen.Terminal.ARG_PTY_ID))
                TouchWorkspaceViewModel(
                    backStackEntry,
                    navController,
                    workspaceRoute,
                    workspaceOwner,
                    backStackEntry.destination.route
                )
                TerminalScreen(
                    viewModel = koinViewModel(
                        key = "${workspaceOwner.tabId}:${workspaceOwner.generation.value}:$routePtyId",
                        parameters = { parametersOf(workspaceOwner) },
                    ),
                    onPtyLoaded = { ptyId, ptyTitle ->
                        // Update tab binding with PTY id and title
                        tabManager.updateTabSession(tabId, ptyId, ptyTitle)
                    }
                )
            }

            // Files screen
            composable(Screen.Files.route) { backStackEntry ->
                val workspaceViewModel = TouchWorkspaceViewModel(
                    backStackEntry,
                    navController,
                    workspaceRoute,
                    workspaceOwner,
                    backStackEntry.destination.route
                )
                FileExplorerScreen(
                    viewModel = filesViewModelForRoute(
                        owner = backStackEntry,
                        workspaceViewModel = workspaceViewModel,
                        keySuffix = "files",
                    ),
                    workspaceKey = workspaceOwner.workspace.key,
                    onFileClick = { path ->
                        navController.navigate(Screen.FileViewer.createRoute(path))
                    },
                    onNavigateBack = {
                        if (!navController.popBackStack()) {
                            onCloseTab()
                        }
                    },
                    onSwitchWorkspace = {
                        navController.navigate(Screen.Projects.route)
                    }
                )
            }

            // File viewer
            composable(
                route = Screen.FileViewer.route,
                arguments = listOf(
                    navArgument(Screen.FileViewer.ARG_PATH) { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val workspaceViewModel = TouchWorkspaceViewModel(
                    backStackEntry,
                    navController,
                    workspaceRoute,
                    workspaceOwner,
                    backStackEntry.destination.route
                )
                val encodedPath = backStackEntry.arguments?.getString(Screen.FileViewer.ARG_PATH) ?: ""
                FileViewerScreen(
                    path = Uri.decode(encodedPath),
                    viewModel = filesViewModelForRoute(
                        owner = backStackEntry,
                        workspaceViewModel = workspaceViewModel,
                        keySuffix = "file-viewer-$encodedPath",
                    ),
                    onNavigateBack = {
                        if (!navController.popBackStack()) {
                            onCloseTab()
                        }
                    }
                )
            }

            // Diff viewer
            composable(
                route = Screen.DiffViewer.route,
                arguments = listOf(
                    navArgument(Screen.DiffViewer.ARG_CONTENT) { type = NavType.StringType },
                    navArgument(Screen.DiffViewer.ARG_FILE_NAME) {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                TouchWorkspaceViewModel(
                    backStackEntry,
                    navController,
                    workspaceRoute,
                    workspaceOwner,
                    backStackEntry.destination.route
                )
                val encodedContent = backStackEntry.arguments?.getString(Screen.DiffViewer.ARG_CONTENT) ?: ""
                val encodedFileName = backStackEntry.arguments?.getString(Screen.DiffViewer.ARG_FILE_NAME) ?: ""
                DiffViewerScreen(
                    diffContent = Uri.decode(encodedContent),
                    fileName = Uri.decode(encodedFileName).takeIf { it.isNotEmpty() },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.SessionDiff.route,
                arguments = listOf(
                    navArgument(Screen.SessionDiff.ARG_SESSION_ID) { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val workspaceViewModel = TouchWorkspaceViewModel(
                    backStackEntry,
                    navController,
                    workspaceRoute,
                    workspaceOwner,
                    backStackEntry.destination.route
                )
                val sessionId = backStackEntry.arguments?.getString(Screen.SessionDiff.ARG_SESSION_ID).orEmpty()
                SessionDiffScreen(
                    sessionId = sessionId,
                    workspaceClient = workspaceViewModel.workspaceClient,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Settings screens
            composable(Screen.Settings.route) { backStackEntry ->
                TouchWorkspaceViewModel(
                    backStackEntry,
                    navController,
                    workspaceRoute,
                    workspaceOwner,
                    backStackEntry.destination.route
                )
                SettingsScreen(
                    viewModel = koinViewModel(
                        key = "${workspaceOwner.tabId}:${workspaceOwner.generation.value}:settings",
                        parameters = { parametersOf(SettingsConnectionContext.Tab(workspaceOwner)) },
                    ),
                    onNavigateBack = { navController.popBackStack() },
                    onDisconnect = onDisconnect,
                    onProviderConfig = {
                        navController.navigate(Screen.ProviderConfig.route)
                    },
                    onModelControls = {
                        navController.navigate(Screen.ModelControls.route)
                    },
                    onChatSettings = {
                        navController.navigate(Screen.ChatSettings.route)
                    },
                    onVisualSettings = {
                        navController.navigate(Screen.VisualSettings.route)
                    },
                    onAgentsConfig = {
                        navController.navigate(Screen.AgentsConfig.route)
                    },
                    onSkills = {
                        navController.navigate(Screen.Skills.route)
                    },
                    onNotificationSettings = {
                        navController.navigate(Screen.NotificationSettings.route)
                    },
                    onConnectionSettings = {
                        navController.navigate(Screen.ConnectionSettings.route)
                    },
                    onLicenses = {
                        navController.navigate(Screen.Licenses.route)
                    }
                )
            }

            composable(Screen.Licenses.route) { backStackEntry ->
                TouchWorkspaceViewModel(
                    backStackEntry,
                    navController,
                    workspaceRoute,
                    workspaceOwner,
                    backStackEntry.destination.route
                )
                dev.blazelight.p4oc.ui.screens.licenses.LicensesScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.ProviderConfig.route) { backStackEntry ->
                TouchWorkspaceViewModel(
                    backStackEntry,
                    navController,
                    workspaceRoute,
                    workspaceOwner,
                    backStackEntry.destination.route
                )
                ProviderConfigScreen(
                    workspaceOwner = workspaceOwner,
                    viewModel = koinViewModel(
                        key = "${workspaceOwner.tabId}:${workspaceOwner.generation.value}:provider-config",
                        parameters = { parametersOf(workspaceOwner) },
                    ),
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.VisualSettings.route) { backStackEntry ->
                TouchWorkspaceViewModel(
                    backStackEntry,
                    navController,
                    workspaceRoute,
                    workspaceOwner,
                    backStackEntry.destination.route
                )
                VisualSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.ChatSettings.route) { backStackEntry ->
                TouchWorkspaceViewModel(
                    backStackEntry,
                    navController,
                    workspaceRoute,
                    workspaceOwner,
                    backStackEntry.destination.route
                )
                ChatSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.ModelControls.route) { backStackEntry ->
                TouchWorkspaceViewModel(
                    backStackEntry,
                    navController,
                    workspaceRoute,
                    workspaceOwner,
                    backStackEntry.destination.route
                )
                ModelControlsScreen(
                    viewModel = koinViewModel(
                        key = "${workspaceOwner.tabId}:${workspaceOwner.generation.value}:model-controls",
                        parameters = { parametersOf(workspaceOwner.workspaceClient) },
                    ),
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.AgentsConfig.route) { backStackEntry ->
                TouchWorkspaceViewModel(
                    backStackEntry,
                    navController,
                    workspaceRoute,
                    workspaceOwner,
                    backStackEntry.destination.route
                )
                AgentsConfigScreen(
                    viewModel = koinViewModel(
                        key = "${workspaceOwner.tabId}:${workspaceOwner.generation.value}:agents-config",
                        parameters = { parametersOf(workspaceOwner.workspaceClient) },
                    ),
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Skills.route) { backStackEntry ->
                TouchWorkspaceViewModel(
                    backStackEntry,
                    navController,
                    workspaceRoute,
                    workspaceOwner,
                    backStackEntry.destination.route
                )
                SkillsScreen(
                    viewModel = koinViewModel(
                        key = "${workspaceOwner.tabId}:${workspaceOwner.generation.value}:skills",
                        parameters = { parametersOf(workspaceOwner.workspaceClient) },
                    ),
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.NotificationSettings.route) { backStackEntry ->
                TouchWorkspaceViewModel(
                    backStackEntry,
                    navController,
                    workspaceRoute,
                    workspaceOwner,
                    backStackEntry.destination.route
                )
                NotificationSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.ConnectionSettings.route) { backStackEntry ->
                TouchWorkspaceViewModel(
                    backStackEntry,
                    navController,
                    workspaceRoute,
                    workspaceOwner,
                    backStackEntry.destination.route
                )
                ConnectionSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun TouchWorkspaceViewModel(
    owner: NavBackStackEntry,
    navController: NavHostController,
    workspaceRoute: String,
    workspaceOwner: WorkspaceRepositoryOwner,
    destinationRoute: String?,
): WorkspaceViewModel {
    val viewModel = workspaceViewModelForTab(
        navController = navController,
        owner = owner,
        workspaceRoute = workspaceRoute,
        workspaceOwner = workspaceOwner,
    )
    LaunchedEffect(viewModel, destinationRoute) {
        viewModel.touch(destinationRoute)
    }
    return viewModel
}

@Composable
private fun workspaceViewModelForTab(
    navController: NavHostController,
    owner: NavBackStackEntry,
    workspaceRoute: String,
    workspaceOwner: WorkspaceRepositoryOwner,
): WorkspaceViewModel {
    val parentEntry = remember(owner, navController, workspaceRoute) {
        navController.getBackStackEntry(WORKSPACE_GRAPH_ROUTE)
    }
    return koinViewModel(
        viewModelStoreOwner = parentEntry,
        key = "${workspaceOwner.tabId}:${workspaceOwner.workspace.key}:${workspaceOwner.generation.value}",
        parameters = { parametersOf(workspaceOwner) },
    )
}

@Composable
private fun sessionListViewModelForRoute(
    owner: NavBackStackEntry,
    workspaceViewModel: WorkspaceViewModel,
    keySuffix: String,
): SessionListViewModel = koinViewModel(
    viewModelStoreOwner = owner,
    key = "${workspaceViewModel.tabId}:${workspaceViewModel.workspace.key}:$keySuffix",
    parameters = { parametersOf(workspaceViewModel.sessionRepository) },
)

@Composable
private fun filesViewModelForRoute(
    owner: NavBackStackEntry,
    workspaceViewModel: WorkspaceViewModel,
    keySuffix: String,
): FilesViewModel = koinViewModel(
    viewModelStoreOwner = owner,
    key = filesViewModelKey(
        tabId = workspaceViewModel.tabId,
        workspaceKey = workspaceViewModel.workspace.key,
        generation = workspaceViewModel.generation,
        keySuffix = keySuffix,
    ),
    parameters = {
        parametersOf(
            workspaceViewModel.fileRepository,
            workspaceViewModel.workspaceChangesRepository,
            workspaceViewModel.uploadCoordinator,
        )
    },
)

private sealed interface NavigationWorkspaceSelection {
    val workspaceKey: WorkspaceKey
    val directory: String?

    data object NoProjectContext : NavigationWorkspaceSelection {
        override val workspaceKey = WorkspaceKey.Global
        override val directory: String? = null
    }

    data class Directory(override val directory: String) : NavigationWorkspaceSelection {
        override val workspaceKey = WorkspaceKey.Directory(directory)
    }
}

private fun String?.toNavigationWorkspaceSelection(): NavigationWorkspaceSelection? = when {
    this == null -> NavigationWorkspaceSelection.NoProjectContext
    isBlank() -> null
    else -> NavigationWorkspaceSelection.Directory(this)
}
