package dev.blazelight.p4oc.ui.tabs

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.datastore.VisualSettings
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.DirectoryManager
import org.koin.compose.koinInject
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import dev.blazelight.p4oc.domain.model.SessionConnectionState
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.workspace.Workspace
import dev.blazelight.p4oc.ui.navigation.Screen
import dev.blazelight.p4oc.ui.workspace.WorkspaceViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import dev.blazelight.p4oc.ui.screens.chat.ChatScreen
import dev.blazelight.p4oc.ui.screens.diff.DiffViewerScreen
import dev.blazelight.p4oc.ui.screens.diff.SessionDiffScreen
import dev.blazelight.p4oc.ui.screens.files.FileExplorerScreen
import dev.blazelight.p4oc.ui.screens.files.FileViewerScreen
import dev.blazelight.p4oc.ui.screens.projects.ProjectsScreen
import dev.blazelight.p4oc.ui.screens.sessions.SessionListScreen
import dev.blazelight.p4oc.ui.screens.sessions.SessionListViewModel
import dev.blazelight.p4oc.ui.screens.settings.*
import dev.blazelight.p4oc.ui.screens.terminal.TerminalScreen

private const val ANIMATION_DURATION = 300
private const val WORKSPACE_ROUTE_ARG_TAB_ID = "tabId"
private const val WORKSPACE_GRAPH_ROUTE = "workspace/{$WORKSPACE_ROUTE_ARG_TAB_ID}"

private fun workspaceGraphRoute(tabId: String): String = "workspace/$tabId"

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
    onConnectionStateChanged: ((SessionConnectionState?) -> Unit)? = null,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    // Read visual settings for sub-agent tab behavior
    val settingsDataStore: SettingsDataStore = koinInject()
    val connectionManager: ConnectionManager = koinInject()
    val directoryManager: DirectoryManager = koinInject()
    val visualSettings by settingsDataStore.visualSettings.collectAsState(initial = VisualSettings())
    val openSubAgentInNewTab = visualSettings.openSubAgentInNewTab

    val baseUrl = connectionManager.currentBaseUrl
    if (baseUrl == null) {
        Box(modifier = modifier)
        return
    }

    val workspace = remember(tabId, startRoute, baseUrl) {
        // TODO(oa-6d53 follow-up): seed this from explicit per-tab workspace state once tab routes
        // carry workspace identity. This bridge snapshots the active server and legacy directory once.
        Workspace(
            server = ServerRef.fromEndpoint(baseUrl),
            directory = initialDirectoryFromRoute(startRoute) ?: directoryManager.getDirectory(),
        )
    }
    val generation = remember(tabId) {
        // TODO(oa-6d53 follow-up): replace placeholder when ConnectionManager exposes server generations.
        ServerGeneration(0L)
    }
    val workspaceRoute = remember(tabId) { workspaceGraphRoute(tabId) }

    // Double-back-to-close when at the root of any tab.
    // Dedicated tabs (terminal, files, chat): closes the tab.
    // Sessions tab: exits the app.
    var backPressedAt by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
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
                navArgument(WORKSPACE_ROUTE_ARG_TAB_ID) { type = NavType.StringType }
            )
        ) {
        // Sessions list (start destination for new tabs)
        composable(Screen.Sessions.route) { backStackEntry ->
            val workspaceViewModel = TouchWorkspaceViewModel(navController, workspaceRoute, tabId, workspace, generation, backStackEntry.destination.route)
            SessionListScreen(
                viewModel = remember(workspaceViewModel) { SessionListViewModel(workspaceViewModel.sessionRepository) },
                onSessionClick = { sessionId, directory ->
                    // Check if session already open in another tab
                    val existingTab = tabManager.findTabBySessionId(sessionId)
                    if (existingTab != null && existingTab.id != tabId) {
                        // Focus existing tab
                        tabManager.focusTab(existingTab.id)
                    } else {
                        // Navigate within this tab
                        navController.navigate(Screen.Chat.createRoute(sessionId, directory))
                    }
                },
                onNewSession = { sessionId, directory ->
                    navController.navigate(Screen.Chat.createRoute(sessionId, directory))
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
                }
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
            val workspaceViewModel = TouchWorkspaceViewModel(navController, workspaceRoute, tabId, workspace, generation, backStackEntry.destination.route)
            val projectId = backStackEntry.arguments?.getString(Screen.SessionsFiltered.ARG_PROJECT_ID) ?: ""
            SessionListScreen(
                viewModel = remember(workspaceViewModel) { SessionListViewModel(workspaceViewModel.sessionRepository) },
                filterProjectId = projectId,
                onSessionClick = { sessionId, directory ->
                    val existingTab = tabManager.findTabBySessionId(sessionId)
                    if (existingTab != null && existingTab.id != tabId) {
                        tabManager.focusTab(existingTab.id)
                    } else {
                        navController.navigate(Screen.Chat.createRoute(sessionId, directory))
                    }
                },
                onNewSession = { sessionId, directory ->
                    navController.navigate(Screen.Chat.createRoute(sessionId, directory))
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
                navArgument(Screen.Chat.ARG_DIRECTORY) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val workspaceViewModel = TouchWorkspaceViewModel(navController, workspaceRoute, tabId, workspace, generation, backStackEntry.destination.route)
            ChatScreen(
                viewModel = koinViewModel(
                    parameters = {
                        parametersOf(
                            workspaceViewModel.workspaceClient,
                            workspaceViewModel.sessionRepository,
                        )
                    },
                ),
                onNavigateBack = { 
                    // Clear session binding when leaving chat
                    tabManager.clearTabSession(tabId)
                    onConnectionStateChanged?.invoke(null)
                    navController.popBackStack() 
                },
                onOpenTerminal = onNewTerminalTab,
                onOpenFiles = onNewFilesTab,
                onViewSessionDiff = { sessionId ->
                    navController.navigate(Screen.SessionDiff.createRoute(sessionId))
                },
                onOpenSubSession = { subSessionId ->
                    // Check if sub-session is already open in another tab
                    val existingTab = tabManager.findTabBySessionId(subSessionId)
                    if (existingTab != null && existingTab.id != tabId) {
                        // Focus the existing tab
                        tabManager.focusTab(existingTab.id)
                    } else if (openSubAgentInNewTab) {
                        // Open in a new tab (default)
                        tabManager.createTab(
                            startRoute = Screen.Chat.createRoute(subSessionId),
                            focus = true
                        )
                    } else {
                        // Same tab (legacy behavior)
                        navController.navigate(Screen.Chat.createRoute(subSessionId))
                    }
                },
                onSessionLoaded = { sessionId, sessionTitle ->
                    // Update tab's session binding
                    tabManager.updateTabSession(tabId, sessionId, sessionTitle)
                },
                onConnectionStateChanged = onConnectionStateChanged,
                isActiveTab = isActiveTab
            )
        }

        // Projects screen
        composable(Screen.Projects.route) { backStackEntry ->
            TouchWorkspaceViewModel(navController, workspaceRoute, tabId, workspace, generation, backStackEntry.destination.route)
            ProjectsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onProjectClick = { projectId, _ ->
                    navController.navigate(Screen.SessionsFiltered.createRoute(projectId))
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
            TouchWorkspaceViewModel(navController, workspaceRoute, tabId, workspace, generation, backStackEntry.destination.route)
            TerminalScreen(
                onPtyLoaded = { ptyId, ptyTitle ->
                    // Update tab binding with PTY id and title
                    tabManager.updateTabSession(tabId, ptyId, ptyTitle)
                }
            )
        }

        // Files screen
        composable(Screen.Files.route) { backStackEntry ->
            TouchWorkspaceViewModel(navController, workspaceRoute, tabId, workspace, generation, backStackEntry.destination.route)
            FileExplorerScreen(
                onFileClick = { path ->
                    navController.navigate(Screen.FileViewer.createRoute(path))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // File viewer
        composable(
            route = Screen.FileViewer.route,
            arguments = listOf(
                navArgument(Screen.FileViewer.ARG_PATH) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            TouchWorkspaceViewModel(navController, workspaceRoute, tabId, workspace, generation, backStackEntry.destination.route)
            val encodedPath = backStackEntry.arguments?.getString(Screen.FileViewer.ARG_PATH) ?: ""
            FileViewerScreen(
                path = Uri.decode(encodedPath),
                onNavigateBack = { navController.popBackStack() }
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
            TouchWorkspaceViewModel(navController, workspaceRoute, tabId, workspace, generation, backStackEntry.destination.route)
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
            TouchWorkspaceViewModel(navController, workspaceRoute, tabId, workspace, generation, backStackEntry.destination.route)
            val sessionId = backStackEntry.arguments?.getString(Screen.SessionDiff.ARG_SESSION_ID).orEmpty()
            SessionDiffScreen(
                sessionId = sessionId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Settings screens
        composable(Screen.Settings.route) { backStackEntry ->
            TouchWorkspaceViewModel(navController, workspaceRoute, tabId, workspace, generation, backStackEntry.destination.route)
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onDisconnect = onDisconnect,
                onProviderConfig = {
                    navController.navigate(Screen.ProviderConfig.route)
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
                }
            )
        }

        composable(Screen.ProviderConfig.route) { backStackEntry ->
            TouchWorkspaceViewModel(navController, workspaceRoute, tabId, workspace, generation, backStackEntry.destination.route)
            ProviderConfigScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.VisualSettings.route) { backStackEntry ->
            TouchWorkspaceViewModel(navController, workspaceRoute, tabId, workspace, generation, backStackEntry.destination.route)
            VisualSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ModelControls.route) { backStackEntry ->
            TouchWorkspaceViewModel(navController, workspaceRoute, tabId, workspace, generation, backStackEntry.destination.route)
            ModelControlsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AgentsConfig.route) { backStackEntry ->
            TouchWorkspaceViewModel(navController, workspaceRoute, tabId, workspace, generation, backStackEntry.destination.route)
            AgentsConfigScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Skills.route) { backStackEntry ->
            TouchWorkspaceViewModel(navController, workspaceRoute, tabId, workspace, generation, backStackEntry.destination.route)
            SkillsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.NotificationSettings.route) { backStackEntry ->
            TouchWorkspaceViewModel(navController, workspaceRoute, tabId, workspace, generation, backStackEntry.destination.route)
            NotificationSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ConnectionSettings.route) { backStackEntry ->
            TouchWorkspaceViewModel(navController, workspaceRoute, tabId, workspace, generation, backStackEntry.destination.route)
            ConnectionSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        }
    }

}

@Composable
private fun TouchWorkspaceViewModel(
    navController: NavHostController,
    workspaceRoute: String,
    tabId: String,
    workspace: Workspace,
    generation: ServerGeneration,
    destinationRoute: String?,
): WorkspaceViewModel {
    val viewModel = workspaceViewModelForTab(
        navController = navController,
        workspaceRoute = workspaceRoute,
        tabId = tabId,
        workspace = workspace,
        generation = generation,
    )
    LaunchedEffect(viewModel, destinationRoute) {
        viewModel.touch(destinationRoute)
    }
    return viewModel
}

@Composable
private fun workspaceViewModelForTab(
    navController: NavHostController,
    workspaceRoute: String,
    tabId: String,
    workspace: Workspace,
    generation: ServerGeneration,
): WorkspaceViewModel {
    val parentEntry = remember(navController, workspaceRoute) {
        navController.getBackStackEntry(WORKSPACE_GRAPH_ROUTE)
    }
    return koinViewModel(
        viewModelStoreOwner = parentEntry,
        parameters = { parametersOf(tabId, workspace, generation) },
    )
}

private fun initialDirectoryFromRoute(route: String): String? =
    runCatching {
        Uri.parse(route).getQueryParameter(Screen.Chat.ARG_DIRECTORY)
    }.getOrNull()?.takeIf { it.isNotBlank() }
