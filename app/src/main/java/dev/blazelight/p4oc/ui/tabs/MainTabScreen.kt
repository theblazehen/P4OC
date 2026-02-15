package dev.blazelight.p4oc.ui.tabs

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.data.remote.dto.CreatePtyRequest
import dev.blazelight.p4oc.domain.model.SessionConnectionState
import dev.blazelight.p4oc.ui.navigation.Screen
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import kotlinx.coroutines.launch
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
    val connectionManager: ConnectionManager = koinInject()
    val coroutineScope = rememberCoroutineScope()
    val theme = LocalOpenCodeTheme.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    
    val tabs by tabManager.tabs.collectAsState()
    val activeTabId by tabManager.activeTabId.collectAsState()
    val showTabWarning by tabManager.showTabWarning.collectAsState()
    
    // State for pending new tab creation
    var pendingNewTab by remember { mutableStateOf(false) }
    var pendingNewTabRoute by remember { mutableStateOf<String?>(null) }
    val pendingTabQueue = remember { mutableStateListOf<String?>() }
    
    // Create initial tab if needed
    val initialNavController = rememberNavController()
    LaunchedEffect(Unit) {
        if (!tabManager.hasTabs()) {
            val initialTab = TabInstance(TabState(), initialNavController)
            tabManager.registerTab(initialTab, focus = true)
        }
    }
    
    // TODO: Load existing PTY sessions as tabs on connect (disabled due to layout conflict)
    // Loading multiple tabs during initial composition causes HorizontalPager layout issues
    // For now, users need to create terminals manually
    // var ptyTabsLoaded by remember { mutableStateOf(false) }
    // LaunchedEffect(Unit) {
    //     if (!ptyTabsLoaded) {
    //         // Wait for UI to stabilize
    //         kotlinx.coroutines.delay(500)
    //         val api = connectionManager.getApi()
    //         if (api != null) {
    //             val result = safeApiCall { api.listPtySessions() }
    //             when (result) {
    //                 is ApiResult.Success -> {
    //                     result.data.forEach { ptyDto ->
    //                         pendingTabQueue.add(Screen.Terminal.createRoute(ptyDto.id))
    //                     }
    //                 }
    //                 is ApiResult.Error -> {
    //                     Log.e(TAG, "Failed to load PTY sessions: ${result.message}")
    //                 }
    //             }
    //         }
    //         ptyTabsLoaded = true
    //     }
    // }
    
    // Process pending tab queue
    LaunchedEffect(pendingTabQueue.size, pendingNewTab) {
        if (!pendingNewTab && pendingTabQueue.isNotEmpty()) {
            val nextRoute = pendingTabQueue.removeAt(0)
            pendingNewTabRoute = nextRoute
            pendingNewTab = true
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
            val newTab = TabInstance(
                state = TabState(),
                navController = newNavController,
                pendingRoute = pendingNewTabRoute
            )
            tabManager.registerTab(newTab, focus = true)
            
            pendingNewTab = false
            pendingNewTabRoute = null
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = theme.background,
        contentWindowInsets = WindowInsets(0),
        modifier = modifier
    ) { _ ->
        // We consume the status bar insets here so child Scaffolds don't double-pad.
        // The tab bar gets the status bar padding, then consumeWindowInsets tells
        // downstream composables that the status bar is already accounted for.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .consumeWindowInsets(WindowInsets.statusBars)
        ) {
            // Tab bar (no longer needs its own statusBarsPadding)
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
                    coroutineScope.launch {
                        // Check if it's a terminal tab and delete the PTY
                        val tab = tabs.find { it.id == tabId }
                        val route = tab?.navController?.currentBackStackEntry?.destination?.route
                        if (route != null && route.startsWith("terminal/")) {
                            // Extract ptyId from route (route is "terminal/{ptyId}")
                            val ptyId = tab.navController.currentBackStackEntry
                                ?.arguments?.getString(Screen.Terminal.ARG_PTY_ID)
                            if (ptyId != null) {
                                val api = connectionManager.getApi()
                                if (api != null) {
                                    val result = safeApiCall { api.deletePtySession(ptyId) }
                                    if (result is ApiResult.Error) {
                                        Log.e(TAG, "Failed to delete PTY $ptyId: ${result.message}")
                                    }
                                }
                            }
                        }
                        
                        // If this is the last tab, create a new one first
                        if (tabs.size == 1) {
                            pendingTabQueue.add(null)
                        }
                        tabManager.closeTab(tabId, null)
                    }
                },
                onAddClick = {
                    pendingTabQueue.add(null)
                },
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
                        pendingRoute = tab.pendingRoute,
                        onNewFilesTab = {
                            pendingTabQueue.add(Screen.Files.route)
                        },
                        onNewTerminalTab = {
                            coroutineScope.launch {
                                val api = connectionManager.getApi() ?: run {
                                    Log.e(TAG, "Cannot create terminal: not connected")
                                    snackbarHostState.showSnackbar("Not connected to server")
                                    return@launch
                                }
                                val result = safeApiCall { api.createPtySession(CreatePtyRequest()) }
                                when (result) {
                                    is ApiResult.Success -> {
                                        val ptyId = result.data.id
                                        pendingTabQueue.add(Screen.Terminal.createRoute(ptyId))
                                    }
                                    is ApiResult.Error -> {
                                        Log.e(TAG, "Failed to create PTY: ${result.message}")
                                        snackbarHostState.showSnackbar("Failed to create terminal: ${result.message}")
                                    }
                                }
                            }
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
