package dev.blazelight.p4oc.ui.tabs

import dev.blazelight.p4oc.core.datastore.PersistedTab
import dev.blazelight.p4oc.core.datastore.PersistedTabState
import dev.blazelight.p4oc.core.datastore.PersistedWorkspaceKey
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.server.WorkspaceKey
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
        workspaceKey: WorkspaceKey,
        serverRef: ServerRef,
        focus: Boolean = true
    ): TabInstance = addTab(
        tab = TabInstance(
            TabState(workspaceKey = workspaceKey, serverRef = serverRef),
            startRoute = startRoute,
        ),
        focus = focus,
    )

    private fun addTab(tab: TabInstance, focus: Boolean): TabInstance {
        val tabToAdd = if (tab.isPinnedHome) TabInstance.home() else tab
        _tabs.update { currentTabs ->
            val withoutDuplicateHome = if (tabToAdd.isPinnedHome) {
                currentTabs.filterNot { it.isPinnedHome }
            } else {
                currentTabs
            }
            val newTabs = ensureHomeFirst(withoutDuplicateHome + tabToAdd)

            // Show warning at 5+ closeable work tabs (once per session)
            if (newTabs.count { !it.isPinnedHome } >= 5 && !tabWarningShown) {
                _showTabWarning.value = true
                tabWarningShown = true
            }

            newTabs
        }

        if (focus) {
            _activeTabId.value = tabToAdd.id
        } else if (_activeTabId.value == null) {
            _activeTabId.value = TabInstance.HOME_TAB_ID
        }

        return tabToAdd
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
        if (currentTabs[tabIndex].isPinnedHome) return

        val isActive = _activeTabId.value == tabId

        // Remove the tab
        _tabs.update { tabs -> ensureHomeFirst(tabs.filter { it.id != tabId }) }

        // If was active, focus adjacent closeable tab or pinned Home.
        if (isActive) {
            val newTabs = _tabs.value
            val newActiveIndex = minOf(tabIndex, newTabs.size - 1)
            _activeTabId.value = newTabs.getOrNull(newActiveIndex)?.id ?: TabInstance.HOME_TAB_ID
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

    fun findFilesTab(serverRef: ServerRef, workspaceKey: WorkspaceKey): TabInstance? =
        _tabs.value.firstOrNull {
            !it.isPinnedHome &&
                it.serverRef == serverRef &&
                it.workspaceKey == workspaceKey &&
                it.startRoute == Screen.Files.route
        }

    fun focusOrCreateFilesTab(serverRef: ServerRef, workspaceKey: WorkspaceKey): TabInstance {
        val existing = findFilesTab(serverRef, workspaceKey)
        if (existing != null) {
            focusTab(existing.id)
            return existing
        }
        return createTab(
            startRoute = Screen.Files.route,
            workspaceKey = workspaceKey,
            serverRef = serverRef,
            focus = true,
        )
    }

    fun findTerminalTabs(serverRef: ServerRef, workspaceKey: WorkspaceKey): List<TabInstance> =
        _tabs.value.filter {
            !it.isPinnedHome &&
                it.serverRef == serverRef &&
                it.workspaceKey == workspaceKey &&
                it.startRoute.startsWith("terminal/")
        }

    fun findSessionTab(
        serverRef: ServerRef,
        workspaceKey: WorkspaceKey,
        sessionId: String,
    ): TabInstance? = _tabs.value.firstOrNull {
        it.serverRef?.endpointKey == serverRef.endpointKey &&
            it.workspaceKey == workspaceKey &&
            it.sessionId == sessionId
    }

    fun findTabByNotificationRoute(route: dev.blazelight.p4oc.core.notification.NotificationRoute): TabInstance? =
        _tabs.value.find {
            it.sessionId == route.sessionId &&
                it.serverRef?.endpointKey == route.serverRef.endpointKey &&
                it.workspaceKey == route.workspaceKey
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
     * Switch only one tab to a different workspace key.
     */
    fun updateTabWorkspace(tabId: String, workspaceKey: WorkspaceKey) {
        _tabs.update { tabs ->
            tabs.map { tab ->
                if (tab.id == tabId) tab.withWorkspaceKey(workspaceKey) else tab
            }
        }
    }

    fun saveState(): PersistedTabState? {
        val persistedTabs = _tabs.value.mapNotNull { tab ->
            if (tab.isPinnedHome) return@mapNotNull null
            val serverRef = tab.serverRef ?: return@mapNotNull null
            val workspaceKey = tab.workspaceKey ?: return@mapNotNull null
            PersistedTab(
                id = tab.id,
                startRoute = persistableStartRoute(tab),
                sessionId = tab.sessionId,
                sessionTitle = tab.sessionTitle,
                workspaceKey = PersistedWorkspaceKey.fromWorkspaceKey(workspaceKey),
                serverEndpointKey = serverRef.endpointKey,
            )
        }
        if (persistedTabs.isEmpty()) return null
        return PersistedTabState(
            serverEndpointKey = requireNotNull(persistedTabs.first().serverEndpointKey),
            activeTabId = _activeTabId.value?.takeIf { activeId -> persistedTabs.any { it.id == activeId } },
            tabs = persistedTabs,
        )
    }

    fun restoreState(state: PersistedTabState, activeServer: ServerRef): RestoreResult {
        return restoreState(state, mapOf(activeServer.endpointKey to activeServer), fallbackServer = activeServer)
    }

    fun restoreState(
        state: PersistedTabState,
        availableServers: Map<String, ServerRef>,
        fallbackServer: ServerRef? = null,
    ): RestoreResult {
        if (state.version != PersistedTabState.CURRENT_VERSION) {
            restored = true
            return RestoreResult.VersionMismatch(state.version)
        }

        val missingServerEndpointKeys = linkedSetOf<String>()
        val restoredTabIds = mutableSetOf<String>()
        val restoredTabs = state.tabs.mapNotNull { persisted ->
            if (persisted.id.isBlank() || persisted.id == TabInstance.HOME_TAB_ID) return@mapNotNull null
            val persistedWorkspaceKey = persisted.workspaceKey ?: return@mapNotNull null
            if (
                persistedWorkspaceKey.type != PersistedWorkspaceKey.Type.GLOBAL &&
                persistedWorkspaceKey.value == null
            ) {
                return@mapNotNull null
            }
            val workspaceKey = persisted.resolvedWorkspaceKey() ?: return@mapNotNull null
            val serverEndpointKey = persisted.resolvedServerEndpointKey(state.serverEndpointKey) ?: return@mapNotNull null
            val serverRef = availableServers[serverEndpointKey] ?: fallbackServer?.takeIf {
                it.endpointKey == serverEndpointKey
            } ?: run {
                missingServerEndpointKeys += serverEndpointKey
                return@mapNotNull null
            }
            if (!restoredTabIds.add(persisted.id)) return@mapNotNull null
            val route = persisted.sessionId?.let { TabChatRouteCodec.chatRoute(it) }
                ?: persisted.startRoute.takeIf { it.isNotBlank() }
                ?: Screen.Sessions.route
            TabInstance(
                state = TabState(
                    id = persisted.id,
                    sessionId = persisted.sessionId,
                    sessionTitle = persisted.sessionTitle,
                    workspaceKey = workspaceKey,
                    serverRef = serverRef,
                ),
                startRoute = route,
            )
        }

        val withHome = ensureHomeFirst(restoredTabs)
        if (restoredTabs.isEmpty()) {
            _tabs.value = withHome
            _activeTabId.value = TabInstance.HOME_TAB_ID
            restored = true
            return missingServerEndpointKeys.firstOrNull()?.let { RestoreResult.MissingServer(it) } ?: RestoreResult.Empty
        }

        _tabs.value = withHome
        _activeTabId.value = state.activeTabId?.takeIf { activeId -> withHome.any { it.id == activeId } }
            ?: restoredTabs.first().id
        restored = true
        return missingServerEndpointKeys.firstOrNull()?.let { RestoreResult.MissingServer(it, restoredTabs.size) }
            ?: RestoreResult.Restored(restoredTabs.size)
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
            val registered = if (tab.isPinnedHome) TabInstance.home() else tab
            if (currentTabs.any { it.id == registered.id }) {
                ensureHomeFirst(currentTabs)
            } else {
                val newTabs = ensureHomeFirst(currentTabs + registered)

                // Show warning at 5+ closeable work tabs (once per session)
                if (newTabs.count { !it.isPinnedHome } >= 5 && !tabWarningShown) {
                    _showTabWarning.value = true
                    tabWarningShown = true
                }

                newTabs
            }
        }

        if (focus) {
            _activeTabId.value = if (tab.isPinnedHome) TabInstance.HOME_TAB_ID else tab.id
        } else if (_activeTabId.value == null) {
            _activeTabId.value = TabInstance.HOME_TAB_ID
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

    fun ensureHomeTab(focus: Boolean = false): TabInstance {
        val existing = _tabs.value.firstOrNull { it.isPinnedHome }
        if (existing != null) {
            _tabs.update(::ensureHomeFirst)
            if (focus) _activeTabId.value = existing.id
            return existing
        }
        return addTab(TabInstance.home(), focus = focus)
    }

    private fun ensureHomeFirst(tabs: List<TabInstance>): List<TabInstance> {
        val home = tabs.firstOrNull { it.isPinnedHome } ?: TabInstance.home()
        return listOf(home) + tabs.filterNot { it.isPinnedHome }
    }

    private fun persistableStartRoute(tab: TabInstance): String = when (val sessionId = tab.sessionId) {
        null -> when {
            tab.startRoute.startsWith("chat/") -> tab.startRoute.substringBefore('?')
            tab.startRoute.startsWith("terminal/") -> Screen.Sessions.route
            else -> tab.startRoute
        }
        else -> TabChatRouteCodec.chatRoute(sessionId)
    }
}

sealed interface RestoreResult {
    data class Restored(val count: Int) : RestoreResult
    data object Empty : RestoreResult
    data class VersionMismatch(val version: Int) : RestoreResult
    data class ServerMismatch(val persistedEndpointKey: String, val activeEndpointKey: String) : RestoreResult
    data class MissingServer(val endpointKey: String, val restoredCount: Int = 0) : RestoreResult
}
