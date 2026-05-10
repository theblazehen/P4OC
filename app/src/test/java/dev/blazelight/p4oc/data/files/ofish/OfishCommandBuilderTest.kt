package dev.blazelight.p4oc.data.files.ofish

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class OfishCommandBuilderTest {
    private val capabilities = OfishCapabilities(
        hasBase64 = true,
        base64DecodeFlag = "-d",
        hashCommand = HashCommand.SHA256SUM,
        hasMv = true,
        hasMkdir = true,
        hasRm = true,
        hasAwk = true,
        hasMktemp = true,
    )

    private val builder = OfishCommandBuilder()

    @Test
    fun `write quotes paths with POSIX single quote escaping`() {
        val script = builder.write("dir/weird'name.txt", "content", null, capabilities).decodedScript()

        assertTrue(script.contains("P='dir/weird'\\''name.txt'"))
    }

    @Test
    fun `commands run through sh wrapper`() {
        val command = builder.write("dir/file.txt", "content", null, capabilities)

        assertTrue(command.startsWith("printf %s "))
        assertTrue(command.endsWith(" | (base64 -d 2>/dev/null || base64 -D) | sh"))
    }

    @Test
    fun `upload shell substitutions quote metacharacters safely`() {
        val token = "tmp/'bad; rm -rf /; $(touch nope)"
        val path = "dir/'bad; rm -rf /; `touch nope`.txt"
        val script = builder.uploadFinish(path, token, "hash'&&boom", capabilities).decodedScript()

        assertTrue(script.contains("P='dir/'\\''bad; rm -rf /; `touch nope`.txt'"))
        assertTrue(script.contains("TMP='tmp/'\\''bad; rm -rf /; \$(touch nope)'"))
        assertTrue(script.contains("EXPECTED='hash'\\''&&boom'"))
    }

    @Test
    fun `write contains marker heredoc conflict guard and atomic write steps`() {
        val script = builder.write("dir/file.txt", "content", "expected", capabilities).decodedScript()

        assertTrue(script.contains("#OFISH_WRITE"))
        assertTrue(script.contains("<<'__OFISH_PAYLOAD__'"))
        assertTrue(script.contains("### 404 missing"))
        assertTrue(script.contains("### 409 conflict actual=%s"))
        assertTrue(script.contains("mkdir -p"))
        assertTrue(script.contains("mktemp"))
        assertTrue(script.contains("trap cleanup"))
        assertTrue(script.contains("mv -f"))
    }

    @Test
    fun `write uses base64 heredoc not raw payload`() {
        val raw = "hello secret content"
        val command = builder.write("file.txt", raw, null, capabilities)

        assertFalse(command.contains(raw))
        assertTrue(command.decodedScript().contains("aGVsbG8gc2VjcmV0IGNvbnRlbnQ="))
    }

    @Test
    fun `raw content containing delimiter text remains base64 encoded`() {
        val raw = "before __OFISH_PAYLOAD__ after"
        val script = builder.write("file.txt", raw, null, capabilities).decodedScript()

        assertFalse(script.contains("before __OFISH_PAYLOAD__ after"))
        assertTrue(script.contains(Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))))
        assertTrue(script.contains("<<'__OFISH_PAYLOAD__'"))
    }

    @Test
    fun `delete contains marker and directory precondition`() {
        val script = builder.delete("dir/file.txt").decodedScript()

        assertTrue(script.contains("#OFISH_DELETE"))
        assertTrue(script.contains("[ -d \"\$P\" ]"))
        assertTrue(script.contains("### 412 precondition reason=directory"))
        assertTrue(script.contains("### 404 missing"))
    }

    @Test
    fun `upload commands contain markers and heredoc chunks`() {
        val init = builder.uploadInit("dir/file.bin", null, capabilities)
        val chunk = builder.uploadChunk("dir/.ofish.upload.abc", byteArrayOf(1, 2, 3), capabilities)
        val finish = builder.uploadFinish("dir/file.bin", "dir/.ofish.upload.abc", null, capabilities)
        val abort = builder.uploadAbort("dir/.ofish.upload.abc")

        assertTrue(init.decodedScript().contains("#OFISH_UPLOAD_INIT"))
        assertTrue(chunk.decodedScript().contains("#OFISH_UPLOAD_CHUNK"))
        assertTrue(chunk.decodedScript().contains("<<'__OFISH_PAYLOAD__'"))
        assertTrue(chunk.decodedScript().contains("### 412 precondition reason=missing_tmp"))
        assertTrue(finish.decodedScript().contains("#OFISH_UPLOAD_FINISH"))
        assertTrue(abort.decodedScript().contains("#OFISH_UPLOAD_ABORT"))
    }

    @Test
    fun `upload finish rechecks expected hash before move`() {
        val script = builder.uploadFinish("dir/file.bin", "dir/.ofish.upload.abc", "expected", capabilities).decodedScript()

        assertTrue(script.contains("EXPECTED='expected'"))
        assertTrue(script.contains("### 404 missing"))
        assertTrue(script.contains("### 409 conflict actual=%s"))
        assertTrue(script.indexOf("### 409 conflict") < script.indexOf("mv -f"))
    }

    private fun String.decodedScript(): String {
        val encoded = Regex("printf %s '?([A-Za-z0-9+/=]+)'? ").find(this)?.groupValues?.get(1)
            ?: error("missing wrapped script")
        return String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
    }
}
