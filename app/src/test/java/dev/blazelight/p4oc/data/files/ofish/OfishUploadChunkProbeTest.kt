package dev.blazelight.p4oc.data.files.ofish

import dev.blazelight.p4oc.data.remote.dto.MessageInfoDto
import dev.blazelight.p4oc.data.remote.dto.MessageTimeDto
import dev.blazelight.p4oc.data.remote.dto.MessageWrapperDto
import dev.blazelight.p4oc.data.remote.dto.PartDto
import dev.blazelight.p4oc.data.remote.dto.PermissionResponseRequest
import dev.blazelight.p4oc.data.remote.dto.SessionDto
import dev.blazelight.p4oc.data.remote.dto.ShellCommandRequest
import dev.blazelight.p4oc.data.remote.dto.TimeDto
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.workspace.Workspace
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OfishUploadChunkProbeTest {
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

    @Test
    fun `uses default when first probe succeeds`() = runTest {
        val client = FakeOfishWorkspaceClient(results = ArrayDeque(listOf(true)))
        val probe = probe(client)

        val result = probe.findSupportedChunkBytes(capabilities, startBytes = 256 * 1024, minBytes = 64 * 1024)

        assertEquals(256 * 1024, result)
        assertEquals(listOf(256 * 1024), client.probedBytes)
    }

    @Test
    fun `tries minimum when default probe fails`() = runTest {
        val client = FakeOfishWorkspaceClient(results = ArrayDeque(listOf(false, true)))
        val probe = probe(client)

        val result = probe.findSupportedChunkBytes(capabilities, startBytes = 1024 * 1024, minBytes = 256 * 1024)

        assertEquals(256 * 1024, result)
        assertEquals(listOf(1024 * 1024, 256 * 1024), client.probedBytes)
    }

    @Test
    fun `never probes upward after success`() = runTest {
        val client = FakeOfishWorkspaceClient(results = ArrayDeque(listOf(true, true)))
        val probe = probe(client)

        val result = probe.findSupportedChunkBytes(capabilities, startBytes = 256 * 1024, minBytes = 64 * 1024)

        assertEquals(256 * 1024, result)
        assertEquals(listOf(256 * 1024), client.probedBytes)
    }

    @Test
    fun `fails explicitly when default and minimum probes fail`() = runTest {
        val client = FakeOfishWorkspaceClient(results = ArrayDeque(listOf(false, false)))
        val probe = probe(client)

        try {
            probe.findSupportedChunkBytes(capabilities, startBytes = 256 * 1024, minBytes = 64 * 1024)
            fail("Expected OfishUploadChunkProbeUnavailableException")
        } catch (_: OfishUploadChunkProbeUnavailableException) {
            // Expected.
        }

        assertEquals(listOf(256 * 1024, 64 * 1024), client.probedBytes)
    }

    @Test
    fun `cache probes once for concurrent callers`() = runTest {
        val client = FakeOfishWorkspaceClient(results = ArrayDeque(listOf(false, true)))
        val cache = CachedOfishUploadChunkBytes(
            probe = probe(client),
            defaultBytes = 256 * 1024,
            minBytes = 128 * 1024,
        )

        val results = (1..8).map { async { cache.get(capabilities) } }.awaitAll()

        assertTrue(results.all { it == 128 * 1024 })
        assertEquals(listOf(256 * 1024, 128 * 1024), client.probedBytes)
    }

    @Test
    fun `probe script uses fixed payload delimiter`() {
        val script = OfishUploadChunkProbeCommandBuilder(
            payloadBytes = { size -> ByteArray(size) { 7 } },
        ).probe(128, capabilities).decodedScript()

        assertTrue(script.contains("<<'__OFISH_PAYLOAD__'"))
    }

    private fun probe(client: FakeOfishWorkspaceClient): OfishUploadChunkProbe = OfishUploadChunkProbe(
        client = client,
        sessionFactory = OfishSessionFactory(client),
        commandBuilder = OfishUploadChunkProbeCommandBuilder(
            payloadBytes = { size -> ByteArray(size) { 7 } },
        ),
    )

    private fun String.decodedScript(): String {
        val encoded = Regex("printf %s '?([A-Za-z0-9+/=]+)'? ").find(this)?.groupValues?.get(1)
            ?: error("missing wrapped script")
        return String(java.util.Base64.getDecoder().decode(encoded), Charsets.UTF_8)
    }

    private class FakeOfishWorkspaceClient(
        private val results: ArrayDeque<Boolean>,
    ) : OfishWorkspaceClient {
        override val workspace: Workspace = Workspace(
            server = ServerRef.fromEndpoint("http://localhost:4096", "local"),
            directory = "/repo",
        )
        val probedBytes = mutableListOf<Int>()
        private var sessions = 0

        override suspend fun createSession(title: String): SessionDto {
            delay(1)
            sessions += 1
            return SessionDto(
                id = "session-$sessions",
                projectID = "project",
                directory = "/repo",
                title = title,
                version = "test",
                time = TimeDto(created = 0),
            )
        }

        override suspend fun deleteSession(id: String): Boolean = true

        override suspend fun executeShellCommand(sessionId: String, request: ShellCommandRequest): MessageWrapperDto {
            val encoded = Regex("printf %s '?([A-Za-z0-9+/=]+)'? ").find(request.command)?.groupValues?.get(1)
                ?: error("probe command did not include encoded script")
            val script = String(java.util.Base64.getDecoder().decode(encoded), Charsets.UTF_8)
            probedBytes += Regex("bytes=(\\d+)").find(script)?.groupValues?.get(1)?.toInt()
                ?: error("probe command did not include bytes marker")
            val ok = results.removeFirstOrNull() ?: false
            return message(if (ok) "### 200 ok" else "### 500 failed reason=test")
        }

        override suspend fun listSessionsCurrentWorkspace(limit: Int?): List<SessionDto> = emptyList()

        override suspend fun respondToPermission(id: String, request: PermissionResponseRequest): Boolean = true

        private fun message(output: String): MessageWrapperDto = MessageWrapperDto(
            info = MessageInfoDto(
                id = "message",
                sessionID = "session",
                time = MessageTimeDto(created = 0),
                role = "assistant",
            ),
            parts = listOf(
                PartDto(
                    id = "part",
                    sessionID = "session",
                    messageID = "message",
                    type = "text",
                    text = output,
                ),
            ),
        )

    }
}
