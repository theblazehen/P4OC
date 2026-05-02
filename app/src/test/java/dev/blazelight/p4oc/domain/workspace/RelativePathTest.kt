package dev.blazelight.p4oc.domain.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RelativePathTest {
    @Test
    fun `accepts relative path`() {
        assertEquals("src/Main.kt", RelativePath("src/Main.kt").value)
    }

    @Test
    fun `rejects blank path`() {
        assertThrows(IllegalArgumentException::class.java) {
            RelativePath("   ")
        }
    }

    @Test
    fun `rejects absolute path`() {
        assertThrows(IllegalArgumentException::class.java) {
            RelativePath("/tmp/file.txt")
        }
    }

    @Test
    fun `rejects file scheme path`() {
        assertThrows(IllegalArgumentException::class.java) {
            RelativePath("file:///tmp/file.txt")
        }
    }
}
