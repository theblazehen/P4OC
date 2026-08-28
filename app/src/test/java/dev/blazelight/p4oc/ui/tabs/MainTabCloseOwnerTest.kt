package dev.blazelight.p4oc.ui.tabs

import dev.blazelight.p4oc.ui.workspace.WorkspaceRepositoryOwner
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MainTabCloseOwnerTest {
    @Test
    fun `tab close removes and closes owner before closing tab`() {
        val tabId = "tab-1"
        val events = mutableListOf<String>()
        val owner = mockk<WorkspaceRepositoryOwner>()
        val owners = mutableMapOf(tabId to owner)
        every { owner.close() } answers {
            assertFalse(owners.containsKey(tabId))
            events += "owner-close"
        }

        closeTabWorkspaceOwner(
            tabId = tabId,
            workspaceOwners = owners,
            closeTab = {
                events += "tab-close"
            },
        )

        assertEquals(listOf("owner-close", "tab-close"), events)
        assertFalse(owners.containsKey(tabId))
        verify(exactly = 1) { owner.close() }
    }

    @Test
    fun `reconciliation after close cannot close removed owner twice`() {
        val tabId = "tab-1"
        val owner = mockk<WorkspaceRepositoryOwner>()
        val owners = mutableMapOf(tabId to owner)
        every { owner.close() } just Runs

        closeTabWorkspaceOwner(tabId, owners) {}
        owners.remove(tabId)?.close()

        verify(exactly = 1) { owner.close() }
    }
}
