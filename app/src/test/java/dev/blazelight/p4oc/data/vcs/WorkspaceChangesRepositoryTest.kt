package dev.blazelight.p4oc.data.vcs

import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.OpenCodeApi
import dev.blazelight.p4oc.data.server.ActiveServerApiProvider
import dev.blazelight.p4oc.data.server.StaleWorkspaceClientException
import dev.blazelight.p4oc.data.workspace.VCS_DIFF_RESPONSE_LIMIT_BYTES
import dev.blazelight.p4oc.data.workspace.VCS_INFO_RESPONSE_LIMIT_BYTES
import dev.blazelight.p4oc.data.workspace.VCS_STATUS_RESPONSE_LIMIT_BYTES
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.workspace.Workspace
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class WorkspaceChangesRepositoryTest {
    @Test
    fun `snapshot strictly decodes Unicode statuses preserves order and computes checked totals`() = runTest {
        val api = mockk<OpenCodeApi>()
        val status = """
            [
              {"file":"z.kt","status":"modified","additions":5,"deletions":2},
              {"file":"資料/雪.kt","status":"added","additions":3,"deletions":0},
              {"file":"old/deleted.txt","status":"deleted","additions":0,"deletions":7}
            ]
        """.trimIndent()
        stubSnapshot(api, INFO_JSON, status, directory = "/work/alpha")
        val repository = repository(api, directory = "/work/alpha", serverLabel = "Alpha server")

        val snapshot = success(repository.loadSnapshot())

        assertEquals("Alpha server", snapshot.serverLabel)
        assertEquals("/work/alpha", snapshot.workspaceDirectory)
        assertEquals("feature/changes", snapshot.branch)
        assertEquals("main", snapshot.defaultBranch)
        assertEquals(listOf("z.kt", "資料/雪.kt", "old/deleted.txt"), snapshot.changes.map { it.file })
        assertEquals(
            listOf(WorkspaceChangeStatus.Modified, WorkspaceChangeStatus.Added, WorkspaceChangeStatus.Deleted),
            snapshot.changes.map { it.status },
        )
        assertEquals(8L, snapshot.additions)
        assertEquals(9L, snapshot.deletions)

        coVerify(exactly = 1) { api.getVcsInfoRaw("/work/alpha", null) }
        coVerify(exactly = 1) { api.getVcsStatusRaw("/work/alpha", null) }
        coVerify(exactly = 0) { api.getFileStatus(any(), any()) }
        coVerify(exactly = 0) { api.readFile(any(), any(), any()) }
        coVerify(exactly = 0) { api.readFileRaw(any(), any(), any()) }
        coVerify(exactly = 0) { api.getSessionDiff(any(), any(), any(), any()) }
        confirmVerified(api)
    }

    @Test
    fun `clean global workspace keeps explicit null identity and routing`() = runTest {
        val api = mockk<OpenCodeApi>()
        stubSnapshot(api, "{}", "[]", directory = null)
        val repository = repository(api, directory = null)

        val snapshot = success(repository.loadSnapshot())

        assertEquals(null, snapshot.workspaceDirectory)
        assertEquals(null, snapshot.branch)
        assertEquals(null, snapshot.defaultBranch)
        assertTrue(snapshot.changes.isEmpty())
        assertEquals(0L, snapshot.additions)
        assertEquals(0L, snapshot.deletions)
        coVerify(exactly = 1) { api.getVcsInfoRaw(null, null) }
        coVerify(exactly = 1) { api.getVcsStatusRaw(null, null) }
    }

    @Test
    fun `diff uses git context three and exact paths with optional patch`() = runTest {
        val api = mockk<OpenCodeApi>()
        val payload = """
            [
              {"file":"src/a.kt","patch":"@@ src patch","status":"modified","additions":2,"deletions":1},
              {"file":"old/deleted.txt","status":"deleted","additions":0,"deletions":4},
              {"file":"test/a.kt","patch":"@@ test patch","additions":1,"deletions":0}
            ]
        """.trimIndent()
        coEvery { api.getVcsDiffRaw(VcsDiffMode.Git, 3, "/repo", null) } returns jsonResponse(payload)
        val repository = repository(api)

        val patches = success(repository.loadDiff())

        assertEquals(listOf("src/a.kt", "old/deleted.txt", "test/a.kt"), patches.keys.toList())
        assertEquals(WorkspacePatch.Content("@@ src patch"), patches["src/a.kt"])
        assertEquals(WorkspacePatch.Unavailable, patches["old/deleted.txt"])
        assertEquals(WorkspacePatch.Content("@@ test patch"), patches["test/a.kt"])
        assertFalse(patches.containsKey("a.kt"))
        assertFalse(patches.containsKey("other/a.kt"))
        assertEquals(WorkspacePatch.Stale, patches["missing/a.kt"] ?: WorkspacePatch.Stale)

        coVerify(exactly = 1) { api.getVcsDiffRaw(VcsDiffMode.Git, 3, "/repo", null) }
        coVerify(exactly = 0) { api.getFileStatus(any(), any()) }
        coVerify(exactly = 0) { api.readFile(any(), any(), any()) }
        coVerify(exactly = 0) { api.readFileRaw(any(), any(), any()) }
        coVerify(exactly = 0) { api.getSessionDiff(any(), any(), any(), any()) }
        confirmVerified(api)
    }

    @Test
    fun `malformed status shapes and fields reject the whole snapshot`() = runTest {
        val malformedPayloads = listOf(
            "null",
            "{\"file\":\"a.kt\"}",
            "{malformed",
            "[{}]",
            "[{\"file\":4,\"status\":\"added\",\"additions\":1,\"deletions\":0}]",
            statusEntry(" "),
            "[${statusEntry("same.kt")},${statusEntry("same.kt")} ]",
            statusEntry("a.kt", status = "renamed"),
            statusEntry("a.kt", additions = "-1"),
            statusEntry("a.kt", deletions = "1.5"),
            statusEntry("a.kt", additions = "\"1\""),
            statusEntry("a.kt", additions = "NaN"),
            statusEntry("a.kt", deletions = "Infinity"),
            statusEntry("a.kt", additions = "9223372036854775808"),
            "[{\"file\":\"a.kt\",\"status\":\"added\",\"additions\":1}]",
        )

        malformedPayloads.forEach { payload ->
            val api = mockk<OpenCodeApi>()
            stubSnapshot(api, INFO_JSON, payload)
            assertEquals(payload, WorkspaceChangesResult.Malformed, repository(api).loadSnapshot())
        }
    }

    @Test
    fun `malformed identity shapes and types are rejected without requesting status`() = runTest {
        val malformedPayloads = listOf(
            "[]",
            "{malformed",
            "{\"branch\":4}",
            "{\"default_branch\":false}",
        )

        malformedPayloads.forEach { payload ->
            val api = mockk<OpenCodeApi>()
            coEvery { api.getVcsInfoRaw("/repo", null) } returns jsonResponse(payload)

            assertEquals(payload, WorkspaceChangesResult.Malformed, repository(api).loadSnapshot())

            coVerify(exactly = 0) { api.getVcsStatusRaw(any(), any()) }
        }
    }

    @Test
    fun `malformed diff shapes and fields reject the whole diff`() = runTest {
        val malformedPayloads = listOf(
            "null",
            "{}",
            "{malformed",
            "[{}]",
            "[{\"file\":3,\"additions\":1,\"deletions\":0}]",
            diffEntry(" "),
            "[${diffEntry("same.kt")},${diffEntry("same.kt")} ]",
            diffEntry("a.kt", status = "\"renamed\""),
            diffEntry("a.kt", status = "false"),
            diffEntry("a.kt", additions = "-1"),
            diffEntry("a.kt", deletions = "0.5"),
            diffEntry("a.kt", additions = "\"1\""),
            diffEntry("a.kt", additions = "NaN"),
            diffEntry("a.kt", deletions = "9223372036854775808"),
            "[{\"file\":\"a.kt\",\"patch\":4,\"additions\":1,\"deletions\":0}]",
            "[{\"file\":\"a.kt\",\"additions\":1}]",
        )

        malformedPayloads.forEach { payload ->
            val api = mockk<OpenCodeApi>()
            coEvery { api.getVcsDiffRaw(VcsDiffMode.Git, 3, "/repo", null) } returns jsonResponse(payload)
            assertEquals(payload, WorkspaceChangesResult.Malformed, repository(api).loadDiff())
        }
    }

    @Test
    fun `branch and path UTF-8 boundaries accept exact cap and reject cap plus one`() = runTest {
        val exactBranch = "b".repeat(512)
        val exactUnicodePath = "é".repeat(2048)
        val exactApi = mockk<OpenCodeApi>()
        stubSnapshot(
            exactApi,
            "{\"branch\":${jsonString(exactBranch)},\"default_branch\":${jsonString(exactBranch)}}",
            "[${statusEntry(exactUnicodePath)}]",
        )

        val exact = success(repository(exactApi).loadSnapshot())

        assertEquals(exactBranch, exact.branch)
        assertEquals(exactUnicodePath, exact.changes.single().file)

        val branchApi = mockk<OpenCodeApi>()
        stubSnapshot(branchApi, "{\"branch\":${jsonString("b".repeat(513))}}", "[]")
        assertEquals(WorkspaceChangesResult.TooLarge, repository(branchApi).loadSnapshot())

        val pathApi = mockk<OpenCodeApi>()
        stubSnapshot(pathApi, INFO_JSON, "[${statusEntry("p".repeat(4097))}]")
        assertEquals(WorkspaceChangesResult.TooLarge, repository(pathApi).loadSnapshot())

        val diffPathApi = mockk<OpenCodeApi>()
        coEvery { diffPathApi.getVcsDiffRaw(VcsDiffMode.Git, 3, "/repo", null) } returns
            jsonResponse("[${diffEntry("p".repeat(4097))}]")
        assertEquals(WorkspaceChangesResult.TooLarge, repository(diffPathApi).loadDiff())
    }

    @Test
    fun `status entry count accepts ten thousand and rejects ten thousand one`() = runTest {
        val exactPayload = entriesJson(10_000) { statusEntry(it) }
        val exactApi = mockk<OpenCodeApi>()
        stubSnapshot(exactApi, INFO_JSON, exactPayload)

        val exact = success(repository(exactApi).loadSnapshot())

        assertEquals(10_000, exact.changes.size)
        assertEquals(10_000L, exact.additions)

        val oversizedApi = mockk<OpenCodeApi>()
        stubSnapshot(oversizedApi, INFO_JSON, entriesJson(10_001) { statusEntry(it) })
        assertEquals(WorkspaceChangesResult.TooLarge, repository(oversizedApi).loadSnapshot())
    }

    @Test
    fun `diff entry count accepts ten thousand and rejects ten thousand one`() = runTest {
        val exactApi = mockk<OpenCodeApi>()
        coEvery { exactApi.getVcsDiffRaw(VcsDiffMode.Git, 3, "/repo", null) } returns
            jsonResponse(entriesJson(10_000) { diffEntry(it) })

        val exact = success(repository(exactApi).loadDiff())

        assertEquals(10_000, exact.size)

        val oversizedApi = mockk<OpenCodeApi>()
        coEvery { oversizedApi.getVcsDiffRaw(VcsDiffMode.Git, 3, "/repo", null) } returns
            jsonResponse(entriesJson(10_001) { diffEntry(it) })
        assertEquals(WorkspaceChangesResult.TooLarge, repository(oversizedApi).loadDiff())
    }

    @Test
    fun `checked status total overflow is malformed instead of wrapping`() = runTest {
        val api = mockk<OpenCodeApi>()
        val payload = "[${statusEntry("max.kt", additions = Long.MAX_VALUE.toString())}," +
            "${statusEntry("one.kt", additions = "1")}]"
        stubSnapshot(api, INFO_JSON, payload)

        assertEquals(WorkspaceChangesResult.Malformed, repository(api).loadSnapshot())
    }

    @Test
    fun `raw route overflows map to too large before decoding`() = runTest {
        val infoApi = mockk<OpenCodeApi>()
        val infoBody = declaredBody(VCS_INFO_RESPONSE_LIMIT_BYTES + 1L)
        coEvery { infoApi.getVcsInfoRaw("/repo", null) } returns Response.success(infoBody)
        assertSame(WorkspaceChangesResult.TooLarge, repository(infoApi).loadSnapshot())
        verify(exactly = 0) { infoBody.source() }
        verify(exactly = 1) { infoBody.close() }

        val statusApi = mockk<OpenCodeApi>()
        val statusBody = declaredBody(VCS_STATUS_RESPONSE_LIMIT_BYTES + 1L)
        coEvery { statusApi.getVcsInfoRaw("/repo", null) } returns jsonResponse(INFO_JSON)
        coEvery { statusApi.getVcsStatusRaw("/repo", null) } returns Response.success(statusBody)
        assertSame(WorkspaceChangesResult.TooLarge, repository(statusApi).loadSnapshot())
        verify(exactly = 0) { statusBody.source() }
        verify(exactly = 1) { statusBody.close() }

        val diffApi = mockk<OpenCodeApi>()
        val diffBody = declaredBody(VCS_DIFF_RESPONSE_LIMIT_BYTES + 1L)
        coEvery { diffApi.getVcsDiffRaw(VcsDiffMode.Git, 3, "/repo", null) } returns Response.success(diffBody)
        assertSame(WorkspaceChangesResult.TooLarge, repository(diffApi).loadDiff())
        verify(exactly = 0) { diffBody.source() }
        verify(exactly = 1) { diffBody.close() }
    }

    @Test
    fun `per-patch limit is local and oversized text is not retained`() = runTest {
        val exactPatch = "é".repeat(512 * 1024)
        val oversizedPatch = exactPatch + "x"
        val payload = "[" +
            diffEntry("exact.patch", patch = exactPatch) + "," +
            diffEntry("large.patch", patch = oversizedPatch) + "," +
            diffEntry("small.patch", patch = "ok") +
            "]"
        val api = mockk<OpenCodeApi>()
        coEvery { api.getVcsDiffRaw(VcsDiffMode.Git, 3, "/repo", null) } returns jsonResponse(payload)

        val patches = success(repository(api).loadDiff())

        assertEquals(WorkspacePatch.Content(exactPatch), patches["exact.patch"])
        assertSame(WorkspacePatch.TooLarge, patches["large.patch"])
        assertEquals(WorkspacePatch.Content("ok"), patches["small.patch"])
        assertTrue(patches["large.patch"] !is WorkspacePatch.Content)
    }

    @Test
    fun `aggregate patch boundary accepts eight MiB and rejects one extra byte`() = runTest {
        val oneMiB = "x".repeat(1024 * 1024)
        val exactPayload = (0 until 8).joinToString(prefix = "[", postfix = "]") { index ->
            diffEntry("$index.patch", patch = oneMiB)
        }
        val exactApi = mockk<OpenCodeApi>()
        coEvery { exactApi.getVcsDiffRaw(VcsDiffMode.Git, 3, "/repo", null) } returns jsonResponse(exactPayload)

        val exact = success(repository(exactApi).loadDiff())

        assertEquals(8, exact.size)
        assertTrue(exact.values.all { it is WorkspacePatch.Content })

        val oversizedPayload = exactPayload.dropLast(1) + "," + diffEntry("extra.patch", patch = "x") + "]"
        val oversizedApi = mockk<OpenCodeApi>()
        coEvery { oversizedApi.getVcsDiffRaw(VcsDiffMode.Git, 3, "/repo", null) } returns
            jsonResponse(oversizedPayload)
        assertEquals(WorkspaceChangesResult.TooLarge, repository(oversizedApi).loadDiff())
    }

    @Test
    fun `invalid UTF-8 in every VCS route maps to malformed before replacement decoding`() = runTest {
        val infoApi = mockk<OpenCodeApi>()
        coEvery { infoApi.getVcsInfoRaw("/repo", null) } returns invalidUtf8Response(
            "{\"branch\":\"".toByteArray() + BAD_UTF8_FIRST + "\"}".toByteArray(),
        )
        assertSame(WorkspaceChangesResult.Malformed, repository(infoApi).loadSnapshot())
        coVerify(exactly = 0) { infoApi.getVcsStatusRaw(any(), any()) }

        val statusApi = mockk<OpenCodeApi>()
        coEvery { statusApi.getVcsInfoRaw("/repo", null) } returns jsonResponse(INFO_JSON)
        coEvery { statusApi.getVcsStatusRaw("/repo", null) } returns invalidUtf8Response(
            distinctBadBytePathEntries(includePatch = false),
        )
        assertSame(WorkspaceChangesResult.Malformed, repository(statusApi).loadSnapshot())

        val diffApi = mockk<OpenCodeApi>()
        coEvery { diffApi.getVcsDiffRaw(VcsDiffMode.Git, 3, "/repo", null) } returns invalidUtf8Response(
            distinctBadBytePathEntries(includePatch = true),
        )
        assertSame(WorkspaceChangesResult.Malformed, repository(diffApi).loadDiff())
    }

    @Test
    fun `unsupported codes map consistently for identity status and diff`() = runTest {
        listOf(404, 405, 501).forEach { code ->
            val infoApi = mockk<OpenCodeApi>()
            coEvery { infoApi.getVcsInfoRaw("/repo", null) } returns errorResponse(code)
            assertEquals(WorkspaceChangesResult.Unsupported, repository(infoApi).loadSnapshot())

            val statusApi = mockk<OpenCodeApi>()
            coEvery { statusApi.getVcsInfoRaw("/repo", null) } returns jsonResponse(INFO_JSON)
            coEvery { statusApi.getVcsStatusRaw("/repo", null) } returns errorResponse(code)
            assertEquals(WorkspaceChangesResult.Unsupported, repository(statusApi).loadSnapshot())

            val diffApi = mockk<OpenCodeApi>()
            coEvery { diffApi.getVcsDiffRaw(VcsDiffMode.Git, 3, "/repo", null) } returns errorResponse(code)
            assertEquals(WorkspaceChangesResult.Unsupported, repository(diffApi).loadDiff())
        }
    }

    @Test
    fun `authorization codes map distinctly and safely for every route`() = runTest {
        listOf(401, 403).forEach { code ->
            FailureRoute.entries.forEach { route ->
                val api = mockk<OpenCodeApi>()
                stubHttpFailure(api, route, code, "credential-bearing authorization payload")
                assertSame(WorkspaceChangesResult.AuthorizationFailure, loadFailure(repository(api), route))
            }
        }
        assertNoMessageField(WorkspaceChangesResult.AuthorizationFailure)
    }

    @Test
    fun `other HTTP codes map distinctly and safely for every route`() = runTest {
        listOf(400, 429, 500).forEachIndexed { index, code ->
            val route = FailureRoute.entries[index]
            val api = mockk<OpenCodeApi>()
            stubHttpFailure(api, route, code, "https://user:password@example.test/private")
            assertSame(WorkspaceChangesResult.HttpFailure, loadFailure(repository(api), route))
        }
        assertNoMessageField(WorkspaceChangesResult.HttpFailure)
    }

    @Test
    fun `network failures map distinctly and safely for every route`() = runTest {
        FailureRoute.entries.forEach { route ->
            val api = mockk<OpenCodeApi>()
            stubThrownFailure(api, route, IOException("credential-bearing network detail"))
            assertSame(WorkspaceChangesResult.NetworkFailure, loadFailure(repository(api), route))
        }
        assertNoMessageField(WorkspaceChangesResult.NetworkFailure)
    }

    @Test
    fun `unexpected failures retain generic safe failure for every route`() = runTest {
        FailureRoute.entries.forEach { route ->
            val api = mockk<OpenCodeApi>()
            stubThrownFailure(api, route, IllegalStateException("unexpected sensitive detail"))
            assertSame(WorkspaceChangesResult.Failure, loadFailure(repository(api), route))
        }
        assertNoMessageField(WorkspaceChangesResult.Failure)
    }

    @Test
    fun `stale client maps separately and cancellation propagates exactly`() = runTest {
        val staleRepository = repository(
            api = mockk(),
            provider = ActiveServerApiProvider { _, _ ->
                throw StaleWorkspaceClientException("stale endpoint detail")
            },
        )
        assertSame(WorkspaceChangesResult.Stale, staleRepository.loadSnapshot())
        assertSame(WorkspaceChangesResult.Stale, staleRepository.loadDiff())

        val cancellation = CancellationException("cancel exact workspace load")
        val cancellationApi = mockk<OpenCodeApi>()
        coEvery { cancellationApi.getVcsInfoRaw("/repo", null) } throws cancellation
        try {
            repository(cancellationApi).loadSnapshot()
            fail("Expected cancellation")
        } catch (error: CancellationException) {
            assertSame(cancellation, error)
        }
    }

    private fun repository(
        api: OpenCodeApi,
        directory: String? = "/repo",
        serverLabel: String = "Test server",
        provider: ActiveServerApiProvider = ActiveServerApiProvider { _, _ -> api },
    ): WorkspaceChangesRepository = WorkspaceChangesRepositoryImpl(
        WorkspaceClient(
            workspace = Workspace(
                server = ServerRef.fromEndpointKey("http://test.local", serverLabel),
                directory = directory,
            ),
            generation = ServerGeneration(1L),
            apiProvider = provider,
            connectionState = MutableStateFlow(ConnectionState.Connected),
        ),
    )

    private fun stubSnapshot(
        api: OpenCodeApi,
        info: String,
        status: String,
        directory: String? = "/repo",
    ) {
        coEvery { api.getVcsInfoRaw(directory, null) } returns jsonResponse(info)
        coEvery { api.getVcsStatusRaw(directory, null) } returns jsonResponse(status)
    }

    private fun statusEntry(
        file: String,
        status: String = "added",
        additions: String = "1",
        deletions: String = "0",
    ): String = """{"file":${jsonString(file)},"status":"$status","additions":$additions,"deletions":$deletions}"""

    private fun diffEntry(
        file: String,
        patch: String? = null,
        status: String? = null,
        additions: String = "1",
        deletions: String = "0",
    ): String = buildString {
        append("{\"file\":")
        append(jsonString(file))
        if (patch != null) {
            append(",\"patch\":")
            append(jsonString(patch))
        }
        if (status != null) {
            append(",\"status\":")
            append(status)
        }
        append(",\"additions\":")
        append(additions)
        append(",\"deletions\":")
        append(deletions)
        append('}')
    }

    private fun entriesJson(count: Int, entry: (String) -> String): String =
        (0 until count).joinToString(prefix = "[", postfix = "]") { index -> entry("file-$index") }

    private fun jsonString(value: String): String = Json.Default.encodeToString(value)

    private fun jsonResponse(content: String): Response<ResponseBody> =
        Response.success(content.toResponseBody(JSON_MEDIA_TYPE))

    private fun errorResponse(code: Int, content: String = "error body"): Response<ResponseBody> =
        Response.error(code, content.toResponseBody(JSON_MEDIA_TYPE))

    private fun invalidUtf8Response(content: ByteArray): Response<ResponseBody> =
        Response.success(content.toResponseBody(JSON_MEDIA_TYPE))

    private fun distinctBadBytePathEntries(includePatch: Boolean): ByteArray {
        val fields = if (includePatch) {
            "\",\"patch\":\"patch\",\"additions\":1,\"deletions\":0}"
        } else {
            "\",\"status\":\"modified\",\"additions\":1,\"deletions\":0}"
        }
        return "[{\"file\":\"".toByteArray() + BAD_UTF8_FIRST + fields.toByteArray() +
            ",{\"file\":\"".toByteArray() + BAD_UTF8_SECOND + fields.toByteArray() + "]".toByteArray()
    }

    private fun stubHttpFailure(api: OpenCodeApi, route: FailureRoute, code: Int, content: String) {
        when (route) {
            FailureRoute.Info -> coEvery { api.getVcsInfoRaw("/repo", null) } returns errorResponse(code, content)
            FailureRoute.Status -> {
                coEvery { api.getVcsInfoRaw("/repo", null) } returns jsonResponse(INFO_JSON)
                coEvery { api.getVcsStatusRaw("/repo", null) } returns errorResponse(code, content)
            }
            FailureRoute.Diff -> coEvery {
                api.getVcsDiffRaw(VcsDiffMode.Git, 3, "/repo", null)
            } returns errorResponse(code, content)
        }
    }

    private fun stubThrownFailure(api: OpenCodeApi, route: FailureRoute, error: Exception) {
        when (route) {
            FailureRoute.Info -> coEvery { api.getVcsInfoRaw("/repo", null) } throws error
            FailureRoute.Status -> {
                coEvery { api.getVcsInfoRaw("/repo", null) } returns jsonResponse(INFO_JSON)
                coEvery { api.getVcsStatusRaw("/repo", null) } throws error
            }
            FailureRoute.Diff -> coEvery {
                api.getVcsDiffRaw(VcsDiffMode.Git, 3, "/repo", null)
            } throws error
        }
    }

    private suspend fun loadFailure(
        repository: WorkspaceChangesRepository,
        route: FailureRoute,
    ): WorkspaceChangesResult<*> = when (route) {
        FailureRoute.Info, FailureRoute.Status -> repository.loadSnapshot()
        FailureRoute.Diff -> repository.loadDiff()
    }

    private fun assertNoMessageField(result: WorkspaceChangesResult<Nothing>) {
        assertTrue(result::class.java.declaredFields.none { it.name == "message" })
    }

    private fun declaredBody(contentLength: Long): ResponseBody {
        val body = mockk<ResponseBody>(relaxed = true)
        every { body.contentLength() } returns contentLength
        return body
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> success(result: WorkspaceChangesResult<T>): T {
        assertTrue("Expected success but was $result", result is WorkspaceChangesResult.Success)
        return (result as WorkspaceChangesResult.Success<T>).data
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val BAD_UTF8_FIRST = byteArrayOf(0x80.toByte())
        val BAD_UTF8_SECOND = byteArrayOf(0x81.toByte())
        const val INFO_JSON = """{"branch":"feature/changes","default_branch":"main"}"""
    }

    private enum class FailureRoute {
        Info,
        Status,
        Diff,
    }
}
