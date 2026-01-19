package com.pocketcode.ui.screens.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcode.core.network.ApiResult
import com.pocketcode.core.network.ConnectionManager
import com.pocketcode.core.network.safeApiCall
import com.pocketcode.domain.model.FileNode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val connectionManager: ConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    private val pathStack = mutableListOf<String>()

    init {
        loadFiles(null)
    }

    fun refresh() {
        loadFiles(_uiState.value.currentPath.takeIf { it.isNotBlank() })
    }

    fun navigateTo(path: String) {
        pathStack.add(_uiState.value.currentPath)
        loadFiles(path)
    }

    fun navigateUp() {
        val previousPath = pathStack.removeLastOrNull() ?: ""
        loadFiles(previousPath.takeIf { it.isNotBlank() })
    }

    private fun loadFiles(path: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val api = connectionManager.getApi() ?: run {
                _uiState.update { it.copy(isLoading = false, error = "Not connected") }
                return@launch
            }
            val result = safeApiCall { api.listFiles(path) }

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
                    }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            files = files,
                            currentPath = path ?: ""
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
            }
        }
    }

    fun loadFileContent(path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, fileContent = null, error = null) }

            val api = connectionManager.getApi() ?: run {
                _uiState.update { it.copy(isLoading = false, error = "Not connected") }
                return@launch
            }
            val result = safeApiCall { api.readFile(path) }

            when (result) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(isLoading = false, fileContent = result.data.content)
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
            }
        }
    }
}

data class FilesUiState(
    val isLoading: Boolean = false,
    val files: List<FileNode> = emptyList(),
    val currentPath: String = "",
    val fileContent: String? = null,
    val error: String? = null
)
