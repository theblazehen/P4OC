package dev.blazelight.p4oc.data.files.ofish

import dev.blazelight.p4oc.data.files.FileCapabilities
import dev.blazelight.p4oc.data.files.FileList
import dev.blazelight.p4oc.data.files.FileOperationResult
import dev.blazelight.p4oc.data.files.FileRepository
import dev.blazelight.p4oc.data.files.FileUploadRequest
import dev.blazelight.p4oc.data.files.FileUploadResult
import dev.blazelight.p4oc.data.files.FileWriteRequest
import dev.blazelight.p4oc.data.files.FileWriteResult
import dev.blazelight.p4oc.data.remote.dto.MessageInfoDto
import dev.blazelight.p4oc.data.remote.dto.MessageTimeDto
import dev.blazelight.p4oc.data.remote.dto.MessageWrapperDto
import dev.blazelight.p4oc.data.remote.dto.PartDto
import dev.blazelight.p4oc.data.remote.dto.PermissionResponseRequest
import dev.blazelight.p4oc.data.remote.dto.SessionDto
import dev.blazelight.p4oc.data.remote.dto.ShellCommandRequest
import dev.blazelight.p4oc.data.remote.dto.TimeDto
import dev.blazelight.p4oc.domain.model.FileContent
import dev.blazelight.p4oc.domain.model.Symbol
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.workspace.Workspace
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfishFileRepositoryTest {
    private val shaCapabilities = OfishCapabilities(
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
    fun `readFile adds shell-derived baseline hash when capabilities are available`() = runTest {
        val client = FakeOfishWorkspaceClient(
            hashFor = mapOf("file.txt" to "deadbeefcafebabe"),
        )
        val repository = repository(
            content = FileContent(content = "hello\n"),
            probeResult = OfishProbeResult.Available(shaCapabilities),
            client = client,
        )

        val result = repository.readFile("file.txt")

        assertTrue(result is FileOperationResult.Ok)
        assertEquals("deadbeefcafebabe", (result as FileOperationResult.Ok).data.hash)
    }

    @Test
    fun `readFile honest-fallbacks to null when shell hash returns no hash`() = runTest {
        // Default fake responds with `### 200 ok` (no hash key). Repository
        // should NOT silently digest in-memory bytes; it should yield null.
        val repository = repository(
            content = FileContent(content = "hello\n"),
            probeResult = OfishProbeResult.Available(shaCapabilities),
            client = FakeOfishWorkspaceClient(),
        )

        val result = repository.readFile("file.txt")

        assertTrue(result is FileOperationResult.Ok)
        assertNull((result as FileOperationResult.Ok).data.hash)
    }

    @Test
    fun `readFile leaves hash null when capabilities are missing`() = runTest {
        val repository = repository(
            content = FileContent(content = "hello\n"),
            probeResult = OfishProbeResult.Missing(listOf("hash"), partial = null),
        )

        val result = repository.readFile("file.txt")

        assertTrue(result is FileOperationResult.Ok)
        assertNull((result as FileOperationResult.Ok).data.hash)
    }

    @Test
    fun `readFile leaves hash null when capabilities failed`() = runTest {
        val repository = repository(
            content = FileContent(content = "hello\n"),
            probeResult = OfishProbeResult.Failed("probe failed"),
        )

        val result = repository.readFile("file.txt")

        assertTrue(result is FileOperationResult.Ok)
        assertNull((result as FileOperationResult.Ok).data.hash)
    }

    @Test
    fun `readFile leaves hash null for non text content`() = runTest {
        val repository = repository(
            content = FileContent(type = "binary", content = "hello\n"),
            probeResult = OfishProbeResult.Available(shaCapabilities),
        )

        val result = repository.readFile("file.bin")

        assertTrue(result is FileOperationResult.Ok)
        assertNull((result as FileOperationResult.Ok).data.hash)
    }

    @Test
    fun `readFile leaves hash null for encoded content`() = runTest {
        val repository = repository(
            content = FileContent(content = "aGVsbG8K", encoding = "base64"),
            probeResult = OfishProbeResult.Available(shaCapabilities),
        )

        val result = repository.readFile("file.txt")

        assertTrue(result is FileOperationResult.Ok)
        assertNull((result as FileOperationResult.Ok).data.hash)
    }

    @Test
    fun `readFile returns delegate failure unchanged`() = runTest {
        val delegate = FakeRepository(readResult = FileOperationResult.Failed("boom"))
        val repository = OfishFileRepository(
            delegate,
            mutationClient(OfishProbeResult.Available(shaCapabilities), FakeOfishWorkspaceClient()),
        )

        val result = repository.readFile("file.txt")

        assertTrue(result is FileOperationResult.Failed)
        assertEquals("boom", (result as FileOperationResult.Failed).message)
    }

    private fun repository(
        content: FileContent,
        probeResult: OfishProbeResult,
        client: FakeOfishWorkspaceClient = FakeOfishWorkspaceClient(),
    ): OfishFileRepository = OfishFileRepository(
        delegate = FakeRepository(FileOperationResult.Ok(content)),
        mutationClient = mutationClient(probeResult, client),
    )

    private fun mutationClient(
        probeResult: OfishProbeResult,
        client: FakeOfishWorkspaceClient,
    ): OfishMutationClient {
        val probe = OfishCapabilityProbe(client, OfishSessionFactory(client))
        return OfishMutationClient(
            client = client,
            sessionFactory = OfishSessionFactory(client),
            capabilityCache = FakeCapabilityCache(probe, probeResult),
            commandBuilder = OfishCommandBuilder(),
        )
    }

    private class FakeCapabilityCache(
        probe: OfishCapabilityProbe,
        private val result: OfishProbeResult,
    ) : CachedOfishCapabilities(probe) {
        override suspend fun get(): OfishProbeResult = result
    }

    private class FakeRepository(
        private val readResult: FileOperationResult<FileContent>,
    ) : FileRepository {
        override suspend fun listFiles(path: String): FileOperationResult<FileList> =
            FileOperationResult.Ok(FileList(path, emptyList()))

        override suspend fun readFile(path: String): FileOperationResult<FileContent> = readResult

        override suspend fun searchSymbols(query: String): FileOperationResult<List<Symbol>> =
            FileOperationResult.Ok(emptyList())

        override suspend fun writeFile(request: FileWriteRequest): FileOperationResult<FileWriteResult> =
            FileOperationResult.Ok(FileWriteResult(request.path))

        override suspend fun deleteFile(path: String): FileOperationResult<Unit> = FileOperationResult.Ok(Unit)

        override suspend fun uploadFile(request: FileUploadRequest): FileOperationResult<FileUploadResult> =
            FileOperationResult.Ok(FileUploadResult(request.path))

        override suspend fun capabilities(): FileCapabilities = FileCapabilities()
    }

    private class FakeOfishWorkspaceClient(
        // Map of normalized path -> deterministic hash to return when the
        // executed shell command targets that path. Other commands fall back
        // to plain `### 200 ok` (no hash).
        private val hashFor: Map<String, String> = emptyMap(),
    ) : OfishWorkspaceClient {
        override val workspace: Workspace = Workspace(
            server = ServerRef.fromEndpoint("http://localhost:4096", "local"),
            directory = "/repo",
        )

        override suspend fun createSession(title: String): SessionDto = SessionDto(
            id = "session-1",
            projectID = "project",
            directory = "/repo",
            title = title,
            version = "test",
            time = TimeDto(created = 0),
        )

        override suspend fun deleteSession(id: String): Boolean = true

        override suspend fun executeShellCommand(sessionId: String, request: ShellCommandRequest): MessageWrapperDto {
            val encoded = Regex("printf %s '?([A-Za-z0-9+/=]+)'? ").find(request.command)?.groupValues?.get(1)
            val script = encoded?.let { String(java.util.Base64.getDecoder().decode(it), Charsets.UTF_8) }.orEmpty()
            val responseText = if (script.contains("#OFISH_HASH")) {
                val path = hashFor.keys.firstOrNull { script.contains(it) }
                if (path != null) "### 200 ok hash=${hashFor.getValue(path)}" else "### 200 ok"
            } else {
                "### 200 ok"
            }
            return MessageWrapperDto(
                info = MessageInfoDto(
                    id = "message",
                    sessionID = sessionId,
                    time = MessageTimeDto(created = 0),
                    role = "assistant",
                ),
                parts = listOf(
                    PartDto(
                        id = "part",
                        sessionID = sessionId,
                        messageID = "message",
                        type = "text",
                        text = responseText,
                    ),
                ),
            )
        }

        override suspend fun listSessionsCurrentWorkspace(limit: Int?): List<SessionDto> = emptyList()

        override suspend fun respondToPermission(id: String, request: PermissionResponseRequest): Boolean = true
    }
}
