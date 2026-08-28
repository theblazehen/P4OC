package dev.blazelight.p4oc.ui.screens.files

import androidx.lifecycle.SavedStateHandle
import dev.blazelight.p4oc.data.files.FileCapabilities
import dev.blazelight.p4oc.data.files.FileList
import dev.blazelight.p4oc.data.files.FileOperationResult
import dev.blazelight.p4oc.data.files.FileRepository
import dev.blazelight.p4oc.data.files.FileUploadRequest
import dev.blazelight.p4oc.data.files.FileUploadResult
import dev.blazelight.p4oc.data.files.FileWriteRequest
import dev.blazelight.p4oc.data.files.FileWriteResult
import dev.blazelight.p4oc.domain.model.FileContent
import dev.blazelight.p4oc.domain.model.FileNode
import dev.blazelight.p4oc.domain.model.Symbol
import dev.blazelight.p4oc.domain.model.SymbolRange
import dev.blazelight.p4oc.ui.screens.files.upload.UploadCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModelSearchTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun rapidTypingDebouncesToOnlyTheLatestRepositoryCall() = runTest(dispatcher) {
        val repository = FakeSearchRepository().apply {
            fileSearchHandler = { query ->
                FileOperationResult.Ok(listOf(fileNode("src/$query.kt")))
            }
        }
        val viewModel = viewModel(repository)
        runCurrent()

        viewModel.setSearchActive(true)
        viewModel.updateSearchQuery("old")
        advanceTimeBy(200)
        viewModel.updateSearchQuery("latest")

        assertTrue(viewModel.uiState.value.isFileSearchLoading)
        advanceTimeBy(249)
        assertTrue(repository.fileSearchCalls.isEmpty())

        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf("latest"), repository.fileSearchCalls)
        assertEquals(listOf("src/latest.kt"), viewModel.fileSearchResults.value.map { it.path })
        assertFalse(viewModel.uiState.value.isFileSearchLoading)
        assertNull(viewModel.uiState.value.fileSearchError)
    }

    @Test
    fun nonCooperativeFileSearchCannotOverwriteTheLatestResult() = runTest(dispatcher) {
        var completeFirst: ((FileOperationResult<List<FileNode>>) -> Unit)? = null
        val repository = FakeSearchRepository().apply {
            fileSearchHandler = { query ->
                if (query == "first") {
                    suspendCoroutine { continuation ->
                        completeFirst = { result -> continuation.resume(result) }
                    }
                } else {
                    FileOperationResult.Ok(listOf(fileNode("second.kt")))
                }
            }
        }
        val viewModel = viewModel(repository)
        runCurrent()
        viewModel.setSearchActive(true)

        viewModel.updateSearchQuery("first")
        advanceTimeBy(250)
        runCurrent()
        assertEquals(listOf("first"), repository.fileSearchCalls)

        viewModel.updateSearchQuery("second")
        advanceTimeBy(250)
        runCurrent()
        assertEquals(listOf("first", "second"), repository.fileSearchCalls)
        assertEquals(listOf("second.kt"), viewModel.fileSearchResults.value.map { it.path })

        checkNotNull(completeFirst).invoke(FileOperationResult.Ok(listOf(fileNode("stale.kt"))))
        runCurrent()

        assertEquals(listOf("second.kt"), viewModel.fileSearchResults.value.map { it.path })
        assertFalse(viewModel.uiState.value.isFileSearchLoading)
    }

    @Test
    fun nonCooperativeSymbolSearchCannotOverwriteTheLatestResultOrError() = runTest(dispatcher) {
        var completeFirst: ((FileOperationResult<List<Symbol>>) -> Unit)? = null
        val repository = FakeSearchRepository().apply {
            symbolSearchHandler = { query ->
                if (query == "First") {
                    suspendCoroutine { continuation ->
                        completeFirst = { result -> continuation.resume(result) }
                    }
                } else {
                    FileOperationResult.Ok(listOf(symbol("Second")))
                }
            }
        }
        val viewModel = viewModel(repository)
        runCurrent()
        viewModel.setSymbolMode(true)

        viewModel.updateSymbolQuery("First")
        runCurrent()
        assertEquals(listOf("First"), repository.symbolSearchCalls)

        viewModel.updateSymbolQuery("Second")
        runCurrent()
        assertEquals(listOf("First", "Second"), repository.symbolSearchCalls)
        assertEquals(listOf("Second"), viewModel.symbolResults.value.map { it.name })

        checkNotNull(completeFirst).invoke(FileOperationResult.Failed("stale failure"))
        runCurrent()

        assertEquals(listOf("Second"), viewModel.symbolResults.value.map { it.name })
        assertNull(viewModel.uiState.value.symbolError)
    }

    @Test
    fun blankQueryClearsResultsAndErrorWithoutCallingRepository() = runTest(dispatcher) {
        val repository = FakeSearchRepository().apply {
            fileSearchHandler = { FileOperationResult.Ok(listOf(fileNode("match.kt"))) }
        }
        val viewModel = viewModel(repository)
        runCurrent()
        viewModel.setSearchActive(true)

        viewModel.updateSearchQuery("match")
        advanceTimeBy(250)
        runCurrent()
        assertEquals(listOf("match.kt"), viewModel.fileSearchResults.value.map { it.path })

        viewModel.updateSearchQuery("   ")

        assertTrue(viewModel.fileSearchResults.value.isEmpty())
        assertFalse(viewModel.uiState.value.isFileSearchLoading)
        assertNull(viewModel.uiState.value.fileSearchError)
        assertEquals(listOf("match"), repository.fileSearchCalls)

        repository.fileSearchHandler = { FileOperationResult.Failed("offline") }
        viewModel.updateSearchQuery("failure")
        advanceTimeBy(250)
        runCurrent()
        assertEquals("offline", viewModel.uiState.value.fileSearchError)

        viewModel.updateSearchQuery("")

        assertNull(viewModel.uiState.value.fileSearchError)
        assertFalse(viewModel.uiState.value.isFileSearchLoading)
        assertEquals(listOf("match", "failure"), repository.fileSearchCalls)
    }

    @Test
    fun failedSearchEndsLoadingAndExposesTheFailure() = runTest(dispatcher) {
        val repository = FakeSearchRepository().apply {
            fileSearchHandler = { FileOperationResult.Failed("workspace unavailable") }
        }
        val viewModel = viewModel(repository)
        runCurrent()
        viewModel.setSearchActive(true)

        viewModel.updateSearchQuery("needle")

        assertTrue(viewModel.uiState.value.isFileSearchLoading)
        assertNull(viewModel.uiState.value.fileSearchError)
        assertTrue(viewModel.fileSearchResults.value.isEmpty())

        advanceTimeBy(250)
        runCurrent()

        assertFalse(viewModel.uiState.value.isFileSearchLoading)
        assertEquals("workspace unavailable", viewModel.uiState.value.fileSearchError)
        assertTrue(viewModel.fileSearchResults.value.isEmpty())
    }

    @Test
    fun refreshRerunsActiveFileSearchWithoutReloadingDirectory() = runTest(dispatcher) {
        val repository = FakeSearchRepository().apply {
            fileSearchHandler = { query ->
                FileOperationResult.Ok(listOf(fileNode("$query-${fileSearchCalls.size}.kt")))
            }
        }
        val viewModel = viewModel(repository)
        runCurrent()
        val initialDirectoryLoads = repository.listFilesCalls.size
        viewModel.setSearchActive(true)

        viewModel.updateSearchQuery("needle")
        advanceTimeBy(250)
        runCurrent()
        assertEquals(listOf("needle-1.kt"), viewModel.fileSearchResults.value.map { it.path })

        viewModel.refresh()
        assertTrue(viewModel.uiState.value.isFileSearchLoading)
        runCurrent()

        assertEquals(listOf("needle", "needle"), repository.fileSearchCalls)
        assertEquals(listOf("needle-2.kt"), viewModel.fileSearchResults.value.map { it.path })
        assertEquals(initialDirectoryLoads, repository.listFilesCalls.size)
    }

    @Test
    fun symbolModeCancelsFileSearchAndRefreshesOnlySymbols() = runTest(dispatcher) {
        val repository = FakeSearchRepository().apply {
            symbolSearchHandler = { query ->
                FileOperationResult.Ok(listOf(symbol("$query-${symbolSearchCalls.size}")))
            }
        }
        val viewModel = viewModel(repository)
        runCurrent()
        val initialDirectoryLoads = repository.listFilesCalls.size

        viewModel.setSearchActive(true)
        viewModel.updateSearchQuery("pending")
        viewModel.setSymbolMode(true)
        viewModel.updateSymbolQuery("Widget")
        runCurrent()
        advanceTimeBy(250)
        runCurrent()

        assertFalse(viewModel.uiState.value.isSearchActive)
        assertTrue(viewModel.uiState.value.isSymbolMode)
        assertTrue(repository.fileSearchCalls.isEmpty())
        assertEquals(listOf("Widget"), repository.symbolSearchCalls)

        viewModel.refresh()
        runCurrent()

        assertEquals(listOf("Widget", "Widget"), repository.symbolSearchCalls)
        assertEquals(listOf("Widget-2"), viewModel.symbolResults.value.map { it.name })
        assertEquals(initialDirectoryLoads, repository.listFilesCalls.size)

        viewModel.setSearchActive(true)
        assertTrue(viewModel.uiState.value.isSearchActive)
        assertFalse(viewModel.uiState.value.isSymbolMode)
        assertTrue(viewModel.symbolResults.value.isEmpty())
        viewModel.clearFilters()
    }

    @Test
    fun clearFiltersCancelsPendingWorkAndResetsBothSearchModes() = runTest(dispatcher) {
        val repository = FakeSearchRepository().apply {
            fileSearchHandler = { FileOperationResult.Ok(listOf(fileNode("ready.kt"))) }
            symbolSearchHandler = { FileOperationResult.Ok(listOf(symbol("ReadySymbol"))) }
        }
        val viewModel = viewModel(repository)
        runCurrent()

        viewModel.setSearchActive(true)
        viewModel.updateSearchQuery("ready")
        advanceTimeBy(250)
        runCurrent()
        assertTrue(viewModel.fileSearchResults.value.isNotEmpty())

        viewModel.clearFilters()
        assertSearchStateCleared(viewModel)

        viewModel.setSymbolMode(true)
        viewModel.updateSymbolQuery("ReadySymbol")
        runCurrent()
        assertTrue(viewModel.symbolResults.value.isNotEmpty())

        viewModel.clearFilters()
        assertSearchStateCleared(viewModel)

        viewModel.setSearchActive(true)
        viewModel.updateSearchQuery("cancelled-before-debounce")
        viewModel.clearFilters()
        advanceTimeBy(250)
        runCurrent()

        assertEquals(listOf("ready"), repository.fileSearchCalls)
        assertSearchStateCleared(viewModel)
    }

    @Test
    fun restoredActiveFileQueryIsSearchedAfterDebounce() = runTest(dispatcher) {
        val repository = FakeSearchRepository().apply {
            fileSearchHandler = { query ->
                FileOperationResult.Ok(listOf(fileNode("restored/$query.kt")))
            }
        }
        val savedStateHandle = SavedStateHandle(
            mapOf(
                FilesViewModel.KEY_SEARCH_ACTIVE to true,
                FilesViewModel.KEY_SEARCH_QUERY to "needle",
                FilesViewModel.KEY_SYMBOL_MODE to false,
            )
        )

        val viewModel = viewModel(repository, savedStateHandle)
        runCurrent()

        assertTrue(viewModel.uiState.value.isSearchActive)
        assertEquals("needle", viewModel.uiState.value.searchQuery)
        assertTrue(viewModel.uiState.value.isFileSearchLoading)
        advanceTimeBy(249)
        assertTrue(repository.fileSearchCalls.isEmpty())

        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf("needle"), repository.fileSearchCalls)
        assertEquals(listOf("restored/needle.kt"), viewModel.fileSearchResults.value.map { it.path })
        assertFalse(viewModel.uiState.value.isFileSearchLoading)
    }

    private fun viewModel(
        repository: FileRepository,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = FilesViewModel(
        fileRepository = repository,
        uploadCoordinator = UploadCoordinator(
            scope = CoroutineScope(Dispatchers.Main),
            repositoryFactory = { repository },
        ),
        savedStateHandle = savedStateHandle,
    )

    private fun assertSearchStateCleared(viewModel: FilesViewModel) {
        val state = viewModel.uiState.value
        assertFalse(state.isSearchActive)
        assertFalse(state.isSymbolMode)
        assertEquals("", state.searchQuery)
        assertEquals("", state.symbolQuery)
        assertFalse(state.isFileSearchLoading)
        assertNull(state.fileSearchError)
        assertNull(state.symbolError)
        assertTrue(viewModel.fileSearchResults.value.isEmpty())
        assertTrue(viewModel.symbolResults.value.isEmpty())
    }

    private class FakeSearchRepository : FileRepository {
        val listFilesCalls = mutableListOf<String>()
        val fileSearchCalls = mutableListOf<String>()
        val symbolSearchCalls = mutableListOf<String>()
        var fileSearchHandler: suspend (String) -> FileOperationResult<List<FileNode>> = {
            FileOperationResult.Ok(emptyList())
        }
        var symbolSearchHandler: suspend (String) -> FileOperationResult<List<Symbol>> = {
            FileOperationResult.Ok(emptyList())
        }

        override suspend fun listFiles(path: String): FileOperationResult<FileList> {
            listFilesCalls += path
            return FileOperationResult.Ok(FileList(path, emptyList()))
        }

        override suspend fun readFile(path: String): FileOperationResult<FileContent> =
            FileOperationResult.Ok(FileContent(content = ""))

        override suspend fun searchFiles(query: String): FileOperationResult<List<FileNode>> {
            fileSearchCalls += query
            return fileSearchHandler(query)
        }

        override suspend fun searchSymbols(query: String): FileOperationResult<List<Symbol>> {
            symbolSearchCalls += query
            return symbolSearchHandler(query)
        }

        override suspend fun writeFile(request: FileWriteRequest): FileOperationResult<FileWriteResult> =
            FileOperationResult.Ok(FileWriteResult(request.path))

        override suspend fun createDirectory(path: String): FileOperationResult<Unit> =
            FileOperationResult.Ok(Unit)

        override suspend fun renameFile(fromPath: String, toPath: String): FileOperationResult<Unit> =
            FileOperationResult.Ok(Unit)

        override suspend fun deleteFile(path: String): FileOperationResult<Unit> =
            FileOperationResult.Ok(Unit)

        override suspend fun uploadFile(request: FileUploadRequest): FileOperationResult<FileUploadResult> =
            FileOperationResult.Ok(FileUploadResult(request.path))

        override suspend fun capabilities(): FileCapabilities = FileCapabilities()
    }

    private companion object {
        fun fileNode(path: String) = FileNode(
            name = path.substringAfterLast('/'),
            path = path,
            type = "file",
        )

        fun symbol(name: String) = Symbol(
            name = name,
            kind = 12,
            uri = "file:///src/$name.kt",
            range = SymbolRange(0, 0, 0, 1),
        )
    }
}
