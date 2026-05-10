package dev.blazelight.p4oc.ui.screens.chat

import android.webkit.MimeTypeMap
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.data.files.FileRepository
import dev.blazelight.p4oc.data.files.FileRepositoryFactory
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.model.FileNode
import dev.blazelight.p4oc.ui.components.chat.SelectedFile
import dev.blazelight.p4oc.ui.screens.files.upload.UploadCoordinator
import dev.blazelight.p4oc.ui.screens.files.upload.UploadQueueState
import dev.blazelight.p4oc.ui.screens.files.upload.UploadSource
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
    private val workspaceClient: WorkspaceClient,
    private val scope: CoroutineScope,
    private val fileRepository: FileRepository = FileRepositoryFactory.create(workspaceClient),
) {
    private companion object {
        const val TAG = "FilePickerManager"
    }

    private val _pickerFiles = MutableStateFlow<List<FileNode>>(emptyList())
    val pickerFiles: StateFlow<List<FileNode>> = _pickerFiles.asStateFlow()

    private val _pickerCurrentPath = MutableStateFlow("")
    val pickerCurrentPath: StateFlow<String> = _pickerCurrentPath.asStateFlow()

    private val _isPickerLoading = MutableStateFlow(false)
    val isPickerLoading: StateFlow<Boolean> = _isPickerLoading.asStateFlow()

    private val _pickerError = MutableStateFlow<String?>(null)
    val pickerError: StateFlow<String?> = _pickerError.asStateFlow()

    private val _attachedFiles = MutableStateFlow<List<SelectedFile>>(emptyList())
    val attachedFiles: StateFlow<List<SelectedFile>> = _attachedFiles.asStateFlow()

    private val uploadCoordinator = UploadCoordinator(
        scope = scope,
        repositoryFactory = { fileRepository },
        destinationPath = { _pickerCurrentPath.value.ifBlank { null } },
        onComplete = { uploadedFiles ->
            if (uploadedFiles.isNotEmpty()) {
                loadPickerFiles(_pickerCurrentPath.value.ifBlank { null })
                _attachedFiles.update { current ->
                    uploadedFiles.fold(current) { acc, item ->
                        if (acc.none { it.path == item.destinationPath }) {
                            acc + SelectedFile(
                                path = item.destinationPath,
                                name = item.displayName,
                                mimeType = item.mimeType,
                            )
                        } else acc
                    }
                }
            }
        },
    )
    val uploadState: StateFlow<UploadQueueState> = uploadCoordinator.state

    fun loadPickerFiles(path: String? = null) {
        scope.launch {
            _isPickerLoading.value = true
            val effectivePath = path ?: "."
            val result = safeApiCall { workspaceClient.listFiles(effectivePath) }
            when (result) {
                is ApiResult.Success -> {
                    _pickerError.value = null
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
                    AppLog.w(TAG, "Failed to load files for path=$effectivePath: ${result.message}")
                    _pickerError.value = result.message
                    _isPickerLoading.value = false
                }
            }
        }
    }

    fun attachFile(file: FileNode) {
        val selected = SelectedFile(path = file.path, name = file.name, mimeType = mimeTypeForFilename(file.name))
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

    fun uploadAndAttach(source: UploadSource, sourceIds: List<String>) {
        uploadCoordinator.upload(source, sourceIds)
    }

    fun cancelUploads() {
        uploadCoordinator.cancel()
    }

    fun retryFailedUploads() {
        uploadCoordinator.retryFailed()
    }

    fun dismissUploadResult() {
        uploadCoordinator.dismiss()
    }

    /**
     * Restore attached files after a failed send — puts them back in the list.
     */
    fun restoreAttachedFiles(files: List<SelectedFile>) {
        _attachedFiles.update { current ->
            files.fold(current) { acc, file ->
                if (acc.none { it.path == file.path }) acc + file else acc
            }
        }
    }

    private fun mimeTypeForFilename(filename: String): String? {
        val extension = filename.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }
}
