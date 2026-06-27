package dev.blazelight.p4oc.ui.screens.chat

import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.core.mime.FilenameMimeType
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.safeApiCall
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Manages file browser navigation and attachment list.
 */
class FilePickerManager(
    private val workspaceClient: WorkspaceClient,
    private val scope: CoroutineScope,
    private val uploadCoordinator: UploadCoordinator,
    private val settingsDataStore: SettingsDataStore,
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

    val uploadState: StateFlow<UploadQueueState> = uploadCoordinator.state

    fun loadPickerFiles(path: String? = null) {
        scope.launch {
            _isPickerLoading.value = true
            val workspaceKey = uploadDirectoryWorkspaceKey()
            val rememberedPath = settingsDataStore.lastUploadDirectoriesByWorkspace.first()[workspaceKey]
            val effectivePath = path ?: rememberedPath?.ifBlank { null } ?: "."
            val result = loadPickerFilesForPath(workspaceKey, effectivePath)
            if (result is ApiResult.Error && path == null && effectivePath != ".") {
                AppLog.w(TAG, "Remembered upload folder '$effectivePath' unavailable; falling back to root")
                settingsDataStore.setLastUploadDirectory(workspaceKey, null)
                loadPickerFilesForPath(workspaceKey, ".")
            }
        }
    }

    private suspend fun loadPickerFilesForPath(workspaceKey: String, path: String): ApiResult<Unit> {
        val result = safeApiCall { workspaceClient.listFiles(path) }
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
                val resolved = if (path == ".") "" else path
                _pickerCurrentPath.value = resolved
                _isPickerLoading.value = false
                settingsDataStore.setLastUploadDirectory(workspaceKey, resolved)
                return ApiResult.Success(Unit)
            }
            is ApiResult.Error -> {
                AppLog.w(TAG, "Failed to load files for path=$path: ${result.message}")
                _pickerError.value = result.message
                _isPickerLoading.value = false
                return result
            }
        }
    }

    private fun uploadDirectoryWorkspaceKey(): String = buildString {
        append(workspaceClient.workspace.server.endpointKey)
        append('|')
        append(workspaceClient.workspace.key.toString())
    }

    fun attachFile(file: FileNode) {
        val selected = SelectedFile(path = file.path, name = file.name, mimeType = FilenameMimeType.resolve(file.name))
        _attachedFiles.update { current ->
            if (current.none { it.path == file.path }) {
                current + selected
            } else {
                current
            }
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
        val currentPath = _pickerCurrentPath.value.ifBlank { null }
        uploadCoordinator.upload(
            source = source,
            sourceIds = sourceIds,
            destinationPath = currentPath,
            onComplete = { uploadedFiles ->
                if (uploadedFiles.isNotEmpty()) {
                    loadPickerFiles(currentPath)
                    _attachedFiles.update { current ->
                        uploadedFiles.fold(current) { acc, item ->
                            if (acc.none { it.path == item.destinationPath }) {
                                acc + SelectedFile(
                                    path = item.destinationPath,
                                    name = item.displayName,
                                    mimeType = item.mimeType,
                                )
                            } else {
                                acc
                            }
                        }
                    }
                }
            },
        )
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
}
