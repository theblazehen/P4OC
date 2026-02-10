package dev.blazelight.p4oc.ui.navigation

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.blazelight.p4oc.ui.screens.server.ServerScreen
import dev.blazelight.p4oc.ui.screens.settings.SettingsScreen
import dev.blazelight.p4oc.ui.screens.settings.ProviderConfigScreen
import dev.blazelight.p4oc.ui.screens.settings.VisualSettingsScreen
import dev.blazelight.p4oc.ui.screens.setup.SetupScreen
import dev.blazelight.p4oc.ui.tabs.MainTabScreen

private const val ANIMATION_DURATION = 300

/**
 * Root navigation graph.
 * Handles initial setup/server screens, then hands off to MainTabScreen
 * which manages its own per-tab navigation.
 */
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
                onNavigateToSessions = {
                    navController.navigate(Screen.Sessions.route) {
                        popUpTo(Screen.Server.route) { inclusive = true }
                    }
                },
                onNavigateToProjects = {
                    navController.navigate(Screen.Sessions.route) {
                        popUpTo(Screen.Server.route) { inclusive = true }
                    }
                },
                onSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        // Main tab container - this is where the tab-based UI lives
        composable(Screen.Sessions.route) {
            MainTabScreen(
                onDisconnect = {
                    navController.navigate(Screen.Server.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Settings accessible from Server screen (before connecting)
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
                onAgentsConfig = {},
                onSkills = {}
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
    }
}
