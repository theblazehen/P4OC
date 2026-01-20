package com.pocketcode.ui.navigation

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pocketcode.ui.screens.chat.ChatScreen
import com.pocketcode.ui.screens.diff.DiffViewerScreen
import com.pocketcode.ui.screens.files.FileExplorerScreen
import com.pocketcode.ui.screens.files.FileViewerScreen
import com.pocketcode.ui.screens.git.GitScreen
import com.pocketcode.ui.screens.server.ServerScreen
import com.pocketcode.ui.screens.sessions.SessionListScreen
import com.pocketcode.ui.screens.settings.ProviderConfigScreen
import com.pocketcode.ui.screens.settings.SettingsScreen
import com.pocketcode.ui.screens.settings.VisualSettingsScreen
import com.pocketcode.ui.screens.settings.ModelControlsScreen
import com.pocketcode.ui.screens.settings.AgentsConfigScreen
import com.pocketcode.ui.screens.settings.SkillsScreen
import com.pocketcode.ui.screens.setup.SetupScreen
import com.pocketcode.ui.screens.terminal.TerminalScreen
import com.pocketcode.ui.screens.projects.ProjectsScreen

private const val ANIMATION_DURATION = 300

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
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
        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Server.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Server.route) {
            ServerScreen(
                onConnected = {
                    navController.navigate(Screen.Sessions.route) {
                        popUpTo(Screen.Server.route) { inclusive = true }
                    }
                },
                onSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Sessions.route) {
            SessionListScreen(
                onSessionClick = { sessionId ->
                    navController.navigate(Screen.Chat.createRoute(sessionId))
                },
                onNewSession = { sessionId ->
                    navController.navigate(Screen.Chat.createRoute(sessionId))
                },
                onSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onProjects = {
                    navController.navigate(Screen.Projects.route)
                }
            )
        }

        composable(Screen.Projects.route) {
            ProjectsScreen(
                onNavigateBack = { navController.popBackStack() },
                onProjectClick = { projectId ->
                    // TODO: Filter sessions by project or navigate to project detail
                },
                onGitClick = { navController.navigate(Screen.Git.route) }
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument(Screen.Chat.ARG_SESSION_ID) { type = NavType.StringType }
            )
        ) {
            ChatScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenTerminal = { navController.navigate(Screen.Terminal.route) },
                onOpenFiles = { navController.navigate(Screen.Files.route) },
                onOpenGit = { navController.navigate(Screen.Git.route) }
            )
        }

        composable(Screen.Terminal.route) {
            TerminalScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Files.route) {
            FileExplorerScreen(
                onFileClick = { path ->
                    navController.navigate(Screen.FileViewer.createRoute(path))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.FileViewer.route,
            arguments = listOf(
                navArgument(Screen.FileViewer.ARG_PATH) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString(Screen.FileViewer.ARG_PATH) ?: ""
            FileViewerScreen(
                path = Uri.decode(encodedPath),
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onDisconnect = {
                    navController.navigate(Screen.Server.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
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
                }
            )
        }

        composable(Screen.ProviderConfig.route) {
            ProviderConfigScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.VisualSettings.route) {
            VisualSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.ModelControls.route) {
            ModelControlsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.AgentsConfig.route) {
            AgentsConfigScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Skills.route) {
            SkillsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Git.route) {
            GitScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

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
            val encodedContent = backStackEntry.arguments?.getString(Screen.DiffViewer.ARG_CONTENT) ?: ""
            val encodedFileName = backStackEntry.arguments?.getString(Screen.DiffViewer.ARG_FILE_NAME) ?: ""
            DiffViewerScreen(
                diffContent = Uri.decode(encodedContent),
                fileName = Uri.decode(encodedFileName).takeIf { it.isNotEmpty() },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
