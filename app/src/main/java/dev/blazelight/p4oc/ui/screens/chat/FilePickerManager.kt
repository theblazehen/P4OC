package dev.blazelight.p4oc.ui.screens.chat

import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.domain.model.FileNode
import dev.blazelight.p4oc.ui.components.chat.SelectedFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Manages file browser navigation and attachment list.
 */
class FilePickerManager(
    private val connectionManager: ConnectionManager,
    private val scope: CoroutineScope
) {
    private val _pickerFiles = MutableStateFlow<List<FileNode>>(emptyList())
    val pickerFiles: StateFlow<List<FileNode>> = _pickerFiles.asStateFlow()

    private val _pickerCurrentPath = MutableStateFlow("")
    val pickerCurrentPath: StateFlow<String> = _pickerCurrentPath.asStateFlow()

    private val _isPickerLoading = MutableStateFlow(false)
    val isPickerLoading: StateFlow<Boolean> = _isPickerLoading.asStateFlow()

    private val _attachedFiles = MutableStateFlow<List<SelectedFile>>(emptyList())
    val attachedFiles: StateFlow<List<SelectedFile>> = _attachedFiles.asStateFlow()

    fun loadPickerFiles(path: String? = null) {
        scope.launch {
            _isPickerLoading.value = true
            val api = connectionManager.getApi() ?: run {
                _isPickerLoading.value = false
                return@launch
            }
            val effectivePath = path ?: "."
            val result = safeApiCall { api.listFiles(effectivePath) }
            when (result) {
                is ApiResult.Success -> {
                    val files = result.data.map { dto ->
                        FileNode(
                            name = dto.name,
                            path = dto.path,
                            absolute = dto.absolute,
                            type = dto.type,
                            ignored = dto.ignored
                        )
                    }
                    _pickerFiles.value = files
                    _pickerCurrentPath.value = if (effectivePath == ".") "" else effectivePath
                    _isPickerLoading.value = false
                }
                is ApiResult.Error -> {
                    _isPickerLoading.value = false
                }
            }
        }
    }

    fun attachFile(file: FileNode) {
        val selected = SelectedFile(path = file.path, name = file.name)
        _attachedFiles.update { current ->
            if (current.none { it.path == file.path }) {
                current + selected
            } else current
        }
    }

    fun detachFile(path: String) {
        _attachedFiles.update { current ->
            current.filter { it.path != path }
        }
    }

    fun clearAttachedFiles() {
        _attachedFiles.value = emptyList()
    }

    /**
     * Restore attached files after a failed send — puts them back in the list.
     */
    fun restoreAttachedFiles(files: List<SelectedFile>) {
        _attachedFiles.value = files
    }
}
