package dev.blazelight.p4oc.ui.screens.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.data.files.FileOperationResult
import dev.blazelight.p4oc.data.files.FileRepository
import dev.blazelight.p4oc.data.files.FileWriteRequest
import dev.blazelight.p4oc.domain.model.FileNode
import dev.blazelight.p4oc.domain.model.Symbol
import dev.blazelight.p4oc.ui.screens.files.upload.UploadCoordinator
import dev.blazelight.p4oc.ui.screens.files.upload.UploadQueueState
import dev.blazelight.p4oc.ui.screens.files.upload.UploadSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FilesViewModel constructor(
    private val fileRepository: FileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    private val _symbolResults = MutableStateFlow<List<Symbol>>(emptyList())
    val symbolResults: StateFlow<List<Symbol>> = _symbolResults.asStateFlow()

    private val pathStack = mutableListOf<String>()
    private var loadFilesJob: Job? = null
    private var loadContentJob: Job? = null
    private var saveJob: Job? = null

    private val _editState = MutableStateFlow(FileEditState())
    val editState: StateFlow<FileEditState> = _editState.asStateFlow()

    private val uploadCoordinator = UploadCoordinator(
        scope = viewModelScope,
        repositoryFactory = { fileRepository },
        destinationPath = { _uiState.value.currentPath.ifBlank { null } },
        onComplete = { items -> if (items.isNotEmpty()) refresh() },
    )
    val uploadState: StateFlow<UploadQueueState> = uploadCoordinator.state

    init {
        loadFiles(ROOT_PATH)
    }

    fun refresh() {
        loadFiles(_uiState.value.currentPath)
    }

    fun navigateTo(path: String) {
        pathStack.add(_uiState.value.currentPath)
        loadFiles(path)
    }

    fun navigateUp() {
        val previousPath = pathStack.removeLastOrNull() ?: ROOT_PATH
        loadFiles(previousPath)
    }

    private fun loadFiles(path: String) {
        loadFilesJob?.cancel()
        loadFilesJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = fileRepository.listFiles(path)) {
                is FileOperationResult.Ok -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            files = result.data.files,
                            currentPath = result.data.path,
                        )
                    }
                }
                is FileOperationResult.Conflict -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
                is FileOperationResult.Failed -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    fun searchSymbols(query: String) {
        viewModelScope.launch {
            _symbolResults.value = emptyList()
            _uiState.update { it.copy(symbolError = null) }
            if (query.isBlank()) return@launch

            when (val result = fileRepository.searchSymbols(query)) {
                is FileOperationResult.Ok -> {
                    _symbolResults.value = result.data
                }
                is FileOperationResult.Conflict -> _uiState.update { it.copy(symbolError = result.message) }
                is FileOperationResult.Failed -> _uiState.update { it.copy(symbolError = result.message) }
            }
        }
    }

    fun loadFileContent(path: String) {
        loadContentJob?.cancel()
        loadContentJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, fileContent = null, error = null) }

            when (val result = fileRepository.readFile(path)) {
                is FileOperationResult.Ok -> {
                    _uiState.update {
                        it.copy(isLoading = false, fileContent = result.data.content)
                    }
                    // Reset edit baseline whenever we (re)load. The viewer screen owns
                    // the decision of whether to enter edit mode; the baseline is only
                    // consumed when it does.
                    _editState.update {
                        FileEditState(
                            path = path,
                            originalContent = result.data.content,
                            currentContent = result.data.content,
                            isDirty = false,
                            contentGeneration = it.contentGeneration + 1,
                            baselineHash = result.data.hash,
                        )
                    }
                }
                is FileOperationResult.Conflict -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
                is FileOperationResult.Failed -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    /** Push the latest text snapshot from the editor into edit state. */
    fun onEditorTextChange(newText: String) {
        _editState.update { current ->
            if (current.path == null) return@update current
            current.copy(
                currentContent = newText,
                isDirty = newText != current.originalContent,
            )
        }
    }

    fun requestSave() {
        val state = _editState.value
        if (state.path == null) return
        if (!state.isDirty) {
            _editState.update { it.copy(saveError = null, pendingSavePreview = null) }
            return
        }
        _editState.update {
            it.copy(
                pendingSavePreview = SavePreview(
                    path = state.path,
                    before = state.originalContent,
                    after = state.currentContent,
                ),
                saveError = null,
            )
        }
    }

    fun dismissSavePreview() {
        _editState.update { it.copy(pendingSavePreview = null) }
    }

    fun confirmSave() {
        performSave(useBaselineHash = true)
    }

    fun reloadFromServer() {
        val path = _editState.value.path ?: return
        _editState.update { it.copy(conflict = null) }
        loadFileContent(path)
    }

    /** Re-issues the write with no baseline hash, suppressing stale-write detection. */
    fun overwriteAnyway() {
        _editState.update { it.copy(conflict = null) }
        performSave(useBaselineHash = false)
    }

    private fun performSave(useBaselineHash: Boolean) {
        val state = _editState.value
        val path = state.path ?: return
        if (state.isSaving) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            _editState.update { it.copy(isSaving = true, saveError = null) }
            val expected = if (useBaselineHash) state.baselineHash else null
            val request = FileWriteRequest(path = path, content = state.currentContent, expectedHash = expected)
            when (val result = fileRepository.writeFile(request)) {
                is FileOperationResult.Ok -> {
                    _editState.update {
                        it.copy(
                            originalContent = state.currentContent,
                            isDirty = false,
                            isSaving = false,
                            pendingSavePreview = null,
                            conflict = null,
                            saveError = null,
                            baselineHash = result.data.hash ?: it.baselineHash,
                            // Do not bump contentGeneration on a successful
                            // save: the buffer did not change externally, so
                            // bumping would needlessly clobber Sora's cursor
                            // and undo history. Generation bumps are reserved
                            // for loadFileContent / discardEdits / conflict
                            // reload.
                        )
                    }
                    // Keep read-mode view in sync with what we just wrote.
                    _uiState.update { it.copy(fileContent = state.currentContent) }
                }
                is FileOperationResult.Conflict -> {
                    _editState.update {
                        it.copy(
                            isSaving = false,
                            pendingSavePreview = null,
                            conflict = ConflictInfo(message = result.message, currentHash = result.currentHash),
                        )
                    }
                }
                is FileOperationResult.Failed -> {
                    _editState.update {
                        it.copy(
                            isSaving = false,
                            pendingSavePreview = null,
                            saveError = result.message,
                        )
                    }
                }
            }
        }
    }

    fun dismissConflict() {
        _editState.update { it.copy(conflict = null) }
    }

    fun discardEdits() {
        _editState.update { state ->
            state.copy(
                currentContent = state.originalContent,
                isDirty = false,
                pendingSavePreview = null,
                saveError = null,
                contentGeneration = state.contentGeneration + 1,
            )
        }
    }

    fun clearSaveError() {
        _editState.update { it.copy(saveError = null) }
    }

    fun uploadFromSources(source: UploadSource, sourceIds: List<String>) {
        uploadCoordinator.upload(source, sourceIds)
    }

    fun retryFailedUploads() {
        uploadCoordinator.retryFailed()
    }

    fun cancelUploads() {
        uploadCoordinator.cancel()
    }

    fun dismissUploadResult() {
        uploadCoordinator.dismiss()
    }

    private companion object {
        const val ROOT_PATH = ""
    }
}

data class FilesUiState(
    val isLoading: Boolean = false,
    val files: List<FileNode> = emptyList(),
    val currentPath: String = "",
    val fileContent: String? = null,
    val error: String? = null,
    val symbolError: String? = null,
)

/**
 * State for the in-place file editor. [contentGeneration] is bumped whenever
 * the buffer must be force-pushed into the editor (initial load, reload after
 * conflict, discard) so the AndroidView wrapper can avoid clobbering Sora's
 * undo history during normal recomposition.
 */
data class FileEditState(
    val path: String? = null,
    val originalContent: String = "",
    val currentContent: String = "",
    val isDirty: Boolean = false,
    val contentGeneration: Int = 0,
    val isSaving: Boolean = false,
    val pendingSavePreview: SavePreview? = null,
    val conflict: ConflictInfo? = null,
    val saveError: String? = null,
    /**
     * Optional baseline hash captured at read time. Populated by the
     * repository (server-supplied for the standard read API, or via OFISH
     * shell `hash_file` for OFISH-backed workspaces). When non-null it
     * enables stale-write detection on save; when null, conflict detection
     * relies solely on the server returning Conflict by some other path.
     */
    val baselineHash: String? = null,
)

data class SavePreview(
    val path: String,
    val before: String,
    val after: String,
)

data class ConflictInfo(
    val message: String,
    val currentHash: String?,
)
