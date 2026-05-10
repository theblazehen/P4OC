package dev.blazelight.p4oc.ui.screens.files.upload

import dev.blazelight.p4oc.data.files.FileCapabilities
import dev.blazelight.p4oc.data.files.FileList
import dev.blazelight.p4oc.data.files.FileOperationResult
import dev.blazelight.p4oc.data.files.FileRepository
import dev.blazelight.p4oc.data.files.FileUploadRequest
import dev.blazelight.p4oc.data.files.FileUploadResult
import dev.blazelight.p4oc.data.files.FileWriteRequest
import dev.blazelight.p4oc.data.files.FileWriteResult
import dev.blazelight.p4oc.domain.model.FileContent
import dev.blazelight.p4oc.domain.model.Symbol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UploadOrchestratorTest {

    private fun plan(id: String, name: String = "$id.txt", size: Long = 4L, mime: String? = "text/plain") =
        UploadOrchestrator.Plan(sourceId = id, displayName = name, sizeBytes = size, mimeType = mime)

    @Test
    fun `single ok upload finishes Done and writes to current path`() = runTest {
        val source = FakeUploadSource(mapOf("u1" to byteArrayOf(1, 2, 3, 4)))
        val repo = FakeFileRepository(uploadOutcomes = mutableListOf(ok("dir/u1.txt")))
        val orchestrator = UploadOrchestrator(repo, source, retryDelayMillis = { 0L })

        val state = orchestrator.run("dir", listOf(plan("u1")))

        assertEquals(1, state.successes.size)
        assertEquals(0, state.failures.size)
        assertEquals("dir/u1.txt", repo.uploadCalls.single().path)
        assertTrue(state.items.single().phase is UploadPhase.Done)
    }

    @Test
    fun `multi item progresses currentIndex and refreshes ordering`() = runTest {
        val source = FakeUploadSource(
            mapOf("a" to byteArrayOf(1), "b" to byteArrayOf(2), "c" to byteArrayOf(3))
        )
        val repo = FakeFileRepository(
            uploadOutcomes = mutableListOf(ok("a.txt"), ok("b.txt"), ok("c.txt"))
        )
        val orchestrator = UploadOrchestrator(repo, source, retryDelayMillis = { 0L })

        val state = orchestrator.run("", listOf(plan("a", "a.txt"), plan("b", "b.txt"), plan("c", "c.txt")))

        assertEquals(3, state.items.size)
        assertEquals(3, state.successes.size)
        assertEquals(listOf("a.txt", "b.txt", "c.txt"), repo.uploadCalls.map { it.path })
    }

    @Test
    fun `failure retries up to maxAttempts and succeeds`() = runTest {
        val source = FakeUploadSource(mapOf("x" to byteArrayOf(9)))
        val repo = FakeFileRepository(
            uploadOutcomes = mutableListOf(
                FileOperationResult.Failed("transient"),
                FileOperationResult.Failed("transient"),
                ok("x.bin"),
            )
        )
        val orchestrator = UploadOrchestrator(repo, source, retryDelayMillis = { 0L })

        val state = orchestrator.run("", listOf(plan("x", "x.bin")))

        assertEquals(3, repo.uploadCalls.size)
        assertEquals(1, state.successes.size)
        assertEquals(3, state.items.single().attempts)
    }

    @Test
    fun `three failures terminal Failed surface message`() = runTest {
        val source = FakeUploadSource(mapOf("x" to byteArrayOf(9)))
        val repo = FakeFileRepository(
            uploadOutcomes = mutableListOf(
                FileOperationResult.Failed("net1"),
                FileOperationResult.Failed("net2"),
                FileOperationResult.Failed("net3"),
            )
        )
        val orchestrator = UploadOrchestrator(repo, source, retryDelayMillis = { 0L })

        val state = orchestrator.run("", listOf(plan("x", "x.bin")))

        assertEquals(3, repo.uploadCalls.size)
        val phase = state.items.single().phase
        assertTrue(phase is UploadPhase.Failed)
        assertEquals("net3", (phase as UploadPhase.Failed).message)
    }

    @Test
    fun `conflict is not retried`() = runTest {
        val source = FakeUploadSource(mapOf("c" to byteArrayOf(1)))
        val repo = FakeFileRepository(
            uploadOutcomes = mutableListOf(FileOperationResult.Conflict("hash mismatch", "abc"))
        )
        val orchestrator = UploadOrchestrator(repo, source, retryDelayMillis = { 0L })

        val state = orchestrator.run("", listOf(plan("c", "c.txt")))

        assertEquals(1, repo.uploadCalls.size)
        val phase = state.items.single().phase
        assertTrue(phase is UploadPhase.Failed)
        assertEquals("hash mismatch", (phase as UploadPhase.Failed).message)
    }

    @Test
    fun `partial batch preserves successes and failures`() = runTest {
        val source = FakeUploadSource(mapOf("a" to byteArrayOf(1), "b" to byteArrayOf(2)))
        val repo = FakeFileRepository(
            uploadOutcomes = mutableListOf(
                ok("a.txt"),
                FileOperationResult.Failed("bad"),
                FileOperationResult.Failed("bad"),
                FileOperationResult.Failed("bad"),
            )
        )
        val orchestrator = UploadOrchestrator(repo, source, retryDelayMillis = { 0L })

        val state = orchestrator.run("", listOf(plan("a", "a.txt"), plan("b", "b.txt")))

        assertEquals(1, state.successes.size)
        assertEquals(1, state.failures.size)
        assertEquals("a.txt", state.successes.single().displayName)
        assertEquals("b.txt", state.failures.single().displayName)
    }

    @Test
    fun `oversize file rejected without calling repository`() = runTest {
        val source = FakeUploadSource(mapOf("big" to ByteArray(10)), failOversize = true)
        val repo = FakeFileRepository(uploadOutcomes = mutableListOf())
        val orchestrator = UploadOrchestrator(repo, source, maxBytes = 4L, retryDelayMillis = { 0L })

        val state = orchestrator.run("", listOf(plan("big", "big.bin", size = 10)))

        assertEquals(0, repo.uploadCalls.size)
        val phase = state.items.single().phase
        assertTrue(phase is UploadPhase.Failed)
    }

    @Test
    fun `retryFailed reruns only failed items and keeps successes`() = runTest {
        val source = FakeUploadSource(mapOf("a" to byteArrayOf(1), "b" to byteArrayOf(2)))
        val repo = FakeFileRepository(
            uploadOutcomes = mutableListOf(
                ok("a.txt"),
                FileOperationResult.Failed("e"),
                FileOperationResult.Failed("e"),
                FileOperationResult.Failed("e"),
                // retry attempts on b only
                ok("b.txt"),
            )
        )
        val orchestrator = UploadOrchestrator(repo, source, retryDelayMillis = { 0L })

        orchestrator.run("", listOf(plan("a", "a.txt"), plan("b", "b.txt")))
        // 4 calls so far: a ok, b * 3 fail
        assertEquals(4, repo.uploadCalls.size)

        orchestrator.retryFailed()

        assertEquals(5, repo.uploadCalls.size)
        val items = orchestrator.state.value.items
        assertEquals(2, items.size)
        assertTrue(items[0].phase is UploadPhase.Done)
        assertTrue(items[1].phase is UploadPhase.Done)
    }

    @Test
    fun `markCancelled rewrites in-flight items to Failed cancelled and preserves Done`() = runTest {
        // Drive the orchestrator into a partial-progress state: item "a"
        // completes, then item "b" suspends forever so we can cancel.
        val source = SuspendingUploadSource(
            ready = mapOf("a" to byteArrayOf(1)),
            suspendForever = setOf("b"),
        )
        val repo = FakeFileRepository(uploadOutcomes = mutableListOf(ok("a.txt")))
        val orchestrator = UploadOrchestrator(repo, source, retryDelayMillis = { 0L })

        val runJob = launch {
            orchestrator.run("", listOf(plan("a", "a.txt"), plan("b", "b.txt")))
        }
        // Let the orchestrator advance until "b" suspends in readBytes.
        source.readingB.await()

        orchestrator.markCancelled()
        runJob.cancel()
        runJob.join()

        val items = orchestrator.state.value.items
        assertEquals(2, items.size)
        assertTrue("expected Done for a, got ${items[0].phase}", items[0].phase is UploadPhase.Done)
        val bPhase = items[1].phase
        assertTrue("expected Failed for b, got $bPhase", bPhase is UploadPhase.Failed)
        assertEquals("cancelled", (bPhase as UploadPhase.Failed).message)
        assertTrue(!orchestrator.state.value.isActive)
        assertTrue(orchestrator.state.value.cancelled)
    }

    @Test
    fun `destination joins current path with sanitized name`() = runTest {
        val source = FakeUploadSource(mapOf("u" to byteArrayOf(1)))
        val repo = FakeFileRepository(uploadOutcomes = mutableListOf(ok("dir/sub/evil_x.bin")))
        val orchestrator = UploadOrchestrator(repo, source, retryDelayMillis = { 0L })

        orchestrator.run("dir/sub", listOf(plan("u", "evil/x.bin")))

        assertEquals("dir/sub/evil_x.bin", repo.uploadCalls.single().path)
    }

    private fun ok(path: String) = FileOperationResult.Ok(FileUploadResult(path = path, hash = null))
}

private class FakeUploadSource(
    private val payloads: Map<String, ByteArray>,
    private val failOversize: Boolean = false,
) : UploadSource {
    override suspend fun probe(sourceId: String): UploadSourceMetadata {
        val bytes = payloads[sourceId] ?: ByteArray(0)
        return UploadSourceMetadata(displayName = sourceId, sizeBytes = bytes.size.toLong(), mimeType = "application/octet-stream")
    }

    override suspend fun readBytes(sourceId: String, maxBytes: Long): ByteArray {
        val bytes = payloads[sourceId] ?: throw IllegalStateException("no payload for $sourceId")
        if (failOversize && bytes.size.toLong() > maxBytes) {
            throw UploadTooLargeException(bytes.size.toLong(), maxBytes)
        }
        return bytes
    }
}

private class SuspendingUploadSource(
    private val ready: Map<String, ByteArray>,
    private val suspendForever: Set<String>,
) : UploadSource {
    val readingB = CompletableDeferred<Unit>()
    override suspend fun probe(sourceId: String): UploadSourceMetadata {
        val bytes = ready[sourceId] ?: ByteArray(0)
        return UploadSourceMetadata(displayName = sourceId, sizeBytes = bytes.size.toLong(), mimeType = null)
    }
    override suspend fun readBytes(sourceId: String, maxBytes: Long): ByteArray {
        if (sourceId in suspendForever) {
            readingB.complete(Unit)
            awaitCancellation()
        }
        return ready[sourceId] ?: ByteArray(0)
    }
}

private class FakeFileRepository(
    private val uploadOutcomes: MutableList<FileOperationResult<FileUploadResult>>,
) : FileRepository {
    val uploadCalls = mutableListOf<FileUploadRequest>()

    override suspend fun listFiles(path: String) =
        FileOperationResult.Ok(FileList(path = path, files = emptyList()))
    override suspend fun readFile(path: String) =
        FileOperationResult.Ok(FileContent(type = "text", content = "", diff = null, mimeType = null))
    override suspend fun searchSymbols(query: String) =
        FileOperationResult.Ok<List<Symbol>>(emptyList())
    override suspend fun writeFile(request: FileWriteRequest) =
        FileOperationResult.Ok(FileWriteResult(path = request.path, hash = null))
    override suspend fun deleteFile(path: String) = FileOperationResult.Ok(Unit)
    override suspend fun uploadFile(request: FileUploadRequest): FileOperationResult<FileUploadResult> {
        uploadCalls += request
        return uploadOutcomes.removeAt(0)
    }
    override suspend fun capabilities() = FileCapabilities(canUpload = true)
}
