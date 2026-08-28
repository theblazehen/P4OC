package dev.blazelight.p4oc.data.files

import dev.blazelight.p4oc.data.remote.dto.FileContentDto
import dev.blazelight.p4oc.data.remote.dto.FileNodeDto
import dev.blazelight.p4oc.data.remote.dto.FileStatusDto
import dev.blazelight.p4oc.data.remote.dto.PositionDto
import dev.blazelight.p4oc.data.remote.dto.RangeDto
import dev.blazelight.p4oc.data.remote.dto.SymbolDto
import dev.blazelight.p4oc.data.remote.dto.SymbolLocationDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class WorkspaceFileRepositoryTest {
    @Test
    fun `listFiles uses dot at client boundary for root and returns empty UI path`() = runTest {
        val client = FakeFileWorkspaceClient(
            files = listOf(
                FileNodeDto(name = "README.md", path = "README.md", absolute = "/repo/README.md", type = "file")
            ),
        )
        val repository = WorkspaceFileRepository(client)

        val result = repository.listFiles("")

        assertTrue(result is FileOperationResult.Ok)
        val data = (result as FileOperationResult.Ok).data
        assertEquals(listOf("."), client.listFilesPaths)
        assertEquals("", data.path)
        assertEquals("README.md", data.files.single().name)
    }

    @Test
    fun `listFiles ignores status failure merges statuses and sorts directories first`() = runTest {
        val client = FakeFileWorkspaceClient(
            files = listOf(
                FileNodeDto(name = "z.kt", path = "z.kt", absolute = "/repo/z.kt", type = "file"),
                FileNodeDto(name = "app", path = "app", absolute = "/repo/app", type = "directory"),
                FileNodeDto(name = "a.kt", path = "a.kt", absolute = "/repo/a.kt", type = "file"),
            ),
            statuses = listOf(FileStatusDto(path = "a.kt", status = "modified")),
        )
        val repository = WorkspaceFileRepository(client)

        val result = repository.listFiles("src//./main") as FileOperationResult.Ok

        assertEquals(listOf("src/main"), client.listFilesPaths)
        assertEquals("src/main", result.data.path)
        assertEquals(listOf("app", "a.kt", "z.kt"), result.data.files.map { it.name })
        assertEquals("modified", result.data.files.single { it.path == "a.kt" }.gitStatus)

        client.statusFailure = IllegalStateException("status unavailable")
        val statusFailureResult = repository.listFiles("src")
        assertTrue(statusFailureResult is FileOperationResult.Ok)
    }

    @Test
    fun `listFiles failure returns failed`() = runTest {
        val client = FakeFileWorkspaceClient(filesFailure = IllegalStateException("list failed"))
        val repository = WorkspaceFileRepository(client)

        val result = repository.listFiles("src")

        assertTrue(result is FileOperationResult.Failed)
        assertEquals("list failed", (result as FileOperationResult.Failed).message)
    }

    @Test
    fun `readFile rejects unsafe path before client call`() = runTest {
        val client = FakeFileWorkspaceClient(content = FileContentDto(type = "text", content = "secret"))
        val repository = WorkspaceFileRepository(client)

        val result = repository.readFile("../secret")

        assertTrue(result is FileOperationResult.Failed)
        assertTrue(client.readFilePaths.isEmpty())
    }

    @Test
    fun `readFile maps content dto to domain`() = runTest {
        val client = FakeFileWorkspaceClient(
            content = FileContentDto(type = "text", content = "hello", diff = "diff", mimeType = "text/plain"),
        )
        val repository = WorkspaceFileRepository(client)

        val result = repository.readFile("src//./Main.kt") as FileOperationResult.Ok

        assertEquals(listOf("src/Main.kt"), client.readFilePaths)
        assertEquals("hello", result.data.content)
        assertEquals("diff", result.data.diff)
        assertEquals("text/plain", result.data.mimeType)
    }

    @Test
    fun `searchFiles trims query and returns empty without a client call for blank query`() = runTest {
        val client = FakeFileWorkspaceClient(searchPaths = listOf("src/Main.kt"))
        val repository = WorkspaceFileRepository(client)

        val blankResult = repository.searchFiles("   \t\n") as FileOperationResult.Ok
        assertTrue(blankResult.data.isEmpty())
        assertTrue(client.fileSearchQueries.isEmpty())

        val result = repository.searchFiles("  Main  ") as FileOperationResult.Ok
        assertEquals(listOf("Main"), client.fileSearchQueries)
        assertEquals(listOf("src/Main.kt"), result.data.map { it.path })
    }

    @Test
    fun `searchFiles preserves relevance order and maps paths to file nodes`() = runTest {
        val client = FakeFileWorkspaceClient(
            searchPaths = listOf(
                "feature/LeastAlphabetical.kt",
                "app/src/MostRelevant.kt",
                "README.md",
            )
        )
        val repository = WorkspaceFileRepository(client)

        val result = repository.searchFiles("kt") as FileOperationResult.Ok

        assertEquals(
            listOf("feature/LeastAlphabetical.kt", "app/src/MostRelevant.kt", "README.md"),
            result.data.map { it.path },
        )
        assertEquals(listOf("LeastAlphabetical.kt", "MostRelevant.kt", "README.md"), result.data.map { it.name })
        assertTrue(result.data.all { it.type == "file" })
    }

    @Test
    fun `searchFiles normalizes and deduplicates paths while discarding unsafe and root results`() = runTest {
        val client = FakeFileWorkspaceClient(
            searchPaths = listOf(
                "src//./Main.kt",
                "../secret.txt",
                "src/Main.kt",
                ".",
                "/absolute.txt",
                "safe/../escape.txt",
                "src//Second.kt",
                "https://example.com/file.kt",
                "~/.private",
                " / ",
            )
        )
        val repository = WorkspaceFileRepository(client)

        val result = repository.searchFiles("src") as FileOperationResult.Ok

        assertEquals(listOf("src/Main.kt", "src/Second.kt"), result.data.map { it.path })
    }

    @Test
    fun `searchFiles propagates client failure as human readable failed result`() = runTest {
        val failure = IllegalStateException("file search unavailable")
        val client = FakeFileWorkspaceClient().apply { searchFileFailure = failure }
        val repository = WorkspaceFileRepository(client)

        val result = repository.searchFiles("Main")

        assertTrue(result is FileOperationResult.Failed)
        result as FileOperationResult.Failed
        assertEquals("file search unavailable", result.message)
        assertEquals(failure, result.cause)
    }

    @Test
    fun `searchSymbols trims query and returns empty for blank query`() = runTest {
        val client = FakeFileWorkspaceClient(symbols = listOf(symbolDto("Main")))
        val repository = WorkspaceFileRepository(client)

        val blankResult = repository.searchSymbols("   ") as FileOperationResult.Ok
        assertTrue(blankResult.data.isEmpty())
        assertTrue(client.searchQueries.isEmpty())

        val result = repository.searchSymbols(" Main ") as FileOperationResult.Ok
        assertEquals(listOf("Main"), result.data.map { it.name })
        assertEquals(listOf("Main"), client.searchQueries)
    }

    @Test
    fun `searchSymbols exposes decoded path for symbol file uris`() = runTest {
        val client = FakeFileWorkspaceClient(
            symbols = listOf(
                symbolDto(
                    "Main",
                    uri = "file:///src/My%20File%20%25/%C3%BCmlaut/%E3%81%93%E3%82%93%E3%81%AB%E3%81%A1%E3%81%AF/hash%23query%3F.kt",
                )
            )
        )
        val repository = WorkspaceFileRepository(client)

        val result = repository.searchSymbols("Main") as FileOperationResult.Ok

        assertEquals("src/My File %/ümlaut/こんにちは/hash#query?.kt", result.data.single().path)
    }

    @Test
    fun `mutations validate paths before returning unsupported`() = runTest {
        val repository = WorkspaceFileRepository(FakeFileWorkspaceClient())

        assertTrue(repository.writeFile(FileWriteRequest(path = "", content = "content")) is FileOperationResult.Failed)
        assertTrue(repository.deleteFile("../secret") is FileOperationResult.Failed)

        val unsupported = repository.uploadFile(
            FileUploadRequest(
                path = "safe.txt",
                contentLength = 1,
                openStream = { ByteArrayInputStream(byteArrayOf(1)) },
            )
        )
        assertTrue(unsupported is FileOperationResult.Failed)
        assertFalse(repository.capabilities().canUpload)
    }

    private fun symbolDto(name: String, uri: String = "file:///repo/$name.kt"): SymbolDto = SymbolDto(
        name = name,
        kind = 1,
        location = SymbolLocationDto(
            uri = uri,
            range = RangeDto(
                start = PositionDto(line = 0, character = 0),
                end = PositionDto(line = 0, character = 1),
            ),
        ),
    )

    private class FakeFileWorkspaceClient(
        var files: List<FileNodeDto> = emptyList(),
        var content: FileContentDto = FileContentDto(type = "text", content = ""),
        var statuses: List<FileStatusDto> = emptyList(),
        var searchPaths: List<String> = emptyList(),
        var symbols: List<SymbolDto> = emptyList(),
        var filesFailure: Throwable? = null,
    ) : FileWorkspaceClient {
        var statusFailure: Throwable? = null
        var searchFileFailure: Throwable? = null
        val listFilesPaths = mutableListOf<String>()
        val readFilePaths = mutableListOf<String>()
        val fileSearchQueries = mutableListOf<String>()
        val searchQueries = mutableListOf<String>()

        override suspend fun listFiles(path: String): List<FileNodeDto> {
            listFilesPaths += path
            filesFailure?.let { throw it }
            return files
        }

        override suspend fun readFile(path: String): FileContentDto {
            readFilePaths += path
            return content
        }

        override suspend fun getFileStatus(): List<FileStatusDto> {
            statusFailure?.let { throw it }
            return statuses
        }

        override suspend fun searchFiles(query: String): List<String> {
            fileSearchQueries += query
            searchFileFailure?.let { throw it }
            return searchPaths
        }

        override suspend fun searchSymbols(query: String): List<SymbolDto> {
            searchQueries += query
            return symbols
        }
    }
}
