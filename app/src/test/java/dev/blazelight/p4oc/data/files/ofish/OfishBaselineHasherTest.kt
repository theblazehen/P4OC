package dev.blazelight.p4oc.data.files.ofish

import dev.blazelight.p4oc.domain.model.FileContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfishBaselineHasherTest {
    @Test
    fun `sha256sum hashes utf8 text bytes`() {
        val hash = OfishBaselineHasher.hash(FileContent(content = "hello\n"), HashCommand.SHA256SUM)

        assertEquals("5891b5b522d5df086d0ff0b110fbd9d21bb4fc7163af34d08286a2e846f6be03", hash)
    }

    @Test
    fun `shasum 256 hashes like sha256sum`() {
        val hash = OfishBaselineHasher.hash(FileContent(content = "hello\n"), HashCommand.SHASUM_256)

        assertEquals("5891b5b522d5df086d0ff0b110fbd9d21bb4fc7163af34d08286a2e846f6be03", hash)
    }

    @Test
    fun `md5sum hashes utf8 text bytes`() {
        val hash = OfishBaselineHasher.hash(FileContent(content = "hello\n"), HashCommand.MD5SUM)

        assertEquals("b1946ac92492d2347c6235b4d2611184", hash)
    }

    @Test
    fun `non text content is not hashed`() {
        val hash = OfishBaselineHasher.hash(FileContent(type = "binary", content = "hello\n"), HashCommand.SHA256SUM)

        assertNull(hash)
    }

    @Test
    fun `encoded content is not hashed`() {
        val hash = OfishBaselineHasher.hash(FileContent(content = "aGVsbG8K", encoding = "base64"), HashCommand.SHA256SUM)

        assertNull(hash)
    }
}
