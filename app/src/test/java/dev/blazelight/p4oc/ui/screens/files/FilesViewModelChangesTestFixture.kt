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
import dev.blazelight.p4oc.data.vcs.WorkspaceChange
import dev.blazelight.p4oc.data.vcs.WorkspaceChangeStatus
import dev.blazelight.p4oc.data.vcs.WorkspaceChangesRepository
import dev.blazelight.p4oc.data.vcs.WorkspaceChangesResult
import dev.blazelight.p4oc.data.vcs.WorkspaceChangesSnapshot
import dev.blazelight.p4oc.data.vcs.WorkspacePatch
import dev.blazelight.p4oc.domain.model.FileContent
import dev.blazelight.p4oc.domain.model.FileNode
import dev.blazelight.p4oc.domain.model.Symbol
import dev.blazelight.p4oc.ui.screens.files.upload.UploadCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun TestScope.loadedChangesViewModel(
    repository: FakeChangesRepository,
): FilesViewModel {
    val viewModel = changesViewModel(repository)
    runCurrent()
    viewModel.enterChanges()
    runCurrent()
    assertFalse(viewModel.changesState.value.isLoading)
    return viewModel
}

internal fun changesViewModel(repository: WorkspaceChangesRepository): FilesViewModel {
    val fileRepository = ChangesFakeFileRepository()
    return FilesViewModel(
        fileRepository = fileRepository,
        workspaceChangesRepository = repository,
        uploadCoordinator = UploadCoordinator(
            scope = CoroutineScope(Dispatchers.Main),
            repositoryFactory = { fileRepository },
        ),
        savedStateHandle = SavedStateHandle(),
    )
}

internal class FakeChangesRepository : WorkspaceChangesRepository {
    var snapshotCalls = 0
    var diffCalls = 0
    var snapshotHandler: suspend () -> WorkspaceChangesResult<WorkspaceChangesSnapshot> = {
        error("Unexpected snapshot request")
    }
    var diffHandler: suspend () -> WorkspaceChangesResult<Map<String, WorkspacePatch>> = {
        error("Unexpected diff request")
    }

    override suspend fun loadSnapshot(): WorkspaceChangesResult<WorkspaceChangesSnapshot> {
        snapshotCalls++
        return snapshotHandler()
    }

    override suspend fun loadDiff(): WorkspaceChangesResult<Map<String, WorkspacePatch>> {
        diffCalls++
        return diffHandler()
    }
}

private class ChangesFakeFileRepository : FileRepository {
    override suspend fun listFiles(path: String): FileOperationResult<FileList> =
        FileOperationResult.Ok(FileList(path, emptyList()))

    override suspend fun readFile(path: String): FileOperationResult<FileContent> =
        FileOperationResult.Ok(FileContent(content = ""))

    override suspend fun searchFiles(query: String): FileOperationResult<List<FileNode>> =
        FileOperationResult.Ok(emptyList())

    override suspend fun searchSymbols(query: String): FileOperationResult<List<Symbol>> =
        FileOperationResult.Ok(emptyList())

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

internal fun changesSnapshot(
    paths: List<String>,
    branch: String? = "feature/changes",
): WorkspaceChangesSnapshot {
    val changes = paths.mapIndexed { index, path ->
        WorkspaceChange(
            file = path,
            status = when (index % 3) {
                0 -> WorkspaceChangeStatus.Added
                1 -> WorkspaceChangeStatus.Modified
                else -> WorkspaceChangeStatus.Deleted
            },
            additions = index.toLong() + 1L,
            deletions = index.toLong(),
        )
    }
    return WorkspaceChangesSnapshot(
        serverLabel = "server-a",
        workspaceDirectory = "/work/alpha",
        branch = branch,
        defaultBranch = "main",
        changes = changes,
        additions = changes.sumOf { it.additions },
        deletions = changes.sumOf { it.deletions },
    )
}
