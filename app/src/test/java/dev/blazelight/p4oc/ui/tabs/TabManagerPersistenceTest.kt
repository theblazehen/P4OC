package dev.blazelight.p4oc.ui.tabs

import dev.blazelight.p4oc.core.datastore.PersistedTab
import dev.blazelight.p4oc.core.datastore.PersistedTabState
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabManagerPersistenceTest {
    private val server = ServerRef.fromEndpointKey("http://localhost:4096")

    @Test
    fun `saveState writes versioned tabs with server endpoint key`() {
        val manager = TabManager()
        val tab = manager.createTab(startRoute = Screen.Sessions.route, focus = true)
        manager.updateTabWorkspace(tab.id, "/repo/a")
        manager.updateTabSession(tab.id, "s1", "Title")

        val saved = manager.saveState(server)!!

        assertEquals(PersistedTabState.CURRENT_VERSION, saved.version)
        assertEquals(server.endpointKey, saved.serverEndpointKey)
        assertEquals(tab.id, saved.activeTabId)
        assertEquals("s1", saved.tabs.single().sessionId)
        assertEquals("/repo/a", saved.tabs.single().workspaceDirectory)
    }

    @Test
    fun `restoreState restores session tab as chat route in same workspace`() {
        val manager = TabManager()
        val state = PersistedTabState(
            serverEndpointKey = server.endpointKey,
            activeTabId = "tab-1",
            tabs = listOf(
                PersistedTab(
                    id = "tab-1",
                    startRoute = Screen.Sessions.route,
                    sessionId = "session with space",
                    sessionTitle = "Chat",
                    workspaceDirectory = "/repo/a b",
                ),
            ),
        )

        val result = manager.restoreState(state, server)

        assertTrue(result is RestoreResult.Restored)
        assertEquals("tab-1", manager.activeTabId.value)
        assertEquals("session with space", manager.tabs.value.single().sessionId)
        assertEquals("/repo/a b", manager.tabs.value.single().workspaceDirectory)
        assertEquals("chat/session%20with%20space", manager.tabs.value.single().startRoute)
    }

    @Test
    fun `restoreState rejects mismatched active server without tabs`() {
        val manager = TabManager()
        val state = PersistedTabState(
            serverEndpointKey = "http://old.example",
            activeTabId = "tab-1",
            tabs = listOf(PersistedTab(id = "tab-1", startRoute = Screen.Sessions.route)),
        )

        val result = manager.restoreState(state, server)

        assertTrue(result is RestoreResult.ServerMismatch)
        assertFalse(manager.hasTabs())
    }

    @Test
    fun `restoreState rejects unsupported version`() {
        val manager = TabManager()
        val state = PersistedTabState(
            version = PersistedTabState.CURRENT_VERSION + 1,
            serverEndpointKey = server.endpointKey,
            activeTabId = "tab-1",
            tabs = listOf(PersistedTab(id = "tab-1", startRoute = Screen.Sessions.route)),
        )

        val result = manager.restoreState(state, server)

        assertTrue(result is RestoreResult.VersionMismatch)
        assertFalse(manager.hasTabs())
    }

    @Test
    fun `terminal routes are not persisted as resurrectable tabs`() {
        val manager = TabManager()
        manager.createTab(startRoute = Screen.Terminal.createRoute("pty-1"), focus = true)

        val saved = manager.saveState(server)!!

        assertEquals(Screen.Sessions.route, saved.tabs.single().startRoute)
    }
}
