package dev.blazelight.p4oc.ui.tabs

import dev.blazelight.p4oc.core.datastore.PersistedTab
import dev.blazelight.p4oc.core.datastore.PersistedTabState
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.ui.navigation.Screen
import dev.blazelight.p4oc.ui.navigation.TabChatRouteCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages the top-level tab system.
 * 
 * Responsibilities:
 * - Track all open tabs and their states
 * - Track which tab is active
 * - Create/close tabs
 * - Enforce session uniqueness (one tab per session)
 * - Handle minimum 1 tab rule
 * 
 * Note: This is a Koin singleton (app lifetime). It must NEVER hold
 * Compose/NavController references — those are created inside the
 * HorizontalPager page composition scope.
 */
class TabManager {
    
    private val _tabs = MutableStateFlow<List<TabInstance>>(emptyList())
    val tabs: StateFlow<List<TabInstance>> = _tabs.asStateFlow()
    
    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()
    
    private val _showTabWarning = MutableStateFlow(false)
    val showTabWarning: StateFlow<Boolean> = _showTabWarning.asStateFlow()
    
    private var tabWarningShown = false
    private var restored = false
    
    /**
     * Get the currently active tab, or null if no tabs exist.
     */
    val activeTab: TabInstance?
        get() = _activeTabId.value?.let { id -> _tabs.value.find { it.id == id } }
    
    /**
     * Create a new tab and optionally focus it.
     * Returns the created tab instance.
     */
    fun createTab(
        startRoute: String = "sessions",
        workspaceDirectory: String? = null,
        focus: Boolean = true
    ): TabInstance {
        val tab = TabInstance(
            TabState(workspaceDirectory = workspaceDirectory?.takeIf { it.isNotBlank() }),
            startRoute = startRoute,
        )
        
        _tabs.update { currentTabs ->
            val newTabs = currentTabs + tab
            
            // Show warning at 5+ tabs (once per session)
            if (newTabs.size >= 5 && !tabWarningShown) {
                _showTabWarning.value = true
                tabWarningShown = true
            }
            
            newTabs
        }
        
        if (focus) {
            _activeTabId.value = tab.id
        }
        
        return tab
    }
    
    /**
     * Close a tab by ID.
     * If closing the active tab, focuses an adjacent tab.
     * If closing the last tab, creates a fresh replacement tab.
     */
    fun closeTab(tabId: String) {
        val currentTabs = _tabs.value
        val tabIndex = currentTabs.indexOfFirst { it.id == tabId }
        
        if (tabIndex == -1) return
        
        val isActive = _activeTabId.value == tabId
        
        if (currentTabs.size == 1) {
            // Last tab - create a fresh replacement
            val newTab = TabInstance(TabState())
            _tabs.value = listOf(newTab)
            _activeTabId.value = newTab.id
            return
        }
        
        // Remove the tab
        _tabs.update { tabs -> tabs.filter { it.id != tabId } }
        
        // If was active, focus adjacent
        if (isActive) {
            val newTabs = _tabs.value
            val newActiveIndex = minOf(tabIndex, newTabs.size - 1)
            _activeTabId.value = newTabs.getOrNull(newActiveIndex)?.id
        }
    }
    
    /**
     * Focus a tab by ID.
     */
    fun focusTab(tabId: String) {
        if (_tabs.value.any { it.id == tabId }) {
            _activeTabId.value = tabId
        }
    }
    
    /**
     * Find a tab that's showing the given session.
     */
    fun findTabBySessionId(sessionId: String): TabInstance? {
        return _tabs.value.find { it.sessionId == sessionId }
    }
    
    /**
     * Update a tab's session binding.
     * Call when navigating to/from a chat screen within a tab.
     */
    fun updateTabSession(tabId: String, sessionId: String?, sessionTitle: String? = null) {
        _tabs.update { tabs ->
            tabs.map { tab ->
                if (tab.id == tabId) {
                    tab.withSessionId(sessionId, sessionTitle)
                } else {
                    tab
                }
            }
        }
    }
    
    /**
     * Clear a tab's session binding.
     * Call when navigating away from a chat screen.
     */
    fun clearTabSession(tabId: String) {
        updateTabSession(tabId, null, null)
    }

    /**
     * Switch only one tab to a different workspace directory.
     */
    fun updateTabWorkspace(tabId: String, directory: String?) {
        _tabs.update { tabs ->
            tabs.map { tab ->
                if (tab.id == tabId) tab.withWorkspaceDirectory(directory) else tab
            }
        }
    }

    fun saveState(serverRef: ServerRef): PersistedTabState? {
        val currentTabs = _tabs.value
        if (currentTabs.isEmpty()) return null
        return PersistedTabState(
            serverEndpointKey = serverRef.endpointKey,
            activeTabId = _activeTabId.value,
            tabs = currentTabs.map { tab ->
                PersistedTab(
                    id = tab.id,
                    startRoute = persistableStartRoute(tab),
                    sessionId = tab.sessionId,
                    sessionTitle = tab.sessionTitle,
                    workspaceDirectory = tab.workspaceDirectory,
                )
            },
        )
    }

    fun restoreState(state: PersistedTabState, activeServer: ServerRef): RestoreResult {
        if (state.version != PersistedTabState.CURRENT_VERSION) {
            restored = true
            return RestoreResult.VersionMismatch(state.version)
        }
        if (state.serverEndpointKey != activeServer.endpointKey) {
            restored = true
            return RestoreResult.ServerMismatch(state.serverEndpointKey, activeServer.endpointKey)
        }

        val restoredTabs = state.tabs.mapNotNull { persisted ->
            if (persisted.id.isBlank()) return@mapNotNull null
            val route = persisted.sessionId?.let { TabChatRouteCodec.chatRoute(it) }
                ?: persisted.startRoute.takeIf { it.isNotBlank() }
                ?: Screen.Sessions.route
            TabInstance(
                state = TabState.withId(
                    id = persisted.id,
                    sessionId = persisted.sessionId,
                    sessionTitle = persisted.sessionTitle,
                    workspaceDirectory = persisted.workspaceDirectory,
                ),
                startRoute = route,
            )
        }

        if (restoredTabs.isEmpty()) {
            restored = true
            return RestoreResult.Empty
        }

        _tabs.value = restoredTabs
        _activeTabId.value = state.activeTabId?.takeIf { activeId -> restoredTabs.any { it.id == activeId } }
            ?: restoredTabs.first().id
        restored = true
        return RestoreResult.Restored(restoredTabs.size)
    }

    fun shouldAttemptRestore(): Boolean = !restored && _tabs.value.isEmpty()
    
    /**
     * Dismiss the tab warning snackbar.
     */
    fun dismissTabWarning() {
        _showTabWarning.value = false
    }
    
    /**
     * Register a tab that was created externally.
     */
    fun registerTab(tab: TabInstance, focus: Boolean = true) {
        _tabs.update { currentTabs ->
            if (currentTabs.any { it.id == tab.id }) {
                currentTabs // Already registered
            } else {
                val newTabs = currentTabs + tab
                
                // Show warning at 5+ tabs (once per session)
                if (newTabs.size >= 5 && !tabWarningShown) {
                    _showTabWarning.value = true
                    tabWarningShown = true
                }
                
                newTabs
            }
        }
        
        if (focus) {
            _activeTabId.value = tab.id
        }
    }
    
    /**
     * Check if we have any tabs.
     */
    fun hasTabs(): Boolean = _tabs.value.isNotEmpty()
    
    /**
     * Get tab count.
     */
    fun tabCount(): Int = _tabs.value.size

    private fun persistableStartRoute(tab: TabInstance): String = when (val sessionId = tab.sessionId) {
        null -> if (tab.startRoute.startsWith("terminal/")) Screen.Sessions.route else tab.startRoute
        else -> TabChatRouteCodec.chatRoute(sessionId)
    }
}

sealed interface RestoreResult {
    data class Restored(val count: Int) : RestoreResult
    data object Empty : RestoreResult
    data class VersionMismatch(val version: Int) : RestoreResult
    data class ServerMismatch(val persistedEndpointKey: String, val activeEndpointKey: String) : RestoreResult
}
