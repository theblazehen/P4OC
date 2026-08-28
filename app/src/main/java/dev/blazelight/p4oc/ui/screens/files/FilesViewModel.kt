package dev.blazelight.p4oc.ui.screens.files

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.data.files.FileCapabilities
import dev.blazelight.p4oc.data.files.FileOperationResult
import dev.blazelight.p4oc.data.files.FileRepository
import dev.blazelight.p4oc.data.files.FileWriteRequest
import dev.blazelight.p4oc.domain.model.FileNode
import dev.blazelight.p4oc.domain.model.Symbol
import dev.blazelight.p4oc.ui.screens.files.upload.UploadCoordinator
import dev.blazelight.p4oc.ui.screens.files.upload.UploadQueueState
import dev.blazelight.p4oc.ui.screens.files.upload.UploadSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Conservative share of the Bundle budget for both UTF-16 edit buffers. */
internal const val MAX_SAVED_EDIT_CONTENT_CHARS = 64 * 1024

@Suppress("TooManyFunctions")
class FilesViewModel constructor(
    private val fileRepository: FileRepository,
    private val uploadCoordinator: UploadCoordinator,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FilesUiState(
            currentPath = savedStateHandle[KEY_CURRENT_PATH] ?: ROOT_PATH,
            searchQuery = savedStateHandle[KEY_SEARCH_QUERY] ?: "",
            isSearchActive = (savedStateHandle[KEY_SEARCH_ACTIVE] ?: false) &&
                !(savedStateHandle[KEY_SYMBOL_MODE] ?: false),
            isSymbolMode = savedStateHandle[KEY_SYMBOL_MODE] ?: false,
            symbolQuery = savedStateHandle[KEY_SYMBOL_QUERY] ?: "",
        )
    )
    private var restoringInitialPath = savedStateHandle.get<String>(KEY_CURRENT_PATH).orEmpty().isNotBlank()
    private var pendingPathRestoreError: String? = null
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    private val _symbolResults = MutableStateFlow<List<Symbol>>(emptyList())
    val symbolResults: StateFlow<List<Symbol>> = _symbolResults.asStateFlow()

    private val _fileSearchResults = MutableStateFlow<List<FileNode>>(emptyList())
    val fileSearchResults: StateFlow<List<FileNode>> = _fileSearchResults.asStateFlow()

    private val pathStack = savedStateHandle.get<ArrayList<String>>(KEY_PATH_STACK)?.toMutableList() ?: mutableListOf()
    private var loadFilesJob: Job? = null
    private var loadContentJob: Job? = null
    private var saveJob: Job? = null
    private var mutationJob: Job? = null
    private var fileSearchJob: Job? = null
    private var symbolSearchJob: Job? = null
    private var fileSearchGeneration = 0L
    private var symbolSearchGeneration = 0L

    private val _editState = MutableStateFlow(restoredEditState())
    val editState: StateFlow<FileEditState> = _editState.asStateFlow()

    val uploadState: StateFlow<UploadQueueState> = uploadCoordinator.state

    init {
        loadCapabilities()
        loadFiles(savedStateHandle[KEY_CURRENT_PATH] ?: ROOT_PATH)
        when {
            _uiState.value.isSymbolMode -> {
                _uiState.value.symbolQuery.takeIf { it.isNotBlank() }?.let(::searchSymbols)
            }
            _uiState.value.isSearchActive -> {
                _uiState.value.searchQuery.takeIf { it.isNotBlank() }?.let {
                    searchFiles(it, debounce = true)
                }
            }
        }
    }

    private fun restoredEditState(): FileEditState {
        val path = savedStateHandle.get<String>(KEY_EDIT_PATH)
        val originalContent = savedStateHandle.get<String>(KEY_EDIT_ORIGINAL_CONTENT)
        val currentContent = savedStateHandle.get<String>(KEY_EDIT_CURRENT_CONTENT)
        return if (path == null) {
            FileEditState()
        } else if (originalContent == null || currentContent == null) {
            clearPersistedEditState()
            FileEditState()
        } else {
            FileEditState(
                path = path,
                originalContent = originalContent,
                currentContent = currentContent,
                isDirty = currentContent != originalContent,
                contentGeneration = 1,
                baselineHash = savedStateHandle[KEY_EDIT_BASELINE_HASH],
            )
        }
    }

    private fun persistEditState(state: FileEditState) {
        val currentContentFits =
            state.currentContent.length <= MAX_SAVED_EDIT_CONTENT_CHARS - state.originalContent.length
        if (state.path == null ||
            state.originalContent.length > MAX_SAVED_EDIT_CONTENT_CHARS ||
            !currentContentFits
        ) {
            clearPersistedEditState()
            return
        }

        // The path is the snapshot's commit marker. Invalidate the prior snapshot
        // before replacing its contents, then publish the new path last.
        savedStateHandle.remove<String>(KEY_EDIT_PATH)
        savedStateHandle[KEY_EDIT_ORIGINAL_CONTENT] = state.originalContent
        savedStateHandle[KEY_EDIT_CURRENT_CONTENT] = state.currentContent
        savedStateHandle[KEY_EDIT_BASELINE_HASH] = state.baselineHash
        savedStateHandle[KEY_EDIT_PATH] = state.path
    }

    private fun clearPersistedEditState() {
        // Remove the commit marker first. An oversized in-memory edit must restore
        // by reloading the file, never from partial or stale persisted contents.
        savedStateHandle.remove<String>(KEY_EDIT_PATH)
        savedStateHandle.remove<String>(KEY_EDIT_ORIGINAL_CONTENT)
        savedStateHandle.remove<String>(KEY_EDIT_CURRENT_CONTENT)
        savedStateHandle.remove<String>(KEY_EDIT_BASELINE_HASH)
    }

    private fun updateEditState(transform: (FileEditState) -> FileEditState) {
        _editState.update { current ->
            transform(current).also(::persistEditState)
        }
    }

    fun refresh() {
        val state = _uiState.value
        when {
            state.isSymbolMode -> searchSymbols(state.symbolQuery)
            state.isSearchActive -> searchFiles(state.searchQuery, debounce = false)
            else -> loadFiles(state.currentPath)
        }
    }

    fun navigateTo(path: String) {
        pathStack.add(_uiState.value.currentPath)
        if (pathStack.size > MAX_PERSISTED_PATH_DEPTH) {
            pathStack.removeAt(0)
        }
        loadFiles(path)
    }

    fun navigateUp() {
        val previousPath = pathStack.removeLastOrNull() ?: ROOT_PATH
        loadFiles(previousPath)
    }

    fun setSearchActive(active: Boolean) {
        savedStateHandle[KEY_SEARCH_ACTIVE] = active
        if (active) {
            savedStateHandle[KEY_SYMBOL_MODE] = false
            cancelSymbolSearch()
            _symbolResults.value = emptyList()
            _uiState.update {
                it.copy(
                    isSearchActive = true,
                    isSymbolMode = false,
                    symbolError = null,
                )
            }
            searchFiles(_uiState.value.searchQuery, debounce = true)
        } else {
            cancelFileSearch()
            _fileSearchResults.value = emptyList()
            _uiState.update {
                it.copy(
                    isSearchActive = false,
                    isFileSearchLoading = false,
                    fileSearchError = null,
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        val bounded = query.take(MAX_PERSISTED_QUERY_CHARS)
        savedStateHandle[KEY_SEARCH_QUERY] = bounded
        _uiState.update { it.copy(searchQuery = bounded) }
        if (_uiState.value.isSearchActive) {
            searchFiles(bounded, debounce = true)
        }
    }

    fun setSymbolMode(active: Boolean) {
        savedStateHandle[KEY_SYMBOL_MODE] = active
        if (active) {
            savedStateHandle[KEY_SEARCH_ACTIVE] = false
            cancelFileSearch()
            _fileSearchResults.value = emptyList()
            _uiState.update {
                it.copy(
                    isSearchActive = false,
                    isSymbolMode = true,
                    isFileSearchLoading = false,
                    fileSearchError = null,
                )
            }
            searchSymbols(_uiState.value.symbolQuery)
        } else {
            cancelSymbolSearch()
            _symbolResults.value = emptyList()
            _uiState.update { it.copy(isSymbolMode = false, symbolError = null) }
        }
    }

    fun updateSymbolQuery(query: String) {
        val bounded = query.take(MAX_PERSISTED_QUERY_CHARS)
        savedStateHandle[KEY_SYMBOL_QUERY] = bounded
        _uiState.update { it.copy(symbolQuery = bounded) }
        if (_uiState.value.isSymbolMode) {
            searchSymbols(bounded)
        }
    }

    fun clearFilters() {
        cancelFileSearch()
        cancelSymbolSearch()
        savedStateHandle[KEY_SEARCH_ACTIVE] = false
        savedStateHandle[KEY_SYMBOL_MODE] = false
        savedStateHandle[KEY_SEARCH_QUERY] = ""
        savedStateHandle[KEY_SYMBOL_QUERY] = ""
        _fileSearchResults.value = emptyList()
        _symbolResults.value = emptyList()
        _uiState.update {
            it.copy(
                isSearchActive = false,
                isSymbolMode = false,
                searchQuery = "",
                symbolQuery = "",
                isFileSearchLoading = false,
                fileSearchError = null,
                symbolError = null,
            )
        }
    }

    private fun persistPathState(path: String) {
        savedStateHandle[KEY_CURRENT_PATH] = path
        savedStateHandle[KEY_PATH_STACK] = ArrayList(pathStack)
    }

    private fun loadFiles(path: String) {
        loadFilesJob?.cancel()
        loadFilesJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = fileRepository.listFiles(path)) {
                is FileOperationResult.Ok -> {
                    val restoreError = pendingPathRestoreError
                    pendingPathRestoreError = null
                    restoringInitialPath = false
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            files = result.data.files,
                            currentPath = result.data.path,
                            pathRestoreError = restoreError,
                        )
                    }
                    persistPathState(result.data.path)
                }
                is FileOperationResult.Conflict -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
                is FileOperationResult.Failed -> {
                    if (path != ROOT_PATH && restoringInitialPath) {
                        pendingPathRestoreError = result.message
                        pathStack.clear()
                        loadFiles(ROOT_PATH)
                    } else {
                        restoringInitialPath = false
                        _uiState.update { it.copy(isLoading = false, error = result.message) }
                    }
                }
            }
        }
    }

    private fun loadCapabilities() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    capabilities = fileRepository.capabilities(),
                    capabilitiesLoaded = true,
                )
            }
        }
    }

    private fun searchFiles(query: String, debounce: Boolean) {
        cancelFileSearch()
        val generation = fileSearchGeneration
        _fileSearchResults.value = emptyList()
        if (query.isBlank()) {
            _uiState.update {
                it.copy(isFileSearchLoading = false, fileSearchError = null)
            }
            return
        }

        _uiState.update {
            it.copy(isFileSearchLoading = true, fileSearchError = null)
        }
        fileSearchJob = viewModelScope.launch {
            if (debounce) delay(FILE_SEARCH_DEBOUNCE_MS)
            val result = fileRepository.searchFiles(query)
            if (!isCurrentFileSearch(generation, query)) return@launch
            when (result) {
                is FileOperationResult.Ok -> {
                    _fileSearchResults.value = result.data
                    _uiState.update {
                        it.copy(isFileSearchLoading = false, fileSearchError = null)
                    }
                }
                is FileOperationResult.Conflict -> {
                    _uiState.update {
                        it.copy(isFileSearchLoading = false, fileSearchError = result.message)
                    }
                }
                is FileOperationResult.Failed -> {
                    _uiState.update {
                        it.copy(isFileSearchLoading = false, fileSearchError = result.message)
                    }
                }
            }
        }
    }

    fun searchSymbols(query: String) {
        cancelSymbolSearch()
        val generation = symbolSearchGeneration
        _symbolResults.value = emptyList()
        _uiState.update { it.copy(symbolError = null) }
        if (query.isBlank()) return

        symbolSearchJob = viewModelScope.launch {
            val result = fileRepository.searchSymbols(query)
            if (!isCurrentSymbolSearch(generation, query)) return@launch
            when (result) {
                is FileOperationResult.Ok -> {
                    _symbolResults.value = result.data
                }
                is FileOperationResult.Conflict -> _uiState.update { it.copy(symbolError = result.message) }
                is FileOperationResult.Failed -> _uiState.update { it.copy(symbolError = result.message) }
            }
        }
    }

    private fun cancelFileSearch() {
        fileSearchGeneration++
        fileSearchJob?.cancel()
        fileSearchJob = null
    }

    private fun cancelSymbolSearch() {
        symbolSearchGeneration++
        symbolSearchJob?.cancel()
        symbolSearchJob = null
    }

    private fun isCurrentFileSearch(generation: Long, query: String): Boolean {
        val state = _uiState.value
        return generation == fileSearchGeneration &&
            state.isSearchActive &&
            state.searchQuery == query
    }

    private fun isCurrentSymbolSearch(generation: Long, query: String): Boolean {
        val state = _uiState.value
        return generation == symbolSearchGeneration &&
            state.isSymbolMode &&
            state.symbolQuery == query
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
                    updateEditState { current ->
                        if (current.path == path && current.isDirty) {
                            current
                        } else {
                            FileEditState(
                                path = path,
                                originalContent = result.data.content,
                                currentContent = result.data.content,
                                isDirty = false,
                                contentGeneration = current.contentGeneration + 1,
                                baselineHash = result.data.hash,
                            )
                        }
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
        updateEditState { current ->
            if (current.path == null) return@updateEditState current
            current.copy(
                currentContent = newText,
                isDirty = newText != current.originalContent,
            )
        }
    }

    @Suppress("ReturnCount")
    fun requestSave() {
        if (!_uiState.value.capabilities.canWrite) {
            clearPendingSaveState()
            return
        }
        val state = _editState.value
        if (state.path == null) return
        if (!state.isDirty) {
            updateEditState { it.copy(saveError = null, pendingSavePreview = null) }
            return
        }
        updateEditState {
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
        updateEditState { it.copy(pendingSavePreview = null) }
    }

    fun confirmSave() {
        performSave(useBaselineHash = true)
    }

    fun reloadFromServer() {
        val path = _editState.value.path ?: return
        updateEditState { it.copy(conflict = null) }
        loadFileContent(path)
    }

    /** Re-issues the write with no baseline hash, suppressing stale-write detection. */
    fun overwriteAnyway() {
        if (!_uiState.value.capabilities.canWrite) {
            clearPendingSaveState()
            return
        }
        updateEditState { it.copy(conflict = null) }
        performSave(useBaselineHash = false)
    }

    @Suppress("ReturnCount")
    private fun performSave(useBaselineHash: Boolean) {
        if (!_uiState.value.capabilities.canWrite) {
            clearPendingSaveState()
            return
        }
        val state = _editState.value
        val path = state.path ?: return
        if (state.isSaving) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            updateEditState { it.copy(isSaving = true, saveError = null) }
            val expected = if (useBaselineHash) state.baselineHash else null
            val request = FileWriteRequest(path = path, content = state.currentContent, expectedHash = expected)
            when (val result = fileRepository.writeFile(request)) {
                is FileOperationResult.Ok -> {
                    updateEditState {
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
                    updateEditState {
                        it.copy(
                            isSaving = false,
                            pendingSavePreview = null,
                            conflict = ConflictInfo(message = result.message, currentHash = result.currentHash),
                        )
                    }
                }
                is FileOperationResult.Failed -> {
                    updateEditState {
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
        updateEditState { it.copy(conflict = null) }
    }

    fun discardEdits() {
        updateEditState { state ->
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
        updateEditState { it.copy(saveError = null) }
    }

    private fun clearPendingSaveState() {
        updateEditState {
            it.copy(
                isSaving = false,
                pendingSavePreview = null,
                conflict = null,
            )
        }
    }

    fun uploadFromSources(source: UploadSource, sourceIds: List<String>) {
        uploadCoordinator.upload(
            source = source,
            sourceIds = sourceIds,
            destinationPath = _uiState.value.currentPath.ifBlank { null },
            onComplete = { items -> if (items.isNotEmpty()) refresh() },
        )
    }

    fun createFile(name: String) {
        val path = childPath(_uiState.value.currentPath, name)
        performMutation(
            unsupported = "File creation is unavailable for this workspace",
            supported = _uiState.value.capabilities.canWrite,
            block = { fileRepository.writeFile(FileWriteRequest(path = path, content = "")) },
            onSuccess = { refresh() },
        )
    }

    fun createFolder(name: String) {
        val path = childPath(_uiState.value.currentPath, name)
        performMutation(
            unsupported = "Folder creation is unavailable for this workspace",
            supported = _uiState.value.capabilities.canCreateDirectory,
            block = { fileRepository.createDirectory(path) },
            onSuccess = { refresh() },
        )
    }

    fun renameFile(file: FileNode, newName: String) {
        val destination = childPath(parentPath(file.path), newName)
        performMutation(
            unsupported = "Rename is unavailable for this workspace",
            supported = _uiState.value.capabilities.canRename,
            block = { fileRepository.renameFile(file.path, destination) },
            onSuccess = { refresh() },
        )
    }

    fun deleteFile(file: FileNode) {
        performMutation(
            unsupported = "Delete is unavailable for this workspace",
            supported = _uiState.value.capabilities.canDelete,
            block = { fileRepository.deleteFile(file.path) },
            onSuccess = { refresh() },
        )
    }

    fun clearMutationMessage() {
        _uiState.update { it.copy(mutationMessage = null) }
    }

    private fun <T> performMutation(
        unsupported: String,
        supported: Boolean,
        block: suspend () -> FileOperationResult<T>,
        onSuccess: () -> Unit,
    ) {
        if (!supported) {
            _uiState.update { it.copy(mutationMessage = unsupported) }
            return
        }
        mutationJob?.cancel()
        mutationJob = viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, mutationMessage = null) }
            when (val result = block()) {
                is FileOperationResult.Ok -> {
                    _uiState.update { it.copy(isMutating = false) }
                    onSuccess()
                }
                is FileOperationResult.Conflict -> {
                    _uiState.update { it.copy(isMutating = false, mutationMessage = result.message) }
                }
                is FileOperationResult.Failed -> {
                    _uiState.update { it.copy(isMutating = false, mutationMessage = result.message) }
                }
            }
        }
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

    companion object {
        private const val FILE_SEARCH_DEBOUNCE_MS = 250L
        private const val MAX_PERSISTED_QUERY_CHARS = 1_024
        private const val MAX_PERSISTED_PATH_DEPTH = 128
        const val ROOT_PATH = ""
        const val KEY_CURRENT_PATH = "files_current_path"
        const val KEY_PATH_STACK = "files_path_stack"
        const val KEY_SEARCH_QUERY = "files_search_query"
        const val KEY_SEARCH_ACTIVE = "files_search_active"
        const val KEY_SYMBOL_QUERY = "files_symbol_query"
        const val KEY_SYMBOL_MODE = "files_symbol_mode"
        const val KEY_EDIT_PATH = "files_edit_path"
        const val KEY_EDIT_ORIGINAL_CONTENT = "files_edit_original_content"
        const val KEY_EDIT_CURRENT_CONTENT = "files_edit_current_content"
        const val KEY_EDIT_BASELINE_HASH = "files_edit_baseline_hash"

        fun childPath(parent: String, child: String): String = listOf(parent.trim('/'), child.trim('/'))
            .filter { it.isNotBlank() }
            .joinToString("/")

        fun parentPath(path: String): String = path.substringBeforeLast('/', missingDelimiterValue = "")
    }
}

data class FilesUiState(
    val isLoading: Boolean = false,
    val files: List<FileNode> = emptyList(),
    val currentPath: String = "",
    val fileContent: String? = null,
    val error: String? = null,
    val pathRestoreError: String? = null,
    val symbolError: String? = null,
    val fileSearchError: String? = null,
    val isFileSearchLoading: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isSymbolMode: Boolean = false,
    val symbolQuery: String = "",
    val capabilities: FileCapabilities = FileCapabilities(),
    val capabilitiesLoaded: Boolean = false,
    val isMutating: Boolean = false,
    val mutationMessage: String? = null,
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
