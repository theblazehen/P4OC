package dev.blazelight.p4oc.ui.screens.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.domain.model.FileStatus
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
    val projectName: String? = null,
    val branch: String? = null,
    val changedFiles: List<FileStatus> = emptyList(),
    val hasVcs: Boolean = true
)

@HiltViewModel
class GitViewModel @Inject constructor(
    private val connectionManager: ConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitUiState())
    val uiState: StateFlow<GitUiState> = _uiState.asStateFlow()

    private var currentProjectId: String? = null

    fun loadGitInfoForProject(projectId: String?) {
        if (projectId == currentProjectId && !_uiState.value.isLoading) return
        currentProjectId = projectId
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val api = connectionManager.getApi() ?: run {
                _uiState.update { it.copy(isLoading = false, hasVcs = false, error = "Not connected") }
                return@launch
            }
            
            try {
                if (projectId != null) {
                    val projects = api.listProjects()
                    val project = projects.find { it.id == projectId }
                    
                    if (project == null) {
                        _uiState.update { it.copy(isLoading = false, hasVcs = false, error = "Project not found") }
                        return@launch
                    }
                    
                    val projectName = project.worktree.substringAfterLast("/")
                    val hasGit = project.vcs == "git"
                    
                    if (!hasGit) {
                        _uiState.update { 
                            it.copy(
                                isLoading = false, 
                                hasVcs = false, 
                                projectName = projectName
                            ) 
                        }
                        return@launch
                    }
                    
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            hasVcs = true,
                            projectName = projectName,
                            branch = "main",
                            changedFiles = emptyList(),
                            error = null
                        ) 
                    }
                } else {
                    loadGitInfo()
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        hasVcs = false,
                        error = e.message ?: "Failed to load project info"
                    ) 
                }
            }
        }
    }

    fun loadGitInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val api = connectionManager.getApi() ?: run {
                _uiState.update { it.copy(isLoading = false, hasVcs = false, error = "Not connected") }
                return@launch
            }
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
