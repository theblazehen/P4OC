package dev.blazelight.p4oc.ui.screens.files

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
        val vm = FilesViewModel(repo)
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
        val vm = FilesViewModel(repo)
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
        val vm = FilesViewModel(repo)
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
        val vm = FilesViewModel(repo)
        vm.loadFileContent("p")
        vm.onEditorTextChange("v2")
        vm.overwriteAnyway()
        assertEquals(1, repo.writes.size)
        assertNull(repo.writes.first().expectedHash)
    }

    @Test
    fun confirmSave_conflictRoutesToConflictState() = runTest {
        val repo = FakeRepo(content = "v1", writeResult = FileOperationResult.Conflict("stale", currentHash = "abc"))
        val vm = FilesViewModel(repo)
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
        val vm = FilesViewModel(repo)
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
        val vm = FilesViewModel(repo)
        vm.loadFileContent("p")
        vm.requestSave()
        assertNull(vm.editState.value.pendingSavePreview)
    }

    private class FakeRepo(
        val content: String,
        val hash: String? = null,
        val writeResult: FileOperationResult<FileWriteResult> =
            FileOperationResult.Ok(FileWriteResult("p", hash = null)),
    ) : FileRepository {
        val writes = mutableListOf<FileWriteRequest>()

        override suspend fun listFiles(path: String): FileOperationResult<FileList> =
            FileOperationResult.Ok(FileList(path, emptyList()))

        override suspend fun readFile(path: String): FileOperationResult<FileContent> =
            FileOperationResult.Ok(FileContent(content = content, hash = hash))

        override suspend fun searchSymbols(query: String): FileOperationResult<List<Symbol>> =
            FileOperationResult.Ok(emptyList())

        override suspend fun writeFile(request: FileWriteRequest): FileOperationResult<FileWriteResult> {
            writes += request
            return writeResult
        }

        override suspend fun deleteFile(path: String): FileOperationResult<Unit> =
            FileOperationResult.Ok(Unit)

        override suspend fun uploadFile(request: FileUploadRequest): FileOperationResult<FileUploadResult> =
            FileOperationResult.Ok(FileUploadResult(request.path))

        override suspend fun capabilities(): FileCapabilities = FileCapabilities(canWrite = true)
    }
}
