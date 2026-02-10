package dev.blazelight.p4oc.ui.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
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
 */
class TabManager {
    
    private val _tabs = MutableStateFlow<List<TabInstance>>(emptyList())
    val tabs: StateFlow<List<TabInstance>> = _tabs.asStateFlow()
    
    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()
    
    private val _showTabWarning = MutableStateFlow(false)
    val showTabWarning: StateFlow<Boolean> = _showTabWarning.asStateFlow()
    
    private var tabWarningShown = false
    
    /**
     * Get the currently active tab, or null if no tabs exist.
     */
    val activeTab: TabInstance?
        get() = _activeTabId.value?.let { id -> _tabs.value.find { it.id == id } }
    
    /**
     * Initialize with a single tab. Must be called from a Composable context
     * to create the NavController.
     */
    @Composable
    fun initializeIfNeeded(): TabInstance {
        val navController = rememberNavController()
        
        return remember(navController) {
            if (_tabs.value.isEmpty()) {
                val tab = TabInstance(TabState(), navController)
                _tabs.value = listOf(tab)
                _activeTabId.value = tab.id
                tab
            } else {
                // Return existing first tab, but update its navController if needed
                _tabs.value.first()
            }
        }
    }
    
    /**
     * Create a new tab and optionally focus it.
     * Returns the created tab instance.
     */
    fun createTab(navController: NavHostController, focus: Boolean = true): TabInstance {
        val tab = TabInstance(TabState(), navController)
        
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
     * If closing the last tab, creates a fresh tab.
     */
    fun closeTab(tabId: String, createNewTabNavController: (() -> NavHostController)? = null) {
        val currentTabs = _tabs.value
        val tabIndex = currentTabs.indexOfFirst { it.id == tabId }
        
        if (tabIndex == -1) return
        
        val isActive = _activeTabId.value == tabId
        
        if (currentTabs.size == 1) {
            // Last tab - create fresh one if we have a factory
            if (createNewTabNavController != null) {
                val newTab = TabInstance(TabState(), createNewTabNavController())
                _tabs.value = listOf(newTab)
                _activeTabId.value = newTab.id
            }
            // If no factory provided, can't close last tab
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
     * Dismiss the tab warning snackbar.
     */
    fun dismissTabWarning() {
        _showTabWarning.value = false
    }
    
    /**
     * Register a tab that was created externally (e.g., via rememberNavController in Compose).
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
}
