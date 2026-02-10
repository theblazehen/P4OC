package dev.blazelight.p4oc.ui.tabs

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.blazelight.p4oc.domain.model.SessionConnectionState
import dev.blazelight.p4oc.ui.navigation.Screen
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import org.koin.compose.koinInject

private const val TAG = "MainTabScreen"

/**
 * Main container for the tab-based UI.
 * Shows TabBar at top and active tab's content below.
 */
@Composable
fun MainTabScreen(
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabManager: TabManager = koinInject()
    val theme = LocalOpenCodeTheme.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    
    val tabs by tabManager.tabs.collectAsState()
    val activeTabId by tabManager.activeTabId.collectAsState()
    val showTabWarning by tabManager.showTabWarning.collectAsState()
    
    // State for pending new tab creation
    var pendingNewTab by remember { mutableStateOf(false) }
    var pendingNewTabRoute by remember { mutableStateOf<String?>(null) }
    
    // Create initial tab if needed
    val initialNavController = rememberNavController()
    LaunchedEffect(Unit) {
        if (!tabManager.hasTabs()) {
            val initialTab = TabInstance(TabState(), initialNavController)
            tabManager.registerTab(initialTab, focus = true)
        }
    }
    
    // Build tab titles and icons from current routes
    val tabTitles = remember { mutableStateMapOf<String, String>() }
    val tabIcons = remember { mutableStateMapOf<String, ImageVector>() }
    val tabConnectionStates = remember { mutableStateMapOf<String, SessionConnectionState>() }
    
    // Collect connection states from each tab's TabInstance
    tabs.forEach { tab ->
        val connectionState by tab.connectionState.collectAsState()
        LaunchedEffect(connectionState) {
            if (connectionState != null) {
                tabConnectionStates[tab.id] = connectionState!!
            } else {
                tabConnectionStates.remove(tab.id)
            }
        }
    }
    
    // Update titles/icons for each tab based on current route
    tabs.forEach { tab ->
        val backStackEntry by tab.navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        
        LaunchedEffect(currentRoute, tab.sessionTitle) {
            tabTitles[tab.id] = getTitleForRoute(currentRoute, tab.sessionTitle)
            tabIcons[tab.id] = getIconForRoute(currentRoute)
        }
    }
    
    // Snackbar for tab warning
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(showTabWarning) {
        if (showTabWarning) {
            snackbarHostState.showSnackbar(
                message = "Multiple tabs may affect performance",
                duration = SnackbarDuration.Short
            )
            tabManager.dismissTabWarning()
        }
    }
    
    // Handle pending new tab creation
    if (pendingNewTab) {
        val newNavController = rememberNavController()
        LaunchedEffect(newNavController) {
            val newTab = TabInstance(TabState(), newNavController)
            tabManager.registerTab(newTab, focus = true)
            
            // Navigate to specific route if requested
            pendingNewTabRoute?.let { route ->
                newNavController.navigate(route)
            }
            
            pendingNewTab = false
            pendingNewTabRoute = null
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = theme.background,
        modifier = modifier
    ) { _ ->
        // Don't use paddingValues - child screens handle their own insets
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Tab bar with status bar padding
            TabBar(
                tabs = tabs,
                activeTabId = activeTabId,
                tabTitles = tabTitles,
                tabIcons = tabIcons,
                tabConnectionStates = tabConnectionStates,
                onTabClick = { tabId ->
                    tabManager.focusTab(tabId)
                },
                onTabClose = { tabId ->
                    // If this is the last tab, create a new one first
                    if (tabs.size == 1) {
                        pendingNewTab = true
                        pendingNewTabRoute = null
                    }
                    tabManager.closeTab(tabId, null)
                },
                onAddClick = {
                    // Trigger new tab creation
                    pendingNewTab = true
                    pendingNewTabRoute = null
                },
                modifier = Modifier.statusBarsPadding()
            )
            
            // Pager state for swipe between tabs
            val pagerState = rememberPagerState(
                initialPage = tabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0),
                pageCount = { tabs.size }
            )
            
            // Sync activeTabId -> pager (when tab clicked or closed)
            LaunchedEffect(activeTabId, tabs.size) {
                val index = tabs.indexOfFirst { it.id == activeTabId }
                if (index >= 0 && pagerState.currentPage != index) {
                    pagerState.animateScrollToPage(index)
                }
            }
            
            // Sync pager -> activeTabId (when user swipes)
            LaunchedEffect(pagerState.settledPage) {
                tabs.getOrNull(pagerState.settledPage)?.let { tab ->
                    if (tab.id != activeTabId) {
                        tabManager.focusTab(tab.id)
                    }
                }
            }
            
            // Tab content area with HorizontalPager for swipe between tabs
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                key = { tabs.getOrNull(it)?.id ?: it.toString() },
                beyondViewportPageCount = 0  // Can't use 1 - causes NavController lifetime crash
            ) { pageIndex ->
                tabs.getOrNull(pageIndex)?.let { tab ->
                    val isActive = tab.id == activeTabId
                    TabNavHost(
                        navController = tab.navController,
                        tabManager = tabManager,
                        tabId = tab.id,
                        onDisconnect = onDisconnect,
                        onNewFilesTab = {
                            pendingNewTab = true
                            pendingNewTabRoute = Screen.Files.route
                        },
                        onNewTerminalTab = {
                            pendingNewTab = true
                            pendingNewTabRoute = Screen.Terminal.route
                        },
                        isActiveTab = isActive,
                        onConnectionStateChanged = { state ->
                            tab.updateConnectionState(state)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
