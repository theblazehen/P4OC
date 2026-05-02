package dev.blazelight.p4oc.domain.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkspacePathTest {
    @Test
    fun `relative workspace path exposes relative value`() {
        val path = WorkspacePath.Relative(RelativePath("app/build.gradle.kts"))

        assertEquals("app/build.gradle.kts", path.value)
    }

    @Test
    fun `relative attachment url round trips spaces unicode and dots`() {
        val paths = listOf(
            "src/My File.kt",
            "docs/ümlaut/こんにちは.md",
            "./a..b/.hidden file.txt",
            "src/a?b#c.kt",
        )

        paths.forEach { rawPath ->
            val path = WorkspacePath.Relative(RelativePath(rawPath))

            assertEquals(path, WorkspacePathAttachmentCodec.parseFromServer(path.toAttachmentUrl()))
        }
    }

    @Test
    fun `server symbol uri parses encoded path query and fragment characters`() {
        val uri = "file://src/My%20File%3F%23.kt"

        assertEquals(
            WorkspacePath.Relative(RelativePath("src/My File?#.kt")),
            WorkspacePathParser.parseFromServer(uri),
        )
    }

    @Test
    fun `server symbol uri preserves unencoded query and fragment as path characters`() {
        val uri = "file://src/My%20File.kt?symbol%3Fquery#heading%23one"

        assertEquals(
            WorkspacePath.Relative(RelativePath("src/My File.kt?symbol?query#heading#one")),
            WorkspacePathParser.parseFromServer(uri),
        )
    }

    @Test
    fun `relative workspace path rejects invalid server values`() {
        listOf("", "   ", "/absolute/path").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                WorkspacePathAttachmentCodec.parseFromServer(value)
            }
        }
    }

    @Test
    fun `absolute workspace path requires leading slash`() {
        val path = WorkspacePath.Absolute("/home/user/project")

        assertEquals("/home/user/project", path.value)
    }

    @Test
    fun `absolute workspace path rejects file scheme`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkspacePath.Absolute("file:///home/user/project")
        }
    }
}
