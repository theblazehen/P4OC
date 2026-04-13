package dev.blazelight.p4oc.ui.navigation

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
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

// iOS-style spring specs
private val iosSpringInt = spring<IntOffset>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
private val popSpring = spring<IntOffset>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium)

// Enter from right: slide in + fade + subtle scale up (iOS push)
private val iosPushEnter: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
    slideInHorizontally(animationSpec = iosSpringInt) { it } +
    fadeIn(animationSpec = tween(160))
}
// Exit to left: slide out 25% + fade (content recedes)
private val iosPushExit: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
    slideOutHorizontally(animationSpec = tween<IntOffset>(210)) { -it / 4 } +
    fadeOut(animationSpec = tween(180))
}
// Pop enter from left: slide back + fade
private val iosPopEnter: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
    slideInHorizontally(animationSpec = tween<IntOffset>(210)) { -it / 4 } +
    fadeIn(animationSpec = tween(180))
}
// Pop exit to right: spring slide out
private val iosPopExit: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
    slideOutHorizontally(animationSpec = popSpring) { it } +
    fadeOut(animationSpec = tween(140))
}

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
        enterTransition = iosPushEnter,
        exitTransition = iosPushExit,
        popEnterTransition = iosPopEnter,
        popExitTransition = iosPopExit
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
                onSkills = {},
                onNotificationSettings = {
                    navController.navigate(Screen.NotificationSettings.route)
                },
                onConnectionSettings = {
                    navController.navigate(Screen.ConnectionSettings.route)
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

        composable(Screen.NotificationSettings.route) {
            dev.blazelight.p4oc.ui.screens.settings.NotificationSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ConnectionSettings.route) {
            dev.blazelight.p4oc.ui.screens.settings.ConnectionSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
