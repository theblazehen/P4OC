package dev.blazelight.p4oc.ui.tabs

import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.WorkspaceKey
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FilesViewModelKeyTest {
    private val tabId = "tab-1"
    private val workspaceKey = WorkspaceKey.Directory("/repo")

    @Test
    fun `files and file viewer keys change with the server generation`() {
        val previousGeneration = ServerGeneration(7L)
        val replacementGeneration = ServerGeneration(8L)

        assertNotEquals(
            filesViewModelKey(tabId, workspaceKey, previousGeneration, "files"),
            filesViewModelKey(tabId, workspaceKey, replacementGeneration, "files"),
        )
        assertNotEquals(
            filesViewModelKey(tabId, workspaceKey, previousGeneration, "file-viewer-src%2FMain.kt"),
            filesViewModelKey(tabId, workspaceKey, replacementGeneration, "file-viewer-src%2FMain.kt"),
        )
    }

    @Test
    fun `workspace cutover changes graph and route local files identities`() {
        val generation = ServerGeneration(7L)
        val previousFilesIdentity = workspaceGraphRoute(tabId, revision = 3) to
            filesViewModelKey(tabId, workspaceKey, generation, "files")
        val replacementWorkspaceKey = WorkspaceKey.Directory("/other")
        val replacementFilesIdentity = workspaceGraphRoute(tabId, revision = 4) to
            filesViewModelKey(tabId, replacementWorkspaceKey, generation, "files")
        val previousViewerIdentity = workspaceGraphRoute(tabId, revision = 3) to
            filesViewModelKey(tabId, workspaceKey, generation, "file-viewer-src%2FMain.kt")
        val replacementViewerIdentity = workspaceGraphRoute(tabId, revision = 4) to
            filesViewModelKey(tabId, replacementWorkspaceKey, generation, "file-viewer-src%2FMain.kt")

        assertNotEquals(previousFilesIdentity, replacementFilesIdentity)
        assertNotEquals(previousViewerIdentity, replacementViewerIdentity)
    }
}
