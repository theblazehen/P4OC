package com.pocketcode.ui.screens.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcode.core.network.OpenCodeApi
import com.pocketcode.domain.model.FileStatus
import com.pocketcode.domain.model.VcsInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GitUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val branch: String? = null,
    val changedFiles: List<FileStatus> = emptyList(),
    val hasVcs: Boolean = true
)

@HiltViewModel
class GitViewModel @Inject constructor(
    private val api: OpenCodeApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitUiState())
    val uiState: StateFlow<GitUiState> = _uiState.asStateFlow()

    init {
        loadGitInfo()
    }

    fun loadGitInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val vcsInfo = api.getVcsInfo()
                val fileStatuses = try {
                    api.getFileStatus()
                } catch (e: Exception) {
                    emptyList()
                }
                
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        branch = vcsInfo.branch,
                        changedFiles = fileStatuses.map { dto ->
                            FileStatus(
                                path = dto.path,
                                status = dto.status,
                                added = dto.added,
                                removed = dto.removed
                            )
                        },
                        hasVcs = true,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        hasVcs = false,
                        error = e.message ?: "Failed to load git info"
                    ) 
                }
            }
        }
    }
}
