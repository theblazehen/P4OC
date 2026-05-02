package dev.blazelight.p4oc.domain.workspace

import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.server.WorkspaceKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkspaceTest {
    private val server = ServerRef.fromEndpoint("example.com")

    @Test
    fun `null directory gives global workspace key`() {
        val workspace = Workspace(server = server, directory = null)

        assertEquals(WorkspaceKey.Global, workspace.key)
    }

    @Test
    fun `non-null directory gives directory workspace key`() {
        val workspace = Workspace(server = server, directory = "/repo")

        assertEquals(WorkspaceKey.Directory("/repo"), workspace.key)
    }

    @Test
    fun `blank directory is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Workspace(server = server, directory = "   ")
        }
    }

    @Test
    fun `workspace key directory rejects blank value`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceKey.Directory("")
        }
    }

    @Test
    fun `workspace exposes no companion defaults`() {
        val companion = Workspace::class.java.declaredClasses.singleOrNull { it.simpleName == "Companion" }

        assertEquals(null, companion)
        assertFalse(Workspace::class.java.methods.any { method ->
            method.name in setOf("getGlobal", "getDEFAULT", "getCurrent")
        })
    }
}
