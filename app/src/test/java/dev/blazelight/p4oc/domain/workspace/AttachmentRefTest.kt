package dev.blazelight.p4oc.domain.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AttachmentRefTest {
    @Test
    fun `file attachment keeps relative path and mime type`() {
        val ref = AttachmentRef.File(RelativePath("docs/readme.md"), mimeType = "text/markdown")

        assertEquals("docs/readme.md", ref.path.value)
        assertEquals("text/markdown", ref.mimeType)
    }

    @Test
    fun `directory attachment keeps relative path`() {
        val ref = AttachmentRef.Directory(RelativePath("docs"))

        assertEquals("docs", ref.path.value)
    }

    @Test
    fun `file attachment rejects blank mime type`() {
        assertThrows(IllegalArgumentException::class.java) {
            AttachmentRef.File(RelativePath("docs/readme.md"), mimeType = " ")
        }
    }

    @Test
    fun `attachment rejects absolute path through relative path`() {
        assertThrows(IllegalArgumentException::class.java) {
            AttachmentRef.File(RelativePath("/etc/passwd"))
        }
    }
}
