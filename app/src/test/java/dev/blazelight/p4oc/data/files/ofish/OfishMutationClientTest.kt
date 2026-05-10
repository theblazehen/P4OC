package dev.blazelight.p4oc.data.files.ofish

import dev.blazelight.p4oc.data.files.FileOperationResult
import dev.blazelight.p4oc.data.files.FileUploadRequest
import dev.blazelight.p4oc.data.files.FileWriteRequest
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfishMutationClientTest {
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
    fun `missing capabilities fail without creating session`() = runTest {
        val client = FakeOfishWorkspaceClient()
        val mutationClient = mutationClient(client, OfishProbeResult.Missing(listOf("base64"), null))

        val result = mutationClient.writeFile(FileWriteRequest("file.txt", "content"))

        assertTrue(result is FileOperationResult.Failed)
        assertEquals(0, client.createdTitles.size)
    }

    @Test
    fun `invalid mutation path fails without creating session`() = runTest {
        val client = FakeOfishWorkspaceClient()
        val mutationClient = mutationClient(client, OfishProbeResult.Available(capabilities))

        val result = mutationClient.uploadFile(FileUploadRequest("../secret", byteArrayOf(1)))

        assertTrue(result is FileOperationResult.Failed)
        assertEquals(0, client.createdTitles.size)
    }

    @Test
    fun `write conflict maps to FileOperationResult Conflict and deletes session`() = runTest {
        val client = FakeOfishWorkspaceClient(outputs = ArrayDeque(listOf("### 409 conflict actual=abc")))
        val mutationClient = mutationClient(client, OfishProbeResult.Available(capabilities))

        val result = mutationClient.writeFile(FileWriteRequest("file.txt", "content", expectedHash = "old"))

        assertTrue(result is FileOperationResult.Conflict)
        assertEquals("abc", (result as FileOperationResult.Conflict).currentHash)
        assertEquals(1, client.createdTitles.size)
        assertEquals(listOf("session-1"), client.deletedIds)
    }

    @Test
    fun `hash guarded write missing maps to conflict`() = runTest {
        val client = FakeOfishWorkspaceClient(outputs = ArrayDeque(listOf("### 404 missing")))
        val mutationClient = mutationClient(client, OfishProbeResult.Available(capabilities))

        val result = mutationClient.writeFile(FileWriteRequest("file.txt", "content", expectedHash = "old"))

        assertTrue(result is FileOperationResult.Conflict)
        assertEquals(null, (result as FileOperationResult.Conflict).currentHash)
    }

    @Test
    fun `upload uses one session for init chunks finish`() = runTest {
        val client = FakeOfishWorkspaceClient(
            outputs = ArrayDeque(
                listOf(
                    "### 200 ok upload=.ofish.upload.tmp",
                    "### 200 ok",
                    "### 200 ok",
                    "### 200 ok hash=abc",
                ),
            ),
        )
        val mutationClient = mutationClient(client, OfishProbeResult.Available(capabilities), uploadChunkBytes = 2)

        val result = mutationClient.uploadFile(FileUploadRequest("file.bin", byteArrayOf(1, 2, 3, 4)))

        assertTrue(result is FileOperationResult.Ok)
        assertEquals("abc", (result as FileOperationResult.Ok).data.hash)
        assertEquals(1, client.createdTitles.size)
        assertEquals(listOf("session-1"), client.deletedIds)
        assertEquals(4, client.commands.size)
        assertTrue(client.commands.last().contains("(base64 -d 2>/dev/null || base64 -D) | sh"))
    }

    @Test
    fun `upload resolves chunk size once per upload`() = runTest {
        val client = FakeOfishWorkspaceClient(
            outputs = ArrayDeque(
                listOf(
                    "### 200 ok upload=.ofish.upload.tmp",
                    "### 200 ok",
                    "### 200 ok",
                    "### 200 ok hash=abc",
                ),
            ),
        )
        val provider = RecordingUploadChunkBytesProvider(bytes = 2)
        val mutationClient = mutationClient(
            client = client,
            probeResult = OfishProbeResult.Available(capabilities),
            uploadChunkBytesProvider = provider,
        )

        val result = mutationClient.uploadFile(FileUploadRequest("file.bin", byteArrayOf(1, 2, 3, 4)))

        assertTrue(result is FileOperationResult.Ok)
        assertEquals(1, provider.calls)
        assertEquals(4, client.commands.size)
    }

    @Test
    fun `upload returns failed when chunk size provider fails`() = runTest {
        val client = FakeOfishWorkspaceClient(outputs = ArrayDeque(listOf("### 200 ok upload=.ofish.upload.tmp", "### 204 deleted")))
        val mutationClient = mutationClient(
            client = client,
            probeResult = OfishProbeResult.Available(capabilities),
            uploadChunkBytesProvider = FailingUploadChunkBytesProvider(),
        )

        val result = mutationClient.uploadFile(FileUploadRequest("file.bin", byteArrayOf(1, 2, 3, 4)))

        assertTrue(result is FileOperationResult.Failed)
        val failed = result as FileOperationResult.Failed
        assertEquals("OFISH upload chunk probe failed for test", failed.message)
        assertTrue(failed.cause is OfishUploadChunkProbeUnavailableException)
        assertEquals(2, client.commands.size)
    }

    @Test
    fun `unsafe upload token rejected before chunks`() = runTest {
        val client = FakeOfishWorkspaceClient(outputs = ArrayDeque(listOf("### 200 ok upload=/tmp/evil")))
        val mutationClient = mutationClient(client, OfishProbeResult.Available(capabilities), uploadChunkBytes = 2)

        val result = mutationClient.uploadFile(FileUploadRequest("dir/file.bin", byteArrayOf(1, 2, 3, 4)))

        assertTrue(result is FileOperationResult.Failed)
        assertEquals(1, client.commands.size)
        assertTrue(client.commands.single().contains("(base64 -d 2>/dev/null || base64 -D) | sh"))
    }

    @Test
    fun `nested upload token is rejected before chunks`() = runTest {
        assertUploadTokenRejected("dir/.ofish.upload.evil/target", destinationPath = "dir/file.bin")
    }

    @Test
    fun `sibling upload token is rejected before chunks`() = runTest {
        assertUploadTokenRejected("other/.ofish.upload.tmp", destinationPath = "dir/file.bin")
    }

    @Test
    fun `extra segment upload token is rejected before chunks`() = runTest {
        assertUploadTokenRejected("dir/sub/.ofish.upload.tmp", destinationPath = "dir/file.bin")
    }

    @Test
    fun `wrong parent upload token is rejected before chunks`() = runTest {
        assertUploadTokenRejected(".ofish.upload.tmp", destinationPath = "dir/file.bin")
    }

    @Test
    fun `upload finish threads expected hash for final recheck`() = runTest {
        val client = FakeOfishWorkspaceClient(
            outputs = ArrayDeque(
                listOf(
                    "### 200 ok upload=.ofish.upload.tmp",
                    "### 200 ok",
                    "### 409 conflict actual=newer",
                    "### 204 deleted",
                ),
            ),
        )
        val mutationClient = mutationClient(client, OfishProbeResult.Available(capabilities), uploadChunkBytes = 4)

        val result = mutationClient.uploadFile(FileUploadRequest("file.bin", byteArrayOf(1, 2), expectedHash = "old"))

        assertTrue(result is FileOperationResult.Conflict)
        assertEquals("newer", (result as FileOperationResult.Conflict).currentHash)
        assertTrue(client.commands.any { it.contains("(base64 -d 2>/dev/null || base64 -D) | sh") })
    }

    @Test
    fun `concurrent cached capabilities probe once`() = runTest {
        val client = FakeOfishWorkspaceClient(
            outputs = ArrayDeque(listOf("caps base64=1 base64_decode=-d hash=sha256sum mv=1 mkdir=1 rm=1 awk=1 mktemp=1\n### 200 ok")),
        )
        val probe = OfishCapabilityProbe(client, OfishSessionFactory(client))
        val cache = CachedOfishCapabilities(probe)

        val results = (1..8).map { async { cache.get() } }.awaitAll()

        assertTrue(results.all { it is OfishProbeResult.Available })
        assertEquals(1, client.commands.size)
        assertEquals(1, client.createdTitles.size)
    }

    private suspend fun assertUploadTokenRejected(uploadToken: String, destinationPath: String) {
        val client = FakeOfishWorkspaceClient(outputs = ArrayDeque(listOf("### 200 ok upload=$uploadToken")))
        val mutationClient = mutationClient(client, OfishProbeResult.Available(capabilities), uploadChunkBytes = 2)

        val result = mutationClient.uploadFile(FileUploadRequest(destinationPath, byteArrayOf(1, 2, 3, 4)))

        assertTrue(result is FileOperationResult.Failed)
        assertEquals(1, client.commands.size)
        assertTrue(client.commands.single().contains("(base64 -d 2>/dev/null || base64 -D) | sh"))
    }

    private fun mutationClient(
        client: FakeOfishWorkspaceClient,
        probeResult: OfishProbeResult,
        uploadChunkBytes: Int = 256 * 1024,
    ): OfishMutationClient = mutationClient(
        client = client,
        probeResult = probeResult,
        uploadChunkBytesProvider = FixedUploadChunkBytesProvider(uploadChunkBytes),
    )

    private fun mutationClient(
        client: FakeOfishWorkspaceClient,
        probeResult: OfishProbeResult,
        uploadChunkBytesProvider: UploadChunkBytesProvider,
    ): OfishMutationClient {
        val probe = OfishCapabilityProbe(client, OfishSessionFactory(client))
        return OfishMutationClient(
            client = client,
            sessionFactory = OfishSessionFactory(client),
            capabilityCache = FakeCapabilityCache(probe, probeResult),
            commandBuilder = OfishCommandBuilder(),
            uploadChunkBytes = uploadChunkBytesProvider,
        )
    }

    private class FailingUploadChunkBytesProvider : UploadChunkBytesProvider {
        override suspend fun get(capabilities: OfishCapabilities): Int {
            throw OfishUploadChunkProbeUnavailableException("OFISH upload chunk probe failed for test")
        }
    }

    private class RecordingUploadChunkBytesProvider(
        private val bytes: Int,
    ) : UploadChunkBytesProvider {
        var calls = 0
            private set

        override suspend fun get(capabilities: OfishCapabilities): Int {
            calls += 1
            assertFalse(capabilities.hashCommand == null)
            return bytes
        }
    }

    private class FakeCapabilityCache(
        probe: OfishCapabilityProbe,
        private val result: OfishProbeResult,
    ) : CachedOfishCapabilities(probe) {
        override suspend fun get(): OfishProbeResult = result
    }

    private class FakeOfishWorkspaceClient(
        val outputs: ArrayDeque<String> = ArrayDeque(),
    ) : OfishWorkspaceClient {
        override val workspace: Workspace = Workspace(
            server = ServerRef.fromEndpoint("http://localhost:4096", "local"),
            directory = "/repo",
        )
        val createdTitles = mutableListOf<String>()
        val deletedIds = mutableListOf<String>()
        val commands = mutableListOf<String>()

        override suspend fun createSession(title: String): SessionDto {
            delay(1)
            createdTitles += title
            return SessionDto(
                id = "session-${createdTitles.size}",
                projectID = "project",
                directory = "/repo",
                title = title,
                version = "test",
                time = TimeDto(created = 0),
            )
        }

        override suspend fun deleteSession(id: String): Boolean {
            deletedIds += id
            return true
        }

        override suspend fun executeShellCommand(sessionId: String, request: ShellCommandRequest): MessageWrapperDto {
            commands += request.command
            return message(outputs.removeFirstOrNull() ?: "### 200 ok")
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
