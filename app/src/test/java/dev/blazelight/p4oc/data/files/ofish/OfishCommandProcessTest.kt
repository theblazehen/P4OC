package dev.blazelight.p4oc.data.files.ofish

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class OfishCommandProcessTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
    fun `write creates parent directory and file`() {
        assumeShellAvailable()
        val root = temporaryFolder.newFolder()

        val output = runShell(builder.write("a/b/file.txt", "hello", null, capabilities), root)

        assertTrue(OfishMutationParser.parse(output) is OfishMutationStatus.Ok)
        assertEquals("hello", File(root, "a/b/file.txt").readText())
    }

    @Test
    fun `write conflict preserves existing file`() {
        assumeShellAvailable()
        val root = temporaryFolder.newFolder()
        val target = File(root, "file.txt")
        target.writeText("old")

        val output = runShell(builder.write("file.txt", "new", "wrong", capabilities), root)

        assertTrue(OfishMutationParser.parse(output) is OfishMutationStatus.Conflict)
        assertEquals("old", target.readText())
    }

    @Test
    fun `write matching expected hash replaces file`() {
        assumeShellAvailable()
        val root = temporaryFolder.newFolder()
        val target = File(root, "file.txt")
        target.writeText("old")

        val output = runShell(builder.write("file.txt", "new", sha256("old".toByteArray()), capabilities), root)

        assertTrue(OfishMutationParser.parse(output) is OfishMutationStatus.Ok)
        assertEquals("new", target.readText())
    }

    @Test
    fun `delete removes file and rejects directory`() {
        assumeShellAvailable()
        val root = temporaryFolder.newFolder()
        val target = File(root, "file.txt")
        target.writeText("content")

        val deleteOutput = runShell(builder.delete("file.txt"), root)

        assertEquals(OfishMutationStatus.Deleted, OfishMutationParser.parse(deleteOutput))
        assertFalse(target.exists())

        File(root, "dir").mkdir()
        val directoryOutput = runShell(builder.delete("dir"), root)

        assertEquals(OfishMutationStatus.PreconditionFailed("directory"), OfishMutationParser.parse(directoryOutput))
        assertTrue(File(root, "dir").isDirectory)
    }

    @Test
    fun `upload chunk probe verifies payload and cleans temp file`() {
        assumeShellAvailable()
        val root = temporaryFolder.newFolder()
        val command = OfishUploadChunkProbeCommandBuilder(
            payloadBytes = { size -> ByteArray(size) { index -> index.toByte() } },
        ).probe(128, capabilities)

        val output = runShell(command, root)

        assertTrue(OfishMutationParser.parse(output) is OfishMutationStatus.Ok)
        assertTrue(root.listFiles()?.none { it.name.startsWith(".ofish.chunk-probe.") } ?: true)
    }

    @Test
    fun `capability probe command runs under zsh invoking sh wrapper`() {
        assumeShellAvailable()
        assumeZshAvailable()
        val root = temporaryFolder.newFolder()

        val output = runShell(OfishCapabilityProbeCommand.build(), root, shell = "/bin/zsh")

        assertTrue(output, OfishCapabilityParser.parse(output) is OfishProbeResult.Available)
    }

    @Test
    fun `upload init chunks and finish reconstruct bytes`() {
        assumeShellAvailable()
        val root = temporaryFolder.newFolder()
        val bytes = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)

        val init = OfishMutationParser.parse(builder.uploadInit("out/file.bin", null, capabilities).runIn(root))
        assertTrue(init is OfishMutationStatus.Ok)
        val token = (init as OfishMutationStatus.Ok).uploadToken ?: error("missing token")

        bytes.toList().chunked(4).forEach { chunk ->
            val status = OfishMutationParser.parse(builder.uploadChunk(token, chunk.toByteArray(), capabilities).runIn(root))
            assertTrue(status is OfishMutationStatus.Ok)
        }

        val finish = OfishMutationParser.parse(builder.uploadFinish("out/file.bin", token, null, capabilities).runIn(root))

        assertTrue(finish is OfishMutationStatus.Ok)
        assertArrayEquals(bytes, File(root, "out/file.bin").readBytes())
    }

    private fun String.runIn(root: File): String = runShell(this, root)

    private fun runShell(command: String, cwd: File, shell: String = "/bin/sh"): String {
        val process = ProcessBuilder(shell, "-c", command)
            .directory(cwd)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.waitFor())
        return output
    }

    private fun assumeShellAvailable() {
        assumeTrue(File("/bin/sh").exists())
        assumeTrue(ProcessBuilder("/bin/sh", "-c", "command -v sha256sum >/dev/null 2>&1").start().waitFor() == 0)
    }

    private fun assumeZshAvailable() {
        assumeTrue(File("/bin/zsh").exists())
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { "%02x".format(it) }
}
