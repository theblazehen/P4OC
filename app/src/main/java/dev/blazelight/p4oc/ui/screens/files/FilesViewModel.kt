package dev.blazelight.p4oc.ui.screens.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.data.files.FileOperationResult
import dev.blazelight.p4oc.data.files.FileRepository
import dev.blazelight.p4oc.domain.model.FileNode
import dev.blazelight.p4oc.domain.model.Symbol
import dev.blazelight.p4oc.ui.screens.files.upload.UploadOrchestrator
import dev.blazelight.p4oc.ui.screens.files.upload.UploadQueueState
import dev.blazelight.p4oc.ui.screens.files.upload.UploadSource
import kotlinx.coroutines.Dispatchers
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
    private var uploadJob: Job? = null
    private var uploadOrchestrator: UploadOrchestrator? = null

    private val _uploadState = MutableStateFlow(UploadQueueState())
    val uploadState: StateFlow<UploadQueueState> = _uploadState.asStateFlow()

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
            if (query.isBlank()) return@launch

            when (val result = fileRepository.searchSymbols(query)) {
                is FileOperationResult.Ok -> {
                    _symbolResults.value = result.data
                }
                is FileOperationResult.Conflict,
                is FileOperationResult.Failed -> { /* Silently fail for search */ }
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

    /**
     * Start a serial upload of the supplied source ids (e.g. SAF Uri strings).
     * Reads/uploads on [Dispatchers.IO]. Refreshes the file list after the
     * batch if any item succeeded. No-op when [sourceIds] is empty.
     */
    fun uploadFromSources(source: UploadSource, sourceIds: List<String>) {
        if (sourceIds.isEmpty()) return
        if (_uploadState.value.isActive) return
        val orchestrator = UploadOrchestrator(
            fileRepository = fileRepository,
            source = source,
        ).also { uploadOrchestrator = it }

        uploadJob?.cancel()
        uploadJob = viewModelScope.launch(Dispatchers.IO) {
            // Mirror orchestrator state to our exposed flow.
            val mirrorJob = launch {
                orchestrator.state.collect { _uploadState.value = it }
            }
            try {
                val plans = sourceIds.map { id ->
                    val meta = runCatching { source.probe(id) }.getOrNull()
                    UploadOrchestrator.Plan(
                        sourceId = id,
                        displayName = meta?.displayName,
                        sizeBytes = meta?.sizeBytes ?: -1L,
                        mimeType = meta?.mimeType,
                    )
                }
                val finalState = orchestrator.run(_uiState.value.currentPath, plans)
                if (finalState.anySuccess) {
                    launch { refresh() }
                }
            } finally {
                mirrorJob.cancel()
            }
        }
    }

    /**
     * Re-run only the items currently in [UploadPhase.Failed]. The
     * orchestrator already owns the [UploadSource] from the original
     * [uploadFromSources] call, so no source argument is required here.
     */
    fun retryFailedUploads() {
        val orchestrator = uploadOrchestrator ?: return
        if (_uploadState.value.failures.isEmpty()) return
        if (_uploadState.value.isActive) return
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch(Dispatchers.IO) {
            val mirrorJob = launch {
                orchestrator.state.collect { _uploadState.value = it }
            }
            try {
                orchestrator.retryFailed(_uiState.value.currentPath)
                if (_uploadState.value.anySuccess) launch { refresh() }
            } finally {
                mirrorJob.cancel()
            }
        }
    }

    fun cancelUploads() {
        uploadJob?.cancel()
        uploadJob = null
        uploadOrchestrator?.markCancelled()
    }

    fun dismissUploadResult() {
        if (_uploadState.value.isActive) return
        _uploadState.value = UploadQueueState()
        uploadOrchestrator = null
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
)
