@file:Suppress("DEPRECATION") // LocalLifecycleOwner – platform version until lifecycle-runtime-compose upgrade
package dev.blazelight.p4oc.ui.tabs

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.core.performance.rememberOptimizedPulse
import dev.blazelight.p4oc.data.remote.dto.CreatePtyRequest
import dev.blazelight.p4oc.domain.model.SessionConnectionState
import dev.blazelight.p4oc.core.datastore.ConnectionSettings
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.ui.navigation.Screen
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val TAG = "MainTabScreen"

/**
 * Unified top bar combining tabs, path indicator, and controls in a compact terminal-style header.
 */
@Composable
private fun UnifiedTopBar(
    tabs: List<TabInstance>,
    activeTabId: String?,
    tabTitles: Map<String, String>,
    tabIcons: Map<String, ImageVector>,
    tabConnectionStates: Map<String, SessionConnectionState>,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onAddClick: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    val listState = rememberLazyListState()
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

    // Auto-scroll to active tab
    LaunchedEffect(activeTabId) {
        val activeIndex = tabs.indexOfFirst { it.id == activeTabId }
        if (activeIndex >= 0) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.background)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Top connector
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(12.dp)
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
                    .width(12.dp)
                    .height(1.dp)
                    .background(theme.border.copy(alpha = 0.3f))
            )
        }

        // Main content: tabs row + controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, theme.border.copy(alpha = 0.4f))
                .background(theme.backgroundElement.copy(alpha = 0.08f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tabs section (scrollable)
            LazyRow(
                state = listState,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(items = tabs, key = { it.id }) { tab ->
                    val isActive = tab.id == activeTabId
                    val title = tabTitles[tab.id] ?: "Tab"
                    val connectionState = tabConnectionStates[tab.id]

                    CompactTabIndicator(
                        title = title,
                        connectionState = connectionState,
                        isActive = isActive,
                        onClick = { onTabClick(tab.id) },
                        onClose = { onTabClose(tab.id) }
                    )
                }
            }

            // Right side: Add tab + Settings
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "│",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.border.copy(alpha = 0.3f)
                )

                // Add tab
                Text(
                    text = "[+new]",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.success,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onAddClick)
                )

                Text(
                    text = "│",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.border.copy(alpha = 0.3f)
                )

                // Settings
                Text(
                    text = "[⚙]",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.accent,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onSettings)
                )
            }
        }

        // Bottom connector with path indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(1.dp)
                    .background(theme.border.copy(alpha = 0.3f))
            )
            Text(
                text = "├",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = theme.border.copy(alpha = 0.5f)
            )

            // Path indicator in the middle
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "~",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.accent.copy(alpha = 0.6f)
                )
                Text(
                    text = "/",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.border.copy(alpha = 0.4f)
                )
                Text(
                    text = "sessions",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted.copy(alpha = 0.8f)
                )
                Text(
                    text = "●",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.success.copy(alpha = glowAlpha),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Text(
                text = "┤",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = theme.border.copy(alpha = 0.5f)
            )
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(1.dp)
                    .background(theme.border.copy(alpha = 0.3f))
            )
        }
    }
}

/**
 * Compact tab indicator for the unified top bar.
 */
@Composable
private fun CompactTabIndicator(
    title: String,
    connectionState: SessionConnectionState?,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current

    val pulseAlpha by rememberOptimizedPulse(
        key = "compact_tab_${connectionState?.name}",
        fast = false
    )

    val shouldPulse = connectionState?.shouldPulse == true
    val needsAttention = connectionState?.showsAttentionBadge == true

    val textColor = when {
        needsAttention -> theme.warning
        isActive -> theme.text
        else -> theme.textMuted
    }
    val borderColor = when {
        isActive -> theme.accent.copy(alpha = 0.5f)
        else -> theme.border.copy(alpha = 0.2f)
    }
    val bgColor = when {
        isActive -> theme.backgroundElement.copy(alpha = 0.3f)
        else -> theme.background.copy(alpha = 0f)
    }

    // Compact terminal-style tab
    Row(
        modifier = Modifier
            .height(24.dp)
            .background(bgColor)
            .border(width = 1.dp, color = borderColor)
            .clickable(onClick = onClick, role = Role.Tab)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // State indicator symbol
        val stateSymbol = when (connectionState) {
            SessionConnectionState.BUSY -> "▶"
            SessionConnectionState.AWAITING_INPUT -> "?"
            SessionConnectionState.ERROR -> "!"
            else -> "○"
        }
        val stateColor = when {
            shouldPulse -> theme.accent.copy(alpha = pulseAlpha)
            connectionState == SessionConnectionState.ERROR -> theme.error
            else -> theme.textMuted
        }

        Text(
            text = stateSymbol,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            color = stateColor
        )

        // Tab title
        Text(
            text = title.lowercase(),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 80.dp)
        )

        // Close button
        Text(
            text = "×",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            color = theme.border.copy(alpha = 0.5f),
            modifier = Modifier.clickable(
                onClick = onClose,
                role = Role.Button
            )
        )
    }
}

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
    val settingsDataStore: SettingsDataStore = koinInject()
    val coroutineScope = rememberCoroutineScope()
    val theme = LocalOpenCodeTheme.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val connSettings by settingsDataStore.connectionSettings.collectAsState(initial = ConnectionSettings())
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val tabs by tabManager.tabs.collectAsState()
    val activeTabId by tabManager.activeTabId.collectAsState()
    val showTabWarning by tabManager.showTabWarning.collectAsState()
    val connectionState by connectionManager.connectionState.collectAsState()
    
    var wasEverConnected by remember { mutableStateOf(false) }

    // Navegar fuera cuando el core escale a Disconnected tras agotar reintentos
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) {
            wasEverConnected = true
            return@LaunchedEffect
        }
        if (!wasEverConnected) return@LaunchedEffect
        if (connectionState is ConnectionState.Disconnected) {
            connectionManager.disconnect()
            onDisconnect()
        }
    }
    
    // Create initial tab if needed (no NavController required)
    LaunchedEffect(Unit) {
        if (!tabManager.hasTabs()) {
            val initialTab = TabInstance(TabState())
            tabManager.registerTab(initialTab, focus = true)
        }
    }
    
    // Build tab titles and icons from current routes (updated inside pager pages).
    // Seed from startRoute so titles are correct even when pages are off-screen.
    val tabTitles = remember { mutableStateMapOf<String, String>() }
    val tabIcons = remember { mutableStateMapOf<String, ImageVector>() }
    tabs.forEach { tab ->
        if (tab.id !in tabTitles) {
            tabTitles[tab.id] = getTitleForRoute(tab.startRoute, tab.sessionTitle)
            tabIcons[tab.id] = getIconForRoute(tab.startRoute)
        }
    }
    val tabConnectionStates = remember { mutableStateMapOf<String, SessionConnectionState>() }
    // Track current routes per tab (for PTY cleanup on tab close)
    val tabRoutes = remember { mutableStateMapOf<String, String>() }
    val tabPtyIds = remember { mutableStateMapOf<String, String>() }
    
    // Collect per-tab session connection states (busy/idle/awaiting)
    tabs.forEach { tab ->
        val tabSessionState by tab.connectionState.collectAsState()
        LaunchedEffect(tabSessionState) {
            if (tabSessionState != null) {
                tabConnectionStates[tab.id] = tabSessionState!!
            } else {
                tabConnectionStates.remove(tab.id)
            }
        }
    }
    
    // Shared tab-close logic: PTY cleanup + state map cleanup + tabManager.closeTab.
    // Used by both TabBar close button and TabNavHost BackHandler.
    val closeTab: (String) -> Unit = remember(coroutineScope) {
        { tabId: String ->
            coroutineScope.launch {
                // Check if it's a terminal tab and delete the PTY
                val route = tabRoutes[tabId]
                if (route != null && route.startsWith("terminal/")) {
                    val ptyId = tabPtyIds[tabId]
                    if (ptyId != null) {
                        val api = connectionManager.getApi()
                        if (api != null) {
                            val result = safeApiCall { api.deletePtySession(ptyId) }
                            if (result is ApiResult.Error) {
                                AppLog.e(TAG, "Failed to delete PTY $ptyId: ${result.message}")
                            }
                        }
                    }
                }

                // Clean up tracked state for this tab
                tabRoutes.remove(tabId)
                tabPtyIds.remove(tabId)
                tabTitles.remove(tabId)
                tabIcons.remove(tabId)
                tabConnectionStates.remove(tabId)

                tabManager.closeTab(tabId)
            }
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
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = theme.background,
        contentWindowInsets = WindowInsets(0),
        modifier = modifier
    ) { padding ->
        // We consume the status bar insets here so child Scaffolds don't double-pad.
        // The tab bar gets the status bar padding, then consumeWindowInsets tells
        // downstream composables that the status bar is already accounted for.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .consumeWindowInsets(WindowInsets.statusBars)
        ) {
            // Unified top bar: tabs + path + controls in one compact terminal-style header
            UnifiedTopBar(
                tabs = tabs,
                activeTabId = activeTabId,
                tabTitles = tabTitles,
                tabIcons = tabIcons,
                tabConnectionStates = tabConnectionStates,
                onTabClick = { tabId -> tabManager.focusTab(tabId) },
                onTabClose = closeTab,
                onAddClick = { tabManager.createTab(focus = true) },
                onSettings = onDisconnect
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
            val saveableStateHolder = rememberSaveableStateHolder()
            
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                key = { tabs.getOrNull(it)?.id ?: it.toString() },
                beyondViewportPageCount = 0
            ) { pageIndex ->
                tabs.getOrNull(pageIndex)?.let { tab ->
                    saveableStateHolder.SaveableStateProvider(tab.id) {
                        val navController = rememberNavController()
                        
                        // Track route for title/icon
                        val backStackEntry by navController.currentBackStackEntryAsState()
                        LaunchedEffect(backStackEntry?.destination?.route, tab.sessionTitle) {
                            val route = backStackEntry?.destination?.route
                            // Only update when route is resolved — avoids overwriting
                            // seeded values with "Tab" during initial null-route composition
                            if (route != null) {
                                tabTitles[tab.id] = getTitleForRoute(route, tab.sessionTitle)
                                tabIcons[tab.id] = getIconForRoute(route)
                                // Track route for PTY cleanup on tab close
                                tabRoutes[tab.id] = route
                            }
                            // Track PTY ID if on a terminal route
                            val ptyId = backStackEntry?.arguments?.getString(Screen.Terminal.ARG_PTY_ID)
                            if (ptyId != null) {
                                tabPtyIds[tab.id] = ptyId
                            }
                        }
                        
                        val isActive = tab.id == activeTabId
                        TabNavHost(
                            navController = navController,
                            tabManager = tabManager,
                            tabId = tab.id,
                            onDisconnect = onDisconnect,
                            onCloseTab = { closeTab(tab.id) },
                            startRoute = tab.startRoute,
                            onNewFilesTab = {
                                tabManager.createTab(startRoute = Screen.Files.route, focus = true)
                            },
                            onNewTerminalTab = {
                                coroutineScope.launch {
                                    val api = connectionManager.getApi() ?: run {
                                        AppLog.e(TAG, "Cannot create terminal: not connected")
                                        snackbarHostState.showSnackbar("Not connected to server")
                                        return@launch
                                    }
                                    val result = safeApiCall { api.createPtySession(CreatePtyRequest()) }
                                    when (result) {
                                        is ApiResult.Success -> {
                                            val ptyId = result.data.id
                                            tabManager.createTab(
                                                startRoute = Screen.Terminal.createRoute(ptyId),
                                                focus = true
                                            )
                                        }
                                        is ApiResult.Error -> {
                                            AppLog.e(TAG, "Failed to create PTY: ${result.message}")
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
}
