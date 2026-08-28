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
import dev.blazelight.p4oc.ui.screens.files.upload.UploadCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModelEditTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadFileContent_initialisesEditBaseline() = runTest {
        val repo = FakeRepo(content = "hello\nworld", hash = "baseline")
        val vm = viewModel(repo)
        vm.loadFileContent("a.txt")
        val edit = vm.editState.value
        assertEquals("a.txt", edit.path)
        assertEquals("hello\nworld", edit.originalContent)
        assertEquals("hello\nworld", edit.currentContent)
        assertEquals("baseline", edit.baselineHash)
        assertFalse(edit.isDirty)
    }

    @Test
    fun onEditorTextChange_marksDirtyAndPreservesOriginal() = runTest {
        val repo = FakeRepo(content = "first")
        val vm = viewModel(repo)
        vm.loadFileContent("x")
        vm.onEditorTextChange("first edited")
        val edit = vm.editState.value
        assertTrue(edit.isDirty)
        assertEquals("first", edit.originalContent)
        assertEquals("first edited", edit.currentContent)
    }

    @Test
    fun confirmSave_okClearsDirtyAndUpdatesOriginalAndReadView() = runTest {
        val repo = FakeRepo(content = "v1", hash = "baseline")
        val vm = viewModel(repo)
        vm.loadFileContent("p")
        vm.onEditorTextChange("v2")
        vm.requestSave()
        assertNotNull(vm.editState.value.pendingSavePreview)
        vm.confirmSave()
        val edit = vm.editState.value
        assertFalse(edit.isDirty)
        assertEquals("v2", edit.originalContent)
        assertNull(edit.pendingSavePreview)
        assertEquals(1, repo.writes.size)
        assertEquals("v2", repo.writes.first().content)
        // Read-mode buffer must reflect the saved content too.
        assertEquals("v2", vm.uiState.value.fileContent)
        assertEquals("baseline", repo.writes.first().expectedHash)
    }

    @Test
    fun overwriteAnyway_sendsNullExpectedHashEvenIfBaselinePresent() = runTest {
        val repo = FakeRepo(content = "v1", hash = "baseline")
        val vm = viewModel(repo)
        vm.loadFileContent("p")
        vm.onEditorTextChange("v2")
        vm.overwriteAnyway()
        assertEquals(1, repo.writes.size)
        assertNull(repo.writes.first().expectedHash)
    }

    @Test
    fun confirmSave_conflictRoutesToConflictState() = runTest {
        val repo = FakeRepo(content = "v1", writeResult = FileOperationResult.Conflict("stale", currentHash = "abc"))
        val vm = viewModel(repo)
        vm.loadFileContent("p")
        vm.onEditorTextChange("v2")
        vm.requestSave()
        vm.confirmSave()
        val edit = vm.editState.value
        assertNotNull(edit.conflict)
        assertEquals("abc", edit.conflict?.currentHash)
        assertTrue("buffer must be preserved on conflict", edit.isDirty)
        assertEquals("v2", edit.currentContent)
    }

    @Test
    fun discardEdits_resetsBufferAndBumpsGeneration() = runTest {
        val repo = FakeRepo(content = "orig")
        val vm = viewModel(repo)
        vm.loadFileContent("p")
        val gen0 = vm.editState.value.contentGeneration
        vm.onEditorTextChange("dirty")
        vm.discardEdits()
        val edit = vm.editState.value
        assertFalse(edit.isDirty)
        assertEquals("orig", edit.currentContent)
        assertTrue(edit.contentGeneration > gen0)
    }

    @Test
    fun requestSaveWithoutChanges_isNoOp() = runTest {
        val repo = FakeRepo(content = "same")
        val vm = viewModel(repo)
        vm.loadFileContent("p")
        vm.requestSave()
        assertNull(vm.editState.value.pendingSavePreview)
    }

    @Test
    fun readOnlyCapability_blocksSavePreviewAndWrite() = runTest {
        val repo = FakeRepo(content = "v1", canWrite = false)
        val vm = viewModel(repo)
        vm.loadFileContent("p")
        vm.onEditorTextChange("v2")

        vm.requestSave()
        vm.confirmSave()
        vm.overwriteAnyway()

        assertTrue(vm.uiState.value.capabilitiesLoaded)
        assertFalse(vm.uiState.value.capabilities.canWrite)
        assertNull(vm.editState.value.pendingSavePreview)
        assertTrue(repo.writes.isEmpty())
    }

    @Test
    fun writableCapability_preservesSaveFlow() = runTest {
        val repo = FakeRepo(content = "v1", canWrite = true)
        val vm = viewModel(repo)
        vm.loadFileContent("p")
        vm.onEditorTextChange("v2")

        vm.requestSave()
        assertNotNull(vm.editState.value.pendingSavePreview)
        vm.confirmSave()

        assertEquals(1, repo.writes.size)
        assertEquals("v2", repo.writes.single().content)
    }

    @Test
    fun recreateWithSameSavedStateHandle_restoresDirtyEditBuffer() = runTest {
        val savedStateHandle = SavedStateHandle()
        val repo = FakeRepo(content = "original", hash = "hash-1")
        val first = viewModel(repo, savedStateHandle)
        first.loadFileContent("src/App.kt")

        first.onEditorTextChange("changed")

        val recreated = viewModel(repo, savedStateHandle)
        val edit = recreated.editState.value

        assertEquals("src/App.kt", edit.path)
        assertEquals("original", edit.originalContent)
        assertEquals("changed", edit.currentContent)
        assertEquals("hash-1", edit.baselineHash)
        assertTrue(edit.isDirty)
    }

    @Test
    fun editSnapshot_atCombinedCharacterCeilingIsPersisted() = runTest {
        val savedStateHandle = SavedStateHandle()
        val original = "o"
        val current = "c".repeat(MAX_SAVED_EDIT_CONTENT_CHARS - original.length)
        val repo = FakeRepo(content = original, hash = "hash-at-limit")
        val vm = viewModel(repo, savedStateHandle)
        vm.loadFileContent("at-limit.txt")

        vm.onEditorTextChange(current)

        val restored = viewModel(repo, savedStateHandle).editState.value
        assertEquals("at-limit.txt", restored.path)
        assertEquals(original, restored.originalContent)
        assertEquals(current, restored.currentContent)
        assertEquals("hash-at-limit", restored.baselineHash)
    }

    @Test
    fun editSnapshot_overCombinedCharacterCeilingRemovesEveryPersistedEditKey() = runTest {
        val savedStateHandle = SavedStateHandle()
        val repo = FakeRepo(content = "o", hash = "oversized-hash")
        val vm = viewModel(repo, savedStateHandle)
        vm.loadFileContent("oversized.txt")

        vm.onEditorTextChange("c".repeat(MAX_SAVED_EDIT_CONTENT_CHARS))

        assertFalse(savedStateHandle.contains("files_edit_path"))
        assertFalse(savedStateHandle.contains("files_edit_original_content"))
        assertFalse(savedStateHandle.contains("files_edit_current_content"))
        assertFalse(savedStateHandle.contains("files_edit_baseline_hash"))
        assertEquals("oversized.txt", vm.editState.value.path)
        assertEquals(MAX_SAVED_EDIT_CONTENT_CHARS, vm.editState.value.currentContent.length)
    }

    @Test
    fun editSnapshot_shrinkingAfterOversizePersistsCompleteSnapshotAgain() = runTest {
        val savedStateHandle = SavedStateHandle()
        val repo = FakeRepo(content = "original", hash = "hash-after-shrink")
        val vm = viewModel(repo, savedStateHandle)
        vm.loadFileContent("shrunk.txt")
        vm.onEditorTextChange("x".repeat(MAX_SAVED_EDIT_CONTENT_CHARS))

        vm.onEditorTextChange("small again")

        val restored = viewModel(repo, savedStateHandle).editState.value
        assertEquals("shrunk.txt", restored.path)
        assertEquals("original", restored.originalContent)
        assertEquals("small again", restored.currentContent)
        assertEquals("hash-after-shrink", restored.baselineHash)
        assertTrue(restored.isDirty)
    }

    @Test
    fun editSnapshot_smallContentRestoresExactly() = runTest {
        val savedStateHandle = SavedStateHandle()
        val original = "alpha\u0000\uD83D\uDE80\nline two"
        val current = "alpha\u0000\uD83D\uDE80\nline two edited"
        val repo = FakeRepo(content = original, hash = "exact-hash")
        val vm = viewModel(repo, savedStateHandle)
        vm.loadFileContent("exact.txt")
        vm.onEditorTextChange(current)

        val restored = viewModel(repo, savedStateHandle).editState.value

        assertEquals("exact.txt", restored.path)
        assertEquals(original, restored.originalContent)
        assertEquals(current, restored.currentContent)
        assertEquals("exact-hash", restored.baselineHash)
        assertTrue(restored.isDirty)
    }

    @Test
    fun loadFileContent_preservesRestoredDirtyBufferForSamePath() = runTest {
        val savedStateHandle = SavedStateHandle()
        val repo = FakeRepo(content = "original", hash = "hash-1")
        val first = viewModel(repo, savedStateHandle)
        first.loadFileContent("src/App.kt")
        first.onEditorTextChange("changed")

        val recreated = viewModel(repo, savedStateHandle)
        recreated.loadFileContent("src/App.kt")

        assertEquals("changed", recreated.editState.value.currentContent)
        assertTrue(recreated.editState.value.isDirty)
    }

    @Test
    fun recreateWithSameSavedStateHandle_restoresPathStackAndFilters() = runTest {
        val savedStateHandle = SavedStateHandle()
        val repo = FakeRepo(content = "")
        val first = viewModel(repo, savedStateHandle)

        first.navigateTo("src")
        first.navigateTo("src/main")
        first.setSearchActive(true)
        first.updateSearchQuery("view")
        first.setSymbolMode(true)
        first.updateSymbolQuery("Main")

        val recreated = viewModel(repo, savedStateHandle)

        assertEquals("src/main", recreated.uiState.value.currentPath)
        assertFalse(recreated.uiState.value.isSearchActive)
        assertEquals("view", recreated.uiState.value.searchQuery)
        assertTrue(recreated.uiState.value.isSymbolMode)
        assertEquals("Main", recreated.uiState.value.symbolQuery)
        assertEquals(listOf("Main", "Main"), repo.symbolQueries)

        recreated.navigateUp()

        assertEquals("src", recreated.uiState.value.currentPath)
    }

    @Test
    fun explorerQueriesAreBoundedBeforeStateAndPersistence() = runTest {
        val savedStateHandle = SavedStateHandle()
        val repo = FakeRepo(content = "")
        val viewModel = viewModel(repo, savedStateHandle)
        val oversized = "x".repeat(2_000)

        viewModel.updateSearchQuery(oversized)
        viewModel.updateSymbolQuery(oversized)

        assertEquals(1_024, viewModel.uiState.value.searchQuery.length)
        assertEquals(1_024, viewModel.uiState.value.symbolQuery.length)
        assertEquals(1_024, savedStateHandle.get<String>(FilesViewModel.KEY_SEARCH_QUERY)?.length)
        assertEquals(1_024, savedStateHandle.get<String>(FilesViewModel.KEY_SYMBOL_QUERY)?.length)
    }

    @Test
    fun persistedNavigationHistoryIsBounded() = runTest {
        val savedStateHandle = SavedStateHandle()
        val repo = FakeRepo(content = "")
        val viewModel = viewModel(repo, savedStateHandle)

        repeat(200) { index -> viewModel.navigateTo("path-$index") }

        assertEquals(128, savedStateHandle.get<ArrayList<String>>(FilesViewModel.KEY_PATH_STACK)?.size)
    }

    @Test
    fun missingRestoredPathFallsBackToRootWithRestoreError() = runTest {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "files_current_path" to "missing",
                "files_path_stack" to arrayListOf(""),
            )
        )
        val repo = FakeRepo(content = "", failedPaths = setOf("missing"))

        val vm = viewModel(repo, savedStateHandle)

        assertEquals("", vm.uiState.value.currentPath)
        assertEquals("missing path", vm.uiState.value.pathRestoreError)
    }

    @Test
    fun initialRootLoadFailureIsExposedInsteadOfLookingEmpty() = runTest {
        val repo = FakeRepo(content = "", failedPaths = setOf(""))

        val vm = viewModel(repo)

        assertFalse(vm.uiState.value.isLoading)
        assertEquals("missing path", vm.uiState.value.error)
        assertTrue(vm.uiState.value.files.isEmpty())
    }

    private class FakeRepo(
        val content: String,
        val hash: String? = null,
        val failedPaths: Set<String> = emptySet(),
        val canWrite: Boolean = true,
        val writeResult: FileOperationResult<FileWriteResult> =
            FileOperationResult.Ok(FileWriteResult("p", hash = null)),
    ) : FileRepository {
        val writes = mutableListOf<FileWriteRequest>()
        val symbolQueries = mutableListOf<String>()

        override suspend fun listFiles(path: String): FileOperationResult<FileList> =
            if (path in failedPaths) {
                FileOperationResult.Failed("missing path")
            } else {
                FileOperationResult.Ok(FileList(path, emptyList()))
            }

        override suspend fun readFile(path: String): FileOperationResult<FileContent> =
            FileOperationResult.Ok(FileContent(content = content, hash = hash))

        override suspend fun searchFiles(query: String): FileOperationResult<List<FileNode>> =
            FileOperationResult.Ok(emptyList())

        override suspend fun searchSymbols(query: String): FileOperationResult<List<Symbol>> {
            symbolQueries += query
            return FileOperationResult.Ok(emptyList())
        }

        override suspend fun writeFile(request: FileWriteRequest): FileOperationResult<FileWriteResult> {
            writes += request
            return writeResult
        }

        override suspend fun createDirectory(path: String): FileOperationResult<Unit> =
            FileOperationResult.Ok(Unit)

        override suspend fun renameFile(fromPath: String, toPath: String): FileOperationResult<Unit> =
            FileOperationResult.Ok(Unit)

        override suspend fun deleteFile(path: String): FileOperationResult<Unit> =
            FileOperationResult.Ok(Unit)

        override suspend fun uploadFile(request: FileUploadRequest): FileOperationResult<FileUploadResult> =
            FileOperationResult.Ok(FileUploadResult(request.path))

        override suspend fun capabilities(): FileCapabilities = FileCapabilities(canWrite = canWrite)
    }

    private fun viewModel(
        repository: FileRepository,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = FilesViewModel(
        fileRepository = repository,
        workspaceChangesRepository = NoOpWorkspaceChangesRepository,
        uploadCoordinator = testUploadCoordinator(repository),
        savedStateHandle = savedStateHandle,
    )

    private fun testUploadCoordinator(repo: FileRepository) = UploadCoordinator(
        scope = CoroutineScope(Dispatchers.Main),
        repositoryFactory = { repo },
    )
}
